//! Temper's operators: precedence, associativity, arity and how tokens
//! match them, as in Kotlin Temper's `Operator.kt`.
//!
//! Almost everything in Temper is an operator: calls and blocks are infix
//! brackets (`f(x)`, `f { }`), declarations are juxtaposed words, and
//! keywords like `if` are plain names. The parser picks among the operators
//! a token may be using these tables and [`can_nest`](crate::parse).

use crate::lex::TokenKind;
use crate::names::Fnv;
use std::collections::HashMap;
use std::hash::BuildHasherDefault;
use std::sync::OnceLock;

/// Where an operator's token goes relative to its operands.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum OpType {
    /// Between operands, like `+`.
    Infix,
    /// After its operand, like postfix `++`.
    Postfix,
    /// Before its operand, like `!` or `{` starting a block.
    Prefix,
    /// Between operands, any of which may be missing, like `,` and `;`.
    Separator,
    /// No operands: a run of value tokens.
    Nullary,
}

/// How `temper fmt` spaces an operator's token from what's next to it on a
/// line: around it if infix, after it if prefix or a separator, inside if
/// brackets. Followers, like the `,` of `extends A, B`, are spaced as their
/// own operators ([`Operator::spacing`]). docs/tools/fmt.md's table of
/// operator spacing is checked against these.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Spacing {
    /// One space: `a + b`, `{ a }`.
    Spaced,
    /// None: `a.b`, `-a`, `f(a)`.
    Tight,
    /// None before, one after: `a, b`, `x: Int`.
    After,
    /// As written, but a run of spaces and tabs becomes one: `a..b`,
    /// `A::B`, a label's `:`, postfix `!`, and words like `is`, which need
    /// their space.
    AsWritten,
}

/// Facts about an operator.
#[derive(Clone, Copy, Debug)]
pub struct Info {
    pub precedence: i32,
    /// Token text, or "" for operators without one (root, leaves, strings).
    pub text: &'static str,
    pub op_type: OpType,
    pub right_assoc: bool,
    /// Brackets: the operator's tokens include an open and a close bracket.
    pub closer: bool,
    /// For operators that take more operands after a follower token, like
    /// `extends A, B`: that token.
    pub follower: Option<&'static str>,
    pub min_arity: u32,
    pub max_arity: u32,
    /// Whether a longer punctuation token may act as this operator, like
    /// `+=` as `=`, or a custom `<+>` as `<`.
    pub customizable: bool,
    /// How `temper fmt` spaces it: by default one space around infix
    /// punctuation and none after prefix punctuation, or inside brackets;
    /// words and the rest as written.
    pub spacing: Spacing,
}

const MANY: u32 = u32::MAX;

const fn op(precedence: i32, text: &'static str, op_type: OpType) -> Info {
    let (min_arity, max_arity) = match op_type {
        OpType::Infix => (2, 2),
        OpType::Postfix | OpType::Prefix => (1, 1),
        OpType::Separator => (0, MANY),
        OpType::Nullary => (0, 0),
    };
    let first = if text.is_empty() {
        b'a'
    } else {
        text.as_bytes()[0]
    };
    let punctuation = !first.is_ascii_alphabetic() && !text.is_empty();
    Info {
        precedence,
        text,
        op_type,
        right_assoc: false,
        closer: false,
        follower: None,
        min_arity,
        max_arity,
        customizable: punctuation,
        spacing: match op_type {
            OpType::Infix if punctuation => Spaced,
            OpType::Prefix if punctuation => Tight,
            _ => AsWritten,
        },
    }
}

impl Info {
    const fn right(self) -> Info {
        Info {
            right_assoc: true,
            ..self
        }
    }
    const fn closer(self) -> Info {
        Info {
            closer: true,
            spacing: Tight,
            ..self
        }
    }
    const fn spacing(self, spacing: Spacing) -> Info {
        Info { spacing, ..self }
    }
    const fn arity(self, min_arity: u32, max_arity: u32) -> Info {
        Info {
            min_arity,
            max_arity,
            ..self
        }
    }
    const fn min(self, min_arity: u32) -> Info {
        Info { min_arity, ..self }
    }
    const fn follower(self, follower: &'static str) -> Info {
        Info {
            follower: Some(follower),
            max_arity: MANY,
            ..self
        }
    }
    const fn fixed(self) -> Info {
        Info {
            customizable: false,
            ..self
        }
    }
}

