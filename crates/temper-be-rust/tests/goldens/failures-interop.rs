use std::sync::{Arc, OnceLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            println!("{} {} {}", lookup("a")?, sign("-")?, setting("a")?);
            println!("{} {} {}", mixed("a", ""), mixed("z", "+"), mixed("a", "-"));
            println!(
                "{}; {}; {}",
                explained("port"),
                explained("a"),
                sign_message("x")
            );
            println!(
                "{}; {} {}",
                early_cause("soon"),
                first_missing(Arc::new(vec![
                    Arc::new("a".to_string()),
                    Arc::new("b".to_string()),
                    Arc::new("c".to_string()),
                ])),
                first_missing(Arc::new(vec![Arc::new("a".to_string())]))
            );
            println!(
                "{} {} {}",
                first_sign(Arc::new(vec![
                    Arc::new(String::new()),
                    Arc::new("+".to_string()),
                ])),
                first_sign(Arc::new(vec![
                    Arc::new(String::new()),
                    Arc::new("?".to_string()),
                ])),
                first_sign(Arc::new(vec![]))
            );
            Ok(())
        })
        .clone()
}

#[derive(Clone, Debug)]
#[non_exhaustive]
pub enum FailuresInteropError {
    NotFound(NotFound),
    SignError(SignError),
    LookupFailed(LookupFailed),
}

temper_core::impl_failure_enum!(
    FailuresInteropError,
    NotFound(NotFound),
    SignError(SignError),
    LookupFailed(LookupFailed)
);

temper_core::impl_failure_from!(FailuresInteropError, SignError, BadSign, EmptyText);

struct NotFoundStruct {
    key: Arc<String>,
    cause: temper_core::Cause,
}

#[derive(Clone)]
pub struct NotFound(Arc<NotFoundStruct>);

impl NotFound {
    pub fn new(key: impl ToArcString) -> NotFound {
        let key = key.to_arc_string();
        NotFound(Arc::new(NotFoundStruct {
            key,
            cause: temper_core::Cause::new(),
        }))
    }

    pub fn message(&self) -> Arc<String> {
        Arc::new(format!("not found (key: {})", self.0.key))
    }

    pub fn key(&self) -> Arc<String> {
        self.0.key.clone()
    }
}

temper_core::impl_failure!(NotFound, message, cause);

temper_core::impl_any_value_trait!(NotFound, [FailuresInteropError]);

#[derive(Clone, Debug)]
pub enum SignError {
    BadSign(BadSign),
    EmptyText(EmptyText),
}

temper_core::impl_failure_enum!(SignError, BadSign(BadSign), EmptyText(EmptyText));

struct BadSignStruct {
    text: Arc<String>,
    cause: temper_core::Cause,
}

#[derive(Clone)]
pub struct BadSign(Arc<BadSignStruct>);

impl BadSign {
    pub fn new(text: impl ToArcString) -> BadSign {
        let text = text.to_arc_string();
        BadSign(Arc::new(BadSignStruct {
            text,
            cause: temper_core::Cause::new(),
        }))
    }

    pub fn message(&self) -> Arc<String> {
        Arc::new(format!("bad sign (text: {})", self.0.text))
    }

    pub fn text(&self) -> Arc<String> {
        self.0.text.clone()
    }
}

temper_core::impl_failure!(BadSign, message, cause);

temper_core::impl_any_value_trait!(BadSign, [SignError, FailuresInteropError]);

struct EmptyTextStruct {
    cause: temper_core::Cause,
}

#[derive(Clone)]
pub struct EmptyText(Arc<EmptyTextStruct>);

impl EmptyText {
    #[allow(clippy::new_without_default)]
    pub fn new() -> EmptyText {
        EmptyText(Arc::new(EmptyTextStruct {
            cause: temper_core::Cause::new(),
        }))
    }

    pub fn message(&self) -> Arc<String> {
        Arc::new("no text".to_string())
    }
}

temper_core::impl_failure!(EmptyText, message, cause);

temper_core::impl_any_value_trait!(EmptyText, [SignError, FailuresInteropError]);

struct LookupFailedStruct {
    name: Arc<String>,
    cause: temper_core::Cause,
}

#[derive(Clone)]
pub struct LookupFailed(Arc<LookupFailedStruct>);

impl LookupFailed {
    pub fn new(name: impl ToArcString) -> LookupFailed {
        let name = name.to_arc_string();
        LookupFailed(Arc::new(LookupFailedStruct {
            name,
            cause: temper_core::Cause::new(),
        }))
    }

    pub fn message(&self) -> Arc<String> {
        Arc::new(format!("lookup failed (name: {})", self.0.name))
    }

    pub fn name(&self) -> Arc<String> {
        self.0.name.clone()
    }
}

temper_core::impl_failure!(LookupFailed, message, cause);

temper_core::impl_any_value_trait!(LookupFailed, [FailuresInteropError]);

pub fn lookup(key: impl ToArcString) -> Result<i32, NotFound> {
    let key = key.to_arc_string();
    if key.as_str() == "a" {
        return Ok(1);
    }
    Err(NotFound::new(key.clone()))
}

