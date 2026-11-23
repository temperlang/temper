use std::sync::{Arc, OnceLock, RwLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            println!("before");
            crate::support::run_async(Arc::new(move || {
                let case_index: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                temper_core::SafeGenerator::from_fn({
                    let case_index = case_index.clone();
                    Arc::new(move |_generator: temper_core::SafeGenerator<()>| {
                        let case_index_local = temper_core::read_locked(&case_index);
                        *case_index.write().unwrap() = -1;
                        if case_index_local == 0 {
                            println!("in async");
                            third(Arc::new(vec![1, 2]));
                            None
                        } else {
                            None
                        }
                    })
                })
            }));
            Ok(())
        })
        .clone()
}

pub(crate) fn third(ls: impl temper_core::ToList<i32>) -> i32 {
    let ls = ls.to_list();
    temper_core::ListedTrait::get(&ls, 2)
}
