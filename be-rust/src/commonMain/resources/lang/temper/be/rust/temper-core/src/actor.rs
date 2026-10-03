//! Turns for instances of `@actor` classes.
//!
//! Each actor instance owns a [`Gate`]. Every method, getter and setter of
//! the class starts with `let _turn = self.1.enter();`, so calls into one
//! instance from several threads run one at a time. The [`Turn`] it returns
//! releases the gate when dropped, which happens on return, on `?` (a
//! bubble) and while unwinding from a panic, so the actor stays usable
//! after each of them.
//!
//! Each thread keeps the chain of actors it is currently inside, innermost
//! last. A call into any actor already on the chain runs inline: `this.m()`,
//! a callback the actor invoked calling back in, or A calling B calling
//! back into A. The thread already holds that actor's turn.
//!
//! A thread that finds a gate held by another thread records that it waits
//! on that gate, then follows the owner of the gate to the gate the owner
//! waits on, and so on. If that walk comes back to this thread, waiting
//! would never end, so it panics with "actor call cycle" instead. The walk
//! only happens on contention, so an uncontended call never takes the
//! global table's lock.
//!
//! Async steps started during a turn belong to the actor whose turn it was:
//! [`current`] is read when a step is queued, and the step enters that
//! actor's gate when it runs.

use std::cell::RefCell;
use std::marker::PhantomData;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Condvar, Mutex, MutexGuard, PoisonError};

static NEXT_THREAD: AtomicU64 = AtomicU64::new(1);

/// Which gate each blocked thread waits on, by thread number.
static WAITING: Mutex<Vec<(u64, Gate)>> = Mutex::new(Vec::new());

thread_local! {
    static CHAIN: RefCell<Vec<Gate>> = const { RefCell::new(Vec::new()) };
    static THREAD: u64 = NEXT_THREAD.fetch_add(1, Ordering::Relaxed);
}

/// One per actor instance. Cloning shares the same gate.
#[derive(Clone)]
pub struct Gate(Arc<GateState>);

struct GateState {
    /// The Temper class name, for the cycle panic message.
    class: &'static str,
    /// The number of the thread whose turn it is, or 0. Written only while
    /// holding `lock`, read without it by the cycle walk.
    owner: AtomicU64,
    lock: Mutex<()>,
    freed: Condvar,
}

/// Proof of being inside an actor's turn. Not `Send`, because the chain it
/// pops on drop belongs to the thread that entered.
#[must_use]
pub struct Turn {
    kind: TurnKind,
    _thread_bound: PhantomData<*const ()>,
}

enum TurnKind {
    /// Holds nothing.
    Inline,
    /// Re-entered an actor already on the chain. Pops the chain on drop, so
    /// [`current`] names the actor whose code is running.
    Nested,
    /// Took the gate. Pops the chain and frees the gate on drop.
    Holding,
}

/// Neither lock is held while user code runs, but tolerate poison anyway so
/// that nothing about a panic can wedge an actor.
fn lock<T>(mutex: &Mutex<T>) -> MutexGuard<'_, T> {
    mutex.lock().unwrap_or_else(PoisonError::into_inner)
}

fn this_thread() -> u64 {
    THREAD.with(|thread| *thread)
}

/// Records that `me` waits on `gate`, and says whether that closes a cycle.
fn start_waiting(me: u64, gate: &Gate) -> bool {
    let mut waiting = lock(&WAITING);
    waiting.push((me, gate.clone()));
    let mut at = gate.clone();
    // Each step moves to another waiting thread, so a walk longer than the
    // table is going round a cycle that does not include `me`.
    for _ in 0..waiting.len() {
        let owner = at.0.owner.load(Ordering::SeqCst);
        if owner == me {
            return true;
        }
        match waiting.iter().find(|(thread, _)| *thread == owner) {
            Some((_, next)) => at = next.clone(),
            None => return false,
        }
    }
    false
}

fn stop_waiting(me: u64) {
    lock(&WAITING).retain(|(thread, _)| *thread != me);
}

impl Gate {
    pub fn new(class: &'static str) -> Gate {
        Gate(Arc::new(GateState {
            class,
            owner: AtomicU64::new(0),
            lock: Mutex::new(()),
            freed: Condvar::new(),
        }))
    }

