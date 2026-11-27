use std::convert::Infallible;
use std::sync::{Arc, OnceLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let doubled: temper_core::List<i32> = temper_core::into_ok(map_all::<_, _, Infallible>(
                Arc::new(vec![1, 2, 3]),
                Arc::new(move |n: i32| Ok(n.wrapping_mul(2))),
            ));
            let safe: Arc<dyn Fn(i32) -> Result<i32, Infallible> + Send + Sync> =
                Arc::new(move |n: i32| Ok(n.wrapping_add(1)));
            println!(
                "{} {}",
                temper_core::listed::join(
                    &doubled,
                    Arc::new(",".to_string()),
                    &(|n: i32| Arc::new(n.to_string())),
                ),
                temper_core::into_ok(safe(1))
            );
            let named: temper_core::List<i32> = map_all::<_, _, Missing>(
                Arc::new(vec![
                    Arc::new("two".to_string()),
                    Arc::new("one".to_string()),
                ]),
                Arc::new(|a0: Arc<String>| find(a0)),
            )
            .unwrap_or_else(|_| Arc::new(vec![]));
            println!(
                "{} {} {}",
                shown(Arc::new(vec![
                    Arc::new("one".to_string()),
                    Arc::new("two".to_string()),
                ])),
                shown(Arc::new(vec![
                    Arc::new("one".to_string()),
                    Arc::new("six".to_string()),
                    Arc::new("ten".to_string()),
                ])),
                temper_core::ListedTrait::len(&named)
            );
            let six = applied(Arc::new(move |n: i32| Ok(n.wrapping_mul(3))), 2).unwrap_or(-1);
            println!("{six}");
            let some = failures::<_, _, Missing>(
                Arc::new(vec![
                    Arc::new("one".to_string()),
                    Arc::new("six".to_string()),
                    Arc::new("two".to_string()),
                    Arc::new("ten".to_string()),
                ]),
                Arc::new(|a0: Arc<String>| find(a0)),
            );
            let none =
                failures::<_, _, Infallible>(Arc::new(vec![1, 2]), Arc::new(move |n: i32| Ok(n)));
            println!("{some} {none}");
            let summed = sum_all::<_, Missing>(
                Arc::new(vec![
                    Arc::new("one".to_string()),
                    Arc::new("two".to_string()),
                ]),
                Arc::new(|a0: Arc<String>| find(a0)),
            )
            .unwrap_or(-1);
            let unsummed = sum_all::<_, Missing>(
                Arc::new(vec![
                    Arc::new("six".to_string()),
                    Arc::new("one".to_string()),
                    Arc::new("ten".to_string()),
                ]),
                Arc::new(|a0: Arc<String>| find(a0)),
            )
            .unwrap_or_else(|e| {
                println!("first missing {}", e.0.key);
                -1
            });
            println!(
                "{summed} {unsummed} {}",
                temper_core::into_ok(sum_all::<_, Infallible>(Arc::new(vec![1, 2]), safe.clone()))
            );
            let twelve = both::<Missing>(
                Arc::new(|a0: Arc<String>| find(a0)),
                Arc::new(move |_k: Arc<String>| Ok(10)),
                "two",
            )
            .unwrap_or(-1);
            let lost = both::<Missing>(
                Arc::new(|a0: Arc<String>| find(a0)),
                Arc::new(|a0: Arc<String>| find(a0)),
                "six",
            )
            .unwrap_or_else(|e2| {
                println!("lost {}", e2.0.key);
                -1
            });
            println!("{twelve} {lost}");
            println!("{} {}", either("one"), either("ten"));
            println!(
                "{}",
                parts(Arc::new(vec![
                    Arc::new("one".to_string()),
                    Arc::new("two".to_string()),
                ]))
                .unwrap_or_else(|_| Arc::new("?".to_string()))
            );
            println!(
                "{}",
                parts(Arc::new(vec![
                    Arc::new("one".to_string()),
                    Arc::new("six".to_string()),
                ]))
                .unwrap_or_else(|e3| Arc::new(format!("missing {}", e3.0.key)))
            );
            println!(
                "{}",
                temper_core::listed::join(
                    &temper_core::listed::sorted(
                        &Arc::new(vec![3, 1, 2]),
                        &(|a: i32, b: i32| a.wrapping_sub(b)),
                    ),
                    Arc::new(",".to_string()),
                    &(|n: i32| Arc::new(n.to_string())),
                )
            );
            let bumped: temper_core::List<i32> = temper_core::into_ok(
                temper_core::listed::try_map::<_, _, Infallible>(&Arc::new(vec![1, 2]), &*safe),
            );
            let twice: temper_core::List<i32> = temper_core::into_ok(map_twice::<_, Infallible>(
                Arc::new(vec![1, 2]),
                safe.clone(),
            ));
            let failed: temper_core::List<i32> =
                map_twice::<_, Missing>(Arc::new(vec![1, 2]), Arc::new(up_to_two)).unwrap_or_else(
                    |e4| {
                        println!("missing {}", e4.0.key);
                        Arc::new(vec![])
                    },
                );
            println!(
                "{} {} {}",
                temper_core::listed::join(
                    &bumped,
                    Arc::new(",".to_string()),
                    &(|n: i32| Arc::new(n.to_string())),
                ),
                temper_core::listed::join(
                    &twice,
                    Arc::new(",".to_string()),
                    &(|n: i32| Arc::new(n.to_string())),
                ),
                temper_core::ListedTrait::len(&failed)
            );
            let all_keyed = keyed("one+two").unwrap_or_else(|_| Arc::new("?".to_string()));
            let none_keyed =
                keyed("one+six+ten").unwrap_or_else(|e5| Arc::new(format!("missing {}", e5.0.key)));
            let plain = temper_core::into_ok(
                temper_std::regex::RegexNodeTrait::compiled(
                    &temper_std::regex::CodePoints::new("o"),
                )
                .replace::<Infallible>(
                    "two",
                    Arc::new(move |_m: temper_std::regex::Match| Ok(Arc::new("0".to_string()))),
                ),
            );
            println!(
                "{all_keyed} {none_keyed} {plain} {}",
                temper_core::into_ok(
                    temper_std::regex::RegexNodeTrait::compiled(&temper_std::regex::CodePoints::new(
                        "w",
                    ))
                    .replace::<Infallible>(
                        "two",
                        Arc::new(|a0: temper_std::regex::Match| Ok(bracketed(a0))),
                    ),
                )
            );
            Ok(())
        })
        .clone()
}

