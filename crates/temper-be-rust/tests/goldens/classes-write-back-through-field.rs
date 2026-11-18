use std::sync::{Arc, OnceLock, RwLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let p = Ping::new(Pong::new());
            println!("ping={}", p.ping());
            println!("pass={}", p.pass());
            println!("record={} most={}", p.record(), p.most());
            println!("around={} n={}", p.around(), p.n());
            Ok(())
        })
        .clone()
}

struct PingStruct {
    peer: Pong,
    n: RwLock<i32>,
    most: RwLock<i32>,
}

#[derive(Clone)]
pub(crate) struct Ping(Arc<PingStruct>);

impl Ping {
    pub fn new(peer: Pong) -> Ping {
        Ping(Arc::new(PingStruct {
            peer,
            n: RwLock::new(0),
            most: RwLock::new(0),
        }))
    }

    pub fn ping(&self) -> i32 {
        self.0.peer.pong(self.clone());
        self.n()
    }

    pub fn pass(&self) -> i32 {
        self.0.peer.bump(self.clone(), self.n())
    }

    pub fn record(&self) -> bool {
        *self.0.n.write().unwrap() = self.n().wrapping_add(1);
        if self.n() > self.most() {
            *self.0.most.write().unwrap() = self.n();
        }
        self.n() > self.most().wrapping_sub(1)
    }

    pub fn around(&self) -> i32 {
        self.n()
            .wrapping_add(self.0.peer.bump(self.clone(), 10))
            .wrapping_add(self.n())
    }

    pub fn n(&self) -> i32 {
        *self.0.n.read().unwrap()
    }

    pub fn most(&self) -> i32 {
        *self.0.most.read().unwrap()
    }
}

temper_core::impl_any_value_trait!(Ping, []);

struct PongStruct {}

#[derive(Clone)]
pub(crate) struct Pong(Arc<PongStruct>);

impl Pong {
    pub fn new() -> Pong {
        Pong(Arc::new(PongStruct {}))
    }

    pub fn pong(&self, a: Ping) {
        *a.0.n.write().unwrap() = a.n().wrapping_add(1);
    }

    pub fn bump(&self, a: Ping, by: i32) -> i32 {
        {
            *a.0.n.write().unwrap() = a.n().wrapping_add(by);
        }
        a.n()
    }
}

temper_core::impl_any_value_trait!(Pong, []);
