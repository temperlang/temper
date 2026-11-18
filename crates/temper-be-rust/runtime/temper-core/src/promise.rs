use super::{Error, Result};
use crate::SafeGenerator;
use std::{
    collections::VecDeque,
    future::Future,
    ops::Deref,
    pin::Pin,
    sync::{Arc, Condvar, Mutex, RwLock},
    task::{Context, Poll, Waker},
};

pub trait AsyncRunnerTrait: Send + Sync {
    fn run_all_blocking(&self);

    fn run_async(&self, task: Task);

    /// Notes work outside any turn that will queue tasks later, such as
    /// std/net's I/O on a thread of its own, so `run_all_blocking` waits
    /// for it rather than returning. Each call is matched by one of
    /// [`release`](AsyncRunnerTrait::release), after that work's tasks
    /// are queued. A runner that never returns on its own may ignore it.
    fn hold(&self) {}

    /// Ends a [`hold`](AsyncRunnerTrait::hold).
    fn release(&self) {}
}

/// A [`AsyncRunner::hold`], released when dropped.
pub struct Hold(AsyncRunner);

impl Drop for Hold {
    fn drop(&mut self) {
        self.0 .0.release();
    }
}

thread_local! {
    /// Whether this thread is running an async step, a turn.
    static IN_TURN: std::cell::Cell<bool> = const { std::cell::Cell::new(false) };
}

/// Whether this thread is running an async step. A runner's tasks mark
/// themselves, so blocking on a promise there fails rather than hanging.
pub fn in_turn() -> bool {
    IN_TURN.with(|t| t.get())
}

