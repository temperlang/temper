use std::sync::{Arc, OnceLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            MESSAGES
                .set(temper_core::Map::new(Arc::new(vec![
                    (200, Arc::new("OK".to_string())),
                    (404, Arc::new("Not Found".to_string())),
                    (500, Arc::new("Internal Server Error".to_string())),
                ])))
                .unwrap_or_else(|_| panic!());
            let message = temper_core::MappedTrait::get(&messages(), 200)?;
            println!("Message: {message}");
            println!(
                "Has 200: {}, 302: {}",
                message_defined(200),
                message_defined(302)
            );
            println!(
                "Fallback: {}",
                temper_core::MappedTrait::get(&messages(), 302)
                    .unwrap_or_else(|_| Arc::new("Found".to_string()))
            );
            println!("Or null: {}", {
                let subject = temper_core::MappedTrait::get_or_null(&messages(), 302);
                subject
                    .clone()
                    .unwrap_or_else(|| Arc::new("none".to_string()))
            });
            println!(
                "Codes: {}",
                temper_core::listed::join(
                    &list_keys(temper_core::Mapped::new(messages())),
                    Arc::new(" ".to_string()),
                    &(|it: i32| Arc::new(it.to_string())),
                )
            );
            println!(
                "Again: {}",
                temper_core::listed::join(
                    &*temper_core::MappedTrait::keys(&messages()),
                    Arc::new(" ".to_string()),
                    &(|it: i32| Arc::new(it.to_string())),
                )
            );
            println!(
                "Messages: {}",
                temper_core::listed::join(
                    &*temper_core::MappedTrait::values(&messages()),
                    Arc::new(", ".to_string()),
                    &(|it: Arc<String>| it.clone()),
                )
            );
            let groups: temper_core::List<i32> = temper_core::mapped_to_list_with(
                &messages(),
                &(|key: i32, _value: Arc<String>| key.wrapping_div(100)),
            );
            println!(
                "Groups: {}",
                temper_core::listed::join(
                    &groups,
                    Arc::new(" ".to_string()),
                    &(|it: i32| Arc::new(it.to_string())),
                )
            );
            let neighbors: temper_core::Map<Arc<String>, temper_core::List<Arc<String>>> = {
                let builder2: temper_core::MapBuilder<Arc<String>, temper_core::List<Arc<String>>> =
                    temper_core::MapBuilder::new();
                temper_core::MapBuilder::set(
                    &builder2,
                    Arc::new("Honduras".to_string()),
                    Arc::new(vec![
                        Arc::new("El Salvador".to_string()),
                        Arc::new("Guatemala".to_string()),
                        Arc::new("Nicaragua".to_string()),
                    ]),
                );
                println!(
                    "From builder: {}",
                    temper_core::listed::join(
                        &temper_core::MappedTrait::get(
                            &builder2,
                            Arc::new("Honduras".to_string()),
                        )?,
                        Arc::new(", ".to_string()),
                        &(|it: Arc<String>| it.clone()),
                    )
                );
                println!(
                    "Key so far: {}",
                    temper_core::ListedTrait::get(
                        &list_keys(temper_core::Mapped::new(builder2.clone())),
                        0,
                    )
                );
                temper_core::MapBuilder::set(
                    &builder2,
                    Arc::new("El Salvador".to_string()),
                    Arc::new(vec![
                        Arc::new("Guatemala".to_string()),
                        Arc::new("Honduras".to_string()),
                    ]),
                );
                temper_core::MapBuilder::set(
                    &builder2,
                    Arc::new("Nicaragua".to_string()),
                    Arc::new(vec![
                        Arc::new("Costa Rica".to_string()),
                        Arc::new("Honduras".to_string()),
                    ]),
                );
                temper_core::MapBuilder::set(
                    &builder2,
                    Arc::new("Panama".to_string()),
                    Arc::new(vec![
                        Arc::new("Colombia".to_string()),
                        Arc::new("Costa Rica".to_string()),
                    ]),
                );
                let _ =
                    temper_core::MapBuilder::remove(&builder2, Arc::new("Honduras".to_string()));
                let was: temper_core::List<Arc<String>> =
                    temper_core::MapBuilder::remove(&builder2, Arc::new("El Salvador".to_string()))
                        .unwrap_or_else(|e| panic!("panic\ncaused by: {e}"));
                println!(
                    "Was there: {}",
                    temper_core::listed::join(
                        &was,
                        Arc::new(", ".to_string()),
                        &(|it: Arc<String>| it.clone()),
                    )
                );
                println!(
                    "{}",
                    (|| {
                        Ok::<_, temper_core::MissingKey>(temper_core::ListedTrait::get(
                            &temper_core::MapBuilder::remove(
                                &builder2,
                                Arc::new("El Salvador".to_string()),
                            )?,
                            0,
                        ))
                    })()
                    .unwrap_or_else(|_| Arc::new("Gone".to_string()))
                );
                println!(
                    "{}",
                    (|| {
                        temper_core::MapBuilder::remove(&builder2, Arc::new("Belize".to_string()))?;
                        Ok::<_, temper_core::MissingKey>(Arc::new("removed".to_string()))
                    })()
                    .unwrap_or_else(|e| e.message())
                );
                temper_core::MappedTrait::to_map(&builder2)
            };
            println!(
                "From built: {}",
                temper_core::listed::join(
                    &temper_core::MappedTrait::get(&neighbors, Arc::new("Nicaragua".to_string()))?,
                    Arc::new(", ".to_string()),
                    &(|it: Arc<String>| it.clone()),
                )
            );
            println!("{}", look_up(302).unwrap_or_else(|e2| e2.message()));
            let from_builder: temper_core::MapBuilder<Arc<String>, i32> =
                temper_core::MapBuilder::new();
            println!(
                "{}",
                temper_core::MappedTrait::get(&from_builder, Arc::new("nope".to_string()))
                    .unwrap_or(-1)
            );
            let map_of_none: temper_core::Map<i32, i32> = temper_core::Map::new(Arc::new(vec![]));
            let map_of_one: temper_core::Map<Arc<String>, Arc<String>> =
                temper_core::Map::new(Arc::new(vec![(
                    Arc::new("jack".to_string()),
                    Arc::new("all trades".to_string()),
                )]));
            let map_of_two: temper_core::Map<i32, Arc<String>> =
                temper_core::Map::new(Arc::new(vec![
                    (1, Arc::new("one".to_string())),
                    (2, Arc::new("electric boogaloo".to_string())),
                ]));
            println!("mapOfNone: {}", temper_core::MappedTrait::len(&map_of_none));
            println!("mapOfOne: {}", temper_core::MappedTrait::len(&map_of_one));
            println!("mapOfTwo: {}", temper_core::MappedTrait::len(&map_of_two));
            EIGHT_COLORS
                .set(temper_core::MapBuilder::new())
                .unwrap_or_else(|_| panic!());
            temper_core::MapBuilder::set(
                &eight_colors(),
                Arc::new("black".to_string()),
                Arc::new(vec![false, false, false]),
            );
            temper_core::MapBuilder::set(
                &eight_colors(),
                Arc::new("red".to_string()),
                Arc::new(vec![true, false, false]),
            );
            temper_core::MapBuilder::set(
                &eight_colors(),
                Arc::new("green".to_string()),
                Arc::new(vec![false, true, false]),
            );
            temper_core::MapBuilder::set(
                &eight_colors(),
                Arc::new("blue".to_string()),
                Arc::new(vec![false, false, true]),
            );
            temper_core::MapBuilder::set(
                &eight_colors(),
                Arc::new("yellow".to_string()),
                Arc::new(vec![true, true, false]),
            );
            temper_core::MapBuilder::set(
                &eight_colors(),
                Arc::new("cyan".to_string()),
                Arc::new(vec![false, true, true]),
            );
            temper_core::MapBuilder::set(
                &eight_colors(),
                Arc::new("purple".to_string()),
                Arc::new(vec![true, false, true]),
            );
            temper_core::MapBuilder::set(
                &eight_colors(),
                Arc::new("white".to_string()),
                Arc::new(vec![true, true, true]),
            );
            println!(
                "with 3 bits you can draw {} colors.",
                temper_core::MappedTrait::len(&eight_colors())
            );
            temper_core::MappedTrait::for_each(
                &eight_colors(),
                &(|color: Arc<String>, bits: temper_core::List<bool>| {
                    let found_by_get_or: temper_core::List<bool> = temper_core::MappedTrait::get_or(
                        &eight_colors(),
                        color.clone(),
                        Arc::new(vec![]),
                    );
                    if temper_core::ListedTrait::len(&found_by_get_or) != 3 {
                        println!("getOr(\"{color}\") failed to find key");
                    }
                    let want = color_to_hex(bits.clone());
                    let got = color_to_hex(found_by_get_or.clone());
                    if want != got {
                        println!("getOr(\"{color}\") found {got} but wanted {want}");
                    }
                }),
            );
            let map: temper_core::Map<Arc<String>, Arc<String>> =
                temper_core::Map::new(Arc::new(vec![
                    (Arc::new("key".to_string()), Arc::new("value".to_string())),
                    (
                        Arc::new("other key".to_string()),
                        Arc::new("other value".to_string()),
                    ),
                ]));
            print_map("map", map.clone());
            let builder: temper_core::MapBuilder<Arc<String>, Arc<String>> =
                temper_core::MappedTrait::to_map_builder(&map);
            temper_core::MapBuilder::set(
                &builder,
                Arc::new("added key".to_string()),
                Arc::new("added value".to_string()),
            );
            temper_core::MapBuilder::set(
                &builder,
                Arc::new("key".to_string()),
                Arc::new("replaced value".to_string()),
            );
            print_map_builder("builder", builder.clone());
            print_map("map (same as above)", map.clone());
            let new_map: temper_core::Map<Arc<String>, Arc<String>> =
                temper_core::MappedTrait::to_map(&builder);
            print_map("builder to map", new_map.clone());
            let builder_as_mapped: temper_core::Mapped<Arc<String>, Arc<String>> =
                temper_core::Mapped::new(builder.clone());
            let new_map_from_mapped: temper_core::Map<Arc<String>, Arc<String>> =
                temper_core::MappedTrait::to_map(&*builder_as_mapped);
            temper_core::MapBuilder::remove(&builder, Arc::new("other key".to_string()))?;
            temper_core::MapBuilder::set(
                &builder,
                Arc::new("key".to_string()),
                Arc::new("double replaced value".to_string()),
            );
            print_map("builder to map (same as above)", new_map.clone());
            print_map(
                "builder to map via mapped (same as above)",
                new_map_from_mapped.clone(),
            );
            temper_core::MapBuilder::clear(&builder);
            temper_core::MapBuilder::set(
                &builder,
                Arc::new("something".to_string()),
                Arc::new("alone".to_string()),
            );
            print_map_builder("recently cleared", builder.clone());
            let _may_or_may_not_copy_map: temper_core::Map<Arc<String>, Arc<String>> =
                temper_core::MappedTrait::to_map(&map);
            let mapped: temper_core::Mapped<Arc<String>, Arc<String>> =
                temper_core::Mapped::new(map.clone());
            let _may_or_may_not_copy_mapped: temper_core::Map<Arc<String>, Arc<String>> =
                temper_core::MappedTrait::to_map(&*mapped);
            let made_by: temper_core::MapBuilder<Arc<String>, Arc<String>> =
                temper_core::MapBuilder::new();
            temper_core::MapBuilder::set(
                &made_by,
                Arc::new("temper".to_string()),
                Arc::new("temper systems".to_string()),
            );
            print_map_builder("made by", made_by.clone());
            let made_by_and_jokes: temper_core::MapBuilder<Arc<String>, Arc<String>> =
                temper_core::MappedTrait::to_map_builder(&made_by);
            temper_core::MapBuilder::set(
                &made_by_and_jokes,
                Arc::new("milk".to_string()),
                Arc::new("milking a cow".to_string()),
            );
            print_map_builder("made by (and a joke about milk)", made_by_and_jokes.clone());
            print_map_builder(
                "made by (not changed from a change to a copy)",
                made_by.clone(),
            );
            Ok(())
        })
        .clone()
}

