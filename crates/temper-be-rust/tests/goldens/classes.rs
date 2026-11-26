use std::sync::{Arc, OnceLock, RwLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            RECT__UNIT.set(Rect::new(1, 1)).unwrap_or_else(|_| panic!());
            let r = Rect::new(2, 3);
            println!("{r}");
            println!("{}", r.area());
            let s = shape(Shape::new(r.clone()));
            println!("{}", s.describe());
            let q = shape(Shape::new(Square::new(4)));
            println!("{}", q.describe());
            println!("{}", q.area());
            println!("{}", r.scaled(2).scaled(3).area());
            println!("{}", Rect::unit().scaled(5));
            let c = Counter::zero();
            c.bump().bump();
            println!("{}", c.count());
            c.set_doubled(10);
            println!("{}", c.doubled());
            println!("{}", c.same(Counter::new().bump().bump()));
            c.set_added(5);
            let added = {
                let t2 = {
                    let t: i32 = 2;
                    c.set_added(t);
                    t
                };
                c.set_added(t2);
                t2
            };
            println!("{added} {}", c.count());
            println!(
                "{} {}",
                Point::new(1, 2) == Point::new(1, 2),
                Point::new(1, 2) != Point::new(2, 1)
            );
            println!(
                "{}",
                Holder::new(Loose::new(3), "a") == Holder::new(Loose::new(-3), "a")
            );
            let loose = Loose::new(2);
            println!(
                "{} {} {} {}",
                loose.mirrors(Loose::new(-2)),
                {
                    let left = maybe_loose(-2);
                    if let Some(left) = &left {
                        left.eq(loose.clone())
                    } else {
                        false
                    }
                },
                {
                    let left2 = loose.clone();
                    let right = maybe_loose(0);
                    if let Some(right) = &right {
                        left2.eq(right.clone())
                    } else {
                        false
                    }
                },
                !{
                    let left3 = maybe_loose(0);
                    if let Some(left3) = &left3 {
                        left3.eq(loose.clone())
                    } else {
                        false
                    }
                }
            );
            let matched = {
                let subject = maybe_loose(-2);
                if if let Some(subject) = &subject {
                    subject.eq(loose.clone())
                } else {
                    false
                } {
                    Arc::new("loose".to_string())
                } else {
                    Arc::new("other".to_string())
                }
            };
            println!("{matched}");
            let b1: Boxed<Arc<String>> = Boxed::new(Arc::new("x".to_string()), 1);
            let b3: Boxed<Arc<String>> = Boxed::new(Arc::new("z".to_string()), 2);
            println!(
                "{} {} {} {} {}",
                b1.0.value,
                b1 == Boxed::new(Arc::new("y".to_string()), 1),
                b1 != b3,
                b1.same_as(Boxed::new(Arc::new("y".to_string()), 1)),
                b3.same_as(b1.clone())
            );
            println!(
                "{} {}",
                Version::new(1, 2).compare_to(Version::new(1, 3)) < 0,
                Version::new(2, 0).compare_to(Version::new(1, 9)) >= 0
            );
            println!(
                "{} {}",
                temper_core::is::<Square>(q.clone()),
                temper_core::is::<Square>(s.clone())
            );
            let sq = temper_core::cast::<Square>(q.clone()).ok_or_else(temper_core::Error::new)?;
            println!("{}", sq.area());
            if temper_core::is::<Rect>(s.clone()) {
                println!("{}", temper_core::cast::<Rect>(s.clone()).unwrap().0.w);
            }
            let st = Stock::new(Arc::new(vec![4, 5]), None);
            st.set_items(Arc::new(vec![1, 2, 3]));
            println!(
                "{} {} {} {}",
                st.0.count,
                st.0.size,
                st.size_now(),
                temper_core::ListedTrait::len(&st.items())
            );
            let plain = Theme::new("black", "white", None, None);
            let loud = {
                let spread = plain.clone();
                Theme(Arc::new(ThemeStruct {
                    size: 20,
                    bold: true,
                    ..(*spread.0).clone()
                }))
            };
            let dark = {
                let spread2 = loud.clone();
                Theme(Arc::new(ThemeStruct {
                    fg: plain.0.bg.clone(),
                    bg: plain.0.fg.clone(),
                    ..(*spread2.0).clone()
                }))
            };
            println!("{plain} | {loud} | {dark}");
            let mixed = {
                let spread3 = picked(dark.clone(), "old");
                let arg = spread3.0.bg.clone();
                let arg2 = noted("red");
                let arg3 = spread3.0.bold;
                Theme::new(arg2.clone(), arg.clone(), Some(9), Some(arg3))
            };
            println!("{mixed}");
            let ordered = {
                let spread4 = picked(plain.clone(), "first");
                Theme(Arc::new(ThemeStruct {
                    fg: noted("second"),
                    bg: noted("third"),
                    ..(*spread4.0).clone()
                }))
            };
            println!("{ordered}");
            let wide = {
                let spread5 = Span::new(2, 5);
                Span::new(spread5.0.start, 9)
            };
            println!("{} {} {}", wide.0.start, wide.0.end, wide.0.length);
            println!(
                "{}",
                compared(Arc::new(move |a: Option<Loose>, b: Option<Loose>| {
                    if let Some(a) = &a {
                        if let Some(b) = &b {
                            a.eq(b.clone())
                        } else {
                            false
                        }
                    } else {
                        b.is_none()
                    }
                }))
            );
            println!(
                "{}",
                compared(Arc::new(move |a: Option<Loose>, b: Option<Loose>| {
                    !(if let Some(a) = &a {
                        if let Some(b) = &b {
                            a.eq(b.clone())
                        } else {
                            false
                        }
                    } else {
                        b.is_none()
                    })
                }))
            );
            println!(
                "{}",
                compared(Arc::new(move |a: Option<Loose>, b: Option<Loose>| {
                    if let Some(b) = &b {
                        if let Some(a) = &a {
                            b.eq(a.clone())
                        } else {
                            false
                        }
                    } else {
                        a.is_none()
                    }
                }))
            );
            println!(
                "{}",
                compared(Arc::new(move |a: Option<Loose>, b: Option<Loose>| {
                    if a.is_none() {
                        return false;
                    }
                    if b.is_none() {
                        return false;
                    }
                    a.as_ref().unwrap().mirrors(b.clone().unwrap())
                }))
            );
            let _favorite = same(std::any::TypeId::of::<i64>());
            let mut named: i32 = 0;
            {
                let mut i: i32 = 0;
                while i < 6 {
                    type_named(i);
                    named = named.wrapping_add(1);
                    i = i.wrapping_add(1);
                }
            }
            println!("{named} types");
            let sql = Sql::new();
            sql.append_string(Some("it"));
            sql.append_string(None::<Arc<String>>);
            sql.append_int(3);
            sql.append_shape(Shape::new(Square::new(2)));
            sql.append_rect(Rect::new(1, 2));
            println!("{}", sql.accumulated());
            let seven: i32 = 7;
            println!("{}", {
                let accumulator = Sql::new();
                {
                    accumulator.append_safe("SELECT ");
                    accumulator.append_int(seven);
                    accumulator.append_safe(", ");
                    accumulator.append_string(Some("x"));
                    accumulator.append_safe(" FROM ");
                    accumulator.append_rect(Rect::new(2, 2));
                }
                accumulator.accumulated()
            });
            let stepper = Limited::new(Stepper::new(3));
            println!("next {}", stepper.next(1));
            println!(
                "{} {}",
                or_fail(true, 4, Failing::new(IntFailing::new())),
                or_fail(
                    true,
                    Arc::new("s".to_string()),
                    Failing::new(AnyFailing::new()),
                )
            );
            Ok(())
        })
        .clone()
}

