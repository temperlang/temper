use std::sync::{Arc, OnceLock, RwLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let a = C::new("foo");
            let b = C::new("bar");
            println!(
                "leastX of {} and {} is {}.",
                a.x(),
                b.x(),
                least_x(a.clone(), b.clone()).x()
            );
            let i = I::new(a.clone());
            println!("As an I, a.x is {}.", i.x());
            let di: D<I> = D::new(I::new(a.clone()));
            let dj: D<J> = D::new(J::new(a.clone()));
            println!("Generically, x is {}.", di.0.thing.x());
            println!("Genericallier, x is still {}.", dj.0.thing.x());
            let si: SI<C> = SI::new(SSub::new(a.clone()));
            println!("Sealed thing is {}.", si.thing().x());
            let labeled: Labeled<Arc<String>, C> = Labeled::new(LabeledSub::new(a.clone()));
            let held_and: temper_core::List<C> = labeled.held_and(b.clone());
            println!(
                "Labeled thing is {}.",
                labeled.label(Arc::new("first".to_string()))
            );
            println!(
                "Held are {} and {}.",
                labeled.held().x(),
                temper_core::ListedTrait::get(&held_and, 1).x()
            );
            let high1 = High::new(Low1::new(11));
            let high1_b = High::new(Low1::new(12));
            let high1_c = High::new(Low1::new(12));
            let high2 = High::new(Low2::new("two"));
            let high2_b = High::new(Low2::new("to be"));
            let high2_c = High::new(Low2::new("to be"));
            let high3 = High::new(Low3::new());
            let high3_b = High::new(Low3::new());
            let pairs: temper_core::List<(High, High)> = Arc::new(vec![
                (high1.clone(), high1_b.clone()),
                (high1_b.clone(), high1_c.clone()),
                (high1.clone(), high2.clone()),
                (high2.clone(), high2_b.clone()),
                (high2_b.clone(), high2_c.clone()),
                (high1.clone(), high3.clone()),
                (high3.clone(), high3_b.clone()),
            ]);
            {
                let length = temper_core::ListedTrait::len(&pairs);
                let mut i2: i32 = 0;
                while i2 < length {
                    let pair: (High, High) = temper_core::ListedTrait::get(&pairs, i2);
                    i2 = i2.wrapping_add(1);
                    println!(
                        "{} == {}: {}",
                        pair.0.to_string(),
                        pair.1.to_string(),
                        pair.0.eq(pair.1.clone())
                    );
                }
            }
            Ok(())
        })
        .clone()
}

pub trait ITrait: temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync {
    fn clone_boxed(&self) -> I;

    fn x(&self) -> Arc<String>;
}

#[derive(Clone)]
pub struct I(Arc<dyn ITrait>);

impl I {
    pub fn new(selfish: impl ITrait + 'static) -> I {
        I(Arc::new(selfish))
    }
}

impl ITrait for I {
    fn clone_boxed(&self) -> I {
        ITrait::clone_boxed(&*self.0)
    }

    fn x(&self) -> Arc<String> {
        ITrait::x(&*self.0)
    }
}

temper_core::impl_any_value_trait_for_interface!(I);

impl std::ops::Deref for I {
    type Target = dyn ITrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

pub trait JTrait:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync + ITrait
{
    fn clone_boxed(&self) -> J;
}

#[derive(Clone)]
pub struct J(Arc<dyn JTrait>);

impl J {
    pub fn new(selfish: impl JTrait + 'static) -> J {
        J(Arc::new(selfish))
    }
}

impl JTrait for J {
    fn clone_boxed(&self) -> J {
        JTrait::clone_boxed(&*self.0)
    }
}

impl ITrait for J {
    fn clone_boxed(&self) -> I {
        ITrait::clone_boxed(&*self.0)
    }

    fn x(&self) -> Arc<String> {
        ITrait::x(&*self.0)
    }
}

temper_core::impl_any_value_trait_for_interface!(J);

impl std::ops::Deref for J {
    type Target = dyn JTrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

pub(crate) fn least_x<IT: ITrait + Clone + Send + Sync + 'static>(a: IT, b: IT) -> IT {
    if ITrait::x(&a) < ITrait::x(&b) {
        a.clone()
    } else {
        b.clone()
    }
}

struct CStruct {
    x: RwLock<Arc<String>>,
}

#[derive(Clone)]
pub(crate) struct C(Arc<CStruct>);

impl C {
    pub fn new(x: impl ToArcString) -> C {
        let x = x.to_arc_string();
        C(Arc::new(CStruct { x: RwLock::new(x) }))
    }

