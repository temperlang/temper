//! Splits Temper source into tokens, matching Kotlin Temper's lexer.
//!
//! The lexer is a small state machine over a stack of open delimiters. The
//! stack decides whether the next characters are code tokens or string
//! content: after `"` we're in a string, after `${` in code again, after the
//! `}` that closes it back in the string. Multi-quoted strings (`"""`) have
//! content lines that start with a margin character: `"` or `~` for text,
//! `:` for a statement fragment (a *scriptlet*), so blocks opened in one
//! scriptlet can close in a later one.
//!
//! Malformed input still produces tokens: bad characters become `Error`
//! tokens, and missing closers are inserted as zero-width *synthetic* tokens.
//!
//! Literate `.temper.md` files lex their prose as comment tokens, with an
//! extra synthetic `/**? ... ?*/` comment per paragraph, so that later stages
//! can use prose next to definitions as documentation.
//!
//! The lexer knows no keywords, except that `return`, `throw` and `yield`
//! may precede a regular expression literal like `/a+/`, where other words
//! precede division.

use crate::id_parts::{self, CONTINUE, MEDIAL, START};
use crate::literate::{self, Block};
use crate::nfkc;
use std::borrow::Cow;

/// What kind of source text to lex.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum SourceKind {
    /// A `.temper` file, all code.
    Standalone,
    /// A literate `.temper.md` file, prose with code blocks.
    Markdown,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum TokenKind {
    Comment,
    Number,
    Punctuation,
    /// An open quote, like `"`, `"""` or the `/` before a regex.
    LeftDelimiter,
    /// A close quote.
    RightDelimiter,
    /// String content.
    QuotedString,
    /// A multi-quoted string's margin character, `"`, `~` or `:`.
    Margin,
    Space,
    Word,
    Error,
}

impl TokenKind {
    /// Whether the token could be replaced by a space without changing meaning.
    pub fn is_ignorable(self) -> bool {
        matches!(
            self,
            TokenKind::Comment | TokenKind::Margin | TokenKind::Space
        )
    }
}

/// Why a token is malformed, named as in Kotlin Temper's message templates.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum LexError {
    BadEmoji,
    InvalidIdentifier,
    MalformedNumber,
    /// A word not in Unicode NFKC.
    UnnormalizedIdentifier,
    UnmatchedBracket,
    UnrecognizedStringEscape,
    UnrecognizedToken,
    UnsupportedNestedMultiQuote,
}

/// A token's kind and byte span. Its text is the source in the span, except
/// for synthetic tokens; see [`Token::text`].
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Token {
    pub kind: TokenKind,
    /// Byte range in the source. Synthetic tokens are zero-width, except
    /// paragraph comments, which span their paragraph.
    pub start: u32,
    pub end: u32,
    /// For tokens not in the source, what they stand for.
    pub synthetic: Option<Synthetic>,
    /// True for tokens the parser may treat as brackets, including `<` and
    /// `>` when they look like angle brackets, and string delimiters.
    pub may_bracket: bool,
    pub error: Option<LexError>,
}

/// What a synthetic token stands for: an inserted closer, the margin and
/// line break that close blocks left open by scriptlets, or a literate
/// file's paragraph as a doc comment.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Synthetic {
    DoubleQuote,
    SingleQuote,
    Backtick,
    Slash,
    TripleQuote,
    RightCurly,
    Colon,
    LineBreak,
    /// A `/**? ... ?*/` comment made from the paragraph the token spans.
    Paragraph,
}

impl Synthetic {
    /// The text, except for paragraphs, which need their source.
    pub fn text(self) -> &'static str {
        match self {
            Synthetic::DoubleQuote => "\"",
            Synthetic::SingleQuote => "'",
            Synthetic::Backtick => "`",
            Synthetic::Slash => "/",
            Synthetic::TripleQuote => "\"\"\"",
            Synthetic::RightCurly => "}",
            Synthetic::Colon => ":",
            Synthetic::LineBreak => "\n",
            Synthetic::Paragraph => "",
        }
    }
}

impl Token {
    pub fn range(self) -> std::ops::Range<usize> {
        self.start as usize..self.end as usize
    }

    /// The token's text, given the source it was lexed from.
    pub fn text(self, source: &str) -> Cow<'_, str> {
        match self.synthetic {
            None => Cow::Borrowed(&source[self.range()]),
            Some(Synthetic::Paragraph) => {
                Cow::Owned(doc_comment(&source[self.range()]).unwrap_or_default())
            }
            Some(synthetic) => Cow::Borrowed(synthetic.text()),
        }
    }
}

/// Splits `text` into tokens.
pub fn lex(text: &str, kind: SourceKind) -> Vec<Token> {
    let markdown = kind == SourceKind::Markdown;
    let mut lexer = Lexer {
        text: text.to_string(),
        start: 0,
        end: 0,
        mode: if markdown { Mode::Prose } else { Mode::Code },
        stack: Vec::new(),
        scriptlets: Vec::new(),
        hole_lines: Vec::new(),
        popped_hole_line: None,
        line_kind: None,
        pre_div: false,
        last_kind: None,
        open_angles: 0,
        kind: None,
        error: None,
        blocks: if markdown {
            literate::find_blocks(text)
        } else {
            Vec::new()
        },
        block_index: 0,
        tokens: Vec::new(),
    };
    while lexer.end < lexer.text.len() {
        lexer.next_token();
    }
    // Close whatever is still open.
    while !lexer.stack.is_empty() {
        let end = lexer.end;
        lexer.cluster(end, end, Chunk::End);
    }
    lexer.tokens
}

/// What the lexer is in the middle of.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum Mode {
    Code,
    BlockComment,
    LineComment,
    /// String content, with the kind of string given by the stack.
    String,
    /// After a line break in a multi-quoted string, looking for a margin.
    Continuation,
    /// Markdown prose.
    Prose,
    /// Inside `\u{...}`: comma-separated code points.
    Unicode,
}

/// The kind of content on a multi-quoted string's current line.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum LineKind {
    /// After a `"` or `~` margin.
    Chars,
    /// After a `:` margin.
    StmtFragment,
}

