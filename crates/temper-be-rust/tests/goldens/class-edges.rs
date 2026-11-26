use std::sync::{Arc, OnceLock, RwLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let p = Person::new("Ann", "Dr");
            println!("{p}");
            println!("{}", p.greet(Arc::new("Bob".to_string())));
            let t = Titled::new(p.clone());
            let n = pick(t.clone());
            println!("{}", n.greet(Arc::new("Cy".to_string())));
            println!("{}", n.self_().name());
            *p.0.name.write().unwrap() = Arc::new("Bea".to_string());
            println!("{} {}", n.name(), t.title());
            println!(
                "{} {} {}",
                p == Person::new("Bea", "X"),
                temper_core::is::<Named>(p.clone()),
                temper_core::is::<Person>(n.clone())
            );
            let b = Box::new();
            println!("{} {}", b.count(), b.0.twice);
            let q = temper_core::cast::<Person>(n.clone()).ok_or_else(temper_core::Error::new)?;
            println!("{}", q.0.title);
            println!("{}", Duo::new(p.clone(), None, None::<Arc<String>>).names());
            println!(
                "{}",
                Duo::new(
                    p.clone(),
                    Some(Person::new("Cy", "Mr")),
                    None::<Arc<String>>,
                )
                .names()
            );
            println!(
                "{}",
                Duo::new(p.clone(), Some(q.clone()), Some(" + ")).names()
            );
            Ok(())
        })
        .clone()
}

pub trait NamedTrait: temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync {
    fn clone_boxed(&self) -> Named;

    fn name(&self) -> Arc<String>;

    fn greet(&self, other: Arc<String>) -> Arc<String> {
        Arc::new(format!("{other}, I am {}", self.name()))
    }

    fn self_(&self) -> Named {
        NamedTrait::clone_boxed(self)
    }
}

#[derive(Clone)]
pub struct Named(Arc<dyn NamedTrait>);

impl Named {
    pub fn new(selfish: impl NamedTrait + 'static) -> Named {
        Named(Arc::new(selfish))
    }
}

impl NamedTrait for Named {
    fn clone_boxed(&self) -> Named {
        NamedTrait::clone_boxed(&*self.0)
    }

    fn name(&self) -> Arc<String> {
        NamedTrait::name(&*self.0)
    }

    fn greet(&self, other: Arc<String>) -> Arc<String> {
        NamedTrait::greet(&*self.0, other)
    }

    fn self_(&self) -> Named {
        NamedTrait::self_(&*self.0)
    }
}

temper_core::impl_any_value_trait_for_interface!(Named);

impl std::ops::Deref for Named {
    type Target = dyn NamedTrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

pub trait TitledTrait:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync + NamedTrait
{
    fn clone_boxed(&self) -> Titled;

    fn title(&self) -> Arc<String>;
}

#[derive(Clone)]
pub struct Titled(Arc<dyn TitledTrait>);

impl Titled {
    pub fn new(selfish: impl TitledTrait + 'static) -> Titled {
        Titled(Arc::new(selfish))
    }
}

impl TitledTrait for Titled {
    fn clone_boxed(&self) -> Titled {
        TitledTrait::clone_boxed(&*self.0)
    }

    fn title(&self) -> Arc<String> {
        TitledTrait::title(&*self.0)
    }
}

impl NamedTrait for Titled {
    fn clone_boxed(&self) -> Named {
        NamedTrait::clone_boxed(&*self.0)
    }

    fn name(&self) -> Arc<String> {
        NamedTrait::name(&*self.0)
    }

    fn greet(&self, other: Arc<String>) -> Arc<String> {
        NamedTrait::greet(&*self.0, other)
    }

    fn self_(&self) -> Named {
        NamedTrait::self_(&*self.0)
    }
}

temper_core::impl_any_value_trait_for_interface!(Titled);

impl std::ops::Deref for Titled {
    type Target = dyn TitledTrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

struct PersonStruct {
    name: RwLock<Arc<String>>,
    title: Arc<String>,
}

#[derive(Clone)]
pub struct Person(Arc<PersonStruct>);

impl Person {
    pub fn new(name: impl ToArcString, title: impl ToArcString) -> Person {
        let name = name.to_arc_string();
        let title = title.to_arc_string();
        Person(Arc::new(PersonStruct {
            name: RwLock::new(name),
            title,
        }))
    }

