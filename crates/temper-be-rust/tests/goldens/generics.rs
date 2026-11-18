use std::sync::{Arc, OnceLock, RwLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let ns: temper_core::List<i32> = Arc::new(vec![1, 2, 3]);
            let none: temper_core::List<i32> = Arc::new(vec![]);
            println!(
                "{} {} {}",
                total(ns.clone()),
                first(ns.clone(), 0),
                first(none.clone(), -1)
            );
            println!(
                "{}",
                first(
                    Arc::new(vec![Arc::new("a".to_string()), Arc::new("b".to_string())]),
                    Arc::new("z".to_string()),
                )
            );
            let r#box: Box<Arc<String>> = Box::new(Arc::new("boxed".to_string()));
            println!("{}", held(Holder::new(r#box.clone())));
            let pair: (Arc<String>, i32) = r#box.pair_with(7);
            println!("{} {}", pair.0, pair.1);
            let shelf: Shelf<Named> =
                Shelf::new(Arc::new(vec![Named::new("ann"), Named::new("bo")]));
            println!("{}", {
                let subject = shelf.first_name();
                subject
                    .clone()
                    .unwrap_or_else(|| Arc::new("none".to_string()))
            });
            let no_names: temper_core::List<Named> = Arc::new(vec![]);
            let empty: Shelf<Named> = Shelf::new(no_names.clone());
            println!("{}", {
                let subject2 = empty.first_name();
                subject2
                    .clone()
                    .unwrap_or_else(|| Arc::new("none".to_string()))
            });
            let n = shelf
                .tally::<Named>(1)
                .wrapping_add(shelf.tally::<Named>(2));
            println!(
                "{} {} {} {} {n}",
                tagged::<Scene, i32>(4),
                tagged::<Scene, Arc<String>>(Arc::new("t".to_string())),
                count_of::<temper_core::AnyValue>(3),
                relayed::<Scene, i32>(5)
            );
            let builder: temper_core::ListBuilder<Arc<String>> = temper_core::listed::new_builder();
            temper_core::listed::add(&builder, Arc::new("x".to_string()), None);
            temper_core::listed::add(&builder, Arc::new("y".to_string()), None);
            temper_core::listed::add(&builder, Arc::new("w".to_string()), Some(0));
            println!(
                "{} {} {}",
                temper_core::ListedTrait::len(&builder),
                temper_core::ListedTrait::get(&builder, 0),
                temper_core::listed::remove_last(&builder)
            );
            let built: temper_core::List<Arc<String>> = temper_core::ListedTrait::to_list(&builder);
            println!(
                "{} {} {}",
                temper_core::ListedTrait::len(&built),
                temper_core::ListedTrait::get_or(&built, 5, Arc::new("?".to_string())),
                temper_core::ListedTrait::get(&temper_core::listed::slice(&built, 1, 2), 0)
            );
            let m: temper_core::Map<Arc<String>, i32> = temper_core::Map::new(Arc::new(vec![
                (Arc::new("a".to_string()), 1),
                (Arc::new("b".to_string()), 2),
            ]));
            println!(
                "{} {} {} {}",
                temper_core::MappedTrait::get(&m, Arc::new("b".to_string())).unwrap_or(-1),
                temper_core::MappedTrait::get(&m, Arc::new("c".to_string())).unwrap_or(-1),
                temper_core::MappedTrait::has(&m, Arc::new("a".to_string())),
                temper_core::MappedTrait::len(&m)
            );
            let mb: temper_core::MapBuilder<Arc<String>, Option<i32>> =
                temper_core::MapBuilder::new();
            temper_core::MapBuilder::set(&mb, Arc::new("q".to_string()), Some(5));
            temper_core::MapBuilder::set(&mb, Arc::new("n".to_string()), None);
            println!(
                "{} {} {}",
                {
                    let subject3 =
                        temper_core::MappedTrait::get_or(&mb, Arc::new("q".to_string()), Some(0));
                    subject3.unwrap_or(-2)
                },
                {
                    let subject4 =
                        temper_core::MappedTrait::get_or(&mb, Arc::new("n".to_string()), Some(0));
                    subject4.unwrap_or(-2)
                },
                temper_core::MappedTrait::has(&mb, Arc::new("n".to_string()))
            );
            println!("{}", {
                let subject5 = {
                    let subject6 = temper_core::MapBuilder::remove(&mb, Arc::new("q".to_string()))
                        .unwrap_or(Some(0));
                    subject6.map(|subject6| Arc::new(subject6.to_string()))
                };
                subject5
                    .clone()
                    .unwrap_or_else(|| Arc::new("null".to_string()))
            });
            println!(
                "{}",
                temper_core::ListedTrait::len(&*temper_core::MappedTrait::keys(&mb))
            );
            indexed();
            both();
            TALLY
                .set(temper_core::MapBuilder::new())
                .unwrap_or_else(|_| panic!());
            temper_core::MapBuilder::set(&tally(), Arc::new("k".to_string()), 2);
            println!(
                "{} {} {}",
                bump("k").unwrap_or(-1),
                bump("z").unwrap_or(-1),
                temper_core::MappedTrait::get(&tally(), Arc::new("k".to_string())).unwrap_or(-1)
            );
            println!(
                "{} {}",
                first_same(Arc::new(vec![1000]), Arc::new(vec![1000])),
                first_same(Arc::new(vec![1000]), Arc::new(vec![7]))
            );
            Ok(())
        })
        .clone()
}

pub trait HolderTrait<T: Clone + Send + Sync + 'static>:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync
{
    fn clone_boxed(&self) -> Holder<T>;

    fn held(&self) -> T;
}

#[derive(Clone)]
pub struct Holder<T: Clone + Send + Sync + 'static>(Arc<dyn HolderTrait<T>>);

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
}

