use std::sync::{Arc, OnceLock, RwLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            B.set(temper_core::PromiseBuilder::new())
                .unwrap_or_else(|_| panic!());
            crate::support::run_async(Arc::new(move || {
                let case_index: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                let v: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                let awaited: Arc<RwLock<Option<temper_core::Promise<i32>>>> =
                    Arc::new(RwLock::new(None));
                temper_core::SafeGenerator::from_fn({
                    let v = v.clone();
                    let case_index = case_index.clone();
                    let awaited = awaited.clone();
                    Arc::new(move |generator: temper_core::SafeGenerator<()>| loop {
                        let case_index_local = temper_core::read_locked(&case_index);
                        *case_index.write().unwrap() = -1;
                        match case_index_local {
                            0 => {
                                *awaited.write().unwrap() = Some(b().promise());
                                *case_index.write().unwrap() = 3;
                                crate::support::awake_upon(
                                    temper_core::read_locked(&awaited).unwrap(),
                                    generator.clone(),
                                );
                                return Some(());
                            }
                            1 => {
                                *v.write().unwrap() = -1;
                                *case_index.write().unwrap() = 2;
                            }
                            2 => {
                                println!("got {}", temper_core::read_locked(&v));
                                return None;
                            }
                            3 => {
                                if (|| {
                                    *v.write().unwrap() = temper_core::read_locked(&awaited)
                                        .as_ref()
                                        .unwrap()
                                        .get()?;
                                    *case_index.write().unwrap() = 2;
                                    Ok::<_, temper_core::BrokenPromise>(())
                                })()
                                .is_err()
                                {
                                    {
                                        *case_index.write().unwrap() = 1;
                                    };
                                }
                            }
                            _ => {
                                return None;
                            }
                        }
                    })
                })
            }));
            b().break_promise();
            Ok(())
        })
        .clone()
}

static B: OnceLock<temper_core::PromiseBuilder<i32>> = OnceLock::new();

pub(crate) fn b() -> temper_core::PromiseBuilder<i32> {
    crate::support::initialized(|| B.get()).clone()
}