    pub fn to_string(&self) -> Arc<String> {
        Arc::new(format!("{} {}", self.0.title, self.name()))
    }

    #[allow(clippy::should_implement_trait)]
    pub fn eq(&self, other: Person) -> bool {
        self.name() == other.name()
    }

    pub fn name(&self) -> Arc<String> {
        self.0.name.read().unwrap().clone()
    }

    pub fn set_name(&self, value: impl ToArcString) {
        let value = value.to_arc_string();
        *self.0.name.write().unwrap() = value;
    }

    pub fn title(&self) -> Arc<String> {
        self.0.title.clone()
    }
}

impl std::fmt::Display for Person {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(&self.to_string())
    }
}

impl PartialEq for Person {
    fn eq(&self, other: &Person) -> bool {
        Person::eq(self, other.clone())
    }
}

impl TitledTrait for Person {
    fn clone_boxed(&self) -> Titled {
        Titled::new(self.clone())
    }

    fn title(&self) -> Arc<String> {
        self.title()
    }
}

impl NamedTrait for Person {
    fn clone_boxed(&self) -> Named {
        Named::new(self.clone())
    }

    fn name(&self) -> Arc<String> {
        self.name()
    }
}

temper_core::impl_any_value_trait!(Person, [Titled, Named]);

pub(crate) fn log(b: Box) {
    println!("box {}", b.count());
}

struct BoxStruct {
    count: RwLock<i32>,
    twice: i32,
}

#[derive(Clone)]
pub struct Box(Arc<BoxStruct>);

impl Box {
    #[allow(clippy::new_without_default)]
    pub fn new() -> Box {
        let count: i32 = 1;
        let selfish = Box(Arc::new(BoxStruct {
            count: RwLock::new(count),
            twice: count.wrapping_mul(2),
        }));
        *selfish.0.count.write().unwrap() = selfish.count().wrapping_add(selfish.0.twice);
        log(selfish.clone());
        selfish
    }

    pub fn count(&self) -> i32 {
        *self.0.count.read().unwrap()
    }

    pub fn set_count(&self, value: i32) {
        *self.0.count.write().unwrap() = value;
    }

    pub fn twice(&self) -> i32 {
        self.0.twice
    }
}

temper_core::impl_any_value_trait!(Box, []);

pub(crate) fn pick(x: Titled) -> Named {
    Named::new(x.clone())
}

struct DuoStruct<T: NamedTrait + Clone + Send + Sync + 'static> {
    first: T,
    second: Option<T>,
    joiner: Arc<String>,
}

#[derive(Clone)]
pub struct Duo<T: NamedTrait + Clone + Send + Sync + 'static>(Arc<DuoStruct<T>>);

impl<T: NamedTrait + Clone + Send + Sync + 'static> Duo<T> {
    pub fn new(first: T, second: Option<T>, joiner: Option<impl ToArcString>) -> Duo<T> {
        let joiner = joiner.map(|x| x.to_arc_string());
        let joiner = joiner.unwrap_or_else(|| Arc::new(" & ".to_string()));
        Duo(Arc::new(DuoStruct {
            first,
            second,
            joiner,
        }))
    }

    pub fn names(&self) -> Arc<String> {
        let s = self.0.second.clone();
        if let Some(s) = &s {
            Arc::new(format!(
                "{}{}{}",
                NamedTrait::name(&self.0.first),
                self.0.joiner,
                NamedTrait::name(s)
            ))
        } else {
            NamedTrait::name(&self.0.first)
        }
    }

    pub fn first(&self) -> T {
        self.0.first.clone()
    }

    pub fn second(&self) -> Option<T> {
        self.0.second.clone()
    }

    pub fn joiner(&self) -> Arc<String> {
        self.0.joiner.clone()
    }
}

