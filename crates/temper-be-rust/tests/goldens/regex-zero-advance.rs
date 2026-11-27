use std::convert::Infallible;
use std::sync::{Arc, OnceLock};
use temper_core::ToArcString;

pub(crate) fn init() -> temper_core::Result<()> {
    static INIT_ONCE: OnceLock<temper_core::Result<()>> = OnceLock::new();
    INIT_ONCE
        .get_or_init(|| {
            COMMA_ITEM_START_REGEX
                .set(temper_std::regex::RegexNodeTrait::compiled(&temper_std::regex::Sequence::new(
                    Arc::new(vec![
                        temper_std::regex::RegexNode::new(temper_std::regex::Or::new(Arc::new(vec![
                            temper_std::regex::RegexNode::new(temper_std::regex::begin()),
                            temper_std::regex::RegexNode::new(temper_std::regex::CodePoints::new(
                                ",",
                            )),
                        ]))),
                        temper_std::regex::RegexNode::new(temper_std::regex::Repeat::new(
                            temper_std::regex::RegexNode::new(temper_std::regex::space()),
                            0,
                            None,
                            Some(false),
                        )),
                    ]),
                )))
                .unwrap_or_else(|_| panic!());
            println!("{}", bulletize_list("apples, bananas, cherries"));
            Ok(())
        })
        .clone()
}

static COMMA_ITEM_START_REGEX: OnceLock<temper_std::regex::Regex> = OnceLock::new();

pub(crate) fn comma_item_start_regex() -> temper_std::regex::Regex {
    crate::support::initialized(|| COMMA_ITEM_START_REGEX.get()).clone()
}

pub fn bulletize_list(text: impl ToArcString) -> Arc<String> {
    let text = text.to_arc_string();
    let replaced = temper_core::into_ok(
        comma_item_start_regex().replace::<Infallible>(
            text.clone(),
            Arc::new(move |_match: temper_std::regex::Match| Ok(Arc::new("\n- ".to_string()))),
        ),
    );
    temper_core::string::slice(
        &replaced,
        temper_core::string::next(&replaced, 0usize),
        replaced.len(),
    )
}
