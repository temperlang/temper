use std::sync::{Arc, OnceLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let calls2 = crate::helpers::calls();
            BASE2.set(1).unwrap_or_else(|_| panic!());
            println!("{}", crate::helpers::deep::greet("modules"));
            println!("sum {}", sum(3));
            println!(
                "share {} {}",
                crate::core::share(7, 2),
                crate::helpers::int_doubled(21)
            );
            println!(
                "{}, {}",
                Engine::new("own").0.name,
                crate::machine::describe()
            );
            crate::helpers::bump();
            crate::helpers::bump();
            println!("calls {calls2}");
            println!("calls now {}", crate::helpers::bump());
            let half = crate::core::Ratio::new(1, 2);
            println!(
                "{} {} {}",
                half == crate::core::Ratio::new(2, 4),
                {
                    let left = crate::core::ratio_of(3, 6);
                    if let Some(left) = &left {
                        left.eq(half.clone())
                    } else {
                        false
                    }
                },
                !{
                    let left2 = half.clone();
                    let right = crate::core::ratio_of(1, 0);
                    if let Some(right) = &right {
                        left2.eq(right.clone())
                    } else {
                        false
                    }
                }
            );
            let moved = {
                let spread = crate::state::Spot::new(1, 2);
                crate::state::Spot::new(spread.x(), 5)
            };
            println!("spot {} {}", moved.x(), moved.y());
            Ok(())
        })
        .clone()
}

struct EngineStruct {
    name: Arc<String>,
}

#[derive(Clone)]
pub(crate) struct Engine(Arc<EngineStruct>);

impl Engine {
    pub fn new(name: impl ToArcString) -> Engine {
        let name = name.to_arc_string();
        Engine(Arc::new(EngineStruct { name }))
    }
}

temper_core::impl_any_value_trait!(Engine, []);

static BASE2: OnceLock<i32> = OnceLock::new();

pub(crate) fn base2() -> i32 {
    *crate::support::initialized(|| BASE2.get())
}

pub fn sum(n: i32) -> i32 {
    crate::helpers::twice(n)
        .wrapping_add(crate::helpers::base())
        .wrapping_add(base2())
}
