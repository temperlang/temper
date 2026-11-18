use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let slippy = Slippy::new();
            let slappy = Slappy::new(slippy.clone());
            println!("{}", slippy.thingums());
            println!("{}", slappy.thingums());
            println!("{}", slippy.thingumses());
            println!("{}", slippy.check_overridden());
            println!("{}", slippy.check_own());
            println!("{}", CTrait::a(&D::new()));
            println!("{}", CTrait::thing(&D::new()));
            println!("{}", CSplitTrait::a(&DSplit::new()));
            println!("{}", B1DeepTrait::a(&DDeep::new()));
            println!("{}", ADeepTrait::a(&EDeep::new()));
            Ok(())
        })
        .clone()
}

pub(crate) trait SlappyTrait:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync
{
    #[allow(dead_code)]
    fn clone_boxed(&self) -> Slappy;

    fn thingums(&self) -> i32;
}

#[derive(Clone)]
pub(crate) struct Slappy(Arc<dyn SlappyTrait>);

impl Slappy {
    pub fn new(selfish: impl SlappyTrait + 'static) -> Slappy {
        Slappy(Arc::new(selfish))
    }
}

impl SlappyTrait for Slappy {
    fn clone_boxed(&self) -> Slappy {
        SlappyTrait::clone_boxed(&*self.0)
    }

    fn thingums(&self) -> i32 {
        SlappyTrait::thingums(&*self.0)
    }
}

temper_core::impl_any_value_trait_for_interface!(Slappy);

impl std::ops::Deref for Slappy {
    type Target = dyn SlappyTrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

struct SlippyStruct {}

#[derive(Clone)]
pub(crate) struct Slippy(Arc<SlippyStruct>);

impl Slippy {
    pub fn new() -> Slippy {
        Slippy(Arc::new(SlippyStruct {}))
    }

    pub fn check_overridden(&self) -> bool {
        self.thingums() == 1
    }

    pub fn check_own(&self) -> bool {
        self.thingumses() == 2
    }

    pub fn thingums(&self) -> i32 {
        3
    }

    pub fn thingumses(&self) -> i32 {
        4
    }
}

impl SlappyTrait for Slippy {
    fn clone_boxed(&self) -> Slappy {
        Slappy::new(self.clone())
    }

    fn thingums(&self) -> i32 {
        self.thingums()
    }
}

temper_core::impl_any_value_trait!(Slippy, [Slappy]);

pub(crate) trait ATrait:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync
{
    #[allow(dead_code)]
    fn clone_boxed(&self) -> A;

    fn a(&self) -> Arc<String> {
        Arc::new("A wins!".to_string())
    }

    fn thing(&self) -> Arc<String> {
        Arc::new("A has thing!".to_string())
    }
}

#[derive(Clone)]
pub(crate) struct A(Arc<dyn ATrait>);

impl A {
    pub fn new(selfish: impl ATrait + 'static) -> A {
        A(Arc::new(selfish))
    }
}

impl ATrait for A {
    fn clone_boxed(&self) -> A {
        ATrait::clone_boxed(&*self.0)
    }

    fn a(&self) -> Arc<String> {
        ATrait::a(&*self.0)
    }

    fn thing(&self) -> Arc<String> {
        ATrait::thing(&*self.0)
    }
}

temper_core::impl_any_value_trait_for_interface!(A);

impl std::ops::Deref for A {
    type Target = dyn ATrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

pub(crate) trait BTrait:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync + ATrait
{
    #[allow(dead_code)]
    fn clone_boxed(&self) -> B;
}

#[derive(Clone)]
pub(crate) struct B(Arc<dyn BTrait>);

impl B {
    pub fn new(selfish: impl BTrait + 'static) -> B {
        B(Arc::new(selfish))
    }
}

impl BTrait for B {
    fn clone_boxed(&self) -> B {
        BTrait::clone_boxed(&*self.0)
    }
}

impl ATrait for B {
    fn clone_boxed(&self) -> A {
        ATrait::clone_boxed(&*self.0)
    }

    fn a(&self) -> Arc<String> {
        ATrait::a(&*self.0)
    }

