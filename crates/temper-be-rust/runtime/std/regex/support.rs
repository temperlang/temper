use super::*;
use std::{
    fmt::Write,
    sync::{Arc, RwLock},
};
use temper_core::{cast, mapped::Map, AnyValue, AsAnyValue};

#[cfg(feature = "regex")]
pub use with_regex::*;
#[cfg(not(feature = "regex"))]
pub use without_regex::*;

#[cfg(feature = "regex")]
mod with_regex {
    use regex::Captures;
    use super::*;

    #[derive(Clone)]
    struct Compiled(Arc<regex::Regex>);
    temper_core::impl_any_value_trait!(Compiled, []);

    fn captures_to_match(regex: &regex::Regex, captures: &Captures) -> Match {
        // Cache the "full" name for reuse. TODO Is this actually more efficient?
        static FULL_NAME: std::sync::OnceLock<Arc<String>> = std::sync::OnceLock::new();
        let full_name = FULL_NAME.get_or_init(|| Arc::new("full".to_string()));
        // Build match.
        let full = captures.get(0).unwrap();
        Match::new(
            Group::new(full_name.clone(), full.as_str(), full.start(), full.end()),
            Map::new(
                regex
                    .capture_names()
                    .skip(1)
                    .zip(captures.iter().skip(1))
                    .filter_map(|(name, capture)| {
                        capture.map(|capture| {
                            // We expect a name for every Temper regex group.
                            let name = Arc::new(name.unwrap().to_string());
                            (
                                name.clone(),
                                Group::new(name, capture.as_str(), capture.start(), capture.end()),
                            )
                        })
                    })
                    .collect::<Vec<_>>(),
            ),
        )
    }

    pub(crate) fn compile_formatted(
        _data: &dyn RegexNodeTrait,
        formatted: Arc<String>,
    ) -> temper_core::AnyValue {
        // TODO Because the formatting is structured, do we always expect parsing success?
        Compiled(Arc::new(regex::Regex::new(&formatted).unwrap())).as_any_value()
    }

    pub(crate) fn compiled_find(
        _regex: &Regex,
        compiled: AnyValue,
        text: Arc<String>,
        begin: usize,
        _regex_refs: RegexRefs,
    ) -> Option<Match> {
        let compiled = cast::<Compiled>(compiled).unwrap().0;
        let captures = compiled.captures(&text[begin..])?;
        Some(captures_to_match(&compiled, &captures))
    }

    pub(crate) fn compiled_found(_regex: &Regex, compiled: AnyValue, text: Arc<String>) -> bool {
        cast::<Compiled>(compiled).unwrap().0.is_match(&text)
    }

    /// Each match replaced by what `format` gives, as `replace_all`
    /// would, but stopping at `format`'s first failure, which it returns
    /// (RFC 164's failure parameter `E`, `Infallible` where it can't).
    pub(crate) fn compiled_replace<E>(
        _regex: &Regex,
        compiled: AnyValue,
        text: Arc<String>,
        format: &dyn Fn(Match) -> Result<Arc<String>, E>,
        _regex_refs: RegexRefs,
    ) -> Result<Arc<String>, E> {
        let compiled = cast::<Compiled>(compiled).unwrap().0;
        let mut result: Option<String> = None;
        let mut last = 0;
        for captures in compiled.captures_iter(&text) {
            let full = captures.get(0).unwrap();
            let out = result.get_or_insert_with(|| String::with_capacity(text.len()));
            out.push_str(&text[last..full.start()]);
            out.push_str(&format(captures_to_match(&compiled, &captures))?);
            last = full.end();
        }
        Ok(match result {
            Some(mut out) => {
                out.push_str(&text[last..]);
                Arc::new(out)
            }
            // No match means we can avoid new allocation.
            None => text,
        })
    }

    pub(crate) fn compiled_split(
        _regex: &Regex,
        compiled: AnyValue,
        text: Arc<String>,
        _regex_refs: RegexRefs,
    ) -> Arc<Vec<Arc<String>>> {
        Arc::new(
            (cast::<Compiled>(compiled).unwrap().0)
                .split(&text)
                .map(|s| Arc::new(s.to_string()))
                .collect(),
        )
    }

    pub(crate) fn push_code_to(
        _regex: &RegexFormatter,
        out: Arc<RwLock<String>>,
        code: i32,
        _inside_code_set: bool,
    ) {
        let mut out = out.write().unwrap();
        out.push_str(r"\u{");
        // Presume we get only good codes here for now.
        write!(out, "{:X}", code).unwrap();
        out.push('}');
    }
}

#[cfg(not(feature = "regex"))]
mod without_regex {
    use super::*;

    pub(crate) fn compile_formatted(
        _data: &dyn RegexNodeTrait,
        formatted: Arc<String>,
    ) -> temper_core::AnyValue {
        panic!()
    }

    pub(crate) fn compiled_find(
        _regex: &Regex,
        compiled: AnyValue,
        text: Arc<String>,
        begin: usize,
        _regex_refs: RegexRefs,
    ) -> Option<Match> {
        panic!()
    }

    pub(crate) fn compiled_found(_regex: &Regex, compiled: AnyValue, text: Arc<String>) -> bool {
        panic!()
    }

    pub(crate) fn compiled_replace<E>(
        _regex: &Regex,
        compiled: AnyValue,
        text: Arc<String>,
        format: &dyn Fn(Match) -> Result<Arc<String>, E>,
        _regex_refs: RegexRefs,
    ) -> Result<Arc<String>, E> {
        panic!()
    }

    pub(crate) fn compiled_split(
        _regex: &Regex,
        compiled: AnyValue,
        text: Arc<String>,
        _regex_refs: RegexRefs,
    ) -> Arc<Vec<Arc<String>>> {
        panic!()
    }

    pub(crate) fn push_code_to(
        _regex: &RegexFormatter,
        out: Arc<RwLock<String>>,
        code: i32,
        _inside_code_set: bool,
    ) {
        panic!()
    }
}
