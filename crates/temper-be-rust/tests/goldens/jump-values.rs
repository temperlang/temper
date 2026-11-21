use std::sync::{Arc, OnceLock, RwLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            *STEPS.write().unwrap() = Some(0);
            println!(
                "{} {}",
                {
                    let part = next(Some(1));
                    if let Some(part) = part {
                        Arc::new(part.to_string())
                    } else {
                        Arc::new("null".to_string())
                    }
                },
                {
                    let part2 = next(None);
                    if let Some(part2) = part2 {
                        Arc::new(part2.to_string())
                    } else {
                        Arc::new("null".to_string())
                    }
                }
            );
            println!(
                "{} {}",
                sum_present(Arc::new(vec![Some(1), None, Some(3)])),
                sum_until_bad(Arc::new(vec![
                    Arc::new("1".to_string()),
                    Arc::new("2".to_string()),
                    Arc::new("x".to_string()),
                    Arc::new("4".to_string()),
                ]))
            );
            println!("{} {} {}", parsed_or("12"), parsed_or("x"), stepped());
            println!("{} {}", paired(Some(3)), paired(None));
            println!(
                "{} {} {}",
                temper_core::ListedTrait::len(&listed(Some(2), Some(4))),
                temper_core::ListedTrait::len(&listed(None, Some(4))),
                temper_core::ListedTrait::get(&listed(Some(2), None), 0)
            );
            println!("{} {}", shown(Some(5)), shown(None));
            println!(
                "{} {} {}",
                both(true, Some(1)),
                both(true, None),
                both(false, Some(1))
            );
            println!("{} {} {}", sign(4), sign(-4), sign(0));
            first_word(Some("hello there"));
            first_word(None::<Arc<String>>);
            println!(
                "{} {} {} {} {}",
                do_jumps(Arc::new(vec![Some(1), None, Some(2), None, Some(3)])),
                do_return(Some(1), 0),
                do_return(None, 9),
                do_return(None, 1),
                do_return(None, 0)
            );
            println!("{}", ordered(Arc::new(vec![Some(1), None])));
            println!(
                "{} {}",
                nested(
                    Arc::new(vec![Some(1), None, None, Some(4)]),
                    Arc::new(vec![Some(0), Some(7), None, Some(0)]),
                ),
                via_orelse(Arc::new(vec![
                    Arc::new("1".to_string()),
                    Arc::new("x".to_string()),
                    Arc::new("2".to_string()),
                    Arc::new("y".to_string()),
                    Arc::new("z".to_string()),
                    Arc::new("3".to_string()),
                ]))
            );
            println!(
                "{} {}",
                gridded(Arc::new(vec![
                    Arc::new(vec![Some(1), Some(2)]),
                    Arc::new(vec![Some(3), Some(-1), Some(4)]),
                    Arc::new(vec![Some(5), None, Some(6)]),
                    Arc::new(vec![Some(7)]),
                ])),
                first_big(
                    Arc::new(vec![
                        Arc::new(vec![Some(1), Some(3)]),
                        Arc::new(vec![None, Some(1)]),
                        Arc::new(vec![Some(4)]),
                        Arc::new(vec![Some(9)]),
                        Arc::new(vec![Some(1)]),
                    ]),
                    Arc::new(vec![Some(7), None, None, None, None]),
                )
            );
            println!("{} {} {}", via_if(1), via_if(-1), via_if(-9));
            println!("{} {} {}", early(), partly(), steps());
            Ok(())
        })
        .clone()
}

static STEPS: RwLock<Option<i32>> = RwLock::new(None);

pub(crate) fn steps() -> i32 {
    crate::support::initialized(|| *STEPS.read().unwrap())
}

pub(crate) fn step(n: i32) -> i32 {
    crate::support::initialized(|| {
        let mut steps = STEPS.write().unwrap();
        *steps = Some((*steps)?.wrapping_add(1));
        Some(())
    });
    println!("step {n}");
    n
}