/// An open delimiter on the stack.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum Opener {
    /// A single-character quote: `"`, `'`, `` ` `` or regex `/`. `left` is
    /// false for the rare close quote pushed after recovering from bad nesting.
    Quote {
        delim: u8,
        left: bool,
    },
    MultiQuote,
    /// `${` in a string.
    Hole,
    /// `\u{` in a string.
    Unicode,
    /// `{` in code.
    Curly,
}

/// An input to the delimiter stack: an opener, a `}`, a margin, a line
/// break inside a string, or the end of input.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum Chunk {
    Open(Opener),
    RightCurly,
    MarginStmt,
    MarginChars,
    LineBreak,
    End,
}

/// Openers grouped as the stack transitions see them.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum Group {
    Quote,
    Backtick,
    MultiQuote,
    Hole,
    Unicode,
    Curly,
}

fn group(opener: Opener) -> Group {
    match opener {
        Opener::Quote { delim: b'`', .. } => Group::Backtick,
        Opener::Quote { .. } => Group::Quote,
        Opener::MultiQuote => Group::MultiQuote,
        Opener::Hole => Group::Hole,
        Opener::Unicode => Group::Unicode,
        Opener::Curly => Group::Curly,
    }
}

// Stack changes, applied in this order.
const BAD: u16 = 1 << 0; // Mark the token as an error; no other effect.
const STORE: u16 = 1 << 1; // Stash openers above the `"""` until the next scriptlet.
const CONS1: u16 = 1 << 2; // Keep only the first character of the token.
const SYN: u16 = 1 << 3; // Pop, inserting a synthetic closer.
const POP: u16 = 1 << 4;
const PUSH: u16 = 1 << 5;
const RESTORE: u16 = 1 << 6; // Bring back openers stashed by STORE.
const RESET_LINE: u16 = 1 << 7; // Back to a chars line if a `"""` is on top.
const UNSET_LINE: u16 = 1 << 8;
const REPROC: u16 = 1 << 9; // Process the chunk again against the new top.

/// How the stack changes on seeing `chunk` with `top` on top.
fn transition(top: Option<Group>, chunk: Chunk) -> u16 {
    use Group as G;
    let incoming = match chunk {
        Chunk::Open(opener) => Some(group(opener)),
        _ => None,
    };
    match (top, incoming, chunk) {
        (Some(G::Quote), Some(G::Quote), _) => POP,
        (Some(G::Quote), Some(G::MultiQuote), _) => BAD,
        (Some(G::Quote | G::Backtick), Some(G::Hole | G::Unicode), _) => PUSH,
        (Some(G::Quote), _, Chunk::LineBreak | Chunk::MarginChars | Chunk::MarginStmt) => {
            SYN | REPROC
        }
        (Some(G::Backtick), Some(G::Backtick), _) => POP,

        (Some(G::MultiQuote), Some(G::MultiQuote | G::Hole), _) => PUSH | UNSET_LINE,
        (Some(G::MultiQuote), Some(G::Curly | G::Unicode | G::Quote), _) => PUSH,
        (Some(G::MultiQuote), _, Chunk::RightCurly) => BAD,
        (Some(G::MultiQuote), _, Chunk::MarginStmt) => RESTORE,
        (Some(G::MultiQuote), _, Chunk::MarginChars) => STORE,

        (Some(G::Hole), Some(G::Hole), _) => CONS1 | BAD,
        (Some(G::Hole), Some(G::Quote | G::Backtick | G::MultiQuote | G::Curly), _) => PUSH,
        (Some(G::Hole), _, Chunk::MarginStmt | Chunk::MarginChars) => BAD,
        (Some(G::Hole), _, Chunk::RightCurly) => POP | RESET_LINE,

        (Some(G::Unicode), Some(G::Hole), _) => PUSH,
        (Some(G::Unicode), _, Chunk::MarginStmt) => POP | REPROC,
        (Some(G::Unicode), _, Chunk::RightCurly) => POP,

        (Some(G::Curly), Some(G::MultiQuote), _) => PUSH | UNSET_LINE,
        (Some(G::Curly), Some(G::Quote | G::Backtick | G::Hole | G::Curly), _) => PUSH,
        (Some(G::Curly), _, Chunk::RightCurly) => POP,
        (Some(G::Curly), _, Chunk::MarginChars) => STORE,

        (Some(_), _, Chunk::End) => SYN | REPROC,
        (None, Some(G::Unicode), _) => 0,
        (None, Some(_), _) => PUSH,
        (None, _, Chunk::RightCurly) => BAD,
        _ => 0,
    }
}

/// The synthetic closer for an opener, and its kind.
fn closer(opener: Opener) -> Option<(Synthetic, TokenKind)> {
    match opener {
        Opener::Quote { delim: b'"', .. } => {
            Some((Synthetic::DoubleQuote, TokenKind::RightDelimiter))
        }
        Opener::Quote { delim, left: true } => {
            let closer = match delim {
                b'\'' => Synthetic::SingleQuote,
                b'`' => Synthetic::Backtick,
                _ => Synthetic::Slash,
            };
            Some((closer, TokenKind::RightDelimiter))
        }
        Opener::Quote { left: false, .. } => None,
        Opener::MultiQuote => Some((Synthetic::TripleQuote, TokenKind::RightDelimiter)),
        Opener::Hole | Opener::Unicode | Opener::Curly => {
            Some((Synthetic::RightCurly, TokenKind::Punctuation))
        }
    }
}

/// How the first character classifies a token.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum CharKind {
    Backslash,
    Digit,
    Dollar,
    Dot,
    LineBreak,
    /// May start a multi-character operator.
    Punctuation,
    /// Always a token by itself: brackets and comma.
    Standalone,
    Quote,
    Semi,
    Slash,
    Space,
    WordStart,
    Error,
}

