use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            FIRST_ID.set(1).unwrap_or_else(|_| panic!());
            Ok(())
        })
        .clone()
}

struct StateStruct {
    id: i32,
}

#[derive(Clone)]
pub struct State(Arc<StateStruct>);

impl State {
    pub fn new(id: i32) -> State {
        State(Arc::new(StateStruct { id }))
    }

    pub fn id(&self) -> i32 {
        self.0.id
    }
}

temper_core::impl_any_value_trait!(State, []);

struct EngineStruct {
    n: i32,
}

#[derive(Clone)]
pub struct Engine(Arc<EngineStruct>);

impl Engine {
    pub fn new(n: i32) -> Engine {
        Engine(Arc::new(EngineStruct { n }))
    }

    pub fn n(&self) -> i32 {
        self.0.n
    }
}

temper_core::impl_any_value_trait!(Engine, []);

struct PartsStruct {
    count: i32,
}

#[derive(Clone)]
pub struct Parts(Arc<PartsStruct>);

impl Parts {
    pub fn new(count: i32) -> Parts {
        Parts(Arc::new(PartsStruct { count }))
    }

    pub fn count(&self) -> i32 {
        self.0.count
    }
}

temper_core::impl_any_value_trait!(Parts, []);

static FIRST_ID: OnceLock<i32> = OnceLock::new();

pub fn first_id() -> i32 {
    *crate::support::initialized(|| FIRST_ID.get())
}