pub(crate) fn pair(a: i32, b: i32) -> i32 {
    a.wrapping_mul(10).wrapping_add(b)
}

pub(crate) fn parse(s: impl ToArcString) -> temper_core::Result<i32> {
    let s = s.to_arc_string();
    temper_core::string::to_int(&s, None)
}

pub(crate) fn next(y: Option<i32>) -> Option<i32> {
    let x: i32;
    if let Some(y) = y {
        x = y;
    } else {
        return None;
    }
    Some(x.wrapping_add(1))
}

pub(crate) fn sum_present(ys: impl temper_core::ToList<Option<i32>>) -> i32 {
    let ys = ys.to_list();
    let mut sum: i32 = 0;
    {
        let mut i: i32 = 0;
        while i < temper_core::ListedTrait::len(&ys) {
            sum = sum.wrapping_add({
                let t: i32;
                let subject = temper_core::ListedTrait::get(&ys, i);
                if let Some(subject) = subject {
                    t = subject;
                } else {
                    {
                        i = i.wrapping_add(1);
                        continue;
                    }
                }
                t
            });
            i = i.wrapping_add(1);
        }
    }
    sum
}

pub(crate) fn sum_until_bad(words: impl temper_core::ToList<Arc<String>>) -> i32 {
    let words = words.to_list();
    let mut sum: i32 = 0;
    let mut i: i32 = 0;
    while i < temper_core::ListedTrait::len(&words) {
        sum = sum.wrapping_add({
            let t: i32;
            let mut held: Option<i32> = None;
            let _ = (|| {
                held = Some(parse(temper_core::ListedTrait::get(&words, i))?);
                Ok::<_, temper_core::Error>(())
            })();
            if let Some(held) = held {
                t = held;
            } else {
                break;
            }
            t
        });
        i = i.wrapping_add(1);
    }
    sum
}

pub(crate) fn parsed_or(s: impl ToArcString) -> i32 {
    let s = s.to_arc_string();
    {
        let t: i32;
        let mut held: Option<i32> = None;
        let _ = (|| {
            held = Some(parse(s.clone())?);
            Ok::<_, temper_core::Error>(())
        })();
        if let Some(held) = held {
            t = held;
        } else {
            return -1;
        }
        t
    }
}

pub(crate) fn after(i: i32) -> Option<i32> {
    if i < 3 {
        Some(i.wrapping_add(1))
    } else {
        None
    }
}

pub(crate) fn stepped() -> Arc<String> {
    let mut seen = Arc::new(String::new());
    {
        let mut i: i32 = 0;
        while i < 6 {
            if i == 1 {
                {
                    i = {
                        let subject = after(i);
                        subject.unwrap_or(9)
                    };
                    continue;
                }
            }
            seen = Arc::new(format!("{seen}{i}"));
            i = {
                let subject = after(i);
                subject.unwrap_or(9)
            };
        }
    }
    seen.clone()
}

pub(crate) fn paired(y: Option<i32>) -> i32 {
    pair(step(1), {
        let t: i32;
        if let Some(y) = y {
            t = y;
        } else {
            return -2;
        }
        t
    })
}

pub(crate) fn listed(y: Option<i32>, z: Option<i32>) -> temper_core::List<i32> {
    Arc::new(vec![
        1,
        {
            let t: i32;
            if let Some(y) = y {
                t = y;
            } else {
                return Arc::new(vec![]);
            }
            t
        },
        3,
        {
            let t: i32;
            if let Some(z) = z {
                t = z;
            } else {
                return Arc::new(vec![9]);
            }
            t
        },
    ])
}

pub(crate) fn shown(y: Option<i32>) -> Arc<String> {
    Arc::new(format!("y={}", {
        let t: i32;
        if let Some(y) = y {
            t = y;
        } else {
            return Arc::new("none".to_string());
        }
        t
    }))
}

pub(crate) fn both(t: bool, y: Option<i32>) -> Arc<String> {
    let ok: bool = if t && y.is_some() {
        true
    } else {
        return Arc::new("no".to_string());
    };
    Arc::new(format!("ok {ok}"))
}

