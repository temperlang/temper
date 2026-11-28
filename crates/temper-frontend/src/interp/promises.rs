//! Promises and `async` blocks, as Kotlin Temper's interpreter runs them
//! (`Promises.kt`, `AsyncFn.kt`). `async { … }` makes its block's generator
//! and queues it; queued blocks run after the code that's running, a
//! module's top level, ends. A block runs until it ends or pauses at an
//! `await` of a promise that isn't resolved yet; resolving the promise
//! queues every block awaiting it, in the order they began to wait. A
//! connected function's work, such as `sendRequest`'s, can be queued too,
//! as a job that resolves its promise when the queue reaches it.

use super::{Abrupt, CoreDeclaredError, Generator, Interp, R, Val, fail, type_name};
use crate::settle::Settled;
use std::cell::RefCell;
use std::rc::Rc;

/// A promise, shared by its `PromiseBuilder` and whoever awaits it.
#[derive(Debug, Default)]
pub struct Promise {
    state: RefCell<State>,
}

#[derive(Debug)]
enum State {
    /// Not resolved yet, with the `async` blocks paused awaiting it.
    Pending(Vec<Rc<Generator>>),
    Complete(Val),
    /// Broken: `await` throws a `BrokenPromise`, which doesn't say why.
    Broken,
}

/// Work in the run queue.
pub(super) enum Task {
    /// An `async` block to run on from where it paused.
    Block(Rc<Generator>),
    /// Native work whose result resolves a promise: a value completes it,
    /// and an error breaks it.
    Job(Rc<Promise>, Box<dyn FnOnce() -> Result<Val, String>>),
}

impl Default for State {
    fn default() -> State {
        State::Pending(Vec::new())
    }
}

impl Promise {
    /// A promise already completed with a value.
    pub(super) fn completed(value: Val) -> Promise {
        Promise {
            state: RefCell::new(State::Complete(value)),
        }
    }

    /// Adds a paused block to those awaiting the promise, which must be
    /// pending.
    pub(super) fn park(&self, block: Rc<Generator>) {
        if let State::Pending(waiters) = &mut *self.state.borrow_mut() {
            waiters.push(block);
        }
    }

    /// What `await` gives, once the promise is resolved: its value, or
    /// for a broken one, a new `BrokenPromise` thrown.
    pub(super) fn outcome(&self) -> Option<R> {
        match &*self.state.borrow() {
            State::Pending(_) => None,
            State::Complete(value) => Some(Ok(value.clone())),
            State::Broken => Some(Err(Abrupt::Throw(CoreDeclaredError::value(
                "BrokenPromise",
                Vec::new(),
            )))),
        }
    }
}

impl Interp<'_> {
    /// `async { … }`: makes the block's generator, which runs only through
    /// its parameters, and queues it.
    pub(super) fn start_async(&mut self, s: &Settled, args: Vec<Val>) -> R {
        let [block] =
            <[Val; 1]>::try_from(args).map_err(|_| fail("async takes a generator block"))?;
        match self.apply(s, block, Vec::new())? {
            Val::Generator(ref generator) => {
                self.ready.push_back(Task::Block(generator.clone()));
                Ok(Val::Void)
            }
            other => Err(fail(format!(
                "Expected a SafeGenerator, not {}",
                type_name(&other)
            ))),
        }
    }

    /// Queues native work, which resolves `promise` when the queue
    /// reaches it.
    pub(super) fn queue_job(
        &mut self,
        promise: Rc<Promise>,
        job: impl FnOnce() -> Result<Val, String> + 'static,
    ) {
        // Native work never runs at compile time; what waits on it waits
        // for good, so the code using it doesn't finish.
        if self.compile_time {
            return;
        }
        self.ready.push_back(Task::Job(promise, Box::new(job)));
    }

    /// Runs queued tasks until none are ready. A block that fails doesn't
    /// stop the others; the first error is the result. A `yield` pauses
    /// a block for good, as Kotlin Temper's stepper ignores what `next()`
    /// gives.
    pub(super) fn run_tasks(&mut self, s: &Settled) -> Result<(), Abrupt> {
        let mut result = Ok(());
        while let Some(task) = self.ready.pop_front() {
            let task = match task {
                Task::Block(block) => block,
                Task::Job(promise, job) => {
                    let resolution = match job() {
                        Ok(value) => State::Complete(value),
                        Err(_) => State::Broken,
                    };
                    self.resolve(&promise, resolution);
                    continue;
                }
            };
            let done = match self.step(s, &task) {
                Err(Abrupt::Await(promise)) => {
                    match &mut *promise.state.borrow_mut() {
                        State::Pending(waiters) => waiters.push(task),
                        _ => unreachable!("a block pauses only on a pending promise"),
                    }
                    continue;
                }
                done => done,
            };
            // A REPL input resumed: its error is its own.
            let parked = (self.inputs_parked.iter()).position(|(g, _)| Rc::ptr_eq(g, &task));
            let input = parked.map(|i| self.inputs_parked.swap_remove(i).1);
            match (done, input) {
                (Ok(_) | Err(Abrupt::Yield(_)), _) => {}
                (Err(Abrupt::Panic(why)), _) if why == super::INTERRUPTED => {
                    result = result.and(Err(Abrupt::Panic(why)))
                }
                (Err(abrupt), Some(input)) => self.inputs_failed.push((input, abrupt)),
                (Err(abrupt), None) => result = result.and(Err(abrupt)),
            }
        }
        result
    }

    /// `PromiseBuilder`'s methods. The first resolution wins; later ones
    /// do nothing, as in Kotlin Temper.
    pub(super) fn promise_builder_method(
        &mut self,
        promise: &Promise,
        name: &str,
        args: &[Val],
    ) -> Option<R> {
        let resolution = match (name, args) {
            ("complete", [value]) => State::Complete(value.clone()),
            ("breakPromise", []) => State::Broken,
            _ => return None,
        };
        self.resolve(promise, resolution);
        Some(Ok(Val::Void))
    }

    /// Resolves a promise that's still pending, queuing its waiters.
    fn resolve(&mut self, promise: &Promise, resolution: State) {
        let mut state = promise.state.borrow_mut();
        if let State::Pending(waiters) = &mut *state {
            self.ready.extend(waiters.drain(..).map(Task::Block));
            *state = resolution;
        }
    }
}