#[derive(Clone, Debug)]
pub(crate) enum FailureParamsError {
    Missing(Missing),
    Unknown(Unknown),
}

temper_core::impl_failure_enum!(FailureParamsError, Missing(Missing), Unknown(Unknown));

struct MissingStruct {
    key: Arc<String>,
    cause: temper_core::Cause,
}

#[derive(Clone)]
pub(crate) struct Missing(Arc<MissingStruct>);

impl Missing {
    pub fn new(key: impl ToArcString) -> Missing {
        let key = key.to_arc_string();
        Missing(Arc::new(MissingStruct {
            key,
            cause: temper_core::Cause::new(),
        }))
    }

    pub fn message(&self) -> Arc<String> {
        Arc::new(format!("missing (key: {})", self.0.key))
    }
}

temper_core::impl_failure!(Missing, message, cause);

temper_core::impl_any_value_trait!(Missing, [FailureParamsError]);

pub(crate) fn find(key: impl ToArcString) -> Result<i32, Missing> {
    let key = key.to_arc_string();
    if key.as_str() == "one" {
        return Ok(1);
    }
    if key.as_str() == "two" {
        return Ok(2);
    }
    Err(Missing::new(key.clone()))
}

pub(crate) fn map_all<
    T: Clone + Send + Sync + 'static,
    R: Clone + Send + Sync + 'static,
    E: temper_core::Failure + Clone + Send + Sync + 'static,
>(
    xs: impl temper_core::ToList<T>,
    f: Arc<dyn Fn(T) -> Result<R, E> + Send + Sync>,
) -> Result<temper_core::List<R>, E> {
    let xs = xs.to_list();
    let out: temper_core::ListBuilder<R> = temper_core::listed::new_builder();
    {
        let length = temper_core::ListedTrait::len(&xs);
        let mut i: i32 = 0;
        while i < length {
            let x = temper_core::ListedTrait::get(&xs, i);
            i = i.wrapping_add(1);
            temper_core::listed::add(&out, f(x.clone())?, None);
        }
    }
    Ok(temper_core::ListedTrait::to_list(&out))
}

