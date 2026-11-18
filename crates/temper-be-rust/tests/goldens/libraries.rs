use std::sync::{Arc, OnceLock, RwLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            CASING__FIRST
                .set(Casing::new("first"))
                .unwrap_or_else(|_| panic!());
            CASING__LAST
                .set(Casing::named("last"))
                .unwrap_or_else(|_| panic!());
            CASING__MARK
                .set(Arc::new("#".to_string()))
                .unwrap_or_else(|_| panic!());
            *TICKS.write().unwrap() = Some(0);
            EXTRAS.set(Arc::new(vec![1])).unwrap_or_else(|_| panic!());
            let grid = Grid::new(2, Arc::new(vec![1.0, 2.0, 3.0, 4.0]));
            println!(
                "{} {} {}",
                rows(grid.0.values.clone(), 2),
                temper_core::float64::to_string(half(3.0)),
                temper_core::float64::to_string(cube(8.0))
            );
            println!("{}", temper_core::float64::to_string(corner(grid.clone())));
            println!(
                "{}",
                temper_core::float64::to_string(total(grid.0.values.clone()))
            );
            let none = text(0);
            let some = count(3);
            println!(
                "{} {} {}",
                {
                    let part = none.clone();
                    part.clone().unwrap_or_else(|| Arc::new("null".to_string()))
                },
                {
                    let part2 = some;
                    if let Some(part2) = part2 {
                        Arc::new(part2.to_string())
                    } else {
                        Arc::new("null".to_string())
                    }
                },
                {
                    let part3 = {
                        let subject = maybe(0);
                        subject.as_ref().map(|subject| subject.0.ncols)
                    };
                    if let Some(part3) = part3 {
                        Arc::new(part3.to_string())
                    } else {
                        Arc::new("null".to_string())
                    }
                }
            );
            println!(
                "{} {} {}",
                maybe(1).is_some(),
                maybe(0).is_some(),
                Grid::parse("").is_none()
            );
            println!("{}", Casing::first());
            println!(
                "{}",
                temper_core::listed::join(
                    &mapped(grid.clone()),
                    Arc::new(",".to_string()),
                    &(|x: f64| temper_core::float64::to_string(x)),
                )
            );
            println!(
                "{}",
                temper_core::listed::join(
                    &cubes(grid.clone()),
                    Arc::new(",".to_string()),
                    &(|x: f64| temper_core::float64::to_string(x)),
                )
            );
            println!(
                "{} {} {}",
                {
                    let left = casing(1);
                    if let Some(left) = &left {
                        left.eq(Casing::first())
                    } else {
                        false
                    }
                },
                {
                    let left2 = casing(0);
                    if let Some(left2) = &left2 {
                        left2.eq(Casing::first())
                    } else {
                        false
                    }
                },
                !{
                    let left3 = casing(0);
                    let right = casing(0);
                    if let Some(left3) = &left3 {
                        if let Some(right) = &right {
                            left3.eq(right.clone())
                        } else {
                            false
                        }
                    } else {
                        right.is_none()
                    }
                }
            );
            println!("{} {}", captured(""), captured("2.5"));
            println!("{}", bidi().as_str() != "lookout");
            println!(
                "{} {} {}",
                word(1).as_str().cmp(word(2).as_str()) as i32,
                word(2).as_str().cmp(word(1).as_str()) as i32,
                word(2).as_str().cmp(word(3).as_str()) as i32
            );
            let named = word(2);
            println!(
                "{}",
                named == word(3) && format!("{named}!").as_str() != named.as_str()
            );
            let digits: temper_core::List<i32> = Arc::new(vec![1, 2, 4, 3]);
            println!(
                "{} {} {} {} {}",
                byte_at(digits.clone(), true, 1),
                byte_at(digits.clone(), false, 0),
                ascending(digits.clone()),
                first_drop(digits.clone()),
                size_with(digits.clone())
            );
            println!(
                "{} {} {} {}",
                tick_if(true),
                tick_if(false),
                ticked(),
                tick_while()
            );
            println!(
                "{} {} {} {} {} {} {} {}",
                shape_name(shape_of(1)),
                shape_name(shape_of(2)),
                temper_core::ListedTrait::len(&sizes(shape_of(1))),
                temper_core::ListedTrait::len(&sizes(shape_of(2))),
                {
                    let part4 = Dot::new(1).grown();
                    if let Some(part4) = part4 {
                        Arc::new(part4.to_string())
                    } else {
                        Arc::new("null".to_string())
                    }
                },
                {
                    let part5 = Dot::new(3).grown();
                    if let Some(part5) = part5 {
                        Arc::new(part5.to_string())
                    } else {
                        Arc::new("null".to_string())
                    }
                },
                Casing::last(),
                Casing::first().marked()
            );
            let carry = Carry::new(3);
            println!(
                "{} {} {} {} {} {} {}",
                carry.0.carrying,
                carry.0.state,
                Carry::new(0).0.carrying,
                {
                    let part6 = tagged(None::<Arc<String>>).0.label.clone();
                    part6
                        .clone()
                        .unwrap_or_else(|| Arc::new("null".to_string()))
                },
                {
                    let part7 = tagged(Some("x")).0.label.clone();
                    part7
                        .clone()
                        .unwrap_or_else(|| Arc::new("null".to_string()))
                },
                weight(42),
                weight(60)
            );
            let flags: temper_core::List<bool> = {
                let b: temper_core::ListBuilder<bool> = temper_core::listed::new_builder();
                temper_core::listed::add(&b, true, None);
                temper_core::listed::add(&b, false, None);
                temper_core::ListedTrait::to_list(&b)
            };
            let joiner = Joiner::new();
            joiner.add_word(Word::new("a"));
            joiner.add_flags(Arc::new(vec![true]));
            joiner.add_texts(Arc::new(vec![
                Arc::new("x".to_string()),
                Arc::new("y".to_string()),
            ]));
            let maybes: temper_core::List<Option<Arc<String>>> =
                Arc::new(vec![None, Some(Arc::new("z".to_string()))]);
            joiner.add_maybes(maybes.clone());
            println!("{}", joiner.out());
            let held: Held<Tag> = Held::new(Tag::new("a"), None);
            let other: Held<Tag> = Held::new(Tag::new("b"), Some(held.clone()));
            let nobody: Option<Held<Tag>> = None;
            println!(
                "{held} {} {} {} {} {}",
                {
                    let subject2 = {
                        let subject3: Option<Held<Tag>> = other.0.next.clone();
                        subject3.as_ref().map(|subject3| subject3.0.item.clone())
                    };
                    subject2.clone().unwrap_or_else(|| Tag::new("none"))
                },
                same_held(Some(held.clone()), Some(held.clone())),
                same_held(Some(held.clone()), Some(other.clone())),
                same_held(nobody.clone(), nobody.clone()),
                same_held(nobody.clone(), Some(held.clone()))
            );
            println!(
                "{} {} {}",
                effects_of("ab", 1, 2),
                Bundle::new(Arc::new(vec![]), Arc::new("2".to_string()))
                    .0
                    .first,
                temper_core::ListedTrait::len(&flags)
            );
            let counter = Ticks::new();
            let stepper: Counting<Arc<String>> = Counting::new(counter.clone());
            CountingTrait::step(&stepper);
            println!(
                "{} {} {} {}",
                temper_core::ListedTrait::get(&CountingTrait::step(&stepper), 0),
                counter.count(),
                Names::new().take(Tag::new("abc")),
                Quoting::new().escape_flag(true)
            );
            println!(
                "{} {} {} {} {}",
                {
                    let subject4 = problem(0);
                    subject4
                        .clone()
                        .unwrap_or_else(|| Arc::new("none".to_string()))
                },
                {
                    let subject5 = message(1);
                    subject5
                        .clone()
                        .unwrap_or_else(|| Arc::new("none".to_string()))
                },
                {
                    let subject6 = message(2);
                    subject6
                        .clone()
                        .unwrap_or_else(|| Arc::new("none".to_string()))
                },
                sum_parsed(Arc::new(vec![
                    Arc::new("1".to_string()),
                    Arc::new("x".to_string()),
                    Arc::new("2".to_string()),
                ])),
                backwards(Arc::new(vec![1, 2, 3]))
            );
            Ok(())
        })
        .clone()
}

