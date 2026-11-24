use std::sync::{Arc, OnceLock, RwLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            Greet::new(None::<Arc<String>>, None);
            Greet::new(Some("Bye!"), None);
            Greet::new(Some("Hola!"), Some(true));
            Greet::new(Some("Salut!"), None);
            Greet::new(None::<Arc<String>>, Some(true));
            Greet::new(None::<Arc<String>>, Some(true));
            {
                let arg = true;
                Greet::new(Some("Ni hao!"), Some(arg));
            }
            *MESSAGE.write().unwrap() = Some(Arc::new("Hi!".to_string()));
            *REPEAT.write().unwrap() = Some(false);
            {
                let arg2 = change_message();
                let arg3 = change_repeat();
                Greet::new(Some(arg3.clone()), Some(arg2));
            }
            println!("{}", Person::new(None::<Arc<String>>, None));
            println!("{}", Person::new(Some("Bob"), None));
            println!("{}", Person::new(Some("Carrie"), Some(50)));
            Person::new(Some("Dan"), None).greet(Some("Hallo"));
            println!(
                "{}",
                calc(2, Arc::new(move |n: i32| Arc::new(n.to_string())), None)
            );
            println!(
                "{}",
                calc(
                    2,
                    Arc::new(move |n: i32| Arc::new(n.to_string())),
                    Some(CalcOptions::new(Some(3), Some(4))),
                )
            );
            println!(
                "{}",
                calc(
                    2,
                    Arc::new(move |n: i32| Arc::new(n.to_string())),
                    Some(CalcOptions::new(Some(3), None)),
                )
            );
            println!(
                "{}",
                calc(
                    2,
                    Arc::new(move |n: i32| Arc::new(n.to_string())),
                    Some(CalcOptions::new(None, Some(4))),
                )
            );
            println!(
                "{}",
                calc(
                    2,
                    Arc::new(move |n: i32| Arc::new(format!("<{n}>"))),
                    Some(CalcOptions::new(Some(5), None)),
                )
            );
            calc(
                2,
                Arc::new(move |n: i32| {
                    println!("{n}");
                }),
                Some(CalcOptions::new(Some(-2), None)),
            );
            Ok(())
        })
        .clone()
}

struct GreetStruct {}

#[derive(Clone)]
pub struct Greet(Arc<GreetStruct>);

impl Greet {
    pub fn new(message: Option<impl ToArcString>, repeat: Option<bool>) -> Greet {
        let message = message.map(|x| x.to_arc_string());
        let message = message.unwrap_or_else(|| Arc::new("Hi!".to_string()));
        let repeat = repeat.unwrap_or(false);
        let selfish = Greet(Arc::new(GreetStruct {}));
        println!("{message}");
        if repeat {
            println!("{message}");
        }
        selfish
    }
}

temper_core::impl_any_value_trait!(Greet, []);

static MESSAGE: RwLock<Option<Arc<String>>> = RwLock::new(None);

pub(crate) fn message() -> Arc<String> {
    crate::support::initialized(|| MESSAGE.read().unwrap().clone())
}

static REPEAT: RwLock<Option<bool>> = RwLock::new(None);

pub(crate) fn repeat() -> bool {
    crate::support::initialized(|| *REPEAT.read().unwrap())
}

pub(crate) fn change_repeat() -> Arc<String> {
    *REPEAT.write().unwrap() = Some(true);
    message()
}

pub(crate) fn change_message() -> bool {
    *MESSAGE.write().unwrap() = Some(Arc::new("Namaste!".to_string()));
    repeat()
}

struct PersonStruct {
    name: Arc<String>,
    age: i32,
}

#[derive(Clone)]
pub(crate) struct Person(Arc<PersonStruct>);

impl Person {
    pub fn new(name: Option<impl ToArcString>, age: Option<i32>) -> Person {
        let name = name.map(|x| x.to_arc_string());
        let name = name.unwrap_or_else(|| Arc::new("Alice".to_string()));
        let age = age.unwrap_or(40);
        Person(Arc::new(PersonStruct { name, age }))
    }

    pub fn to_string(&self) -> Arc<String> {
        Arc::new(format!(
            "{{ class: Person, name: {}, age: {} }}",
            self.0.name, self.0.age
        ))
    }

    pub fn greet(&self, greeting: Option<impl ToArcString>) {
        let greeting = greeting.map(|x| x.to_arc_string());
        let greeting = greeting.unwrap_or_else(|| Arc::new("Hi".to_string()));
        println!("{greeting}, {}", self.0.name);
    }
}

impl std::fmt::Display for Person {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(&self.to_string())
    }
}

temper_core::impl_any_value_trait!(Person, []);

struct CalcOptionsStruct {
    scale: i32,
    origin: i32,
}

#[derive(Clone)]
pub(crate) struct CalcOptions(Arc<CalcOptionsStruct>);

impl CalcOptions {
    pub fn new(scale: Option<i32>, origin: Option<i32>) -> CalcOptions {
        let scale = scale.unwrap_or(1);
        let origin = origin.unwrap_or(0);
        CalcOptions(Arc::new(CalcOptionsStruct { scale, origin }))
    }
}

temper_core::impl_any_value_trait!(CalcOptions, []);

pub(crate) fn calc<T: Clone + Send + Sync + 'static>(
    n: i32,
    transform: Arc<dyn Fn(i32) -> T + Send + Sync>,
    options: Option<CalcOptions>,
) -> T {
    let options = options.unwrap_or_else(|| CalcOptions::new(None, None));
    let origin = options.0.origin;
    let scale = options.0.scale;
    transform(n.wrapping_sub(origin).wrapping_mul(scale))
}

pub mod builders {
    use super::*;

    #[derive(Clone)]
    pub struct GreetBuilder {}

    #[derive(Clone)]
    pub struct GreetOptions {
        message: Option<Arc<String>>,
        repeat: Option<bool>,
    }

    impl GreetOptions {
        pub fn new(_selfish: GreetBuilder) -> Self {
            Self {
                message: None,
                repeat: None,
            }
        }

        pub fn message(mut self, message: Arc<String>) -> Self {
            self.message = Some(message);
            self
        }

        pub fn repeat(mut self, repeat: bool) -> Self {
            self.repeat = Some(repeat);
            self
        }

        pub fn build(self) -> Greet {
            Greet::new(self.message, self.repeat)
        }
    }

    impl GreetBuilder {
        pub fn build(self) -> Greet {
            self.options().build()
        }

        pub fn options(self) -> GreetOptions {
            GreetOptions::new(self)
        }
    }
}