macro_rules! operators {
    ($($name:ident = $info:expr,)*) => {
        /// An operator, named as in Kotlin Temper.
        #[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
        pub enum Operator { $($name,)* }

        const INFO: &[Info] = &[$($info,)*];
        const ALL: &[Operator] = &[$(Operator::$name,)*];
    };
}

use OpType::{Infix, Nullary, Postfix, Prefix, Separator};
use Spacing::{After, AsWritten, Spaced, Tight};

// Order matters where several operators share a token: earlier ones are
// tried first, as `Angle` before `Lt`.
operators! {
    Root = op(i32::MIN, "", Prefix).arity(0, MANY),
    SemiSemiSemi = op(-6, ";;;", Separator).right(),
    SemiSemi = op(-5, ";;", Separator).right(),
    Arrow = op(-5, "=>", Separator).right().spacing(Spaced),
    Semi = op(-4, ";", Separator).right().spacing(After),
    // Only for markers like `mixin("name"):` between statements.
    SelectivePostColon = op(-3, ":", Postfix).fixed(),
    ThinArrow = op(-2, "->", Infix).right(),
    InfixGiven = op(-1, "given", Infix),
    PrefixGiven = op(-1, "given", Prefix),
    Return = op(-1, "return", Prefix).min(0),
    Throw = op(-1, "throw", Prefix),
    Yield = op(-1, "yield", Prefix).min(0),
    Of = op(-1, "of", Infix).right(),
    Comma = op(0, ",", Separator).right().spacing(After),
    // `label: stmt`, below comma so `{ a: b, c: d }` works as a bag.
    LowColon = op(1, ":", Infix).fixed().spacing(AsWritten),
    PreCase = op(1, "case", Prefix),
    At = op(2, "@", Prefix).arity(1, 2),
    Eq = op(3, "=", Infix).right(),
    PlusEq = op(3, "+=", Infix).right(),
    DashEq = op(3, "-=", Infix).right(),
    StarEq = op(3, "*=", Infix).right(),
    SlashEq = op(3, "/=", Infix).right(),
    PctEq = op(3, "%=", Infix).right(),
    AmpEq = op(3, "&=", Infix).right(),
    CaretEq = op(3, "^=", Infix).right(),
    BarEq = op(3, "|=", Infix).right(),
    AmpAmpEq = op(3, "&&=", Infix).right(),
    BarBarEq = op(3, "||=", Infix).right(),
    LtLtEq = op(3, "<<=", Infix).right(),
    GtGtEq = op(3, ">>=", Infix).right(),
    GtGtGtEq = op(3, ">>>=", Infix).right(),
    OrElse = op(4, "orelse", Infix).right(),
    // Between a call ending in a block and a word continuing it: `} else {`.
    CallJoin = op(5, "callJoin:", Infix).right(),
    ExtendsComma = op(6, "extends", Infix).follower(","),
    ImplementsComma = op(6, "implements", Infix).follower(","),
    ForbidsComma = op(6, "forbids", Infix).follower(","),
    SupportsComma = op(6, "supports", Infix).follower(","),
    ExtendsNoComma = op(6, "extends", Infix),
    ImplementsNoComma = op(6, "implements", Infix),
    ForbidsNoComma = op(6, "forbids", Infix),
    SupportsNoComma = op(6, "supports", Infix),
    // `name: Type`.
    HighColon = op(7, ":", Infix).right().spacing(After),
    Throws = op(8, "throws", Infix).follower("|"),
    Coalesce = op(9, "??", Infix),
    BarBar = op(10, "||", Infix).right(),
    AmpAmp = op(11, "&&", Infix).right(),
    EqEq = op(12, "==", Infix),
    NotEq = op(12, "!=", Infix),
    EqEqEq = op(12, "===", Infix),
    NotEqEq = op(12, "!==", Infix),
    EqTilde = op(12, "=~", Infix).right(),
    BangTilde = op(12, "!~", Infix).right(),
    // Before `Lt`, which shares its token; the lexer's bracket flag decides.
    Angle = op(24, "<", Infix).closer().arity(1, MANY),
    Lt = op(13, "<", Infix),
    Le = op(13, "<=", Infix),
    Gt = op(13, ">", Infix),
    Ge = op(13, ">=", Infix),
    In = op(13, "in", Infix).min(1),
    Instanceof = op(13, "instanceof", Infix),
    Is = op(13, "is", Infix),
    PreIs = op(13, "is", Prefix),
    As = op(13, "as", Infix),
    Bar = op(14, "|", Infix).right(),
    Caret = op(15, "^", Infix).right(),
    Amp = op(16, "&", Infix).right(),
    LtLt = op(17, "<<", Infix),
    GtGt = op(17, ">>", Infix),
    GtGtGt = op(17, ">>>", Infix),
    ColonColon = op(18, "::", Infix).right().spacing(AsWritten),
    Plus = op(19, "+", Infix),
    Dash = op(19, "-", Infix),
    Star = op(20, "*", Infix),
    Slash = op(20, "/", Infix),
    Pct = op(20, "%", Infix),
    Tilde = op(20, "~", Infix),
    StarStar = op(21, "**", Infix),
    PreIncr = op(22, "++", Prefix),
    PreDecr = op(22, "--", Prefix),
    PrePlus = op(22, "+", Prefix),
    PreDash = op(22, "-", Prefix),
    Bang = op(22, "!", Prefix),
    PreTilde = op(22, "~", Prefix),
    PreAmp = op(22, "&", Prefix),
    PreStar = op(22, "*", Prefix).min(0),
    Ellipsis = op(22, "...", Prefix).min(0),
    Await = op(22, "await", Prefix),
    PostIncr = op(23, "++", Postfix),
    PostDecr = op(23, "--", Postfix),
    PostBang = op(23, "!", Postfix),
    PostQuest = op(23, "?", Postfix),
    Dot = op(24, ".", Infix).spacing(Tight),
    ChainNull = op(24, "?.", Infix).spacing(Tight),
    DotDot = op(24, "..", Infix).right().spacing(AsWritten),
    CurlyGroup = op(24, "{", Prefix).closer().arity(0, MANY).spacing(Spaced),
    ParenGroup = op(24, "(", Prefix).closer().arity(0, MANY),
    SquareGroup = op(24, "[", Prefix).closer().min(0),
    Curly = op(24, "{", Infix).closer().arity(1, MANY).spacing(Spaced),
    Paren = op(24, "(", Infix).closer().arity(1, MANY),
    Square = op(24, "[", Infix).closer(),
    Esc = op(24, "\\", Prefix),
    EscParen = op(24, "\\(", Prefix).closer().spacing(AsWritten),
    EscCurly = op(24, "\\{", Prefix).closer().spacing(AsWritten),
    DollarCurly = op(24, "${", Prefix).closer().arity(0, 1),
    UnicodeRun = op(24, "\\u{", Prefix).closer().arity(0, MANY).spacing(AsWritten),
    New = op(24, "new", Prefix).min(0),
    // A quoted string's parts: delimiters, content and holes.
    QuotedGroup = op(25, "", Prefix).closer().arity(0, MANY),
    // A run of value tokens, like `1` or `let x`.
    Leaf = op(i32::MAX, "", Nullary),
}