pub(crate) fn found(
    keys: impl temper_core::ToList<Arc<String>>,
) -> Result<temper_core::List<i32>, Missing> {
    let keys = keys.to_list();
    map_all::<_, _, Missing>(
        keys.clone(),
        Arc::new(move |k: Arc<String>| find(k.clone())),
    )
}

pub(crate) fn shown(keys: impl temper_core::ToList<Arc<String>>) -> Arc<String> {
    let keys = keys.to_list();
    let ns: temper_core::List<i32>;
    let mut caught: Option<Missing> = None;
    let mut held: Option<temper_core::List<i32>> = None;
    if let Err(failure) = (|| {
        held = Some(found(keys.clone())?);
        Ok::<_, Missing>(())
    })() {
        caught = Some(failure.clone());
    }
    if let Some(held) = &held {
        ns = held.clone();
    } else {
        let e = caught.clone().unwrap();
        return Arc::new(format!("missing {}", e.0.key));
    }
    temper_core::listed::join(
        &ns,
        Arc::new(",".to_string()),
        &(|n: i32| Arc::new(n.to_string())),
    )
}

pub(crate) fn applied(
    f: Arc<dyn Fn(i32) -> Result<i32, Missing> + Send + Sync>,
    n: i32,
) -> Result<i32, Missing> {
    f(n)
}

pub(crate) fn failures<
    T: Clone + Send + Sync + 'static,
    R: Clone + Send + Sync + 'static,
    E: temper_core::Failure + Clone + Send + Sync + 'static,
>(
    xs: impl temper_core::ToList<T>,
    f: Arc<dyn Fn(T) -> Result<R, E> + Send + Sync>,
) -> i32 {
    let xs = xs.to_list();
    let mut failed: i32 = 0;
    {
        let length = temper_core::ListedTrait::len(&xs);
        let mut i: i32 = 0;
        while i < length {
            let x = temper_core::ListedTrait::get(&xs, i);
            i = i.wrapping_add(1);
            if (|| {
                f(x.clone())?;
                Ok::<_, E>(())
            })()
            .is_err()
            {
                {
                    failed = failed.wrapping_add(1);
                };
            }
        }
    }
    failed
}

pub(crate) fn sum_all<
    T: Clone + Send + Sync + 'static,
    E: temper_core::Failure + Clone + Send + Sync + 'static,
>(
    xs: impl temper_core::ToList<T>,
    f: Arc<dyn Fn(T) -> Result<i32, E> + Send + Sync>,
) -> Result<i32, E> {
    let xs = xs.to_list();
    let mut total: i32 = 0;
    let mut first: Option<E> = None;
    {
        let length = temper_core::ListedTrait::len(&xs);
        let mut i: i32 = 0;
        while i < length {
            let x = temper_core::ListedTrait::get(&xs, i);
            i = i.wrapping_add(1);
            if let Err(e) = (|| {
                total = total.wrapping_add(f(x.clone())?);
                Ok::<_, E>(())
            })() {
                if first.is_none() {
                    first = Some(e.clone());
                }
            }
        }
    }
    let failed = first.clone();
    if let Some(failed) = &failed {
        return Err(failed.clone());
    }
    Ok(total)
}

pub(crate) fn both<E: temper_core::Failure + Clone + Send + Sync + 'static>(
    a: Arc<dyn Fn(Arc<String>) -> Result<i32, E> + Send + Sync>,
    b: Arc<dyn Fn(Arc<String>) -> Result<i32, E> + Send + Sync>,
    key: impl ToArcString,
) -> Result<i32, E> {
    let key = key.to_arc_string();
    Ok(a(key.clone())?.wrapping_add(b(key.clone())?))
}

struct UnknownStruct {
    word: Arc<String>,
    cause: temper_core::Cause,
}

#[derive(Clone)]
pub(crate) struct Unknown(Arc<UnknownStruct>);

impl Unknown {
    pub fn new(word: impl ToArcString) -> Unknown {
        let word = word.to_arc_string();
        Unknown(Arc::new(UnknownStruct {
            word,
            cause: temper_core::Cause::new(),
        }))
    }

    pub fn message(&self) -> Arc<String> {
        Arc::new(format!("unknown (word: {})", self.0.word))
    }
}

