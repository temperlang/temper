use std::sync::{Arc, OnceLock};
use temper_core::AsAnyValue;
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            report_length(Arc::new(vec![
                4.as_any_value(),
                5.as_any_value(),
                6.as_any_value(),
            ]));
            let ls: temper_core::List<i32> = temper_core::listed::sorted(
                &Arc::new(vec![5, 6, 7, 8, 9, 0, 1, 2, 3, 4]),
                &(|i: i32, j: i32| i.wrapping_sub(j)),
            );
            println!(
                "All  {}",
                temper_core::listed::join(
                    &ls,
                    Arc::new(", ".to_string()),
                    &(|x: i32| Arc::new(x.to_string())),
                )
            );
            println!(
                "Even {}",
                temper_core::listed::join(
                    &temper_core::listed::filter(&ls, &(|x: i32| x & 1 == 0)),
                    Arc::new(", ".to_string()),
                    &(|x: i32| Arc::new(x.to_string())),
                )
            );
            println!(
                "Odd  {}",
                temper_core::listed::join(
                    &temper_core::listed::filter(&ls, &(|x: i32| x & 1 != 0)),
                    Arc::new(", ".to_string()),
                    &(|x: i32| Arc::new(x.to_string())),
                )
            );
            println!(
                "Neg  {}",
                temper_core::listed::join(
                    &temper_core::listed::map(&ls, &(|x: i32| x.wrapping_neg())),
                    Arc::new(", ".to_string()),
                    &(|x: i32| Arc::new(x.to_string())),
                )
            );
            let listed: temper_core::Listed<i32> = temper_core::Listed::new(ls.clone());
            println!(
                "Neg  {}",
                temper_core::listed::join(
                    &temper_core::listed::map(&*listed, &(|x: i32| x.wrapping_neg())),
                    Arc::new(", ".to_string()),
                    &(|x: i32| Arc::new(x.to_string())),
                )
            );
            let listed_list: temper_core::List<i32> = temper_core::ListedTrait::to_list(&*listed);
            println!(
                "Listed list length is {}",
                temper_core::ListedTrait::len(&listed_list)
            );
            println!("Looping");
            let n = temper_core::ListedTrait::len(&ls);
            {
                let mut i: i32 = 0;
                while i < n {
                    println!("  {}", temper_core::ListedTrait::get(&ls, i));
                    i = i.wrapping_add(1);
                }
            }
            report_split("a ; b ; c", " ; ");
            report_split("def", "");
            report_split(" g  h i", " ");
            report_split(" ", " ");
            report_split("", " ");
            report_split("", "");
            report_split("🌍", "");
            let triangle_of_foo_bar: temper_core::ListBuilder<
                temper_core::ListBuilder<Arc<String>>,
            > = temper_core::ListedTrait::to_list_builder(&temper_core::listed::map(
                &Arc::new(vec![
                    Arc::new(vec![]),
                    Arc::new(vec![Arc::new("bar".to_string())]),
                    Arc::new(vec![
                        Arc::new("bar".to_string()),
                        Arc::new("baz".to_string()),
                    ]),
                    Arc::new(vec![
                        Arc::new("bar".to_string()),
                        Arc::new("baz".to_string()),
                        Arc::new("boo".to_string()),
                    ]),
                    Arc::new(vec![
                        Arc::new("bar".to_string()),
                        Arc::new("baz".to_string()),
                        Arc::new("boo".to_string()),
                        Arc::new("far".to_string()),
                    ]),
                    Arc::new(vec![
                        Arc::new("bar".to_string()),
                        Arc::new("baz".to_string()),
                        Arc::new("boo".to_string()),
                        Arc::new("far".to_string()),
                        Arc::new("faz".to_string()),
                    ]),
                    Arc::new(vec![
                        Arc::new("far".to_string()),
                        Arc::new("faz".to_string()),
                        Arc::new("foo".to_string()),
                        Arc::new("bar".to_string()),
                        Arc::new("baz".to_string()),
                        Arc::new("boo".to_string()),
                    ]),
                ]),
                &(|list: temper_core::List<Arc<String>>| {
                    let builder: temper_core::ListBuilder<Arc<String>> =
                        temper_core::ListedTrait::to_list_builder(&list);
                    temper_core::listed::sort(
                        &builder,
                        &(|a: Arc<String>, b: Arc<String>| a.as_str().cmp(b.as_str()) as i32),
                    );
                    builder.clone()
                }),
            ));
            {
                let mut j: i32 = 0;
                while j < temper_core::ListedTrait::len(&triangle_of_foo_bar) {
                    let row: temper_core::ListBuilder<Arc<String>> =
                        temper_core::ListedTrait::get(&triangle_of_foo_bar, j);
                    println!(
                        "Fwd  [{}]",
                        temper_core::listed::join(
                            &row,
                            Arc::new(" ".to_string()),
                            &(|x: Arc<String>| x.clone()),
                        )
                    );
                    temper_core::listed::reverse(&row);
                    j = j.wrapping_add(1);
                }
            }
            temper_core::listed::reverse(&triangle_of_foo_bar);
            {
                let mut j2: i32 = 0;
                while j2 < temper_core::ListedTrait::len(&triangle_of_foo_bar) {
                    let row2: temper_core::ListBuilder<Arc<String>> =
                        temper_core::ListedTrait::get(&triangle_of_foo_bar, j2);
                    println!(
                        "Back [{}]",
                        temper_core::listed::join(
                            &row2,
                            Arc::new(" ".to_string()),
                            &(|x: Arc<String>| x.clone()),
                        )
                    );
                    j2 = j2.wrapping_add(1);
                }
            }
            let message_builder: temper_core::ListBuilder<Arc<String>> =
                temper_core::listed::new_builder();
            temper_core::listed::add(&message_builder, Arc::new("hello".to_string()), None);
            temper_core::listed::set(&message_builder, 0, Arc::new("hi".to_string()));
            temper_core::listed::add_all(
                &message_builder,
                temper_core::Listed::new(Arc::new(vec![
                    Arc::new("there".to_string()),
                    Arc::new("y'all".to_string()),
                ])),
                None,
            );
            temper_core::listed::add(&message_builder, Arc::new("say".to_string()), Some(0));
            let copy_that: temper_core::ListBuilder<Arc<String>> =
                temper_core::ListedTrait::to_list_builder(&message_builder);
            temper_core::listed::add_all(
                &message_builder,
                temper_core::Listed::new(Arc::new(vec![
                    Arc::new("more".to_string()),
                    Arc::new("than".to_string()),
                ])),
                Some(1),
            );
            temper_core::listed::add_all(
                &copy_that,
                temper_core::Listed::new(message_builder.clone()),
                None,
            );
            println!(
                "{}",
                temper_core::listed::join(
                    &message_builder,
                    Arc::new(" ".to_string()),
                    &(|x: Arc<String>| x.clone()),
                )
            );
            println!(
                "{}",
                temper_core::listed::join(
                    &temper_core::listed::sorted(
                        &copy_that,
                        &(|a: Arc<String>, b: Arc<String>| b.as_str().cmp(a.as_str()) as i32),
                    ),
                    Arc::new(" ".to_string()),
                    &(|x: Arc<String>| x.clone()),
                )
            );
            println!(
                "{}",
                temper_core::listed::join(
                    &copy_that,
                    Arc::new(" ".to_string()),
                    &(|x: Arc<String>| x.clone()),
                )
            );
            let old_last = temper_core::listed::remove_last(&copy_that);
            println!(
                "Removed {old_last} so {}",
                temper_core::listed::join(
                    &copy_that,
                    Arc::new(" ".to_string()),
                    &(|x: Arc<String>| x.clone()),
                )
            );
            let next_last: temper_core::List<Arc<String>> = temper_core::listed::splice(
                &copy_that,
                Some(temper_core::ListedTrait::len(&copy_that).wrapping_sub(1)),
                Some(1),
                None::<temper_core::Listed<_>>,
            );
            let old_words: temper_core::List<Arc<String>> = temper_core::listed::splice(
                &copy_that,
                Some(4),
                Some(3),
                Some(temper_core::Listed::new(next_last.clone())),
            );
            temper_core::listed::splice(
                &copy_that,
                None,
                Some(0),
                Some(temper_core::Listed::new(old_words.clone())),
            );
            temper_core::listed::splice(
                &copy_that,
                Some(temper_core::ListedTrait::len(&copy_that)),
                None,
                Some(temper_core::Listed::new(Arc::new(vec![
                    Arc::new("and".to_string()),
                    Arc::new("the".to_string()),
                    Arc::new("end".to_string()),
                ]))),
            );
            println!(
                "{}",
                temper_core::listed::join(
                    &copy_that,
                    Arc::new(" ".to_string()),
                    &(|x: Arc<String>| x.clone()),
                )
            );
            temper_core::listed::splice(
                &copy_that,
                None,
                None,
                Some(temper_core::Listed::new(Arc::new(vec![Arc::new(
                    "gone".to_string(),
                )]))),
            );
            println!(
                "{}",
                temper_core::listed::join(
                    &copy_that,
                    Arc::new(" ".to_string()),
                    &(|x: Arc<String>| x.clone()),
                )
            );
            let past_end: temper_core::List<Arc<String>> = temper_core::listed::splice(
                &copy_that,
                Some(temper_core::ListedTrait::len(&copy_that).wrapping_add(5)),
                Some(1),
                None::<temper_core::Listed<_>>,
            );
            temper_core::listed::splice(
                &copy_that,
                Some(-3),
                Some(0),
                Some(temper_core::Listed::new(Arc::new(vec![Arc::new(
                    "first".to_string(),
                )]))),
            );
            println!(
                "{} {}",
                temper_core::ListedTrait::len(&past_end),
                temper_core::listed::join(
                    &copy_that,
                    Arc::new(" ".to_string()),
                    &(|x: Arc<String>| x.clone()),
                )
            );
            temper_core::listed::clear(&message_builder);
            temper_core::listed::add(&message_builder, Arc::new("Bye".to_string()), None);
            println!(
                "{}",
                temper_core::listed::join(
                    &message_builder,
                    Arc::new(" ".to_string()),
                    &(|x: Arc<String>| x.clone()),
                )
            );
            {
                let mut i2: i32 = -2;
                while i2 <= 10 {
                    println!(
                        "Slice({i2}, {}): [{}]",
                        i2.wrapping_add(2),
                        temper_core::listed::join(
                            &temper_core::listed::slice(&ls, i2, i2.wrapping_add(2)),
                            Arc::new(", ".to_string()),
                            &(|x: i32| Arc::new(x.to_string())),
                        )
                    );
                    i2 = i2.wrapping_add(1);
                }
            }
            println!(
                "Outside fallback: {}",
                temper_core::ListedTrait::get_or(&ls, temper_core::ListedTrait::len(&ls), 123)
            );
            {
                let boolean_to_string = Arc::new(move |b: bool| Arc::new(b.to_string()));
                let ls2: temper_core::List<bool> = Arc::new(vec![true, false]);
                let joined = temper_core::listed::join(
                    &ls2,
                    Arc::new(", ".to_string()),
                    &*boolean_to_string,
                );
                let inverted: temper_core::List<bool> =
                    temper_core::listed::map(&ls2, &(|b: bool| !b));
                let joined_and_inverted = temper_core::listed::join(
                    &inverted,
                    Arc::new(", ".to_string()),
                    &(|b: bool| {
                        if b {
                            Arc::new("not false".to_string())
                        } else {
                            Arc::new("not true".to_string())
                        }
                    }),
                );
                println!("list = [{joined}]");
                println!("list (each inverted) = [{joined_and_inverted}]");
                let only_true: temper_core::List<bool> =
                    temper_core::listed::filter(&ls2, &(|b: bool| b));
                let only_false: temper_core::List<bool> =
                    temper_core::listed::filter(&ls2, &(|b: bool| !b));
                println!(
                    "list (== true only) = [{}]",
                    temper_core::listed::join(
                        &only_true,
                        Arc::new(", ".to_string()),
                        &*boolean_to_string,
                    )
                );
                println!(
                    "list (== false only) = [{}]",
                    temper_core::listed::join(
                        &only_false,
                        Arc::new(", ".to_string()),
                        &*boolean_to_string,
                    )
                );
            }
            {
                let sizes: temper_core::List<f64> = Arc::new(vec![0.25, 3.0]);
                let labels: temper_core::List<Arc<String>> = temper_core::listed::map(
                    &sizes,
                    &(|f: f64| {
                        if temper_core::float64::cmp(f, 1.0) < 0 {
                            Arc::new("small".to_string())
                        } else {
                            Arc::new("big".to_string())
                        }
                    }),
                );
                println!(
                    "sizes = [{}]",
                    temper_core::listed::join(
                        &labels,
                        Arc::new(", ".to_string()),
                        &(|s: Arc<String>| s.clone()),
                    )
                );
            }
            run_something();
            {
                let a_list_builder: temper_core::ListBuilder<Arc<String>> =
                    temper_core::listed::new_builder();
                temper_core::listed::add(&a_list_builder, Arc::new("first".to_string()), None);
                let built_first: temper_core::List<Arc<String>> =
                    temper_core::ListedTrait::to_list(&a_list_builder);
                temper_core::listed::add(&a_list_builder, Arc::new("second".to_string()), None);
                let built_second: temper_core::List<Arc<String>> =
                    temper_core::ListedTrait::to_list(&*temper_core::Listed::new(
                        a_list_builder.clone(),
                    ));
                temper_core::listed::add(&a_list_builder, Arc::new("third".to_string()), None);
                println!(
                    "builtFirst has {}",
                    temper_core::listed::join(
                        &built_first,
                        Arc::new(", ".to_string()),
                        &(|s: Arc<String>| s.clone()),
                    )
                );
                println!(
                    "builtSecond has {}",
                    temper_core::listed::join(
                        &built_second,
                        Arc::new(", ".to_string()),
                        &(|s: Arc<String>| s.clone()),
                    )
                );
            }
            Ok(())
        })
        .clone()
}

