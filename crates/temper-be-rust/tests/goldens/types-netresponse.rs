use std::sync::{Arc, OnceLock, RwLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            URL.set(Arc::new(format!("http://127.0.0.1:{}", 28461i32)))
                .unwrap_or_else(|_| panic!());
            crate::support::run_async(Arc::new(move || {
                let case_index: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                let req: Arc<RwLock<Option<temper_std::net::NetRequest>>> =
                    Arc::new(RwLock::new(None));
                let resp: Arc<RwLock<Option<temper_std::net::NetResponse>>> =
                    Arc::new(RwLock::new(None));
                let body: Arc<RwLock<Arc<String>>> = Arc::new(RwLock::new(Arc::new(String::new())));
                let subject: Arc<RwLock<Option<Arc<String>>>> = Arc::new(RwLock::new(None));
                let awaited: Arc<
                    RwLock<Option<temper_core::Promise<temper_std::net::NetResponse>>>,
                > = Arc::new(RwLock::new(None));
                let awaited2: Arc<RwLock<Option<temper_core::Promise<Option<Arc<String>>>>>> =
                    Arc::new(RwLock::new(None));
                temper_core::SafeGenerator::from_fn({
                    let req = req.clone();
                    let resp = resp.clone();
                    let body = body.clone();
                    let subject = subject.clone();
                    let case_index = case_index.clone();
                    let awaited = awaited.clone();
                    let awaited2 = awaited2.clone();
                    Arc::new(move |generator: temper_core::SafeGenerator<()>| loop {
                        let case_index_local = temper_core::read_locked(&case_index);
                        *case_index.write().unwrap() = -1;
                        match case_index_local {
                            0 => {
                                *req.write().unwrap() =
                                    Some(temper_std::net::NetRequest::new(url()));
                                temper_core::read_locked(&req)
                                    .as_ref()
                                    .unwrap()
                                    .post("[]", "application/json");
                                *awaited.write().unwrap() =
                                    Some(temper_core::read_locked(&req).as_ref().unwrap().send());
                                *case_index.write().unwrap() = 2;
                                crate::support::awake_upon(
                                    temper_core::read_locked(&awaited).unwrap(),
                                    generator.clone(),
                                );
                                return Some(());
                            }
                            1 => {
                                println!("failed");
                                *case_index.write().unwrap() = -1;
                            }
                            2 => {
                                if (|| {
                                    *resp.write().unwrap() = Some(
                                        temper_core::read_locked(&awaited)
                                            .as_ref()
                                            .unwrap()
                                            .get()?,
                                    );
                                    if temper_std::net::NetResponseTrait::status(
                                        temper_core::read_locked(&resp).as_ref().unwrap(),
                                    ) == 200
                                    {
                                        *case_index.write().unwrap() = 3;
                                    } else {
                                        *case_index.write().unwrap() = 4;
                                    }
                                    Ok::<_, temper_core::BrokenPromise>(())
                                })()
                                .is_err()
                                {
                                    {
                                        *case_index.write().unwrap() = 1;
                                    };
                                }
                            }
                            3 => {
                                *awaited2.write().unwrap() =
                                    Some(temper_std::net::NetResponseTrait::body_content(
                                        temper_core::read_locked(&resp).as_ref().unwrap(),
                                    ));
                                *case_index.write().unwrap() = 5;
                                crate::support::awake_upon(
                                    temper_core::read_locked(&awaited2).unwrap(),
                                    generator.clone(),
                                );
                                return Some(());
                            }
                            4 => {
                                {
                                    println!(
                                        "HTTP status was not 200: {}!",
                                        temper_std::net::NetResponseTrait::status(
                                            temper_core::read_locked(&resp).as_ref().unwrap(),
                                        )
                                    );
                                }
                                *case_index.write().unwrap() = -1;
                            }
                            5 => {
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
                                    println!(
                                        "Thanks, server.  I got {}.",
                                        temper_core::read_locked(&body)
                                    );
                                    *case_index.write().unwrap() = -1;
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
            Ok(())
        })
        .clone()
}

static URL: OnceLock<Arc<String>> = OnceLock::new();

pub(crate) fn url() -> Arc<String> {
    crate::support::initialized(|| URL.get()).clone()
}
