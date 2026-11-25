use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE.get_or_init(|| Ok(())).clone()
}

pub fn share(n: i32, d: i32) -> i32 {
    n.wrapping_div(d)
}

struct RatioStruct {
    n: i32,
    d: i32,
}

#[derive(Clone)]
pub struct Ratio(Arc<RatioStruct>);

impl Ratio {
    pub fn new(n: i32, d: i32) -> Ratio {
        Ratio(Arc::new(RatioStruct { n, d }))
    }

    #[allow(clippy::should_implement_trait)]
    pub fn eq(&self, other: Ratio) -> bool {
        self.0.n.wrapping_mul(other.0.d) == other.0.n.wrapping_mul(self.0.d)
    }

    pub fn n(&self) -> i32 {
        self.0.n
    }

    pub fn d(&self) -> i32 {
        self.0.d
    }
}

impl PartialEq for Ratio {
    fn eq(&self, other: &Ratio) -> bool {
        Ratio::eq(self, other.clone())
    }
}

temper_core::impl_any_value_trait!(Ratio, []);

pub fn ratio_of(n: i32, d: i32) -> Option<Ratio> {
    if d == 0 {
        None
    } else {
        Some(Ratio::new(n, d))
    }
}

pub mod builders {
    use super::*;

    #[derive(Clone)]
    pub struct RatioBuilder {
        pub n: i32,
        pub d: i32,
    }

    impl RatioBuilder {
        pub fn build(self) -> Ratio {
            Ratio::new(self.n, self.d)
        }
    }
}