pub trait ShapeTrait: temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync {
    fn clone_boxed(&self) -> Shape;

    fn area(&self) -> i32;

    fn describe(&self) -> Arc<String> {
        Arc::new(format!("shape of area {}", self.area()))
    }
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

    fn area(&self) -> i32 {
        ShapeTrait::area(&*self.0)
    }

    fn describe(&self) -> Arc<String> {
        ShapeTrait::describe(&*self.0)
    }
}

temper_core::impl_any_value_trait_for_interface!(Shape);

impl std::ops::Deref for Shape {
    type Target = dyn ShapeTrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

struct RectStruct {
    w: i32,
    h: i32,
}

#[derive(Clone)]
pub struct Rect(Arc<RectStruct>);

static RECT__UNIT: OnceLock<Rect> = OnceLock::new();

impl Rect {
    pub fn new(w: i32, h: i32) -> Rect {
        Rect(Arc::new(RectStruct { w, h }))
    }

    pub fn unit() -> Rect {
        crate::support::initialized(|| RECT__UNIT.get()).clone()
    }

    pub fn area(&self) -> i32 {
        self.0.w.wrapping_mul(self.0.h)
    }

    pub fn to_string(&self) -> Arc<String> {
        Arc::new(format!("Rect({}, {})", self.0.w, self.0.h))
    }