    pub fn x(&self) -> Arc<String> {
        self.0.x.read().unwrap().clone()
    }
}

impl JTrait for C {
    fn clone_boxed(&self) -> J {
        J::new(self.clone())
    }
}

impl ITrait for C {
    fn clone_boxed(&self) -> I {
        I::new(self.clone())
    }

    fn x(&self) -> Arc<String> {
        self.x()
    }
}

temper_core::impl_any_value_trait!(C, [J, I]);

struct DStruct<T: ITrait + Clone + Send + Sync + 'static> {
    thing: T,
}

#[derive(Clone)]
pub(crate) struct D<T: ITrait + Clone + Send + Sync + 'static>(Arc<DStruct<T>>);

impl<T: ITrait + Clone + Send + Sync + 'static> D<T> {
    pub fn new(thing: T) -> D<T> {
        D(Arc::new(DStruct { thing }))
    }
}

temper_core::impl_any_value_trait!(D<T>, [] where T: ITrait);

pub trait SITrait<T: ITrait + Clone + Send + Sync + 'static>:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync
{
    fn clone_boxed(&self) -> SI<T>;

    fn thing(&self) -> T;
}

#[derive(Clone)]
pub struct SI<T: ITrait + Clone + Send + Sync + 'static>(Arc<dyn SITrait<T>>);

impl<T: ITrait + Clone + Send + Sync + 'static> SI<T> {
    pub fn new(selfish: impl SITrait<T> + 'static) -> SI<T> {
        SI(Arc::new(selfish))
    }
}

impl<T: ITrait + Clone + Send + Sync + 'static> SITrait<T> for SI<T> {
    fn clone_boxed(&self) -> SI<T> {
        SITrait::clone_boxed(&*self.0)
    }

    fn thing(&self) -> T {
        SITrait::thing(&*self.0)
    }
}

temper_core::impl_any_value_trait_for_interface!(SI<T> where T: ITrait);

impl<T: ITrait + Clone + Send + Sync + 'static> std::ops::Deref for SI<T> {
    type Target = dyn SITrait<T>;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

struct SSubStruct<T: ITrait + Clone + Send + Sync + 'static> {
    thing: T,
}

#[derive(Clone)]
pub(crate) struct SSub<T: ITrait + Clone + Send + Sync + 'static>(Arc<SSubStruct<T>>);

impl<T: ITrait + Clone + Send + Sync + 'static> SSub<T> {
    pub fn new(thing: T) -> SSub<T> {
        SSub(Arc::new(SSubStruct { thing }))
    }

    pub fn thing(&self) -> T {
        self.0.thing.clone()
    }
}

impl<T: ITrait + Clone + Send + Sync + 'static> SITrait<T> for SSub<T> {
    fn clone_boxed(&self) -> SI<T> {
        SI::new(self.clone())
    }

    fn thing(&self) -> T {
        self.thing()
    }
}

temper_core::impl_any_value_trait!(SSub<T>, [SI<T>] where T: ITrait);

pub(crate) trait HolderTrait<T: Clone + Send + Sync + 'static>:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync
{
    #[allow(dead_code)]
    fn clone_boxed(&self) -> Holder<T>;

    fn held(&self) -> T;

    fn held_and(&self, other: T) -> temper_core::List<T> {
        Arc::new(vec![self.held(), other.clone()])
    }
}

#[derive(Clone)]
pub(crate) struct Holder<T: Clone + Send + Sync + 'static>(Arc<dyn HolderTrait<T>>);

impl<T: Clone + Send + Sync + 'static> Holder<T> {
    pub fn new(selfish: impl HolderTrait<T> + 'static) -> Holder<T> {
        Holder(Arc::new(selfish))
    }
}

impl<T: Clone + Send + Sync + 'static> HolderTrait<T> for Holder<T> {
    fn clone_boxed(&self) -> Holder<T> {
        HolderTrait::clone_boxed(&*self.0)
    }

    fn held(&self) -> T {
        HolderTrait::held(&*self.0)
    }

    fn held_and(&self, other: T) -> temper_core::List<T> {
        HolderTrait::held_and(&*self.0, other)
    }
}

