use std::sync::{Arc, OnceLock, RwLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            A.set(Box::new(2, 3, None::<Arc<String>>))
                .unwrap_or_else(|_| panic!());
            let b = grown(a());
            println!("{} {b} {}", a(), area_of(Sized::new(b.clone())));
            println!("{}", {
                let spread = b.clone();
                Box(Arc::new(BoxStruct {
                    label: Some(Arc::new("named".to_string())),
                    ..(*spread.0).clone()
                }))
            });
            let wide = a().0.w > 1 && {
                let spread2 = a();
                Box(Arc::new(BoxStruct {
                    w: 10,
                    ..(*spread2.0).clone()
                }))
            }
            .area() > 20;
            let p: Two<i32> = Two::new(1, 2);
            let q: Two<i32> = {
                let spread3: Two<i32> = p.clone();
                Two::new(spread3.0.first, 5)
            };
            println!("{wide} {} {}", q.0.first, q.0.second);
            let nested = {
                let spread4 = {
                    let spread5 = a();
                    Box(Arc::new(BoxStruct {
                        w: 7,
                        ..(*spread5.0).clone()
                    }))
                };
                Box(Arc::new(BoxStruct {
                    h: 8,
                    ..(*spread4.0).clone()
                }))
            };
            println!(
                "{}",
                both(said("one", 1), {
                    let spread6 = a();
                    Box(Arc::new(BoxStruct {
                        h: said("two", 2),
                        ..(*spread6.0).clone()
                    }))
                })
            );
            println!(
                "{nested} {}",
                a().0.w > 5 || {
                    let spread7 = a();
                    Box(Arc::new(BoxStruct {
                        w: said("three", 3),
                        ..(*spread7.0).clone()
                    }))
                }
                .0
                .w == 3
            );
            println!("{}", widths(a(), 3));
            let s = Step::new(1, "s").next().next();
            let same: Two<i32> = {
                let spread8: Two<i32> = q.clone();
                Two(Arc::new(TwoStruct {
                    ..(*spread8.0).clone()
                }))
            };
            let words: Two<Arc<String>> =
                Two::new(Arc::new("a".to_string()), Arc::new("b".to_string()));
            let flipped: Two<Arc<String>> = swapped(words.clone());
            println!(
                "{} {} {}{} {}{}",
                s.0.n, s.0.tag, same.0.first, same.0.second, flipped.0.first, flipped.0.second
            );
            println!(
                "{} {}",
                size(doubled(Shape::new(Circle::new(3)))),
                size(doubled(Shape::new(Square::new(5))))
            );
            let made = applied(Arc::new(move |n: i32| {
                let spread = a();
                Box(Arc::new(BoxStruct {
                    h: n,
                    ..(*spread.0).clone()
                }))
            }));
            println!("{} {} {made}", picked(true), picked(false));
            let caught = {
                let spread9 = a();
                Box(Arc::new(BoxStruct {
                    w: positive(-1).unwrap_or(0),
                    h: positive(5).unwrap_or(-1),
                    ..(*spread9.0).clone()
                }))
            };
            println!(
                "{caught} {}",
                (|| Ok::<_, temper_core::Error>(sized(a(), 4)?.to_string()))()
                    .unwrap_or_else(|_| Arc::new("-".to_string()))
            );
            println!(
                "{}",
                (|| {
                    sized(a(), -1)?;
                    Ok::<_, temper_core::Error>(Arc::new("sized".to_string()))
                })()
                .unwrap_or_else(|_| Arc::new("bubbled".to_string()))
            );
            GATE.set(temper_core::PromiseBuilder::new())
                .unwrap_or_else(|_| panic!());
            crate::support::run_async(Arc::new(move || {
                let case_index: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                let later: Arc<RwLock<Option<Box>>> = Arc::new(RwLock::new(None));
                let spread: Arc<RwLock<Option<Box>>> = Arc::new(RwLock::new(None));
                let arg: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                let arg2: Arc<RwLock<i32>> = Arc::new(RwLock::new(0));
                let awaited: Arc<RwLock<Option<temper_core::Promise<i32>>>> =
                    Arc::new(RwLock::new(None));
                temper_core::SafeGenerator::from_fn({
                    let later = later.clone();
                    let spread = spread.clone();
                    let arg = arg.clone();
                    let arg2 = arg2.clone();
                    let case_index = case_index.clone();
                    let awaited = awaited.clone();
                    Arc::new(move |generator: temper_core::SafeGenerator<()>| loop {
                        let case_index_local = temper_core::read_locked(&case_index);
                        *case_index.write().unwrap() = -1;
                        match case_index_local {
                            0 => {
                                *spread.write().unwrap() = Some(a());
                                *arg.write().unwrap() =
                                    temper_core::read_locked(&spread).as_ref().unwrap().0.w;
                                *case_index.write().unwrap() = 3;
                            }
                            1 => {
                                *arg2.write().unwrap() = -1;
                                *case_index.write().unwrap() = 2;
                            }
                            2 => {
                                *later.write().unwrap() = Some(Box::new(
                                    temper_core::read_locked(&arg),
                                    temper_core::read_locked(&arg2),
                                    Some("later"),
                                ));
                                println!("{}", temper_core::read_locked(&later).as_ref().unwrap());
                                return None;
                            }
                            3 => {
                                *awaited.write().unwrap() = Some(gate().promise());
                                *case_index.write().unwrap() = 4;
                                crate::support::awake_upon(
                                    temper_core::read_locked(&awaited).unwrap(),
                                    generator.clone(),
                                );
                                return Some(());
                            }
                            4 => {
                                if (|| {
                                    *arg2.write().unwrap() = temper_core::read_locked(&awaited)
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
            gate().complete(6);
            Ok(())
        })
        .clone()
}

pub(crate) trait SizedTrait:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync
{
    #[allow(dead_code)]
    fn clone_boxed(&self) -> Sized;

    fn area(&self) -> i32;
}

#[derive(Clone)]
pub(crate) struct Sized(Arc<dyn SizedTrait>);

impl Sized {
    pub fn new(selfish: impl SizedTrait + 'static) -> Sized {
        Sized(Arc::new(selfish))
    }
}

impl SizedTrait for Sized {
    fn clone_boxed(&self) -> Sized {
        SizedTrait::clone_boxed(&*self.0)
    }

    fn area(&self) -> i32 {
        SizedTrait::area(&*self.0)
    }
}

temper_core::impl_any_value_trait_for_interface!(Sized);

impl std::ops::Deref for Sized {
    type Target = dyn SizedTrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

#[derive(Clone)]
struct BoxStruct {
    w: i32,
    h: i32,
    label: Option<Arc<String>>,
}

#[derive(Clone)]
pub(crate) struct Box(Arc<BoxStruct>);

impl Box {
    pub fn new(w: i32, h: i32, label: Option<impl ToArcString>) -> Box {
        let label = label.map(|x| x.to_arc_string());
        Box(Arc::new(BoxStruct { w, h, label }))
    }

    pub fn area(&self) -> i32 {
        self.0.w.wrapping_mul(self.0.h)
    }

    pub fn to_string(&self) -> Arc<String> {
        Arc::new(format!(
            "{} {}x{}",
            {
                let subject = self.0.label.clone();
                subject
                    .clone()
                    .unwrap_or_else(|| Arc::new("box".to_string()))
            },
            self.0.w,
            self.0.h
        ))
    }
}

impl std::fmt::Display for Box {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(&self.to_string())
    }
}

impl SizedTrait for Box {
    fn clone_boxed(&self) -> Sized {
        Sized::new(self.clone())
    }

    fn area(&self) -> i32 {
        self.area()
    }
}

temper_core::impl_any_value_trait!(Box, [Sized]);

#[derive(Clone)]
struct TwoStruct<T: Clone + Send + Sync + 'static> {
    first: T,
    second: T,
}

#[derive(Clone)]
pub(crate) struct Two<T: Clone + Send + Sync + 'static>(Arc<TwoStruct<T>>);

impl<T: Clone + Send + Sync + 'static> Two<T> {
    pub fn new(first: T, second: T) -> Two<T> {
        Two(Arc::new(TwoStruct { first, second }))
    }
}

temper_core::impl_any_value_trait!(Two<T>, []);

pub(crate) fn grown(b: Box) -> Box {
    let spread = b.clone();
    Box(Arc::new(BoxStruct {
        w: b.0.w.wrapping_add(1),
        ..(*spread.0).clone()
    }))
}

pub(crate) fn area_of(s: Sized) -> i32 {
    s.area()
}

static A: OnceLock<Box> = OnceLock::new();

pub(crate) fn a() -> Box {
    crate::support::initialized(|| A.get()).clone()
}

pub(crate) fn said(s: impl ToArcString, n: i32) -> i32 {
    let s = s.to_arc_string();
    println!("{s}");
    n
}

pub(crate) fn both(x: i32, b: Box) -> Arc<String> {
    Arc::new(format!("{x} {b}"))
}

pub(crate) fn widths(b: Box, n: i32) -> i32 {
    let mut t = b.clone();
    {
        let mut i: i32 = 0;
        while i < n {
            t = {
                let spread = t.clone();
                Box(Arc::new(BoxStruct {
                    w: t.0.h,
                    h: t.0.w.wrapping_add(1),
                    ..(*spread.0).clone()
                }))
            };
            i = i.wrapping_add(1);
        }
    }
    while {
        let spread = t.clone();
        Box(Arc::new(BoxStruct {
            h: 1,
            ..(*spread.0).clone()
        }))
    }
    .area() < 9
    {
        t = {
            let spread = t.clone();
            Box(Arc::new(BoxStruct {
                w: t.0.w.wrapping_add(1),
                ..(*spread.0).clone()
            }))
        };
    }
    {
        let mut i: i32 = 0;
        while i < 2 {
            t = {
                let spread = t.clone();
                let arg = said("h", t.0.w);
                let arg2 = said("w", t.0.h);
                let arg3 = spread.0.label.clone();
                Box::new(arg2, arg, arg3.clone())
            };
            i = i.wrapping_add(1);
        }
    }
    if t.0.w > 0 {
        let b = {
            let spread = b.clone();
            Box(Arc::new(BoxStruct {
                label: Some(Arc::new("inner".to_string())),
                ..(*spread.0).clone()
            }))
        };
        println!("{b}");
    }
    t.0.w
}

#[derive(Clone)]
struct StepStruct {
    n: i32,
    tag: Arc<String>,
}

#[derive(Clone)]
pub(crate) struct Step(Arc<StepStruct>);

impl Step {
    pub fn new(n: i32, tag: impl ToArcString) -> Step {
        let tag = tag.to_arc_string();
        Step(Arc::new(StepStruct { n, tag }))
    }

    pub fn next(&self) -> Step {
        let spread = self.clone();
        Step(Arc::new(StepStruct {
            n: self.0.n.wrapping_add(1),
            ..(*spread.0).clone()
        }))
    }
}

temper_core::impl_any_value_trait!(Step, []);

pub(crate) fn swapped<T: Clone + Send + Sync + 'static>(t: Two<T>) -> Two<T> {
    let _spread: Two<T> = t.clone();
    Two::new(t.0.second.clone(), t.0.first.clone())
}

pub(crate) trait ShapeTrait:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync
{
    #[allow(dead_code)]
    fn clone_boxed(&self) -> Shape;
}

#[derive(Clone)]
pub(crate) struct Shape(Arc<dyn ShapeTrait>);

impl Shape {
    pub fn new(selfish: impl ShapeTrait + 'static) -> Shape {
        Shape(Arc::new(selfish))
    }
}

impl ShapeTrait for Shape {
    fn clone_boxed(&self) -> Shape {
        ShapeTrait::clone_boxed(&*self.0)
    }
}

temper_core::impl_any_value_trait_for_interface!(Shape);

impl std::ops::Deref for Shape {
    type Target = dyn ShapeTrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

struct CircleStruct {
    r: i32,
}

#[derive(Clone)]
pub(crate) struct Circle(Arc<CircleStruct>);

impl Circle {
    pub fn new(r: i32) -> Circle {
        Circle(Arc::new(CircleStruct { r }))
    }
}

impl ShapeTrait for Circle {
    fn clone_boxed(&self) -> Shape {
        Shape::new(self.clone())
    }
}

temper_core::impl_any_value_trait!(Circle, [Shape]);

struct SquareStruct {
    side: i32,
}

#[derive(Clone)]
pub(crate) struct Square(Arc<SquareStruct>);

impl Square {
    pub fn new(side: i32) -> Square {
        Square(Arc::new(SquareStruct { side }))
    }
}

impl ShapeTrait for Square {
    fn clone_boxed(&self) -> Shape {
        Shape::new(self.clone())
    }
}

temper_core::impl_any_value_trait!(Square, [Shape]);

pub(crate) fn doubled(x: Shape) -> Shape {
    let made = if temper_core::is::<Circle>(x.clone()) {
        {
            let _spread = temper_core::cast::<Circle>(x.clone()).unwrap();
            Shape::new(Circle::new(
                temper_core::cast::<Circle>(x.clone())
                    .unwrap().0
                    .r
                    .wrapping_mul(2),
            ))
        }
    } else {
        Shape::new(if temper_core::is::<Square>(x.clone()) {
            {
                let _spread = temper_core::cast::<Square>(x.clone()).unwrap();
                Square::new(
                    temper_core::cast::<Square>(x.clone())
                        .unwrap().0
                        .side
                        .wrapping_mul(2),
                )
            }
        } else {
            panic!()
        })
    };
    made.clone()
}

pub(crate) fn size(x: Shape) -> i32 {
    if temper_core::is::<Circle>(x.clone()) {
        temper_core::cast::<Circle>(x.clone()).unwrap().0.r
    } else if temper_core::is::<Square>(x.clone()) {
        temper_core::cast::<Square>(x.clone()).unwrap().0.side
    } else {
        panic!()
    }
}

pub(crate) fn picked(c: bool) -> Box {
    if c {
        let spread = a();
        Box(Arc::new(BoxStruct {
            w: 9,
            ..(*spread.0).clone()
        }))
    } else {
        a()
    }
}

pub(crate) fn applied(f: Arc<dyn Fn(i32) -> Box + Send + Sync>) -> Box {
    f(4)
}

pub(crate) fn positive(n: i32) -> temper_core::Result<i32> {
    if n < 0 {
        return Err(temper_core::Error::new());
    }
    Ok(n)
}

pub(crate) fn sized(b: Box, n: i32) -> temper_core::Result<Box> {
    let spread = b.clone();
    Ok(Box(Arc::new(BoxStruct {
        w: positive(n)?,
        ..(*spread.0).clone()
    })))
}

static GATE: OnceLock<temper_core::PromiseBuilder<i32>> = OnceLock::new();

pub(crate) fn gate() -> temper_core::PromiseBuilder<i32> {
    crate::support::initialized(|| GATE.get()).clone()
}