impl Operator {
    pub fn info(self) -> &'static Info {
        &INFO[self as usize]
    }

    pub fn op_type(self) -> OpType {
        self.info().op_type
    }

    pub fn closer(self) -> bool {
        self.info().closer
    }

    /// A bracket operator whose token is an open bracket.
    pub fn is_bracket(self) -> bool {
        self.closer() && OPEN_BRACKETS.contains(&self.info().text)
    }

    /// The close bracket for this operator's open bracket, if it has one.
    pub fn partner(self) -> Option<&'static str> {
        let i = OPEN_BRACKETS.iter().position(|&t| t == self.info().text)?;
        Some(CLOSE_BRACKET_PARTNERS[i])
    }

    /// How `temper fmt` spaces this operator's token `text`: as the
    /// operator does, a longer form too (`:=` as `=`); but a follower, like
    /// the `,` of `extends A, B` or the `|` of `throws A | B`, as the
    /// operator it is alone, and a bracket's longer form (`</` as `<`),
    /// which isn't a bracket, as written.
    pub fn spacing(self, text: &str) -> Spacing {
        let info = self.info();
        if text == info.text {
            info.spacing
        } else if info.follower == Some(text) {
            by_text(text)
                .first()
                .map_or(info.spacing, |alone| alone.info().spacing)
        } else if info.closer && Some(text) != self.partner() {
            Spacing::AsWritten
        } else {
            info.spacing
        }
    }

    /// The operators of `op_type` that a token may be, in order of preference.
    pub fn matching(text: &str, kind: TokenKind, op_type: OpType) -> Matches {
        let mut matches = Matches::default();
        if !matches!(
            kind,
            TokenKind::Punctuation
                | TokenKind::LeftDelimiter
                | TokenKind::RightDelimiter
                | TokenKind::Word
        ) {
            return matches;
        }
        // The token itself, then for infix `x=` plain `=`, then shorter
        // prefixes of punctuation, so custom operators like `<+>` work.
        if add_matches(&mut matches, text, text, op_type)
            || kind != TokenKind::Punctuation
            || (op_type == OpType::Infix
                && text.ends_with('=')
                && add_matches(&mut matches, text, "=", op_type))
            || is_operator_text(text)
        {
            return matches;
        }
        let mut prefix = text;
        while let Some((last, _)) = prefix.char_indices().next_back() {
            if last == 0 {
                break;
            }
            prefix = &prefix[..last];
            if add_matches(&mut matches, text, prefix, op_type) || is_operator_text(prefix) {
                break;
            }
        }
        matches
    }

    /// The operators of each type that a token may be, as [`Self::matching`].
    pub fn matching_all(text: &str, kind: TokenKind) -> AllMatches {
        let mut all = AllMatches::default();
        let exact = by_text(text);
        if kind == TokenKind::Punctuation && exact.is_empty() {
            // Maybe a longer form of an operator.
            for op_type in [
                OpType::Prefix,
                OpType::Infix,
                OpType::Postfix,
                OpType::Separator,
            ] {
                *all.get_mut(op_type) = Operator::matching(text, kind, op_type);
            }
        } else if matches!(
            kind,
            TokenKind::Punctuation
                | TokenKind::LeftDelimiter
                | TokenKind::RightDelimiter
                | TokenKind::Word
        ) {
            for &op in exact {
                all.get_mut(op.op_type()).push(op);
            }
        }
        all
    }
}

