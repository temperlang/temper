use std::sync::{Arc, OnceLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let strings: temper_core::List<Option<Arc<String>>> =
                Arc::new(vec![None, Some(Arc::new("b".to_string()))]);
            let no_strings: temper_core::List<Option<Arc<String>>> = Arc::new(vec![]);
            let ints: temper_core::List<Option<i32>> = Arc::new(vec![None, Some(2)]);
            let a = first_or_null(strings.clone()).flatten();
            let b = first_or_null(no_strings.clone()).flatten();
            let c = first_or_null(ints.clone()).flatten();
            println!("{} {} {}", a.is_none(), b.is_none(), c.is_none());
            println!("{}", {
                let subject = first_or_null(Arc::new(vec![Arc::new("x".to_string())]));
                subject
                    .clone()
                    .unwrap_or_else(|| Arc::new("none".to_string()))
            });
            println!(
                "{} {}",
                {
                    let subject2 = first_or_null(temper_core::listed::slice(&ints, 1, 2)).flatten();
                    subject2.unwrap_or(-1)
                },
                {
                    let subject3 = first_or_null(Arc::new(vec![3]));
                    subject3.unwrap_or(-1)
                }
            );
            let slot: Slot<Option<Arc<String>>> = Slot::new(None);
            println!(
                "{} {}",
                slot.kept(true).flatten().is_none(),
                slot.kept(false).flatten().is_none()
            );
            println!("{}", {
                let subject4 = Slot::new(Some(Arc::new("s".to_string())))
                    .kept(true)
                    .flatten();
                subject4
                    .clone()
                    .unwrap_or_else(|| Arc::new("none".to_string()))
            });
            let nameds: temper_core::List<Option<Named>> = Arc::new(vec![None]);
            let no_nameds: temper_core::List<Named> = Arc::new(vec![]);
            let n = first_or_null(Arc::new(vec![Named::new("n")]));
            println!(
                "{} {} {}",
                first_or_null(nameds.clone()).flatten().is_none(),
                first_or_null(no_nameds.clone()).is_none(),
                {
                    let subject5 = if n.is_none() {
                        None
                    } else {
                        Some(n.as_ref().unwrap().0.name.clone())
                    };
                    subject5
                        .clone()
                        .unwrap_or_else(|| Arc::new("none".to_string()))
                }
            );
            println!("{}", {
                let subject6 = {
                    let subject7 = first_or_null(no_nameds.clone());
                    subject7.as_ref().map(|subject7| subject7.0.name.clone())
                };
                subject6
                    .clone()
                    .unwrap_or_else(|| Arc::new("none".to_string()))
            });
            let none = None;
            println!("{}", {
                let subject8 =
                    with_default(Some(Arc::new("a".to_string())), none.clone().map(Some));
                subject8
                    .clone()
                    .unwrap_or_else(|| Arc::new("null".to_string()))
            });
            println!("{}", {
                let subject9 = with_default(
                    Some(Arc::new("a".to_string())),
                    Some(Some(Arc::new("b".to_string()))),
                );
                subject9
                    .clone()
                    .unwrap_or_else(|| Arc::new("null".to_string()))
            });
            println!("{}", {
                let subject10 = with_default(Some(Arc::new("a".to_string())), None);
                subject10
                    .clone()
                    .unwrap_or_else(|| Arc::new("null".to_string()))
            });
            println!("{}", {
                let subject11 = with_default::<Option<i32>>(Some(1), None);
                subject11.unwrap_or(-1)
            });
            println!(
                "{} {}",
                with_default(Arc::new("c".to_string()), None),
                with_default(4, Some(5))
            );
            Ok(())
        })
        .clone()
}

pub fn first_or_null<T: Clone + Send + Sync + 'static>(
    ls: impl temper_core::ToList<T>,
) -> Option<T> {
    let ls = ls.to_list();
    if temper_core::ListedTrait::is_empty(&ls) {
        None
    } else {
        Some(temper_core::ListedTrait::get(&ls, 0))
    }
}

struct SlotStruct<T: Clone + Send + Sync + 'static> {
    value: T,
}

#[derive(Clone)]
pub struct Slot<T: Clone + Send + Sync + 'static>(Arc<SlotStruct<T>>);

impl<T: Clone + Send + Sync + 'static> Slot<T> {
    pub fn new(value: T) -> Slot<T> {
        Slot(Arc::new(SlotStruct { value }))
    }

    pub fn kept(&self, keep: bool) -> Option<T> {
        if keep {
            Some(self.0.value.clone())
        } else {
            None
        }
    }

    pub fn value(&self) -> T {
        self.0.value.clone()
    }
}

temper_core::impl_any_value_trait!(Slot<T>, []);

struct NamedStruct {
    name: Arc<String>,
}

#[derive(Clone)]
pub struct Named(Arc<NamedStruct>);

impl Named {
    pub fn new(name: impl ToArcString) -> Named {
        let name = name.to_arc_string();
        Named(Arc::new(NamedStruct { name }))
    }

    pub fn name(&self) -> Arc<String> {
        self.0.name.clone()
    }
}

temper_core::impl_any_value_trait!(Named, []);

pub fn with_default<T: Clone + Send + Sync + 'static>(x: T, y: Option<T>) -> T {
    let y = y.unwrap_or_else(|| x.clone());
    y.clone()
}