    /// Starts a turn, waiting while another thread has one.
    ///
    /// Panics with "actor call cycle" if the thread whose turn it is waits,
    /// directly or through other actors' turns, on this thread.
    pub fn enter(&self) -> Turn {
        let on_chain = CHAIN.with(|chain| {
            let mut chain = chain.borrow_mut();
            let on_chain = chain.iter().any(|gate| Arc::ptr_eq(&gate.0, &self.0));
            if on_chain {
                chain.push(self.clone());
            }
            on_chain
        });
        if on_chain {
            return Turn::with(TurnKind::Nested);
        }
        let me = this_thread();
        let mut held = lock(&self.0.lock);
        if self.0.owner.load(Ordering::SeqCst) != 0 {
            if start_waiting(me, self) {
                drop(held);
                stop_waiting(me);
                panic!("actor call cycle: {} is waiting on this call", self.0.class);
            }
            while self.0.owner.load(Ordering::SeqCst) != 0 {
                held = self
                    .0
                    .freed
                    .wait(held)
                    .unwrap_or_else(PoisonError::into_inner);
            }
            stop_waiting(me);
        }
        self.0.owner.store(me, Ordering::SeqCst);
        drop(held);
        CHAIN.with(|chain| chain.borrow_mut().push(self.clone()));
        Turn::with(TurnKind::Holding)
    }

    fn leave(&self) {
        let held = lock(&self.0.lock);
        self.0.owner.store(0, Ordering::SeqCst);
        drop(held);
        self.0.freed.notify_one();
    }
}

impl Turn {
    /// A turn that holds nothing, for code that may or may not be an actor's.
    pub fn inline() -> Turn {
        Turn::with(TurnKind::Inline)
    }

    fn with(kind: TurnKind) -> Turn {
        Turn {
            kind,
            _thread_bound: PhantomData,
        }
    }
}

impl Drop for Turn {
    fn drop(&mut self) {
        if let TurnKind::Inline = self.kind {
            return;
        }
        // Turns are locals, so they drop in the reverse order they were
        // entered, and the innermost gate on the chain is ours.
        let gate = CHAIN.with(|chain| chain.borrow_mut().pop());
        if let (TurnKind::Holding, Some(gate)) = (&self.kind, gate) {
            gate.leave();
        }
    }
}

/// The actor whose code this thread is running, if any.
pub fn current() -> Option<Gate> {
    CHAIN.with(|chain| chain.borrow().last().cloned())
}

