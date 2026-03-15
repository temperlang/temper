use std::sync::Arc;
use std::io::BufRead;
use temper_core::{Promise, PromiseBuilder, SafeGenerator};

pub fn std_sleep(ms: i32) -> Promise<()> {
    let pb = PromiseBuilder::new();
    let promise = pb.promise();
    crate::run_async(Arc::new(move || {
        let pb = pb.clone();
        SafeGenerator::from_fn(Arc::new(move |_generator: SafeGenerator<()>| {
            std::thread::sleep(std::time::Duration::from_millis(ms as u64));
            pb.complete(());
            None
        }))
    }));
    promise
}

pub fn std_read_line() -> Promise<Option<Arc<String>>> {
    let pb = PromiseBuilder::new();
    let promise = pb.promise();
    crate::run_async(Arc::new(move || {
        let pb = pb.clone();
        SafeGenerator::from_fn(Arc::new(move |_generator: SafeGenerator<()>| {
            #[cfg(unix)]
            {
                use std::os::unix::io::AsRawFd;
                let stdin_fd = std::io::stdin().as_raw_fd();
                let mut old_termios = std::mem::MaybeUninit::uninit();
                let is_tty = unsafe { libc::isatty(stdin_fd) } != 0;
                if is_tty {
                    unsafe {
                        libc::tcgetattr(stdin_fd, old_termios.as_mut_ptr());
                        let mut raw = old_termios.assume_init();
                        raw.c_lflag &= !(libc::ICANON | libc::ECHO);
                        raw.c_cc[libc::VMIN] = 1;
                        raw.c_cc[libc::VTIME] = 0;
                        libc::tcsetattr(stdin_fd, libc::TCSANOW, &raw);
                    }
                    let mut buf = [0u8; 1];
                    match std::io::Read::read(&mut std::io::stdin(), &mut buf) {
                        Ok(1) => {
                            unsafe {
                                libc::tcsetattr(stdin_fd, libc::TCSANOW, old_termios.as_ptr());
                            }
                            if buf[0] == 3 {
                                // Ctrl+C
                                unsafe { libc::raise(libc::SIGINT); }
                                pb.complete(None);
                            } else {
                                pb.complete(Some(Arc::new(String::from_utf8_lossy(&buf).to_string())));
                            }
                        }
                        _ => {
                            unsafe {
                                libc::tcsetattr(stdin_fd, libc::TCSANOW, old_termios.as_ptr());
                            }
                            pb.complete(None);
                        }
                    }
                } else {
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
                }
            }
            #[cfg(not(unix))]
            {
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
            }
            None
        }))
    }));
    promise
}
