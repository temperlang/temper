use std::sync::{Arc, OnceLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            println!("sum(1, 2): {}", sum(1, 2, None));
            println!(
                "length \"\" vs null vs (): {} vs {} vs {}",
                length(Some("")),
                length(None::<Arc<String>>),
                length(None::<Arc<String>>)
            );
            println!("twice(5): {}", twice(5));
            let is_connected = sum(1, 2, None) == 3;
            if is_connected {
                println!("prod(new Hidden(1), 2): {}", prod(Hidden::new(1), 2));
            }
            let hider = Hider::new(3);
            println!("new Hider(3).plus(4): {}", hider.plus(4));
            if is_connected {
                println!("new Hider(3).times(4): {}", hider.times(4));
            }
            Ok(())
        })
        .clone()
}

#[allow(dead_code)]
pub(crate) fn sum_of3(a: i32, b: i32, c: i32) -> i32 {
    a.wrapping_add(b).wrapping_add(c)
}

pub fn sum(i: i32, j: i32, bonus: Option<i32>) -> i32 {
    let bonus = bonus.unwrap_or(0);
    sum_of3(i, j, bonus).wrapping_add(1000)
}

pub fn length(string: Option<impl ToArcString>) -> i32 {
    let string = string.map(|x| x.to_arc_string());
    if let Some(string) = &string {
        temper_core::string::count_between(string, 0usize, string.len()).wrapping_add(1000)
    } else {
        999
    }
}

pub fn twice(i: i32) -> i32 {
    i.wrapping_mul(2).wrapping_add(1000)
}

struct HiddenStruct {
    i: i32,
}

#[derive(Clone)]
pub(crate) struct Hidden(Arc<HiddenStruct>);

impl Hidden {
    pub fn new(i: i32) -> Hidden {
        Hidden(Arc::new(HiddenStruct { i }))
    }

    pub fn i(&self) -> i32 {
        self.0.i
    }
}

temper_core::impl_any_value_trait!(Hidden, []);

pub(crate) fn prod(hidden: Hidden, j: i32) -> i32 {
    crate::_connected::prod(hidden, j)
}

struct HiderStruct {
    hidden: Hidden,
}

#[derive(Clone)]
pub struct Hider(Arc<HiderStruct>);

impl Hider {
    pub fn new(i: i32) -> Hider {
        Hider(Arc::new(HiderStruct {
            hidden: Hidden::new(i),
        }))
    }

    pub fn plus(&self, j: i32) -> i32 {
        sum(self.0.hidden.0.i, j, None)
    }

    pub fn times(&self, j: i32) -> i32 {
        prod(self.0.hidden.clone(), j)
    }
}

temper_core::impl_any_value_trait!(Hider, []);