temper_core::impl_any_value_trait_for_interface!(Holder<T>);

impl<T: Clone + Send + Sync + 'static> std::ops::Deref for Holder<T> {
    type Target = dyn HolderTrait<T>;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

struct BoxStruct<T: Clone + Send + Sync + 'static> {
    value: T,
}

#[derive(Clone)]
pub struct Box<T: Clone + Send + Sync + 'static>(Arc<BoxStruct<T>>);

impl<T: Clone + Send + Sync + 'static> Box<T> {
    pub fn new(value: T) -> Box<T> {
        Box(Arc::new(BoxStruct { value }))
    }

    pub fn held(&self) -> T {
        self.0.value.clone()
    }

    pub fn pair_with(&self, other: i32) -> (T, i32) {
        (self.0.value.clone(), other)
    }

    pub fn value(&self) -> T {
        self.0.value.clone()
    }
}

impl<T: Clone + Send + Sync + 'static> HolderTrait<T> for Box<T> {
    fn clone_boxed(&self) -> Holder<T> {
        Holder::new(self.clone())
    }

    fn held(&self) -> T {
        self.held()
    }
}

temper_core::impl_any_value_trait!(Box<T>, [Holder<T>]);

struct NamedStruct {
    name: Arc<String>,
}

#[derive(Clone)]
pub struct Named(Arc<NamedStruct>);

impl Named {
    pub fn new(name: impl ToArcString) -> Named {
        let name = name.to_arc_string();
        Named(Arc::new(NamedStruct { name }))
    }

    pub fn name(&self) -> Arc<String> {
        self.0.name.clone()
    }
}

temper_core::impl_any_value_trait!(Named, []);

struct CounterStruct {
    n: RwLock<i32>,
}

#[derive(Clone)]
pub struct Counter(Arc<CounterStruct>);

impl Counter {
    pub fn new(n: i32) -> Counter {
        Counter(Arc::new(CounterStruct { n: RwLock::new(n) }))
    }

    pub fn n(&self) -> i32 {
        *self.0.n.read().unwrap()
    }

    pub fn set_n(&self, value: i32) {
        *self.0.n.write().unwrap() = value;
    }
}

temper_core::impl_any_value_trait!(Counter, []);

struct ShelfStruct<T: Clone + Send + Sync + 'static + Into<Named>> {
    items: temper_core::List<T>,
}

#[derive(Clone)]
pub struct Shelf<T: Clone + Send + Sync + 'static + Into<Named>>(Arc<ShelfStruct<T>>);