temper_core::impl_any_value_trait_for_interface!(Holder<T>);

impl<T: Clone + Send + Sync + 'static> std::ops::Deref for Holder<T> {
    type Target = dyn HolderTrait<T>;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

pub(crate) trait LabeledTrait<L: Clone + Send + Sync + 'static, T: Clone + Send + Sync + 'static>:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync + HolderTrait<T>
{
    #[allow(dead_code)]
    fn clone_boxed(&self) -> Labeled<L, T>;

    fn label(&self, l: L) -> Arc<String>;
}

#[derive(Clone)]
pub(crate) struct Labeled<L: Clone + Send + Sync + 'static, T: Clone + Send + Sync + 'static>(
    Arc<dyn LabeledTrait<L, T>>,
);

impl<L: Clone + Send + Sync + 'static, T: Clone + Send + Sync + 'static> Labeled<L, T> {
    pub fn new(selfish: impl LabeledTrait<L, T> + 'static) -> Labeled<L, T> {
        Labeled(Arc::new(selfish))
    }
}

impl<L: Clone + Send + Sync + 'static, T: Clone + Send + Sync + 'static> LabeledTrait<L, T>
    for Labeled<L, T>
{
    fn clone_boxed(&self) -> Labeled<L, T> {
        LabeledTrait::clone_boxed(&*self.0)
    }

    fn label(&self, l: L) -> Arc<String> {
        LabeledTrait::label(&*self.0, l)
    }
}

impl<L: Clone + Send + Sync + 'static, T: Clone + Send + Sync + 'static> HolderTrait<T>
    for Labeled<L, T>
{
    fn clone_boxed(&self) -> Holder<T> {
        HolderTrait::clone_boxed(&*self.0)
    }

    fn held(&self) -> T {
        HolderTrait::held(&*self.0)
    }

    fn held_and(&self, other: T) -> temper_core::List<T> {
        HolderTrait::held_and(&*self.0, other)
    }
}

temper_core::impl_any_value_trait_for_interface!(Labeled<L, T>);

