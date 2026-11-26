use std::sync::{Arc, OnceLock};

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            let regex = temper_std::regex::RegexNodeTrait::compiled(
                &temper_std::regex::Sequence::new(Arc::new(vec![
                    temper_std::regex::RegexNode::new(temper_std::regex::one_or_more(
                        temper_std::regex::RegexNode::new(temper_std::regex::CodeSet::new(
                            Arc::new(vec![temper_std::regex::CodePart::new(
                                temper_std::regex::CodePoints::new("ab"),
                            )]),
                            None,
                        )),
                        None,
                    )),
                    temper_std::regex::RegexNode::new(temper_std::regex::CodePoints::new("c")),
                ])),
            );
            let full = temper_std::regex::RegexNodeTrait::compiled(&temper_std::regex::entire(
                regex.data(),
            ));
            let strings: temper_core::List<Arc<String>> = Arc::new(vec![
                Arc::new("abc".to_string()),
                Arc::new("ac".to_string()),
                Arc::new("bbabac".to_string()),
                Arc::new("ab".to_string()),
                Arc::new("dc".to_string()),
                Arc::new("acc".to_string()),
                Arc::new("cbc".to_string()),
            ]);
            {
                let length = temper_core::ListedTrait::len(&strings);
                let mut i: i32 = 0;
                while i < length {
                    let string = temper_core::ListedTrait::get(&strings, i);
                    i = i.wrapping_add(1);
                    println!("found {string}: {}", regex.found(string.clone()));
                    println!("matches {string}: {}", full.found(string.clone()));
                }
            }
            let catcher = temper_std::regex::RegexNodeTrait::compiled(
                &temper_std::regex::Sequence::new(Arc::new(vec![
                    temper_std::regex::RegexNode::new(temper_std::regex::Repeat::new(
                        temper_std::regex::RegexNode::new(temper_std::regex::CodePoints::new("a")),
                        1,
                        None,
                        Some(false),
                    )),
                    temper_std::regex::RegexNode::new(temper_std::regex::Capture::new(
                        "intro",
                        temper_std::regex::RegexNode::new(temper_std::regex::Repeat::new(
                            temper_std::regex::RegexNode::new(temper_std::regex::CodePoints::new(
                                "b",
                            )),
                            1,
                            None,
                            Some(false),
                        )),
                    )),
                    temper_std::regex::RegexNode::new(temper_std::regex::CodePoints::new("c")),
                    temper_std::regex::RegexNode::new(temper_std::regex::Repeat::new(
                        temper_std::regex::RegexNode::new(temper_std::regex::Or::new(Arc::new(vec![
                            temper_std::regex::RegexNode::new(temper_std::regex::Capture::new(
                                "option1",
                                temper_std::regex::RegexNode::new(
                                    temper_std::regex::CodePoints::new("d"),
                                ),
                            )),
                            temper_std::regex::RegexNode::new(temper_std::regex::Capture::new(
                                "alphaOption2",
                                temper_std::regex::RegexNode::new(
                                    temper_std::regex::CodePoints::new("e"),
                                ),
                            )),
                        ]))),
                        1,
                        None,
                        Some(false),
                    )),
                ])),
            );
            TEXT.set(Arc::new("🌍aaabbceef".to_string()))
                .unwrap_or_else(|_| panic!());
            let r#match = catcher.find(text(), None)?;
            check_group(r#match.full());
            check_group(
                temper_core::MappedTrait::get(&r#match.groups(), Arc::new("intro".to_string()))?,
            );
            check_group(temper_core::MappedTrait::get(
                &r#match.groups(),
                Arc::new("alphaOption2".to_string()),
            )?,);
            let thing = temper_std::regex::RegexNodeTrait::compiled(&temper_std::regex::Repeat::new(
                temper_std::regex::RegexNode::new(temper_std::regex::CodeSet::new(
                    Arc::new(vec![temper_std::regex::CodePart::new(
                        temper_std::regex::CodeRange::new(97, 122),
                    )]),
                    Some(false),
                )),
                1,
                None,
                Some(false),
            ));
            let multi_text = Arc::new("---abc---xyz---".to_string());
            let first = thing.find(multi_text.clone(), None)?;
            let second = thing.find(multi_text.clone(), Some(first.full().end()))?;
            println!(
                "Found both {} and {}.",
                first.full().value(),
                second.full().value()
            );
            MORE_TEXT
                .set(Arc::new("🌍aaabbcee🌎abcd🌏".to_string()))
                .unwrap_or_else(|_| panic!());
            let replaced = catcher.replace(
                more_text(),
                Arc::new(move |r#match: temper_std::regex::Match| {
                    let groups: temper_core::Map<Arc<String>, temper_std::regex::Group> =
                        r#match.groups();
                    let option1 = (|| {
                        Ok::<_, temper_core::MissingKey>(
                            temper_core::MappedTrait::get(&groups, Arc::new("option1".to_string()))?
                                .value(),
                        )
                    })()
                    .unwrap_or_else(|_| Arc::new(String::new()));
                    let alpha_option2 = (|| {
                        Ok::<_, temper_core::MissingKey>(
                            temper_core::MappedTrait::get(
                                &groups,
                                Arc::new("alphaOption2".to_string()),
                            )?
                            .value(),
                        )
                    })()
                    .unwrap_or_else(|_| Arc::new(String::new()));
                    let begin = temper_core::string::count_between(
                        more_text(),
                        0usize,
                        r#match.full().begin(),
                    );
                    Arc::new(format!("-{option1}{alpha_option2}{begin}-"))
                }),
            );
            println!("replaced: {replaced}");
            let not_replaced = catcher.replace(
                "nope",
                Arc::new(move |_match: temper_std::regex::Match| Arc::new("hi".to_string())),
            );
            println!("not replaced: {not_replaced}");
            let no_match =
                (|| Ok::<_, temper_core::Error>(catcher.find("not here", None)?.full().value()))()
                    .unwrap_or_else(|_| Arc::new("sorry".to_string()));
            println!("No match says {no_match}");
            let or = temper_std::regex::RegexNodeTrait::compiled(&temper_std::regex::Or::new(
                Arc::new(vec![
                    temper_std::regex::RegexNode::new(temper_std::regex::CodePoints::new("ab")),
                    temper_std::regex::RegexNode::new(temper_std::regex::CodePoints::new("bc")),
                ]),
            ))
            .data();
            let option1 = Arc::new("ab".to_string());
            let option2 = Arc::new("bc".to_string());
            let option_none = Arc::new("cb".to_string());
            println!(
                "or option 1: {}",
                temper_std::regex::RegexNodeTrait::found(&or, option1.clone())
            );
            println!(
                "or option 2: {}",
                temper_std::regex::RegexNodeTrait::found(&or, option2.clone())
            );
            println!(
                "or option none: {}",
                temper_std::regex::RegexNodeTrait::found(&or, option_none.clone())
            );
            let begin2 = temper_std::regex::RegexNodeTrait::compiled(
                &temper_std::regex::Sequence::new(Arc::new(vec![
                    temper_std::regex::RegexNode::new(temper_std::regex::begin()),
                    temper_std::regex::RegexNode::new(temper_std::regex::CodePoints::new("a")),
                ])),
            );
            let begins_with_a_true = Arc::new("ab".to_string());
            let begins_with_a_false = Arc::new("bab".to_string());
            println!("begin true: {}", begin2.found(begins_with_a_true.clone()));
            println!("begin false: {}", begin2.found(begins_with_a_false.clone()));
            let negated_basic =
                temper_std::regex::RegexNodeTrait::compiled(&temper_std::regex::entire(
                    temper_std::regex::RegexNodeTrait::compiled(&temper_std::regex::Sequence::new(
                        Arc::new(vec![
                            temper_std::regex::RegexNode::new(temper_std::regex::CodePoints::new(
                                "a",
                            )),
                            temper_std::regex::RegexNode::new(temper_std::regex::CodeSet::new(
                                Arc::new(vec![temper_std::regex::CodePart::new(
                                    temper_std::regex::CodeRange::new(97, 99),
                                )]),
                                Some(true),
                            )),
                        ]),
                    ))
                    .data(),
                ));
            let nb_true = Arc::new("a🌍".to_string());
            let nb_false = Arc::new("ac".to_string());
            println!(
                "negated basic true: {}",
                negated_basic.found(nb_true.clone())
            );
            println!(
                "negated basic false: {}",
                negated_basic.found(nb_false.clone())
            );
            let simpler_lines =
                Arc::new("It was the best of times.\nIt was the worst of times.".to_string());
            println!(
                "{}",
                temper_std::regex::RegexNodeTrait::compiled(&temper_std::regex::Repeat::new(
                    temper_std::regex::RegexNode::new(temper_std::regex::CodeSet::new(
                        Arc::new(vec![]),
                        Some(true),
                    )),
                    1,
                    None,
                    Some(false),
                ))
                .find(simpler_lines.clone(), None)?
                .full()
                .value()
            );
            println!(
                "{}",
                (|| {
                    Ok::<_, temper_core::Error>(
                        temper_std::regex::RegexNodeTrait::compiled(&temper_std::regex::Repeat::new(
                            temper_std::regex::RegexNode::new(temper_std::regex::CodeSet::new(
                                Arc::new(vec![]),
                                Some(false),
                            )),
                            1,
                            None,
                            Some(false),
                        ))
                        .find(simpler_lines.clone(), None)?
                        .full()
                        .value(),
                    )
                })()
                .unwrap_or_else(|_| Arc::new("-".to_string()))
            );
            let not_low =
                temper_std::regex::RegexNodeTrait::compiled(&temper_std::regex::one_or_more(
                    temper_std::regex::RegexNode::new(temper_std::regex::CodeSet::new(
                        Arc::new(vec![temper_std::regex::CodePart::new(
                            temper_std::regex::CodeRange::new(0, 47),
                        )]),
                        Some(true),
                    )),
                    None,
                ));
            println!("not low: {}", not_low.find("-/a🌍-", None)?.full().value());
            println!(
                "not space or >: {}",
                temper_std::regex::RegexNodeTrait::compiled(&temper_std::regex::Repeat::new(
                    temper_std::regex::RegexNode::new(temper_std::regex::CodeSet::new(
                        Arc::new(vec![
                            temper_std::regex::CodePart::new(temper_std::regex::CodePoints::new(
                                "\t",
                            )),
                            temper_std::regex::CodePart::new(temper_std::regex::CodePoints::new(
                                "\n",
                            )),
                            temper_std::regex::CodePart::new(temper_std::regex::CodePoints::new(
                                " ",
                            )),
                            temper_std::regex::CodePart::new(temper_std::regex::CodePoints::new(
                                ">",
                            )),
                        ]),
                        Some(true),
                    )),
                    1,
                    None,
                    Some(false),
                ))
                .find("ab>c", None)?
                .full()
                .value()
            );
            let id_start =
                temper_std::regex::RegexNodeTrait::compiled(&temper_std::regex::CodeSet::new(
                    Arc::new(vec![
                        temper_std::regex::CodePart::new(temper_std::regex::CodeRange::new(
                            97,
                            122,
                        )),
                        temper_std::regex::CodePart::new(temper_std::regex::CodeRange::new(65, 90)),
                        temper_std::regex::CodePart::new(temper_std::regex::CodePoints::new("_")),
                    ]),
                    Some(false),
                ))
                .data();
            let id_continue = temper_std::regex::Or::new(Arc::new(vec![
                id_start.clone(),
                temper_std::regex::RegexNodeTrait::compiled(&temper_std::regex::CodeSet::new(
                    Arc::new(vec![temper_std::regex::CodePart::new(
                        temper_std::regex::CodeRange::new(48, 57),
                    )]),
                    Some(false),
                ))
                .data(),
            ]));
            let word = temper_std::regex::RegexNodeTrait::compiled(
                &temper_std::regex::Sequence::new(Arc::new(vec![
                    temper_std::regex::RegexNode::new(temper_std::regex::word_boundary()),
                    id_start.clone(),
                    temper_std::regex::RegexNode::new(temper_std::regex::Repeat::new(
                        temper_std::regex::RegexNode::new(id_continue.clone()),
                        0,
                        None,
                        None,
                    )),
                    temper_std::regex::RegexNode::new(temper_std::regex::word_boundary()),
                ])),
            );
            let expected = Arc::new("def_456".to_string());
            let found = word
                .find(Arc::new(format!("123_abc {expected} ghi_789")), None)?
                .full()
                .value();
            println!("Found id: {found}");
            let string_to_escape = Arc::new("$(*^".to_string());
            let regex_with_interpolated_string = temper_std::regex::RegexNodeTrait::compiled(
                &temper_std::regex::Sequence::new(Arc::new(vec![
                    temper_std::regex::RegexNode::new(temper_std::regex::dot()),
                    temper_std::regex::RegexNode::new(temper_std::regex::Repeat::new(
                        temper_std::regex::RegexNode::new(temper_std::regex::CodePoints::new(
                            string_to_escape.clone(),
                        )),
                        1,
                        None,
                        Some(false),
                    )),
                    temper_std::regex::RegexNode::new(temper_std::regex::dot()),
                ])),
            );
            let string_to_escape_and_more =
                Arc::new(format!("a{string_to_escape}{string_to_escape}b"));
            let escaped_match =
                regex_with_interpolated_string.find(string_to_escape_and_more.clone(), None)?;
            println!("{}", escaped_match.full().value());
            let own_full_match = temper_std::regex::RegexNodeTrait::compiled(
                &temper_std::regex::Sequence::new(Arc::new(vec![
                    temper_std::regex::RegexNode::new(temper_std::regex::dot()),
                    temper_std::regex::RegexNode::new(temper_std::regex::Capture::new(
                        "full",
                        temper_std::regex::RegexNode::new(temper_std::regex::dot()),
                    )),
                ])),
            )
            .find("ab", None)?;
            let own_full = temper_core::MappedTrait::get(
                &own_full_match.groups(),
                Arc::new("full".to_string()),
            )?
            .value();
            println!("Own full: {own_full} vs {}", own_full_match.full().value());
            let combo_lines =
                Arc::new("It was the best\nof times.\r\n\rIt was the worst of times.".to_string());
            {
                let list: temper_core::List<Arc<String>> =
                    temper_std::regex::RegexNodeTrait::compiled(&temper_std::regex::Or::new(
                        Arc::new(vec![
                            temper_std::regex::RegexNode::new(temper_std::regex::CodePoints::new(
                                "\n",
                            )),
                            temper_std::regex::RegexNode::new(temper_std::regex::Sequence::new(
                                Arc::new(vec![
                                    temper_std::regex::RegexNode::new(
                                        temper_std::regex::CodePoints::new("\r"),
                                    ),
                                    temper_std::regex::RegexNode::new(
                                        temper_std::regex::Repeat::new(
                                            temper_std::regex::RegexNode::new(
                                                temper_std::regex::CodePoints::new("\n"),
                                            ),
                                            0,
                                            Some(1),
                                            Some(false),
                                        ),
                                    ),
                                ]),
                            )),
                        ]),
                    ))
                    .split(combo_lines.clone());
                let length2 = temper_core::ListedTrait::len(&list);
                let mut i2: i32 = 0;
                while i2 < length2 {
                    let line = temper_core::ListedTrait::get(&list, i2);
                    i2 = i2.wrapping_add(1);
                    println!("{line};");
                }
            }
            Ok(())
        })
        .clone()
}

static TEXT: OnceLock<Arc<String>> = OnceLock::new();

pub(crate) fn text() -> Arc<String> {
    crate::support::initialized(|| TEXT.get()).clone()
}

pub(crate) fn check_group(group: temper_std::regex::Group) {
    let name = group.name();
    let value = group.value();
    let begin = group.begin();
    let end = group.end();
    let size = Arc::new(temper_core::string::count_between(text(), begin, end).to_string());
    let begin = Arc::new(temper_core::string::count_between(text(), 0usize, begin).to_string());
    println!("match group '{name}': '{value}' at {begin} size {size}");
}

static MORE_TEXT: OnceLock<Arc<String>> = OnceLock::new();

pub(crate) fn more_text() -> Arc<String> {
    crate::support::initialized(|| MORE_TEXT.get()).clone()
}
