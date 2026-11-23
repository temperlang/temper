use std::sync::{Arc, OnceLock, RwLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let b: temper_core::ListBuilder<i32> =
                temper_core::ListedTrait::to_list_builder(&Arc::new(vec![1, 2]));
            temper_core::listed::add_all(&b, temper_core::Listed::new(b.clone()), None);
            let c: temper_core::ListBuilder<i32> =
                temper_core::ListedTrait::to_list_builder(&Arc::new(vec![1, 2]));
            temper_core::listed::add_all(&c, temper_core::Listed::new(c.clone()), Some(1));
            let d: temper_core::ListBuilder<i32> =
                temper_core::ListedTrait::to_list_builder(&Arc::new(vec![1, 2, 3]));
            let removed: temper_core::List<i32> = temper_core::listed::splice(
                &d,
                Some(1),
                Some(1),
                Some(temper_core::Listed::new(d.clone())),
            );
            println!(
                "self {} {} {} {}",
                show(b.clone()),
                show(c.clone()),
                show(d.clone()),
                show(removed.clone())
            );
            let e: temper_core::ListBuilder<i32> =
                temper_core::ListedTrait::to_list_builder(&Arc::new(vec![1, 2]));
            let kept: temper_core::List<i32> = temper_core::listed::filter(&e, &(|n: i32| n > 0));
            temper_core::listed::add(&e, 3, None);
            println!("filter {} {}", show(kept.clone()), show(e.clone()));
            F.set(temper_core::ListedTrait::to_list_builder(
                &Arc::new(vec![3, 1, 2]),
            ))
            .unwrap_or_else(|_| panic!());
            *PEEKS.write().unwrap() = Some(0);
            temper_core::listed::sort(
                &f(),
                &(|a: i32, z: i32| {
                    *PEEKS.write().unwrap() =
                        Some(peeks().wrapping_add(temper_core::ListedTrait::len(&f())));
                    a.wrapping_sub(z)
                }),
            );
            println!("peeking {}", show(f()));
            let walked: temper_core::List<i32> = Arc::new(vec![
                reduce_from_growing().unwrap_or(-1),
                map_shrinking().unwrap_or(-1),
                reduce_shrinking().unwrap_or(-1),
                filter_growing().unwrap_or(-1),
                join_growing().unwrap_or(-1),
                for_each_removing().unwrap_or(-1),
                for_each_adding().unwrap_or(-1),
                to_list_with_removing().unwrap_or(-1),
            ]);
            println!("walks {}", show(walked.clone()));
            T.set(temper_core::ListedTrait::to_list_builder(
                &Arc::new(vec![3, 1, 2]),
            ))
            .unwrap_or_else(|_| panic!());
            temper_core::listed::sort(
                &t(),
                &(|a: i32, z: i32| {
                    temper_core::listed::set(&t(), 0, 99);
                    a.wrapping_sub(z)
                }),
            );
            U.set(temper_core::ListedTrait::to_list_builder(
                &Arc::new(vec![3, 1, 2]),
            ))
            .unwrap_or_else(|_| panic!());
            temper_core::listed::sort(
                &u(),
                &(|a: i32, z: i32| {
                    let last = temper_core::listed::remove_last(&u());
                    temper_core::listed::add(&u(), last.wrapping_add(10), None);
                    a.wrapping_sub(z)
                }),
            );
            println!("same length {} {}", show(t()), show(u()));
            S.set(temper_core::ListedTrait::to_list_builder(
                &Arc::new(vec![3, 1, 2]),
            ))
            .unwrap_or_else(|_| panic!());
            temper_core::listed::sort(
                &s(),
                &(|a: i32, z: i32| {
                    if temper_core::ListedTrait::len(&s()) < 40 {
                        temper_core::listed::add(&s(), 0, None);
                    }
                    a.wrapping_sub(z)
                }),
            );
            println!("sorted {}", temper_core::ListedTrait::len(&s()));
            Ok(())
        })
        .clone()
}

pub(crate) fn show(ls: impl temper_core::ToListed<i32>) -> Arc<String> {
    let ls = ls.to_listed();
    temper_core::listed::join(
        &*ls,
        Arc::new(",".to_string()),
        &(|n: i32| Arc::new(n.to_string())),
    )
}

static F: OnceLock<temper_core::ListBuilder<i32>> = OnceLock::new();

pub(crate) fn f() -> temper_core::ListBuilder<i32> {
    crate::support::initialized(|| F.get()).clone()
}

static PEEKS: RwLock<Option<i32>> = RwLock::new(None);

pub(crate) fn peeks() -> i32 {
    crate::support::initialized(|| *PEEKS.read().unwrap())
}

pub(crate) fn words() -> temper_core::ListBuilder<Arc<String>> {
    let w: temper_core::ListBuilder<Arc<String>> = temper_core::listed::new_builder();
    {
        let mut i: i32 = 0;
        while i < 3 {
            temper_core::listed::add(&w, Arc::new(format!("w{i}")), None);
            i = i.wrapping_add(1);
        }
    }
    w.clone()
}

pub(crate) fn reduce_from_growing() -> temper_core::Result<i32> {
    let g: temper_core::ListBuilder<i32> =
        temper_core::ListedTrait::to_list_builder(&Arc::new(vec![1, 2]));
    Ok(temper_core::listed::reduce_from(
        &g,
        0,
        &(|sum: i32, n: i32| {
            if temper_core::ListedTrait::len(&g) < 4 {
                temper_core::listed::add(&g, 9, None);
            }
            sum.wrapping_mul(10).wrapping_add(n)
        }),
    ))
}