struct GridStruct {
    ncols: i32,
    values: temper_core::List<f64>,
}

#[derive(Clone)]
pub struct Grid(Arc<GridStruct>);

impl Grid {
    pub fn new(ncols: i32, values: impl temper_core::ToList<f64>) -> Grid {
        let values = values.to_list();
        Grid(Arc::new(GridStruct { ncols, values }))
    }

    pub fn get(&self, i: i32, j: i32) -> temper_core::Result<f64> {
        Ok(temper_core::ListedTrait::get(
            &self.0.values,
            i.wrapping_mul(self.0.ncols).wrapping_add(j),
        ))
    }

    pub fn parse(s: impl ToArcString) -> Option<Grid> {
        let s = s.to_arc_string();
        (|| Ok::<_, temper_core::Error>(Some(parse_grid(s.clone())?)))().unwrap_or(None)
    }

    pub fn ncols(&self) -> i32 {
        self.0.ncols
    }

    pub fn values(&self) -> temper_core::List<f64> {
        self.0.values.clone()
    }
}

temper_core::impl_any_value_trait!(Grid, []);

pub fn parse_grid(s: impl ToArcString) -> temper_core::Result<Grid> {
    let s = s.to_arc_string();
    if s.as_str() == "" {
        return Err(temper_core::Error::new());
    }
    Ok(Grid::new(
        1,
        Arc::new(vec![temper_core::string::to_float64(&s)?]),
    ))
}

struct CasingStruct {
    name: Arc<String>,
}

#[derive(Clone)]
pub struct Casing(Arc<CasingStruct>);

static CASING__FIRST: OnceLock<Casing> = OnceLock::new();

static CASING__LAST: OnceLock<Casing> = OnceLock::new();

static CASING__MARK: OnceLock<Arc<String>> = OnceLock::new();

impl Casing {
    #[allow(clippy::should_implement_trait)]
    pub fn eq(&self, other: Casing) -> bool {
        self.0.name == other.0.name
    }

    fn new(name: impl ToArcString) -> Casing {
        let name = name.to_arc_string();
        Casing(Arc::new(CasingStruct { name }))
    }

    pub fn to_string(&self) -> Arc<String> {
        self.0.name.clone()
    }

    pub fn first() -> Casing {
        crate::support::initialized(|| CASING__FIRST.get()).clone()
    }

    fn named(name: impl ToArcString) -> Casing {
        let name = name.to_arc_string();
        Casing::new(name.clone())
    }

    pub fn last() -> Casing {
        crate::support::initialized(|| CASING__LAST.get()).clone()
    }

    fn mark() -> Arc<String> {
        crate::support::initialized(|| CASING__MARK.get()).clone()
    }

    pub fn marked(&self) -> Arc<String> {
        Arc::new(format!("{}{}", Casing::mark(), self.0.name))
    }

    pub fn name(&self) -> Arc<String> {
        self.0.name.clone()
    }
}

impl std::fmt::Display for Casing {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(&self.to_string())
    }
}

impl PartialEq for Casing {
    fn eq(&self, other: &Casing) -> bool {
        Casing::eq(self, other.clone())
    }
}

temper_core::impl_any_value_trait!(Casing, []);

