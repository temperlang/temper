use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let builder: temper_core::ListBuilder<Arc<String>> = temper_core::listed::new_builder();
            temper_core::listed::add(&builder, Arc::new("a".to_string()), None);
            println!("{}", temper_core::listed::remove_last(&builder));
            println!("before");
            println!("{}", temper_core::listed::remove_last(&builder));
            println!("not reached");
            Ok(())
        })
        .clone()
}
