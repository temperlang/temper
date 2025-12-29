use std::sync::{Arc, RwLock};

// pub trait GeneratorTrait<T> {
//     //
// }

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

    pub fn next(&self) -> Option<T> {
        self.0.next_with(&self)
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
