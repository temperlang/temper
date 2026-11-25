use std::sync::{Arc, OnceLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let found = lookup("a").unwrap_or(-1);
            let missing = lookup("z").unwrap_or(-1);
            println!("{found} {missing}");
            let n = lookup("q").unwrap_or_else(|e| {
                println!("not found: {}", e.0.key);
                0
            });
            let m = lookup("b").unwrap_or(-1);
            println!("{n} {m}");
            println!(
                "{} {} {}",
                shown("a", "b"),
                shown("a", "x"),
                shown("y", "z")
            );
            {
                let list: temper_core::List<Arc<String>> = Arc::new(vec![
                    Arc::new("+".to_string()),
                    Arc::new("-".to_string()),
                    Arc::new(String::new()),
                    Arc::new("x".to_string()),
                ]);
                let length = temper_core::ListedTrait::len(&list);
                let mut i: i32 = 0;
                while i < length {
                    let t = temper_core::ListedTrait::get(&list, i);
                    i = i.wrapping_add(1);
                    let s: i32;
                    let mut caught: Option<SignError> = None;
                    let mut held: Option<i32> = None;
                    if let Err(failure) = (|| {
                        held = Some(parse_sign(t.clone())?);
                        Ok::<_, SignError>(())
                    })() {
                        caught = Some(failure.clone());
                    }
                    if let Some(held) = held {
                        s = held;
                    } else {
                        let e2 = caught.clone().unwrap();
                        println!("{}", describe(e2.clone()));
                        continue;
                    }
                    println!("{s}");
                }
            }
            let code = parse_sign("?").unwrap_or_else(|e3| {
                if temper_core::is::<EmptyText>(e3.clone()) {
                    0
                } else if temper_core::is::<BadSign>(e3.clone()) {
                    9
                } else {
                    panic!()
                }
            });
            println!("{code}");
            let mut kept = NotFound::new("none");
            let v = lookup("k").unwrap_or_else(|e4| {
                kept = e4.clone();
                -1
            });
            let problems: temper_core::List<NotFound> =
                Arc::new(vec![NotFound::new("x"), kept.clone()]);
            println!(
                "{v} {}",
                temper_core::listed::join(
                    &problems,
                    Arc::new(",".to_string()),
                    &(|p: NotFound| p.0.key.clone()),
                )
            );
            let _w = rethrown(kept.clone()).unwrap_or_else(|e5| {
                println!("again {}", e5.0.key);
                0
            });
            let port = setting("port").unwrap_or_else(|e6| {
                println!("{}", e6.0.setting);
                -1
            });
            let first = setting("a").unwrap_or(-1);
            println!("{port} {first}");
            println!("{} {}", pair("a", "z"), pair("z", "a"));
            let mut total: i32 = 0;
            {
                let list2: temper_core::List<Arc<String>> = Arc::new(vec![
                    Arc::new("a".to_string()),
                    Arc::new("b".to_string()),
                    Arc::new("c".to_string()),
                    Arc::new("a".to_string()),
                ]);
                let length2 = temper_core::ListedTrait::len(&list2);
                let mut i2: i32 = 0;
                while i2 < length2 {
                    let k = temper_core::ListedTrait::get(&list2, i2);
                    i2 = i2.wrapping_add(1);
                    total = total.wrapping_add({
                        let t2: i32;
                        let mut held2: Option<i32> = None;
                        let _ = (|| {
                            held2 = Some(lookup(k.clone())?);
                            Ok::<_, NotFound>(())
                        })();
                        if let Some(held2) = held2 {
                            t2 = held2;
                        } else {
                            break;
                        }
                        t2
                    });
                }
            }
            println!("{total}");
            Ok(())
        })
        .clone()
}

#[derive(Clone, Debug)]
pub(crate) enum FailuresError {
    NotFound(NotFound),
    ConfigError(ConfigError),
}

temper_core::impl_failure_enum!(FailuresError, NotFound(NotFound), ConfigError(ConfigError));

struct NotFoundStruct {
    key: Arc<String>,
    cause: temper_core::Cause,
}

#[derive(Clone)]
pub(crate) struct NotFound(Arc<NotFoundStruct>);

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
}

temper_core::impl_failure!(NotFound, message, cause);

temper_core::impl_any_value_trait!(NotFound, [FailuresError]);

pub(crate) fn lookup(key: impl ToArcString) -> Result<i32, NotFound> {
    let key = key.to_arc_string();
    if key.as_str() == "a" {
        return Ok(1);
    }
    if key.as_str() == "b" {
        return Ok(2);
    }
    Err(NotFound::new(key.clone()))
}