    pub fn scaled(&self, k: i32) -> Rect {
        Rect::new(self.0.w.wrapping_mul(k), self.0.h.wrapping_mul(k))
    }

    pub fn w(&self) -> i32 {
        self.0.w
    }

    pub fn h(&self) -> i32 {
        self.0.h
    }
}

impl std::fmt::Display for Rect {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(&self.to_string())
    }
}

impl ShapeTrait for Rect {
    fn clone_boxed(&self) -> Shape {
        Shape::new(self.clone())
    }

    fn area(&self) -> i32 {
        self.area()
    }
}

temper_core::impl_any_value_trait!(Rect, [Shape]);

struct SquareStruct {
    side: i32,
}

#[derive(Clone)]
pub struct Square(Arc<SquareStruct>);

impl Square {
    pub fn new(side: i32) -> Square {
        Square(Arc::new(SquareStruct { side }))
    }

    pub fn area(&self) -> i32 {
        self.0.side.wrapping_mul(self.0.side)
    }

    pub fn describe(&self) -> Arc<String> {
        Arc::new(format!("square of side {}", self.0.side))
    }
}

impl ShapeTrait for Square {
    fn clone_boxed(&self) -> Shape {
        Shape::new(self.clone())
    }

    fn area(&self) -> i32 {
        self.area()
    }

    fn describe(&self) -> Arc<String> {
        self.describe()
    }
}

temper_core::impl_any_value_trait!(Square, [Shape]);

struct CounterStruct {
    count: RwLock<i32>,
    hidden: RwLock<i32>,
}

#[derive(Clone)]
pub(crate) struct Counter(Arc<CounterStruct>);

impl Counter {
    pub fn new() -> Counter {
        Counter(Arc::new(CounterStruct {
            count: RwLock::new(0),
            hidden: RwLock::new(0),
        }))
    }

    pub fn doubled(&self) -> i32 {
        self.count().wrapping_mul(2)
    }

    pub fn set_doubled(&self, value: i32) {
        *self.0.count.write().unwrap() = value.wrapping_sub(self.count());
    }

    pub fn set_added(&self, value: i32) {
        *self.0.count.write().unwrap() = self.count().wrapping_add(value);
    }

    pub fn bump(&self) -> Counter {
        *self.0.count.write().unwrap() = self.count().wrapping_add(1);
        *self.0.hidden.write().unwrap() = self.hidden().wrapping_add(1);
        self.clone()
    }

    pub fn same(&self, other: Counter) -> bool {
        self.hidden() == other.hidden()
    }

    pub fn zero() -> Counter {
        Counter::new()
    }

    pub fn count(&self) -> i32 {
        *self.0.count.read().unwrap()
    }

    fn hidden(&self) -> i32 {
        *self.0.hidden.read().unwrap()
    }
}

temper_core::impl_any_value_trait!(Counter, []);

