use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let letters: temper_core::List<Arc<String>> = Arc::new(vec![
                Arc::new("a".to_string()),
                Arc::new("b".to_string()),
                Arc::new("c".to_string()),
            ]);
            println!("{}", at(letters.clone(), 2));
            println!("before");
            println!("{}", at(letters.clone(), 3));
            println!("not reached");
            Ok(())
        })
        .clone()
}

pub(crate) fn at(listed: impl temper_core::ToListed<Arc<String>>, i: i32) -> Arc<String> {
    let listed = listed.to_listed();
    temper_core::ListedTrait::get(&*listed, i)
}
