use std::sync::{Arc, OnceLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            CHANGE_TYPE_ADDITION
                .set(Arc::new("Addition".to_string()))
                .unwrap_or_else(|_| panic!());
            CHANGE_TYPE_DELETION
                .set(Arc::new("Deletion".to_string()))
                .unwrap_or_else(|_| panic!());
            CHANGE_TYPE_UNCHANGED
                .set(Arc::new("Unchanged".to_string()))
                .unwrap_or_else(|_| panic!());
            println!(
                "{}",
                format_patch(diff(
                    temper_core::string::split("Hello,\nWorld!", Arc::new("\n".to_string())),
                    temper_core::string::split("Hello,\nCosmos!", Arc::new("\n".to_string())),
                    Arc::new(move |a: Arc<String>, b: Arc<String>| a == b),
                ))
            );
            Ok(())
        })
        .clone()
}

struct ChangeStruct<T: Clone + Send + Sync + 'static> {
    r#type: Arc<String>,
    left_index: i32,
    right_index: i32,
    items: temper_core::List<T>,
}

#[derive(Clone)]
pub struct Change<T: Clone + Send + Sync + 'static>(Arc<ChangeStruct<T>>);

impl<T: Clone + Send + Sync + 'static> Change<T> {
    pub fn new(
        r#type: impl ToArcString,
        left_index: i32,
        right_index: i32,
        items: impl temper_core::ToList<T>,
    ) -> Change<T> {
        let r#type = r#type.to_arc_string();
        let items = items.to_list();
        Change(Arc::new(ChangeStruct {
            r#type,
            left_index,
            right_index,
            items,
        }))
    }

    pub fn r#type(&self) -> Arc<String> {
        self.0.r#type.clone()
    }

    pub fn left_index(&self) -> i32 {
        self.0.left_index
    }

    pub fn right_index(&self) -> i32 {
        self.0.right_index
    }

    pub fn items(&self) -> temper_core::List<T> {
        self.0.items.clone()
    }
}

temper_core::impl_any_value_trait!(Change<T>, []);

struct PatchStruct<T: Clone + Send + Sync + 'static> {
    changes: temper_core::List<Change<T>>,
}

#[derive(Clone)]
pub struct Patch<T: Clone + Send + Sync + 'static>(Arc<PatchStruct<T>>);

impl<T: Clone + Send + Sync + 'static> Patch<T> {
    pub fn new(changes: impl temper_core::ToList<Change<T>>) -> Patch<T> {
        let changes = changes.to_list();
        Patch(Arc::new(PatchStruct { changes }))
    }

    pub fn changes(&self) -> temper_core::List<Change<T>> {
        self.0.changes.clone()
    }
}

temper_core::impl_any_value_trait!(Patch<T>, []);

static CHANGE_TYPE_ADDITION: OnceLock<Arc<String>> = OnceLock::new();

pub fn change_type_addition() -> Arc<String> {
    crate::support::initialized(|| CHANGE_TYPE_ADDITION.get()).clone()
}

static CHANGE_TYPE_DELETION: OnceLock<Arc<String>> = OnceLock::new();

pub fn change_type_deletion() -> Arc<String> {
    crate::support::initialized(|| CHANGE_TYPE_DELETION.get()).clone()
}

pub fn format_patch(patch: Patch<Arc<String>>) -> Arc<String> {
    let mut formatted = Arc::new(String::new());
    let changes: temper_core::List<Change<Arc<String>>> = patch.0.changes.clone();
    let n = temper_core::ListedTrait::len(&changes);
    {
        let mut i: i32 = 0;
        while i < n {
            let change: Change<Arc<String>> = temper_core::ListedTrait::get(&changes, i);
            let change_type = change.0.r#type.clone();
            let items: temper_core::List<Arc<String>> = change.0.items.clone();
            let prefix = if change_type == change_type_addition() {
                Arc::new("+".to_string())
            } else if change_type == change_type_deletion() {
                Arc::new("-".to_string())
            } else {
                Arc::new(" ".to_string())
            };
            let n_items = temper_core::ListedTrait::len(&items);
            {
                let mut j: i32 = 0;
                while j < n_items {
                    let item = temper_core::ListedTrait::get(&items, j);
                    formatted = Arc::new(format!("{formatted}{prefix}{item}\n"));
                    j = j.wrapping_add(1);
                }
            }
            i = i.wrapping_add(1);
        }
    }
    formatted.clone()
}

static CHANGE_TYPE_UNCHANGED: OnceLock<Arc<String>> = OnceLock::new();

pub fn change_type_unchanged() -> Arc<String> {
    crate::support::initialized(|| CHANGE_TYPE_UNCHANGED.get()).clone()
}

struct DiffPathStruct {
    left_index: i32,
    right_index: i32,
    change_type: Arc<String>,
    previous: Option<DiffPath>,
}

#[derive(Clone)]
pub(crate) struct DiffPath(Arc<DiffPathStruct>);

impl DiffPath {
    pub fn new(
        left_index: i32,
        right_index: i32,
        change_type: impl ToArcString,
        previous: Option<DiffPath>,
    ) -> DiffPath {
        let change_type = change_type.to_arc_string();
        DiffPath(Arc::new(DiffPathStruct {
            left_index,
            right_index,
            change_type,
            previous,
        }))
    }
}

temper_core::impl_any_value_trait!(DiffPath, []);

