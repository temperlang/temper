use std::sync::{Arc, OnceLock, RwLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            println!("{}", take(up_to(3), 9));
            let one: temper_core::Generator<i32> = up_to(1);
            println!("{}", take(one.clone(), 9));
            println!("{}", take(one.clone(), 9));
            println!("{}", take(squares(10), 9));
            println!("{}", take(temper_core::Generator::from(count(2)), 9));
            println!("{}", first_two(count(1)));
            println!("{}", take(doubled(up_to(2)), 9));
            println!(
                "{}",
                take(doubled(temper_core::Generator::from(count(2))), 9)
            );
            Ok(())
        })
        .clone()
}

#[derive(Clone, Debug)]
pub(crate) enum FailureGeneratorsError {
    TooBig(TooBig),
}

temper_core::impl_failure_enum!(FailureGeneratorsError, TooBig(TooBig));

struct TooBigStruct {
    n: i32,
    cause: temper_core::Cause,
}

#[derive(Clone)]
pub(crate) struct TooBig(Arc<TooBigStruct>);

impl TooBig {
    pub fn new(n: i32) -> TooBig {
        TooBig(Arc::new(TooBigStruct {
            n,
            cause: temper_core::Cause::new(),
        }))
    }

    pub fn message(&self) -> Arc<String> {
        Arc::new(format!("too big (n: {})", self.0.n))
    }
}

temper_core::impl_failure!(TooBig, message, cause);

temper_core::impl_any_value_trait!(TooBig, [FailureGeneratorsError]);

pub(crate) fn generated<T: Clone + Send + Sync + 'static>(
    make: Arc<dyn Fn() -> temper_core::Generator<T> + Send + Sync>,
) -> temper_core::Generator<T> {
    make()
}

pub(crate) fn safely<T: Clone + Send + Sync + 'static>(
    make: Arc<dyn Fn() -> temper_core::SafeGenerator<T> + Send + Sync>,
) -> temper_core::SafeGenerator<T> {
    make()
}

pub(crate) fn take(g: temper_core::Generator<i32>, n: i32) -> Arc<String> {
    let taken: temper_core::ListBuilder<Arc<String>> = temper_core::listed::new_builder();
    while temper_core::ListedTrait::len(&taken) < n {
        let mut failed = false;
        let mut caught: Option<Arc<dyn temper_core::Failure>> = None;
        let mut held: Option<Option<i32>> = None;
        if let Err(failure) = (|| {
            held = Some(g.next()?);
            Ok::<_, Arc<dyn temper_core::Failure>>(())
        })() {
            caught = Some(failure.clone());
            failed = true;
        }
        let r: Option<i32> = if failed {
            let e = caught.clone().unwrap();
            let why = if temper_core::is::<TooBig>(e.clone()) {
                Arc::new(format!(
                    "too big {}",
                    temper_core::cast::<TooBig>(e.clone()).unwrap().0.n
                ))
            } else {
                e.message()
            };
            return Arc::new(format!(
                "{} {why}",
                temper_core::listed::join(
                    &taken,
                    Arc::new(" ".to_string()),
                    &(|s: Arc<String>| s.clone()),
                )
            ));
        } else {
            held.unwrap()
        };
        if let Some(r) = r {
            temper_core::listed::add(&taken, Arc::new(r.to_string()), None);
        } else {
            temper_core::listed::add(&taken, Arc::new("done".to_string()), None);
            break;
        }
    }
    temper_core::listed::join(
        &taken,
        Arc::new(" ".to_string()),
        &(|s: Arc<String>| s.clone()),
    )
}

pub(crate) fn up_to(limit: i32) -> temper_core::Generator<i32> {
    generated(Arc::new(move || {
        let case_index: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
        let i: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
        temper_core::Generator::from_fn({
            let i = i.clone();
            let case_index = case_index.clone();
            Arc::new(move |_generator: temper_core::Generator<i32>| loop {
                let case_index_local = temper_core::read_locked(&case_index);
                *case_index.write().unwrap() = -1;
                match case_index_local {
                    0 => {
                        *i.write().unwrap() = 1;
                        *case_index.write().unwrap() = 1;
                    }
                    1 => {
                        if temper_core::read_locked(&i) > limit {
                            return Err(TooBig::new(temper_core::read_locked(&i)).into());
                        }
                        *case_index.write().unwrap() = 2;
                        return Ok(Some(temper_core::read_locked(&i)));
                    }
                    2 => {
                        *i.write().unwrap() = temper_core::read_locked(&i).wrapping_add(1);
                        *case_index.write().unwrap() = 1;
                    }
                    _ => {
                        return Ok(None);
                    }
                }
            })
        })
    }))
}