/// The operators a token may be, by type.
#[derive(Clone, Copy, Debug, Default)]
pub struct AllMatches {
    pub prefix: Matches,
    pub infix: Matches,
    pub postfix: Matches,
    pub separator: Matches,
    nullary: Matches,
}

impl AllMatches {
    fn get_mut(&mut self, op_type: OpType) -> &mut Matches {
        match op_type {
            OpType::Prefix => &mut self.prefix,
            OpType::Infix => &mut self.infix,
            OpType::Postfix => &mut self.postfix,
            OpType::Separator => &mut self.separator,
            OpType::Nullary => &mut self.nullary,
        }
    }
}

/// Adds operators for `candidate`, a form of the token `text`. Returns
/// whether there were any.
fn add_matches(matches: &mut Matches, text: &str, candidate: &str, op_type: OpType) -> bool {
    let exact = candidate == text;
    for &op in by_text(candidate) {
        let info = op.info();
        if info.op_type == op_type && (exact || info.customizable) {
            matches.push(op);
        }
    }
    matches.len > 0
}

fn is_operator_text(text: &str) -> bool {
    !by_text(text).is_empty()
}

/// The operators with a token text, in order.
fn by_text(text: &str) -> &'static [Operator] {
    type Table = HashMap<&'static str, Vec<Operator>, BuildHasherDefault<Fnv>>;
    static TABLE: OnceLock<Table> = OnceLock::new();
    if text.len() > LONGEST_TEXT {
        return &[];
    }
    let table = TABLE.get_or_init(|| {
        let mut table = Table::default();
        for (i, info) in INFO
            .iter()
            .enumerate()
            .filter(|(_, info)| !info.text.is_empty())
        {
            table.entry(info.text).or_default().push(ALL[i]);
        }
        table
    });
    table.get(text).map_or(&[], Vec::as_slice)
}

/// The length of the longest operator text, `instanceof`.
const LONGEST_TEXT: usize = 10;

/// Open brackets and, at the same index, their partners.
pub const OPEN_BRACKETS: [&str; 9] = ["(", "{", "[", "<", "</", "\\(", "\\{", "${", "\\u{"];
const CLOSE_BRACKET_PARTNERS: [&str; 9] = [")", "}", "]", ">", ">", ")", "}", "}", "}"];

pub fn is_open_bracket(text: &str) -> bool {
    OPEN_BRACKETS.contains(&text)
}

pub fn is_close_bracket(text: &str) -> bool {
    matches!(text, ")" | "}" | "]" | ">")
}

/// Tokens that may continue an operator with more operands, like the `,` in
/// `extends A, B`.
pub fn is_follower(text: &str) -> bool {
    text == "," || text == "|"
}

/// Up to three operators matching a token.
#[derive(Clone, Copy, Debug, Default)]
pub struct Matches {
    ops: [Option<Operator>; 3],
    len: usize,
}

