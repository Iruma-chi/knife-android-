//! Thread-safe random-access storage shared by filesystem and package-backed game data.
//!
//! `read_at` must either fill the complete destination or return an error. Implementations must
//! reject ranges outside `len()` and must not depend on a shared mutable cursor between callers.

use std::{
    fs::File,
    io::{Read, Seek, SeekFrom},
    path::Path,
    sync::Mutex,
};

/// Immutable random-access byte source. This is the common boundary for ordinary files and
/// future NSP/XCI RomFS-backed files.
pub trait ReadAt: Send + Sync {
    fn len(&self) -> u64;

    fn is_empty(&self) -> bool {
        self.len() == 0
    }

    fn read_at(&self, offset: u64, dst: &mut [u8]) -> std::io::Result<()>;
}

/// Portable filesystem adapter. The mutex makes seek+read atomic on all targets supported by
/// this crate; package-backed implementations can provide genuinely positional reads instead.
pub struct FileReadAt {
    file: Mutex<File>,
    len: u64,
}

impl FileReadAt {
    pub fn open(path: &Path) -> std::io::Result<Self> {
        let file = File::open(path)?;
        let len = file.metadata()?.len();
        Ok(Self { file: Mutex::new(file), len })
    }
}

impl ReadAt for FileReadAt {
    fn len(&self) -> u64 {
        self.len
    }

    fn read_at(&self, offset: u64, dst: &mut [u8]) -> std::io::Result<()> {
        let end = offset.checked_add(dst.len() as u64).ok_or_else(|| {
            std::io::Error::new(std::io::ErrorKind::InvalidInput, "read range overflow")
        })?;
        if end > self.len {
            return Err(std::io::Error::new(
                std::io::ErrorKind::UnexpectedEof,
                "read outside storage",
            ));
        }
        let mut file = self.file.lock().map_err(|_| {
            std::io::Error::new(std::io::ErrorKind::Other, "storage lock poisoned")
        })?;
        file.seek(SeekFrom::Start(offset))?;
        file.read_exact(dst)
    }
}
