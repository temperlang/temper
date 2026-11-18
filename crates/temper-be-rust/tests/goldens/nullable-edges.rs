use std::sync::{Arc, OnceLock, RwLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let label = Label::new(None::<Arc<String>>);
            println!("{}", label.shown());
            *label.0.text.write().unwrap() = Some(Arc::new("hi".to_string()));
            println!("{}", label.shown());
            println!(
                "{} {} {}",
                same_float(Some(1.5), Some(1.5)),
                same_float(None, Some(0.0)),
                same_float(None, None)
            );
            println!(
                "{} {}",
                same_long(Some(2), Some(2)),
                same_long(Some(2), None)
            );
            println!(
                "{} {} {}",
                longer(Some("41")),
                longer(None::<Arc<String>>),
                longer(Some("x"))
            );
            println!(
                "{}",
                must(Some("ok")).unwrap_or_else(|_| Arc::new("bad".to_string()))
            );
            println!("{} {}", tried("7"), tried("x"));
            println!(
                "{} {}",
                first_code(Part::new(Code::new("a"))).unwrap_or(-1),
                first_code(Part::new(NoCode::new())).unwrap_or(-1)
            );
            Ok(())
        })
        .clone()
}

struct LabelStruct {
    text: RwLock<Option<Arc<String>>>,
}

#[derive(Clone)]
pub struct Label(Arc<LabelStruct>);

impl Label {
    pub fn new(text: Option<impl ToArcString>) -> Label {
        let text = text.map(|x| x.to_arc_string());
        Label(Arc::new(LabelStruct {
            text: RwLock::new(text),
        }))
    }

    pub fn shown(&self) -> Arc<String> {
        let t = self.text();
        t.clone().unwrap_or_else(|| Arc::new("(none)".to_string()))
    }

    pub fn text(&self) -> Option<Arc<String>> {
        self.0.text.read().unwrap().clone()
    }

    pub fn set_text(&self, value: Option<Arc<String>>) {
        *self.0.text.write().unwrap() = value;
    }
}

temper_core::impl_any_value_trait!(Label, []);

pub(crate) fn same_float(a: Option<f64>, b: Option<f64>) -> bool {
    a.zip(b).map_or(a.is_none() && b.is_none(), |(a, b)| {
        temper_core::float64::cmp(a, b) == 0
    })
}

pub(crate) fn same_long(a: Option<i64>, b: Option<i64>) -> bool {
    a == b
}

pub(crate) fn must(s: Option<impl ToArcString>) -> temper_core::Result<Arc<String>> {
    let s = s.map(|x| x.to_arc_string());
    s.clone().ok_or_else(temper_core::Error::new)
}

pub(crate) fn longer(s: Option<impl ToArcString>) -> i32 {
    let s = s.map(|x| x.to_arc_string());
    (|| {
        let t = must(s.clone())?;
        let n = temper_core::string::to_int(&t, None)?;
        Ok::<_, temper_core::Error>(n.wrapping_add(1))
    })()
    .unwrap_or(0)
}

pub(crate) fn tried(s: impl ToArcString) -> i32 {
    let s = s.to_arc_string();
    {
        println!("trying {s}");
        temper_core::string::to_int(&s, None)
    }
    .unwrap_or(-1)
}

pub(crate) trait PartTrait:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync
{
    #[allow(dead_code)]
    fn clone_boxed(&self) -> Part;
}

#[derive(Clone)]
pub(crate) struct Part(Arc<dyn PartTrait>);

impl Part {
    pub fn new(selfish: impl PartTrait + 'static) -> Part {
        Part(Arc::new(selfish))
    }
}

impl PartTrait for Part {
    fn clone_boxed(&self) -> Part {
        PartTrait::clone_boxed(&*self.0)
    }
}

temper_core::impl_any_value_trait_for_interface!(Part);

impl std::ops::Deref for Part {
    type Target = dyn PartTrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

struct CodeStruct {
    text: Arc<String>,
}

#[derive(Clone)]
pub(crate) struct Code(Arc<CodeStruct>);

impl Code {
    pub fn new(text: impl ToArcString) -> Code {
        let text = text.to_arc_string();
        Code(Arc::new(CodeStruct { text }))
    }
}

impl PartTrait for Code {
    fn clone_boxed(&self) -> Part {
        Part::new(self.clone())
    }
}

temper_core::impl_any_value_trait!(Code, [Part]);

struct NoCodeStruct {}

#[derive(Clone)]
pub(crate) struct NoCode(Arc<NoCodeStruct>);

impl NoCode {
    pub fn new() -> NoCode {
        NoCode(Arc::new(NoCodeStruct {}))
    }
}

impl PartTrait for NoCode {
    fn clone_boxed(&self) -> Part {
        Part::new(self.clone())
    }
}

temper_core::impl_any_value_trait!(NoCode, [Part]);

pub(crate) fn first_code(attempt: Part) -> temper_core::Result<i32> {
    Ok({
        let attempt2 = (|| {
            Ok::<_, temper_core::Error>(temper_core::string::get(
                &temper_core::cast::<Code>(attempt.clone())
                    .ok_or_else(temper_core::Error::new)?
                    .0
                    .text,
                0usize,
            ))
        })();
        if let Ok(v) = attempt2 {
            v
        } else {
            println!("no code");
            return Err(temper_core::Error::new());
        }
    })
}