pub fn diff<T: Clone + Send + Sync + 'static>(
    left: impl temper_core::ToList<T>,
    right: impl temper_core::ToList<T>,
    eq: Arc<dyn Fn(T, T) -> bool + Send + Sync>,
) -> Patch<T> {
    let left = left.to_list();
    let right = right.to_list();
    let left_length = temper_core::ListedTrait::len(&left);
    let right_length = temper_core::ListedTrait::len(&right);
    let reached_row_length = left_length.wrapping_add(1);
    let reached_col_length = right_length.wrapping_add(1);
    let reached = temper_core::DenseBitVector::with_capacity(
        reached_row_length.wrapping_mul(reached_col_length),
    );
    let cost_zero_edges: temper_core::Deque<Option<DiffPath>> = temper_core::deque::new();
    temper_core::deque::add(&cost_zero_edges, None);
    let cost_one_edges: temper_core::Deque<DiffPath> = temper_core::deque::new();
    loop {
        let diff_path = if !temper_core::deque::is_empty(&cost_zero_edges) {
            temper_core::deque::remove_first(&cost_zero_edges)
        } else if !temper_core::deque::is_empty(&cost_one_edges) {
            Some(temper_core::deque::remove_first(&cost_one_edges))
        } else {
            None
        };
        let left_index = {
            let subject = diff_path.as_ref().map(|diff_path| diff_path.0.left_index);
            subject.unwrap_or(0)
        };
        let right_index = {
            let subject = diff_path.as_ref().map(|diff_path| diff_path.0.right_index);
            subject.unwrap_or(0)
        };
        if left_index == left_length && right_index == right_length {
            let path_elements: temper_core::ListBuilder<DiffPath> =
                temper_core::listed::new_builder();
            {
                let mut path_element = diff_path.clone();
                while path_element.is_some() {
                    let path = path_element.clone().unwrap();
                    temper_core::listed::add(&path_elements, path.clone(), None);
                    path_element = path.0.previous.clone();
                }
            }
            temper_core::listed::reverse(&path_elements);
            let mut left_patch_index: i32 = 0;
            let mut right_patch_index: i32 = 0;
            let changes: temper_core::ListBuilder<Change<T>> = temper_core::listed::new_builder();
            let n = temper_core::ListedTrait::len(&path_elements);
            {
                let mut i: i32 = 0;
                let mut end: i32;
                while i < n {
                    let change_type = temper_core::ListedTrait::get(&path_elements, i)
                        .0
                        .change_type
                        .clone();
                    end = i.wrapping_add(1);
                    while end < n
                        && change_type
                            == temper_core::ListedTrait::get(&path_elements, end)
                                .0
                                .change_type
                    {
                        end = end.wrapping_add(1);
                    }
                    let n_items = end.wrapping_sub(i);
                    let items: temper_core::List<T>;
                    let mut n_left_items: i32 = 0;
                    let mut n_right_items: i32 = 0;
                    if change_type == change_type_addition() {
                        items = temper_core::listed::slice(
                            &right,
                            right_patch_index,
                            right_patch_index.wrapping_add(n_items),
                        );
                        n_right_items = n_items;
                    } else {
                        items = temper_core::listed::slice(
                            &left,
                            left_patch_index,
                            left_patch_index.wrapping_add(n_items),
                        );
                        if change_type == change_type_unchanged() {
                            n_right_items = n_items;
                        }
                        n_left_items = n_items;
                    }
                    temper_core::listed::add(
                        &changes,
                        Change::new(
                            change_type.clone(),
                            left_patch_index,
                            right_patch_index,
                            items.clone(),
                        ),
                        None,
                    );
                    left_patch_index = left_patch_index.wrapping_add(n_left_items);
                    right_patch_index = right_patch_index.wrapping_add(n_right_items);
                    i = end;
                }
            }
            return Patch::new(temper_core::ListedTrait::to_list(&changes));
        }
        if left_index < left_length {
            if right_index < right_length {
                let same_reached_index = left_index
                    .wrapping_add(1)
                    .wrapping_add(right_index.wrapping_add(1).wrapping_mul(reached_row_length));
                if !reached.get(same_reached_index)
                    && eq(
                        temper_core::ListedTrait::get(&left, left_index),
                        temper_core::ListedTrait::get(&right, right_index),
                    )
                {
                    reached.set(same_reached_index, true);
                    temper_core::deque::add(
                        &cost_zero_edges,
                        Some(DiffPath::new(
                            left_index.wrapping_add(1),
                            right_index.wrapping_add(1),
                            change_type_unchanged(),
                            diff_path.clone(),
                        )),
                    );
                }
            }
            let del_reached_index = left_index
                .wrapping_add(1)
                .wrapping_add(right_index.wrapping_mul(reached_row_length));
            if !reached.get(del_reached_index) {
                reached.set(del_reached_index, true);
                temper_core::deque::add(
                    &cost_one_edges,
                    DiffPath::new(
                        left_index.wrapping_add(1),
                        right_index,
                        change_type_deletion(),
                        diff_path.clone(),
                    ),
                );
            }
        }
        if right_index < right_length {
            let add_reached_index = left_index
                .wrapping_add(right_index.wrapping_add(1).wrapping_mul(reached_row_length));
            if !reached.get(add_reached_index) {
                reached.set(add_reached_index, true);
                temper_core::deque::add(
                    &cost_one_edges,
                    DiffPath::new(
                        left_index,
                        right_index.wrapping_add(1),
                        change_type_addition(),
                        diff_path.clone(),
                    ),
                );
            }
        }
    }
}

pub mod builders {
    use super::*;

    #[derive(Clone)]
    pub struct ChangeBuilder<T: Clone + Send + Sync + 'static> {
        pub r#type: Arc<String>,
        pub left_index: i32,
        pub right_index: i32,
        pub items: temper_core::List<T>,
    }

    impl<T: Clone + Send + Sync + 'static> ChangeBuilder<T> {
        pub fn build(self) -> Change<T> {
            Change::new(self.r#type, self.left_index, self.right_index, self.items)
        }
    }
}