pub(crate) fn map_shrinking() -> temper_core::Result<i32> {
    let w: temper_core::ListBuilder<Arc<String>> = words();
    Ok(temper_core::ListedTrait::len(&temper_core::listed::map(
        &w,
        &(|s: Arc<String>| {
            if temper_core::ListedTrait::len(&w) > 1 {
                temper_core::listed::remove_last(&w);
            }
            s.clone()
        }),
    )))
}

pub(crate) fn reduce_shrinking() -> temper_core::Result<i32> {
    let w: temper_core::ListBuilder<Arc<String>> = words();
    let all = temper_core::listed::reduce(
        &w,
        &(|all: Arc<String>, _s: Arc<String>| {
            if temper_core::ListedTrait::len(&w) > 1 {
                temper_core::listed::remove_last(&w);
            }
            all.clone()
        }),
    );
    if all.is_empty() {
        Ok(0)
    } else {
        Ok(1)
    }
}

pub(crate) fn filter_growing() -> temper_core::Result<i32> {
    let g: temper_core::ListBuilder<i32> =
        temper_core::ListedTrait::to_list_builder(&Arc::new(vec![1, 2]));
    Ok(temper_core::ListedTrait::len(&temper_core::listed::filter(
        &g,
        &(|_n: i32| {
            if temper_core::ListedTrait::len(&g) < 4 {
                temper_core::listed::add(&g, 9, None);
            }
            true
        }),
    )))
}

pub(crate) fn join_growing() -> temper_core::Result<i32> {
    let g: temper_core::ListBuilder<i32> =
        temper_core::ListedTrait::to_list_builder(&Arc::new(vec![1, 2]));
    let joined = temper_core::listed::join(
        &g,
        Arc::new(",".to_string()),
        &(|n: i32| {
            if temper_core::ListedTrait::len(&g) < 4 {
                temper_core::listed::add(&g, 9, None);
            }
            Arc::new(n.to_string())
        }),
    );
    if joined.is_empty() {
        Ok(0)
    } else {
        Ok(1)
    }
}

pub(crate) fn letters() -> temper_core::MapBuilder<Arc<String>, i32> {
    let m: temper_core::MapBuilder<Arc<String>, i32> = temper_core::MapBuilder::new();
    temper_core::MapBuilder::set(&m, Arc::new(format!("a{}", 1i32)), 1);
    temper_core::MapBuilder::set(&m, Arc::new(format!("b{}", 2i32)), 2);
    temper_core::MapBuilder::set(&m, Arc::new(format!("c{}", 3i32)), 3);
    m.clone()
}

pub(crate) fn for_each_removing() -> temper_core::Result<i32> {
    let m: temper_core::MapBuilder<Arc<String>, i32> = letters();
    let sum: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
    temper_core::MappedTrait::for_each(
        &m,
        &(|_k: Arc<String>, v: i32| {
            if temper_core::MappedTrait::has(&m, Arc::new("c3".to_string())) {
                let _ = temper_core::MapBuilder::remove(&m, Arc::new("c3".to_string()));
            }
            *sum.write().unwrap() = temper_core::read_locked(&sum).wrapping_add(v);
        }),
    );
    Ok(temper_core::read_locked(&sum))
}

pub(crate) fn for_each_adding() -> temper_core::Result<i32> {
    let m: temper_core::MapBuilder<Arc<String>, i32> = letters();
    let count: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
    temper_core::MappedTrait::for_each(
        &m,
        &(|_k: Arc<String>, _v: i32| {
            *count.write().unwrap() = temper_core::read_locked(&count).wrapping_add(1);
            if temper_core::MappedTrait::len(&m) < 30 {
                temper_core::MapBuilder::set(
                    &m,
                    Arc::new(format!("k{}", temper_core::MappedTrait::len(&m))),
                    1,
                );
            }
        }),
    );
    Ok(temper_core::read_locked(&count))
}

pub(crate) fn to_list_with_removing() -> temper_core::Result<i32> {
    let m: temper_core::MapBuilder<Arc<String>, i32> = letters();
    Ok(temper_core::ListedTrait::len(&temper_core::mapped_to_list_with(
        &m,
        &(|_k: Arc<String>, v: i32| {
            if temper_core::MappedTrait::has(&m, Arc::new("c3".to_string())) {
                let _ = temper_core::MapBuilder::remove(&m, Arc::new("c3".to_string()));
            }
            v
        }),
    )))
}

static T: OnceLock<temper_core::ListBuilder<i32>> = OnceLock::new();

pub(crate) fn t() -> temper_core::ListBuilder<i32> {
    crate::support::initialized(|| T.get()).clone()
}

static U: OnceLock<temper_core::ListBuilder<i32>> = OnceLock::new();

pub(crate) fn u() -> temper_core::ListBuilder<i32> {
    crate::support::initialized(|| U.get()).clone()
}

static S: OnceLock<temper_core::ListBuilder<i32>> = OnceLock::new();

pub(crate) fn s() -> temper_core::ListBuilder<i32> {
    crate::support::initialized(|| S.get()).clone()
}