    fn thing(&self) -> Arc<String> {
        ATrait::thing(&*self.0)
    }
}

temper_core::impl_any_value_trait_for_interface!(B);

impl std::ops::Deref for B {
    type Target = dyn BTrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

pub(crate) trait CTrait:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync + ATrait
{
    #[allow(dead_code)]
    fn clone_boxed(&self) -> C;

    fn a(&self) -> Arc<String> {
        Arc::new("C wins!".to_string())
    }

    fn thing(&self) -> Arc<String> {
        Arc::new("C has thing!".to_string())
    }
}

#[derive(Clone)]
pub(crate) struct C(Arc<dyn CTrait>);

impl C {
    pub fn new(selfish: impl CTrait + 'static) -> C {
        C(Arc::new(selfish))
    }
}

impl CTrait for C {
    fn clone_boxed(&self) -> C {
        CTrait::clone_boxed(&*self.0)
    }

    fn a(&self) -> Arc<String> {
        CTrait::a(&*self.0)
    }

    fn thing(&self) -> Arc<String> {
        CTrait::thing(&*self.0)
    }
}

impl ATrait for C {
    fn clone_boxed(&self) -> A {
        ATrait::clone_boxed(&*self.0)
    }

    fn a(&self) -> Arc<String> {
        ATrait::a(&*self.0)
    }

    fn thing(&self) -> Arc<String> {
        ATrait::thing(&*self.0)
    }
}

temper_core::impl_any_value_trait_for_interface!(C);

impl std::ops::Deref for C {
    type Target = dyn CTrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

struct DStruct {}

#[derive(Clone)]
pub(crate) struct D(Arc<DStruct>);

impl D {
    pub fn new() -> D {
        D(Arc::new(DStruct {}))
    }
}

impl BTrait for D {
    fn clone_boxed(&self) -> B {
        B::new(self.clone())
    }
}

impl CTrait for D {
    fn clone_boxed(&self) -> C {
        C::new(self.clone())
    }
}

impl ATrait for D {
    fn clone_boxed(&self) -> A {
        A::new(self.clone())
    }

    fn a(&self) -> Arc<String> {
        CTrait::a(self)
    }

    fn thing(&self) -> Arc<String> {
        CTrait::thing(self)
    }
}

temper_core::impl_any_value_trait!(D, [B, C, A]);

pub(crate) trait ASplitTrait:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync
{
    #[allow(dead_code)]
    fn clone_boxed(&self) -> ASplit;

    fn a(&self) -> Arc<String> {
        Arc::new("A wins!".to_string())
    }
}

#[derive(Clone)]
pub(crate) struct ASplit(Arc<dyn ASplitTrait>);

impl ASplit {
    pub fn new(selfish: impl ASplitTrait + 'static) -> ASplit {
        ASplit(Arc::new(selfish))
    }
}

impl ASplitTrait for ASplit {
    fn clone_boxed(&self) -> ASplit {
        ASplitTrait::clone_boxed(&*self.0)
    }

    fn a(&self) -> Arc<String> {
        ASplitTrait::a(&*self.0)
    }
}

temper_core::impl_any_value_trait_for_interface!(ASplit);

impl std::ops::Deref for ASplit {
    type Target = dyn ASplitTrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

pub(crate) trait BSplitTrait:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync + ASplitTrait
{
    #[allow(dead_code)]
    fn clone_boxed(&self) -> BSplit;
}

#[derive(Clone)]
pub(crate) struct BSplit(Arc<dyn BSplitTrait>);

impl BSplit {
    pub fn new(selfish: impl BSplitTrait + 'static) -> BSplit {
        BSplit(Arc::new(selfish))
    }
}

impl BSplitTrait for BSplit {
    fn clone_boxed(&self) -> BSplit {
        BSplitTrait::clone_boxed(&*self.0)
    }
}

impl ASplitTrait for BSplit {
    fn clone_boxed(&self) -> ASplit {
        ASplitTrait::clone_boxed(&*self.0)
    }

    fn a(&self) -> Arc<String> {
        ASplitTrait::a(&*self.0)
    }
}

temper_core::impl_any_value_trait_for_interface!(BSplit);

impl std::ops::Deref for BSplit {
    type Target = dyn BSplitTrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

pub(crate) trait CSplitTrait:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync
{
    #[allow(dead_code)]
    fn clone_boxed(&self) -> CSplit;