impl<T: Clone + Send + Sync + 'static + Into<Named>> Shelf<T> {
    pub fn new(items: impl temper_core::ToList<T>) -> Shelf<T> {
        let items = items.to_list();
        Shelf(Arc::new(ShelfStruct { items }))
    }

    pub fn first_name(&self) -> Option<Arc<String>> {
        if temper_core::ListedTrait::is_empty(&self.0.items) {
            None
        } else {
            Some(
                Into::<Named>::into(temper_core::ListedTrait::get(&self.0.items, 0))
                    .0
                    .name
                    .clone(),
            )
        }
    }

    #[allow(clippy::extra_unused_type_parameters)]
    pub fn tally<TName: Clone + Send + Sync + 'static>(&self, extra: i32) -> i32 {
        temper_core::ListedTrait::len(&self.0.items).wrapping_add(extra)
    }

    pub fn items(&self) -> temper_core::List<T> {
        self.0.items.clone()
    }
}

temper_core::impl_any_value_trait!(Shelf<T>, [] where T: Into<Named>);

pub trait SceneTrait: temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync {
    fn clone_boxed(&self) -> Scene;
}

#[derive(Clone)]
pub struct Scene(Arc<dyn SceneTrait>);

impl Scene {
    pub fn new(selfish: impl SceneTrait + 'static) -> Scene {
        Scene(Arc::new(selfish))
    }
}

impl SceneTrait for Scene {
    fn clone_boxed(&self) -> Scene {
        SceneTrait::clone_boxed(&*self.0)
    }
}

temper_core::impl_any_value_trait_for_interface!(Scene);

impl std::ops::Deref for Scene {
    type Target = dyn SceneTrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

struct StageStruct {}

#[derive(Clone)]
pub struct Stage(Arc<StageStruct>);

impl Stage {
    #[allow(clippy::new_without_default)]
    pub fn new() -> Stage {
        Stage(Arc::new(StageStruct {}))
    }
}

impl SceneTrait for Stage {
    fn clone_boxed(&self) -> Scene {
        Scene::new(self.clone())
    }
}

temper_core::impl_any_value_trait!(Stage, [Scene]);

#[allow(clippy::extra_unused_type_parameters)]
pub(crate) fn tagged<S: Clone + Send + Sync + 'static, T: Clone + Send + Sync + 'static>(
    x: T,
) -> T {
    x.clone()
}

#[allow(clippy::extra_unused_type_parameters)]
pub(crate) fn count_of<T: Clone + Send + Sync + 'static>(n: i32) -> i32 {
    let b: temper_core::ListBuilder<T> = temper_core::listed::new_builder();
    temper_core::ListedTrait::len(&b).wrapping_add(n)
}

#[allow(clippy::extra_unused_type_parameters)]
pub(crate) fn relayed<
    S: SceneTrait + Clone + Send + Sync + 'static,
    T: Clone + Send + Sync + 'static,
>(
    x: T,
) -> T {
    tagged::<S, T>(x.clone())
}

pub(crate) fn none_in(length: i32) -> bool {
    length == 0
}

pub fn first<T: Clone + Send + Sync + 'static>(
    items: impl temper_core::ToListed<T>,
    fallback: T,
) -> T {
    let items = items.to_listed();
    if none_in(temper_core::ListedTrait::len(&*items)) {
        fallback.clone()
    } else {
        temper_core::ListedTrait::get(&*items, 0)
    }
}

pub(crate) fn held<T: Clone + Send + Sync + 'static>(h: Holder<T>) -> T {
    h.held()
}

pub(crate) fn total(ns: impl temper_core::ToList<i32>) -> i32 {
    let ns = ns.to_list();
    let mut sum: i32 = 0;
    {
        let mut i: i32 = 0;
        while i < temper_core::ListedTrait::len(&ns) {
            sum = sum.wrapping_add(temper_core::ListedTrait::get(&ns, i));
            i = i.wrapping_add(1);
        }
    }
    sum
}

