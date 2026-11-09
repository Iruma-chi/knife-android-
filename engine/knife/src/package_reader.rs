//! In-place NSP/XCI RomFS adapter for Knife's random-access asset layer.
//!
//! The caller supplies already-open descriptors (normally obtained from Android's Storage Access
//! Framework). The native reader duplicates them, so the Java caller may close its descriptors as
//! soon as `open` returns. This module deliberately does not choose or persist Android URIs.

use std::{
    ffi::{CStr, CString, c_char, c_void},
    io,
    os::fd::RawFd,
    ptr::NonNull,
    sync::{Arc, Mutex, OnceLock},
    sync::atomic::{AtomicU64, Ordering},
    time::{SystemTime, UNIX_EPOCH},
};

use anyhow::{Context, Result, anyhow, ensure};
use knife_core::storage::ReadAt;

#[repr(C)]
struct NativeInput {
    name: *const c_char,
    fd: i32,
}

#[repr(C)]
#[derive(Default)]
struct NativeEntry {
    size: u64,
    is_directory: u32,
}

unsafe extern "C" {
    fn nxi_game_open(
        inputs: *const NativeInput,
        input_count: usize,
        title_id: u64,
        error: *mut c_char,
        error_capacity: usize,
    ) -> *mut c_void;
    fn nxi_game_close(image: *mut c_void);
    fn nxi_game_title_id(image: *const c_void) -> u64;
    fn nxi_game_stat(image: *const c_void, path: *const c_char, entry: *mut NativeEntry) -> i32;
    fn nxi_game_list_count(image: *const c_void, directory: *const c_char) -> usize;
    fn nxi_game_list_name(
        image: *const c_void, directory: *const c_char, index: usize,
        name: *mut c_char, name_capacity: usize, entry: *mut NativeEntry,
    ) -> i32;
    fn nxi_game_read(
        image: *const c_void,
        path: *const c_char,
        offset: u64,
        destination: *mut c_void,
        length: u64,
        error: *mut c_char,
        error_capacity: usize,
    ) -> u64;
}

struct NativeHandle(NonNull<c_void>);

// GameImage's underlying file reads use pread and immutable storage. The mutex below also
// serializes FFI calls, making the ownership contract conservative across future native changes.
unsafe impl Send for NativeHandle {}
unsafe impl Sync for NativeHandle {}

impl Drop for NativeHandle {
    fn drop(&mut self) {
        // SAFETY: this handle is created only by nxi_game_open and dropped exactly once.
        unsafe { nxi_game_close(self.0.as_ptr()) };
    }
}

/// One opened package image. Individual RomFS files are exposed as `ReadAt` objects.
pub struct PackageImage {
    native: Mutex<NativeHandle>,
    title_id: u64,
    cache_id: u64,
}

impl PackageImage {
    /// Open package/split-image/key descriptors. Names must be basenames understood by the native
    /// parser (`game.nsp`, `game.xci`, `game.xc0`, `prod.keys`, etc.). The native layer duplicates FDs.
    pub fn open(inputs: &[(String, RawFd)], title_id: u64) -> Result<Arc<Self>> {
        ensure!(!inputs.is_empty(), "no package inputs were supplied");
        let names: Vec<CString> = inputs
            .iter()
            .map(|(name, fd)| {
                ensure!(*fd >= 0, "invalid file descriptor for {name}");
                CString::new(name.as_bytes()).with_context(|| format!("invalid input filename: {name}"))
            })
            .collect::<Result<_>>()?;
        let native_inputs: Vec<NativeInput> = names
            .iter()
            .zip(inputs)
            .map(|(name, (_, fd))| NativeInput { name: name.as_ptr(), fd: *fd })
            .collect();
        let mut error = [0i8; 1024];
        // SAFETY: arrays remain alive for the call; the native function copies names and duplicates FDs.
        let handle = unsafe {
            nxi_game_open(native_inputs.as_ptr(), native_inputs.len(), title_id, error.as_mut_ptr(), error.len())
        };
        let handle = NonNull::new(handle).ok_or_else(|| {
            // SAFETY: native error buffer is always NUL-terminated when capacity is nonzero.
            let message = unsafe { CStr::from_ptr(error.as_ptr()) }.to_string_lossy();
            anyhow!("opening NSP/XCI image: {message}")
        })?;
        // SAFETY: valid native handle returned by nxi_game_open.
        let resolved_title = unsafe { nxi_game_title_id(handle.as_ptr()) };
        static NEXT_CACHE_ID: AtomicU64 = AtomicU64::new(1);
        let nonce = SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_nanos() as u64;
        let cache_id = nonce ^ NEXT_CACHE_ID.fetch_add(1, Ordering::Relaxed);
        Ok(Arc::new(Self { native: Mutex::new(NativeHandle(handle)), title_id: resolved_title, cache_id }))
    }

