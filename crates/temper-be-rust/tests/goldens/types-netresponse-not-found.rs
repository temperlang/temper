use std::sync::{Arc, OnceLock, RwLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            URL.set(Arc::new(format!("http://127.0.0.1:{}/missing", 28461i32)))
                .unwrap_or_else(|_| panic!());
            crate::support::run_async(Arc::new(move || {
                let case_index: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                let resp: Arc<RwLock<Option<temper_std::net::NetResponse>>> =
                    Arc::new(RwLock::new(None));
                let body: Arc<RwLock<Arc<String>>> = Arc::new(RwLock::new(Arc::new(String::new())));
                let subject: Arc<RwLock<Option<Arc<String>>>> = Arc::new(RwLock::new(None));
                let awaited: Arc<
                    RwLock<Option<temper_core::Promise<temper_std::net::NetResponse>>>,
                > = Arc::new(RwLock::new(None));
                let awaited2: Arc<RwLock<Option<temper_core::Promise<Option<Arc<String>>>>>> =
                    Arc::new(RwLock::new(None));
                let e: Arc<RwLock<Option<temper_core::BrokenPromise>>> =
                    Arc::new(RwLock::new(None));
                let awaited3: Arc<
                    RwLock<Option<temper_core::Promise<temper_std::net::NetResponse>>>,
                > = Arc::new(RwLock::new(None));
                temper_core::SafeGenerator::from_fn({
                    let resp = resp.clone();
                    let body = body.clone();
                    let subject = subject.clone();
                    let e = e.clone();
                    let case_index = case_index.clone();
                    let awaited = awaited.clone();
                    let awaited2 = awaited2.clone();
                    let awaited3 = awaited3.clone();
                    Arc::new(move |generator: temper_core::SafeGenerator<()>| loop {
                        let case_index_local = temper_core::read_locked(&case_index);
                        *case_index.write().unwrap() = -1;
                        match case_index_local {
                            0 => {
                                *awaited.write().unwrap() =
                                    Some(temper_std::net::NetRequest::new(url()).send());
                                *case_index.write().unwrap() = 3;
                                crate::support::awake_upon(
                                    temper_core::read_locked(&awaited).unwrap(),
                                    generator.clone(),
                                );
                                return Some(());
                            }
                            1 => {
                                println!("broken promise");
                                *case_index.write().unwrap() = 2;
                            }
                            2 => {
                                *awaited3.write().unwrap() = Some(
                                    temper_std::net::NetRequest::new("http://127.0.0.1:1/").send(),
                                );
                                *case_index.write().unwrap() = 6;
                                crate::support::awake_upon(
                                    temper_core::read_locked(&awaited3).unwrap(),
                                    generator.clone(),
                                );
                                return Some(());
                            }
                            3 => {
                                'orelse: {
                                    *resp.write().unwrap() = Some(
                                        if let Ok(v) = temper_core::read_locked(&awaited)
                                            .as_ref()
                                            .unwrap()
                                            .get()
                                        {
                                            v
                                        } else {
                                            break 'orelse;
                                        },
                                    );
                                    println!(
                                        "status {}",
                                        temper_std::net::NetResponseTrait::status(
                                            temper_core::read_locked(&resp).as_ref().unwrap(),
                                        )
                                    );
                                    *awaited2.write().unwrap() =
                                        Some(temper_std::net::NetResponseTrait::body_content(
                                            temper_core::read_locked(&resp).as_ref().unwrap(),
                                        ));
                                    *case_index.write().unwrap() = 4;
                                    crate::support::awake_upon(
                                        temper_core::read_locked(&awaited2).unwrap(),
                                        generator.clone(),
                                    );
                                    return Some(());
                                }
                                {
                                    *case_index.write().unwrap() = 1;
                                };
                            }
                            4 => {
                                if (|| {
                                    *subject.write().unwrap() = temper_core::read_locked(&awaited2)
                                        .as_ref()
                                        .unwrap()
                                        .get()?;
                                    *body.write().unwrap() =
                                        if temper_core::read_locked(&subject).is_none() {
                                            Arc::new("missing".to_string())
                                        } else {
                                            temper_core::read_locked(&subject).unwrap()
                                        };
                                    println!("body {}", temper_core::read_locked(&body));
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
                            5 => {
                                {
                                    println!(
                                        "no response from port 1: {}",
                                        temper_core::read_locked(&e).as_ref().unwrap().message()
                                    );
                                };
                                *case_index.write().unwrap() = -1;
                            }
                            6 => {
                                if let Err(failure) = (|| {
                                    temper_core::read_locked(&awaited3)
                                        .as_ref()
                                        .unwrap()
                                        .get()?;
                                    println!("port 1 answered");
                                    *case_index.write().unwrap() = -1;
                                    Ok::<_, temper_core::BrokenPromise>(())
                                })() {
                                    *e.write().unwrap() = Some(failure.clone());
                                    *case_index.write().unwrap() = 5;
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

static URL: OnceLock<Arc<String>> = OnceLock::new();

pub(crate) fn url() -> Arc<String> {
    crate::support::initialized(|| URL.get()).clone()
}
