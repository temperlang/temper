use std::sync::{Arc, OnceLock, RwLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            crate::support::run_async(Arc::new(move || {
                let case_index5: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                let b: Arc<RwLock<Option<temper_core::PromiseBuilder<Arc<String>>>>> =
                    Arc::new(RwLock::new(None));
                let b2: Arc<RwLock<Option<temper_core::PromiseBuilder<Arc<String>>>>> =
                    Arc::new(RwLock::new(None));
                let b3: Arc<RwLock<Option<temper_core::PromiseBuilder<Arc<String>>>>> =
                    Arc::new(RwLock::new(None));
                let b4: Arc<RwLock<Option<temper_core::PromiseBuilder<Arc<String>>>>> =
                    Arc::new(RwLock::new(None));
                let b5: Arc<RwLock<Option<temper_core::PromiseBuilder<Arc<String>>>>> =
                    Arc::new(RwLock::new(None));
                let b6: Arc<RwLock<Option<temper_core::PromiseBuilder<Arc<String>>>>> =
                    Arc::new(RwLock::new(None));
                let a: Arc<RwLock<Option<temper_core::PromiseBuilder<()>>>> =
                    Arc::new(RwLock::new(None));
                let b7: Arc<RwLock<Option<temper_core::PromiseBuilder<()>>>> =
                    Arc::new(RwLock::new(None));
                let b8: Arc<RwLock<Option<temper_core::PromiseBuilder<Arc<String>>>>> =
                    Arc::new(RwLock::new(None));
                let t: Arc<RwLock<Arc<String>>> = Arc::new(RwLock::new(Arc::new(String::new())));
                let awaited2: Arc<RwLock<Option<temper_core::Promise<Arc<String>>>>> =
                    Arc::new(RwLock::new(None));
                let t2: Arc<RwLock<Arc<String>>> = Arc::new(RwLock::new(Arc::new(String::new())));
                let awaited3: Arc<RwLock<Option<temper_core::Promise<Arc<String>>>>> =
                    Arc::new(RwLock::new(None));
                let t3: Arc<RwLock<Arc<String>>> = Arc::new(RwLock::new(Arc::new(String::new())));
                let e: Arc<RwLock<Option<temper_core::BrokenPromise>>> =
                    Arc::new(RwLock::new(None));
                let awaited4: Arc<RwLock<Option<temper_core::Promise<Arc<String>>>>> =
                    Arc::new(RwLock::new(None));
                let t4: Arc<RwLock<Arc<String>>> = Arc::new(RwLock::new(Arc::new(String::new())));
                let e2: Arc<RwLock<Option<temper_core::BrokenPromise>>> =
                    Arc::new(RwLock::new(None));
                let awaited5: Arc<RwLock<Option<temper_core::Promise<Arc<String>>>>> =
                    Arc::new(RwLock::new(None));
                let t5: Arc<RwLock<Arc<String>>> = Arc::new(RwLock::new(Arc::new(String::new())));
                let awaited6: Arc<RwLock<Option<temper_core::Promise<Arc<String>>>>> =
                    Arc::new(RwLock::new(None));
                let t6: Arc<RwLock<Arc<String>>> = Arc::new(RwLock::new(Arc::new(String::new())));
                let awaited7: Arc<RwLock<Option<temper_core::Promise<Arc<String>>>>> =
                    Arc::new(RwLock::new(None));
                let t7: Arc<RwLock<Arc<String>>> = Arc::new(RwLock::new(Arc::new(String::new())));
                let awaited8: Arc<RwLock<Option<temper_core::Promise<Arc<String>>>>> =
                    Arc::new(RwLock::new(None));
                let awaited9: Arc<RwLock<Option<temper_core::Promise<()>>>> =
                    Arc::new(RwLock::new(None));
                let t8: Arc<RwLock<Arc<String>>> = Arc::new(RwLock::new(Arc::new(String::new())));
                let awaited10: Arc<RwLock<Option<temper_core::Promise<Arc<String>>>>> =
                    Arc::new(RwLock::new(None));
                temper_core::SafeGenerator::from_fn({
                    let b = b.clone();
                    let b2 = b2.clone();
                    let b3 = b3.clone();
                    let e = e.clone();
                    let b4 = b4.clone();
                    let e2 = e2.clone();
                    let b5 = b5.clone();
                    let b6 = b6.clone();
                    let a = a.clone();
                    let b7 = b7.clone();
                    let b8 = b8.clone();
                    let case_index5 = case_index5.clone();
                    let t = t.clone();
                    let awaited2 = awaited2.clone();
                    let t2 = t2.clone();
                    let awaited3 = awaited3.clone();
                    let t3 = t3.clone();
                    let awaited4 = awaited4.clone();
                    let t4 = t4.clone();
                    let awaited5 = awaited5.clone();
                    let t5 = t5.clone();
                    let awaited6 = awaited6.clone();
                    let t6 = t6.clone();
                    let awaited7 = awaited7.clone();
                    let t7 = t7.clone();
                    let awaited8 = awaited8.clone();
                    let awaited9 = awaited9.clone();
                    let t8 = t8.clone();
                    let awaited10 = awaited10.clone();
                    Arc::new(move |generator2: temper_core::SafeGenerator<()>| loop {
                        let case_index_local = temper_core::read_locked(&case_index5);
                        *case_index5.write().unwrap() = -1;
                        match case_index_local {
                            0 => {
                                *b.write().unwrap() = Some(temper_core::PromiseBuilder::new());
                                temper_core::read_locked(&b)
                                    .as_ref()
                                    .unwrap()
                                    .complete(Arc::new("Completed synchronously".to_string()));
                                *awaited2.write().unwrap() =
                                    Some(temper_core::read_locked(&b).as_ref().unwrap().promise());
                                *case_index5.write().unwrap() = 3;
                                crate::support::awake_upon(
                                    temper_core::read_locked(&awaited2).unwrap(),
                                    generator2.clone(),
                                );
                                return Some(());
                            }
                            1 => {
                                panic!();
                            }
                            2 => {
                                *b2.write().unwrap() = Some(temper_core::PromiseBuilder::new());
                                temper_core::read_locked(&b2)
                                    .as_ref()
                                    .unwrap()
                                    .break_promise();
                                *case_index5.write().unwrap() = 6;
                            }
                            3 => {
                                if (|| {
                                    *t.write().unwrap() = temper_core::read_locked(&awaited2)
                                        .as_ref()
                                        .unwrap()
                                        .get()?;
                                    println!("{}", temper_core::read_locked(&t));
                                    *case_index5.write().unwrap() = 2;
                                    Ok::<_, temper_core::BrokenPromise>(())
                                })()
                                .is_err()
                                {
                                    {
                                        *case_index5.write().unwrap() = 1;
                                    };
                                }
                            }
                            4 => {
                                *t2.write().unwrap() = Arc::new("Broken synchronously".to_string());
                                *case_index5.write().unwrap() = 5;
                            }
                            5 => {
                                println!("{}", temper_core::read_locked(&t2));
                                *b3.write().unwrap() = Some(temper_core::PromiseBuilder::new());
                                temper_core::read_locked(&b3)
                                    .as_ref()
                                    .unwrap()
                                    .break_promise();
                                *case_index5.write().unwrap() = 10;
                            }
                            6 => {
                                *awaited3.write().unwrap() =
                                    Some(temper_core::read_locked(&b2).as_ref().unwrap().promise());
                                *case_index5.write().unwrap() = 7;
                                crate::support::awake_upon(
                                    temper_core::read_locked(&awaited3).unwrap(),
                                    generator2.clone(),
                                );
                                return Some(());
                            }
                            7 => {
                                if (|| {
                                    *t2.write().unwrap() = temper_core::read_locked(&awaited3)
                                        .as_ref()
                                        .unwrap()
                                        .get()?;
                                    *case_index5.write().unwrap() = 5;
                                    Ok::<_, temper_core::BrokenPromise>(())
                                })()
                                .is_err()
                                {
                                    {
                                        *case_index5.write().unwrap() = 4;
                                    };
                                }
                            }
                            8 => {
                                *t3.write().unwrap() = Arc::new(format!(
                                    "Caught {}",
                                    temper_core::read_locked(&e).as_ref().unwrap().message()
                                ));
                                *case_index5.write().unwrap() = 9;
                            }
                            9 => {
                                println!("{}", temper_core::read_locked(&t3));
                                *b4.write().unwrap() = Some(temper_core::PromiseBuilder::new());
                                crate::support::run_async({
                                    let b4 = b4.clone();
                                    Arc::new(move || {
                                        let case_index: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                                        temper_core::SafeGenerator::from_fn({
                                            let b4 = b4.clone();
                                            let case_index = case_index.clone();
                                            Arc::new(
                                                move |_generator: temper_core::SafeGenerator<()>| {
                                                    let case_index_local =
                                                        temper_core::read_locked(&case_index);
                                                    *case_index.write().unwrap() = -1;
                                                    if case_index_local == 0 {
                                                        temper_core::read_locked(&b4)
                                                            .as_ref()
                                                            .unwrap()
                                                            .break_promise();
                                                        None
                                                    } else {
                                                        None
                                                    }
                                                },
                                            )
                                        })
                                    })
                                });
                                *case_index5.write().unwrap() = 14;
                            }
                            10 => {
                                *awaited4.write().unwrap() =
                                    Some(temper_core::read_locked(&b3).as_ref().unwrap().promise());
                                *case_index5.write().unwrap() = 11;
                                crate::support::awake_upon(
                                    temper_core::read_locked(&awaited4).unwrap(),
                                    generator2.clone(),
                                );
                                return Some(());
                            }
                            11 => {
                                if let Err(failure) = (|| {
                                    *t3.write().unwrap() = temper_core::read_locked(&awaited4)
                                        .as_ref()
                                        .unwrap()
                                        .get()?;
                                    *case_index5.write().unwrap() = 9;
                                    Ok::<_, temper_core::BrokenPromise>(())
                                })() {
                                    *e.write().unwrap() = Some(failure.clone());
                                    *case_index5.write().unwrap() = 8;
                                }
                            }
                            12 => {
                                *t4.write().unwrap() = Arc::new(format!(
                                    "Caught {} later",
                                    temper_core::read_locked(&e2).as_ref().unwrap().message()
                                ));
                                *case_index5.write().unwrap() = 13;
                            }
                            13 => {
                                println!("{}", temper_core::read_locked(&t4));
                                *case_index5.write().unwrap() = 18;
                            }
                            14 => {
                                *awaited5.write().unwrap() =
                                    Some(temper_core::read_locked(&b4).as_ref().unwrap().promise());
                                *case_index5.write().unwrap() = 15;
                                crate::support::awake_upon(
                                    temper_core::read_locked(&awaited5).unwrap(),
                                    generator2.clone(),
                                );
                                return Some(());
                            }
                            15 => {
                                if let Err(failure) = (|| {
                                    *t4.write().unwrap() = temper_core::read_locked(&awaited5)
                                        .as_ref()
                                        .unwrap()
                                        .get()?;
                                    *case_index5.write().unwrap() = 13;
                                    Ok::<_, temper_core::BrokenPromise>(())
                                })() {
                                    *e2.write().unwrap() = Some(failure.clone());
                                    *case_index5.write().unwrap() = 12;
                                }
                            }
                            16 => {
                                panic!();
                            }
                            17 => {
                                *b6.write().unwrap() = Some(temper_core::PromiseBuilder::new());
                                temper_core::read_locked(&b6)
                                    .as_ref()
                                    .unwrap()
                                    .complete(Arc::new("Worth doing twice".to_string()));
                                *awaited7.write().unwrap() =
                                    Some(temper_core::read_locked(&b6).as_ref().unwrap().promise());
                                *case_index5.write().unwrap() = 22;
                                crate::support::awake_upon(
                                    temper_core::read_locked(&awaited7).unwrap(),
                                    generator2.clone(),
                                );
                                return Some(());
                            }
                            18 => {
                                *b5.write().unwrap() = Some(temper_core::PromiseBuilder::new());
                                crate::support::run_async({
                                    let b5 = b5.clone();
                                    Arc::new(move || {
                                        let case_index2: Arc<RwLock<i32>> =
                                            Arc::new(RwLock::new(0));
                                        temper_core::SafeGenerator::from_fn({
                                            let b5 = b5.clone();
                                            let case_index2 = case_index2.clone();
                                            Arc::new(
                                                move |_generator: temper_core::SafeGenerator<()>| {
                                                    let case_index_local =
                                                        temper_core::read_locked(&case_index2);
                                                    *case_index2.write().unwrap() = -1;
                                                    if case_index_local == 0 {
                                                        temper_core::read_locked(&b5)
                                                            .as_ref()
                                                            .unwrap()
                                                            .complete(Arc::new(
                                                                "Completed asynchronously"
                                                                    .to_string(),
                                                            ));
                                                        None
                                                    } else {
                                                        None
                                                    }
                                                },
                                            )
                                        })
                                    })
                                });
                                *awaited6.write().unwrap() =
                                    Some(temper_core::read_locked(&b5).as_ref().unwrap().promise());
                                *case_index5.write().unwrap() = 19;
                                crate::support::awake_upon(
                                    temper_core::read_locked(&awaited6).unwrap(),
                                    generator2.clone(),
                                );
                                return Some(());
                            }
                            19 => {
                                if (|| {
                                    *t5.write().unwrap() = temper_core::read_locked(&awaited6)
                                        .as_ref()
                                        .unwrap()
                                        .get()?;
                                    println!("{}", temper_core::read_locked(&t5));
                                    *case_index5.write().unwrap() = 17;
                                    Ok::<_, temper_core::BrokenPromise>(())
                                })()
                                .is_err()
                                {
                                    {
                                        *case_index5.write().unwrap() = 16;
                                    };
                                }
                            }
                            20 => {
                                panic!();
                            }
                            21 => {
                                *a.write().unwrap() = Some(temper_core::PromiseBuilder::new());
                                *b7.write().unwrap() = Some(temper_core::PromiseBuilder::new());
                                crate::support::run_async({
                                    let a = a.clone();
                                    let b7 = b7.clone();
                                    Arc::new(move || {
                                        let case_index3: Arc<RwLock<i32>> =
                                            Arc::new(RwLock::new(0));
                                        let awaited: Arc<RwLock<Option<temper_core::Promise<()>>>> =
                                            Arc::new(RwLock::new(None));
                                        temper_core::SafeGenerator::from_fn({
                                            let a = a.clone();
                                            let b7 = b7.clone();
                                            let case_index3 = case_index3.clone();
                                            let awaited = awaited.clone();
                                            Arc::new(
                                                move |generator: temper_core::SafeGenerator<()>| loop {
                                                    let case_index_local =
                                                        temper_core::read_locked(&case_index3);
                                                    *case_index3.write().unwrap() = -1;
                                                    match case_index_local {
                                                        0 => {
                                                            *awaited.write().unwrap() = Some(
                                                                temper_core::read_locked(&b7)
                                                                    .as_ref()
                                                                    .unwrap()
                                                                    .promise(),
                                                            );
                                                            *case_index3.write().unwrap() = 3;
                                                            crate::support::awake_upon(
                                                                temper_core::read_locked(&awaited)
                                                                    .unwrap(),
                                                                generator.clone(),
                                                            );
                                                            return Some(());
                                                        }
                                                        1 => {
                                                            panic!();
                                                        }
                                                        2 => {
                                                            println!("After");
                                                            temper_core::read_locked(&a)
                                                                .as_ref()
                                                                .unwrap()
                                                                .complete(());
                                                            return None;
                                                        }
                                                        3 => {
                                                            if (|| {
                                                                temper_core::read_locked(&awaited)
                                                                    .as_ref()
                                                                    .unwrap()
                                                                    .get()?;
                                                                *case_index3.write().unwrap() = 2;
                                                                Ok::<_, temper_core::BrokenPromise>(
                                                                    (),
                                                                )
                                                            })()
                                                            .is_err()
                                                            {
                                                                {
                                                                    *case_index3.write().unwrap() =
                                                                        1;
                                                                };
                                                            }
                                                        }
                                                        _ => {
                                                            return None;
                                                        }
                                                    }
                                                },
                                            )
                                        })
                                    })
                                });
                                crate::support::run_async({
                                    let b7 = b7.clone();
                                    Arc::new(move || {
                                        let case_index4: Arc<RwLock<i32>> =
                                            Arc::new(RwLock::new(0));
                                        temper_core::SafeGenerator::from_fn({
                                            let b7 = b7.clone();
                                            let case_index4 = case_index4.clone();
                                            Arc::new(
                                                move |_generator: temper_core::SafeGenerator<()>| {
                                                    let case_index_local =
                                                        temper_core::read_locked(&case_index4);
                                                    *case_index4.write().unwrap() = -1;
                                                    if case_index_local == 0 {
                                                        println!("Before");
                                                        temper_core::read_locked(&b7)
                                                            .as_ref()
                                                            .unwrap()
                                                            .complete(());
                                                        None
                                                    } else {
                                                        None
                                                    }
                                                },
                                            )
                                        })
                                    })
                                });
                                *awaited9.write().unwrap() =
                                    Some(temper_core::read_locked(&a).as_ref().unwrap().promise());
                                *case_index5.write().unwrap() = 26;
                                crate::support::awake_upon(
                                    temper_core::read_locked(&awaited9).unwrap(),
                                    generator2.clone(),
                                );
                                return Some(());
                            }
                            22 => {
                                'orelse: {
                                    *t6.write().unwrap() = if let Ok(v) =
                                        temper_core::read_locked(&awaited7).as_ref().unwrap().get()
                                    {
                                        v
                                    } else {
                                        break 'orelse;
                                    };
                                    println!("{}", temper_core::read_locked(&t6));
                                    *awaited8.write().unwrap() = Some(
                                        temper_core::read_locked(&b6).as_ref().unwrap().promise(),
                                    );
                                    *case_index5.write().unwrap() = 23;
                                    crate::support::awake_upon(
                                        temper_core::read_locked(&awaited8).unwrap(),
                                        generator2.clone(),
                                    );
                                    return Some(());
                                }
                                {
                                    *case_index5.write().unwrap() = 20;
                                };
                            }
                            23 => {
                                if (|| {
                                    *t7.write().unwrap() = temper_core::read_locked(&awaited8)
                                        .as_ref()
                                        .unwrap()
                                        .get()?;
                                    println!("{}", temper_core::read_locked(&t7));
                                    *case_index5.write().unwrap() = 21;
                                    Ok::<_, temper_core::BrokenPromise>(())
                                })()
                                .is_err()
                                {
                                    {
                                        *case_index5.write().unwrap() = 20;
                                    };
                                }
                            }
                            24 => {
                                panic!();
                            }
                            25 => {
                                println!("What is your favourite colour?");
                                *b8.write().unwrap() = Some(temper_core::PromiseBuilder::new());
                                temper_core::read_locked(&b8)
                                    .as_ref()
                                    .unwrap()
                                    .complete(Arc::new("Blue".to_string()));
                                temper_core::read_locked(&b8)
                                    .as_ref()
                                    .unwrap()
                                    .complete(Arc::new("No, yell...".to_string()));
                                *awaited10.write().unwrap() =
                                    Some(temper_core::read_locked(&b8).as_ref().unwrap().promise());
                                *case_index5.write().unwrap() = 27;
                                crate::support::awake_upon(
                                    temper_core::read_locked(&awaited10).unwrap(),
                                    generator2.clone(),
                                );
                                return Some(());
                            }
                            26 => {
                                if (|| {
                                    temper_core::read_locked(&awaited9)
                                        .as_ref()
                                        .unwrap()
                                        .get()?;
                                    *case_index5.write().unwrap() = 25;
                                    Ok::<_, temper_core::BrokenPromise>(())
                                })()
                                .is_err()
                                {
                                    {
                                        *case_index5.write().unwrap() = 24;
                                    };
                                }
                            }
                            27 => {
                                if (|| {
                                    *t8.write().unwrap() = temper_core::read_locked(&awaited10)
                                        .as_ref()
                                        .unwrap()
                                        .get()?;
                                    println!("{}", temper_core::read_locked(&t8));
                                    *case_index5.write().unwrap() = -1;
                                    Ok::<_, temper_core::BrokenPromise>(())
                                })()
                                .is_err()
                                {
                                    {
                                        *case_index5.write().unwrap() = -1;
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
            Ok(())
        })
        .clone()
}
