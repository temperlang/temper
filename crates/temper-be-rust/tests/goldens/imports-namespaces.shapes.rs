use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            UNIT.set(1).unwrap_or_else(|_| panic!());
            Ok(())
        })
        .clone()
}

static UNIT: OnceLock<i32> = OnceLock::new();

pub fn unit() -> i32 {
    *crate::support::initialized(|| UNIT.get())
}

pub fn twice(x: i32) -> i32 {
    x.wrapping_mul(2)
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

    pub fn value(&self) -> T {
        self.0.value.clone()
    }
}

temper_core::impl_any_value_trait!(Box<T>, []);

pub trait ShapeTrait: temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync {
    fn clone_boxed(&self) -> Shape;

    fn area(&self) -> i32;
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
}

temper_core::impl_any_value_trait_for_interface!(Shape);

impl std::ops::Deref for Shape {
    type Target = dyn ShapeTrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

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

    pub fn sized(side: i32) -> Square {
        Square::new(side)
    }

    pub fn side(&self) -> i32 {
        self.0.side
    }
}

impl ShapeTrait for Square {
    fn clone_boxed(&self) -> Shape {
        Shape::new(self.clone())
    }

    fn area(&self) -> i32 {
        self.area()
    }
}

temper_core::impl_any_value_trait!(Square, [Shape]);
