use super::Hidden;
use more::Support;

mod more;

pub(crate) fn sum(i: i32, j: i32, bonus: i32) -> i32 {
    i + j + bonus
}

pub(crate) fn prod(hidden: Hidden, j: i32) -> i32 {
    let support = Support {};
    support.prod(hidden.i(), j)
}