pub trait ShapeTrait: temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync {
    fn clone_boxed(&self) -> Shape;

    fn size(&self) -> i32;
}

#[derive(Clone)]
pub struct Shape(Arc<dyn ShapeTrait>);

impl Shape {
    pub fn new(selfish: impl ShapeTrait + 'static) -> Shape {
        Shape(Arc::new(selfish))
    }
}

impl ShapeTrait for Shape {
    fn clone_boxed(&self) -> Shape {
        ShapeTrait::clone_boxed(&*self.0)
    }

    fn size(&self) -> i32 {
        ShapeTrait::size(&*self.0)
    }
}

temper_core::impl_any_value_trait_for_interface!(Shape);

impl std::ops::Deref for Shape {
    type Target = dyn ShapeTrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

struct DotStruct {
    size: i32,
}

#[derive(Clone)]
pub struct Dot(Arc<DotStruct>);

impl Dot {
    pub fn new(size: i32) -> Dot {
        Dot(Arc::new(DotStruct { size }))
    }

    pub fn bigger(&self) -> Option<Dot> {
        if self.0.size < 3 {
            Some(Dot::new(self.0.size.wrapping_add(1)))
        } else {
            None
        }
    }

    pub fn grown(&self) -> Option<i32> {
        let subject = self.bigger();
        subject.as_ref().map(|subject| subject.scaled(10))
    }

    pub fn scaled(&self, k: i32) -> i32 {
        self.0.size.wrapping_mul(k)
    }

    pub fn size(&self) -> i32 {
        self.0.size
    }
}

impl ShapeTrait for Dot {
    fn clone_boxed(&self) -> Shape {
        Shape::new(self.clone())
    }

    fn size(&self) -> i32 {
        self.size()
    }
}

temper_core::impl_any_value_trait!(Dot, [Shape]);

struct LineStruct {
    size: i32,
}

#[derive(Clone)]
pub struct Line(Arc<LineStruct>);

impl Line {
    pub fn new(size: i32) -> Line {
        Line(Arc::new(LineStruct { size }))
    }

    pub fn size(&self) -> i32 {
        self.0.size
    }
}

impl ShapeTrait for Line {
    fn clone_boxed(&self) -> Shape {
        Shape::new(self.clone())
    }

    fn size(&self) -> i32 {
        self.size()
    }
}

temper_core::impl_any_value_trait!(Line, [Shape]);

struct CarryStruct {
    carrying: bool,
    state: i32,
}

#[derive(Clone)]
pub struct Carry(Arc<CarryStruct>);

impl Carry {
    pub fn new(start: i32) -> Carry {
        let mut carrying: bool;
        let mut state: i32;
        carrying = false;
        state = 0;
        if start != 0 {
            carrying = true;
            state = start;
        }
        Carry(Arc::new(CarryStruct { carrying, state }))
    }

    pub fn carrying(&self) -> bool {
        self.0.carrying
    }

    pub fn state(&self) -> i32 {
        self.0.state
    }
}

temper_core::impl_any_value_trait!(Carry, []);

struct TaggedStruct {
    label: Option<Arc<String>>,
}

#[derive(Clone)]
pub struct Tagged(Arc<TaggedStruct>);

impl Tagged {
    pub fn new(label: Option<impl ToArcString>) -> Tagged {
        let label = label.map(|x| x.to_arc_string());
        Tagged(Arc::new(TaggedStruct { label }))
    }

