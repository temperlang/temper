//! Profiles: what a target handles natively (notes/tmpl/profiles.md).
//!
//! A backend's profile picks the shared lowerings it gets: the features
//! it keeps natively, and how it writes generators.

/// A set of features a target keeps natively.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Hash)]
pub struct Features(pub u32);

impl Features {
    pub const NONE: Features = Features(0);
    /// Statements inside expressions: `BlockValue`.
    pub const STATEMENT_EXPRESSIONS: Features = Features(1);
    /// `Switch` on integers, which [`crate::switches::to_if_chains`]
    /// lowers to `if`s where it's missing.
    pub const SWITCH: Features = Features(2);
    /// Code that's ill-typed but runnable, which a dynamically typed
    /// target runs as written, as Kotlin's interpreter does: a type-only
    /// `Error` runs as its value ([`crate::errors::settle`]). Without it,
    /// the `Error` fails where it runs, as a statically typed target's
    /// compiler would reject the code.
    pub const RUNS_TYPE_ERRORS: Features = Features(4);
    /// A bottom type, as a function's result: `Never<T>` kept where
    /// [`crate::nevers::may_keep`] allows; without it, `T` everywhere
    /// ([`crate::nevers::lower`]).
    pub const NEVER: Features = Features(8);
    pub const ALL: Features = Features(15);

    pub const fn with(self, other: Features) -> Features {
        Features(self.0 | other.0)
    }

    pub fn contains(self, other: Features) -> bool {
        self.0 & other.0 == other.0
    }
}

/// What a target handles natively, which lowering and validation read.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Profile {
    pub native: Features,
    /// How generator literals are written ([`crate::coroutines`]).
    pub coroutines: crate::coroutines::CoroutineStrategy,
}

impl Profile {
    /// Everything high-level: what starting tmpl may hold.
    pub fn full() -> Profile {
        Profile {
            native: Features::ALL,
            coroutines: crate::coroutines::CoroutineStrategy::TranslateToGenerator,
        }
    }
}