impl Matches {
    fn push(&mut self, op: Operator) {
        self.ops[self.len] = Some(op);
        self.len += 1;
    }

    pub fn is_empty(&self) -> bool {
        self.len == 0
    }

    pub fn iter(&self) -> impl Iterator<Item = Operator> + '_ {
        self.ops[..self.len].iter().flatten().copied()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn names(text: &str, kind: TokenKind, op_type: OpType) -> Vec<Operator> {
        Operator::matching(text, kind, op_type).iter().collect()
    }

    /// docs/tools/fmt.md's table of operator spacing, rendered from the
    /// operators' [`Spacing`]: each class's operators, in the table's
    /// order, a bracket as its pair, and a text that's in more than one
    /// class marked with its kind.
    fn spacing_table() -> String {
        // The operators written in code (`callJoin:` the parser inserts).
        let written: Vec<Operator> = (ALL.iter().copied())
            .filter(|&op| !op.info().text.is_empty() && op != Operator::CallJoin)
            .collect();
        let entry = |op: Operator| match op.partner() {
            Some(close) if op.closer() => format!("{} {close}", op.info().text),
            _ => op.info().text.to_string(),
        };
        let kind = |op: Operator| match op {
            Operator::LowColon => "label",
            Operator::HighColon => "type",
            Operator::SelectivePostColon => "marker",
            _ => match op.op_type() {
                Prefix => "prefix",
                Postfix => "postfix",
                _ => "infix",
            },
        };
        let rows = [
            (Spaced, "One space around, or inside"),
            (Tight, "None around, after, or inside"),
            (After, "None before, one after"),
            (
                AsWritten,
                "As written (a run of spaces and tabs becomes one)",
            ),
        ];
        let mut table = "| Spacing | Operators |\n|---|---|\n".to_string();
        for (spacing, what) in rows {
            let mut cells: Vec<String> = Vec::new();
            for &op in &written {
                if op.info().spacing != spacing {
                    continue;
                }
                let text = entry(op);
                let shared =
                    (written.iter()).any(|&o| entry(o) == text && o.info().spacing != spacing);
                let cell = format!("`{}`", text.replace('|', "\\|"));
                let cell = match shared {
                    true => format!("{cell} ({})", kind(op)),
                    false => cell,
                };
                if !cells.contains(&cell) {
                    cells.push(cell);
                }
            }
            table += &format!("| {what} | {} |\n", cells.join(", "));
        }
        table
    }

    /// The docs' table of how `temper fmt` spaces each operator can't
    /// drift from the operators' declared [`Spacing`].
    #[test]
    fn docs_spacing_table_is_the_operators() {
        let path = concat!(env!("CARGO_MANIFEST_DIR"), "/../../docs/tools/fmt.md");
        let docs = std::fs::read_to_string(path).unwrap().replace("\r\n", "\n");
        let table = spacing_table();
        assert!(
            docs.contains(&table),
            "docs/tools/fmt.md's table of operator spacing should read:\n\n{table}"
        );
    }

    #[test]
    fn longest_text_is_longest() {
        assert_eq!(INFO.iter().map(|i| i.text.len()).max(), Some(LONGEST_TEXT));
    }

    #[test]
    fn matching_prefers_exact_then_assignment_then_prefixes() {
        use Operator as O;
        let p = TokenKind::Punctuation;
        assert_eq!(names("<", p, Infix), [O::Angle, O::Lt]);
        assert_eq!(names(":", p, Infix), [O::LowColon, O::HighColon]);
        assert_eq!(names(":=", p, Infix), [O::Eq]);
        assert_eq!(names("<+>", p, Infix), [O::Angle, O::Lt]);
        assert_eq!(names("++", p, Infix), []);
        assert_eq!(
            names("extends", TokenKind::Word, Infix),
            [O::ExtendsComma, O::ExtendsNoComma]
        );
        assert_eq!(names("x", TokenKind::Word, Infix), []);
        assert_eq!(names("-", TokenKind::QuotedString, Prefix), []);
        for (text, kind) in [
            ("<", p),
            ("?=", p),
            ("<+>", p),
            ("-", p),
            ("is", TokenKind::Word),
        ] {
            let all = Operator::matching_all(text, kind);
            for (op_type, got) in [
                (Prefix, all.prefix),
                (Infix, all.infix),
                (Postfix, all.postfix),
            ] {
                assert_eq!(
                    got.iter().collect::<Vec<_>>(),
                    names(text, kind, op_type),
                    "{text}"
                );
            }
        }
    }
}
