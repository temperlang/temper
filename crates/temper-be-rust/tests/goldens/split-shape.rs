use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let items: temper_core::List<Arc<String>> = Arc::new(
                vec![
                    temper_core::part(|| {
                        vec![
                            Arc::new("item 0 é✓𝄞".to_string()),
                            Arc::new("item 1 é✓𝄞".to_string()),
                            Arc::new("item 2 é✓𝄞".to_string()),
                            Arc::new("item 3 é✓𝄞".to_string()),
                        ]
                    }),
                    temper_core::part(|| {
                        vec![
                            Arc::new("item 4 é✓𝄞".to_string()),
                            Arc::new("item 5 é✓𝄞".to_string()),
                            Arc::new("item 6 é✓𝄞".to_string()),
                            Arc::new("item 7 é✓𝄞".to_string()),
                        ]
                    }),
                    temper_core::part(|| {
                        vec![
                            Arc::new("item 8 é✓𝄞".to_string()),
                            Arc::new("item 9 é✓𝄞".to_string()),
                        ]
                    }),
                ]
                .into_iter()
                .flatten()
                .collect::<Vec<_>>(),
            );
            let table: temper_core::Map<Arc<String>, Arc<String>> = temper_core::Map::new(Arc::new(
                vec![
                    temper_core::part(|| {
                        vec![
                            (
                                Arc::new("k0".to_string()),
                                Arc::new("item 0 é✓𝄞".to_string()),
                            ),
                            (
                                Arc::new("k1".to_string()),
                                Arc::new("item 1 é✓𝄞".to_string()),
                            ),
                            (
                                Arc::new("k2".to_string()),
                                Arc::new("item 2 é✓𝄞".to_string()),
                            ),
                            (
                                Arc::new("k3".to_string()),
                                Arc::new("item 3 é✓𝄞".to_string()),
                            ),
                        ]
                    }),
                    temper_core::part(|| {
                        vec![
                            (
                                Arc::new("k4".to_string()),
                                Arc::new("item 4 é✓𝄞".to_string()),
                            ),
                            (
                                Arc::new("k5".to_string()),
                                Arc::new("item 5 é✓𝄞".to_string()),
                            ),
                        ]
                    }),
                ]
                .into_iter()
                .flatten()
                .collect::<Vec<_>>(),
            ));
            let long = Arc::new("abcdefghij é✓𝄞 \0 abcdefghij é✓𝄞 \0 ".to_string());
            let built: temper_core::ListBuilder<Arc<String>> = temper_core::listed::new_builder();
            temper_core::part(|| {
                temper_core::listed::add(&built, Arc::new("item 0 é✓𝄞".to_string()), None);
                temper_core::listed::add(&built, Arc::new("item 1 é✓𝄞".to_string()), None);
                temper_core::listed::add(&built, Arc::new("item 2 é✓𝄞".to_string()), None);
                temper_core::listed::add(&built, Arc::new("item 3 é✓𝄞".to_string()), None);
            });
            temper_core::part(|| {
                temper_core::listed::add(&built, Arc::new("item 4 é✓𝄞".to_string()), None);
                temper_core::listed::add(&built, Arc::new("item 5 é✓𝄞".to_string()), None);
                temper_core::listed::add(&built, Arc::new("item 6 é✓𝄞".to_string()), None);
                temper_core::listed::add(&built, Arc::new("item 7 é✓𝄞".to_string()), None);
            });
            temper_core::part(|| {
                temper_core::listed::add(&built, Arc::new("item 8 é✓𝄞".to_string()), None);
                temper_core::listed::add(&built, Arc::new("item 9 é✓𝄞".to_string()), None);
            });
            let all: temper_core::List<Arc<String>> = temper_core::ListedTrait::to_list(&built);
            println!(
                "{} {}",
                temper_core::ListedTrait::len(&items),
                temper_core::ListedTrait::get(&items, 9)
            );
            println!(
                "{} {}",
                temper_core::MappedTrait::len(&table),
                temper_core::MappedTrait::get(&table, Arc::new("k5".to_string()))
                    .unwrap_or_else(|_| Arc::new("none".to_string()))
            );
            println!(
                "{}",
                temper_core::ListedTrait::len(&temper_core::string::split(
                    &long,
                    Arc::new("✓".to_string()),
                ))
            );
            println!(
                "{} {}",
                temper_core::ListedTrait::len(&all),
                temper_core::ListedTrait::get(&all, 9)
            );
            Ok(())
        })
        .clone()
}
