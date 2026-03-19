use std::sync::Arc;
use temper_core::{Promise, PromiseBuilder, SafeGenerator};

pub fn std_next_keypress() -> Promise<Option<Arc<String>>> {
    let pb = PromiseBuilder::new();
    let promise = pb.promise();
    crate::run_async(Arc::new(move || {
        let pb = pb.clone();
        SafeGenerator::from_fn(Arc::new(move |_generator: SafeGenerator<()>| {
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
                                return None;
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
            None
        }))
    }));
    promise
}