    fn a(&self) -> Arc<String> {
        Arc::new("C wins!".to_string())
    }
}

#[derive(Clone)]
pub(crate) struct CSplit(Arc<dyn CSplitTrait>);

impl CSplit {
    pub fn new(selfish: impl CSplitTrait + 'static) -> CSplit {
        CSplit(Arc::new(selfish))
    }
}

impl CSplitTrait for CSplit {
    fn clone_boxed(&self) -> CSplit {
        CSplitTrait::clone_boxed(&*self.0)
    }

    fn a(&self) -> Arc<String> {
        CSplitTrait::a(&*self.0)
    }
}

temper_core::impl_any_value_trait_for_interface!(CSplit);

impl std::ops::Deref for CSplit {
    type Target = dyn CSplitTrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

struct DSplitStruct {}

#[derive(Clone)]
pub(crate) struct DSplit(Arc<DSplitStruct>);

impl DSplit {
    pub fn new() -> DSplit {
        DSplit(Arc::new(DSplitStruct {}))
    }
}

impl BSplitTrait for DSplit {
    fn clone_boxed(&self) -> BSplit {
        BSplit::new(self.clone())
    }
}

impl CSplitTrait for DSplit {
    fn clone_boxed(&self) -> CSplit {
        CSplit::new(self.clone())
    }
}

impl ASplitTrait for DSplit {
    fn clone_boxed(&self) -> ASplit {
        ASplit::new(self.clone())
    }

    fn a(&self) -> Arc<String> {
        CSplitTrait::a(self)
    }
}

temper_core::impl_any_value_trait!(DSplit, [BSplit, CSplit, ASplit]);

pub(crate) trait ADeepTrait:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync
{
    #[allow(dead_code)]
    fn clone_boxed(&self) -> ADeep;

    fn a(&self) -> Arc<String> {
        Arc::new("A wins!".to_string())
    }
}

#[derive(Clone)]
pub(crate) struct ADeep(Arc<dyn ADeepTrait>);

impl ADeep {
    pub fn new(selfish: impl ADeepTrait + 'static) -> ADeep {
        ADeep(Arc::new(selfish))
    }
}

impl ADeepTrait for ADeep {
    fn clone_boxed(&self) -> ADeep {
        ADeepTrait::clone_boxed(&*self.0)
    }

    fn a(&self) -> Arc<String> {
        ADeepTrait::a(&*self.0)
    }
}

temper_core::impl_any_value_trait_for_interface!(ADeep);

impl std::ops::Deref for ADeep {
    type Target = dyn ADeepTrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

pub(crate) trait B1DeepTrait:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync + ADeepTrait
{
    #[allow(dead_code)]
    fn clone_boxed(&self) -> B1Deep;

    fn a(&self) -> Arc<String> {
        Arc::new("B1 wins!".to_string())
    }
}

#[derive(Clone)]
pub(crate) struct B1Deep(Arc<dyn B1DeepTrait>);

impl B1Deep {
    pub fn new(selfish: impl B1DeepTrait + 'static) -> B1Deep {
        B1Deep(Arc::new(selfish))
    }
}

impl B1DeepTrait for B1Deep {
    fn clone_boxed(&self) -> B1Deep {
        B1DeepTrait::clone_boxed(&*self.0)
    }

    fn a(&self) -> Arc<String> {
        B1DeepTrait::a(&*self.0)
    }
}

impl ADeepTrait for B1Deep {
    fn clone_boxed(&self) -> ADeep {
        ADeepTrait::clone_boxed(&*self.0)
    }

    fn a(&self) -> Arc<String> {
        ADeepTrait::a(&*self.0)
    }
}

temper_core::impl_any_value_trait_for_interface!(B1Deep);

impl std::ops::Deref for B1Deep {
    type Target = dyn B1DeepTrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

pub(crate) trait B2DeepTrait:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync + B1DeepTrait
{
    #[allow(dead_code)]
    fn clone_boxed(&self) -> B2Deep;
}

#[derive(Clone)]
pub(crate) struct B2Deep(Arc<dyn B2DeepTrait>);

impl B2Deep {
    pub fn new(selfish: impl B2DeepTrait + 'static) -> B2Deep {
        B2Deep(Arc::new(selfish))
    }
}

impl B2DeepTrait for B2Deep {
    fn clone_boxed(&self) -> B2Deep {
        B2DeepTrait::clone_boxed(&*self.0)
    }
}

impl B1DeepTrait for B2Deep {
    fn clone_boxed(&self) -> B1Deep {
        B1DeepTrait::clone_boxed(&*self.0)
    }

