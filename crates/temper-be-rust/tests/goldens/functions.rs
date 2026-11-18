use std::sync::{Arc, OnceLock, RwLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let counter = make_counter(10);
            counter();
            println!("count {}", counter());
            let other = make_counter(0);
            println!("other {} count {}", other(), counter());
            let add_two = twice(Arc::new(add_one));
            println!("twice {} {}", add_two(5), apply_to(Arc::new(add_one), 1));
            println!(
                "triple {} {}",
                apply_to(Arc::new(Scaler::triple), 4),
                twice(Arc::new(Scaler::triple))(2)
            );
            let ns: temper_core::List<i32> = Arc::new(vec![5, 2, 8, 1, 4]);
            let doubled: temper_core::List<i32> =
                temper_core::listed::map(&ns, &(|n: i32| n.wrapping_mul(2)));
            let big: temper_core::List<i32> =
                temper_core::listed::filter(&doubled, &(|n: i32| n > 4));
            println!(
                "{}",
                temper_core::listed::join(
                    &big,
                    Arc::new(", ".to_string()),
                    &(|n: i32| Arc::new(n.to_string())),
                )
            );
            let order: temper_core::List<i32> =
                temper_core::listed::sorted(&ns, &(|a: i32, b: i32| a.wrapping_sub(b)));
            println!(
                "{}",
                temper_core::listed::join(
                    &order,
                    Arc::new(" ".to_string()),
                    &(|n: i32| Arc::new(n.to_string())),
                )
            );
            let mut total: i32 = 0;
            {
                let length = temper_core::ListedTrait::len(&ns);
                let mut i2: i32 = 0;
                while i2 < length {
                    let n = temper_core::ListedTrait::get(&ns, i2);
                    i2 = i2.wrapping_add(1);
                    total = total.wrapping_add(n);
                }
            }
            println!("total {total}");
            let button = Button::new("hits", make_counter(0));
            button.press();
            println!("{}", button.press());
            let makers: temper_core::ListBuilder<Arc<dyn Fn() -> i32 + Send + Sync>> =
                temper_core::listed::new_builder();
            {
                let mut i: i32 = 0;
                while i < 3 {
                    let k = i.wrapping_mul(10);
                    temper_core::listed::add(&makers, Arc::new(move || k), None);
                    i = i.wrapping_add(1);
                }
            }
            println!(
                "{}",
                temper_core::listed::join(
                    &makers,
                    Arc::new(" ".to_string()),
                    &(|c: Arc<dyn Fn() -> i32 + Send + Sync>| Arc::new(c().to_string())),
                )
            );
            drive(
                5,
                Arc::new(move || {
                    let case_index: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                    let fs: Arc<
                        RwLock<Option<temper_core::ListBuilder<Arc<dyn Fn() -> i32 + Send + Sync>>>>,
                    > = Arc::new(RwLock::new(None));
                    let i: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                    let j: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                    temper_core::SafeGenerator::from_fn({
                        let fs = fs.clone();
                        let i = i.clone();
                        let j = j.clone();
                        let case_index = case_index.clone();
                        Arc::new(move |_generator: temper_core::SafeGenerator<()>| loop {
                            let case_index_local = temper_core::read_locked(&case_index);
                            *case_index.write().unwrap() = -1;
                            match case_index_local {
                                0 => {
                                    *fs.write().unwrap() = Some(temper_core::listed::new_builder());
                                    *i.write().unwrap() = 0;
                                    *case_index.write().unwrap() = 1;
                                }
                                1 => {
                                    if temper_core::read_locked(&i) < 3 {
                                        *case_index.write().unwrap() = 2;
                                    } else {
                                        *case_index.write().unwrap() = 3;
                                    }
                                }
                                2 => {
                                    *j.write().unwrap() =
                                        temper_core::read_locked(&i).wrapping_mul(10);
                                    temper_core::listed::add(
                                        temper_core::read_locked(&fs).as_ref().unwrap(),
                                        {
                                            let j2 = temper_core::read_locked(&j);
                                            Arc::new(move || j2)
                                        },
                                        None,
                                    );
                                    *case_index.write().unwrap() = 4;
                                    return Some(());
                                }
                                3 => {
                                    println!(
                                        "{}",
                                        temper_core::listed::join(
                                            temper_core::read_locked(&fs).as_ref().unwrap(),
                                            Arc::new(" ".to_string()),
                                            &(|c: Arc<dyn Fn() -> i32 + Send + Sync>| {
                                                Arc::new(c().to_string())
                                            }),
                                        )
                                    );
                                    *case_index.write().unwrap() = -1;
                                    return Some(());
                                }
                                4 => {
                                    temper_core::listed::add(
                                        temper_core::read_locked(&fs).as_ref().unwrap(),
                                        {
                                            let j3 = temper_core::read_locked(&j);
                                            Arc::new(move || j3.wrapping_add(1))
                                        },
                                        None,
                                    );
                                    *i.write().unwrap() =
                                        temper_core::read_locked(&i).wrapping_add(1);
                                    *case_index.write().unwrap() = 1;
                                }
                                _ => {
                                    return None;
                                }
                            }
                        })
                    })
                }),
            );
            println!(
                "sum {} doubles {}",
                sum_from(ns.clone(), 4),
                doubles("bookkeeper")
            );
            println!("climb {} {}", climb(0), climb(1));
            let gate = Gate::new();
            println!("gate {} {}", gate.allows(0), gate.passed(4));
            *gate.0.check.write().unwrap() = Some(Arc::new(move |x: i32| x.wrapping_sub(1)));
            println!(
                "gate {} {} {}",
                gate.allows(1),
                gate.allows(2),
                gate.passed(4)
            );
            let none = if gate.allows(0) { Some(3) } else { None };
            println!(
                "{} {}",
                string_is_palindrome("step on no pets"),
                string_is_palindrome("steps")
            );
            println!(
                "{} {} {}",
                int_or_zero(none),
                list_first_or(Arc::new(vec![4, 5]), 0),
                list_first_or(
                    Arc::new(vec![Arc::new("x".to_string())]),
                    Arc::new("-".to_string()),
                )
            );
            println!(
                "{} {}",
                string_padded("ab", 2, None::<Arc<String>>),
                string_padded("ab", 1, Some("*"))
            );
            println!("{} {}", gate.passed(4), gate_passed(gate.clone(), "gate"));
            Ok(())
        })
        .clone()
}