/// Wraps `task` to run as a turn of the actor current now, if any.
pub fn belonging_to_current(task: crate::Task) -> crate::Task {
    match current() {
        None => task,
        Some(gate) => Arc::new(move || {
            let _turn = gate.enter();
            task()
        }),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::panic::AssertUnwindSafe;
    use std::sync::atomic::AtomicI32;
    use std::sync::mpsc;
    use std::time::Duration;

    /// A hand-written stand-in for what be-rust emits for an actor class.
    #[derive(Clone)]
    struct Counter(Arc<std::sync::RwLock<i32>>, Gate);

    impl Counter {
        fn new() -> Counter {
            Counter(Arc::new(std::sync::RwLock::new(0)), Gate::new("Counter"))
        }
        /// Read, spin, then write: loses updates unless calls take turns.
        fn bump(&self) {
            let _turn = self.1.enter();
            let n = *self.0.read().unwrap();
            let mut spin = 0i32;
            for j in 0..500 {
                spin = spin.wrapping_add(std::hint::black_box(j));
            }
            *self.0.write().unwrap() = n + 1 + spin.wrapping_sub(spin);
        }
        fn twice(&self) {
            let _turn = self.1.enter();
            self.bump();
            self.bump();
        }
        fn count(&self) -> i32 {
            let _turn = self.1.enter();
            let n = *self.0.read().unwrap();
            n
        }
        fn checked(&self, k: i32) -> Result<i32, ()> {
            let _turn = self.1.enter();
            if k < 0 {
                return Err(());
            }
            Ok(k)
        }
        fn boom(&self) {
            let _turn = self.1.enter();
            panic!("boom");
        }
        fn with_callback(&self, f: &dyn Fn()) {
            let _turn = self.1.enter();
            f();
        }
    }

    /// Fails the test instead of hanging it when `body` deadlocks.
    fn within<T: Send + 'static>(body: impl FnOnce() -> T + Send + 'static) -> T {
        let (send, receive) = mpsc::channel();
        std::thread::spawn(move || {
            let _ = send.send(body());
        });
        receive
            .recv_timeout(Duration::from_secs(10))
            .expect("did not finish in 10 seconds")
    }

    fn panic_text(result: std::thread::Result<()>) -> String {
        let payload = result.expect_err("expected a panic");
        if let Some(text) = payload.downcast_ref::<String>() {
            return text.clone();
        }
        payload.downcast_ref::<&str>().unwrap().to_string()
    }

    #[test]
    fn calls_from_many_threads_take_turns() {
        let count = within(|| {
            let c = Counter::new();
            let threads: Vec<_> = (0..8)
                .map(|_| {
                    let c = c.clone();
                    std::thread::spawn(move || {
                        for _ in 0..2000 {
                            c.bump();
                        }
                    })
                })
                .collect();
            for thread in threads {
                thread.join().unwrap();
            }
            c.count()
        });
        assert_eq!(count, 16000);
    }

    #[test]
    fn self_calls_and_callbacks_run_inline() {
        let count = within(|| {
            let c = Counter::new();
            c.twice();
            let inner = c.clone();
            c.with_callback(&move || inner.bump());
            c.count()
        });
        assert_eq!(count, 3);
    }

    #[test]
    fn a_call_back_through_another_actor_runs_inline() {
        let counts = within(|| {
            let a = Counter::new();
            let b = Counter::new();
            // a's turn calls into b, whose turn calls back into a.
            a.with_callback(&|| b.with_callback(&|| a.bump()));
            b.with_callback(&|| a.with_callback(&|| b.bump()));
            // Both actors are released and usable from another thread.
            std::thread::spawn(move || {
                a.bump();
                b.bump();
                (a.count(), b.count())
            })
            .join()
            .unwrap()
        });
        assert_eq!(counts, (2, 2));
    }

    #[test]
    fn a_cycle_across_two_threads_panics_on_one_side() {
        let mut results = within(|| {
            let a = Counter::new();
            let b = Counter::new();
            let both_in = Arc::new(std::sync::Barrier::new(2));
            // Thread 1 holds a and calls b; thread 2 holds b and calls a.
            let side = |mine: Counter, theirs: Counter, delay: u64| {
                let both_in = both_in.clone();
                std::thread::spawn(move || {
                    let result = std::panic::catch_unwind(AssertUnwindSafe(|| {
                        mine.with_callback(&|| {
                            both_in.wait();
                            std::thread::sleep(Duration::from_millis(delay));
                            theirs.bump();
                        })
                    }));
                    result.map_err(|payload| panic_text(Err(payload)))
                })
            };
            let one = side(a.clone(), b.clone(), 0);
            let two = side(b.clone(), a.clone(), 50);
            let results = vec![one.join().unwrap(), two.join().unwrap()];
            assert_eq!(a.count() + b.count(), 1);
            results
        });
        results.sort();
        assert_eq!(
            results,
            vec![
                Ok(()),
                Err("actor call cycle: Counter is waiting on this call".to_string())
            ]
        );
    }

    #[test]
    fn a_bubble_releases_the_actor() {
        let (result, count) = within(|| {
            let c = Counter::new();
            let result = c.checked(-1);
            let c2 = c.clone();
            std::thread::spawn(move || c2.bump()).join().unwrap();
            (result, c.count())
        });
        assert_eq!(result, Err(()));
        assert_eq!(count, 1);
    }

    #[test]
    fn a_panic_releases_the_actor() {
        let (text, count) = within(|| {
            let c = Counter::new();
            let c2 = c.clone();
            let text = panic_text(std::panic::catch_unwind(move || c2.boom()));
            let c3 = c.clone();
            std::thread::spawn(move || c3.bump()).join().unwrap();
            (text, c.count())
        });
        assert_eq!(text, "boom");
        assert_eq!(count, 1);
    }

    #[test]
    fn a_panic_on_another_thread_releases_the_actor() {
        let count = within(|| {
            let c = Counter::new();
            let c2 = c.clone();
            assert!(std::thread::spawn(move || c2.boom()).join().is_err());
            c.bump();
            c.count()
        });
        assert_eq!(count, 1);
    }

    #[test]
    fn a_task_queued_during_a_turn_runs_as_a_turn_of_that_actor() {
        let (inside, outside) = within(|| {
            let c = Counter::new();
            let seen = Arc::new(AtomicI32::new(0));
            let probe: crate::Task = {
                let seen = seen.clone();
                Arc::new(move || {
                    // On the chain and innermost: we are in the actor's turn.
                    let mine = current().is_some();
                    seen.store(if mine { 1 } else { -1 }, Ordering::SeqCst);
                })
            };
            let inside = {
                let _turn = c.1.enter();
                belonging_to_current(probe.clone())
            };
            let outside = belonging_to_current(probe);
            std::thread::spawn(move || {
                inside();
                let a = seen.load(Ordering::SeqCst);
                outside();
                (a, seen.load(Ordering::SeqCst))
            })
            .join()
            .unwrap()
        });
        assert_eq!((inside, outside), (1, -1));
    }

    #[test]
    fn a_queued_step_waits_for_the_turn_in_progress() {
        let order = within(|| {
            let c = Counter::new();
            let log = Arc::new(Mutex::new(Vec::new()));
            let step: crate::Task = {
                let log = log.clone();
                Arc::new(move || log.lock().unwrap().push("step"))
            };
            let step = {
                let _turn = c.1.enter();
                belonging_to_current(step)
            };
            let (entered_send, entered) = mpsc::channel();
            let holder = {
                let c = c.clone();
                let log = log.clone();
                std::thread::spawn(move || {
                    let _turn = c.1.enter();
                    entered_send.send(()).unwrap();
                    std::thread::sleep(Duration::from_millis(100));
                    log.lock().unwrap().push("turn");
                })
            };
            entered.recv().unwrap();
            step();
            holder.join().unwrap();
            let order = log.lock().unwrap().clone();
            order
        });
        assert_eq!(order, vec!["turn", "step"]);
    }
}