pub(crate) fn list_keys<
    K: Clone + Send + Sync + 'static + Eq + std::hash::Hash,
    V: Clone + Send + Sync + 'static,
>(
    mapped: temper_core::Mapped<K, V>,
) -> temper_core::List<K> {
    temper_core::listed::map(
        &temper_core::MappedTrait::to_list(&*mapped),
        &(|entry: (K, V)| entry.0.clone()),
    )
}

static MESSAGES: OnceLock<temper_core::Map<i32, Arc<String>>> = OnceLock::new();

pub(crate) fn messages() -> temper_core::Map<i32, Arc<String>> {
    crate::support::initialized(|| MESSAGES.get()).clone()
}

pub(crate) fn message_defined(code: i32) -> Arc<String> {
    Arc::new(temper_core::MappedTrait::has(&messages(), code).to_string())
}

pub(crate) fn look_up(code: i32) -> Result<Arc<String>, temper_core::MissingKey> {
    temper_core::MappedTrait::get(&messages(), code)
}

pub(crate) fn color_to_hex(color: impl temper_core::ToList<bool>) -> Arc<String> {
    let color = color.to_list();
    let c = temper_core::listed::join(
        &color,
        Arc::new(String::new()),
        &(|b: bool| {
            if b {
                Arc::new("FF".to_string())
            } else {
                Arc::new("00".to_string())
            }
        }),
    );
    Arc::new(format!("#{c}"))
}

static EIGHT_COLORS: OnceLock<temper_core::MapBuilder<Arc<String>, temper_core::List<bool>>> =
    OnceLock::new();

pub(crate) fn eight_colors() -> temper_core::MapBuilder<Arc<String>, temper_core::List<bool>> {
    crate::support::initialized(|| EIGHT_COLORS.get()).clone()
}

pub(crate) fn print_map(name: impl ToArcString, map: temper_core::Map<Arc<String>, Arc<String>>) {
    let name = name.to_arc_string();
    println!("--- {name} ---");
    temper_core::MappedTrait::for_each(
        &map,
        &(|key: Arc<String>, value: Arc<String>| {
            println!("{key} = {value}");
        }),
    );
}

pub(crate) fn print_map_builder(
    name: impl ToArcString,
    map: temper_core::MapBuilder<Arc<String>, Arc<String>>,
) {
    let name = name.to_arc_string();
    println!("--- {name} ---");
    temper_core::MappedTrait::for_each(
        &map,
        &(|key: Arc<String>, value: Arc<String>| {
            println!("{key} = {value}");
        }),
    );
}