    pub fn label(&self) -> Option<Arc<String>> {
        self.0.label.clone()
    }
}

temper_core::impl_any_value_trait!(Tagged, []);

pub(crate) fn tagged(label: Option<impl ToArcString>) -> Tagged {
    let label = label.map(|x| x.to_arc_string());
    Tagged::new(label.clone())
}

pub(crate) fn weight(c: i32) -> i32 {
    let base: i32 = if c == 42 { 0 } else { 6 };
    (if c > 50 { 1i32 } else { 2 })
        .wrapping_add(base)
        .wrapping_add(c)
}

pub(crate) fn shape_name(s: Shape) -> Arc<String> {
    let name = if temper_core::is::<Dot>(s.clone()) {
        Arc::new("dot".to_string())
    } else if temper_core::is::<Line>(s.clone()) {
        Arc::new("line".to_string())
    } else {
        panic!()
    };
    name.clone()
}

pub(crate) fn shape_of(n: i32) -> Shape {
    let shape = if n > 1 {
        Shape::new(Line::new(n))
    } else {
        Shape::new(Dot::new(n))
    };
    shape.clone()
}

pub(crate) fn sizes(s: Shape) -> temper_core::List<i32> {
    let found: temper_core::List<i32> = if temper_core::is::<Dot>(s.clone()) {
        Arc::new(vec![
            temper_core::cast::<Dot>(s.clone()).unwrap().0.size,
            temper_core::cast::<Dot>(s.clone()).unwrap().0.size,
        ])
    } else {
        Arc::new(vec![])
    };
    found.clone()
}

pub(crate) fn rows(values: impl temper_core::ToList<f64>, ncols: i32) -> i32 {
    let values = values.to_list();
    temper_core::int_div(temper_core::ListedTrait::len(&values), ncols).unwrap_or_else(|_| panic!())
}

pub(crate) fn scaled(grid: Grid) -> temper_core::Result<Grid> {
    if grid.0.ncols == 0 {
        return Err(temper_core::Error::new());
    }
    Ok(grid.clone())
}

pub(crate) fn cubes(grid: Grid) -> temper_core::List<f64> {
    (|| {
        let more: temper_core::List<f64> =
            temper_core::listed::map(&scaled(grid.clone())?.0.values, &(|x: f64| x * x * x));
        Ok::<_, temper_core::Error>(more.clone())
    })()
    .unwrap_or_else(|_| panic!())
}

pub(crate) fn each(values: impl temper_core::ToList<f64>, f: Arc<dyn Fn(f64) + Send + Sync>) {
    let values = values.to_list();
    let mut i: i32 = 0;
    while i < temper_core::ListedTrait::len(&values) {
        f(temper_core::ListedTrait::get(&values, i));
        i = i.wrapping_add(1);
    }
}

pub(crate) fn total(values: impl temper_core::ToList<f64>) -> f64 {
    let values = values.to_list();
    let sum: Arc<RwLock<f64>> = Arc::new(RwLock::new(0.0));
    each(values.clone(), {
        let sum = sum.clone();
        Arc::new(move |x: f64| {
            if (|| {
                let part = temper_core::float64::div(x, 2.0)?;
                *sum.write().unwrap() = temper_core::read_locked(&sum) + part;
                Ok::<_, temper_core::Error>(())
            })()
            .is_err()
            {
                panic!();
            }
        })
    });
    temper_core::read_locked(&sum)
}

pub(crate) fn half(x: f64) -> f64 {
    temper_core::float64::div(x, 2.0).unwrap_or_else(|_| panic!())
}

pub(crate) fn cube(x: f64) -> f64 {
    x * 0.25
}

pub(crate) fn corner(grid: Grid) -> f64 {
    grid.get(1, 1).unwrap_or(-1.0)
}

pub(crate) fn maybe(n: i32) -> Option<Grid> {
    if n > 0 {
        Some(Grid::new(n, Arc::new(vec![1.0])))
    } else {
        None
    }
}

pub(crate) fn mapped(grid: Grid) -> temper_core::List<f64> {
    temper_core::listed::map(
        &scaled(grid.clone()).unwrap_or_else(|_| panic!()).0.values,
        &(|x: f64| {
            if temper_core::float64::cmp(x, 1.5) > 0 {
                x * 2.0
            } else {
                x
            }
        }),
    )
}

pub(crate) fn byte_at(digits: impl temper_core::ToList<i32>, short: bool, c: i32) -> i32 {
    let digits = digits.to_list();
    if short {
        temper_core::ListedTrait::get(&digits, c).wrapping_mul(17)
    } else {
        temper_core::ListedTrait::get(&digits, 2i32.wrapping_mul(c))
            .wrapping_mul(16)
            .wrapping_add(temper_core::ListedTrait::get(
                &digits,
                2i32.wrapping_mul(c).wrapping_add(1),
            ))
    }
}

pub(crate) fn ascending(digits: impl temper_core::ToList<i32>) -> bool {
    let digits = digits.to_list();
    if temper_core::ListedTrait::len(&digits) > 1 {
        temper_core::ListedTrait::get(&digits, 0).wrapping_add(1)
            == temper_core::ListedTrait::get(&digits, 1)
    } else {
        false
    }
}

pub(crate) fn first_drop(digits: impl temper_core::ToList<i32>) -> i32 {
    let digits = digits.to_list();
    let mut i: i32 = 1;
    while i < temper_core::ListedTrait::len(&digits)
        && temper_core::ListedTrait::get(&digits, i.wrapping_sub(1))
            <= temper_core::ListedTrait::get(&digits, i)
    {
        i = i.wrapping_add(1);
    }
    i
}

static TICKS: RwLock<Option<i32>> = RwLock::new(None);

pub(crate) fn ticks() -> i32 {
    crate::support::initialized(|| *TICKS.read().unwrap())
}

pub(crate) fn tick() -> i32 {
    crate::support::initialized(|| {
        let mut ticks = TICKS.write().unwrap();
        *ticks = Some((*ticks)?.wrapping_add(1));
        Some(())
    });
    ticks()
}

pub(crate) fn tick_if(short: bool) -> i32 {
    if short {
        tick()
    } else {
        tick().wrapping_mul(10).wrapping_add(tick())
    }
}

pub(crate) fn ticked() -> bool {
    if ticks() > 0 {
        tick().wrapping_mul(10).wrapping_add(tick()) > 0
    } else {
        false
    }
}

pub(crate) fn tick_while() -> i32 {
    let mut i: i32 = 0;
    while i < 3 && tick() < tick() {
        i = i.wrapping_add(1);
    }
    i
}

static EXTRAS: OnceLock<temper_core::List<i32>> = OnceLock::new();

pub(crate) fn extras() -> temper_core::List<i32> {
    crate::support::initialized(|| EXTRAS.get()).clone()
}

pub fn size_with<T: Clone + Send + Sync + 'static>(items: impl temper_core::ToList<T>) -> i32 {
    let items = items.to_list();
    temper_core::ListedTrait::len(&items).wrapping_add(temper_core::ListedTrait::len(&extras()))
}

pub(crate) fn text(n: i32) -> Option<Arc<String>> {
    if n > 0 {
        Some(Arc::new("x".to_string()))
    } else {
        None
    }
}

pub(crate) fn count(n: i32) -> Option<i32> {
    if n > 0 {
        Some(n)
    } else {
        None
    }
}

pub(crate) fn casing(n: i32) -> Option<Casing> {
    if n > 0 {
        Some(Casing::first())
    } else {
        None
    }
}

pub(crate) fn captured(s: impl ToArcString) -> Arc<String> {
    let s = s.to_arc_string();
    let found = (|| Ok::<_, temper_core::Error>(Some(parse_grid(s.clone())?)))().unwrap_or(None);
    temper_core::listed::join(
        &temper_core::listed::map(&Arc::new(vec![1, 2]), &(|_x: i32| found.is_none())),
        Arc::new(",".to_string()),
        &(|b: bool| Arc::new(b.to_string())),
    )
}

pub(crate) fn bidi() -> Arc<String> {
    Arc::new("look\u{202e}out".to_string())
}

