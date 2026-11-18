//! This build's version, as `build.rs` names it from git
//! ([`temper_tmpl::version`] has the rules and the spellings), with the
//! commit it was built from and whether the tree had changes.

use temper_tmpl::version::Version;

/// The build's version: `0.7.0-alpha.0.123` for a dev build.
pub fn built() -> Version {
    let text = env!("TEMPER_BUILD_VERSION");
    Version::parse(text).unwrap_or_else(|| panic!("build.rs named {text:?}"))
}

/// The commit the build is from, abbreviated, where git could say.
pub fn commit() -> Option<&'static str> {
    option_env!("TEMPER_BUILD_COMMIT")
}

/// Whether tracked files differed from that commit, when the build
/// script last ran (it runs again only when HEAD or the tags move).
pub fn modified() -> bool {
    option_env!("TEMPER_BUILD_MODIFIED") == Some("1")
}

/// The version with the commit and a mark for changes, as `tempest
/// --version` prints it: `0.7.0-alpha.0.123 (3f2b063, modified)`.
pub fn describe() -> String {
    let version = built();
    match (commit(), modified()) {
        (Some(commit), true) => format!("{version} ({commit}, modified)"),
        (Some(commit), false) => format!("{version} ({commit})"),
        (None, _) => version.to_string(),
    }
}

/// Makes the build's version the one this process names for runtimes,
/// std, and core ([`temper_tmpl::version::current`]); a program calls it
/// first thing, as `temper` does. Without it, a library user's output
/// names a dev build with no commits counted (`0.7.0-alpha.0.0`), as
/// tests' does.
pub fn init() {
    temper_tmpl::version::set_current(built());
}

#[cfg(test)]
mod tests {
    #[test]
    fn describes() {
        let text = super::describe();
        assert!(text.starts_with(&super::built().to_string()), "{text}");
    }
}
