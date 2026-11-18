//! Running lowered code ([`super::lower`]): a [`Frame`] per call, and one
//! loop, [`Interp::run_frame`], over its ops. An ordinary call runs the
//! callee's frame nested on the Rust stack. A frame holds all its code
//! needs to go on from where it is, its position included, so that a
//! generator, or an `async` block, can own one and re-enter the loop
//! where it left off.

use super::lower::{Code, Exit, Op, Source};
use super::{Abrupt, Class, Closure, Interp, R, Val, fail};
use crate::settle::Settled;
use std::cell::{OnceCell, RefCell};
use std::rc::Rc;

/// A captured variable, shared by the frame declaring it and the closures
/// and classes using it.
pub(super) type Cell = Rc<RefCell<Val>>;

pub(super) fn new_cell() -> Cell {
    Rc::new(RefCell::new(Val::Uninit))
}

/// A running piece of code.
#[derive(Debug)]
pub(super) struct Frame {
    code: Rc<Code>,
    /// Its slots, then its stack.
    values: Vec<Val>,
    cells: Vec<Cell>,
    captures: Rc<[Cell]>,
    /// The next op.
    pc: u32,
}

impl Frame {
    /// A frame to run `code` from the start, with its first slots set
    /// from `args` and the rest unset.
    pub(super) fn new(code: Rc<Code>, captures: Rc<[Cell]>, args: Vec<Val>) -> Frame {
        let mut values = args;
        values.resize(code.slots as usize, Val::Uninit);
        values.reserve(code.stack as usize);
        let cells = (0..code.cells).map(|_| new_cell()).collect();
        Frame {
            code,
            values,
            cells,
            captures,
            pc: 0,
        }
    }

    fn push(&mut self, value: Val) {
        self.values.push(value);
    }

    fn pop(&mut self) -> Val {
        self.values
            .pop()
            .expect("lowered code keeps its stack balanced")
    }

    /// The top `n` values, popped.
    fn pop_n(&mut self, n: u32) -> Vec<Val> {
        self.values.split_off(self.values.len() - n as usize)
    }

    fn truncate(&mut self, depth: u32) {
        self.values.truncate((self.code.slots + depth) as usize);
    }
}