pub(crate) fn word(n: i32) -> Arc<String> {
    if n > 1 {
        Arc::new("b".to_string())
    } else {
        Arc::new("a".to_string())
    }
}

struct WordStruct {
    text: Arc<String>,
}

#[derive(Clone)]
pub struct Word(Arc<WordStruct>);

impl Word {
    pub fn new(text: impl ToArcString) -> Word {
        let text = text.to_arc_string();
        Word(Arc::new(WordStruct { text }))
    }

    pub fn text(&self) -> Arc<String> {
        self.0.text.clone()
    }
}

temper_core::impl_any_value_trait!(Word, []);

struct JoinerStruct {
    out: RwLock<Arc<String>>,
}

#[derive(Clone)]
pub struct Joiner(Arc<JoinerStruct>);

impl Joiner {
    #[allow(clippy::new_without_default)]
    pub fn new() -> Joiner {
        Joiner(Arc::new(JoinerStruct {
            out: RwLock::new(Arc::new(String::new())),
        }))
    }

    pub fn add_word(&self, w: Word) {
        *self.0.out.write().unwrap() = Arc::new(format!("{}w{}", self.out(), w.0.text));
    }

    pub fn add_flags(&self, x: impl temper_core::ToList<bool>) {
        let x = x.to_list();
        *self.0.out.write().unwrap() = Arc::new(format!(
            "{}f{}",
            self.out(),
            temper_core::ListedTrait::len(&x)
        ));
    }

    pub fn add_texts(&self, x: impl temper_core::ToList<Arc<String>>) {
        let x = x.to_list();
        *self.0.out.write().unwrap() = Arc::new(format!(
            "{}t{}",
            self.out(),
            temper_core::ListedTrait::len(&x)
        ));
    }

    pub fn add_maybes(&self, x: impl temper_core::ToList<Option<Arc<String>>>) {
        let x = x.to_list();
        *self.0.out.write().unwrap() = Arc::new(format!(
            "{}m{}",
            self.out(),
            temper_core::ListedTrait::len(&x)
        ));
    }

    pub fn out(&self) -> Arc<String> {
        self.0.out.read().unwrap().clone()
    }

    pub fn set_out(&self, value: impl ToArcString) {
        let value = value.to_arc_string();
        *self.0.out.write().unwrap() = value;
    }
}

temper_core::impl_any_value_trait!(Joiner, []);

pub trait ShownTrait: temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync {
    fn clone_boxed(&self) -> Shown;

    fn to_string(&self) -> Arc<String>;
}

#[derive(Clone)]
pub struct Shown(Arc<dyn ShownTrait>);

impl Shown {
    pub fn new(selfish: impl ShownTrait + 'static) -> Shown {
        Shown(Arc::new(selfish))
    }
}

impl ShownTrait for Shown {
    fn clone_boxed(&self) -> Shown {
        ShownTrait::clone_boxed(&*self.0)
    }

    fn to_string(&self) -> Arc<String> {
        ShownTrait::to_string(&*self.0)
    }
}

temper_core::impl_any_value_trait_for_interface!(Shown);

impl std::ops::Deref for Shown {
    type Target = dyn ShownTrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

struct TagStruct {
    name: Arc<String>,
}

#[derive(Clone)]
pub struct Tag(Arc<TagStruct>);

impl Tag {
    pub fn new(name: impl ToArcString) -> Tag {
        let name = name.to_arc_string();
        Tag(Arc::new(TagStruct { name }))
    }

    pub fn to_string(&self) -> Arc<String> {
        Arc::new(format!("<{}>", self.0.name))
    }

    pub fn name(&self) -> Arc<String> {
        self.0.name.clone()
    }
}

impl std::fmt::Display for Tag {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(&self.to_string())
    }
}

impl ShownTrait for Tag {
    fn clone_boxed(&self) -> Shown {
        Shown::new(self.clone())
    }

    fn to_string(&self) -> Arc<String> {
        self.to_string()
    }
}

temper_core::impl_any_value_trait!(Tag, [Shown]);

struct HeldStruct<T: ShownTrait + Clone + Send + Sync + 'static> {
    item: T,
    next: Option<Held<T>>,
}

#[derive(Clone)]
pub struct Held<T: ShownTrait + Clone + Send + Sync + 'static>(Arc<HeldStruct<T>>);

impl<T: ShownTrait + Clone + Send + Sync + 'static> Held<T> {
    pub fn new(item: T, next: Option<Held<T>>) -> Held<T> {
        Held(Arc::new(HeldStruct { item, next }))
    }

    pub fn to_string(&self) -> Arc<String> {
        Arc::new(format!("Held({})", ShownTrait::to_string(&self.0.item)))
    }

    pub fn item(&self) -> T {
        self.0.item.clone()
    }

    pub fn next(&self) -> Option<Held<T>> {
        self.0.next.clone()
    }
}

impl<T: ShownTrait + Clone + Send + Sync + 'static> std::fmt::Display for Held<T> {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(&self.to_string())
    }
}

temper_core::impl_any_value_trait!(Held<T>, [] where T: ShownTrait);

pub trait EffectTrait<T: Clone + Send + Sync + 'static>:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync
{
    fn clone_boxed(&self) -> Effect<T>;
}

#[derive(Clone)]
pub struct Effect<T: Clone + Send + Sync + 'static>(Arc<dyn EffectTrait<T>>);

impl<T: Clone + Send + Sync + 'static> Effect<T> {
    pub fn new(selfish: impl EffectTrait<T> + 'static) -> Effect<T> {
        Effect(Arc::new(selfish))
    }
}

impl<T: Clone + Send + Sync + 'static> EffectTrait<T> for Effect<T> {
    fn clone_boxed(&self) -> Effect<T> {
        EffectTrait::clone_boxed(&*self.0)
    }
}

