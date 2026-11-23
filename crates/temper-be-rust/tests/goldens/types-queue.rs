use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            QUEUE
                .set(temper_core::queue::new())
                .unwrap_or_else(|_| panic!());
            check_empty();
            if !temper_core::queue::is_empty(&queue()) {
                println!("{}", temper_core::queue::remove_first(&queue()));
            }
            temper_core::queue::add(&queue(), Arc::new("foo".to_string()));
            temper_core::queue::add(&queue(), Arc::new("bar".to_string()));
            println!("{}", temper_core::queue::remove_first(&queue()));
            temper_core::queue::add(&queue(), Arc::new("baz".to_string()));
            println!("{}", temper_core::queue::remove_first(&queue()));
            temper_core::queue::add(&queue(), Arc::new("boo".to_string()));
            check_empty();
            while !temper_core::queue::is_empty(&queue()) {
                println!("{}", temper_core::queue::remove_first(&queue()));
            }
            check_empty();
            let numbers: temper_core::Queue<Option<i32>> = temper_core::queue::new();
            let mut sum: i32 = 0;
            {
                let mut i: i32 = 0;
                while i < 100 {
                    temper_core::queue::add(&numbers, Some(i));
                    temper_core::queue::add(&numbers, None);
                    if i.wrapping_rem(3) == 0 {
                        sum = sum.wrapping_add({
                            let subject = temper_core::queue::remove_first(&numbers);
                            subject.unwrap_or(-1000)
                        });
                        sum = sum.wrapping_add({
                            let subject2 = temper_core::queue::remove_first(&numbers);
                            subject2.unwrap_or(1)
                        });
                    }
                    i = i.wrapping_add(1);
                }
            }
            let mut order = true;
            let mut expected: i32 = 34;
            while !temper_core::queue::is_empty(&numbers) {
                let n = temper_core::queue::remove_first(&numbers);
                if let Some(n) = n {
                    if n != expected {
                        order = false;
                    }
                    expected = expected.wrapping_add(1);
                }
            }
            println!("sum {sum}, in order {order}, through {expected}");
            Ok(())
        })
        .clone()
}

static QUEUE: OnceLock<temper_core::Queue<Arc<String>>> = OnceLock::new();

pub(crate) fn queue() -> temper_core::Queue<Arc<String>> {
    crate::support::initialized(|| QUEUE.get()).clone()
}

pub(crate) fn check_empty() {
    println!(
        "{}",
        if temper_core::queue::is_empty(&queue()) {
            "Empty"
        } else {
            "Not empty"
        }
    );
}