fn classify(c: char) -> CharKind {
    match c {
        '0'..='9' => CharKind::Digit,
        // ASCII first, to skip the table search for most characters.
        'a'..='z' | 'A'..='Z' | '_' => CharKind::WordStart,
        _ if !c.is_ascii() && id_parts::contains(START, c) => CharKind::WordStart,
        '.' => CharKind::Dot,
        '/' => CharKind::Slash,
        '\\' => CharKind::Backslash,
        ';' => CharKind::Semi,
        '$' => CharKind::Dollar,
        '"' | '\'' | '`' => CharKind::Quote,
        ' ' | '\t' => CharKind::Space,
        '\n' | '\r' => CharKind::LineBreak,
        '(' | ')' | '{' | '}' | '[' | ']' | ',' => CharKind::Standalone,
        '~' | '!' | '@' | '#' | '%' | '^' | '&' | '*' | '-' | '=' | '+' | '|' | '<' | '>' | '?'
        | ':' => CharKind::Punctuation,
        _ => CharKind::Error,
    }
}

fn is_line_break(b: u8) -> bool {
    b == b'\n' || b == b'\r'
}

fn is_continue(c: char) -> bool {
    if c.is_ascii() {
        c.is_ascii_alphanumeric() || c == '_'
    } else {
        id_parts::contains(CONTINUE, c)
    }
}

/// The words after which `/` starts a regex rather than dividing.
const VALUE_PRECEDING_WORDS: [&str; 3] = ["return", "throw", "yield"];

/// The emoji presentation selector, which may follow a keycap digit or `#`.
const EMOJI_SELECTOR: char = '\u{FE0F}';

struct Lexer {
    /// Own copy of the source, so the lexer needs no lifetime.
    text: String,
    /// Start and end of the token being scanned.
    start: usize,
    end: usize,
    mode: Mode,
    stack: Vec<Opener>,
    /// Per open multi-quoted string, openers stashed until its next
    /// scriptlet line, bottom first.
    scriptlets: Vec<Vec<Opener>>,
    /// Per open hole, the line kind where it opened, to return to.
    hole_lines: Vec<Option<LineKind>>,
    /// The line kind saved by the last hole popped.
    popped_hole_line: Option<LineKind>,
    line_kind: Option<LineKind>,
    /// Whether a `/` here means division rather than a regex.
    pre_div: bool,
    last_kind: Option<TokenKind>,
    /// How many `<` angle brackets are open, so `>>` can close two.
    open_angles: u32,
    /// Kind and error of the token being scanned.
    kind: Option<TokenKind>,
    error: Option<LexError>,
    /// Markdown blocks for literate files, and the code block we're at.
    blocks: Vec<Block>,
    block_index: usize,
    tokens: Vec<Token>,
}

impl Lexer {
    fn byte(&self, i: usize) -> u8 {
        self.text.as_bytes().get(i).copied().unwrap_or(0)
    }

    fn char_at(&self, i: usize) -> char {
        self.text[i..].chars().next().unwrap_or('\0')
    }

    fn at_line_start(&self, i: usize) -> bool {
        i == 0 || is_line_break(self.byte(i - 1))
    }

    fn fail(&mut self, error: LexError) {
        self.kind = Some(TokenKind::Error);
        self.error = Some(error);
    }

    /// If a temper code block ends at `pos`, the end of its closing fence.
    fn code_end_at(&self, pos: usize) -> Option<usize> {
        let block = self.blocks.get(self.block_index)?;
        (block.end.start == pos).then_some(block.end.end)
    }

    /// If a temper code block starts at `pos`, the end of its opening fence.
    /// Moves forward through the blocks as positions increase.
    fn code_start_at(&mut self, pos: usize) -> Option<usize> {
        while self
            .blocks
            .get(self.block_index)
            .is_some_and(|b| b.begin.start < pos)
        {
            self.block_index += 1;
        }
        let block = self.blocks.get(self.block_index)?;
        (block.begin.start == pos && block.is_temper_code()).then_some(block.begin.end)
    }

    /// The innermost open quote: its delimiter and whether it's `"""`.
    fn string_context(&self) -> (u8, bool) {
        for opener in self.stack.iter().rev() {
            match *opener {
                Opener::Quote { delim, left: true } => return (delim, false),
                Opener::MultiQuote => return (b'"', true),
                _ => {}
            }
        }
        (0, false)
    }

    /// Whether `c`, just consumed, starts a `${` hole in the current string.
    fn starts_hole(&self, c: u8) -> bool {
        let (delim, _) = self.string_context();
        c == b'$' && self.byte(self.end) == b'{' && (delim == b'"' || delim == b'`')
    }

    fn next_token(&mut self) {
        self.start = self.end;
        self.kind = None;
        self.error = None;
        let mut mode_was = self.mode;
        loop {
            // A code block's closing fence interrupts any token.
            if self.mode != Mode::Prose && self.at_line_start(self.end) {
                if let Some(fence_end) = self.code_end_at(self.end) {
                    if self.start < self.end {
                        break;
                    }
                    self.end = fence_end;
                    self.mode = Mode::Prose;
                }
            }
            mode_was = self.mode;
            let again = match self.mode {
                Mode::Code => self.code(),
                Mode::BlockComment => self.block_comment(),
                Mode::LineComment => self.line_comment(),
                Mode::String => self.string(),
                Mode::Continuation => self.continuation(),
                Mode::Prose => self.prose(),
                Mode::Unicode => {
                    self.unicode();
                    false
                }
            };
            if !again {
                break;
            }
        }
        let kind = self.kind.unwrap_or(TokenKind::Error);
        let (start, end) = (self.start, self.end.max(self.start));
        let text = &self.text[start..end];

        self.pre_div = match kind {
            TokenKind::Comment | TokenKind::Space | TokenKind::Margin => self.pre_div,
            TokenKind::Word => !VALUE_PRECEDING_WORDS.contains(&text),
            TokenKind::Punctuation => {
                text.starts_with("--")
                    || text.starts_with("++")
                    || text.ends_with(')')
                    || text.ends_with(']')
            }
            _ => true,
        };
        let may_bracket = match kind {
            TokenKind::Punctuation => match text {
                // `<` right after a value, as in `List<`, may open angle brackets.
                "<" => {
                    let after_value = self.last_kind.is_some_and(|k| !k.is_ignorable());
                    self.open_angles += after_value as u32;
                    after_value
                }
                ">" => {
                    let closes = self.open_angles != 0;
                    self.open_angles -= closes as u32;
                    closes
                }
                "(" | ")" | "{" | "}" | "[" | "]" | "</" | "\\(" | "\\{" | "${" | "\\u{" => true,
                _ => false,
            },
            TokenKind::LeftDelimiter | TokenKind::RightDelimiter => true,
            _ => false,
        };
        let error = self.error;
        self.tokens.push(Token {
            kind,
            start: start as u32,
            end: end as u32,
            synthetic: None,
            may_bracket,
            error,
        });
        self.last_kind = Some(kind);
        if mode_was == Mode::Prose && kind == TokenKind::Comment {
            self.paragraph_comments(start, end);
        }
    }

