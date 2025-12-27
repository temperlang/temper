use crate::SafeGenerator;
use std::{
    collections::VecDeque,
    ops::Deref,
    sync::{Arc, Condvar, Mutex, RwLock},
};

pub trait AsyncRunnerTrait: Send + Sync {
    fn run_all_blocking(&self);

    fn run_async(&self, task: Task);
}

#[derive(Clone)]
pub struct AsyncRunner(Arc<dyn AsyncRunnerTrait>);

impl AsyncRunner {
    pub fn new(selfish: impl AsyncRunnerTrait + 'static) -> Self {
        Self(Arc::new(selfish))
    }

    pub fn run_async<T>(&self, gen: std::sync::Arc<dyn Fn() -> SafeGenerator<T> + Send + Sync>)
    where
        T: Clone + Send + Sync + 'static,
    {
        let gen = gen();
        // TODO Any way to avoid the extra Arc wrapping?
        let gen_ignoring_result = Arc::new(move || {
            let _ = gen.next();
        });
        self.0.run_async(gen_ignoring_result);
    }
}

impl Deref for AsyncRunner {
    type Target = dyn AsyncRunnerTrait;
    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

struct SingleThreadAsyncRunnerStruct {
    tasks: VecDeque<Task>,
}

pub struct SingleThreadAsyncRunner(Arc<RwLock<SingleThreadAsyncRunnerStruct>>);

impl SingleThreadAsyncRunner {
    pub fn new() -> AsyncRunner {
        AsyncRunner::new(SingleThreadAsyncRunner(Arc::new(RwLock::new(
            SingleThreadAsyncRunnerStruct {
                tasks: VecDeque::new(),
            },
        ))))
    }
}

impl AsyncRunnerTrait for SingleThreadAsyncRunner {
    fn run_all_blocking(&self) {
        loop {
            let task = {
                let mut lock = self.0.write().unwrap();
                lock.tasks.pop_front()
            };
            let Some(task) = task else {
                break;
            };
            task();
        }
    }

    fn run_async(&self, task: Task) {
        let mut lock = self.0.write().unwrap();
        lock.tasks.push_back(task);
    }
}

#[derive(Clone)]
pub struct Promise<T>
where
    T: Clone,
{
    // Provide only one bonus listener for now. TODO Do we need more?
    // TODO Just take a Generator directly instead of a function?
    next: Arc<RwLock<Option<Task>>>,
    wait: WaitPair<T>,
}

impl<T> Promise<T>
where
    T: Clone,
{
    pub fn get(&self) -> Result<T, ()> {
        let (lock, cvar) = &*self.wait;
        let mut result = lock.lock().unwrap();
        while result.is_none() {
            result = cvar.wait(result).unwrap();
        }
        result.clone().unwrap()
    }

    fn next(&self) {
        let next = {
            let mut next = self.next.write().unwrap();
            next.take()
        };
        if let Some(next) = next {
            next();
        }
    }

    pub fn on_ready(&self, next: Task) {
        let result = {
            let (lock, _) = &*self.wait;
            lock.lock().unwrap().clone()
        };
        match result {
            Some(_) => next(),
            None => {
                *self.next.write().unwrap() = Some(next);
            }
        }
    }
}

#[derive(Clone)]
pub struct PromiseBuilder<T>
where
    T: Clone,
{
    promise: Promise<T>,
    wait: WaitPair<T>,
}

impl<T> PromiseBuilder<T>
where
    T: Clone,
{
    pub fn new() -> Self {
        let wait = Arc::new((Mutex::new(None), Condvar::new()));
        Self {
            promise: Promise {
                next: Arc::new(RwLock::new(None)),
                wait: wait.clone(),
            },
            wait,
        }
    }

    pub fn break_promise(&self) {
        let (lock, cvar) = &*self.wait;
        {
            let mut result = lock.lock().unwrap();
            *result = Some(Err(()));
        }
        self.promise().next();
        cvar.notify_all();
    }

    pub fn complete(&self, value: T) {
        let (lock, cvar) = &*self.wait;
        {
            let mut result = lock.lock().unwrap();
            // TODO Still notify below if previously set?
            if result.is_none() {
                *result = Some(Ok(value));
            }
        }
        self.promise().next();
        cvar.notify_all();
    }

    pub fn promise(&self) -> Promise<T> {
        self.promise.clone()
    }
}

pub type Task = std::sync::Arc<dyn Fn() + Send + Sync>;

type WaitPair<T> = Arc<(Mutex<Option<Result<T, ()>>>, Condvar)>;
