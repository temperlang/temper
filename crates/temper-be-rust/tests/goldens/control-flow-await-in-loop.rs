use std::sync::{Arc, OnceLock, RwLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            ONCE.set(temper_core::PromiseBuilder::new())
                .unwrap_or_else(|_| panic!());
            once().complete(Arc::new("same".to_string()));
            LATER
                .set(temper_core::PromiseBuilder::new())
                .unwrap_or_else(|_| panic!());
            crate::support::run_async(Arc::new(move || {
                let case_index: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                let i: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                let s: Arc<RwLock<Arc<String>>> = Arc::new(RwLock::new(Arc::new(String::new())));
                let j: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                let s2: Arc<RwLock<Arc<String>>> = Arc::new(RwLock::new(Arc::new(String::new())));
                let t: Arc<RwLock<Arc<String>>> = Arc::new(RwLock::new(Arc::new(String::new())));
                let awaited: Arc<RwLock<Option<temper_core::Promise<Arc<String>>>>> =
                    Arc::new(RwLock::new(None));
                let awaited2: Arc<RwLock<Option<temper_core::Promise<Arc<String>>>>> =
                    Arc::new(RwLock::new(None));
                let awaited3: Arc<RwLock<Option<temper_core::Promise<Arc<String>>>>> =
                    Arc::new(RwLock::new(None));
                temper_core::SafeGenerator::from_fn({
                    let i = i.clone();
                    let s = s.clone();
                    let j = j.clone();
                    let s2 = s2.clone();
                    let t = t.clone();
                    let case_index = case_index.clone();
                    let awaited = awaited.clone();
                    let awaited2 = awaited2.clone();
                    let awaited3 = awaited3.clone();
                    Arc::new(move |generator: temper_core::SafeGenerator<()>| loop {
                        let case_index_local = temper_core::read_locked(&case_index);
                        *case_index.write().unwrap() = -1;
                        match case_index_local {
                            0 => {
                                *i.write().unwrap() = 0;
                                *case_index.write().unwrap() = 1;
                            }
                            1 => {
                                if temper_core::read_locked(&i) < 2 {
                                    *case_index.write().unwrap() = 2;
                                } else {
                                    *case_index.write().unwrap() = 3;
                                }
                            }
                            2 => {
                                *awaited.write().unwrap() = Some(once().promise());
                                *case_index.write().unwrap() = 6;
                                crate::support::awake_upon(
                                    temper_core::read_locked(&awaited).unwrap(),
                                    generator.clone(),
                                );
                                return Some(());
                            }
                            3 => {
                                *j.write().unwrap() = 0;
                                *case_index.write().unwrap() = 7;
                            }
                            4 => {
                                *s.write().unwrap() = Arc::new("?".to_string());
                                *case_index.write().unwrap() = 5;
                            }
                            5 => {
                                println!(
                                    "{} {}",
                                    temper_core::read_locked(&s),
                                    temper_core::read_locked(&i)
                                );
                                *i.write().unwrap() = temper_core::read_locked(&i).wrapping_add(1);
                                *case_index.write().unwrap() = 1;
                            }
                            6 => {
                                if (|| {
                                    *s.write().unwrap() = temper_core::read_locked(&awaited)
                                        .as_ref()
                                        .unwrap()
                                        .get()?;
                                    *case_index.write().unwrap() = 5;
                                    Ok::<_, temper_core::BrokenPromise>(())
                                })()
                                .is_err()
                                {
                                    {
                                        *case_index.write().unwrap() = 4;
                                    };
                                }
                            }
                            7 => {
                                if temper_core::read_locked(&j) < 2 {
                                    *case_index.write().unwrap() = 8;
                                } else {
                                    *case_index.write().unwrap() = 9;
                                }
                            }
                            8 => {
                                *awaited2.write().unwrap() = Some(later().promise());
                                *case_index.write().unwrap() = 12;
                                crate::support::awake_upon(
                                    temper_core::read_locked(&awaited2).unwrap(),
                                    generator.clone(),
                                );
                                return Some(());
                            }
                            9 => {
                                *awaited3.write().unwrap() = Some(once().promise());
                                *case_index.write().unwrap() = 15;
                                crate::support::awake_upon(
                                    temper_core::read_locked(&awaited3).unwrap(),
                                    generator.clone(),
                                );
                                return Some(());
                            }
                            10 => {
                                *s2.write().unwrap() = Arc::new("?".to_string());
                                *case_index.write().unwrap() = 11;
                            }
                            11 => {
                                println!(
                                    "{} {}",
                                    temper_core::read_locked(&s2),
                                    temper_core::read_locked(&j)
                                );
                                *j.write().unwrap() = temper_core::read_locked(&j).wrapping_add(1);
                                *case_index.write().unwrap() = 7;
                            }
                            12 => {
                                if (|| {
                                    *s2.write().unwrap() = temper_core::read_locked(&awaited2)
                                        .as_ref()
                                        .unwrap()
                                        .get()?;
                                    *case_index.write().unwrap() = 11;
                                    Ok::<_, temper_core::BrokenPromise>(())
                                })()
                                .is_err()
                                {
                                    {
                                        *case_index.write().unwrap() = 10;
                                    };
                                }
                            }
                            13 => {
                                *t.write().unwrap() = Arc::new("?".to_string());
                                *case_index.write().unwrap() = 14;
                            }
                            14 => {
                                println!("{} after the loops", temper_core::read_locked(&t));
                                return None;
                            }
                            15 => {
                                if (|| {
                                    *t.write().unwrap() = temper_core::read_locked(&awaited3)
                                        .as_ref()
                                        .unwrap()
                                        .get()?;
                                    *case_index.write().unwrap() = 14;
                                    Ok::<_, temper_core::BrokenPromise>(())
                                })()
                                .is_err()
                                {
                                    {
                                        *case_index.write().unwrap() = 13;
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
            later().complete(Arc::new("later".to_string()));
            Ok(())
        })
        .clone()
}

static ONCE: OnceLock<temper_core::PromiseBuilder<Arc<String>>> = OnceLock::new();

pub(crate) fn once() -> temper_core::PromiseBuilder<Arc<String>> {
    crate::support::initialized(|| ONCE.get()).clone()
}

static LATER: OnceLock<temper_core::PromiseBuilder<Arc<String>>> = OnceLock::new();

pub(crate) fn later() -> temper_core::PromiseBuilder<Arc<String>> {
    crate::support::initialized(|| LATER.get()).clone()
}