    /// Lexes a code token. Returns whether to continue in a new mode.
    fn code(&mut self) -> bool {
        let c = self.char_at(self.end);
        let next = self.byte(self.end + 1);
        match classify(c) {
            CharKind::WordStart => {
                self.kind = Some(TokenKind::Word);
                self.end += c.len_utf8();
                self.word_end();
                // As Kotlin, only words with a code point past Latin-1 are
                // checked, so `ª` (NFKC `a`) passes.
                let word = &self.text[self.start..self.end];
                if self.kind == Some(TokenKind::Word)
                    && word.chars().any(|c| c > '\u{ff}')
                    && !nfkc::is_nfkc(word)
                {
                    self.fail(LexError::UnnormalizedIdentifier);
                }
                self.quoted_name();
            }
            CharKind::Dot if next.is_ascii_digit() => {
                self.kind = Some(TokenKind::Number);
                self.number_end();
            }
            CharKind::Dot => {
                self.kind = Some(TokenKind::Punctuation);
                self.end += 1;
                while self.byte(self.end) == b'.' && !self.byte(self.end + 1).is_ascii_digit() {
                    self.end += 1;
                }
            }
            CharKind::Slash if next == b'*' || next == b'/' => {
                self.end += 2;
                self.mode = if next == b'*' {
                    Mode::BlockComment
                } else {
                    Mode::LineComment
                };
                return true;
            }
            CharKind::Slash if self.pre_div => {
                self.kind = Some(TokenKind::Punctuation);
                self.punctuation_end();
            }
            CharKind::Slash => {
                self.cluster(
                    self.end,
                    self.end + 1,
                    Chunk::Open(Opener::Quote {
                        delim: b'/',
                        left: true,
                    }),
                );
                self.kind = Some(TokenKind::LeftDelimiter);
                self.mode = Mode::String;
            }
            CharKind::Backslash => {
                self.kind = Some(TokenKind::Punctuation);
                match next {
                    b'{' => self.cluster(self.end + 1, self.end + 2, Chunk::Open(Opener::Curly)),
                    b'(' => self.end += 2,
                    _ => self.end += 1,
                }
            }
            CharKind::Dollar if next == b'{' => {
                self.kind = Some(TokenKind::Punctuation);
                self.cluster(self.end, self.end + 2, Chunk::Open(Opener::Hole));
            }
            CharKind::Dollar => {
                self.fail(LexError::UnrecognizedToken);
                self.end += 1;
            }
            CharKind::Digit => {
                self.kind = Some(TokenKind::Number);
                self.number_end();
            }
            CharKind::Punctuation => {
                self.kind = Some(TokenKind::Punctuation);
                self.end += 1;
                if self.char_at(self.end) == EMOJI_SELECTOR {
                    // A keycap emoji like `#️`.
                    self.kind = Some(TokenKind::Error);
                    self.word_end();
                    self.error = Some(LexError::BadEmoji);
                } else {
                    let after = self.byte(self.end);
                    let continues = match c {
                        '<' => matches!(after, b'<' | b'=' | b'/'),
                        // Split `>>` when closing angle brackets.
                        '>' => self.open_angles == 0,
                        _ => true,
                    };
                    if continues {
                        self.punctuation_end();
                    }
                }
            }
            CharKind::Standalone => {
                self.kind = Some(TokenKind::Punctuation);
                match c {
                    '{' => self.cluster(self.end, self.end + 1, Chunk::Open(Opener::Curly)),
                    '}' => self.cluster(self.end, self.end + 1, Chunk::RightCurly),
                    _ => self.end += 1,
                }
            }
            CharKind::Error => {
                self.kind = Some(TokenKind::Error);
                self.run_end(CharKind::Error);
                self.error = Some(LexError::UnrecognizedToken);
            }
            CharKind::Space => {
                self.kind = Some(TokenKind::Space);
                self.run_end(CharKind::Space);
            }
            CharKind::LineBreak => {
                self.kind = Some(TokenKind::Space);
                self.run_end(CharKind::LineBreak);
                // A line break ends a scriptlet line in a multi-quoted string.
                if self.line_kind.take().is_some() {
                    self.mode = Mode::Continuation;
                }
            }
            CharKind::Semi => {
                self.kind = Some(TokenKind::Punctuation);
                self.run_end(CharKind::Semi);
            }
            CharKind::Quote => self.open_quote(c as u8),
        }
        false
    }

    /// Lexes `nym` followed by a backquoted name, like ``nym`a b` ``, as one
    /// word, if closed and without `${` holes.
    fn quoted_name(&mut self) {
        if self.byte(self.end) != b'`' || !self.text[self.start..].starts_with("nym") {
            return;
        }
        let mut i = self.end + 1;
        while i < self.text.len() {
            let b = self.byte(i);
            i += 1;
            match b {
                b'`' => {
                    self.end = i;
                    return;
                }
                b'$' if self.byte(i) == b'{' => return,
                b'\\' if i == self.text.len() => return,
                b'\\' => i += 1,
                _ => {}
            }
        }
    }

    fn open_quote(&mut self, quote: u8) {
        let line_kind_was = self.line_kind;
        let mut multi = false;
        let opener = if quote == b'"' && self.text[self.end..].starts_with("\"\"\"") {
            multi = true;
            Opener::MultiQuote
        } else {
            Opener::Quote {
                delim: quote,
                left: true,
            }
        };
        let len = if multi { 3 } else { 1 };
        if multi && line_kind_was == Some(LineKind::StmtFragment) {
            // Its lines' margins would be ambiguous with ours, so it opens
            // nothing. Kotlin opens it, swallowing the following lines.
            self.end += len;
            self.fail(LexError::UnsupportedNestedMultiQuote);
            return;
        }
        self.cluster(self.end, self.end + len, Chunk::Open(opener));
        if multi && line_kind_was == Some(LineKind::StmtFragment) {
            self.fail(LexError::UnsupportedNestedMultiQuote);
        } else if self.mode == Mode::String {
            self.kind = Some(TokenKind::LeftDelimiter);
        } else {
            self.kind = Some(TokenKind::Error);
        }
    }

