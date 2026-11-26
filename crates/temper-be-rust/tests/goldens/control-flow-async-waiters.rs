use std::sync::{Arc, OnceLock, RwLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            crate::support::run_async(Arc::new(move || {
                let case_index7: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                let b: Arc<RwLock<Option<temper_core::PromiseBuilder<i32>>>> =
                    Arc::new(RwLock::new(None));
                let first_done: Arc<RwLock<Option<temper_core::PromiseBuilder<i32>>>> =
                    Arc::new(RwLock::new(None));
                let second_done: Arc<RwLock<Option<temper_core::PromiseBuilder<i32>>>> =
                    Arc::new(RwLock::new(None));
                let first: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                let second: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                let b2: Arc<RwLock<Option<temper_core::PromiseBuilder<i32>>>> =
                    Arc::new(RwLock::new(None));
                let first_done2: Arc<RwLock<Option<temper_core::PromiseBuilder<i32>>>> =
                    Arc::new(RwLock::new(None));
                let second_done2: Arc<RwLock<Option<temper_core::PromiseBuilder<i32>>>> =
                    Arc::new(RwLock::new(None));
                let first2: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                let second2: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                let awaited5: Arc<RwLock<Option<temper_core::Promise<i32>>>> =
                    Arc::new(RwLock::new(None));
                let awaited6: Arc<RwLock<Option<temper_core::Promise<i32>>>> =
                    Arc::new(RwLock::new(None));
                let awaited7: Arc<RwLock<Option<temper_core::Promise<i32>>>> =
                    Arc::new(RwLock::new(None));
                let awaited8: Arc<RwLock<Option<temper_core::Promise<i32>>>> =
                    Arc::new(RwLock::new(None));
                temper_core::SafeGenerator::from_fn({
                    let b = b.clone();
                    let first_done = first_done.clone();
                    let second_done = second_done.clone();
                    let first = first.clone();
                    let second = second.clone();
                    let b2 = b2.clone();
                    let first_done2 = first_done2.clone();
                    let second_done2 = second_done2.clone();
                    let first2 = first2.clone();
                    let second2 = second2.clone();
                    let case_index7 = case_index7.clone();
                    let awaited5 = awaited5.clone();
                    let awaited6 = awaited6.clone();
                    let awaited7 = awaited7.clone();
                    let awaited8 = awaited8.clone();
                    Arc::new(move |generator2: temper_core::SafeGenerator<()>| loop {
                        let case_index_local = temper_core::read_locked(&case_index7);
                        *case_index7.write().unwrap() = -1;
                        match case_index_local {
                            0 => {
                                *b.write().unwrap() = Some(temper_core::PromiseBuilder::new());
                                *first_done.write().unwrap() =
                                    Some(temper_core::PromiseBuilder::new());
                                *second_done.write().unwrap() =
                                    Some(temper_core::PromiseBuilder::new());
                                crate::support::run_async({
                                    let b = b.clone();
                                    let first_done = first_done.clone();
                                    Arc::new(move || {
                                        let case_index: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                                        let v: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                                        let awaited: Arc<
                                            RwLock<Option<temper_core::Promise<i32>>>,
                                        > = Arc::new(RwLock::new(None));
                                        temper_core::SafeGenerator::from_fn({
                                            let b = b.clone();
                                            let first_done = first_done.clone();
                                            let v = v.clone();
                                            let case_index = case_index.clone();
                                            let awaited = awaited.clone();
                                            Arc::new(
                                                move |generator: temper_core::SafeGenerator<()>| loop {
                                                    let case_index_local =
                                                        temper_core::read_locked(&case_index);
                                                    *case_index.write().unwrap() = -1;
                                                    match case_index_local {
                                                        0 => {
                                                            *awaited.write().unwrap() = Some(
                                                                temper_core::read_locked(&b)
                                                                    .as_ref()
                                                                    .unwrap()
                                                                    .promise(),
                                                            );
                                                            *case_index.write().unwrap() = 3;
                                                            crate::support::awake_upon(
                                                                temper_core::read_locked(&awaited)
                                                                    .unwrap(),
                                                                generator.clone(),
                                                            );
                                                            return Some(());
                                                        }
                                                        1 => {
                                                            *v.write().unwrap() = -1;
                                                            *case_index.write().unwrap() = 2;
                                                        }
                                                        2 => {
                                                            temper_core::read_locked(&first_done)
                                                                .as_ref()
                                                                .unwrap()
                                                                .complete(temper_core::read_locked(
                                                                    &v,
                                                                ));
                                                            return None;
                                                        }
                                                        3 => {
                                                            if (|| {
                                                                *v.write().unwrap() =
                                                                    temper_core::read_locked(
                                                                        &awaited,
                                                                    )
                                                                    .as_ref()
                                                                    .unwrap()
                                                                    .get()?;
                                                                *case_index.write().unwrap() = 2;
                                                                Ok::<_, temper_core::BrokenPromise>(
                                                                    (),
                                                                )
                                                            })()
                                                            .is_err()
                                                            {
                                                                {
                                                                    *case_index.write().unwrap() =
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
                                    let b = b.clone();
                                    let second_done = second_done.clone();
                                    Arc::new(move || {
                                        let case_index2: Arc<RwLock<i32>> =
                                            Arc::new(RwLock::new(0));
                                        let v2: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                                        let awaited2: Arc<
                                            RwLock<Option<temper_core::Promise<i32>>>,
                                        > = Arc::new(RwLock::new(None));
                                        temper_core::SafeGenerator::from_fn({
                                            let b = b.clone();
                                            let second_done = second_done.clone();
                                            let v2 = v2.clone();
                                            let case_index2 = case_index2.clone();
                                            let awaited2 = awaited2.clone();
                                            Arc::new(
                                                move |generator: temper_core::SafeGenerator<()>| loop {
                                                    let case_index_local =
                                                        temper_core::read_locked(&case_index2);
                                                    *case_index2.write().unwrap() = -1;
                                                    match case_index_local {
                                                        0 => {
                                                            *awaited2.write().unwrap() = Some(
                                                                temper_core::read_locked(&b)
                                                                    .as_ref()
                                                                    .unwrap()
                                                                    .promise(),
                                                            );
                                                            *case_index2.write().unwrap() = 3;
                                                            crate::support::awake_upon(
                                                                temper_core::read_locked(&awaited2)
                                                                    .unwrap(),
                                                                generator.clone(),
                                                            );
                                                            return Some(());
                                                        }
                                                        1 => {
                                                            *v2.write().unwrap() = -1;
                                                            *case_index2.write().unwrap() = 2;
                                                        }
                                                        2 => {
                                                            temper_core::read_locked(&second_done)
                                                                .as_ref()
                                                                .unwrap()
                                                                .complete(temper_core::read_locked(
                                                                    &v2,
                                                                ));
                                                            return None;
                                                        }
                                                        3 => {
                                                            if (|| {
                                                                *v2.write().unwrap() =
                                                                    temper_core::read_locked(
                                                                        &awaited2,
                                                                    )
                                                                    .as_ref()
                                                                    .unwrap()
                                                                    .get()?;
                                                                *case_index2.write().unwrap() = 2;
                                                                Ok::<_, temper_core::BrokenPromise>(
                                                                    (),
                                                                )
                                                            })()
                                                            .is_err()
                                                            {
                                                                {
                                                                    *case_index2.write().unwrap() =
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
                                    let b = b.clone();
                                    Arc::new(move || {
                                        let case_index3: Arc<RwLock<i32>> =
                                            Arc::new(RwLock::new(0));
                                        temper_core::SafeGenerator::from_fn({
                                            let b = b.clone();
                                            let case_index3 = case_index3.clone();
                                            Arc::new(
                                                move |_generator: temper_core::SafeGenerator<()>| {
                                                    let case_index_local =
                                                        temper_core::read_locked(&case_index3);
                                                    *case_index3.write().unwrap() = -1;
                                                    if case_index_local == 0 {
                                                        temper_core::read_locked(&b)
                                                            .as_ref()
                                                            .unwrap()
                                                            .complete(7);
                                                        None
                                                    } else {
                                                        None
                                                    }
                                                },
                                            )
                                        })
                                    })
                                });
                                *awaited5.write().unwrap() = Some(
                                    temper_core::read_locked(&first_done)
                                        .as_ref()
                                        .unwrap()
                                        .promise(),
                                );
                                *case_index7.write().unwrap() = 3;
                                crate::support::awake_upon(
                                    temper_core::read_locked(&awaited5).unwrap(),
                                    generator2.clone(),
                                );
                                return Some(());
                            }
                            1 => {
                                panic!();
                            }
                            2 => {
                                *b2.write().unwrap() = Some(temper_core::PromiseBuilder::new());
                                *first_done2.write().unwrap() =
                                    Some(temper_core::PromiseBuilder::new());
                                *second_done2.write().unwrap() =
                                    Some(temper_core::PromiseBuilder::new());
                                crate::support::run_async({
                                    let b2 = b2.clone();
                                    let first_done2 = first_done2.clone();
                                    Arc::new(move || {
                                        let case_index4: Arc<RwLock<i32>> =
                                            Arc::new(RwLock::new(0));
                                        let v3: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                                        let awaited3: Arc<
                                            RwLock<Option<temper_core::Promise<i32>>>,
                                        > = Arc::new(RwLock::new(None));
                                        temper_core::SafeGenerator::from_fn({
                                            let b2 = b2.clone();
                                            let first_done2 = first_done2.clone();
                                            let v3 = v3.clone();
                                            let case_index4 = case_index4.clone();
                                            let awaited3 = awaited3.clone();
                                            Arc::new(
                                                move |generator: temper_core::SafeGenerator<()>| loop {
                                                    let case_index_local =
                                                        temper_core::read_locked(&case_index4);
                                                    *case_index4.write().unwrap() = -1;
                                                    match case_index_local {
                                                        0 => {
                                                            *awaited3.write().unwrap() = Some(
                                                                temper_core::read_locked(&b2)
                                                                    .as_ref()
                                                                    .unwrap()
                                                                    .promise(),
                                                            );
                                                            *case_index4.write().unwrap() = 3;
                                                            crate::support::awake_upon(
                                                                temper_core::read_locked(&awaited3)
                                                                    .unwrap(),
                                                                generator.clone(),
                                                            );
                                                            return Some(());
                                                        }
                                                        1 => {
                                                            *v3.write().unwrap() = -1;
                                                            *case_index4.write().unwrap() = 2;
                                                        }
                                                        2 => {
                                                            temper_core::read_locked(&first_done2)
                                                                .as_ref()
                                                                .unwrap()
                                                                .complete(temper_core::read_locked(
                                                                    &v3,
                                                                ));
                                                            return None;
                                                        }
                                                        3 => {
                                                            if (|| {
                                                                *v3.write().unwrap() =
                                                                    temper_core::read_locked(
                                                                        &awaited3,
                                                                    )
                                                                    .as_ref()
                                                                    .unwrap()
                                                                    .get()?;
                                                                *case_index4.write().unwrap() = 2;
                                                                Ok::<_, temper_core::BrokenPromise>(
                                                                    (),
                                                                )
                                                            })()
                                                            .is_err()
                                                            {
                                                                {
                                                                    *case_index4.write().unwrap() =
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
                                    let b2 = b2.clone();
                                    let second_done2 = second_done2.clone();
                                    Arc::new(move || {
                                        let case_index5: Arc<RwLock<i32>> =
                                            Arc::new(RwLock::new(0));
                                        let v4: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                                        let awaited4: Arc<
                                            RwLock<Option<temper_core::Promise<i32>>>,
                                        > = Arc::new(RwLock::new(None));
                                        temper_core::SafeGenerator::from_fn({
                                            let b2 = b2.clone();
                                            let second_done2 = second_done2.clone();
                                            let v4 = v4.clone();
                                            let case_index5 = case_index5.clone();
                                            let awaited4 = awaited4.clone();
                                            Arc::new(
                                                move |generator: temper_core::SafeGenerator<()>| loop {
                                                    let case_index_local =
                                                        temper_core::read_locked(&case_index5);
                                                    *case_index5.write().unwrap() = -1;
                                                    match case_index_local {
                                                        0 => {
                                                            *awaited4.write().unwrap() = Some(
                                                                temper_core::read_locked(&b2)
                                                                    .as_ref()
                                                                    .unwrap()
                                                                    .promise(),
                                                            );
                                                            *case_index5.write().unwrap() = 3;
                                                            crate::support::awake_upon(
                                                                temper_core::read_locked(&awaited4)
                                                                    .unwrap(),
                                                                generator.clone(),
                                                            );
                                                            return Some(());
                                                        }
                                                        1 => {
                                                            *v4.write().unwrap() = -1;
                                                            *case_index5.write().unwrap() = 2;
                                                        }
                                                        2 => {
                                                            temper_core::read_locked(&second_done2)
                                                                .as_ref()
                                                                .unwrap()
                                                                .complete(temper_core::read_locked(
                                                                    &v4,
                                                                ));
                                                            return None;
                                                        }
                                                        3 => {
                                                            if (|| {
                                                                *v4.write().unwrap() =
                                                                    temper_core::read_locked(
                                                                        &awaited4,
                                                                    )
                                                                    .as_ref()
                                                                    .unwrap()
                                                                    .get()?;
                                                                *case_index5.write().unwrap() = 2;
                                                                Ok::<_, temper_core::BrokenPromise>(
                                                                    (),
                                                                )
                                                            })()
                                                            .is_err()
                                                            {
                                                                {
                                                                    *case_index5.write().unwrap() =
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
                                    let b2 = b2.clone();
                                    Arc::new(move || {
                                        let case_index6: Arc<RwLock<i32>> =
                                            Arc::new(RwLock::new(0));
                                        temper_core::SafeGenerator::from_fn({
                                            let b2 = b2.clone();
                                            let case_index6 = case_index6.clone();
                                            Arc::new(
                                                move |_generator: temper_core::SafeGenerator<()>| {
                                                    let case_index_local =
                                                        temper_core::read_locked(&case_index6);
                                                    *case_index6.write().unwrap() = -1;
                                                    if case_index_local == 0 {
                                                        temper_core::read_locked(&b2)
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
                                *awaited7.write().unwrap() = Some(
                                    temper_core::read_locked(&first_done2)
                                        .as_ref()
                                        .unwrap()
                                        .promise(),
                                );
                                *case_index7.write().unwrap() = 6;
                                crate::support::awake_upon(
                                    temper_core::read_locked(&awaited7).unwrap(),
                                    generator2.clone(),
                                );
                                return Some(());
                            }
                            3 => {
                                'orelse: {
                                    *first.write().unwrap() = if let Ok(v) =
                                        temper_core::read_locked(&awaited5).as_ref().unwrap().get()
                                    {
                                        v
                                    } else {
                                        break 'orelse;
                                    };
                                    *awaited6.write().unwrap() = Some(
                                        temper_core::read_locked(&second_done)
                                            .as_ref()
                                            .unwrap()
                                            .promise(),
                                    );
                                    *case_index7.write().unwrap() = 4;
                                    crate::support::awake_upon(
                                        temper_core::read_locked(&awaited6).unwrap(),
                                        generator2.clone(),
                                    );
                                    return Some(());
                                }
                                {
                                    *case_index7.write().unwrap() = 1;
                                };
                            }
                            4 => {
                                if (|| {
                                    *second.write().unwrap() = temper_core::read_locked(&awaited6)
                                        .as_ref()
                                        .unwrap()
                                        .get()?;
                                    println!(
                                        "after complete: first got {}, second got {}",
                                        temper_core::read_locked(&first),
                                        temper_core::read_locked(&second)
                                    );
                                    *case_index7.write().unwrap() = 2;
                                    Ok::<_, temper_core::BrokenPromise>(())
                                })()
                                .is_err()
                                {
                                    {
                                        *case_index7.write().unwrap() = 1;
                                    };
                                }
                            }
                            5 => {
                                panic!();
                            }
                            6 => {
                                'orelse: {
                                    *first2.write().unwrap() = if let Ok(v) =
                                        temper_core::read_locked(&awaited7).as_ref().unwrap().get()
                                    {
                                        v
                                    } else {
                                        break 'orelse;
                                    };
                                    *awaited8.write().unwrap() = Some(
                                        temper_core::read_locked(&second_done2)
                                            .as_ref()
                                            .unwrap()
                                            .promise(),
                                    );
                                    *case_index7.write().unwrap() = 7;
                                    crate::support::awake_upon(
                                        temper_core::read_locked(&awaited8).unwrap(),
                                        generator2.clone(),
                                    );
                                    return Some(());
                                }
                                {
                                    *case_index7.write().unwrap() = 5;
                                };
                            }
                            7 => {
                                if (|| {
                                    *second2.write().unwrap() = temper_core::read_locked(&awaited8)
                                        .as_ref()
                                        .unwrap()
                                        .get()?;
                                    println!(
                                        "after breakPromise: first got {}, second got {}",
                                        temper_core::read_locked(&first2),
                                        temper_core::read_locked(&second2)
                                    );
                                    *case_index7.write().unwrap() = -1;
                                    Ok::<_, temper_core::BrokenPromise>(())
                                })()
                                .is_err()
                                {
                                    {
                                        *case_index7.write().unwrap() = 5;
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