pub(crate) fn sum(a: impl ToArcString, b: impl ToArcString) -> Result<i32, NotFound> {
    let a = a.to_arc_string();
    let b = b.to_arc_string();
    let x = lookup(a.clone())?;
    let y = lookup(b.clone())?;
    Ok(x.wrapping_add(y))
}

pub(crate) fn shown(a: impl ToArcString, b: impl ToArcString) -> Arc<String> {
    let a = a.to_arc_string();
    let b = b.to_arc_string();
    let total: i32;
    let mut caught: Option<NotFound> = None;
    let mut held: Option<i32> = None;
    if let Err(failure) = (|| {
        held = Some(sum(a.clone(), b.clone())?);
        Ok::<_, NotFound>(())
    })() {
        caught = Some(failure.clone());
    }
    if let Some(held) = held {
        total = held;
    } else {
        let e = caught.clone().unwrap();
        return Arc::new(format!("no {}", e.0.key));
    }
    Arc::new(total.to_string())
}

#[derive(Clone, Debug)]
pub(crate) enum SignError {
    EmptyText(EmptyText),
    BadSign(BadSign),
}

temper_core::impl_failure_enum!(SignError, EmptyText(EmptyText), BadSign(BadSign));

struct EmptyTextStruct {
    cause: temper_core::Cause,
}

#[derive(Clone)]
pub(crate) struct EmptyText(Arc<EmptyTextStruct>);

impl EmptyText {
    pub fn new() -> EmptyText {
        EmptyText(Arc::new(EmptyTextStruct {
            cause: temper_core::Cause::new(),
        }))
    }

    pub fn message(&self) -> Arc<String> {
        Arc::new("empty text".to_string())
    }
}

temper_core::impl_failure!(EmptyText, message, cause);

temper_core::impl_any_value_trait!(EmptyText, [SignError]);

struct BadSignStruct {
    text: Arc<String>,
    cause: temper_core::Cause,
}

#[derive(Clone)]
pub(crate) struct BadSign(Arc<BadSignStruct>);

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
}

temper_core::impl_failure!(BadSign, message, cause);

temper_core::impl_any_value_trait!(BadSign, [SignError]);

pub(crate) fn sign(text: impl ToArcString) -> Result<i32, BadSign> {
    let text = text.to_arc_string();
    if text.as_str() == "+" {
        Ok(1)
    } else if text.as_str() == "-" {
        Ok(-1)
    } else {
        Err(BadSign::new(text.clone()))
    }
}

pub(crate) fn parse_sign(text: impl ToArcString) -> Result<i32, SignError> {
    let text = text.to_arc_string();
    if text.as_str() == "" {
        return Err(EmptyText::new().into());
    }
    Ok(sign(text.clone())?)
}

pub(crate) fn describe(e: SignError) -> Arc<String> {
    if temper_core::is::<EmptyText>(e.clone()) {
        Arc::new("empty".to_string())
    } else if temper_core::is::<BadSign>(e.clone()) {
        Arc::new(format!(
            "bad sign {}",
            temper_core::cast::<BadSign>(e.clone()).unwrap().0.text
        ))
    } else {
        panic!();
    }
}

pub(crate) fn rethrown(p: NotFound) -> Result<i32, NotFound> {
    Err(p.clone())
}

struct ConfigErrorStruct {
    setting: Arc<String>,
    cause: temper_core::Cause,
}

#[derive(Clone)]
pub(crate) struct ConfigError(Arc<ConfigErrorStruct>);

impl ConfigError {
    pub fn new(setting: impl ToArcString) -> ConfigError {
        let setting = setting.to_arc_string();
        ConfigError(Arc::new(ConfigErrorStruct {
            setting,
            cause: temper_core::Cause::new(),
        }))
    }

    pub fn message(&self) -> Arc<String> {
        Arc::new(format!("config error (setting: {})", self.0.setting))
    }
}

temper_core::impl_failure!(ConfigError, message, cause);

temper_core::impl_any_value_trait!(ConfigError, [FailuresError]);

pub(crate) fn setting(name: impl ToArcString) -> Result<i32, ConfigError> {
    let name = name.to_arc_string();
    lookup(name.clone()).map_err(|e| {
        temper_core::with_cause(
            ConfigError::new(Arc::new(format!("{name}: missing {}", e.0.key))),
            e.clone(),
        )
    })
}

pub(crate) fn pair(a: impl ToArcString, b: impl ToArcString) -> Arc<String> {
    let a = a.to_arc_string();
    let b = b.to_arc_string();
    (|| {
        let x = lookup(a.clone())?;
        let y = lookup(b.clone()).unwrap_or(0);
        Ok::<_, NotFound>(Arc::new(format!("{x}+{y}")))
    })()
    .unwrap_or_else(|e| Arc::new(format!("outer {}", e.0.key)))
}