impl<L: Clone + Send + Sync + 'static, T: Clone + Send + Sync + 'static> std::ops::Deref
    for Labeled<L, T>
{
    type Target = dyn LabeledTrait<L, T>;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

struct LabeledSubStruct {
    held: C,
}

#[derive(Clone)]
pub(crate) struct LabeledSub(Arc<LabeledSubStruct>);

impl LabeledSub {
    pub fn new(held: C) -> LabeledSub {
        LabeledSub(Arc::new(LabeledSubStruct { held }))
    }

    pub fn label(&self, l: impl ToArcString) -> Arc<String> {
        let l = l.to_arc_string();
        Arc::new(format!("{l}: {}", self.0.held.x()))
    }

    pub fn held(&self) -> C {
        self.0.held.clone()
    }
}

impl LabeledTrait<Arc<String>, C> for LabeledSub {
    fn clone_boxed(&self) -> Labeled<Arc<String>, C> {
        Labeled::new(self.clone())
    }

    fn label(&self, l: Arc<String>) -> Arc<String> {
        self.label(l)
    }
}

impl HolderTrait<C> for LabeledSub {
    fn clone_boxed(&self) -> Holder<C> {
        Holder::new(self.clone())
    }

    fn held(&self) -> C {
        self.held()
    }
}

temper_core::impl_any_value_trait!(LabeledSub, [Labeled<Arc<String>, C>, Holder<C>]);

pub(crate) trait HighTrait:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync
{
    #[allow(dead_code)]
    fn clone_boxed(&self) -> High;

    fn to_string(&self) -> Arc<String>;

    fn eq(&self, other: High) -> bool;
}

#[derive(Clone)]
pub(crate) struct High(Arc<dyn HighTrait>);

impl High {
    pub fn new(selfish: impl HighTrait + 'static) -> High {
        High(Arc::new(selfish))
    }
}

impl HighTrait for High {
    fn clone_boxed(&self) -> High {
        HighTrait::clone_boxed(&*self.0)
    }

    fn to_string(&self) -> Arc<String> {
        HighTrait::to_string(&*self.0)
    }

    fn eq(&self, other: High) -> bool {
        HighTrait::eq(&*self.0, other)
    }
}

temper_core::impl_any_value_trait_for_interface!(High);

impl std::ops::Deref for High {
    type Target = dyn HighTrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

struct Low1Struct {
    i: i32,
}

#[derive(Clone)]
pub(crate) struct Low1(Arc<Low1Struct>);

impl Low1 {
    pub fn new(i: i32) -> Low1 {
        Low1(Arc::new(Low1Struct { i }))
    }

    pub fn to_string(&self) -> Arc<String> {
        Arc::new(format!("Low1({})", self.0.i))
    }

    pub fn eq(&self, other: High) -> bool {
        if temper_core::is::<Low1>(other.clone()) {
            self.0.i == temper_core::cast::<Low1>(other.clone()).unwrap().0.i
        } else {
            false
        }
    }
}

impl std::fmt::Display for Low1 {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(&self.to_string())
    }
}

impl HighTrait for Low1 {
    fn clone_boxed(&self) -> High {
        High::new(self.clone())
    }

    fn to_string(&self) -> Arc<String> {
        self.to_string()
    }

    fn eq(&self, other: High) -> bool {
        self.eq(other)
    }
}

temper_core::impl_any_value_trait!(Low1, [High]);

pub(crate) trait MidTrait:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync + HighTrait
{
    #[allow(dead_code)]
    fn clone_boxed(&self) -> Mid;
}

#[derive(Clone)]
pub(crate) struct Mid(Arc<dyn MidTrait>);

impl Mid {
    pub fn new(selfish: impl MidTrait + 'static) -> Mid {
        Mid(Arc::new(selfish))
    }
}

impl MidTrait for Mid {
    fn clone_boxed(&self) -> Mid {
        MidTrait::clone_boxed(&*self.0)
    }
}

impl HighTrait for Mid {
    fn clone_boxed(&self) -> High {
        HighTrait::clone_boxed(&*self.0)
    }

    fn to_string(&self) -> Arc<String> {
        HighTrait::to_string(&*self.0)
    }

    fn eq(&self, other: High) -> bool {
        HighTrait::eq(&*self.0, other)
    }
}

temper_core::impl_any_value_trait_for_interface!(Mid);

impl std::ops::Deref for Mid {
    type Target = dyn MidTrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

struct Low2Struct {
    s: Arc<String>,
}

#[derive(Clone)]
pub(crate) struct Low2(Arc<Low2Struct>);

impl Low2 {
    pub fn new(s: impl ToArcString) -> Low2 {
        let s = s.to_arc_string();
        Low2(Arc::new(Low2Struct { s }))
    }

    pub fn to_string(&self) -> Arc<String> {
        Arc::new(format!("Low2({})", self.0.s))
    }

    pub fn eq(&self, other: High) -> bool {
        if temper_core::is::<Low2>(other.clone()) {
            self.0.s == temper_core::cast::<Low2>(other.clone()).unwrap().0.s
        } else {
            false
        }
    }
}

impl std::fmt::Display for Low2 {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(&self.to_string())
    }
}

impl MidTrait for Low2 {
    fn clone_boxed(&self) -> Mid {
        Mid::new(self.clone())
    }
}

impl HighTrait for Low2 {
    fn clone_boxed(&self) -> High {
        High::new(self.clone())
    }

    fn to_string(&self) -> Arc<String> {
        self.to_string()
    }

    fn eq(&self, other: High) -> bool {
        self.eq(other)
    }
}

temper_core::impl_any_value_trait!(Low2, [Mid, High]);

struct Low3Struct {}

#[derive(Clone)]
pub(crate) struct Low3(Arc<Low3Struct>);

impl Low3 {
    pub fn new() -> Low3 {
        Low3(Arc::new(Low3Struct {}))
    }

    pub fn to_string(&self) -> Arc<String> {
        Arc::new("Low3".to_string())
    }

    pub fn eq(&self, other: High) -> bool {
        temper_core::is::<Low3>(other.clone())
    }
}

impl std::fmt::Display for Low3 {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(&self.to_string())
    }
}

impl MidTrait for Low3 {
    fn clone_boxed(&self) -> Mid {
        Mid::new(self.clone())
    }
}

impl HighTrait for Low3 {
    fn clone_boxed(&self) -> High {
        High::new(self.clone())
    }

    fn to_string(&self) -> Arc<String> {
        self.to_string()
    }

    fn eq(&self, other: High) -> bool {
        self.eq(other)
    }
}

temper_core::impl_any_value_trait!(Low3, [Mid, High]);
