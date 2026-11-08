//! Android H.264 decoder using the NDK MediaCodec buffer API.
//!
//! The game renderer consumes tightly packed NV12 frames. We request the
//! semi-planar YUV420 output format and also accept planar YUV420 output.

use std::time::{Duration, Instant};

use anyhow::{Context, Result, bail, ensure};
use ndk::media::{
    media_codec::{
        DequeuedInputBufferResult, DequeuedOutputBufferInfoResult, MediaCodec,
        MediaCodecDirection,
    },
    media_format::MediaFormat,
};

use super::{FrameDecoder, Nv12Frame, Output, Packet, StreamInfo};

const MIME_AVC: &str = "video/avc";
const COLOR_YUV420_PLANAR: i32 = 19;
const COLOR_YUV420_SEMIPLANAR: i32 = 21;
const BUFFER_FLAG_END_OF_STREAM: u32 = 4;
const NAL_START_CODE: [u8; 4] = [0, 0, 0, 1];

#[derive(Clone, Copy)]
struct Layout {
    width: usize,
    height: usize,
    stride: usize,
    slice_height: usize,
    color_format: i32,
}

pub struct Decoder {
    codec: MediaCodec,
    layout: Layout,
    timescale: u64,
}

impl Decoder {
    fn read_layout(&mut self) {
        let format = self.codec.output_format();
        self.layout = Layout {
            width: format.i32("width").unwrap_or(super::super::W as i32).max(0) as usize,
            height: format.i32("height").unwrap_or(super::super::H as i32).max(0) as usize,
            stride: format.i32("stride").unwrap_or(super::super::W as i32).max(0) as usize,
            slice_height: format.i32("slice-height").unwrap_or(super::super::H as i32).max(0) as usize,
            color_format: format.i32("color-format").unwrap_or(COLOR_YUV420_SEMIPLANAR),
        };
    }

    fn drain(&mut self, output: &mut Output, timeout: Duration) -> Result<bool> {
        loop {
            match self.codec.dequeue_output_buffer(timeout)? {
                DequeuedOutputBufferInfoResult::Buffer(buffer) => {
                    let info = *buffer.info();
                    let eos = info.flags() & BUFFER_FLAG_END_OF_STREAM != 0;
                    let frame = if info.size() > 0 {
                        let bytes = buffer.buffer();
                        Some(frame_from_output(
                            bytes,
                            info.offset().max(0) as usize,
                            info.size().max(0) as usize,
                            self.layout,
                        )?)
                    } else {
                        None
                    };
                    self.codec.release_output_buffer(buffer, false)?;
                    if let Some(frame) = frame {
                        output.emit(frame);
                    }
                    if eos {
                        return Ok(true);
                    }
                }
                DequeuedOutputBufferInfoResult::OutputFormatChanged => self.read_layout(),
                DequeuedOutputBufferInfoResult::OutputBuffersChanged => {}
                DequeuedOutputBufferInfoResult::TryAgainLater => return Ok(false),
            }
        }
    }

    fn queue_packet(&mut self, packet: &Packet, output: &mut Output) -> Result<()> {
        let deadline = Instant::now() + Duration::from_secs(3);
        loop {
            match self.codec.dequeue_input_buffer(Duration::from_millis(20))? {
                DequeuedInputBufferResult::Buffer(mut input) => {
                    let target = input.buffer_mut();
                    ensure!(
                        packet.annex_b.len() <= target.len(),
                        "H.264 packet ({} bytes) exceeds MediaCodec input buffer ({} bytes)",
                        packet.annex_b.len(),
                        target.len()
                    );
                    for (slot, byte) in target.iter_mut().zip(packet.annex_b.iter().copied()) {
                        slot.write(byte);
                    }
                    let pts_us = (packet.presentation_time.max(0) as u64)
                        .saturating_mul(1_000_000)
                        / self.timescale.max(1);
                    self.codec.queue_input_buffer(input, 0, packet.annex_b.len(), pts_us, 0)?;
                    return Ok(());
                }
                DequeuedInputBufferResult::TryAgainLater => {
                    self.drain(output, Duration::ZERO)?;
                    if Instant::now() >= deadline {
                        bail!("Timed out waiting for an Android H.264 input buffer");
                    }
                }
            }
        }
    }
}

impl FrameDecoder for Decoder {
    const NAME: &'static str = "Android NDK MediaCodec";