temper_core::impl_any_value_trait_for_interface!(Effect<T>);

impl<T: Clone + Send + Sync + 'static> std::ops::Deref for Effect<T> {
    type Target = dyn EffectTrait<T>;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

struct PlainStruct<T: Clone + Send + Sync + 'static> {
    text: Arc<String>,
    phantom_t: std::marker::PhantomData<T>,
}

#[derive(Clone)]
pub struct Plain<T: Clone + Send + Sync + 'static>(Arc<PlainStruct<T>>);

impl<T: Clone + Send + Sync + 'static> Plain<T> {
    pub fn new(text: impl ToArcString) -> Plain<T> {
        let text = text.to_arc_string();
        Plain(Arc::new(PlainStruct {
            text,
            phantom_t: std::marker::PhantomData,
        }))
    }

    pub fn text(&self) -> Arc<String> {
        self.0.text.clone()
    }
}

impl<T: Clone + Send + Sync + 'static> EffectTrait<T> for Plain<T> {
    fn clone_boxed(&self) -> Effect<T> {
        Effect::new(self.clone())
    }
}

temper_core::impl_any_value_trait!(Plain<T>, [Effect<T>]);

struct HappenedStruct<T: Clone + Send + Sync + 'static> {
    event: T,
}

#[derive(Clone)]
pub struct Happened<T: Clone + Send + Sync + 'static>(Arc<HappenedStruct<T>>);

impl<T: Clone + Send + Sync + 'static> Happened<T> {
    pub fn new(event: T) -> Happened<T> {
        Happened(Arc::new(HappenedStruct { event }))
    }

    pub fn event(&self) -> T {
        self.0.event.clone()
    }
}

impl<T: Clone + Send + Sync + 'static> EffectTrait<T> for Happened<T> {
    fn clone_boxed(&self) -> Effect<T> {
        Effect::new(self.clone())
    }
}

temper_core::impl_any_value_trait!(Happened<T>, [Effect<T>]);

struct BundleStruct<T: Clone + Send + Sync + 'static> {
    effects: temper_core::List<Effect<T>>,
    first: T,
}

#[derive(Clone)]
pub struct Bundle<T: Clone + Send + Sync + 'static>(Arc<BundleStruct<T>>);

impl<T: Clone + Send + Sync + 'static> Bundle<T> {
    pub fn new(effects: impl temper_core::ToList<Effect<T>>, first: T) -> Bundle<T> {
        let effects = effects.to_list();
        Bundle(Arc::new(BundleStruct { effects, first }))
    }

    pub fn effects(&self) -> temper_core::List<Effect<T>> {
        self.0.effects.clone()
    }

    pub fn first(&self) -> T {
        self.0.first.clone()
    }
}

temper_core::impl_any_value_trait!(Bundle<T>, []);

pub(crate) fn join_effects<T: Clone + Send + Sync + 'static>(
    into: temper_core::ListBuilder<Effect<T>>,
    more: impl temper_core::ToList<Effect<T>>,
) -> i32 {
    let more = more.to_list();
    {
        let length = temper_core::ListedTrait::len(&more);
        let mut i: i32 = 0;
        while i < length {
            let e: Effect<T> = temper_core::ListedTrait::get(&more, i);
            i = i.wrapping_add(1);
            temper_core::listed::add(&into, e.clone(), None);
        }
    }
    temper_core::ListedTrait::len(&into)
}

pub fn effects_of<T: Clone + Send + Sync + 'static>(
    text: impl ToArcString,
    event: T,
    other: T,
) -> Arc<String> {
    let text = text.to_arc_string();
    let into: temper_core::ListBuilder<Effect<T>> = temper_core::listed::new_builder();
    join_effects(into.clone(), Arc::new(vec![]));
    join_effects(
        into.clone(),
        Arc::new(vec![Effect::new(Plain::new(text.clone()))]),
    );
    join_effects(
        into.clone(),
        Arc::new(vec![
            Effect::new(Happened::new(event.clone())),
            Effect::new(Plain::new("!")),
            Effect::new(Happened::new(other.clone())),
        ]),
    );
    let mut shown = Arc::new(String::new());
    {
        let list: temper_core::List<Effect<T>> = temper_core::ListedTrait::to_list(&into);
        let length = temper_core::ListedTrait::len(&list);
        let mut i: i32 = 0;
        while i < length {
            let e: Effect<T> = temper_core::ListedTrait::get(&list, i);
            i = i.wrapping_add(1);
            if temper_core::is::<Plain<T>>(e.clone()) {
                shown = Arc::new(format!(
                    "{shown}{}",
                    temper_core::cast::<Plain<T>>(e.clone()).unwrap().0.text
                ));
            } else {
                shown = Arc::new(format!("{shown}*"));
            }
        }
    }
    shown.clone()
}

pub fn same_held<T: ShownTrait + Clone + Send + Sync + 'static>(
    a: Option<Held<T>>,
    b: Option<Held<T>>,
) -> bool {
    {
        let left: Option<Held<T>> = a.clone();
        let right: Option<Held<T>> = b.clone();
        if let Some(left) = &left {
            if let Some(right) = &right {
                temper_core::AnyValueTrait::ptr_id(&left.clone())
                    == temper_core::AnyValueTrait::ptr_id(&right.clone())
            } else {
                false
            }
        } else {
            right.is_none()
        }
    }
}

pub trait StepperTrait<T: Clone + Send + Sync + 'static>:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync
{
    fn clone_boxed(&self) -> Stepper<T>;

    fn step(&self) -> temper_core::List<T>;
}

#[derive(Clone)]
pub struct Stepper<T: Clone + Send + Sync + 'static>(Arc<dyn StepperTrait<T>>);