pub(crate) fn sign(n: i32) -> Arc<String> {
    Arc::new(
        pair(0, {
            let t: i32;
            if n > 0 {
                t = 1;
            } else if n < 0 {
                t = -1;
            } else {
                return Arc::new("zero".to_string());
            }
            t
        })
        .to_string(),
    )
}

pub(crate) fn first_word(text: Option<impl ToArcString>) {
    let text = text.map(|x| x.to_arc_string());
    let t: Arc<String>;
    if let Some(text) = &text {
        t = text.clone();
    } else {
        return;
    }
    println!(
        "first {}",
        temper_core::ListedTrait::get(
            &temper_core::string::split(&t, Arc::new(" ".to_string())),
            0,
        )
    );
}

pub(crate) fn early() -> i32 {
    7
}

pub(crate) fn partly() -> i32 {
    step(2);
    step(3)
}

pub(crate) fn do_jumps(ys: impl temper_core::ToList<Option<i32>>) -> Arc<String> {
    let ys = ys.to_list();
    let mut seen = Arc::new(String::new());
    {
        let mut i: i32 = 0;
        while i < temper_core::ListedTrait::len(&ys) {
            let v: i32;
            let subject = temper_core::ListedTrait::get(&ys, i);
            if let Some(subject) = subject {
                v = subject;
            } else {
                seen = Arc::new(format!("{seen}a"));
                if i == 1 {
                    {
                        i = i.wrapping_add(1);
                        continue;
                    }
                }
                seen = Arc::new(format!("{seen}b"));
                break;
            }
            seen = Arc::new(format!("{seen}{v}"));
            i = i.wrapping_add(1);
        }
    }
    seen.clone()
}

pub(crate) fn do_return(y: Option<i32>, z: i32) -> Arc<String> {
    let v = if let Some(y) = y {
        y
    } else {
        if z > 0 {
            if z > 5 {
                return Arc::new("big".to_string());
            }
            return Arc::new("pos".to_string());
        }
        0
    };
    Arc::new(format!("v{v}"))
}

pub(crate) fn trio(a: i32, b: i32, c: i32) -> i32 {
    a.wrapping_add(b).wrapping_add(c)
}

pub(crate) fn ordered(ys: impl temper_core::ToList<Option<i32>>) -> i32 {
    let ys = ys.to_list();
    let mut sum: i32 = 0;
    {
        let mut i: i32 = 0;
        while i < temper_core::ListedTrait::len(&ys) {
            sum = sum.wrapping_add(trio(
                step(10i32.wrapping_add(i)),
                {
                    let t: i32;
                    let subject = temper_core::ListedTrait::get(&ys, i);
                    if let Some(subject) = subject {
                        t = subject;
                    } else {
                        step(20i32.wrapping_add(i));
                        {
                            i = i.wrapping_add(1);
                            continue;
                        }
                    }
                    t
                },
                step(30i32.wrapping_add(i)),
            ));
            i = i.wrapping_add(1);
        }
    }
    sum
}

pub(crate) fn nested(
    xs: impl temper_core::ToList<Option<i32>>,
    ys: impl temper_core::ToList<Option<i32>>,
) -> Arc<String> {
    let xs = xs.to_list();
    let ys = ys.to_list();
    let mut seen = Arc::new(String::new());
    {
        {
            let mut i: i32 = 0;
            while i < temper_core::ListedTrait::len(&xs) {
                let v: i32;
                let subject = temper_core::ListedTrait::get(&xs, i);
                if let Some(subject) = subject {
                    v = subject;
                } else {
                    let subject2 = temper_core::ListedTrait::get(&ys, i);
                    if let Some(subject2) = subject2 {
                        v = subject2;
                    } else {
                        break;
                    }
                }
                seen = Arc::new(format!("{seen}{v}"));
                i = i.wrapping_add(1);
            }
        }
    }
    seen.clone()
}

