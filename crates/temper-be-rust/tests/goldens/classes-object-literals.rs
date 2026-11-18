use std::sync::{Arc, OnceLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let person1 = Person::new("Alice", Some(50));
            let person2 = Person::new("Bob", Some(45));
            let circle1 = crate::extra_class::Circle::new(2);
            let circle2 = crate::extra_class::Circle::new(1);
            let things: temper_core::List<crate::extra_class::Stringable> = Arc::new(vec![
                crate::extra_class::Stringable::new(person1.clone()),
                crate::extra_class::Stringable::new(person2.clone()),
                crate::extra_class::Stringable::new(circle1.clone()),
                crate::extra_class::Stringable::new(circle2.clone()),
            ]);
            temper_core::listed::map(
                &things,
                &(|thing: crate::extra_class::Stringable| {
                    println!("{}", crate::extra_class::StringableTrait::to_string(&thing));
                    0
                }),
            );
            Whatever::new(456);
            Whatever::new(321);
            crate::extra_class::Rectangle::new(7, 8);
            bother();
            let whatever = Whatever::new(6);
            println!("Whatever again: {}", whatever.0.blah);
            let person_again = Person::new("Carrie", Some(40));
            let name = person_again.0.name.clone();
            let some_age = person_again.0.age;
            println!("name is {name} and age is {some_age}");
            let person_more = Person::new(name.clone(), Some(35));
            println!("{person_more}");
            let twin = Person::new(person1.0.name.clone(), Some(person1.0.age));
            println!("Some are twins: {}", person1 == twin);
            println!("All are twins: {}", person2 == twin);
            Ok(())
        })
        .clone()
}

struct PersonStruct {
    name: Arc<String>,
    age: i32,
}

#[derive(Clone)]
pub struct Person(Arc<PersonStruct>);

impl Person {
    pub fn new(name: impl ToArcString, age: Option<i32>) -> Person {
        let name = name.to_arc_string();
        let age = age.unwrap_or(-100);
        Person(Arc::new(PersonStruct { name, age }))
    }

    pub fn to_string(&self) -> Arc<String> {
        let age_text = temper_core::listed::join(&Arc::new(vec![0]), Arc::new(String::new()), &*{
            let this = self.clone();
            Arc::new(move |_unused: i32| Arc::new(this.0.age.to_string()))
        });
        Arc::new(format!("Person {} of age {age_text}", self.0.name))
    }

    #[allow(clippy::should_implement_trait)]
    pub fn eq(&self, other: Person) -> bool {
        if self.0.name == other.0.name {
            self.0.age == other.0.age
        } else {
            false
        }
    }

    pub fn name(&self) -> Arc<String> {
        self.0.name.clone()
    }

    pub fn age(&self) -> i32 {
        self.0.age
    }
}

impl std::fmt::Display for Person {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(&self.to_string())
    }
}

impl PartialEq for Person {
    fn eq(&self, other: &Person) -> bool {
        Person::eq(self, other.clone())
    }
}

impl crate::extra_class::StringableTrait for Person {
    fn clone_boxed(&self) -> crate::extra_class::Stringable {
        crate::extra_class::Stringable::new(self.clone())
    }

    fn to_string(&self) -> Arc<String> {
        self.to_string()
    }
}

temper_core::impl_any_value_trait!(Person, [crate::extra_class::Stringable]);

struct WhateverStruct {
    blah: Arc<String>,
}

#[derive(Clone)]
pub(crate) struct Whatever(Arc<WhateverStruct>);

impl Whatever {
    pub fn new(blech: i32) -> Whatever {
        Whatever(Arc::new(WhateverStruct {
            blah: Arc::new(blech.to_string()),
        }))
    }
}

temper_core::impl_any_value_trait!(Whatever, []);

struct MoreoverStruct {
    blech: i32,
    bling: i32,
}

#[derive(Clone)]
pub(crate) struct Moreover(Arc<MoreoverStruct>);

impl Moreover {
    pub fn new(blech: Option<i32>, bling: Option<i32>) -> Moreover {
        let blech = blech.unwrap_or(0);
        let bling = bling.unwrap_or(0);
        Moreover(Arc::new(MoreoverStruct { blech, bling }))
    }

    pub fn to_string(&self) -> Arc<String> {
        Arc::new(format!("{} {}", self.0.blech, self.0.bling))
    }
}

impl std::fmt::Display for Moreover {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(&self.to_string())
    }
}

temper_core::impl_any_value_trait!(Moreover, []);

pub(crate) fn bother() {
    let _what_i_am = Moreover::new(Some(1), None);
    let moreover = Moreover::new(Some(2), Some(3));
    println!("Moreover: {moreover}");
    let moreover2 = Moreover::new(Some(4), None);
    println!("Moreover again: {moreover2}");
    let moreover3 = Moreover::new(None, Some(7));
    println!("Moreover yet again: {moreover3}");
    let whatever = Whatever::new(5);
    println!("Whatever: {}", whatever.0.blah);
}

pub mod builders {
    use super::*;

    #[derive(Clone)]
    pub struct PersonBuilder {
        pub name: Arc<String>,
    }

    #[derive(Clone)]
    pub struct PersonOptions {
        selfish: PersonBuilder,
        age: Option<i32>,
    }

    impl PersonOptions {
        pub fn new(selfish: PersonBuilder) -> Self {
            Self { selfish, age: None }
        }

        pub fn age(mut self, age: i32) -> Self {
            self.age = Some(age);
            self
        }

        pub fn build(self) -> Person {
            Person::new(self.selfish.name, self.age)
        }
    }

    impl PersonBuilder {
        pub fn build(self) -> Person {
            self.options().build()
        }

        pub fn options(self) -> PersonOptions {
            PersonOptions::new(self)
        }
    }
}