impl<T: Clone + Send + Sync + 'static> Stepper<T> {
    pub fn new(selfish: impl StepperTrait<T> + 'static) -> Stepper<T> {
        Stepper(Arc::new(selfish))
    }
}

impl<T: Clone + Send + Sync + 'static> StepperTrait<T> for Stepper<T> {
    fn clone_boxed(&self) -> Stepper<T> {
        StepperTrait::clone_boxed(&*self.0)
    }

    fn step(&self) -> temper_core::List<T> {
        StepperTrait::step(&*self.0)
    }
}

temper_core::impl_any_value_trait_for_interface!(Stepper<T>);

impl<T: Clone + Send + Sync + 'static> std::ops::Deref for Stepper<T> {
    type Target = dyn StepperTrait<T>;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

pub trait CountingTrait<T: Clone + Send + Sync + 'static>:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync + StepperTrait<T>
{
    fn clone_boxed(&self) -> Counting<T>;

    fn count(&self) -> i32;

    fn set_count(&self, c: i32);

    fn made(&self) -> T;

    fn step(&self) -> temper_core::List<T> {
        self.set_count(self.count().wrapping_add(1));
        Arc::new(vec![self.made()])
    }
}

#[derive(Clone)]
pub struct Counting<T: Clone + Send + Sync + 'static>(Arc<dyn CountingTrait<T>>);

impl<T: Clone + Send + Sync + 'static> Counting<T> {
    pub fn new(selfish: impl CountingTrait<T> + 'static) -> Counting<T> {
        Counting(Arc::new(selfish))
    }
}

impl<T: Clone + Send + Sync + 'static> CountingTrait<T> for Counting<T> {
    fn clone_boxed(&self) -> Counting<T> {
        CountingTrait::clone_boxed(&*self.0)
    }

    fn count(&self) -> i32 {
        CountingTrait::count(&*self.0)
    }

    fn set_count(&self, c: i32) {
        CountingTrait::set_count(&*self.0, c)
    }

    fn made(&self) -> T {
        CountingTrait::made(&*self.0)
    }

    fn step(&self) -> temper_core::List<T> {
        CountingTrait::step(&*self.0)
    }
}

impl<T: Clone + Send + Sync + 'static> StepperTrait<T> for Counting<T> {
    fn clone_boxed(&self) -> Stepper<T> {
        StepperTrait::clone_boxed(&*self.0)
    }

    fn step(&self) -> temper_core::List<T> {
        StepperTrait::step(&*self.0)
    }
}

temper_core::impl_any_value_trait_for_interface!(Counting<T>);

impl<T: Clone + Send + Sync + 'static> std::ops::Deref for Counting<T> {
    type Target = dyn CountingTrait<T>;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

struct TicksStruct {
    n: RwLock<i32>,
}

#[derive(Clone)]
pub struct Ticks(Arc<TicksStruct>);

impl Ticks {
    #[allow(clippy::new_without_default)]
    pub fn new() -> Ticks {
        Ticks(Arc::new(TicksStruct { n: RwLock::new(0) }))
    }

    pub fn count(&self) -> i32 {
        self.n()
    }

    pub fn set_count(&self, c: i32) {
        *self.0.n.write().unwrap() = c;
    }

    pub fn made(&self) -> Arc<String> {
        Arc::new(format!("tick{}", self.n()))
    }

    fn n(&self) -> i32 {
        *self.0.n.read().unwrap()
    }
}

impl CountingTrait<Arc<String>> for Ticks {
    fn clone_boxed(&self) -> Counting<Arc<String>> {
        Counting::new(self.clone())
    }

    fn count(&self) -> i32 {
        self.count()
    }

    fn set_count(&self, c: i32) {
        self.set_count(c)
    }

    fn made(&self) -> Arc<String> {
        self.made()
    }
}

impl StepperTrait<Arc<String>> for Ticks {
    fn clone_boxed(&self) -> Stepper<Arc<String>> {
        Stepper::new(self.clone())
    }

    fn step(&self) -> temper_core::List<Arc<String>> {
        CountingTrait::step(self)
    }
}

temper_core::impl_any_value_trait!(Ticks, [Counting<Arc<String>>, Stepper<Arc<String>>]);

pub trait SinkTrait<T: Clone + Send + Sync + 'static>:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync
{
    fn clone_boxed(&self) -> Sink<T>;

    fn take(&self, e: T) -> i32;
}

#[derive(Clone)]
pub struct Sink<T: Clone + Send + Sync + 'static>(Arc<dyn SinkTrait<T>>);

impl<T: Clone + Send + Sync + 'static> Sink<T> {
    pub fn new(selfish: impl SinkTrait<T> + 'static) -> Sink<T> {
        Sink(Arc::new(selfish))
    }
}

impl<T: Clone + Send + Sync + 'static> SinkTrait<T> for Sink<T> {
    fn clone_boxed(&self) -> Sink<T> {
        SinkTrait::clone_boxed(&*self.0)
    }

    fn take(&self, e: T) -> i32 {
        SinkTrait::take(&*self.0, e)
    }
}

temper_core::impl_any_value_trait_for_interface!(Sink<T>);

impl<T: Clone + Send + Sync + 'static> std::ops::Deref for Sink<T> {
    type Target = dyn SinkTrait<T>;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

struct NamesStruct {}

#[derive(Clone)]
pub struct Names(Arc<NamesStruct>);

impl Names {
    #[allow(clippy::new_without_default)]
    pub fn new() -> Names {
        Names(Arc::new(NamesStruct {}))
    }

    pub fn take(&self, e: Tag) -> i32 {
        if e.0.name.as_str() == "abc" {
            3
        } else {
            0
        }
    }
}