temper_core::impl_any_value_trait!(Duo<T>, [] where T: NamedTrait);

pub(crate) fn twice(n: i32) -> i32 {
    n.wrapping_mul(2)
}

struct TallyStruct {
    count: RwLock<i32>,
    other: RwLock<i32>,
}

#[derive(Clone)]
pub struct Tally(Arc<TallyStruct>);

impl Tally {
    #[allow(clippy::new_without_default)]
    pub fn new() -> Tally {
        Tally(Arc::new(TallyStruct {
            count: RwLock::new(0),
            other: RwLock::new(0),
        }))
    }

    pub fn add(&self, n: i32) {
        let mut count = self.0.count.write().unwrap();
        *count = (*count).wrapping_add(n);
    }

    pub fn bump(&self) {
        let mut count = self.0.count.write().unwrap();
        *count = (*count).wrapping_add(1);
    }

    pub fn clamp_add(&self, n: i32) {
        let mut count = self.0.count.write().unwrap();
        *count = if *count > 10 && n > 0 {
            *count
        } else {
            (*count).wrapping_add(n)
        };
    }

    pub fn split(&self, n: i32) -> temper_core::Result<()> {
        let mut count = self.0.count.write().unwrap();
        *count = temper_core::int_div(*count, n)?;
        Ok(())
    }

    pub fn copy_from(&self, t: Tally) {
        *self.0.count.write().unwrap() = t.count().wrapping_add(1);
    }

    pub fn from_other(&self) {
        *self.0.count.write().unwrap() = self.other().wrapping_add(self.count());
    }

    pub fn plus_twice(&self) {
        *self.0.count.write().unwrap() = self.count().wrapping_add(twice(self.count()));
    }

    pub fn copy_into(&self, t: Tally) {
        *t.0.count.write().unwrap() = self.count().wrapping_add(1);
    }

    pub fn count(&self) -> i32 {
        *self.0.count.read().unwrap()
    }

    pub fn set_count(&self, value: i32) {
        *self.0.count.write().unwrap() = value;
    }

    pub fn other(&self) -> i32 {
        *self.0.other.read().unwrap()
    }

    pub fn set_other(&self, value: i32) {
        *self.0.other.write().unwrap() = value;
    }
}

temper_core::impl_any_value_trait!(Tally, []);

pub fn bump_twice(tally: Tally) {
    let mut count = tally.0.count.write().unwrap();
    *count = 2i32.wrapping_add(*count);
}

pub mod builders {
    use super::*;

    #[derive(Clone)]
    pub struct PersonBuilder {
        pub name: Arc<String>,
        pub title: Arc<String>,
    }

    impl PersonBuilder {
        pub fn build(self) -> Person {
            Person::new(self.name, self.title)
        }
    }

    #[derive(Clone)]
    pub struct DuoBuilder<T: NamedTrait + Clone + Send + Sync + 'static> {
        pub first: T,
    }

    #[derive(Clone)]
    pub struct DuoOptions<T: NamedTrait + Clone + Send + Sync + 'static> {
        selfish: DuoBuilder<T>,
        second: Option<T>,
        joiner: Option<Arc<String>>,
    }

    impl<T: NamedTrait + Clone + Send + Sync + 'static> DuoOptions<T> {
        pub fn new(selfish: DuoBuilder<T>) -> Self {
            Self {
                selfish,
                second: None,
                joiner: None,
            }
        }

        pub fn second(mut self, second: T) -> Self {
            self.second = Some(second);
            self
        }

        pub fn joiner(mut self, joiner: Arc<String>) -> Self {
            self.joiner = Some(joiner);
            self
        }

        pub fn build(self) -> Duo<T> {
            Duo::new(self.selfish.first, self.second, self.joiner)
        }
    }

    impl<T: NamedTrait + Clone + Send + Sync + 'static> DuoBuilder<T> {
        pub fn build(self) -> Duo<T> {
            self.options().build()
        }

        pub fn options(self) -> DuoOptions<T> {
            DuoOptions::new(self)
        }
    }
}