    /// Mode for after a comment: back to looking for a margin when directly
    /// inside a multi-quoted string.
    fn mode_after_comment(&self) -> Mode {
        if self.stack.last() == Some(&Opener::MultiQuote) {
            Mode::Continuation
        } else {
            Mode::Code
        }
    }

    fn block_comment(&mut self) -> bool {
        self.kind = Some(TokenKind::Comment);
        while self.end < self.text.len() {
            let c = self.char_at(self.end);
            self.end += c.len_utf8();
            if c == '*' && self.byte(self.end) == b'/' {
                self.end += 1;
                self.mode = self.mode_after_comment();
                return false;
            }
            if c == '\n' || c == '\r' {
                // Let the code-fence check look at the new line.
                return true;
            }
        }
        false
    }

    fn line_comment(&mut self) -> bool {
        self.kind = Some(TokenKind::Comment);
        while self.end < self.text.len() {
            if is_line_break(self.byte(self.end)) {
                self.mode = self.mode_after_comment();
                return self.end == self.start;
            }
            self.end += self.char_at(self.end).len_utf8();
        }
        false
    }

    /// Lexes string content, an escape, a hole start, or a close quote.
    fn string(&mut self) -> bool {
        self.kind = Some(TokenKind::QuotedString);
        let (delim, multi) = self.string_context();
        let regex = delim == b'/';
        // A `/` inside a regex character class like `[/]` doesn't close it.
        let mut in_class = false;
        let len = self.text.len();
        while self.end < len {
            let c = self.byte(self.end);
            if is_line_break(c) {
                if multi {
                    self.mode = Mode::Continuation;
                    self.end += 1;
                } else if self.start == self.end {
                    // Unclosed single-line string: the cluster closes it.
                    self.cluster(self.end, self.end + 1, Chunk::LineBreak);
                    if self.mode == Mode::String {
                        return true;
                    }
                    self.kind = Some(TokenKind::Space);
                    // The line break still ends a scriptlet line. Kotlin
                    // reads the next line as code.
                    if self.line_kind.take().is_some() {
                        self.mode = Mode::Continuation;
                    }
                }
                return false;
            }
            self.end += if c < 0x80 {
                1
            } else {
                self.char_at(self.end).len_utf8()
            };
            let unicode = c == b'\\'
                && self.end + 1 < len
                && self.byte(self.end) == b'u'
                && self.byte(self.end + 1) == b'{';
            if self.starts_hole(c) || unicode {
                if self.end - 1 != self.start {
                    // End the content before the hole.
                    self.end -= 1;
                    return false;
                }
                self.kind = Some(TokenKind::Punctuation);
                let (step, opener) = if unicode {
                    (2, Opener::Unicode)
                } else {
                    (1, Opener::Hole)
                };
                self.cluster(self.end - 1, self.end + step, Chunk::Open(opener));
                if self.mode != Mode::String {
                    return false;
                }
            } else if c == b'\\' {
                if self.end - 1 != self.start {
                    // Escapes are tokens by themselves.
                    self.end -= 1;
                } else if self.end == len || (!multi && is_line_break(self.byte(self.end))) {
                    self.fail(LexError::UnrecognizedStringEscape);
                } else {
                    let escaped = self.byte(self.end);
                    self.end += self.char_at(self.end).len_utf8();
                    match escaped {
                        b'u' => self.hex_end(4),
                        b'x' => self.hex_end(2),
                        _ => {}
                    }
                }
                return false;
            } else if !multi && c == delim && !in_class {
                // `/a/->b/` is a regex with a replacement.
                if regex && self.byte(self.end) == b'-' && self.byte(self.end + 1) == b'>' {
                    self.end += 2;
                    continue;
                }
                if self.end - 1 != self.start {
                    self.end -= 1;
                    return false;
                }
                self.cluster(
                    self.end - 1,
                    self.end,
                    Chunk::Open(Opener::Quote { delim, left: false }),
                );
                if self.mode != Mode::String {
                    if regex {
                        self.word_end(); // flags
                    }
                    self.kind = Some(TokenKind::RightDelimiter);
                }
                return false;
            } else if c == b'[' && regex {
                in_class = true;
            } else if c == b']' {
                in_class = false;
            }
        }
        false
    }

    /// After a line break in a multi-quoted string: space, a margin
    /// character that continues the string, a comment, or anything else,
    /// which ends the string.
    fn continuation(&mut self) -> bool {
        let c = self.byte(self.end);
        match c {
            b'"' | b':' | b'~' => {
                self.end += 1;
                self.kind = Some(TokenKind::Margin);
                let stmt = c == b':';
                self.cluster(
                    self.start,
                    self.end,
                    if stmt {
                        Chunk::MarginStmt
                    } else {
                        Chunk::MarginChars
                    },
                );
                if stmt {
                    self.line_kind = Some(LineKind::StmtFragment);
                    self.mode = Mode::Code;
                } else {
                    self.line_kind = Some(LineKind::Chars);
                    self.mode = Mode::String;
                }
                false
            }
            b'\n' | b'\r' => {
                self.kind = Some(TokenKind::Space);
                self.end += 1;
                false
            }
            b' ' | b'\t' => {
                self.kind = Some(TokenKind::Space);
                while matches!(self.byte(self.end), b' ' | b'\t') {
                    self.end += 1;
                }
                false
            }
            b'/' if matches!(self.byte(self.end + 1), b'/' | b'*') => {
                self.mode = Mode::Code;
                self.line_kind = Some(LineKind::StmtFragment);
                true
            }
            _ => {
                let start = self.start;
                // Close what the last scriptlet line left open, with the
                // string. Kotlin closes only the innermost.
                while self.stack.last().is_some_and(|&o| o != Opener::MultiQuote) {
                    self.pop(start, true);
                }
                self.pop(start, true);
                self.mode = Mode::Code;
                true
            }
        }
    }