struct PointStruct {
    x: i32,
    y: i32,
}

#[derive(Clone)]
pub(crate) struct Point(Arc<PointStruct>);

impl Point {
    pub fn new(x: i32, y: i32) -> Point {
        Point(Arc::new(PointStruct { x, y }))
    }

    pub fn eq(&self, other: Point) -> bool {
        if self.0.x == other.0.x {
            self.0.y == other.0.y
        } else {
            false
        }
    }
}

impl PartialEq for Point {
    fn eq(&self, other: &Point) -> bool {
        Point::eq(self, other.clone())
    }
}

temper_core::impl_any_value_trait!(Point, []);

struct LooseStruct {
    n: i32,
}

#[derive(Clone)]
pub(crate) struct Loose(Arc<LooseStruct>);

impl Loose {
    pub fn new(n: i32) -> Loose {
        Loose(Arc::new(LooseStruct { n }))
    }

    pub fn eq(&self, other: Loose) -> bool {
        if self.0.n == other.0.n {
            true
        } else {
            self.0.n == other.0.n.wrapping_neg()
        }
    }

    pub fn mirrors(&self, other: Loose) -> bool {
        if other.eq(self.clone()) {
            self.eq(other.clone())
        } else {
            false
        }
    }
}

impl PartialEq for Loose {
    fn eq(&self, other: &Loose) -> bool {
        Loose::eq(self, other.clone())
    }
}

temper_core::impl_any_value_trait!(Loose, []);

pub(crate) fn maybe_loose(n: i32) -> Option<Loose> {
    if n == 0 {
        None
    } else {
        Some(Loose::new(n))
    }
}

struct BoxedStruct<T: Clone + Send + Sync + 'static> {
    value: T,
    tag: i32,
}

#[derive(Clone)]
pub(crate) struct Boxed<T: Clone + Send + Sync + 'static>(Arc<BoxedStruct<T>>);

impl<T: Clone + Send + Sync + 'static> Boxed<T> {
    pub fn new(value: T, tag: i32) -> Boxed<T> {
        Boxed(Arc::new(BoxedStruct { value, tag }))
    }

    pub fn eq(&self, other: Boxed<T>) -> bool {
        self.0.tag == other.0.tag
    }

    pub fn same_as(&self, other: Boxed<T>) -> bool {
        if self.eq(other.clone()) {
            other.eq(self.clone())
        } else {
            false
        }
    }
}

impl<T: Clone + Send + Sync + 'static> PartialEq for Boxed<T> {
    fn eq(&self, other: &Boxed<T>) -> bool {
        Boxed::eq(self, other.clone())
    }
}

temper_core::impl_any_value_trait!(Boxed<T>, []);

struct HolderStruct {
    loose: Loose,
    tag: Arc<String>,
}

#[derive(Clone)]
pub(crate) struct Holder(Arc<HolderStruct>);

impl Holder {
    pub fn new(loose: Loose, tag: impl ToArcString) -> Holder {
        let tag = tag.to_arc_string();
        Holder(Arc::new(HolderStruct { loose, tag }))
    }

    pub fn eq(&self, other: Holder) -> bool {
        if self.0.loose == other.0.loose {
            self.0.tag == other.0.tag
        } else {
            false
        }
    }
}

impl PartialEq for Holder {
    fn eq(&self, other: &Holder) -> bool {
        Holder::eq(self, other.clone())
    }
}

temper_core::impl_any_value_trait!(Holder, []);

struct VersionStruct {
    major: i32,
    minor: i32,
}

#[derive(Clone)]
pub(crate) struct Version(Arc<VersionStruct>);

impl Version {
    pub fn new(major: i32, minor: i32) -> Version {
        Version(Arc::new(VersionStruct { major, minor }))
    }

    pub fn compare_to(&self, other: Version) -> i32 {
        if self.0.major != other.0.major {
            self.0.major.cmp(&other.0.major) as i32
        } else {
            self.0.minor.cmp(&other.0.minor) as i32
        }
    }
}

