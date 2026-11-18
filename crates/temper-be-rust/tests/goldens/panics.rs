use std::sync::{Arc, OnceLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let zero = failing(0).unwrap_or(-1);
            let a = failing(1).unwrap_or(-1);
            let b = (|| Ok::<_, temper_core::Error>(downcast(Shape::new(Circle::new()))?.0.side))()
                .unwrap_or(-2);
            let c = temper_core::int_div(1, zero).unwrap_or(-3);
            let d = (|| temper_core::int64_to_int32(temper_core::int64_div(1, zero as i64)?))()
                .unwrap_or(-4);
            let e = temper_core::string::to_int("nope", None).unwrap_or(-5);
            let f = temper_core::string::to_float64("x").unwrap_or(-6.0);
            let g = temper_core::string::from_code_point(1114112)
                .unwrap_or_else(|_| Arc::new("?".to_string()));
            let digits =
                temper_std::regex::RegexNodeTrait::compiled(&temper_std::regex::Repeat::new(
                    temper_std::regex::RegexNode::new(temper_std::regex::CodeSet::new(
                        Arc::new(vec![temper_std::regex::CodePart::new(
                            temper_std::regex::CodeRange::new(48, 57),
                        )]),
                        Some(false),
                    )),
                    1,
                    None,
                    Some(false),
                ));
            let h = (|| Ok::<_, temper_core::Error>(digits.find("none", None)?.full().value()))()
                .unwrap_or_else(|_| Arc::new("no digits".to_string()));
            let i = calling(Arc::new(move || failing(2)));
            println!(
                "{a} {b} {c} {d} {e} {} {g} {h} {i}",
                temper_core::float64::cmp(f, -6.0) == 0
            );
            println!("panicking");
            let p = inner().unwrap_or(-1);
            println!("after the panic {p}");
            Ok(())
        })
        .clone()
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

struct SquareStruct {
    side: i32,
}

#[derive(Clone)]
pub(crate) struct Square(Arc<SquareStruct>);

impl Square {
    #[allow(dead_code)]
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

struct CircleStruct {}

#[derive(Clone)]
pub(crate) struct Circle(Arc<CircleStruct>);

impl Circle {
    pub fn new() -> Circle {
        Circle(Arc::new(CircleStruct {}))
    }
}

impl ShapeTrait for Circle {
    fn clone_boxed(&self) -> Shape {
        Shape::new(self.clone())
    }
}

temper_core::impl_any_value_trait!(Circle, [Shape]);

pub(crate) fn failing(n: i32) -> temper_core::Result<i32> {
    if n > 0 {
        return Err(temper_core::Error::new());
    }
    Ok(n)
}

pub(crate) fn downcast(s: Shape) -> temper_core::Result<Square> {
    temper_core::cast::<Square>(s.clone()).ok_or_else(temper_core::Error::new)
}

pub(crate) fn calling(f: Arc<dyn Fn() -> temper_core::Result<i32> + Send + Sync>) -> i32 {
    f().unwrap_or(-9)
}

pub(crate) fn explode(n: i32) -> temper_core::Result<i32> {
    if n > 0 {
        panic!();
    }
    Err(temper_core::Error::new())
}

pub(crate) fn noted(name: impl ToArcString) -> i32 {
    let name = name.to_arc_string();
    println!("{name} fallback");
    -2
}

pub(crate) fn inner() -> temper_core::Result<i32> {
    Ok(explode(1).unwrap_or_else(|_| noted("inner")))
}