impl SinkTrait<Tag> for Names {
    fn clone_boxed(&self) -> Sink<Tag> {
        Sink::new(self.clone())
    }

    fn take(&self, e: Tag) -> i32 {
        self.take(e)
    }
}

temper_core::impl_any_value_trait!(Names, [Sink<Tag>]);

pub trait EscapesTrait: temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync {
    fn clone_boxed(&self) -> Escapes;

    fn escape(&self, s: Option<Arc<String>>) -> Arc<String>;

    fn escape_flag(&self, b: bool) -> Arc<String> {
        self.escape(Some(if b {
            Arc::new("T".to_string())
        } else {
            Arc::new("F".to_string())
        }))
    }
}

#[derive(Clone)]
pub struct Escapes(Arc<dyn EscapesTrait>);

impl Escapes {
    pub fn new(selfish: impl EscapesTrait + 'static) -> Escapes {
        Escapes(Arc::new(selfish))
    }
}

impl EscapesTrait for Escapes {
    fn clone_boxed(&self) -> Escapes {
        EscapesTrait::clone_boxed(&*self.0)
    }

    fn escape(&self, s: Option<Arc<String>>) -> Arc<String> {
        EscapesTrait::escape(&*self.0, s)
    }

    fn escape_flag(&self, b: bool) -> Arc<String> {
        EscapesTrait::escape_flag(&*self.0, b)
    }
}

temper_core::impl_any_value_trait_for_interface!(Escapes);

impl std::ops::Deref for Escapes {
    type Target = dyn EscapesTrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

struct QuotingStruct {}

#[derive(Clone)]
pub struct Quoting(Arc<QuotingStruct>);

impl Quoting {
    #[allow(clippy::new_without_default)]
    pub fn new() -> Quoting {
        Quoting(Arc::new(QuotingStruct {}))
    }

    pub fn escape(&self, s: Option<impl ToArcString>) -> Arc<String> {
        let s = s.map(|x| x.to_arc_string());
        Arc::new(format!(
            "'{}'",
            s.clone().unwrap_or_else(|| Arc::new("null".to_string()))
        ))
    }
}

impl EscapesTrait for Quoting {
    fn clone_boxed(&self) -> Escapes {
        Escapes::new(self.clone())
    }

    fn escape(&self, s: Option<Arc<String>>) -> Arc<String> {
        self.escape(s)
    }
}

temper_core::impl_any_value_trait!(Quoting, [Escapes]);

pub(crate) fn problem(n: i32) -> Option<Arc<String>> {
    let p: Option<Arc<String>> = if n == 0 { None } else { panic!() };
    p.clone()
}

pub(crate) fn message(n: i32) -> Option<Arc<String>> {
    let p = if n == 0 {
        None
    } else if n == 1 {
        Some(Arc::new("one".to_string()))
    } else if n == 2 {
        None
    } else {
        panic!()
    };
    p.clone()
}

pub(crate) fn sum_parsed(xs: impl temper_core::ToList<Arc<String>>) -> i32 {
    let xs = xs.to_list();
    let mut total: i32 = 0;
    {
        let mut i: i32 = 0;
        while i < temper_core::ListedTrait::len(&xs) {
            let x = temper_core::ListedTrait::get(&xs, i);
            let n: i32;
            let mut held: Option<i32> = None;
            let _ = (|| {
                held = Some(temper_core::string::to_int(&x, None)?);
                Ok::<_, temper_core::Error>(())
            })();
            if let Some(held) = held {
                n = held;
            } else {
                {
                    i = i.wrapping_add(1);
                    continue;
                }
            }
            total = total.wrapping_add(n);
            i = i.wrapping_add(1);
        }
    }
    {
        let length = temper_core::ListedTrait::len(&xs);
        let mut i: i32 = 0;
        while i < length {
            let x = temper_core::ListedTrait::get(&xs, i);
            i = i.wrapping_add(1);
            let m: i32;
            let mut held: Option<i32> = None;
            let _ = (|| {
                held = Some(temper_core::string::to_int(&x, None)?);
                Ok::<_, temper_core::Error>(())
            })();
            if let Some(held) = held {
                m = held;
            } else {
                continue;
            }
            total = total.wrapping_add(m.wrapping_mul(10));
        }
    }
    total
}

pub(crate) fn backwards(xs: impl temper_core::ToList<i32>) -> i32 {
    let xs = xs.to_list();
    let mut t: i32 = 0;
    {
        let mut i = temper_core::ListedTrait::len(&xs);
        while {
            i = i.wrapping_sub(1);
            i
        } >= 0
        {
            t = t
                .wrapping_mul(10)
                .wrapping_add(temper_core::ListedTrait::get(&xs, i));
        }
    }
    t
}

pub mod builders {
    use super::*;

    #[derive(Clone)]
    pub struct GridBuilder {
        pub ncols: i32,
        pub values: temper_core::List<f64>,
    }

    impl GridBuilder {
        pub fn build(self) -> Grid {
            Grid::new(self.ncols, self.values)
        }
    }

    #[derive(Clone)]
    pub struct HeldBuilder<T: ShownTrait + Clone + Send + Sync + 'static> {
        pub item: T,
        pub next: Option<Held<T>>,
    }

    impl<T: ShownTrait + Clone + Send + Sync + 'static> HeldBuilder<T> {
        pub fn build(self) -> Held<T> {
            Held::new(self.item, self.next)
        }
    }

    #[derive(Clone)]
    pub struct BundleBuilder<T: Clone + Send + Sync + 'static> {
        pub effects: temper_core::List<Effect<T>>,
        pub first: T,
    }

    impl<T: Clone + Send + Sync + 'static> BundleBuilder<T> {
        pub fn build(self) -> Bundle<T> {
            Bundle::new(self.effects, self.first)
        }
    }
}
