use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let dot = shape_of(1);
            let line = shape_of(2);
            println!(
                "{} {} {} {}",
                named(dot.clone()),
                named(line.clone()),
                pair(dot.clone(), line.clone()),
                pair(line.clone(), dot.clone())
            );
            println!(
                "{} {} {} {}",
                code(line.clone(), true),
                code(dot.clone(), false),
                code(line.clone(), false),
                Namer::new().name(line.clone())
            );
            Ok(())
        })
        .clone()
}

pub(crate) trait ShapeTrait:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync
{
    #[allow(dead_code)]
    fn clone_boxed(&self) -> Shape;

    fn size(&self) -> i32;
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
pub(crate) struct Dot(Arc<DotStruct>);

impl Dot {
    pub fn new(size: i32) -> Dot {
        Dot(Arc::new(DotStruct { size }))
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
pub(crate) struct Line(Arc<LineStruct>);

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

pub(crate) fn shape_of(n: i32) -> Shape {
    if n > 1 {
        Shape::new(Line::new(n))
    } else {
        Shape::new(Dot::new(n))
    }
}

pub(crate) fn named(s: Shape) -> Arc<String> {
    let name = if temper_core::is::<Dot>(s.clone()) {
        Arc::new("dot".to_string())
    } else if temper_core::is::<Line>(s.clone()) {
        Arc::new("line".to_string())
    } else {
        panic!()
    };
    name.clone()
}

pub(crate) fn pair(s: Shape, t: Shape) -> Arc<String> {
    if temper_core::is::<Dot>(s.clone()) {
        if temper_core::is::<Dot>(t.clone()) {
            Arc::new("dd".to_string())
        } else if temper_core::is::<Line>(t.clone()) {
            Arc::new("dl".to_string())
        } else {
            panic!();
        }
    } else if temper_core::is::<Line>(s.clone()) {
        Arc::new("l".to_string())
    } else {
        panic!();
    }
}

pub(crate) fn doubled(n: i32) -> i32 {
    n.wrapping_mul(2)
}

pub(crate) fn code(s: Shape, early: bool) -> i32 {
    if early {
        return if temper_core::is::<Dot>(s.clone()) {
            1
        } else if temper_core::is::<Line>(s.clone()) {
            2
        } else {
            panic!()
        };
    }
    if s.size() > 1 {
        if temper_core::is::<Dot>(s.clone()) {
            10
        } else if temper_core::is::<Line>(s.clone()) {
            20
        } else {
            panic!();
        }
    } else {
        doubled(if temper_core::is::<Dot>(s.clone()) {
            3
        } else if temper_core::is::<Line>(s.clone()) {
            4
        } else {
            panic!()
        })
    }
}

struct NamerStruct {}

#[derive(Clone)]
pub(crate) struct Namer(Arc<NamerStruct>);

impl Namer {
    pub fn new() -> Namer {
        Namer(Arc::new(NamerStruct {}))
    }

    pub fn name(&self, s: Shape) -> Arc<String> {
        if temper_core::is::<Dot>(s.clone()) {
            Arc::new("a dot".to_string())
        } else if temper_core::is::<Line>(s.clone()) {
            Arc::new("a line".to_string())
        } else {
            panic!();
        }
    }
}

temper_core::impl_any_value_trait!(Namer, []);
