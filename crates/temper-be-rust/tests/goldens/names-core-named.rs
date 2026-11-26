use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let core_ = crate::core::greet("a module named core");
            println!("{core_}");
            println!(
                "shares {} {}",
                crate::core::share(7, 2),
                crate::core::share(1, 0)
            );
            let mut builtins: i32 = 1;
            builtins = builtins.wrapping_add(1);
            println!("builtins is {builtins}");
            let s = Arc::new("core's String".to_string());
            let mine = String_::new(3);
            println!("{s}, and mine holds {}", mine.0.n);
            println!("{}", twice(21));
            Ok(())
        })
        .clone()
}

struct StringStruct {
    n: i32,
}

#[derive(Clone)]
pub(crate) struct String_(Arc<StringStruct>);

impl String_ {
    pub fn new(n: i32) -> String_ {
        String_(Arc::new(StringStruct { n }))
    }
}

temper_core::impl_any_value_trait!(String_, []);

pub(crate) fn twice(core_: i32) -> i32 {
    core_.wrapping_mul(2)
}
