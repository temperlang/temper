use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            println!("Checkpoint 0");
            {
                let _a: i32;
                panic!()
            };
            println!("Checkpoint 1");
            {
                let _a2: i32 = 1;
                yay();
            };
            println!("Checkpoint 2");
            {
                let a3: i32 = 1;
                panic!();
            }
        })
        .clone()
}

pub(crate) fn yay() {
    println!("yay");
}

pub(crate) fn meh() {
    println!("meh");
}

pub(crate) fn boo() {
    println!("boo");
}

#[derive(Clone, Debug)]
pub(crate) enum Problem {
    Oops(Oops),
}

temper_core::impl_failure_enum!(Problem, Oops(Oops));

struct OopsStruct {
    cause: temper_core::Cause,
}

#[derive(Clone)]
pub(crate) struct Oops(Arc<OopsStruct>);

impl Oops {
    #[allow(dead_code)]
    pub fn new() -> Oops {
        Oops(Arc::new(OopsStruct {
            cause: temper_core::Cause::new(),
        }))
    }

    pub fn message(&self) -> Arc<String> {
        Arc::new("oops".to_string())
    }
}

temper_core::impl_failure!(Oops, message, cause);

temper_core::impl_any_value_trait!(Oops, [Problem]);

pub(crate) fn f(x: i32) -> Result<i32, Oops> {
    Ok(x)
}

pub(crate) fn g(x: i32) -> Result<i32, Oops> {
    Ok(x)
}

pub(crate) fn i(x: i32) -> Result<i32, Oops> {
    Ok(x)
}
