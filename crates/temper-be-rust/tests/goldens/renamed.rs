use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            NATIVE.set(2).unwrap_or_else(|_| panic!());
            Ok(())
        })
        .clone()
}

pub fn lambda(n: i32) -> i32 {
    n.wrapping_add(1)
}

static NATIVE: OnceLock<i32> = OnceLock::new();

pub fn native() -> i32 {
    *crate::support::initialized(|| NATIVE.get())
}

struct CheckedStruct {
    end: i32,
}

#[derive(Clone)]
pub struct Checked(Arc<CheckedStruct>);

impl Checked {
    pub fn new(end: i32) -> Checked {
        Checked(Arc::new(CheckedStruct { end }))
    }

    pub fn delete(&self) -> i32 {
        self.0.end.wrapping_mul(2)
    }

    pub fn local(&self) -> i32 {
        self.0.end.wrapping_add(native())
    }

    pub fn end(&self) -> i32 {
        self.0.end
    }
}

temper_core::impl_any_value_trait!(Checked, []);

struct BuiltinsStruct {
    hash: i32,
}

#[derive(Clone)]
pub struct Builtins(Arc<BuiltinsStruct>);

impl Builtins {
    pub fn new(hash: i32) -> Builtins {
        Builtins(Arc::new(BuiltinsStruct { hash }))
    }

    pub fn get(&self, i: i32) -> i32 {
        self.0.hash.wrapping_add(i)
    }

    pub fn set(&self, i: i32) -> i32 {
        i.wrapping_mul(2)
    }

    pub fn map(&self, i: i32) -> i32 {
        i.wrapping_add(1)
    }

    pub fn format(&self) -> Arc<String> {
        Arc::new(format!("#{}", self.0.hash))
    }

    pub fn int(&self) -> i32 {
        self.0.hash.wrapping_mul(10)
    }

    pub fn later(&self) -> i32 {
        self.int().wrapping_add(1)
    }

    pub fn init(&self) -> i32 {
        self.0.hash.wrapping_add(3)
    }

    pub fn print(&self) -> Arc<String> {
        Arc::new(format!("print {}", self.init()))
    }

    pub fn hash(&self) -> i32 {
        self.0.hash
    }
}

temper_core::impl_any_value_trait!(Builtins, []);
