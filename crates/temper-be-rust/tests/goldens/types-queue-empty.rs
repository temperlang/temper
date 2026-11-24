use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let queue: temper_core::Queue<Arc<String>> = temper_core::queue::new();
            temper_core::queue::add(&queue, Arc::new("a".to_string()));
            println!("{}", temper_core::queue::remove_first(&queue));
            println!("before");
            println!("{}", temper_core::queue::remove_first(&queue));
            println!("not reached");
            Ok(())
        })
        .clone()
}
