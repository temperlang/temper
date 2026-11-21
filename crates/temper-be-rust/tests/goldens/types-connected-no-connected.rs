use std::sync::{Arc, OnceLock, RwLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let counter = Counter::new();
            counter.add(2);
            counter.add(3);
            println!("counter.total(): {}", counter.total());
            println!("new Doubler().twice(5): {}", Doubler::new().twice(5));
            Ok(())
        })
        .clone()
}

struct CounterStruct {
    count: RwLock<i32>,
}

#[derive(Clone)]
pub struct Counter(Arc<CounterStruct>);

impl Counter {
    #[allow(clippy::new_without_default)]
    pub fn new() -> Counter {
        Counter(Arc::new(CounterStruct {
            count: RwLock::new(0),
        }))
    }

    pub fn add(&self, i: i32) {
        let mut count = self.0.count.write().unwrap();
        *count = (*count).wrapping_add(i);
    }

    pub fn total(&self) -> i32 {
        self.count().wrapping_add(1000)
    }

    fn count(&self) -> i32 {
        *self.0.count.read().unwrap()
    }
}

temper_core::impl_any_value_trait!(Counter, []);

struct DoublerStruct {}

#[derive(Clone)]
pub struct Doubler(Arc<DoublerStruct>);

impl Doubler {
    #[allow(clippy::new_without_default)]
    pub fn new() -> Doubler {
        Doubler(Arc::new(DoublerStruct {}))
    }

    pub fn twice(&self, i: i32) -> i32 {
        i.wrapping_mul(2).wrapping_add(1000)
    }
}

temper_core::impl_any_value_trait!(Doubler, []);