temper_core::impl_failure!(Unknown, message, cause);

temper_core::impl_any_value_trait!(Unknown, [FailureParamsError]);

pub(crate) fn word(key: impl ToArcString) -> Result<i32, Unknown> {
    let key = key.to_arc_string();
    if key.as_str() == "ten" {
        return Ok(10);
    }
    Err(Unknown::new(key.clone()))
}

pub(crate) fn either(key: impl ToArcString) -> Arc<String> {
    let key = key.to_arc_string();
    let n: i32;
    let mut caught: Option<FailureParamsError> = None;
    let mut held: Option<i32> = None;
    if let Err(failure) = (|| {
        held = Some(both::<FailureParamsError>(
            Arc::new(move |x: Arc<String>| Ok(find(x.clone())?)),
            Arc::new(move |x: Arc<String>| Ok(word(x.clone())?)),
            key.clone(),
        )?,);
        Ok::<_, FailureParamsError>(())
    })() {
        caught = Some(failure.clone());
    }
    if let Some(held) = held {
        n = held;
    } else {
        let e = caught.clone().unwrap();
        return if temper_core::is::<Missing>(e.clone()) {
            Arc::new(format!(
                "no {}",
                temper_core::cast::<Missing>(e.clone()).unwrap().0.key
            ))
        } else if temper_core::is::<Unknown>(e.clone()) {
            Arc::new(format!(
                "unknown {}",
                temper_core::cast::<Unknown>(e.clone()).unwrap().0.word
            ))
        } else {
            panic!()
        };
    }
    Arc::new(n.to_string())
}

pub(crate) fn parts(keys: impl temper_core::ToList<Arc<String>>) -> Result<Arc<String>, Missing> {
    let keys = keys.to_list();
    let found: temper_core::List<Arc<String>> = temper_core::listed::try_filter::<_, Missing>(
        &keys,
        &(|k: Arc<String>| Ok(find(k.clone())? > 1)),
    )?;
    let total = temper_core::listed::try_reduce_from::<_, _, Missing>(
        &keys,
        0,
        &(|n: i32, k: Arc<String>| Ok(n.wrapping_add(find(k.clone())?))),
    )?;
    Ok(Arc::new(format!(
        "{} {total} {}",
        temper_core::listed::join(
            &found,
            Arc::new(",".to_string()),
            &(|k: Arc<String>| k.clone()),
        ),
        temper_core::listed::try_join::<_, Missing>(
            &keys,
            Arc::new("+".to_string()),
            &(|k: Arc<String>| Ok(Arc::new(find(k.clone())?.to_string()))),
        )?
    )))
}

pub(crate) fn map_twice<
    T: Clone + Send + Sync + 'static,
    E: temper_core::Failure + Clone + Send + Sync + 'static,
>(
    xs: impl temper_core::ToList<T>,
    f: Arc<dyn Fn(T) -> Result<T, E> + Send + Sync>,
) -> Result<temper_core::List<T>, E> {
    let xs = xs.to_list();
    let once: temper_core::List<T> = map_all::<_, _, E>(xs.clone(), f.clone())?;
    temper_core::listed::try_map::<_, _, E>(&once, &*f)
}

pub(crate) fn up_to_two(n: i32) -> Result<i32, Missing> {
    if n > 2 {
        return Err(Missing::new(Arc::new(n.to_string())));
    }
    Ok(n.wrapping_add(1))
}

pub(crate) fn keyed(text: impl ToArcString) -> Result<Arc<String>, Missing> {
    let text = text.to_arc_string();
    temper_std::regex::RegexNodeTrait::compiled(&temper_std::regex::Repeat::new(
        temper_std::regex::RegexNode::new(temper_std::regex::CodeSet::new(
            Arc::new(vec![temper_std::regex::CodePart::new(temper_std::regex::CodeRange::new(
                97,
                122,
            ))]),
            Some(false),
        )),
        1,
        None,
        Some(false),
    ))
    .replace::<Missing>(
        text.clone(),
        Arc::new(move |m: temper_std::regex::Match| {
            println!("seen {}", m.full().value());
            Ok(Arc::new(find(m.full().value())?.to_string()))
        }),
    )
}

pub(crate) fn bracketed(m: temper_std::regex::Match) -> Arc<String> {
    Arc::new(format!("<{}>", m.full().value()))
}
