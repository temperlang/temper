use std::sync::{Arc, OnceLock, RwLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let c = C::new();
            println!("method1(10)={}", c.method1(10));
            println!("method2(10)={}", c.method2(10));
            let a = Money::new(150);
            let b = Money::new(175);
            println!("plus: {}", a.plus(b.clone()));
            println!("same whole: {}", a.same_whole(b.clone()));
            println!("same rounded: {}", a.same_rounded(b.clone()));
            a.spend_from(b.clone());
            a.spend_from(b.clone());
            println!("b spent: {}", b.times_spent());
            let label = Label::new(1);
            println!("close: {}", label.close(Sink::new(TenSink::new())));
            println!("noted: {}", label.noted("!"));
            let leaf = Tally::new(2, None, Arc::new(vec![]));
            let mid = Tally::new(
                3,
                Some(leaf.clone()),
                Arc::new(vec![leaf.clone(), leaf.clone()]),
            );
            let top = Tally::new(
                1,
                Some(mid.clone()),
                Arc::new(vec![mid.clone(), leaf.clone()]),
            );
            println!("depths: {} {} {}", leaf.0.depth, mid.0.depth, top.0.depth);
            println!("totals: {} {} {}", leaf.total(), mid.total(), top.total());
            Ok(())
        })
        .clone()
}

struct CStruct {}

#[derive(Clone)]
pub(crate) struct C(Arc<CStruct>);

impl C {
    pub fn new() -> C {
        C(Arc::new(CStruct {}))
    }

    fn helper(&self, x: i32) -> i32 {
        x.wrapping_add(1)
    }

    pub fn method1(&self, x: i32) -> i32 {
        self.helper(x.wrapping_mul(2))
    }

    pub fn method2(&self, x: i32) -> i32 {
        self.helper(x.wrapping_mul(3))
    }
}

temper_core::impl_any_value_trait!(C, []);

struct MoneyStruct {
    cents: i32,
    spent: RwLock<i32>,
}

#[derive(Clone)]
pub(crate) struct Money(Arc<MoneyStruct>);

impl Money {
    pub fn new(cents: i32) -> Money {
        Money(Arc::new(MoneyStruct {
            cents,
            spent: RwLock::new(0),
        }))
    }

    fn whole(&self) -> i32 {
        self.0.cents.wrapping_div(100)
    }

    fn rounded(&self) -> i32 {
        self.0.cents.wrapping_add(50).wrapping_div(100)
    }

    pub fn plus(&self, other: Money) -> Money {
        Money::new(self.0.cents.wrapping_add(other.0.cents))
    }

    pub fn same_whole(&self, other: Money) -> bool {
        self.whole() == other.whole()
    }

    pub fn same_rounded(&self, other: Money) -> bool {
        self.rounded() == other.rounded()
    }

    pub fn spend_from(&self, other: Money) {
        *other.0.spent.write().unwrap() = other.spent().wrapping_add(1);
    }

    pub fn times_spent(&self) -> i32 {
        self.spent()
    }

    pub fn to_string(&self) -> Arc<String> {
        Arc::new(format!("{} cents", self.0.cents))
    }

    fn spent(&self) -> i32 {
        *self.0.spent.read().unwrap()
    }
}

impl std::fmt::Display for Money {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(&self.to_string())
    }
}

temper_core::impl_any_value_trait!(Money, []);

pub(crate) trait SinkTrait:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync
{
    #[allow(dead_code)]
    fn clone_boxed(&self) -> Sink;

    fn end(&self) -> i32;
}

#[derive(Clone)]
pub(crate) struct Sink(Arc<dyn SinkTrait>);

impl Sink {
    pub fn new(selfish: impl SinkTrait + 'static) -> Sink {
        Sink(Arc::new(selfish))
    }
}

impl SinkTrait for Sink {
    fn clone_boxed(&self) -> Sink {
        SinkTrait::clone_boxed(&*self.0)
    }

    fn end(&self) -> i32 {
        SinkTrait::end(&*self.0)
    }
}

temper_core::impl_any_value_trait_for_interface!(Sink);

impl std::ops::Deref for Sink {
    type Target = dyn SinkTrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

struct TenSinkStruct {}

#[derive(Clone)]
pub(crate) struct TenSink(Arc<TenSinkStruct>);

impl TenSink {
    pub fn new() -> TenSink {
        TenSink(Arc::new(TenSinkStruct {}))
    }

    pub fn end(&self) -> i32 {
        10
    }
}

impl SinkTrait for TenSink {
    fn clone_boxed(&self) -> Sink {
        Sink::new(self.clone())
    }

    fn end(&self) -> i32 {
        self.end()
    }
}

temper_core::impl_any_value_trait!(TenSink, [Sink]);

struct NoteStruct {
    body: Arc<String>,
}

#[derive(Clone)]
pub(crate) struct Note(Arc<NoteStruct>);

impl Note {
    pub fn new(body: impl ToArcString) -> Note {
        let body = body.to_arc_string();
        Note(Arc::new(NoteStruct { body }))
    }

    pub fn text(&self, more: impl ToArcString) -> Note {
        let more = more.to_arc_string();
        Note::new(Arc::new(format!("{}{more}", self.0.body)))
    }
}

temper_core::impl_any_value_trait!(Note, []);

struct LabelStruct {
    end: i32,
}

#[derive(Clone)]
pub(crate) struct Label(Arc<LabelStruct>);

impl Label {
    pub fn new(end: i32) -> Label {
        Label(Arc::new(LabelStruct { end }))
    }

    pub fn text(&self) -> Arc<String> {
        Arc::new("label".to_string())
    }

    pub fn close(&self, sink: Sink) -> i32 {
        sink.end().wrapping_add(self.0.end)
    }

    pub fn noted(&self, more: impl ToArcString) -> Arc<String> {
        let more = more.to_arc_string();
        Note::new(self.text()).text(more.clone()).0.body.clone()
    }
}

temper_core::impl_any_value_trait!(Label, []);

struct TallyStruct {
    depth: i32,
    size: i32,
}

#[derive(Clone)]
pub(crate) struct Tally(Arc<TallyStruct>);

impl Tally {
    pub fn new(
        size: i32,
        parent: Option<Tally>,
        children: impl temper_core::ToList<Tally>,
    ) -> Tally {
        let children = children.to_list();
        let depth = if let Some(parent) = &parent {
            parent.0.depth.wrapping_add(1)
        } else {
            0
        };
        let mut sum = size;
        {
            let mut i: i32 = 0;
            while i < temper_core::ListedTrait::len(&children) {
                sum = sum.wrapping_add(temper_core::ListedTrait::get(&children, i).0.size);
                i = i.wrapping_add(1);
            }
        }
        Tally(Arc::new(TallyStruct {
            depth,
            size: sum.wrapping_add(depth),
        }))
    }

    pub fn total(&self) -> i32 {
        self.0.size
    }
}

temper_core::impl_any_value_trait!(Tally, []);