pub(crate) fn checked(n: i32, limit: i32) -> Result<i32, TooBig> {
    if n > limit {
        return Err(TooBig::new(n));
    }
    Ok(n)
}

pub(crate) fn squares(limit: i32) -> temper_core::Generator<i32> {
    generated(Arc::new(move || {
        let case_index: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
        let i: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
        temper_core::Generator::from_fn({
            let i = i.clone();
            let case_index = case_index.clone();
            Arc::new(move |_generator: temper_core::Generator<i32>| loop {
                let case_index_local = temper_core::read_locked(&case_index);
                *case_index.write().unwrap() = -1;
                match case_index_local {
                    0 => {
                        *case_index.write().unwrap() = 1;
                        return Ok(Some(checked(50, limit).unwrap_or(0)));
                    }
                    1 => {
                        *i.write().unwrap() = 1;
                        *case_index.write().unwrap() = 2;
                    }
                    2 => {
                        *case_index.write().unwrap() = 3;
                        return Ok(Some(checked(
                            temper_core::read_locked(&i).wrapping_mul(temper_core::read_locked(&i)),
                            limit,
                        )?,));
                    }
                    3 => {
                        *i.write().unwrap() = temper_core::read_locked(&i).wrapping_add(1);
                        *case_index.write().unwrap() = 2;
                    }
                    _ => {
                        return Ok(None);
                    }
                }
            })
        })
    }))
}

pub(crate) fn count(n: i32) -> temper_core::SafeGenerator<i32> {
    safely(Arc::new(move || {
        let case_index: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
        let i: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
        temper_core::SafeGenerator::from_fn({
            let i = i.clone();
            let case_index = case_index.clone();
            Arc::new(move |_generator: temper_core::SafeGenerator<i32>| loop {
                let case_index_local = temper_core::read_locked(&case_index);
                *case_index.write().unwrap() = -1;
                match case_index_local {
                    0 => {
                        *i.write().unwrap() = 1;
                        *case_index.write().unwrap() = 1;
                    }
                    1 => {
                        if temper_core::read_locked(&i) <= n {
                            *case_index.write().unwrap() = 2;
                        } else {
                            *case_index.write().unwrap() = -1;
                        }
                    }
                    2 => {
                        *case_index.write().unwrap() = 3;
                        return Some(temper_core::read_locked(&i));
                    }
                    3 => {
                        *i.write().unwrap() = temper_core::read_locked(&i).wrapping_add(1);
                        *case_index.write().unwrap() = 1;
                    }
                    _ => {
                        return None;
                    }
                }
            })
        })
    }))
}

pub(crate) fn shown(r: Option<i32>) -> Arc<String> {
    if let Some(r) = r {
        Arc::new(r.to_string())
    } else {
        Arc::new("done".to_string())
    }
}

pub(crate) fn first_two(g: temper_core::SafeGenerator<i32>) -> Arc<String> {
    let a = shown(g.next_safe());
    let b = (|| {
        Ok::<_, Arc<dyn temper_core::Failure>>(shown(
            temper_core::Generator::from(g.clone()).next()?,
        ))
    })()
    .unwrap_or_else(|_| Arc::new("failed".to_string()));
    Arc::new(format!("{a} {b}"))
}

pub(crate) fn doubled(inner: temper_core::Generator<i32>) -> temper_core::Generator<i32> {
    generated({
        let inner = inner.clone();
        Arc::new(move || {
            let case_index: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
            let r: Arc<RwLock<Option<Option<i32>>>> = Arc::new(RwLock::new(None));
            temper_core::Generator::from_fn({
                let inner = inner.clone();
                let r = r.clone();
                let case_index = case_index.clone();
                Arc::new(move |_generator: temper_core::Generator<i32>| loop {
                    let case_index_local = temper_core::read_locked(&case_index);
                    *case_index.write().unwrap() = -1;
                    match case_index_local {
                        0 => {
                            *case_index.write().unwrap() = 1;
                        }
                        1 => {
                            *r.write().unwrap() = Some(inner.next()?);
                            if temper_core::read_locked(&r).unwrap().is_some() {
                                *case_index.write().unwrap() = 2;
                            } else {
                                *case_index.write().unwrap() = 3;
                            }
                        }
                        2 => {
                            *case_index.write().unwrap() = 1;
                            return Ok(Some(
                                temper_core::read_locked(&r)
                                    .unwrap()
                                    .unwrap()
                                    .wrapping_mul(2),
                            ));
                        }
                        3 => {
                            {
                                {
                                    *case_index.write().unwrap() = -1;
                                    continue;
                                }
                            }
                        }
                        _ => {
                            return Ok(None);
                        }
                    }
                })
            })
        })
    })
}
