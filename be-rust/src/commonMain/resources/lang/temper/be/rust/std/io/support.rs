use std::sync::Arc;
use std::io::BufRead;
use temper_core::{Promise, PromiseBuilder, SafeGenerator};

pub fn std_sleep(ms: i32) -> Promise<()> {
    let pb = PromiseBuilder::new();
    let promise = pb.promise();
    crate::run_async(Arc::new(move || {
        let pb = pb.clone();
        SafeGenerator::from_fn(Arc::new(move |_generator: SafeGenerator<()>| {
            eprintln!("[sleep] {}ms", ms);
            std::thread::sleep(std::time::Duration::from_millis(ms as u64));
            pb.complete(());
            None
        }))
    }));
    promise
}

pub fn std_term_cols() -> i32 {
    match crossterm::terminal::size() {
        Ok((cols, _)) => cols as i32,
        Err(_) => 80,
    }
}

pub fn std_term_rows() -> i32 {
    match crossterm::terminal::size() {
        Ok((_, rows)) => rows as i32,
        Err(_) => 24,
    }
}

pub fn std_read_line() -> Promise<Option<Arc<String>>> {
    let pb = PromiseBuilder::new();
    let promise = pb.promise();
    crate::run_async(Arc::new(move || {
        let pb = pb.clone();
        SafeGenerator::from_fn(Arc::new(move |_generator: SafeGenerator<()>| {
            let stdin = std::io::stdin();
            let mut line = String::new();
            match stdin.lock().read_line(&mut line) {
                Ok(0) => pb.complete(None),
                Ok(_) => {
                    let trimmed = line.trim_end_matches('\n').trim_end_matches('\r');
                    pb.complete(Some(Arc::new(trimmed.to_string())));
                }
                Err(_) => pb.complete(None),
            }
            None
        }))
    }));
    promise
}
