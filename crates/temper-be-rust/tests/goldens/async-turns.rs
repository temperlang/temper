use std::sync::{Arc, OnceLock, RwLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let t: temper_core::SafeGenerator<()> = ticks(
                2,
                Arc::new(move |i: i32| {
                    println!("tick {i}");
                }),
            );
            t.next_safe();
            t.next_safe();
            t.next_safe();
            println!("fibonacci {}", shown(take(fibonacci(), 10)));
            println!("collatz {}", shown(take(collatz(6), 100)));
            let fib: temper_core::SafeGenerator<i32> = fibonacci();
            take(fib.clone(), 5);
            println!("then {}", shown(take(fib.clone(), 3)));
            C.set(Counter::new()).unwrap_or_else(|_| panic!());
            GATE_A
                .set(temper_core::PromiseBuilder::new())
                .unwrap_or_else(|_| panic!());
            GATE_B
                .set(temper_core::PromiseBuilder::new())
                .unwrap_or_else(|_| panic!());
            crate::support::run_async(Arc::new(move || {
                let case_index: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                let k: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                let awaited: Arc<RwLock<Option<temper_core::Promise<i32>>>> =
                    Arc::new(RwLock::new(None));
                temper_core::SafeGenerator::from_fn({
                    let k = k.clone();
                    let case_index = case_index.clone();
                    let awaited = awaited.clone();
                    Arc::new(move |generator: temper_core::SafeGenerator<()>| loop {
                        let case_index_local = temper_core::read_locked(&case_index);
                        *case_index.write().unwrap() = -1;
                        match case_index_local {
                            0 => {
                                c().bump("A", 10000);
                                *case_index.write().unwrap() = 3;
                            }
                            1 => {
                                *k.write().unwrap() = -1;
                                *case_index.write().unwrap() = 2;
                            }
                            2 => {
                                c().bump(
                                    Arc::new(format!("A{}", temper_core::read_locked(&k))),
                                    10000,
                                );
                                return None;
                            }
                            3 => {
                                *awaited.write().unwrap() = Some(gate_a().promise());
                                *case_index.write().unwrap() = 4;
                                crate::support::awake_upon(
                                    temper_core::read_locked(&awaited).unwrap(),
                                    generator.clone(),
                                );
                                return Some(());
                            }
                            4 => {
                                if (|| {
                                    *k.write().unwrap() = temper_core::read_locked(&awaited)
                                        .as_ref()
                                        .unwrap()
                                        .get()?;
                                    *case_index.write().unwrap() = 2;
                                    Ok::<_, temper_core::Error>(())
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
            crate::support::run_async(Arc::new(move || {
                let case_index: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                let k: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                let awaited: Arc<RwLock<Option<temper_core::Promise<i32>>>> =
                    Arc::new(RwLock::new(None));
                temper_core::SafeGenerator::from_fn({
                    let k = k.clone();
                    let case_index = case_index.clone();
                    let awaited = awaited.clone();
                    Arc::new(move |generator: temper_core::SafeGenerator<()>| loop {
                        let case_index_local = temper_core::read_locked(&case_index);
                        *case_index.write().unwrap() = -1;
                        match case_index_local {
                            0 => {
                                c().bump("B", 10000);
                                *case_index.write().unwrap() = 3;
                            }
                            1 => {
                                *k.write().unwrap() = -1;
                                *case_index.write().unwrap() = 2;
                            }
                            2 => {
                                c().bump(
                                    Arc::new(format!("B{}", temper_core::read_locked(&k))),
                                    10000,
                                );
                                return None;
                            }
                            3 => {
                                *awaited.write().unwrap() = Some(gate_b().promise());
                                *case_index.write().unwrap() = 4;
                                crate::support::awake_upon(
                                    temper_core::read_locked(&awaited).unwrap(),
                                    generator.clone(),
                                );
                                return Some(());
                            }
                            4 => {
                                if (|| {
                                    *k.write().unwrap() = temper_core::read_locked(&awaited)
                                        .as_ref()
                                        .unwrap()
                                        .get()?;
                                    *case_index.write().unwrap() = 2;
                                    Ok::<_, temper_core::Error>(())
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
            crate::support::run_async(Arc::new(move || {
                let case_index: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                temper_core::SafeGenerator::from_fn({
                    let case_index = case_index.clone();
                    Arc::new(move |_generator: temper_core::SafeGenerator<()>| {
                        let case_index_local = temper_core::read_locked(&case_index);
                        *case_index.write().unwrap() = -1;
                        if case_index_local == 0 {
                            println!("opener: calling complete");
                            gate_a().complete(1);
                            gate_b().complete(2);
                            println!("opener: complete returned");
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

pub fn later(n: i32) -> temper_core::Promise<i32> {
    let pb: temper_core::PromiseBuilder<i32> = temper_core::PromiseBuilder::new();
    crate::support::run_async({
        let pb = pb.clone();
        Arc::new(move || {
            let case_index: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
            temper_core::SafeGenerator::from_fn({
                let pb = pb.clone();
                let case_index = case_index.clone();
                Arc::new(move |_generator: temper_core::SafeGenerator<()>| {
                    let case_index_local = temper_core::read_locked(&case_index);
                    *case_index.write().unwrap() = -1;
                    if case_index_local == 0 {
                        pb.complete(n.wrapping_mul(2));
                        None
                    } else {
                        None
                    }
                })
            })
        })
    });
    pb.promise()
}

pub(crate) fn generated<T: Clone + Send + Sync + 'static>(
    make: Arc<dyn Fn() -> temper_core::SafeGenerator<T> + Send + Sync>,
) -> temper_core::SafeGenerator<T> {
    make()
}

pub fn ticks(n: i32, each: Arc<dyn Fn(i32) + Send + Sync>) -> temper_core::SafeGenerator<()> {
    generated({
        let each = each.clone();
        Arc::new(move || {
            let case_index: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
            let i: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
            temper_core::SafeGenerator::from_fn({
                let each = each.clone();
                let i = i.clone();
                let case_index = case_index.clone();
                Arc::new(move |_generator: temper_core::SafeGenerator<()>| loop {
                    let case_index_local = temper_core::read_locked(&case_index);
                    *case_index.write().unwrap() = -1;
                    match case_index_local {
                        0 => {
                            *i.write().unwrap() = 0;
                            *case_index.write().unwrap() = 1;
                        }
                        1 => {
                            if temper_core::read_locked(&i) < n {
                                *case_index.write().unwrap() = 2;
                            } else {
                                *case_index.write().unwrap() = -1;
                            }
                        }
                        2 => {
                            each(temper_core::read_locked(&i));
                            *case_index.write().unwrap() = 3;
                            return Some(());
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
        })
    })
}

pub(crate) fn started_at<T: Clone + Send + Sync + 'static>(
    start: i32,
    make: Arc<dyn Fn(i32) -> temper_core::SafeGenerator<T> + Send + Sync>,
) -> temper_core::SafeGenerator<T> {
    make(start)
}

pub fn fibonacci() -> temper_core::SafeGenerator<i32> {
    generated(Arc::new(move || {
        let case_index: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
        let a: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
        let b: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
        let next: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
        temper_core::SafeGenerator::from_fn({
            let a = a.clone();
            let b = b.clone();
            let next = next.clone();
            let case_index = case_index.clone();
            Arc::new(move |_generator: temper_core::SafeGenerator<i32>| loop {
                let case_index_local = temper_core::read_locked(&case_index);
                *case_index.write().unwrap() = -1;
                match case_index_local {
                    0 => {
                        *a.write().unwrap() = 0;
                        *b.write().unwrap() = 1;
                        *case_index.write().unwrap() = 1;
                    }
                    1 => {
                        *case_index.write().unwrap() = 2;
                        return Some(temper_core::read_locked(&a));
                    }
                    2 => {
                        *next.write().unwrap() =
                            temper_core::read_locked(&a).wrapping_add(temper_core::read_locked(&b));
                        *a.write().unwrap() = temper_core::read_locked(&b);
                        *b.write().unwrap() = temper_core::read_locked(&next);
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

pub fn collatz(start: i32) -> temper_core::SafeGenerator<i32> {
    started_at(
        start,
        Arc::new(move |first: i32| {
            let case_index: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
            let n: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
            temper_core::SafeGenerator::from_fn({
                let n = n.clone();
                let case_index = case_index.clone();
                Arc::new(move |_generator: temper_core::SafeGenerator<i32>| loop {
                    let case_index_local = temper_core::read_locked(&case_index);
                    *case_index.write().unwrap() = -1;
                    match case_index_local {
                        0 => {
                            *n.write().unwrap() = first;
                            *case_index.write().unwrap() = 1;
                        }
                        1 => {
                            *case_index.write().unwrap() = 2;
                            return Some(temper_core::read_locked(&n));
                        }
                        2 => {
                            if temper_core::read_locked(&n) == 1 {
                                {
                                    *case_index.write().unwrap() = -1;
                                    continue;
                                }
                            }
                            if temper_core::read_locked(&n).wrapping_rem(2) == 0 {
                                *n.write().unwrap() = temper_core::read_locked(&n).wrapping_div(2);
                            } else {
                                *n.write().unwrap() = 3i32
                                    .wrapping_mul(temper_core::read_locked(&n))
                                    .wrapping_add(1);
                            }
                            *case_index.write().unwrap() = 1;
                        }
                        _ => {
                            return None;
                        }
                    }
                })
            })
        }),
    )
}

pub(crate) fn take(g: temper_core::SafeGenerator<i32>, n: i32) -> temper_core::List<i32> {
    let taken: temper_core::ListBuilder<i32> = temper_core::listed::new_builder();
    while temper_core::ListedTrait::len(&taken) < n {
        let r: Option<i32> = g.next_safe();
        if let Some(r) = r {
            temper_core::listed::add(&taken, r, None);
        } else {
            break;
        }
    }
    temper_core::ListedTrait::to_list(&taken)
}

pub(crate) fn shown(ints: impl temper_core::ToList<i32>) -> Arc<String> {
    let ints = ints.to_list();
    temper_core::listed::join(
        &ints,
        Arc::new(" ".to_string()),
        &(|i: i32| Arc::new(i.to_string())),
    )
}

struct CounterStruct {
    n: RwLock<i32>,
}

#[derive(Clone)]
pub(crate) struct Counter(Arc<CounterStruct>);

impl Counter {
    pub fn new() -> Counter {
        Counter(Arc::new(CounterStruct { n: RwLock::new(0) }))
    }

    pub fn bump(&self, tag: impl ToArcString, times: i32) {
        let tag = tag.to_arc_string();
        println!("{tag} start");
        {
            let mut i: i32 = 0;
            while i < times {
                let before = self.n();
                *self.0.n.write().unwrap() = before.wrapping_add(1);
                i = i.wrapping_add(1);
            }
        }
        println!("{tag} done n={}", self.n());
    }

    pub fn n(&self) -> i32 {
        *self.0.n.read().unwrap()
    }
}

temper_core::impl_any_value_trait!(Counter, []);

static C: OnceLock<Counter> = OnceLock::new();

pub(crate) fn c() -> Counter {
    crate::support::initialized(|| C.get()).clone()
}

static GATE_A: OnceLock<temper_core::PromiseBuilder<i32>> = OnceLock::new();

pub(crate) fn gate_a() -> temper_core::PromiseBuilder<i32> {
    crate::support::initialized(|| GATE_A.get()).clone()
}

static GATE_B: OnceLock<temper_core::PromiseBuilder<i32>> = OnceLock::new();

pub(crate) fn gate_b() -> temper_core::PromiseBuilder<i32> {
    crate::support::initialized(|| GATE_B.get()).clone()
}

pub fn halve(n: i32) -> temper_core::Result<i32> {
    if n.wrapping_rem(2) != 0 {
        return Err(temper_core::Error::new());
    }
    Ok(n.wrapping_div(2))
}