temper_core::impl_any_value_trait!(Version, []);

struct StockStruct {
    count: i32,
    items_var: RwLock<temper_core::List<i32>>,
    size: i32,
}

#[derive(Clone)]
pub(crate) struct Stock(Arc<StockStruct>);

impl Stock {
    pub fn items(&self) -> temper_core::List<i32> {
        self.items_var()
    }

    pub fn set_items(&self, value: impl temper_core::ToList<i32>) {
        let value = value.to_list();
        *self.0.items_var.write().unwrap() = value.clone();
    }

    pub fn new(items: impl temper_core::ToList<i32>, count: Option<i32>) -> Stock {
        let items = items.to_list();
        let count = count.unwrap_or_else(|| temper_core::ListedTrait::len(&items));
        Stock(Arc::new(StockStruct {
            count,
            items_var: RwLock::new(items.clone()),
            size: temper_core::ListedTrait::len(&items),
        }))
    }

    pub fn size_now(&self) -> i32 {
        temper_core::ListedTrait::len(&self.items())
    }

    fn items_var(&self) -> temper_core::List<i32> {
        self.0.items_var.read().unwrap().clone()
    }
}

temper_core::impl_any_value_trait!(Stock, []);

pub(crate) fn shape(x: Shape) -> Shape {
    x.clone()
}

#[derive(Clone)]
struct ThemeStruct {
    fg: Arc<String>,
    bg: Arc<String>,
    size: i32,
    bold: bool,
}

#[derive(Clone)]
pub(crate) struct Theme(Arc<ThemeStruct>);

impl Theme {
    pub fn new(
        fg: impl ToArcString,
        bg: impl ToArcString,
        size: Option<i32>,
        bold: Option<bool>,
    ) -> Theme {
        let fg = fg.to_arc_string();
        let bg = bg.to_arc_string();
        let size = size.unwrap_or(12);
        let bold = bold.unwrap_or(false);
        Theme(Arc::new(ThemeStruct { fg, bg, size, bold }))
    }

    pub fn weight(&self) -> Arc<String> {
        if self.0.bold {
            Arc::new("bold".to_string())
        } else {
            Arc::new("plain".to_string())
        }
    }

    pub fn to_string(&self) -> Arc<String> {
        Arc::new(format!(
            "{}/{} {} {}",
            self.0.fg,
            self.0.bg,
            self.0.size,
            self.weight()
        ))
    }
}

impl std::fmt::Display for Theme {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(&self.to_string())
    }
}

temper_core::impl_any_value_trait!(Theme, []);

pub(crate) fn picked(t: Theme, why: impl ToArcString) -> Theme {
    let why = why.to_arc_string();
    println!("{why}");
    t.clone()
}

pub(crate) fn noted(s: impl ToArcString) -> Arc<String> {
    let s = s.to_arc_string();
    println!("{s}");
    s.clone()
}

struct SpanStruct {
    start: i32,
    end: i32,
    length: i32,
}

#[derive(Clone)]
pub(crate) struct Span(Arc<SpanStruct>);

impl Span {
    pub fn new(start: i32, end: i32) -> Span {
        let start_ = start;
        let end_ = end;
        Span(Arc::new(SpanStruct {
            start: start_,
            end: end_,
            length: end_.wrapping_sub(start_),
        }))
    }
}

temper_core::impl_any_value_trait!(Span, []);