    /// Markdown prose, up to the end of the next temper code block's
    /// opening fence.
    fn prose(&mut self) -> bool {
        self.kind = Some(TokenKind::Comment);
        while self.end < self.text.len() {
            if self.at_line_start(self.end) {
                if let Some(code_start) = self.code_start_at(self.end) {
                    self.end = code_start;
                    self.mode = Mode::Code;
                    // Indented code has a zero-width fence, so code right
                    // here, as at the file start, gets no empty comment.
                    return self.end == self.start;
                }
            }
            self.end += self.char_at(self.end).len_utf8();
        }
        false
    }

    /// Inside `\u{...}`: commas, a closing `}`, holes, and runs of other text.
    fn unicode(&mut self) {
        let c = self.char_at(self.end);
        match c {
            ',' => {
                self.kind = Some(TokenKind::Punctuation);
                self.end += 1;
            }
            '}' => {
                self.kind = Some(TokenKind::Punctuation);
                self.cluster(self.end, self.end + 1, Chunk::RightCurly);
            }
            _ => {
                self.end += c.len_utf8();
                if c.is_ascii() && self.starts_hole(c as u8) {
                    self.kind = Some(TokenKind::Punctuation);
                    self.cluster(self.end - 1, self.end + 1, Chunk::Open(Opener::Hole));
                } else {
                    self.kind = Some(TokenKind::QuotedString);
                    if matches!(classify(c), CharKind::Digit | CharKind::WordStart) {
                        self.word_end();
                    } else {
                        while self.end < self.text.len() {
                            let c = self.char_at(self.end);
                            if matches!(c, '$' | '{' | ',' | '}')
                                || matches!(classify(c), CharKind::Digit | CharKind::WordStart)
                            {
                                break;
                            }
                            self.end += c.len_utf8();
                        }
                    }
                }
            }
        }
    }

    /// Extends a punctuation token over following operator characters.
    fn punctuation_end(&mut self) {
        let len = self.text.len();
        while self.end < len {
            let c = self.char_at(self.end);
            let before = self.byte(self.end.wrapping_sub(1));
            let after = self.byte(self.end + 1);
            let stop = match classify(c) {
                CharKind::Punctuation => match c {
                    // `<` only joins `<<`, `<=` and the like.
                    '<' => !matches!(before, b'=' | b'<') && !matches!(after, b'=' | b'<'),
                    // `>` only joins arrows and comparisons, so `x>` stays an angle bracket.
                    '>' => self.open_angles > 0 || !matches!(before, b'-' | b'=' | b'>' | b'<'),
                    _ => false,
                },
                CharKind::Dot => after.is_ascii_digit(),
                // As at a line's end, so a line break added after it
                // can't change how it lexes.
                CharKind::Slash => {
                    if after == b'*' || after == b'/' {
                        true
                    } else if self.byte(self.start) == b'<' {
                        // `</` is a token, but `<</` is not.
                        if self.end == self.start + 1 {
                            self.end += 1;
                        }
                        true
                    } else {
                        false
                    }
                }
                _ => true,
            };
            if stop {
                return;
            }
            self.end += c.len_utf8();
        }
    }

    /// Extends a run of characters of the same kind, stopping at the end of
    /// a code block.
    fn run_end(&mut self, kind: CharKind) {
        while self.end < self.text.len() {
            let c = self.char_at(self.end);
            if classify(c) != kind
                || (self.at_line_start(self.end) && self.code_end_at(self.end).is_some())
            {
                break;
            }
            self.end += c.len_utf8();
        }
    }

    fn word_end(&mut self) {
        let was_error = self.kind == Some(TokenKind::Error);
        // Medial characters, like a middle dot, must be followed by a word character.
        let mut last_medial = false;
        while self.end < self.text.len() {
            let c = self.char_at(self.end);
            if is_continue(c) {
                last_medial = false;
            } else if id_parts::contains(MEDIAL, c) {
                if last_medial {
                    self.kind = Some(TokenKind::Error);
                }
                last_medial = true;
            } else {
                break;
            }
            self.end += c.len_utf8();
        }
        if last_medial {
            self.kind = Some(TokenKind::Error);
        }
        if !was_error && self.kind == Some(TokenKind::Error) {
            self.error = Some(LexError::InvalidIdentifier);
        }
    }

    fn number_end(&mut self) {
        self.digits_end();
        // Only a dot followed by a digit is a decimal point, so `1.seconds` works.
        if self.byte(self.end) == b'.' && self.byte(self.end + 1).is_ascii_digit() {
            self.end += 1;
            self.digits_end();
        }
        if matches!(self.byte(self.end), b'e' | b'E') {
            self.end += 1;
            if matches!(self.byte(self.end), b'+' | b'-') {
                self.end += 1;
            }
            if !self.digits_end() {
                self.fail(LexError::MalformedNumber);
            }
        }
        // Suffixes like `42L` lex as part of the number.
        let suffix_start = self.end;
        self.word_end();
        if self.kind != Some(TokenKind::Error) {
            // Keycap emoji like `1️` are not numbers.
            let suffix = &self.text[suffix_start..self.end];
            let bad = suffix
                .chars()
                .take_while(|&c| is_continue(c))
                .any(|c| c == EMOJI_SELECTOR);
            if bad {
                self.fail(LexError::BadEmoji);
            }
        }
    }

    /// Consumes digits with single underscores between them. Returns
    /// whether there were any.
    fn digits_end(&mut self) -> bool {
        if !self.byte(self.end).is_ascii_digit() {
            return false;
        }
        self.end += 1;
        loop {
            match self.byte(self.end) {
                b'0'..=b'9' => {}
                b'_' if self.byte(self.end + 1).is_ascii_digit() => {}
                _ => return true,
            }
            self.end += 1;
        }
    }

    fn hex_end(&mut self, max: usize) {
        for _ in 0..max {
            if !self.byte(self.end).is_ascii_hexdigit() {
                break;
            }
            self.end += 1;
        }
    }