pub(crate) fn make_counter(start: i32) -> Arc<dyn Fn() -> i32 + Send + Sync> {
    let count: Arc<RwLock<i32>> = Arc::new(RwLock::new(start));
    {
        let count = count.clone();
        Arc::new(move || {
            *count.write().unwrap() = temper_core::read_locked(&count).wrapping_add(1);
            temper_core::read_locked(&count)
        })
    }
}

pub(crate) fn twice(
    f: Arc<dyn Fn(i32) -> i32 + Send + Sync>,
) -> Arc<dyn Fn(i32) -> i32 + Send + Sync> {
    {
        let f = f.clone();
        Arc::new(move |x: i32| f(f(x)))
    }
}

pub(crate) fn apply_to(f: Arc<dyn Fn(i32) -> i32 + Send + Sync>, x: i32) -> i32 {
    f(x)
}

pub(crate) fn add_one(x: i32) -> i32 {
    x.wrapping_add(1)
}

struct ScalerStruct {}

#[derive(Clone)]
pub(crate) struct Scaler(Arc<ScalerStruct>);

impl Scaler {
    #[allow(dead_code)]
    pub fn new() -> Scaler {
        Scaler(Arc::new(ScalerStruct {}))
    }

    pub fn triple(x: i32) -> i32 {
        x.wrapping_mul(3)
    }
}

temper_core::impl_any_value_trait!(Scaler, []);

struct ButtonStruct {
    label: Arc<String>,
    on_press: Arc<dyn Fn() -> i32 + Send + Sync>,
}

#[derive(Clone)]
pub(crate) struct Button(Arc<ButtonStruct>);

impl Button {
    pub fn new(label: impl ToArcString, on_press: Arc<dyn Fn() -> i32 + Send + Sync>) -> Button {
        let label = label.to_arc_string();
        Button(Arc::new(ButtonStruct { label, on_press }))
    }

    pub fn press(&self) -> Arc<String> {
        Arc::new(format!("{} {}", self.0.label, (self.0.on_press)()))
    }
}

temper_core::impl_any_value_trait!(Button, []);

pub(crate) fn drive(
    steps: i32,
    body: Arc<dyn Fn() -> temper_core::SafeGenerator<()> + Send + Sync>,
) {
    let g: temper_core::SafeGenerator<()> = body();
    let mut s: i32 = 0;
    while s < steps {
        g.next_safe();
        s = s.wrapping_add(1);
    }
}

pub(crate) fn sum_from(xs: impl temper_core::ToList<i32>, least: i32) -> i32 {
    let xs = xs.to_list();
    let mut sum: i32 = 0;
    {
        let length = temper_core::ListedTrait::len(&xs);
        let mut i: i32 = 0;
        while i < length {
            let x = temper_core::ListedTrait::get(&xs, i);
            i = i.wrapping_add(1);
            if x < least {
                continue;
            }
            sum = sum.wrapping_add(x);
        }
    }
    sum
}

