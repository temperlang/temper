use std::sync::{Arc, OnceLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let _zero = failing(0).unwrap_or(-1);
            let a = failing(1).unwrap_or(-1);
            let b = (|| Ok::<_, temper_core::Error>(downcast(Shape::new(Circle::new()))?.0.side))()
                .unwrap_or(-2);
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
            let h = {
                let subject = {
                    let subject2 = {
                        let subject3 = digits.find("none", None);
                        subject3.as_ref().map(|subject3| subject3.full())
                    };
                    subject2.as_ref().map(|subject2| subject2.value())
                };
                subject
                    .clone()
                    .unwrap_or_else(|| Arc::new("no digits".to_string()))
            };
            let i = calling(Arc::new(move || failing(2)));
            println!(
                "{a} {b} {e} {} {g} {h} {i}",
                temper_core::float64::cmp(f, -6.0) == 0
            );
            let k = temper_core::string::to_int64("zz", None).unwrap_or(-7);
            let l = temper_core::string::to_int("99999999999", None).unwrap_or(-8);
            let m = temper_core::float64::to_int(1e20).unwrap_or(-9);
            let n = temper_core::int64_to_int32(5000000000).unwrap_or(-10);
            let points: temper_core::List<i32> = Arc::new(vec![65, 1114112]);
            let o =
                temper_core::string::from_code_points(&*temper_core::Listed::new(points.clone()))
                    .unwrap_or_else(|e2| temper_core::int_to_string(e2.code_point(), Some(16)));
            let q = appending(-1).unwrap_or_else(|e3| Arc::new(e3.code_point().to_string()));
            let builder: temper_core::MapBuilder<Arc<String>, i32> = temper_core::MapBuilder::new();
            let r =
                temper_core::MappedTrait::get(&builder, Arc::new("x".to_string())).unwrap_or(-11);
            let r2 = (|| {
                Ok::<_, temper_core::MissingKey>(Arc::new(
                    temper_core::MapBuilder::remove(&builder, Arc::new("y".to_string()))?
                        .to_string(),
                ))
            })()
            .unwrap_or_else(|e4| e4.message());
            let s = (|| {
                Ok::<_, temper_std::temporal::TemporalError>(
                    temper_std::temporal::Date::new(2023, 2, 30)?.to_string(),
                )
            })()
            .unwrap_or_else(|_| Arc::new("no date".to_string()));
            let t = (|| {
                Ok::<_, temper_std::temporal::TemporalError>(
                    temper_std::temporal::Date::from_iso_string("2023-13-01")?.to_string(),
                )
            })()
            .unwrap_or_else(|_| Arc::new("bad date".to_string()));
            let u = parsing("[1,").unwrap_or_else(|_| Arc::new("bad json".to_string()));
            let partial = temper_std::json::JsonTextProducer::new(None);
            partial.start_object();
            let v = partial.to_json_string().unwrap_or_else(|e5| e5.problem());
            println!("{k} {l} {m} {n} {o} {q} {r} {r2} {s} {t} {u}");
            println!("{v}");
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
    if temper_core::is::<Square>(s.clone()) {
        Ok(temper_core::cast::<Square>(s.clone()).unwrap())
    } else {
        Err(temper_core::Error::new())
    }
}

pub(crate) fn calling(f: Arc<dyn Fn() -> temper_core::Result<i32> + Send + Sync>) -> i32 {
    f().unwrap_or(-9)
}

pub(crate) fn appending(code_point: i32) -> Result<Arc<String>, temper_core::InvalidCodePoint> {
    let sb = Arc::new(std::sync::RwLock::new(String::new()));
    temper_core::string::builder::append_code_point(&sb, code_point)?;
    Ok(temper_core::string::builder::to_string(&sb))
}

pub(crate) fn parsing(text: impl ToArcString) -> Result<Arc<String>, temper_std::json::JsonError> {
    let text = text.to_arc_string();
    temper_std::json::parse_json(text.clone())?;
    Ok(Arc::new("parsed".to_string()))
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