    fn a(&self) -> Arc<String> {
        B1DeepTrait::a(&*self.0)
    }
}

impl ADeepTrait for B2Deep {
    fn clone_boxed(&self) -> ADeep {
        ADeepTrait::clone_boxed(&*self.0)
    }

    fn a(&self) -> Arc<String> {
        ADeepTrait::a(&*self.0)
    }
}

temper_core::impl_any_value_trait_for_interface!(B2Deep);

impl std::ops::Deref for B2Deep {
    type Target = dyn B2DeepTrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

pub(crate) trait CDeepTrait:
    temper_core::AsAnyValue + temper_core::AnyValueTrait + Send + Sync + ADeepTrait
{
    #[allow(dead_code)]
    fn clone_boxed(&self) -> CDeep;
}

#[derive(Clone)]
pub(crate) struct CDeep(Arc<dyn CDeepTrait>);

impl CDeep {
    pub fn new(selfish: impl CDeepTrait + 'static) -> CDeep {
        CDeep(Arc::new(selfish))
    }
}

impl CDeepTrait for CDeep {
    fn clone_boxed(&self) -> CDeep {
        CDeepTrait::clone_boxed(&*self.0)
    }
}

impl ADeepTrait for CDeep {
    fn clone_boxed(&self) -> ADeep {
        ADeepTrait::clone_boxed(&*self.0)
    }

    fn a(&self) -> Arc<String> {
        ADeepTrait::a(&*self.0)
    }
}

temper_core::impl_any_value_trait_for_interface!(CDeep);

impl std::ops::Deref for CDeep {
    type Target = dyn CDeepTrait;

    fn deref(&self) -> &Self::Target {
        &*self.0
    }
}

struct DDeepStruct {}

#[derive(Clone)]
pub(crate) struct DDeep(Arc<DDeepStruct>);

impl DDeep {
    pub fn new() -> DDeep {
        DDeep(Arc::new(DDeepStruct {}))
    }
}

impl B2DeepTrait for DDeep {
    fn clone_boxed(&self) -> B2Deep {
        B2Deep::new(self.clone())
    }
}

impl CDeepTrait for DDeep {
    fn clone_boxed(&self) -> CDeep {
        CDeep::new(self.clone())
    }
}

impl B1DeepTrait for DDeep {
    fn clone_boxed(&self) -> B1Deep {
        B1Deep::new(self.clone())
    }
}

impl ADeepTrait for DDeep {
    fn clone_boxed(&self) -> ADeep {
        ADeep::new(self.clone())
    }

    fn a(&self) -> Arc<String> {
        B1DeepTrait::a(self)
    }
}

temper_core::impl_any_value_trait!(DDeep, [B2Deep, CDeep, B1Deep, ADeep]);

struct EDeepStruct {}

#[derive(Clone)]
pub(crate) struct EDeep(Arc<EDeepStruct>);

impl EDeep {
    pub fn new() -> EDeep {
        EDeep(Arc::new(EDeepStruct {}))
    }
}

impl CDeepTrait for EDeep {
    fn clone_boxed(&self) -> CDeep {
        CDeep::new(self.clone())
    }
}

impl B2DeepTrait for EDeep {
    fn clone_boxed(&self) -> B2Deep {
        B2Deep::new(self.clone())
    }
}

impl ADeepTrait for EDeep {
    fn clone_boxed(&self) -> ADeep {
        ADeep::new(self.clone())
    }
}

impl B1DeepTrait for EDeep {
    fn clone_boxed(&self) -> B1Deep {
        B1Deep::new(self.clone())
    }

    fn a(&self) -> Arc<String> {
        ADeepTrait::a(self)
    }
}

temper_core::impl_any_value_trait!(EDeep, [CDeep, B2Deep, ADeep, B1Deep]);