    /// Updates the delimiter stack for a chunk at `before..after`, possibly
    /// marking the token as an error, shortening it, or inserting synthetic
    /// closers. Sets the end of the token and the mode from the new stack.
    fn cluster(&mut self, before: usize, after: usize, chunk: Chunk) {
        let mut len = after - before;
        loop {
            let top = self.stack.last().copied();
            let mut changes = transition(top.map(group), chunk);
            if top == Some(Opener::Hole) && chunk == Chunk::Open(Opener::Hole) {
                // `${` nests in a hole as anywhere in code. Kotlin makes the
                // `$` an error.
                changes = PUSH;
            }
            if changes & BAD != 0 {
                let error = match (top, chunk) {
                    (None | Some(Opener::MultiQuote), Chunk::RightCurly) => {
                        LexError::UnmatchedBracket
                    }
                    _ => LexError::UnrecognizedToken,
                };
                self.fail(error);
            }
            if changes & STORE != 0 && !self.store_in_scriptlet(before) {
                self.kind = Some(TokenKind::Error);
                changes = (changes | CONS1) & !REPROC;
            }
            if changes & CONS1 != 0 {
                len = 1;
            }
            if changes & (SYN | POP) != 0 {
                self.pop(before, changes & SYN != 0);
            }
            if changes & PUSH != 0 {
                if let Chunk::Open(opener) = chunk {
                    self.stack.push(opener);
                    match opener {
                        Opener::MultiQuote => self.scriptlets.push(Vec::new()),
                        Opener::Hole => self.hole_lines.push(self.line_kind),
                        _ => {}
                    }
                }
            }
            if changes & RESTORE != 0 {
                // In order. Kotlin reverses them, and may take another
                // string's.
                if let Some(stash) = self.scriptlets.last_mut() {
                    let stored = std::mem::take(stash);
                    self.stack.extend(stored);
                }
            }
            if changes & RESET_LINE != 0 {
                // Back to the line kind where the hole opened, so the rest
                // of a scriptlet line stays code. Kotlin goes to a chars line.
                self.line_kind = self.popped_hole_line;
            }
            if changes & UNSET_LINE != 0 {
                self.line_kind = None;
            }
            if changes & REPROC == 0 {
                break;
            }
        }
        self.end = before + len;
        self.mode = match self.stack.last() {
            Some(Opener::Quote { .. }) => Mode::String,
            Some(Opener::MultiQuote) if self.line_kind == Some(LineKind::StmtFragment) => {
                Mode::Code
            }
            Some(Opener::MultiQuote) => Mode::String,
            Some(Opener::Unicode) => Mode::Unicode,
            Some(Opener::Hole | Opener::Curly) | None => Mode::Code,
        };
    }

    /// On a `"` or `~` margin, sets aside openers left by the previous
    /// scriptlet line, like the `{` in `: for (x of xs) {`, until the next
    /// `:` line. Single-line strings left open are closed first. Returns
    /// false if not inside a multi-quoted string.
    fn store_in_scriptlet(&mut self, pos: usize) -> bool {
        let Some(mq) = self.stack.iter().rposition(|&o| o == Opener::MultiQuote) else {
            return false;
        };
        let above = &self.stack[mq + 1..];
        if let Some(lowest_quote) = above.iter().position(|o| matches!(o, Opener::Quote { .. })) {
            for _ in 0..above.len() - lowest_quote {
                self.pop(pos, true);
            }
        }
        let stored = self.stack.split_off(mq + 1);
        if let Some(stash) = self.scriptlets.last_mut() {
            stash.extend(stored);
        }
        true
    }

    /// Pops the top opener, inserting a synthetic closer if `synthesize`.
    /// Closing a multi-quoted string also closes blocks its scriptlets left open.
    fn pop(&mut self, pos: usize, synthesize: bool) {
        let Some(popped) = self.stack.pop() else {
            return;
        };
        let multi = popped == Opener::MultiQuote;
        if popped == Opener::Hole {
            self.popped_hole_line = self.hole_lines.pop().flatten();
        }
        if multi {
            let mut stored = self.scriptlets.pop().unwrap_or_default();
            stored.reverse(); // Innermost first.
            if !stored.is_empty() {
                if self.line_kind != Some(LineKind::StmtFragment) {
                    self.synthetic(pos, Synthetic::Colon, TokenKind::Margin, false);
                    self.line_kind = Some(LineKind::StmtFragment);
                }
                for opener in stored {
                    if let Some((closer, kind)) = closer(opener) {
                        self.synthetic(pos, closer, kind, true);
                    }
                }
                self.synthetic(pos, Synthetic::LineBreak, TokenKind::Space, false);
            }
        }
        if synthesize {
            if let Some((closer, kind)) = closer(popped) {
                self.synthetic(pos, closer, kind, true);
            }
        }
        if multi {
            self.reset_line_kind(LineKind::StmtFragment);
        }
    }

    /// Sets the line kind for when a `"""` is on top, or unsets it.
    fn reset_line_kind(&mut self, kind: LineKind) {
        let in_multi = self.stack.last() == Some(&Opener::MultiQuote);
        self.line_kind = if in_multi { Some(kind) } else { None };
    }

    fn synthetic(&mut self, pos: usize, synthetic: Synthetic, kind: TokenKind, may_bracket: bool) {
        let pos = pos as u32;
        self.tokens.push(Token {
            kind,
            start: pos,
            end: pos,
            synthetic: Some(synthetic),
            may_bracket,
            error: None,
        });
    }

    /// Adds a synthetic doc comment for each paragraph in the prose token at
    /// `start..end`.
    fn paragraph_comments(&mut self, start: usize, end: usize) {
        let Some(first) = self.blocks.iter().position(|b| b.begin.start >= start) else {
            return;
        };
        for i in first..self.blocks.len() {
            let block = &self.blocks[i];
            if block.end.end > end {
                break;
            }
            if block.kind == literate::Kind::Paragraph {
                // Plain paragraphs start at their indentation, so the first
                // line keeps its indent relative to the others. List items
                // start after their marker.
                let start = if self.text[block.begin.clone()].trim().is_empty() {
                    block.begin.start
                } else {
                    block.begin.end
                };
                let range = start..block.end.start;
                if !is_blank(&self.text[range.clone()]) {
                    self.tokens.push(Token {
                        kind: TokenKind::Comment,
                        start: start as u32,
                        end: range.end as u32,
                        synthetic: Some(Synthetic::Paragraph),
                        may_bracket: false,
                        error: None,
                    });
                }
            }
        }
    }
}