pub(crate) fn doubles(s: impl ToArcString) -> i32 {
    let s = s.to_arc_string();
    let mut n: i32 = 0;
    let end = s.len();
    {
        let mut i: usize = 0usize;
        let mut prev = i;
        while (Some(i).cmp(&Some(end)) as i32) < 0 {
            if (Some(prev).cmp(&Some(i)) as i32) < 0
                && temper_core::string::get(&s, prev) == temper_core::string::get(&s, i)
            {
                n = n.wrapping_add(1);
            }
            prev = i;
            i = temper_core::string::next(&s, i);
        }
    }
    n
}

pub(crate) fn climb(x: i32) -> i32 {
    let mut d: i32;
    if x == 0 {
        #[derive(Clone)]
        struct DownGroup {
            x: i32,
        }
        impl DownGroup {
            fn down(&self, n: i32) -> i32 {
                let x = self.x;
                if n <= x {
                    0
                } else {
                    self.down(n.wrapping_sub(1)).wrapping_add(1)
                }
            }
        }
        let down_group = DownGroup { x };
        let down = Arc::new(move |a0: i32| down_group.down(a0));
        d = down(3);
    } else {
        return -1;
    }
    d = d.wrapping_add(1);
    if d == 4 {
        let top = d.wrapping_add(1);
        #[derive(Clone)]
        struct UpGroup {
            top: i32,
        }
        impl UpGroup {
            fn up(&self, n: i32) -> i32 {
                let top = self.top;
                if n >= top {
                    n
                } else {
                    self.up(n.wrapping_add(1))
                }
            }
        }
        let up_group = UpGroup { top };
        let up = Arc::new(move |a0: i32| up_group.up(a0));
        up(d)
    } else {
        d
    }
}

struct GateStruct {
    check: RwLock<Option<Arc<dyn Fn(i32) -> i32 + Send + Sync>>>,
}

#[derive(Clone)]
pub(crate) struct Gate(Arc<GateStruct>);

impl Gate {
    pub fn new() -> Gate {
        Gate(Arc::new(GateStruct {
            check: RwLock::new(None),
        }))
    }

    pub fn allows(&self, n: i32) -> bool {
        let c = self.check();
        if let Some(c) = &c {
            c(n) > 0
        } else {
            true
        }
    }

    pub fn passed(&self, n: i32) -> i32 {
        let c = self.check();
        if let Some(c) = &c {
            c(n)
        } else {
            n
        }
    }

    pub fn check(&self) -> Option<Arc<dyn Fn(i32) -> i32 + Send + Sync>> {
        self.0.check.read().unwrap().clone()
    }
}

temper_core::impl_any_value_trait!(Gate, []);

pub(crate) fn string_is_palindrome(s: impl ToArcString) -> bool {
    let s = s.to_arc_string();
    let mut i: usize = 0usize;
    let mut j = s.len();
    while (Some(i).cmp(&Some(j)) as i32) < 0 {
        j = temper_core::string::prev(&s, j);
        if temper_core::string::get(&s, i) != temper_core::string::get(&s, j) {
            return false;
        }
        i = temper_core::string::next(&s, i);
    }
    true
}

pub(crate) fn int_or_zero(n: Option<i32>) -> i32 {
    n.unwrap_or(0)
}

pub(crate) fn list_first_or<T: Clone + Send + Sync + 'static>(
    ls: impl temper_core::ToList<T>,
    d: T,
) -> T {
    let ls = ls.to_list();
    if temper_core::ListedTrait::is_empty(&ls) {
        d.clone()
    } else {
        temper_core::ListedTrait::get(&ls, 0)
    }
}

pub(crate) fn string_padded(
    s: impl ToArcString,
    n: i32,
    fill: Option<impl ToArcString>,
) -> Arc<String> {
    let s = s.to_arc_string();
    let fill = fill.map(|x| x.to_arc_string());
    let fill = fill.unwrap_or_else(|| Arc::new(".".to_string()));
    let mut out = s.clone();
    {
        let mut i: i32 = 0;
        while i < n {
            out = Arc::new(format!("{out}{fill}"));
            i = i.wrapping_add(1);
        }
    }
    out.clone()
}

pub(crate) fn gate_passed(g: Gate, s: impl ToArcString) -> Arc<String> {
    let s = s.to_arc_string();
    Arc::new(format!("{s} {}", g.allows(1)))
}