    pub fn title_id(&self) -> u64 { self.title_id }
    pub fn cache_id(&self) -> u64 { self.cache_id }

    /// Open a file from RomFS as a random-access source. Missing paths return `Ok(None)` so the
    /// extracted-folder fallback can be used; malformed paths and native errors remain errors.
    pub fn try_file(self: &Arc<Self>, path: &str) -> Result<Option<Arc<dyn ReadAt>>> {
        let path_c = CString::new(path).context("RomFS path contains an interior NUL")?;
        let native = self.native.lock().map_err(|_| anyhow!("native game-image lock poisoned"))?;
        let mut entry = NativeEntry::default();
        // SAFETY: path and output struct are valid for the duration of the call.
        let found = unsafe { nxi_game_stat(native.0.as_ptr(), path_c.as_ptr(), &mut entry) };
        ensure!(found >= 0, "native RomFS stat failed: {path}");
        if found == 0 { return Ok(None); }
        ensure!(entry.is_directory == 0, "RomFS path is a directory: {path}");
        Ok(Some(Arc::new(PackageFile { image: self.clone(), path: path.to_owned(), len: entry.size })))
    }

    pub fn file(self: &Arc<Self>, path: &str) -> Result<Arc<dyn ReadAt>> {
        self.try_file(path)?.ok_or_else(|| anyhow!("RomFS file not found: {path}"))
    }

    /// Immediate children of a RomFS directory. Returned names retain their on-image spelling.
    pub fn list(&self, directory: &str) -> Result<Vec<(String, bool)>> {
        let directory = CString::new(directory).context("RomFS directory contains an interior NUL")?;
        let native = self.native.lock().map_err(|_| anyhow!("native game-image lock poisoned"))?;
        // SAFETY: handle and NUL-terminated directory remain valid for the call.
        let count = unsafe { nxi_game_list_count(native.0.as_ptr(), directory.as_ptr()) };
        ensure!(count <= 1_000_000, "native RomFS directory returned an unreasonable child count");
        let mut children = Vec::with_capacity(count);
        for index in 0..count {
            let mut name = vec![0i8; 4096];
            let mut entry = NativeEntry::default();
            // SAFETY: buffers and pointers remain valid for the call; native function bounds-checks index.
            let rc = unsafe {
                nxi_game_list_name(native.0.as_ptr(), directory.as_ptr(), index,
                    name.as_mut_ptr(), name.len(), &mut entry)
            };
            ensure!(rc == 0, "native RomFS directory enumeration failed at child {index}");
            // SAFETY: native function guarantees a NUL-terminated name within the buffer.
            let name = unsafe { CStr::from_ptr(name.as_ptr()) }.to_string_lossy().into_owned();
            children.push((name, entry.is_directory != 0));
        }
        Ok(children)
    }
}


#[repr(C)]
pub struct PackageInput {
    pub name: *const c_char,
    pub fd: i32,
}

static ACTIVE_PACKAGE: OnceLock<Mutex<Option<Arc<PackageImage>>>> = OnceLock::new();

fn active_package() -> &'static Mutex<Option<Arc<PackageImage>>> {
    ACTIVE_PACKAGE.get_or_init(|| Mutex::new(None))
}

/// Return the package installed by the Android launcher, if one was opened successfully.
pub fn current() -> Option<Arc<PackageImage>> {
    active_package().lock().ok()?.clone()
}

/// Clear a previously selected package before a new Activity launch, preventing stale package reuse.
#[unsafe(no_mangle)]
pub extern "C" fn knife_package_clear() {
    if let Ok(mut active) = active_package().lock() {
        *active = None;
    }
}

