//! Writing generated files so that tools' incremental builds, which go
//! by files' times, redo only what changed: in `temper build`, and in
//! wasm too, where it writes nothing.

use std::path::Path;

/// FNV-1a, 64 bits: a hash of generated files' text that stays the same
/// from one release to the next, to tell their changes apart, not to
/// guard against anyone.
pub fn fnv64(bytes: &[u8]) -> u64 {
    let mut hash: u64 = 0xcbf2_9ce4_8422_2325;
    for &b in bytes {
        hash = (hash ^ u64::from(b)).wrapping_mul(0x0100_0000_01b3);
    }
    hash
}

/// Writes a file, and the directories above it, unless it already holds
/// exactly `bytes`, so an unchanged file keeps its time and the tools'
/// own builds, which go by times, skip what it builds. Whether it wrote.
pub fn write_if_changed(path: &Path, bytes: &[u8]) -> std::io::Result<bool> {
    if std::fs::read(path).is_ok_and(|old| old == bytes) {
        return Ok(false);
    }
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent)?;
    }
    std::fs::write(path, bytes)?;
    Ok(true)
}
