//! `Map`, `MapBuilder` and `Pair`, as in Kotlin Temper's `core.temper`,
//! where `Mapped` is their common interface. Entries keep the order their
//! keys were first added in. Keys are `Int` or `String` values
//! (`MapKey`; Kotlin's core has a TODO about `Int64`). A missing key fails
//! `get` and `remove`, so `orelse` can catch it.

use super::{Abrupt, CoreDeclaredError, Interp, R, Val, fail};
use crate::settle::Settled;
use std::cell::RefCell;
use std::collections::HashMap;
use std::rc::Rc;

#[derive(Clone, Debug, PartialEq, Eq, Hash)]
enum Key {
    Int(i32),
    Str(Rc<str>),
}

fn key(value: &Val) -> Result<Key, Abrupt> {
    match value {
        Val::Int(n) => Ok(Key::Int(*n)),
        Val::Str(s) => Ok(Key::Str(s.clone())),
        _ => Err(fail("Not a map key")),
    }
}

/// A map's entries in order, with an index by key.
#[derive(Clone, Debug, Default)]
pub struct Entries {
    pairs: Vec<(Val, Val)>,
    index: HashMap<Key, usize>,
}

impl Entries {
    /// The pairs, to take their values out of as the map is dropped
    /// ([`super::Val`]'s `Drop`).
    pub(super) fn pairs_mut(&mut self) -> &mut [(Val, Val)] {
        &mut self.pairs
    }

    /// Entries from a list of `Pair`s; a later pair's value replaces an
    /// earlier one's for the same key, keeping its place.
    pub(super) fn from_pairs(pairs: &[Val]) -> Result<Entries, Abrupt> {
        let mut entries = Entries::default();
        for pair in pairs {
            let Val::Pair(pair) = pair else {
                return Err(fail("A Map is made from Pairs"));
            };
            entries.set(pair.0.clone(), pair.1.clone())?;
        }
        Ok(entries)
    }

    pub(super) fn len(&self) -> usize {
        self.pairs.len()
    }

    /// The entries, in order.
    pub(super) fn pairs(&self) -> &[(Val, Val)] {
        &self.pairs
    }

    fn get(&self, k: &Val) -> Result<Option<Val>, Abrupt> {
        Ok(self.index.get(&key(k)?).map(|&i| self.pairs[i].1.clone()))
    }

    fn set(&mut self, k: Val, v: Val) -> Result<(), Abrupt> {
        match self.index.get(&key(&k)?) {
            Some(&i) => self.pairs[i].1 = v,
            None => {
                self.index.insert(key(&k)?, self.pairs.len());
                self.pairs.push((k, v));
            }
        }
        Ok(())
    }

    fn remove(&mut self, k: &Val) -> Result<Option<Val>, Abrupt> {
        let Some(i) = self.index.remove(&key(k)?) else {
            return Ok(None);
        };
        let (_, v) = self.pairs.remove(i);
        for at in self.index.values_mut() {
            if *at > i {
                *at -= 1;
            }
        }
        Ok(Some(v))
    }
}

/// `map[key]`, throwing core's `MissingKey` if the key is missing.
pub(super) fn get(map: &Val, k: &Val) -> R {
    with_entries(map, |e| e.get(k))?.ok_or_else(|| {
        Abrupt::Throw(CoreDeclaredError::value(
            "MissingKey",
            vec![("key", k.clone())],
        ))
    })
}

/// Runs `f` on a `Map`'s or `MapBuilder`'s entries.
fn with_entries<T>(map: &Val, f: impl FnOnce(&Entries) -> T) -> T {
    match map {
        Val::Map(entries) => f(entries),
        Val::MapBuilder(entries) => f(&entries.borrow()),
        _ => f(&Entries::default()),
    }
}

fn pair(k: &Val, v: &Val) -> Val {
    Val::Pair(Rc::new((k.clone(), v.clone())))
}

impl Interp<'_> {
    /// A method of a `Map` or `MapBuilder`. Callbacks see a copy of a
    /// builder's entries, so they may change it.
    pub(super) fn map_method(&mut self, s: &Settled, this: &Val, name: &str, args: Vec<Val>) -> R {
        if let Some(result) = builder_method(this, name, &args) {
            return result;
        }
        match (name, args.as_slice()) {
            ("get", [k]) => get(this, k),
            ("getOr", [k, fallback]) => {
                Ok(with_entries(this, |e| e.get(k))?.unwrap_or_else(|| fallback.clone()))
            }
            ("has", [k]) => Ok(Val::Bool(with_entries(this, |e| e.get(k))?.is_some())),
            ("keys" | "values", []) => {
                let keys = name == "keys";
                let items = with_entries(this, |e| {
                    e.pairs
                        .iter()
                        .map(|(k, v)| if keys { k.clone() } else { v.clone() })
                        .collect()
                });
                Ok(Val::List(items))
            }
            ("toMap", []) => Ok(match this {
                Val::Map(entries) => Val::Map(entries.clone()),
                _ => Val::Map(Rc::new(with_entries(this, Entries::clone))),
            }),
            ("toMapBuilder", []) => Ok(Val::MapBuilder(Rc::new(RefCell::new(with_entries(
                this,
                Entries::clone,
            ))))),
            ("toList" | "toListBuilder", []) => {
                let items: Vec<Val> =
                    with_entries(this, |e| e.pairs.iter().map(|(k, v)| pair(k, v)).collect());
                Ok(list_or_builder(name == "toList", items))
            }
            ("toListWith" | "toListBuilderWith" | "forEach", [f]) => {
                let pairs = with_entries(this, |e| e.pairs.clone());
                let mut out = Vec::with_capacity(pairs.len());
                for (k, v) in pairs {
                    out.push(self.apply(s, f.clone(), vec![k, v])?);
                }
                Ok(match name {
                    "forEach" => Val::Void,
                    _ => list_or_builder(name == "toListWith", out),
                })
            }
            _ => super::no_method(this, name),
        }
    }
}

fn list_or_builder(list: bool, items: Vec<Val>) -> Val {
    if list {
        Val::List(items.into())
    } else {
        Val::ListBuilder(Rc::new(RefCell::new(items)))
    }
}

/// `MapBuilder`'s own methods, which don't call back.
fn builder_method(this: &Val, name: &str, args: &[Val]) -> Option<R> {
    let Val::MapBuilder(builder) = this else {
        return None;
    };
    Some(match (name, args) {
        ("set", [k, v]) => builder
            .borrow_mut()
            .set(k.clone(), v.clone())
            .map(|()| Val::Void),
        ("remove", [k]) => builder.borrow_mut().remove(k).and_then(|v| {
            v.ok_or_else(|| {
                Abrupt::Throw(CoreDeclaredError::value(
                    "MissingKey",
                    vec![("key", k.clone())],
                ))
            })
        }),
        ("clear", []) => {
            *builder.borrow_mut() = Entries::default();
            Ok(Val::Void)
        }
        _ => return None,
    })
}
