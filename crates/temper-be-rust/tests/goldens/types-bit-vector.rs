use std::sync::OnceLock;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let v = temper_core::BitVector::with_capacity(16);
            println!("v[2]={}", v.get(2));
            println!("v[3]={}", v.get(3));
            println!("v[3] <- true");
            v.set(3, true);
            println!("v[2]={}", v.get(2));
            println!("v[3]={}", v.get(3));
            println!("v[3] <- false");
            v.set(3, false);
            println!("v[2]={}", v.get(2));
            println!("v[3]={}", v.get(3));
            v.set(100, true);
            v.set(200, false);
            println!(
                "v[99]={} v[100]={} v[101]={} v[200]={} v[-1]={}",
                v.get(99),
                v.get(100),
                v.get(101),
                v.get(200),
                v.get(-1)
            );
            v.set(1000, true);
            println!(
                "v[999]={} v[1000]={} v[100]={}",
                v.get(999),
                v.get(1000),
                v.get(100)
            );
            let u = temper_core::BitVector::with_capacity(0);
            u.set(8, true);
            u.set(32, true);
            u.set(64, true);
            println!(
                "{} {} {} {} {} {} {} {} {}",
                u.get(7),
                u.get(8),
                u.get(9),
                u.get(31),
                u.get(32),
                u.get(33),
                u.get(63),
                u.get(64),
                u.get(65)
            );
            v.set(1000, false);
            println!("v[1000]={} v[999]={}", v.get(1000), v.get(999));
            let w = temper_core::BitVector::with_capacity(-9);
            w.set(1, true);
            println!("w[0]={} w[1]={}", w.get(0), w.get(1));
            println!("before");
            v.set(-1, true);
            println!("not reached");
            Ok(())
        })
        .clone()
}
