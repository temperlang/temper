use std::sync::{Arc, OnceLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            println!("{}", describe(None));
            println!("{}", describe(Some(41)));
            println!("{}", {
                let subject = first_word(Some("hi"));
                subject
                    .clone()
                    .unwrap_or_else(|| Arc::new("nothing".to_string()))
            });
            println!("{}", {
                let subject2 = first_word(Some(""));
                subject2
                    .clone()
                    .unwrap_or_else(|| Arc::new("nothing".to_string()))
            });
            println!(
                "{} {} {} {}",
                same(None::<Arc<String>>, None::<Arc<String>>),
                same(Some("a"), None::<Arc<String>>),
                same(None::<Arc<String>>, Some("a")),
                same(Some("a"), Some("a"))
            );
            println!(
                "{} {} {}",
                same_int(Some(1), Some(2)),
                same_int(Some(3), Some(3)),
                same_int(None, Some(3))
            );
            println!("{} {}", parse("12"), parse("x"));
            println!("{} {} {}", halved("12", 2), halved("x", 2), halved("12", 0));
            println!("{}", checked(7));
            println!("{} {}", or_zero(None), or_zero(Some(5)));
            varying();
            let list = Node::new(1, Some(Node::new(2, Some(Node::new(3, None)))));
            println!("{}", list.last());
            let tail = list.0.next.clone();
            if tail.is_some() {
                println!("{}", tail.as_ref().unwrap().0.value);
            }
            if positive(-1).is_err() {
                println!("negative");
            }
            let fallback = positive(-2).unwrap_or(0);
            println!("{fallback}");
            println!(
                "{} {} {}",
                reason(1, None::<Arc<String>>),
                reason(-1, None::<Arc<String>>),
                reason(-1, Some("bad"))
            );
            Ok(())
        })
        .clone()
}

struct NodeStruct {
    value: i32,
    next: Option<Node>,
}

#[derive(Clone)]
pub struct Node(Arc<NodeStruct>);

impl Node {
    pub fn new(value: i32, next: Option<Node>) -> Node {
        Node(Arc::new(NodeStruct { value, next }))
    }

    pub fn last(&self) -> i32 {
        let n = self.0.next.clone();
        if let Some(n) = &n {
            n.last()
        } else {
            self.0.value
        }
    }

    pub fn value(&self) -> i32 {
        self.0.value
    }

    pub fn next(&self) -> Option<Node> {
        self.0.next.clone()
    }
}

temper_core::impl_any_value_trait!(Node, []);

pub fn describe(x: Option<i32>) -> Arc<String> {
    if x.is_none() {
        return Arc::new("none".to_string());
    }
    Arc::new(format!("some {}", x.unwrap().wrapping_add(1)))
}

pub fn first_word(s: Option<impl ToArcString>) -> Option<Arc<String>> {
    let s = s.map(|x| x.to_arc_string());
    if s.is_some() && s.as_ref().unwrap().as_str() != "" {
        s.clone()
    } else {
        None
    }
}

pub(crate) fn same(a: Option<impl ToArcString>, b: Option<impl ToArcString>) -> bool {
    let a = a.map(|x| x.to_arc_string());
    let b = b.map(|x| x.to_arc_string());
    a == b
}

pub(crate) fn same_int(a: Option<i32>, b: Option<i32>) -> bool {
    a == b
}

pub(crate) fn parse(s: impl ToArcString) -> i32 {
    let s = s.to_arc_string();
    temper_core::string::to_int(&s, None).unwrap_or(-1)
}

pub(crate) fn halved(s: impl ToArcString, d: i32) -> i32 {
    let s = s.to_arc_string();
    (|| temper_core::int_div(temper_core::string::to_int(&s, None)?, d))().unwrap_or(-1)
}

pub(crate) fn positive(n: i32) -> temper_core::Result<i32> {
    if n < 0 {
        return Err(temper_core::Error::new());
    }
    Ok(n)
}

pub(crate) fn checked(n: i32) -> i32 {
    positive(n).unwrap_or_else(|_| panic!())
}

pub fn stops() -> i32 {
    {
        panic!();
    }
}

pub(crate) fn or_zero(x: Option<i32>) -> i32 {
    x.unwrap_or(0)
}

pub(crate) fn reason(n: i32, why: Option<impl ToArcString>) -> Arc<String> {
    let why = why.map(|x| x.to_arc_string());
    (|| {
        positive(n)?;
        Ok::<_, temper_core::Error>(Arc::new("ok".to_string()))
    })()
    .unwrap_or_else(|_| why.clone().unwrap_or_else(|| Arc::new("fails".to_string())))
}

pub(crate) fn varying() {
    let mut maybe: Option<i32> = None;
    println!("{}", maybe.is_none());
    maybe = Some(3);
    println!("{}", maybe.is_some());
}

pub mod builders {
    use super::*;

    #[derive(Clone)]
    pub struct NodeBuilder {
        pub value: i32,
        pub next: Option<Node>,
    }

    impl NodeBuilder {
        pub fn build(self) -> Node {
            Node::new(self.value, self.next)
        }
    }
}