/// C ABI used by the small GameActivity JNI shim. The native GameImage duplicates each descriptor,
/// so the Kotlin layer can close its ParcelFileDescriptors immediately after this returns.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn knife_package_install(
    inputs: *const PackageInput,
    input_count: usize,
    title_id: u64,
    error: *mut c_char,
    error_capacity: usize,
) -> i32 {
    if !error.is_null() && error_capacity > 0 {
        // SAFETY: caller supplied a writable error buffer of the declared capacity.
        unsafe { *error = 0 };
    }
    let result = (|| -> Result<Arc<PackageImage>> {
        ensure!(!inputs.is_null() && input_count > 0 && input_count <= 64, "invalid package input array");
        // SAFETY: caller guarantees an array of input_count valid PackageInput values for this call.
        let inputs = unsafe { std::slice::from_raw_parts(inputs, input_count) };
        let mut owned = Vec::with_capacity(input_count);
        for input in inputs {
            ensure!(!input.name.is_null() && input.fd >= 0, "invalid package input name or descriptor");
            // SAFETY: JNI shim supplies NUL-terminated names that live for the duration of this call.
            let name = unsafe { CStr::from_ptr(input.name) }.to_string_lossy().into_owned();
            owned.push((name, input.fd));
        }
        PackageImage::open(&owned, title_id)
    })();
    match result {
        Ok(package) => match active_package().lock() {
            Ok(mut active) => {
                *active = Some(package);
                0
            }
            Err(_) => {
                if !error.is_null() && error_capacity > 0 {
                    let message = b"active package mutex is poisoned";
                    let count = message.len().min(error_capacity - 1);
                    // SAFETY: caller supplied a writable error buffer of the declared capacity.
                    unsafe {
                        std::ptr::copy_nonoverlapping(message.as_ptr(), error.cast::<u8>(), count);
                        *error.add(count) = 0;
                    }
                }
                -1
            }
        },
        Err(failure) => {
            if !error.is_null() && error_capacity > 0 {
                let message = failure.to_string();
                let bytes = message.as_bytes();
                let count = bytes.len().min(error_capacity - 1);
                // SAFETY: caller supplied a writable error buffer of the declared capacity.
                unsafe {
                    std::ptr::copy_nonoverlapping(bytes.as_ptr(), error.cast::<u8>(), count);
                    *error.add(count) = 0;
                }
            }
            -1
        }
    }
}

struct PackageFile {
    image: Arc<PackageImage>,
    path: String,
    len: u64,
}

impl ReadAt for PackageFile {
    fn len(&self) -> u64 { self.len }

    fn read_at(&self, offset: u64, destination: &mut [u8]) -> io::Result<()> {
        let length = u64::try_from(destination.len()).map_err(|_| io::Error::other("read length overflow"))?;
        let end = offset.checked_add(length).ok_or_else(|| io::Error::new(io::ErrorKind::InvalidInput, "read range overflow"))?;
        if end > self.len {
            return Err(io::Error::new(io::ErrorKind::UnexpectedEof, "read outside RomFS file"));
        }
        if destination.is_empty() { return Ok(()); }
        let path = CString::new(self.path.as_bytes()).map_err(|_| io::Error::new(io::ErrorKind::InvalidInput, "RomFS path contains NUL"))?;
        let native = self.image.native.lock().map_err(|_| io::Error::other("native game-image lock poisoned"))?;
        let mut error = [0i8; 512];
        // SAFETY: handle, path, and destination remain valid for the call; native read is bounded.
        let count = unsafe {
            nxi_game_read(native.0.as_ptr(), path.as_ptr(), offset, destination.as_mut_ptr().cast(), length, error.as_mut_ptr(), error.len())
        };
        if count != length {
            // SAFETY: native error buffer is NUL-terminated when non-empty.
            let message = unsafe { CStr::from_ptr(error.as_ptr()) }.to_string_lossy();
            return Err(io::Error::new(io::ErrorKind::UnexpectedEof, if message.is_empty() { "short RomFS read".into() } else { message.into_owned() }));
        }
        Ok(())
    }
}
