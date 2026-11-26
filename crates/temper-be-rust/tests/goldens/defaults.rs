use std::sync::{Arc, OnceLock, RwLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            *CALLS.write().unwrap() = Some(0);
            println!("{}", range(1, None, None));
            println!("{}", range(1, Some(5), None));
            println!("{}", range(1, Some(5), Some(3)));
            println!("{}", range(2, None, Some(7)));
            println!("calls {}", calls());
            println!("{}", tag("plain", None::<Arc<String>>));
            println!("{}", tag("marked", Some("#")));
            println!("{}", named(None::<Arc<String>>));
            println!("{}", named(None::<Arc<String>>));
            println!("{}", named(Some("Ada")));
            println!(
                "{}",
                Greeter::new(None::<Arc<String>>, None::<Arc<String>>)
                    .greet(None::<Arc<String>>, None)
            );
            println!(
                "{}",
                Greeter::new(Some("Hi"), None::<Arc<String>>).greet(Some("you"), Some(2))
            );
            println!(
                "{}",
                Greeter::new(None::<Arc<String>>, Some("?")).greet(None::<Arc<String>>, Some(2))
            );
            println!("{}", Greeter::shout("hey", None::<Arc<String>>));
            let counter = Counter::new(None);
            counter.add(None);
            println!(
                "count {} {}",
                counter.add(Some(5)),
                Counter::new(Some(1)).add(None)
            );
            println!(
                "{}",
                crate::helpers::wrap("a", None::<Arc<String>>, None::<Arc<String>>)
            );
            println!(
                "{}",
                crate::helpers::wrap("b", Some("<"), None::<Arc<String>>)
            );
            println!("{}", crate::helpers::wrap("c", Some("<"), Some(">")));
            let pad = crate::helpers::Pad::new(None, None);
            println!("{}", pad.apply("x", None::<Arc<String>>));
            println!(
                "{}",
                crate::helpers::Pad::new(Some(0), Some(3)).apply("y", Some("-"))
            );
            Ok(())
        })
        .clone()
}

static CALLS: RwLock<Option<i32>> = RwLock::new(None);

pub(crate) fn calls() -> i32 {
    crate::support::initialized(|| *CALLS.read().unwrap())
}

pub(crate) fn next() -> i32 {
    crate::support::initialized(|| {
        let mut calls = CALLS.write().unwrap();
        *calls = Some((*calls)?.wrapping_add(1));
        Some(())
    });
    calls()
}

pub(crate) fn range(start: i32, end: Option<i32>, step: Option<i32>) -> Arc<String> {
    let end = end.unwrap_or_else(|| start.wrapping_add(10));
    let step = step.unwrap_or_else(next);
    Arc::new(format!("{start}..{end} by {step}"))
}

pub(crate) fn tag(text: impl ToArcString, mark: Option<impl ToArcString>) -> Arc<String> {
    let text = text.to_arc_string();
    let mark = mark.map(|x| x.to_arc_string());
    if let Some(mark) = &mark {
        Arc::new(format!("{mark}{text}"))
    } else {
        text.clone()
    }
}

pub(crate) fn named(name: Option<impl ToArcString>) -> Arc<String> {
    let name = name.map(|x| x.to_arc_string());
    let name = name.or_else(|| Some(Arc::new("nobody".to_string())));
    name.clone().unwrap_or_else(|| Arc::new("null".to_string()))
}

struct GreeterStruct {
    greeting: Arc<String>,
    punct: Arc<String>,
}

#[derive(Clone)]
pub(crate) struct Greeter(Arc<GreeterStruct>);

impl Greeter {
    pub fn new(greeting: Option<impl ToArcString>, punct: Option<impl ToArcString>) -> Greeter {
        let greeting = greeting.map(|x| x.to_arc_string());
        let punct = punct.map(|x| x.to_arc_string());
        let greeting = greeting.unwrap_or_else(|| Arc::new("Hello".to_string()));
        let punct = punct.unwrap_or_else(|| Arc::new("!".to_string()));
        Greeter(Arc::new(GreeterStruct { greeting, punct }))
    }

    pub fn greet(&self, name: Option<impl ToArcString>, times: Option<i32>) -> Arc<String> {
        let name = name.map(|x| x.to_arc_string());
        let name = name.unwrap_or_else(|| Arc::new("world".to_string()));
        let times = times.unwrap_or(1);
        let mut out = Arc::new(String::new());
        {
            let mut i: i32 = 0;
            while i < times {
                out = Arc::new(format!("{out}{}, {name}{}", self.0.greeting, self.0.punct));
                i = i.wrapping_add(1);
            }
        }
        out.clone()
    }

    pub fn shout(text: impl ToArcString, mark: Option<impl ToArcString>) -> Arc<String> {
        let text = text.to_arc_string();
        let mark = mark.map(|x| x.to_arc_string());
        let mark = mark.unwrap_or_else(|| Arc::new("!".to_string()));
        Arc::new(format!("{text}{mark}{mark}"))
    }
}

temper_core::impl_any_value_trait!(Greeter, []);

struct CounterStruct {
    count: RwLock<i32>,
}

#[derive(Clone)]
pub(crate) struct Counter(Arc<CounterStruct>);

impl Counter {
    pub fn new(start: Option<i32>) -> Counter {
        let start = start.unwrap_or(10);
        Counter(Arc::new(CounterStruct {
            count: RwLock::new(start),
        }))
    }

    pub fn add(&self, by: Option<i32>) -> i32 {
        let by = by.unwrap_or(1);
        {
            let mut count = self.0.count.write().unwrap();
            *count = (*count).wrapping_add(by);
        }
        self.count()
    }

    pub fn count(&self) -> i32 {
        *self.0.count.read().unwrap()
    }
}

temper_core::impl_any_value_trait!(Counter, []);