impl Interp<'_> {
    /// Runs a frame until its code returns or ends abruptly.
    pub(super) fn run_frame(&mut self, s: &Settled, frame: &mut Frame) -> R {
        let code = frame.code.clone();
        loop {
            if self.fuel == 0 {
                self.refuel()?;
            }
            self.fuel -= 1;
            let at = frame.pc;
            frame.pc += 1;
            let result = match &code.ops[at as usize] {
                Op::Const(value) => {
                    frame.push(value.clone());
                    Ok(())
                }
                Op::Local(i) => {
                    let value = frame.values[*i as usize].clone();
                    push_variable(s, &code, at, frame, value)
                }
                Op::Cell(i) => {
                    let value = frame.cells[*i as usize].borrow().clone();
                    push_variable(s, &code, at, frame, value)
                }
                Op::Capture(i) => {
                    let value = frame.captures[*i as usize].borrow().clone();
                    push_variable(s, &code, at, frame, value)
                }
                Op::Global(i) => {
                    let value = self.globals[*i as usize].clone();
                    match value {
                        Val::Uninit if self.unset_why.contains_key(i) => {
                            Err(fail(self.unset_why[i].to_string()))
                        }
                        value => push_variable(s, &code, at, frame, value),
                    }
                }
                Op::SetLocal(i) => {
                    frame.values[*i as usize] = frame.pop();
                    Ok(())
                }
                Op::SetCell(i) => {
                    *frame.cells[*i as usize].borrow_mut() = frame.pop();
                    Ok(())
                }
                Op::SetCapture(i) => {
                    *frame.captures[*i as usize].borrow_mut() = frame.pop();
                    Ok(())
                }
                Op::SetGlobal(i) => {
                    self.globals[*i as usize] = frame.pop();
                    Ok(())
                }
                Op::FreshCell(i) => {
                    frame.cells[*i as usize] = new_cell();
                    Ok(())
                }
                Op::Pop => {
                    frame.pop();
                    Ok(())
                }
                Op::Dup => {
                    let top = frame.values.last().unwrap().clone();
                    frame.push(top);
                    Ok(())
                }
                Op::Truncate(depth) => {
                    frame.truncate(*depth);
                    Ok(())
                }
                Op::Jump(to) => {
                    frame.pc = *to;
                    Ok(())
                }
                Op::Cached { index, skip } => {
                    if let Some(Some(value)) = self.cached.get(*index as usize) {
                        frame.push(value.clone());
                        frame.pc = *skip;
                    }
                    Ok(())
                }
                Op::Cache(index) => {
                    let i = *index as usize;
                    if self.cached.len() <= i {
                        self.cached.resize(i + 1, None);
                    }
                    self.cached[i] = frame.values.last().cloned();
                    Ok(())
                }
                Op::JumpIfFalse(to) => match frame.pop() {
                    Val::Bool(true) => Ok(()),
                    Val::Bool(false) => {
                        frame.pc = *to;
                        Ok(())
                    }
                    other => Err(fail(format!(
                        "Expected a Boolean, not {}",
                        super::type_name(&other)
                    ))),
                },
                Op::GetProp(name) => {
                    let object = frame.pop();
                    self.get_property(s, object, *name, Some(&code.classes))
                        .map(|value| frame.push(value))
                }
                Op::GetInput(slot) => match &frame.pop() {
                    Val::Object(object) => match object.fields.borrow()[*slot as usize].clone() {
                        Val::Uninit => Err(fail("An input has not been initialized")),
                        value => {
                            frame.push(value);
                            Ok(())
                        }
                    },
                    _ => Err(fail("No this here")),
                },
                Op::SetProp(name) => {
                    let value = frame.pop();
                    let object = frame.pop();
                    self.set_property(s, object, *name, value.clone(), Some(&code.classes))
                        .map(|()| frame.push(value))
                }
                Op::Call { args } => {
                    let args = frame.pop_n(*args);
                    let function = frame.pop();
                    self.apply(s, function, args).map(|value| frame.push(value))
                }
                Op::CallMethod { name, args } => {
                    let args = frame.pop_n(*args);
                    let object = frame.pop();
                    let result = match object {
                        Val::Object(_) | Val::Class(_) => {
                            self.call_method(s, &object, *name, args, Some(&code.classes))
                        }
                        _ => self.apply(s, Val::Method(Rc::new((object, *name))), args),
                    };
                    result.map(|value| frame.push(value))
                }
                Op::MakeFn { node, sources } => {
                    let captures = capture(frame, sources);
                    frame.push(Val::Fn(Rc::new(Closure {
                        node: *node,
                        captures,
                    })));
                    Ok(())
                }
                Op::MakeClass {
                    node,
                    layout,
                    sources,
                } => {
                    let captures = capture(frame, sources);
                    let size = s
                        .scopes
                        .of_node(*node)
                        .map_or(0, |scope| s.scopes.get(scope).names.len());
                    let statics = RefCell::new(vec![Val::Uninit; size]);
                    let (node, layout, supers) = (*node, layout.clone(), OnceCell::new());
                    frame.push(Val::Class(Rc::new(Class {
                        node,
                        layout,
                        captures,
                        statics,
                        supers,
                        operators: Default::default(),
                    })));
                    Ok(())
                }
                Op::InitStatics => match frame.pop() {
                    Val::Class(ref class) => self.init_statics(s, class),
                    _ => Err(fail("Class not set")),
                },
                Op::Return => return Ok(frame.pop()),
                // Kept here, the frame goes on after this op when resumed.
                Op::Yield => return Err(Abrupt::Yield(frame.pop())),
                Op::Await => match frame.pop() {
                    Val::Promise(ref promise) => match promise.outcome() {
                        Some(outcome) => outcome.map(|value| frame.push(value)),
                        None => {
                            // Paused at this op, keeping the promise, to
                            // await it again when resumed.
                            frame.push(Val::Promise(promise.clone()));
                            frame.pc = at;
                            return Err(Abrupt::Await(promise.clone()));
                        }
                    },
                    other => Err(fail(format!(
                        "Expected a Promise, not {}",
                        super::type_name(&other)
                    ))),
                },
                Op::Exit(Exit::Break(target)) => Err(Abrupt::Break(*target)),
                Op::Exit(Exit::Continue(target)) => Err(Abrupt::Continue(*target)),
                Op::Exit(Exit::Return(target)) => Err(Abrupt::Return(*target, frame.pop())),
                Op::Fail(message) => Err(Abrupt::Fail(message.as_ref().map(|m| m.to_string()))),
                Op::Panic(message) => Err(Abrupt::Panic(message.to_string())),
                Op::Required(slot) => match frame.values[*slot as usize] {
                    Val::Uninit => Err(fail("Too few arguments")),
                    _ => Ok(()),
                },
                Op::Default { slot, skip } => {
                    if !matches!(frame.values[*slot as usize], Val::Uninit | Val::Null) {
                        frame.pc = *skip;
                    }
                    Ok(())
                }
            };
            if let Err(abrupt) = result {
                if let Some(value) = land(frame, at, abrupt)? {
                    return Ok(value);
                }
            }
        }
    }
}

