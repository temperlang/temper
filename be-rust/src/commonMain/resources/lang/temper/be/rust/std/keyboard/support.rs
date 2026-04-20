use std::sync::Arc;
use temper_core::{Promise, PromiseBuilder};

#[cfg(not(feature = "keyboard"))]
pub fn std_next_keypress() -> Promise<Option<Arc<String>>> {
    panic!()
}

#[cfg(feature = "keyboard")]
pub fn std_next_keypress() -> Promise<Option<Arc<String>>> {
    let pb = PromiseBuilder::new();
    let promise = pb.promise();
    // Spawn a real OS thread so the blocking read doesn't starve
    // the single-threaded async runner. The promise completion will
    // fire the on_ready continuation from this thread.
    std::thread::spawn(move || {
        use crossterm::terminal;
        use crossterm::event::{self, Event, KeyCode, KeyEvent};

        terminal::enable_raw_mode().ok();
        let result = loop {
            match event::read() {
                Ok(Event::Key(KeyEvent { code, .. })) => {
                    break match code {
                        KeyCode::Up => Some("ArrowUp"),
                        KeyCode::Down => Some("ArrowDown"),
                        KeyCode::Left => Some("ArrowLeft"),
                        KeyCode::Right => Some("ArrowRight"),
                        KeyCode::Enter => Some("Enter"),
                        KeyCode::Esc => Some("Escape"),
                        KeyCode::Backspace => Some("Backspace"),
                        KeyCode::Tab => Some("Tab"),
                        KeyCode::Char(c) => {
                            terminal::disable_raw_mode().ok();
                            pb.complete(Some(Arc::new(c.to_string())));
                            return;
                        }
                        _ => Some("Unknown"),
                    };
                }
                Err(_) => { break None; }
                _ => continue,
            }
        };
        terminal::disable_raw_mode().ok();
        pb.complete(result.map(|s| Arc::new(s.to_string())));
    });
    promise
}
