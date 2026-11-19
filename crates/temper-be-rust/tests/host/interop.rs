//! Host code using the generated `async-turns` crate the standard way,
//! and `class-edges`' builder for a generic class,
//! for `functional.rs`'s `runs_and_lints`: it prints a line per check,
//! which the test compares with what it expects. Clippy lints it with the
//! generated crates.

use std::future::Future;
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::sync::{Arc, Mutex};
use std::task::{Context, Poll, Wake, Waker};
use temper_core::{AsyncRunner, SafeGenerator};

/// Runs a future to its end on this thread, with nothing but std, letting
/// the Temper runner take its turns while the future waits: one way to
/// put Temper's runner beside another executor.
fn block_on<F: Future>(runner: &AsyncRunner, future: F) -> F::Output {
    struct Unpark(std::thread::Thread);
    impl Wake for Unpark {
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
        runner.run_all_blocking();
        if let Poll::Ready(output) = future.as_mut().poll(&mut cx) {
            return output;
        }
        std::thread::park();
    }
}

fn main() {
    // Its top-level code logs, and queues its async blocks, which run here.
    let runner = async_turns::init(None).unwrap();
    runner.run_all_blocking();

    // `.await` takes a generated promise: it's a `Future`.
    let value = block_on(&runner, async { async_turns::later(1).await });
    println!("await {}", value.unwrap());

    // Blocking inside a turn, before the promise settles, panics.
    std::panic::set_hook(Box::new(|_| {}));
    runner.run_async(Arc::new(|| {
        SafeGenerator::<()>::from_fn(Arc::new(|_| {
            let _ = async_turns::later(4).get();
            None
        }))
    }));
    let blocked = catch_unwind(AssertUnwindSafe(|| runner.run_all_blocking()));
    let message = blocked
        .err()
        .and_then(|e| e.downcast_ref::<&str>().map(|m| m.to_string()));
    println!(
        "in a turn: {}",
        message.is_some_and(|m| m.contains("would deadlock"))
    );
    runner.run_all_blocking();

    // A `for` loop steps a generator: it's an `Iterator`.
    let seen = Arc::new(Mutex::new(Vec::new()));
    let each = seen.clone();
    for () in async_turns::ticks(3, Arc::new(move |i| each.lock().unwrap().push(i))) {}
    println!("for {:?}", seen.lock().unwrap());

    // A generator stepped again after a failed step is done.
    let mut g = async_turns::ticks(
        3,
        Arc::new(|i| {
            if i == 1 {
                panic!("boom");
            }
        }),
    );
    // `Iterator::next`, as `SafeGenerator`'s own `next` gives a `Result`.
    Iterator::next(&mut g);
    if catch_unwind(AssertUnwindSafe(|| Iterator::next(&mut g))).is_err() {
        println!("failed step");
    }
    println!("then done {}", Iterator::next(&mut g).is_none());

    // A `for` loop takes a lazy generator's values, and stops early.
    let fib: Vec<String> = async_turns::fibonacci()
        .take(8)
        .map(|n| n.to_string())
        .collect();
    println!("fibonacci {}", fib.join(","));
    let mut steps = Vec::new();
    for n in async_turns::collatz(7) {
        steps.push(n.to_string());
    }
    println!("collatz {}", steps.join(","));

    // A Temper failure is an `Err`, as notes/api-compat.md promises.
    match async_turns::halve(3) {
        Ok(n) => println!("halve returned {n}"),
        Err(_) => println!("halve {} caught", async_turns::halve(8).unwrap()),
    }

    // A generic class's builder, without its options and with them.
    use class_edges::builders::DuoBuilder;
    let ann = class_edges::Person::new("Ann", "Dr");
    let duo = DuoBuilder { first: ann.clone() }.build();
    println!("builder {}", duo.names());
    let duo = DuoBuilder { first: ann }
        .options()
        .second(class_edges::Person::new("Cy", "Mr"))
        .joiner(Arc::new(" + ".to_string()))
        .build();
    println!("options {}", duo.names());
}
