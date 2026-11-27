use crate::failure::{BrokenGenerator, Failure};
use std::sync::{Arc, RwLock};

/// What a step of a `Generator`'s body gives: its next value, `None` once
/// it's done, or the failure its body threw, any library's.
pub type Step<T> = std::result::Result<Option<T>, Arc<dyn Failure>>;

/// What a `Generator`'s `next()` gives: its next value, `None` once it's
/// done, or a `BrokenGenerator` whose cause is what its body threw.
pub type Next<T> = std::result::Result<Option<T>, BrokenGenerator>;

/// A generator whose body may throw, Temper's `Generator<T>`: each step
/// gives its next value, `None` once it's done, or what the body threw,
/// which leaves it done. A `SafeGenerator` becomes one by `From`.
pub trait GeneratorTrait<T>: Send + Sync {
    fn next_with(&self, selfish: &Generator<T>) -> Step<T>;
}

#[derive(Clone)]
pub struct Generator<T>(Arc<dyn GeneratorTrait<T>>);

impl<T> Generator<T>
where
    T: Clone + Send + Sync + 'static,
{
    /// A generator from a step function, which gets the generator.
    pub fn from_fn(
        generator_fn: Arc<dyn Fn(Generator<T>) -> Step<T> + Send + Sync>,
    ) -> Generator<T> {
        Generator::new(FnGenerator {
            done: RwLock::new(false),
            generator_fn,
        })
    }

    pub fn new(selfish: impl GeneratorTrait<T> + 'static) -> Generator<T> {
        Generator(Arc::new(selfish))
    }

    /// Runs the body to its next pause: its value, `None` once it's done,
    /// or a `BrokenGenerator` whose cause is what it threw.
    pub fn next(&self) -> Next<T> {
        self.0.next_with(self).map_err(|e| {
            let broken = BrokenGenerator::new();
            broken.set_cause(e);
            broken
        })
    }
}

struct FnGenerator<T> {
    done: RwLock<bool>,
    generator_fn: Arc<dyn Fn(Generator<T>) -> Step<T> + Send + Sync>,
}

impl<T> GeneratorTrait<T> for FnGenerator<T>
where
    T: Clone + Send + Sync + 'static,
{
    fn next_with(&self, selfish: &Generator<T>) -> Step<T> {
        if *self.done.read().unwrap() {
            return Ok(None);
        }
        let step = (self.generator_fn)(selfish.clone());
        if !matches!(step, Ok(Some(_))) {
            *self.done.write().unwrap() = true;
        }
        step
    }
}

impl<T> GeneratorTrait<T> for SafeGenerator<T>
where
    T: Clone + Send + Sync + 'static,
{
    fn next_with(&self, _: &Generator<T>) -> Step<T> {
        Ok(self.next_safe())
    }
}

impl<T> From<SafeGenerator<T>> for Generator<T>
where
    T: Clone + Send + Sync + 'static,
{
    fn from(safe: SafeGenerator<T>) -> Generator<T> {
        Generator::new(safe)
    }
}

pub trait SafeGeneratorTrait<T>: Send + Sync {
    fn next_with(&self, selfish: &SafeGenerator<T>) -> Option<T>;
    fn done(&self) -> bool;
    fn close(&self);
}

#[derive(Clone)]
pub struct SafeGenerator<T>(Arc<dyn SafeGeneratorTrait<T>>);

impl<T> SafeGenerator<T>
where
    T: Clone + Send + Sync + 'static,
{
    pub fn from_fn(
        generator_fn: std::sync::Arc<
            dyn Fn(SafeGenerator<T>) -> Option<T> + std::marker::Send + std::marker::Sync,
        >,
    ) -> SafeGenerator<T> {
        SafeGenerator::new(SafeGeneratorFnAdapter(RwLock::new(
            SafeGeneratorFnAdapterStruct {
                done: false,
                generator_fn: generator_fn.clone(),
            },
        )))
    }

    pub fn new(selfish: impl SafeGeneratorTrait<T> + 'static) -> SafeGenerator<T> {
        SafeGenerator(Arc::new(selfish))
    }

    pub fn next(&self) -> Next<T> {
        Ok(self.next_safe())
    }

    pub fn next_safe(&self) -> Option<T> {
        self.0.next_with(&self)
    }
}

/// A generator is an iterator, so a `for` loop steps it. Every clone shares
/// the one generator, so iterating a clone again continues where the last
/// left off.
impl<T> Iterator for SafeGenerator<T>
where
    T: Clone + Send + Sync + 'static,
{
    type Item = T;

    fn next(&mut self) -> Option<T> {
        self.next_safe()
    }
}

impl<T> std::ops::Deref for SafeGenerator<T> {
    type Target = dyn SafeGeneratorTrait<T>;
    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

struct SafeGeneratorFnAdapterStruct<T> {
    done: bool,
    generator_fn: std::sync::Arc<
        dyn Fn(SafeGenerator<T>) -> Option<T> + std::marker::Send + std::marker::Sync,
    >,
}

// Avoid Arc layer here because we only expose through interface trait.
struct SafeGeneratorFnAdapter<T>(RwLock<SafeGeneratorFnAdapterStruct<T>>);

impl<T> SafeGeneratorFnAdapter<T> {
    pub fn done(&self) -> bool {
        self.0.read().unwrap().done
    }

    pub fn generator_fn(
        &self,
    ) -> Arc<dyn Fn(SafeGenerator<T>) -> Option<T> + std::marker::Send + std::marker::Sync> {
        self.0.read().unwrap().generator_fn.clone()
    }

    pub fn close(&self) {
        self.0.write().unwrap().done = true;
    }
}

impl<T> SafeGeneratorTrait<T> for SafeGeneratorFnAdapter<T>
where
    T: Clone + 'static,
{
    fn next_with(&self, selfish: &SafeGenerator<T>) -> Option<T> {
        if self.done() {
            return None;
        }
        let result = (self.generator_fn())(selfish.clone());
        if result.is_none() {
            self.close();
        }
        result
    }

    fn done(&self) -> bool {
        self.done()
    }

    fn close(&self) {
        self.close();
    }
}