    fn new(info: &StreamInfo) -> Result<Self> {
        ensure!(info.width == super::super::W && info.height == super::super::H,
            "Android movie decoder expects {}x{} H.264, got {}x{}",
            super::super::W, super::super::H, info.width, info.height);

        let mut csd0 = Vec::new();
        let mut csd1 = Vec::new();
        for nal in &info.parameter_sets {
            let Some(header) = nal.first() else { continue };
            match header & 0x1f {
                7 => { csd0.extend_from_slice(&NAL_START_CODE); csd0.extend_from_slice(nal); }
                8 => { csd1.extend_from_slice(&NAL_START_CODE); csd1.extend_from_slice(nal); }
                _ => {}
            }
        }
        ensure!(!csd0.is_empty() && !csd1.is_empty(), "MP4 is missing H.264 SPS/PPS parameter sets");

        let make_format = |color_format| {
            let mut format = MediaFormat::new();
            format.set_str("mime", MIME_AVC);
            format.set_i32("width", info.width as i32);
            format.set_i32("height", info.height as i32);
            format.set_i32("color-format", color_format);
            format.set_i32("max-input-size", (info.width * info.height * 2) as i32);
            format.set_buffer("csd-0", &csd0);
            format.set_buffer("csd-1", &csd1);
            format
        };

        let mut codec = MediaCodec::from_decoder_type(MIME_AVC)
            .context("This Android device has no AVC/H.264 MediaCodec decoder")?;
        let mut requested_color = COLOR_YUV420_SEMIPLANAR;
        let format = make_format(requested_color);
        if let Err(first_error) = codec.configure(&format, None, MediaCodecDirection::Decoder) {
            tracing::warn!("MediaCodec rejected NV12 output; retrying planar YUV420: {first_error}");
            drop(format);
            drop(codec);
            requested_color = COLOR_YUV420_PLANAR;
            codec = MediaCodec::from_decoder_type(MIME_AVC)
                .context("Recreating Android AVC decoder for planar YUV420")?;
            let planar_format = make_format(requested_color);
            codec.configure(&planar_format, None, MediaCodecDirection::Decoder)
                .context("Configuring Android H.264 MediaCodec for planar YUV420")?;
        }
        codec.start().context("Starting Android H.264 MediaCodec")?;

        let mut decoder = Self {
            codec,
            layout: Layout {
                width: info.width as usize,
                height: info.height as usize,
                stride: info.width as usize,
                slice_height: info.height as usize,
                color_format: requested_color,
            },
            timescale: u64::from(info.timescale),
        };
        decoder.read_layout();
        Ok(decoder)
    }

    fn decode(&mut self, packet: &Packet, output: &mut Output) -> Result<()> {
        self.queue_packet(packet, output)?;
        self.drain(output, Duration::ZERO)?;
        Ok(())
    }

    fn finish(&mut self, output: &mut Output) -> Result<()> {
        let deadline = Instant::now() + Duration::from_secs(5);
        loop {
            match self.codec.dequeue_input_buffer(Duration::from_millis(20))? {
                DequeuedInputBufferResult::Buffer(input) => {
                    self.codec.queue_input_buffer(input, 0, 0, 0, BUFFER_FLAG_END_OF_STREAM)?;
                    break;
                }
                DequeuedInputBufferResult::TryAgainLater => {
                    self.drain(output, Duration::from_millis(5))?;
                    if Instant::now() >= deadline {
                        bail!("Timed out sending end-of-stream to Android MediaCodec");
                    }
                }
            }
        }
        loop {
            if self.drain(output, Duration::from_millis(20))? {
                break;
            }
            if Instant::now() >= deadline {
                bail!("Timed out draining Android MediaCodec at end-of-stream");
            }
        }
        let _ = self.codec.stop();
        Ok(())
    }
}

fn frame_from_output(data: &[u8], offset: usize, size: usize, layout: Layout) -> Result<Nv12Frame> {
    let w = super::super::W as usize;
    let h = super::super::H as usize;
    ensure!(layout.width == w && layout.height == h,
        "MediaCodec output dimensions are {}x{}, expected {}x{}",
        layout.width, layout.height, w, h);
    ensure!(layout.stride >= w && layout.slice_height >= h,
        "Invalid MediaCodec YUV stride/slice-height: {}/{}", layout.stride, layout.slice_height);
    let end = offset.checked_add(size).context("MediaCodec output range overflow")?;
    ensure!(end <= data.len(), "MediaCodec output buffer is shorter than its reported size");
    let bytes = &data[offset..end];
    let y_len = layout.stride.checked_mul(layout.slice_height).context("Y plane size overflow")?;
    ensure!(bytes.len() >= y_len, "MediaCodec output is missing its Y plane");

    let mut y_plane = vec![0u8; w * h];
    for row in 0..h {
        let src = row * layout.stride;
        y_plane[row * w..(row + 1) * w].copy_from_slice(&bytes[src..src + w]);
    }

    let cw = w / 2;
    let ch = h / 2;
    let mut uv_plane = vec![0u8; w * ch];
    match layout.color_format {
        COLOR_YUV420_SEMIPLANAR => {
            let uv_stride = layout.stride;
            let uv_start = y_len;
            let required = uv_start.checked_add(uv_stride * ((layout.slice_height + 1) / 2))
                .context("UV plane size overflow")?;
            ensure!(bytes.len() >= required, "MediaCodec output is missing its interleaved UV plane");
            for row in 0..ch {
                let src = uv_start + row * uv_stride;
                uv_plane[row * w..(row + 1) * w].copy_from_slice(&bytes[src..src + w]);
            }
        }
        COLOR_YUV420_PLANAR => {
            let c_stride = (layout.stride + 1) / 2;
            let c_slice = (layout.slice_height + 1) / 2;
            let c_len = c_stride.checked_mul(c_slice).context("chroma plane size overflow")?;
            let u_start = y_len;
            let v_start = u_start.checked_add(c_len).context("V plane offset overflow")?;
            ensure!(bytes.len() >= v_start + c_len, "MediaCodec output is missing planar chroma data");
            for row in 0..ch {
                for col in 0..cw {
                    uv_plane[row * w + col * 2] = bytes[u_start + row * c_stride + col];
                    uv_plane[row * w + col * 2 + 1] = bytes[v_start + row * c_stride + col];
                }
            }
        }
        other => bail!("Android MediaCodec returned unsupported YUV color format {other:#x}; expected planar (19) or semi-planar (21)"),
    }

    Ok(Nv12Frame { y_plane, uv_plane, width: super::super::W, height: super::super::H })
}