pub fn sign(text: impl ToArcString) -> Result<i32, SignError> {
    let text = text.to_arc_string();
    if text.as_str() == "+" {
        Ok(1)
    } else if text.as_str() == "-" {
        Ok(-1)
    } else if text.as_str() == "" {
        Err(EmptyText::new().into())
    } else {
        Err(BadSign::new(text.clone()).into())
    }
}

pub fn setting(name: impl ToArcString) -> Result<i32, LookupFailed> {
    let name = name.to_arc_string();
    lookup(name.clone()).map_err(|failure| {
        temper_core::with_cause(LookupFailed::new(name.clone()), failure.clone())
    })
}

pub fn made_before(name: impl ToArcString) -> Result<i32, LookupFailed> {
    let name = name.to_arc_string();
    let failure = LookupFailed::new(name.clone());
    lookup(name.clone())
        .map_err(|failure2| temper_core::with_cause(failure.clone(), failure2.clone()))
}

pub fn thrown_again(name: impl ToArcString) -> Result<i32, LookupFailed> {
    let name = name.to_arc_string();
    setting(name.clone()).or_else(|e| {
        lookup("q").map_err(|failure| temper_core::with_cause(e.clone(), failure.clone()))
    })
}

pub fn risky(n: i32) -> Result<i32, NotFound> {
    if n > 1 {
        panic!();
    }
    lookup("a")
}

pub fn explained(name: impl ToArcString) -> Arc<String> {
    let name = name.to_arc_string();
    (|| Ok::<_, LookupFailed>(Arc::new(setting(name.clone())?.to_string())))().unwrap_or_else(|e| {
        let cause = temper_core::Failure::cause(&e);
        let why = if let Some(cause) = &cause {
            cause.message()
        } else {
            Arc::new("nothing".to_string())
        };
        Arc::new(format!("{} for {}, from {why}", e.message(), e.0.name))
    })
}

pub fn sign_message(text: impl ToArcString) -> Arc<String> {
    let text = text.to_arc_string();
    (|| Ok::<_, SignError>(Arc::new(sign(text.clone())?.to_string())))()
        .unwrap_or_else(|e| e.message())
}

pub fn early_cause(name: impl ToArcString) -> Arc<String> {
    let name = name.to_arc_string();
    (|| Ok::<_, LookupFailed>(Arc::new(made_before(name.clone())?.to_string())))().unwrap_or_else(
        |e| {
            let cause = temper_core::Failure::cause(&e);
            if let Some(cause) = &cause {
                cause.message()
            } else {
                Arc::new("nothing".to_string())
            }
        },
    )
}

pub fn sign_name(e: SignError) -> Arc<String> {
    if temper_core::is::<BadSign>(e.clone()) {
        Arc::new("bad sign".to_string())
    } else if temper_core::is::<EmptyText>(e.clone()) {
        Arc::new("empty text".to_string())
    } else {
        panic!();
    }
}

pub fn mixed(key: impl ToArcString, text: impl ToArcString) -> i32 {
    let key = key.to_arc_string();
    let text = text.to_arc_string();
    (|| {
        Ok::<_, FailuresInteropError>(
            lookup(key.clone())?.wrapping_add(10i32.wrapping_div(sign(text.clone())?)),
        )
    })()
    .unwrap_or(-1)
}

pub fn first_sign(texts: impl temper_core::ToList<Arc<String>>) -> Arc<String> {
    let texts = texts.to_list();
    {
        let length = temper_core::ListedTrait::len(&texts);
        let mut i: i32 = 0;
        while i < length {
            let t = temper_core::ListedTrait::get(&texts, i);
            i = i.wrapping_add(1);
            let s: i32;
            let mut caught: Option<SignError> = None;
            let mut held: Option<i32> = None;
            if let Err(failure) = (|| {
                held = Some(sign(t.clone())?);
                Ok::<_, SignError>(())
            })() {
                caught = Some(failure.clone());
            }
            if let Some(held) = held {
                s = held;
            } else {
                let e = caught.clone().unwrap();
                if temper_core::is::<BadSign>(e.clone()) {
                    return Arc::new(format!(
                        "bad {}",
                        temper_core::cast::<BadSign>(e.clone()).unwrap().0.text
                    ));
                }
                continue;
            }
            return Arc::new(s.to_string());
        }
    }
    Arc::new("none".to_string())
}

pub fn first_missing(keys: impl temper_core::ToList<Arc<String>>) -> Arc<String> {
    let keys = keys.to_list();
    {
        let length = temper_core::ListedTrait::len(&keys);
        let mut i: i32 = 0;
        while i < length {
            let k = temper_core::ListedTrait::get(&keys, i);
            i = i.wrapping_add(1);
            let n: i32;
            let mut caught: Option<NotFound> = None;
            let mut held: Option<i32> = None;
            if let Err(failure) = (|| {
                held = Some(lookup(k.clone())?);
                Ok::<_, NotFound>(())
            })() {
                caught = Some(failure.clone());
            }
            if let Some(held) = held {
                n = held;
            } else {
                let e = caught.clone().unwrap();
                return e.0.key.clone();
            }
            if n < 0 {
                break;
            }
        }
    }
    Arc::new("none".to_string())
}