pub(crate) fn report_length<T: Clone + Send + Sync + 'static>(list: impl temper_core::ToListed<T>) {
    let list = list.to_listed();
    println!("Length: {}", temper_core::ListedTrait::len(&*list));
}

pub(crate) fn report_split(string: impl ToArcString, sep: impl ToArcString) {
    let string = string.to_arc_string();
    let sep = sep.to_arc_string();
    let strings: temper_core::List<Arc<String>> = temper_core::string::split(&string, sep.clone());
    let len = Arc::new(temper_core::ListedTrait::len(&strings).to_string());
    println!(
        "Split {} - {len}",
        temper_core::listed::join(
            &strings,
            Arc::new(", ".to_string()),
            &(|x: Arc<String>| x.clone()),
        )
    );
}

pub(crate) fn something(callback: Arc<dyn Fn(temper_core::Listed<Arc<String>>) + Send + Sync>) {
    callback(temper_core::Listed::new(Arc::new(vec![
        Arc::new("Called".to_string()),
        Arc::new("with".to_string()),
        Arc::new("listed.".to_string()),
    ])));
}

pub(crate) fn run_something() {
    something(Arc::new(move |words: temper_core::Listed<Arc<String>>| {
        println!(
            "{}",
            temper_core::listed::join(
                &*words,
                Arc::new(" ".to_string()),
                &(|word: Arc<String>| word.clone()),
            )
        );
    }));
}
