use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            println!("{}", crate::shapes::unit());
            println!("{}", crate::shapes::twice(21));
            let r#box: crate::shapes::Box<i32> = crate::shapes::Box::new(3);
            let squares: temper_core::List<crate::shapes::Shape> = Arc::new(vec![
                crate::shapes::Shape::new(crate::shapes::Square::new(4)),
                crate::shapes::Shape::new(crate::shapes::Square::new(crate::shapes::unit())),
                crate::shapes::Shape::new(crate::shapes::Square::sized(5)),
            ]);
            {
                let length = temper_core::ListedTrait::len(&squares);
                let mut i: i32 = 0;
                while i < length {
                    let s = temper_core::ListedTrait::get(&squares, i);
                    i = i.wrapping_add(1);
                    println!("area {}", crate::shapes::ShapeTrait::area(&s));
                }
            }
            let mixed: temper_core::List<crate::shapes::Shape> = Arc::new(vec![
                crate::shapes::Shape::new(Circle::new(2)),
                crate::shapes::Shape::new(crate::shapes::Square::new(2)),
            ]);
            {
                let length2 = temper_core::ListedTrait::len(&mixed);
                let mut i2: i32 = 0;
                while i2 < length2 {
                    let s2 = temper_core::ListedTrait::get(&mixed, i2);
                    i2 = i2.wrapping_add(1);
                    {
                        if temper_core::is::<crate::shapes::Square>(s2.clone()) {
                            println!(
                                "a square of {}",
                                temper_core::cast::<crate::shapes::Square>(s2.clone())
                                    .unwrap()
                                    .area()
                            );
                        } else {
                            println!("something else of {}", crate::shapes::ShapeTrait::area(&s2));
                        }
                    }
                }
            }
            println!("{}", bump(r#box.value()));
            Ok(())
        })
        .clone()
}

struct CircleStruct {
    r: i32,
}

#[derive(Clone)]
pub(crate) struct Circle(Arc<CircleStruct>);

impl Circle {
    pub fn new(r: i32) -> Circle {
        Circle(Arc::new(CircleStruct { r }))
    }

    pub fn area(&self) -> i32 {
        3i32.wrapping_mul(self.0.r).wrapping_mul(self.0.r)
    }
}

impl crate::shapes::ShapeTrait for Circle {
    fn clone_boxed(&self) -> crate::shapes::Shape {
        crate::shapes::Shape::new(self.clone())
    }

    fn area(&self) -> i32 {
        self.area()
    }
}

temper_core::impl_any_value_trait!(Circle, [crate::shapes::Shape]);

pub(crate) fn bump(shapes: i32) -> i32 {
    shapes.wrapping_add(1)
}