/// Whitespace as Kotlin sees it, for trimming paragraphs.
pub fn is_kotlin_whitespace(c: char) -> bool {
    (c.is_whitespace() && c != '\u{85}') || ('\u{1C}'..='\u{1F}').contains(&c)
}

fn is_blank(text: &str) -> bool {
    text.chars().all(is_kotlin_whitespace)
}

/// Formats a paragraph as a `/**? ... ?*/` doc comment, without its common
/// indentation and surrounding blank lines. None if blank.
pub(crate) fn doc_comment(paragraph: &str) -> Option<String> {
    let lines: Vec<&str> = paragraph
        .split("\r\n")
        .flat_map(|l| l.split(['\n', '\r']))
        .collect();
    let is_blank = |line: &&str| line.chars().all(is_kotlin_whitespace);
    let first = lines.iter().find(|l| !is_blank(l))?;
    let indent = first.len() - first.trim_start_matches([' ', '\t']).len();
    let common = lines
        .iter()
        .filter(|l| !is_blank(l))
        .map(|l| {
            first
                .bytes()
                .zip(l.bytes())
                .take_while(|(a, b)| a == b)
                .count()
        })
        .fold(indent, usize::min);
    let begin = lines.iter().position(|l| !is_blank(l))?;
    let end = lines.iter().rposition(|l| !is_blank(l))? + 1;
    let mut out = String::from("/**?\n");
    for line in &lines[begin..end] {
        let line = line.trim_end_matches(is_kotlin_whitespace);
        let line = if line.len() >= common {
            &line[common..]
        } else {
            line
        };
        out.push_str(if line.is_empty() { " *" } else { " * " });
        out.push_str(line);
        out.push('\n');
    }
    out.push_str("?*/");
    Some(out)
}

/// Appends one line per token: kind, byte start, byte end, flags, error and
/// text, separated by tabs. Flags are `s` for synthetic and `b` for may
/// bracket, or `-` for neither. Text is escaped to fit on one line. This is
/// the format of the expected tokens in `tests/*.cases` and of
/// `compat/oracle/LexTokens.java`.
pub fn write_token_lines(source: &str, tokens: &[Token], out: &mut String) {
    use std::fmt::Write;
    for &token in tokens {
        let flags = match (token.synthetic.is_some(), token.may_bracket) {
            (true, true) => "sb",
            (true, false) => "s",
            (false, true) => "b",
            (false, false) => "-",
        };
        let _ = write!(
            out,
            "{:?}\t{}\t{}\t{flags}\t",
            token.kind, token.start, token.end
        );
        match token.error {
            Some(error) => {
                let _ = write!(out, "{error:?}\t");
            }
            None => out.push_str("-\t"),
        }
        escape_into(&token.text(source), out);
        out.push('\n');
    }
}

/// Appends text escaped to fit on one line: backslash, line breaks, tabs
/// and other control characters.
pub fn escape_into(text: &str, out: &mut String) {
    use std::fmt::Write;
    for c in text.chars() {
        match c {
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            c if (c as u32) < 0x20 || c == '\u{7f}' => {
                let _ = write!(out, "\\u{{{:x}}}", c as u32);
            }
            c => out.push(c),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Tokens as `kind:text`, with synthetic ones marked `+`.
    fn tokens(text: &str, kind: SourceKind) -> Vec<String> {
        lex(text, kind)
            .into_iter()
            .filter(|t| t.kind != TokenKind::Space)
            .map(|t| {
                let synthetic = if t.synthetic.is_some() { "+" } else { "" };
                format!("{synthetic}{:?}:{}", t.kind, t.text(text))
            })
            .collect()
    }

    #[test]
    fn simple_statement() {
        assert_eq!(
            tokens("let x = 42;", SourceKind::Standalone),
            [
                "Word:let",
                "Word:x",
                "Punctuation:=",
                "Number:42",
                "Punctuation:;"
            ]
        );
    }

    #[test]
    fn string_with_hole() {
        assert_eq!(
            tokens("\"a${b}c\"", SourceKind::Standalone),
            [
                "LeftDelimiter:\"",
                "QuotedString:a",
                "Punctuation:${",
                "Word:b",
                "Punctuation:}",
                "QuotedString:c",
                "RightDelimiter:\"",
            ]
        );
    }

    #[test]
    fn unclosed_brackets_get_synthetic_closers() {
        assert_eq!(
            tokens("{ \"a", SourceKind::Standalone),
            [
                "Punctuation:{",
                "LeftDelimiter:\"",
                "QuotedString:a",
                "+RightDelimiter:\"",
                "+Punctuation:}"
            ]
        );
    }

    #[test]
    fn regex_after_return_but_division_after_value() {
        assert_eq!(
            tokens("return /a/; x /a", SourceKind::Standalone),
            [
                "Word:return",
                "LeftDelimiter:/",
                "QuotedString:a",
                "RightDelimiter:/",
                "Punctuation:;",
                "Word:x",
                "Punctuation:/",
                "Word:a",
            ]
        );
    }

    #[test]
    fn angle_brackets_split_shifts() {
        let text = "List<List<T>> x >> y";
        let got = lex(text, SourceKind::Standalone);
        let gts: Vec<_> = got
            .iter()
            .map(|t| (&text[t.range()], t.may_bracket))
            .filter(|(text, _)| text.starts_with('>'))
            .collect();
        assert_eq!(gts, [(">", true), (">", true), (">>", false)]);
    }

    #[test]
    fn markdown_prose_is_comments() {
        let got = tokens("Hi\nthere.\n\n```temper\nx\n```\n", SourceKind::Markdown);
        assert_eq!(
            got,
            [
                "Comment:Hi\nthere.\n\n```temper",
                "+Comment:/**?\n * Hi\n * there.\n?*/",
                "Word:x",
                "Comment:```\n",
            ]
        );
    }

    #[test]
    fn doc_comment_strips_common_indent() {
        assert_eq!(
            doc_comment("\n  a\n\n   b  \n").as_deref(),
            Some("/**?\n * a\n *\n *  b\n?*/")
        );
        assert_eq!(doc_comment(" \n\t\n"), None);
    }
}
