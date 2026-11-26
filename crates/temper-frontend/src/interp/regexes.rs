//! std's `regex` connecteds, as Kotlin Temper's `RegexExec.kt` has them.
//! `RegexFormatter.regexCompileFormatted` compiles a `RegexNode`'s objects
//! into a [`Regex`], not its formatted text, which goes unused; `Regex`'s
//! `compiledFound`, `compiledFind` (null for no match), `compiledReplace`
//! and `compiledSplit` match with it, making `Match` and `Group` objects of the classes of
//! `regexRefs`' examples. `replace` and `split` go through matches as
//! Kotlin's `Regex` does, a char past an empty match. A match's `groups`
//! has the captures that took part, in the pattern's order.
//! `RegexFormatter.pushCodeTo`, for codes the formatter has no plainer form
//! for, appends `\u{…}`.

use super::{Abrupt, Interp, R, Val, fail, maps};
use crate::regex::{Found, Node, Regex, Special};
use crate::settle::Settled;
use std::rc::Rc;

impl Interp<'_> {
    pub(super) fn regex_connected(
        &mut self,
        s: &Settled,
        class: &str,
        name: &str,
        args: Vec<Val>,
    ) -> R {
        match (name, args.as_slice()) {
            ("regexCompileFormatted", [data, _]) => {
                Ok(Val::Regex(Rc::new(Regex::new(self.regex_node(s, data)?))))
            }
            ("compiledFound", [Val::Regex(regex), Val::Str(text)]) => {
                Ok(Val::Bool(regex.find(text, 0).is_some()))
            }
            (
                "compiledFind",
                [
                    Val::Regex(regex),
                    Val::Str(text),
                    Val::StringIndex(begin),
                    refs,
                ],
            ) => {
                let mut begin = (*begin as usize).min(text.len());
                while !text.is_char_boundary(begin) {
                    begin -= 1;
                }
                match regex.find(text, begin) {
                    Some(found) => self.regex_match(s, regex, text, &found, refs),
                    None => Ok(Val::Null),
                }
            }
            ("compiledReplace", [Val::Regex(regex), Val::Str(text), format, refs]) => {
                let mut out = String::with_capacity(text.len());
                let mut last = 0;
                for found in regex.find_all(text) {
                    out.push_str(&text[last..found.begin]);
                    let found_match = self.regex_match(s, regex, text, &found, refs)?;
                    match self.apply(s, format.clone(), vec![found_match])? {
                        Val::Str(ref replacement) => out.push_str(replacement),
                        _ => return Err(fail("A replacement must be a String")),
                    }
                    last = found.end;
                    if last >= text.len() {
                        break;
                    }
                }
                out.push_str(&text[last..]);
                Ok(Val::Str(out.into()))
            }
            ("compiledSplit", [Val::Regex(regex), Val::Str(text), _]) => {
                let mut parts = Vec::new();
                let mut last = 0;
                for found in regex.find_all(text) {
                    parts.push(Val::Str(text[last..found.begin].into()));
                    last = found.end;
                }
                parts.push(Val::Str(text[last..].into()));
                Ok(Val::List(parts.into()))
            }
            ("pushCodeTo", [Val::StringBuilder(out), Val::Int(code), _]) => {
                out.borrow_mut().push_str(&format!("\\u{{{code:x}}}"));
                Ok(Val::Void)
            }
            _ => Err(fail(format!(
                "No connected implementation of {class}.{name}"
            ))),
        }
    }

    /// A `RegexNode`'s objects as a [`Node`], by their classes' names.
    fn regex_node(&mut self, s: &Settled, value: &Val) -> Result<Node, Abrupt> {
        let Val::Object(object) = value else {
            return Err(fail("Not a RegexNode"));
        };
        let class = super::class_name(s, object.class.node);
        let get = |this: &mut Self, field: &str| {
            let name = s
                .library
                .names
                .find(field)
                .ok_or_else(|| fail(format!("No member {field}")))?;
            this.get_property(s, value.clone(), name, None)
        };
        let count = |value: Val| match value {
            Val::Int(n) if n >= 0 => Ok(n as u32),
            _ => Err(fail("Expected a code point or count")),
        };
        Ok(match class {
            "Capture" => {
                let Val::Str(ref name) = get(self, "name")? else {
                    return Err(fail("Expected a String"));
                };
                let item = get(self, "item")?;
                Node::Capture {
                    name: name.to_string(),
                    index: 0,
                    item: Box::new(self.regex_node(s, &item)?),
                }
            }
            "CodePoints" => match get(self, "value")? {
                Val::Str(ref text) => Node::Chars(text.to_string()),
                _ => return Err(fail("Expected a String")),
            },
            "CodeRange" => Node::Range(count(get(self, "min")?)?, count(get(self, "max")?)?),
            "CodeSet" => {
                let items = get(self, "items")?;
                let negated = matches!(get(self, "negated")?, Val::Bool(true));
                Node::Set(self.regex_nodes(s, &items)?, negated)
            }
            "Or" | "Sequence" => {
                let items = get(self, "items")?;
                let items = self.regex_nodes(s, &items)?;
                if class == "Or" {
                    Node::Or(items)
                } else {
                    Node::Sequence(items)
                }
            }
            "Repeat" => {
                let item = get(self, "item")?;
                let item = Box::new(self.regex_node(s, &item)?);
                let max = match get(self, "max")? {
                    Val::Null => None,
                    max => Some(count(max)?),
                };
                let reluctant = matches!(get(self, "reluctant")?, Val::Bool(true));
                Node::Repeat(item, count(get(self, "min")?)?, max, reluctant)
            }
            other => Node::Special(
                Special::named(other).ok_or_else(|| fail(format!("Not a RegexNode: {other}")))?,
            ),
        })
    }

    fn regex_nodes(&mut self, s: &Settled, list: &Val) -> Result<Vec<Node>, Abrupt> {
        let Val::List(items) = list else {
            return Err(fail("Expected a List"));
        };
        items.iter().map(|item| self.regex_node(s, item)).collect()
    }

    /// A `Match` for what was found, of the classes `refs`, a `RegexRefs`,
    /// has examples of.
    fn regex_match(
        &mut self,
        s: &Settled,
        regex: &Regex,
        text: &str,
        found: &Found,
        refs: &Val,
    ) -> R {
        let class_of = |this: &mut Self, field: &str| {
            let name = s
                .library
                .names
                .find(field)
                .ok_or_else(|| fail(format!("No member {field}")))?;
            match this.get_property(s, refs.clone(), name, None)? {
                Val::Object(ref example) => Ok(example.class.clone()),
                _ => Err(fail("Expected an object")),
            }
        };
        let (group_class, match_class) = (class_of(self, "group")?, class_of(self, "match")?);
        let group = |this: &mut Self, name: &str, (begin, end): (usize, usize)| {
            let parts = vec![
                Val::Str(name.into()),
                Val::Str(text[begin..end].into()),
                Val::StringIndex(begin as u32),
                Val::StringIndex(end as u32),
            ];
            this.construct(s, group_class.clone(), parts)
        };
        let full = group(self, "full", (found.begin, found.end))?;
        let mut pairs = Vec::new();
        for (name, &span) in regex.names.iter().zip(&found.captures) {
            if let Some(span) = span {
                let value = group(self, name, span)?;
                pairs.push(Val::Pair(Rc::new((Val::Str(name.as_str().into()), value))));
            }
        }
        let groups = Val::Map(Rc::new(maps::Entries::from_pairs(&pairs)?));
        self.construct(s, match_class, vec![full, groups])
    }
}