pub(crate) fn compared(
    eq: Arc<dyn Fn(Option<Loose>, Option<Loose>) -> bool + Send + Sync>,
) -> Arc<String> {
    let ns: temper_core::List<i32> = Arc::new(vec![0, 1, -1, 2]);
    let out: temper_core::ListBuilder<Arc<String>> = temper_core::listed::new_builder();
    {
        let length = temper_core::ListedTrait::len(&ns);
        let mut i: i32 = 0;
        while i < length {
            let a = temper_core::ListedTrait::get(&ns, i);
            i = i.wrapping_add(1);
            {
                let length2 = temper_core::ListedTrait::len(&ns);
                let mut i2: i32 = 0;
                while i2 < length2 {
                    let b = temper_core::ListedTrait::get(&ns, i2);
                    i2 = i2.wrapping_add(1);
                    temper_core::listed::add(
                        &out,
                        if eq(maybe_loose(a), maybe_loose(b)) {
                            Arc::new("T".to_string())
                        } else {
                            Arc::new("f".to_string())
                        },
                        None,
                    );
                }
            }
        }
    }
    temper_core::listed::join(&out, Arc::new(String::new()), &(|s: Arc<String>| s.clone()))
}

pub(crate) fn type_named(n: i32) -> std::any::TypeId {
    if n == 0 {
        std::any::TypeId::of::<()>()
    } else if n == 1 {
        std::any::TypeId::of::<bool>()
    } else if n == 2 {
        std::any::TypeId::of::<i32>()
    } else if n == 3 {
        std::any::TypeId::of::<i64>()
    } else if n == 4 {
        std::any::TypeId::of::<f64>()
    } else {
        std::any::TypeId::of::<Arc<String>>()
    }
}

pub(crate) fn same(t: std::any::TypeId) -> std::any::TypeId {
    t
}

struct SqlStruct {
    parts: temper_core::ListBuilder<Arc<String>>,
}

#[derive(Clone)]
pub(crate) struct Sql(Arc<SqlStruct>);

impl Sql {
    pub fn new() -> Sql {
        Sql(Arc::new(SqlStruct {
            parts: temper_core::listed::new_builder(),
        }))
    }

    pub fn append_safe(&self, s: impl ToArcString) {
        let s = s.to_arc_string();
        temper_core::listed::add(&self.0.parts, s.clone(), None);
    }

    pub fn append_string(&self, x: Option<impl ToArcString>) {
        let x = x.map(|x| x.to_arc_string());
        temper_core::listed::add(
            &self.0.parts,
            if let Some(x) = &x {
                Arc::new(format!("'{x}'"))
            } else {
                Arc::new("NULL".to_string())
            },
            None,
        );
    }

    pub fn append_int(&self, x: i32) {
        temper_core::listed::add(&self.0.parts, Arc::new(x.to_string()), None);
    }

    pub fn append_shape(&self, x: Shape) {
        temper_core::listed::add(&self.0.parts, Arc::new(format!("shape {}", x.area())), None);
    }

    pub fn append_rect(&self, x: Rect) {
        temper_core::listed::add(&self.0.parts, Arc::new(format!("rect {x}")), None);
    }

    pub fn accumulated(&self) -> Arc<String> {
        temper_core::listed::join(
            &self.0.parts,
            Arc::new(String::new()),
            &(|p: Arc<String>| p.clone()),
        )
    }
}

temper_core::impl_any_value_trait!(Sql, []);

pub trait LimitedTrait: temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync {
    fn clone_boxed(&self) -> Limited;

    fn next(&self, n: i32) -> i32;

    fn too_far(&self) -> i32;
}

#[derive(Clone)]
pub struct Limited(Arc<dyn LimitedTrait>);

impl Limited {
    pub fn new(selfish: impl LimitedTrait + 'static) -> Limited {
        Limited(Arc::new(selfish))
    }
}

impl LimitedTrait for Limited {
    fn clone_boxed(&self) -> Limited {
        LimitedTrait::clone_boxed(&*self.0)
    }

    fn next(&self, n: i32) -> i32 {
        LimitedTrait::next(&*self.0, n)
    }

    fn too_far(&self) -> i32 {
        LimitedTrait::too_far(&*self.0)
    }
}

temper_core::impl_any_value_trait_for_interface!(Limited);

impl std::ops::Deref for Limited {
    type Target = dyn LimitedTrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

struct StepperStruct {
    limit: i32,
}

#[derive(Clone)]
pub struct Stepper(Arc<StepperStruct>);

impl Stepper {
    pub fn new(limit: i32) -> Stepper {
        Stepper(Arc::new(StepperStruct { limit }))
    }

