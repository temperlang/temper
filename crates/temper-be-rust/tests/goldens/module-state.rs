use std::sync::{Arc, OnceLock, RwLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            GREETING
                .set(Arc::new("hi".to_string()))
                .unwrap_or_else(|_| panic!());
            *COUNT.write().unwrap() = Some(0);
            LIMIT.set(3).unwrap_or_else(|_| panic!());
            *TOTAL.write().unwrap() = Some(10);
            *TICKS.write().unwrap() = Some(0);
            let mut local: i32 = 1;
            PREFIXES
                .set(Arc::new(vec![Arc::new("p-".to_string())]))
                .unwrap_or_else(|_| panic!());
            PALETTE__WARM
                .set(Palette::named("warm"))
                .unwrap_or_else(|_| panic!());
            FAVORITE.set(Palette::warm()).unwrap_or_else(|_| panic!());
            local = local.wrapping_add(limit());
            println!("{}", report());
            println!("{}", report());
            println!("local {local}");
            println!("{} {}", Palette::warm().0.name, favorite().0.name);
            Ok(())
        })
        .clone()
}

static GREETING: OnceLock<Arc<String>> = OnceLock::new();

pub fn greeting() -> Arc<String> {
    crate::support::initialized(|| GREETING.get()).clone()
}

static COUNT: RwLock<Option<i32>> = RwLock::new(None);

pub(crate) fn count() -> i32 {
    crate::support::initialized(|| *COUNT.read().unwrap())
}

static LIMIT: OnceLock<i32> = OnceLock::new();

pub(crate) fn limit() -> i32 {
    *crate::support::initialized(|| LIMIT.get())
}

static TOTAL: RwLock<Option<i32>> = RwLock::new(None);

pub fn total() -> i32 {
    crate::support::initialized(|| *TOTAL.read().unwrap())
}

static TICKS: RwLock<Option<i32>> = RwLock::new(None);

pub fn ticks() -> i32 {
    crate::support::initialized(|| *TICKS.read().unwrap())
}

static PREFIXES: OnceLock<temper_core::List<Arc<String>>> = OnceLock::new();

pub(crate) fn prefixes() -> temper_core::List<Arc<String>> {
    crate::support::initialized(|| PREFIXES.get()).clone()
}

struct PaletteStruct {
    name: Arc<String>,
}

#[derive(Clone)]
pub struct Palette(Arc<PaletteStruct>);

static PALETTE__WARM: OnceLock<Palette> = OnceLock::new();

impl Palette {
    pub fn new(name: impl ToArcString) -> Palette {
        let name = name.to_arc_string();
        Palette(Arc::new(PaletteStruct { name }))
    }

    pub fn warm() -> Palette {
        crate::support::initialized(|| PALETTE__WARM.get()).clone()
    }

    pub fn named(n: impl ToArcString) -> Palette {
        let n = n.to_arc_string();
        Palette::new(Arc::new(format!(
            "{}{n}",
            temper_core::ListedTrait::get(&prefixes(), 0)
        )))
    }

    pub fn name(&self) -> Arc<String> {
        self.0.name.clone()
    }
}

temper_core::impl_any_value_trait!(Palette, []);

static FAVORITE: OnceLock<Palette> = OnceLock::new();

pub fn favorite() -> Palette {
    crate::support::initialized(|| FAVORITE.get()).clone()
}

pub(crate) fn bump() -> i32 {
    crate::support::initialized(|| {
        let mut count = COUNT.write().unwrap();
        *count = Some((*count)?.wrapping_add(1));
        Some(())
    });
    *TOTAL.write().unwrap() = Some(total().wrapping_add(count()));
    count()
}

pub fn tick() {
    crate::support::initialized(|| {
        let mut ticks = TICKS.write().unwrap();
        *ticks = Some((*ticks)?.wrapping_add(1));
        Some(())
    });
}

pub fn report() -> Arc<String> {
    Arc::new(format!(
        "{} {} of {} {}",
        greeting(),
        bump(),
        limit(),
        total()
    ))
}