impl Interp<'_> {
    /// More steps, once `fuel` runs out: a slice of the reserve, unless
    /// the run was interrupted, or there's none left.
    fn refuel(&mut self) -> Result<(), Abrupt> {
        if (self.interrupt.as_ref()).is_some_and(|i| i.load(std::sync::atomic::Ordering::Relaxed)) {
            return Err(Abrupt::Panic(super::INTERRUPTED.into()));
        }
        if self.reserve == 0 {
            return Err(Abrupt::Panic("ran out of steps".into()));
        }
        self.fuel = self.reserve.min(super::SLICE);
        self.reserve -= self.fuel;
        Ok(())
    }
}

/// Pushes a variable's value, failing if it's not set yet.
fn push_variable(
    s: &Settled,
    code: &Code,
    at: u32,
    frame: &mut Frame,
    value: Val,
) -> Result<(), Abrupt> {
    if let Val::Uninit = value {
        let node = code.nodes[at as usize];
        let name = s
            .scopes
            .resolved(node)
            .map_or("", |b| s.library.names.get(s.scopes.symbol(b).name));
        return Err(fail(format!("{name} has not been initialized")));
    }
    frame.push(value);
    Ok(())
}

/// The cells a closure or class captures from a frame.
fn capture(frame: &Frame, sources: &[Source]) -> Rc<[Cell]> {
    let source = |source: &Source| match *source {
        Source::Cell(i) => frame.cells[i as usize].clone(),
        Source::Capture(i) => frame.captures[i as usize].clone(),
    };
    sources.iter().map(source).collect()
}

/// Where an abrupt end of an op at `at` goes: an `orelse`'s handler for a
/// failure, a landing for a jump from a `for … of` body's call, or this
/// frame's end (`Some`) for its own `return` or `continue`. Anything else
/// goes on to the caller.
fn land(frame: &mut Frame, at: u32, abrupt: Abrupt) -> Result<Option<Val>, Abrupt> {
    let code = frame.code.clone();
    let within = |start: u32, end: u32| start <= at && at < end;
    match abrupt {
        Abrupt::Fail(_) => {
            if let Some(h) = code.handlers.iter().find(|h| within(h.start, h.end)) {
                frame.truncate(h.depth);
                frame.pc = h.to;
                return Ok(None);
            }
        }
        Abrupt::Break(target) | Abrupt::Continue(target) => {
            let is_continue = matches!(abrupt, Abrupt::Continue(_));
            let landing = code.landings.iter().find(|l| {
                l.target == target && l.continue_ == is_continue && within(l.start, l.end)
            });
            if let Some(l) = landing {
                frame.truncate(l.depth);
                frame.pc = l.to;
                return Ok(None);
            }
            if is_continue && code.function == Some(target) {
                return Ok(Some(Val::Void));
            }
        }
        Abrupt::Return(target, value) if code.function == Some(target) => return Ok(Some(value)),
        _ => {}
    }
    Err(abrupt)
}