pub(crate) fn gridded(
    rows: impl temper_core::ToList<temper_core::List<Option<i32>>>,
) -> Arc<String> {
    let rows = rows.to_list();
    let mut seen = Arc::new(String::new());
    {
        {
            let mut i: i32 = 0;
            'outer: while i < temper_core::ListedTrait::len(&rows) {
                let row: temper_core::List<Option<i32>> = temper_core::ListedTrait::get(&rows, i);
                {
                    let mut j: i32 = 0;
                    while j < temper_core::ListedTrait::len(&row) {
                        let v: i32;
                        let subject = temper_core::ListedTrait::get(&row, j);
                        if let Some(subject) = subject {
                            v = subject;
                        } else {
                            break 'outer;
                        }
                        let w: i32;
                        let subject = if v < 0 { None } else { Some(v) };
                        if let Some(subject) = subject {
                            w = subject;
                        } else {
                            seen = Arc::new(format!("{seen}-"));
                            {
                                i = i.wrapping_add(1);
                                continue 'outer;
                            }
                        }
                        seen = Arc::new(format!("{seen}{w}"));
                        j = j.wrapping_add(1);
                    }
                }
                seen = Arc::new(format!("{seen}|"));
                i = i.wrapping_add(1);
            }
        }
    }
    seen.clone()
}

pub(crate) fn first_big(
    rows: impl temper_core::ToList<temper_core::List<Option<i32>>>,
    keys: impl temper_core::ToList<Option<i32>>,
) -> Arc<String> {
    let rows = rows.to_list();
    let keys = keys.to_list();
    let mut seen = Arc::new(String::new());
    {
        {
            let mut i: i32 = 0;
            'outer: while i < temper_core::ListedTrait::len(&rows) {
                let k = {
                    let subject = temper_core::ListedTrait::get(&keys, i);
                    if let Some(subject) = subject {
                        subject
                    } else {
                        let row: temper_core::List<Option<i32>> =
                            temper_core::ListedTrait::get(&rows, i);
                        let mut first: i32 = -1;
                        {
                            let mut j: i32 = 0;
                            while j < temper_core::ListedTrait::len(&row) {
                                let v: i32;
                                let subject2 = temper_core::ListedTrait::get(&row, j);
                                if let Some(subject2) = subject2 {
                                    v = subject2;
                                } else {
                                    {
                                        i = i.wrapping_add(1);
                                        continue 'outer;
                                    }
                                }
                                if v > 5 {
                                    break 'outer;
                                }
                                if v > 2 {
                                    first = v;
                                    break;
                                }
                                j = j.wrapping_add(1);
                            }
                        }
                        first
                    }
                };
                seen = Arc::new(format!("{seen}{k},"));
                i = i.wrapping_add(1);
            }
        }
    }
    seen.clone()
}

pub(crate) fn via_orelse(words: impl temper_core::ToList<Arc<String>>) -> Arc<String> {
    let words = words.to_list();
    let mut seen = Arc::new(String::new());
    {
        let mut i: i32 = 0;
        while i < temper_core::ListedTrait::len(&words) {
            let n: i32;
            let mut held: Option<i32> = None;
            let _ = (|| {
                held = Some(parse(temper_core::ListedTrait::get(&words, i))?);
                Ok::<_, temper_core::Error>(())
            })();
            if let Some(held) = held {
                n = held;
            } else {
                seen = Arc::new(format!("{seen}!"));
                if i > 2 {
                    break;
                }
                {
                    i = i.wrapping_add(1);
                    continue;
                }
            }
            seen = Arc::new(format!("{seen}{n}"));
            i = i.wrapping_add(1);
        }
    }
    seen.clone()
}

pub(crate) fn via_if(n: i32) -> Arc<String> {
    let s = if n > 0 {
        Arc::new("pos".to_string())
    } else {
        if n < -5 {
            return Arc::new("low".to_string());
        }
        Arc::new("neg".to_string())
    };
    Arc::new(format!("s={s}"))
}