pub(crate) fn indexed() {
    let counts: temper_core::ListBuilder<i32> = temper_core::listed::new_builder();
    temper_core::listed::add(&counts, 1, None);
    temper_core::listed::add(&counts, 10, None);
    let mut at: i32 = 0;
    {
        let k = at;
        temper_core::listed::set(
            &counts,
            k,
            temper_core::ListedTrait::get(&counts, k).wrapping_add(4),
        );
    }
    {
        temper_core::listed::set(
            &counts,
            1,
            temper_core::ListedTrait::get(&counts, 1).wrapping_mul(3),
        );
    }
    let was = {
        let k = {
            let t = at;
            at = t.wrapping_add(1);
            t
        };
        let t = temper_core::ListedTrait::get(&counts, k);
        temper_core::listed::set(&counts, k, t.wrapping_add(1));
        t
    };
    let pre = {
        let k = at;
        let t = temper_core::ListedTrait::get(&counts, k).wrapping_add(1);
        temper_core::listed::set(&counts, k, t);
        t
    };
    let post = {
        let t = temper_core::ListedTrait::get(&counts, 0);
        temper_core::listed::set(&counts, 0, t.wrapping_sub(1));
        t
    };
    let down = {
        let t = temper_core::ListedTrait::get(&counts, 0).wrapping_sub(1);
        temper_core::listed::set(&counts, 0, t);
        t
    };
    println!("{was} {pre} {post} {down} {at}");
}

pub(crate) fn both() {
    let mut x: i32 = 1;
    let mut y: i32 = 10;
    let locals = {
        let t = x;
        x = t.wrapping_add(1);
        t
    }
    .wrapping_add({
        let t = y;
        y = t.wrapping_add(1);
        t
    });
    let p = Counter::new(100);
    let q = Counter::new(1000);
    let fields = {
        let t = p.n();
        *p.0.n.write().unwrap() = t.wrapping_add(1);
        t
    }
    .wrapping_add({
        let t = q.n();
        *q.0.n.write().unwrap() = t.wrapping_add(1);
        t
    });
    let same = {
        let t = p.n();
        *p.0.n.write().unwrap() = t.wrapping_add(1);
        t
    }
    .wrapping_add({
        let t = p.n();
        *p.0.n.write().unwrap() = t.wrapping_add(1);
        t
    });
    let a: temper_core::ListBuilder<i32> = temper_core::listed::new_builder();
    temper_core::listed::add(&a, 3, None);
    let b: temper_core::ListBuilder<i32> = temper_core::listed::new_builder();
    temper_core::listed::add(&b, 30, None);
    let i: i32 = 0;
    let items = {
        let k = i;
        let t = temper_core::ListedTrait::get(&a, k);
        temper_core::listed::set(&a, k, t.wrapping_add(1));
        t
    }
    .wrapping_add({
        let k = i;
        let t = temper_core::ListedTrait::get(&b, k);
        temper_core::listed::set(&b, k, t.wrapping_add(1));
        t
    });
    println!(
        "{locals} {x} {y} {fields} {same} {} {} {items} {} {}",
        p.n(),
        q.n(),
        temper_core::ListedTrait::get(&a, 0),
        temper_core::ListedTrait::get(&b, 0)
    );
}

static TALLY: OnceLock<temper_core::MapBuilder<Arc<String>, i32>> = OnceLock::new();

pub(crate) fn tally() -> temper_core::MapBuilder<Arc<String>, i32> {
    crate::support::initialized(|| TALLY.get()).clone()
}

pub(crate) fn bump(key: impl ToArcString) -> temper_core::Result<i32> {
    let key = key.to_arc_string();
    {
        temper_core::MapBuilder::set(
            &tally(),
            key.clone(),
            temper_core::MappedTrait::get(&tally(), key.clone())?.wrapping_sub(1),
        );
    }
    let before = {
        let t = temper_core::MappedTrait::get(&tally(), key.clone())?;
        temper_core::MapBuilder::set(&tally(), key.clone(), t.wrapping_add(1));
        t
    };
    let after = {
        let t = temper_core::MappedTrait::get(&tally(), key.clone())?.wrapping_add(1);
        temper_core::MapBuilder::set(&tally(), key.clone(), t);
        t
    };
    Ok(before.wrapping_add(after))
}

pub(crate) fn first_same(
    a: impl temper_core::ToList<i32>,
    b: impl temper_core::ToList<i32>,
) -> bool {
    let a = a.to_list();
    let b = b.to_list();
    temper_core::ListedTrait::get(&a, 0) == temper_core::ListedTrait::get(&b, 0)
}