    pub fn next(&self, n: i32) -> i32 {
        if n < self.0.limit {
            n.wrapping_add(1)
        } else {
            self.too_far()
        }
    }

    pub fn too_far(&self) -> i32 {
        panic!();
    }

    pub fn limit(&self) -> i32 {
        self.0.limit
    }
}

impl LimitedTrait for Stepper {
    fn clone_boxed(&self) -> Limited {
        Limited::new(self.clone())
    }

    fn next(&self, n: i32) -> i32 {
        self.next(n)
    }

    fn too_far(&self) -> i32 {
        self.too_far()
    }
}

temper_core::impl_any_value_trait!(Stepper, [Limited]);

pub(crate) trait FailingTrait<T: Clone + Send + Sync + 'static>:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync
{
    #[allow(dead_code)]
    fn clone_boxed(&self) -> Failing<T>;

    fn fail(&self) -> T;
}

#[derive(Clone)]
pub(crate) struct Failing<T: Clone + Send + Sync + 'static>(Arc<dyn FailingTrait<T>>);

impl<T: Clone + Send + Sync + 'static> Failing<T> {
    pub fn new(selfish: impl FailingTrait<T> + 'static) -> Failing<T> {
        Failing(Arc::new(selfish))
    }
}

impl<T: Clone + Send + Sync + 'static> FailingTrait<T> for Failing<T> {
    fn clone_boxed(&self) -> Failing<T> {
        FailingTrait::clone_boxed(&*self.0)
    }

    fn fail(&self) -> T {
        FailingTrait::fail(&*self.0)
    }
}

temper_core::impl_any_value_trait_for_interface!(Failing<T>);

impl<T: Clone + Send + Sync + 'static> std::ops::Deref for Failing<T> {
    type Target = dyn FailingTrait<T>;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

struct IntFailingStruct {}

#[derive(Clone)]
pub(crate) struct IntFailing(Arc<IntFailingStruct>);

impl IntFailing {
    pub fn new() -> IntFailing {
        IntFailing(Arc::new(IntFailingStruct {}))
    }

    pub fn fail(&self) -> i32 {
        panic!();
    }
}

impl FailingTrait<i32> for IntFailing {
    fn clone_boxed(&self) -> Failing<i32> {
        Failing::new(self.clone())
    }

    fn fail(&self) -> i32 {
        self.fail()
    }
}

temper_core::impl_any_value_trait!(IntFailing, [Failing<i32>]);

struct AnyFailingStruct<T: Clone + Send + Sync + 'static> {
    phantom_t: std::marker::PhantomData<T>,
}

#[derive(Clone)]
pub(crate) struct AnyFailing<T: Clone + Send + Sync + 'static>(Arc<AnyFailingStruct<T>>);

impl<T: Clone + Send + Sync + 'static> AnyFailing<T> {
    pub fn new() -> AnyFailing<T> {
        AnyFailing(Arc::new(AnyFailingStruct {
            phantom_t: std::marker::PhantomData,
        }))
    }

    pub fn fail(&self) -> T {
        panic!();
    }
}

impl<T: Clone + Send + Sync + 'static> FailingTrait<T> for AnyFailing<T> {
    fn clone_boxed(&self) -> Failing<T> {
        Failing::new(self.clone())
    }

    fn fail(&self) -> T {
        self.fail()
    }
}

temper_core::impl_any_value_trait!(AnyFailing<T>, [Failing<T>]);

pub(crate) fn or_fail<T: Clone + Send + Sync + 'static>(ok: bool, x: T, f: Failing<T>) -> T {
    if ok {
        x.clone()
    } else {
        f.fail()
    }
}

pub mod builders {
    use super::*;

    #[derive(Clone)]
    pub struct RectBuilder {
        pub w: i32,
        pub h: i32,
    }

    impl RectBuilder {
        pub fn build(self) -> Rect {
            Rect::new(self.w, self.h)
        }
    }
}