/// Runs `step` as a turn, marked so for [`in_turn`].
fn turn(step: impl FnOnce()) {
    struct Unmark(bool);
    impl Drop for Unmark {
        fn drop(&mut self) {
            IN_TURN.with(|t| t.set(self.0));
        }
    }
    let _unmark = Unmark(IN_TURN.with(|t| t.replace(true)));
    step();
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
            turn(|| {
                let _ = gen.next_safe();
            })
        });
        self.0.run_async(gen_ignoring_result);
    }

    /// Keeps `run_all_blocking` waiting, while the result lives, for
    /// work outside any turn that will queue tasks later, as std/net's
    /// requests on their own threads do.
    pub fn hold(&self) -> Hold {
        self.0.hold();
        Hold(self.clone())
    }

    /// Queues `generator`'s next step on this runner once `promise` is
    /// resolved, so the call that resolves it returns before the step runs,
    /// as in the interpreter and JS, and a promise resolved on another
    /// thread resumes its waiter on the runner, never beside another step.
    pub fn awake_upon<T, U>(&self, promise: &Promise<T>, generator: SafeGenerator<U>)
    where
        T: Clone,
        U: Clone + Send + Sync + 'static,
    {
        let runner = self.clone();
        promise.on_ready(Arc::new(move || {
            let generator = generator.clone();
            runner.0.run_async(Arc::new(move || {
                turn(|| {
                    let _ = generator.next_safe();
                })
            }));
        }));
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

/// Runs its tasks one at a time, in order, on whichever thread calls
/// `run_all_blocking`. If several threads call it at once, its turn lock
/// keeps their tasks from overlapping. Generated code reaches it only
/// through [`AsyncRunner`] (`run_async` and `awake_upon`), never its queue,
/// so a runner with one queue per isolate or actor, many on a thread pool,
/// could replace it; this is the one-isolate case.
///
/// Its third part counts the holds ([`AsyncRunner::hold`]); while there
/// are any, `run_all_blocking` waits for more tasks rather than returning.
pub struct SingleThreadAsyncRunner(
    Arc<RwLock<SingleThreadAsyncRunnerStruct>>,
    Arc<Mutex<()>>,
    Arc<(Mutex<usize>, Condvar)>,
);

impl SingleThreadAsyncRunner {
    pub fn new() -> AsyncRunner {
        AsyncRunner::new(SingleThreadAsyncRunner(
            Arc::new(RwLock::new(SingleThreadAsyncRunnerStruct {
                tasks: VecDeque::new(),
            })),
            Arc::new(Mutex::new(())),
            Arc::new((Mutex::new(0), Condvar::new())),
        ))
    }

    /// Wakes `run_all_blocking` if it's waiting for a hold, after taking
    /// the holds' lock, so it can't miss this between checking and waiting.
    fn wake(&self) {
        let (holds, cvar) = &*self.2;
        let _holds = holds.lock().unwrap_or_else(|e| e.into_inner());
        cvar.notify_all();
    }
}

impl AsyncRunnerTrait for SingleThreadAsyncRunner {
    fn run_all_blocking(&self) {
        assert!(
            !in_turn(),
            "run_all_blocking inside an async turn would deadlock"
        );
        loop {
            let _turn = self.1.lock().unwrap_or_else(|e| e.into_inner());
            let task = {
                let mut lock = self.0.write().unwrap();
                lock.tasks.pop_front()
            };
            let Some(task) = task else {
                drop(_turn);
                // Done, unless held: then wait for a task or the last hold's
                // end, checking for tasks under the holds' lock, which
                // `run_async` takes to wake this.
                let (holds, cvar) = &*self.2;
                let held = holds.lock().unwrap_or_else(|e| e.into_inner());
                if *held == 0 {
                    break;
                }
                if self.0.read().unwrap().tasks.is_empty() {
                    drop(cvar.wait(held).unwrap_or_else(|e| e.into_inner()));
                }
                continue;
            };
            task();
        }
    }

    fn run_async(&self, task: Task) {
        self.0.write().unwrap().tasks.push_back(task);
        self.wake();
    }

    fn hold(&self) {
        *self.2 .0.lock().unwrap_or_else(|e| e.into_inner()) += 1;
    }

    fn release(&self) {
        let (holds, cvar) = &*self.2;
        let mut held = holds.lock().unwrap_or_else(|e| e.into_inner());
        *held = held.saturating_sub(1);
        cvar.notify_all();
    }
}

#[derive(Clone)]
pub struct Promise<T>
where
    T: Clone,
{
    // The result and the waiters share one mutex, so a waiter added while
    // another thread resolves the promise is either kept or sees the result.
    state: StatePair<T>,
}

impl<T> Promise<T>
where
    T: Clone,
{
    /// The result, waiting for it if need be. Waiting inside an async turn
    /// for a promise not yet resolved panics rather than deadlocking, since
    /// what would resolve it may need the turn.
    pub fn get(&self) -> Result<T> {
        let (lock, cvar) = &*self.state;
        let mut state = lock.lock().unwrap();
        if state.result.is_none() && in_turn() {
            // Unlocked first, so the promise isn't poisoned.
            drop(state);
            panic!(
                "blocking on an unresolved Temper promise inside an async turn would deadlock; await it instead"
            );
        }
        while state.result.is_none() {
            state = cvar.wait(state).unwrap();
        }
        state.result.clone().unwrap()
    }

    /// Runs `next` once this promise is resolved: now if it already is,
    /// otherwise inside the call that resolves it, after the waiters added
    /// before it.
    pub fn on_ready(&self, next: Task) {
        {
            let mut state = self.state.0.lock().unwrap();
            if state.result.is_none() {
                state.waiters.push(next);
                return;
            }
        }
        next();
    }
}

/// A promise is a `Future` of its result, so Rust code can `.await` it on
/// any executor. It adds no bounds of its own: it's `Send` exactly when
/// its value type is, and `Unpin`.
impl<T> Future for Promise<T>
where
    T: Clone,
{
    type Output = Result<T>;

    fn poll(self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Result<T>> {
        let mut state = self.state.0.lock().unwrap();
        if let Some(result) = &state.result {
            return Poll::Ready(result.clone());
        }
        // One waker per task: a later poll's replaces its earlier one's.
        let waker = cx.waker();
        if !state.wakers.iter().any(|w| w.will_wake(waker)) {
            state.wakers.push(waker.clone());
        }
        Poll::Pending
    }
}

#[derive(Clone)]
pub struct PromiseBuilder<T>
where
    T: Clone,
{
    promise: Promise<T>,
}

impl<T> PromiseBuilder<T>
where
    T: Clone,
{
    pub fn new() -> Self {
        Self {
            promise: Promise {
                state: Arc::new((
                    Mutex::new(PromiseState {
                        result: None,
                        waiters: Vec::new(),
                        wakers: Vec::new(),
                    }),
                    Condvar::new(),
                )),
            },
        }
    }

    pub fn break_promise(&self) {
        self.resolve(Err(Error::new()));
    }

    pub fn complete(&self, value: T) {
        self.resolve(Ok(value));
    }

    pub fn promise(&self) -> Promise<T> {
        self.promise.clone()
    }

    /// The first resolution sticks. Later ones change nothing and wake no one.
    fn resolve(&self, result: Result<T>) {
        let (lock, cvar) = &*self.promise.state;
        let (waiters, wakers) = {
            let mut state = lock.lock().unwrap();
            if state.result.is_some() {
                return;
            }
            state.result = Some(result);
            (
                std::mem::take(&mut state.waiters),
                std::mem::take(&mut state.wakers),
            )
        };
        cvar.notify_all();
        for waker in wakers {
            waker.wake();
        }
        // Outside the lock, so a waiter may await this promise again.
        for next in waiters {
            next();
        }
    }
}

struct PromiseState<T> {
    result: Option<Result<T>>,
    waiters: Vec<Task>,
    /// The tasks of Rust code that `.await`s it ([`Future`]).
    wakers: Vec<Waker>,
}

type StatePair<T> = Arc<(Mutex<PromiseState<T>>, Condvar)>;

pub type Task = std::sync::Arc<dyn Fn() + Send + Sync>;

#[cfg(test)]
mod tests {
    use super::*;

    fn log_task(log: &Arc<Mutex<Vec<String>>>, line: &str) -> Task {
        let log = log.clone();
        let line = line.to_string();
        Arc::new(move || log.lock().unwrap().push(line.clone()))
    }

    fn lines(log: &Arc<Mutex<Vec<String>>>) -> Vec<String> {
        log.lock().unwrap().clone()
    }

    #[test]
    fn every_waiter_resumes_on_complete() {
        let log = Arc::new(Mutex::new(Vec::new()));
        let pb = PromiseBuilder::<i32>::new();
        pb.promise().on_ready(log_task(&log, "first"));
        pb.promise().on_ready(log_task(&log, "second"));
        pb.complete(7);
        assert_eq!(lines(&log), ["first", "second"]);
        assert_eq!(pb.promise().get().ok(), Some(7));
    }

    #[test]
    fn every_waiter_resumes_on_break_promise() {
        let log = Arc::new(Mutex::new(Vec::new()));
        let pb = PromiseBuilder::<i32>::new();
        pb.promise().on_ready(log_task(&log, "first"));
        pb.promise().on_ready(log_task(&log, "second"));
        pb.break_promise();
        assert_eq!(lines(&log), ["first", "second"]);
        assert!(pb.promise().get().is_err());
    }

    #[test]
    fn on_ready_after_resolution_runs_now() {
        let log = Arc::new(Mutex::new(Vec::new()));
        let pb = PromiseBuilder::<i32>::new();
        pb.complete(1);
        pb.promise().on_ready(log_task(&log, "late"));
        assert_eq!(lines(&log), ["late"]);
    }

    #[test]
    fn first_resolution_sticks_and_wakes_waiters_once() {
        let log = Arc::new(Mutex::new(Vec::new()));
        let pb = PromiseBuilder::<i32>::new();
        pb.promise().on_ready(log_task(&log, "woken"));
        pb.complete(1);
        pb.complete(2);
        pb.break_promise();
        assert_eq!(lines(&log), ["woken"]);
        assert_eq!(pb.promise().get().ok(), Some(1));
    }

    #[test]
    fn get_blocks_until_another_thread_completes() {
        let pb = PromiseBuilder::<i32>::new();
        let promise = pb.promise();
        let waiter = std::thread::spawn(move || promise.get().ok());
        std::thread::sleep(std::time::Duration::from_millis(20));
        pb.complete(5);
        assert_eq!(waiter.join().unwrap(), Some(5));
    }

    /// Runs a future to its end on this thread, with nothing but std.
    fn block_on<F: Future>(future: F) -> F::Output {
        struct Unpark(std::thread::Thread);
        impl std::task::Wake for Unpark {
            fn wake(self: Arc<Self>) {
                self.0.unpark();
            }
        }
        let waker = Waker::from(Arc::new(Unpark(std::thread::current())));
        let mut cx = Context::from_waker(&waker);
        let mut future = std::pin::pin!(future);
        loop {
            if let Poll::Ready(output) = future.as_mut().poll(&mut cx) {
                return output;
            }
            std::thread::park();
        }
    }

    fn assert_send_static<F: Future + Send + 'static>(_: &F) {}

    #[test]
    fn awake_upon_queues_the_step_after_the_resolving_call() {
        let log = Arc::new(Mutex::new(Vec::new()));
        let runner = SingleThreadAsyncRunner::new();
        let pb = PromiseBuilder::<i32>::new();
        let step = {
            let log = log.clone();
            SafeGenerator::<()>::from_fn(Arc::new(move |_| {
                log.lock()
                    .unwrap()
                    .push(format!("step in turn {}", in_turn()));
                None
            }))
        };
        runner.awake_upon(&pb.promise(), step);
        pb.complete(1);
        log.lock().unwrap().push("complete returned".into());
        runner.run_all_blocking();
        assert_eq!(lines(&log), ["complete returned", "step in turn true"]);
        assert!(!in_turn());
    }

    #[test]
    fn a_hold_keeps_run_all_blocking_waiting() {
        let log = Arc::new(Mutex::new(Vec::new()));
        let runner = SingleThreadAsyncRunner::new();
        let pb = PromiseBuilder::<i32>::new();
        let step = {
            let log = log.clone();
            SafeGenerator::<()>::from_fn(Arc::new(move |_| {
                log.lock().unwrap().push("resumed".to_string());
                None
            }))
        };
        runner.awake_upon(&pb.promise(), step);
        // As std/net's I/O: settled on another thread, outside any turn,
        // which queues the step, then its hold ends.
        let hold = runner.hold();
        let io = std::thread::spawn(move || {
            std::thread::sleep(std::time::Duration::from_millis(50));
            pb.complete(1);
            drop(hold);
        });
        runner.run_all_blocking();
        assert_eq!(lines(&log), ["resumed"]);
        io.join().unwrap();
    }

    #[test]
    fn get_inside_a_turn_panics_rather_than_deadlocking() {
        let runner = SingleThreadAsyncRunner::new();
        let pb = PromiseBuilder::<i32>::new();
        let promise = pb.promise();
        runner.run_async(Arc::new(move || {
            let promise = promise.clone();
            SafeGenerator::<()>::from_fn(Arc::new(move |_| {
                let _ = promise.get();
                None
            }))
        }));
        let panicked = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            runner.run_all_blocking();
        }));
        let message = panicked.unwrap_err();
        let message = message.downcast_ref::<&str>().unwrap();
        assert!(message.contains("would deadlock"), "{message}");
        // Outside a turn, it waits as before.
        pb.complete(2);
        assert_eq!(pb.promise().get().ok(), Some(2));
    }

    #[test]
    fn a_generator_is_an_iterator() {
        let count = Arc::new(Mutex::new(0));
        let generator = SafeGenerator::from_fn(Arc::new(move |_| {
            let mut n = count.lock().unwrap();
            *n += 1;
            (*n <= 3).then_some(*n)
        }));
        let mut seen = Vec::new();
        for n in generator.clone() {
            seen.push(n);
        }
        assert_eq!(seen, [1, 2, 3]);
        // Its inherent `next` gives a `Result`; `Iterator::next` an `Option`.
        assert_eq!(Iterator::next(&mut generator.clone()), None);
    }

    #[test]
    fn a_promise_is_a_future_that_any_executor_may_await() {
        let pb = PromiseBuilder::<i32>::new();
        let promise = pb.promise();
        assert_send_static(&promise);
        let awaited = async move { promise.await.ok() };
        assert_send_static(&awaited);
        let completer = std::thread::spawn(move || {
            std::thread::sleep(std::time::Duration::from_millis(20));
            pb.complete(5);
        });
        assert_eq!(block_on(awaited), Some(5));
        completer.join().unwrap();
        let broken = PromiseBuilder::<i32>::new();
        broken.break_promise();
        assert!(block_on(broken.promise()).is_err());
    }

    #[test]
    fn a_promise_of_a_value_that_is_not_send_is_awaitable_on_this_thread() {
        let pb = PromiseBuilder::<std::rc::Rc<i32>>::new();
        let promise = pb.promise();
        pb.complete(std::rc::Rc::new(3));
        assert_eq!(block_on(async { *promise.await.ok().unwrap() }), 3);
    }

    #[test]
    fn promise_and_builder_are_send_and_sync() {
        fn check<X: Send + Sync>() {}
        check::<Promise<i32>>();
        check::<PromiseBuilder<i32>>();
    }
}
