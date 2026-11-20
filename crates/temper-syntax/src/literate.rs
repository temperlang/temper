//! Finds code blocks and paragraphs in literate `.temper.md` files.
//!
//! Temper treats a markdown file as prose with embedded code. Code blocks that
//! are untagged (indented, or a fence without an info string) or tagged
//! `temper` (but not `inert`) are Temper code; everything else is prose.
//! Paragraphs are reported too, since prose next to code can become docs.
//! [`top_blocks`] also reports prose's other top-level blocks (lists, block
//! quotes, HTML, link reference definitions, headings and thematic breaks),
//! for docs from prose.
//!
//! This follows the CommonMark block structure that Kotlin Temper gets from
//! commonmark-java: block quotes, list items, fenced and indented code, ATX
//! and setext headings, thematic breaks, HTML blocks, link reference
//! definitions and paragraphs. Ranges match Kotlin Temper's `TaggedRange`, in
//! UTF-8 byte offsets.

use std::ops::Range;

/// A block, with ranges for its opening and closing delimiters. Delimiter
/// ranges start at a line start, and are empty where a block has no
/// delimiter, such as indented code or paragraphs; so a block's text, as
/// written, is `begin.start..end.end`.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Block {
    pub kind: Kind,
    pub begin: Range<usize>,
    pub end: Range<usize>,
    /// Whether the block is inside a block quote. Quoted code is prose,
    /// unlike in Kotlin Temper; see [`Block::is_temper_code`].
    pub quoted: bool,
    /// Whether the block is in no list item or block quote.
    pub top: bool,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Kind {
    Paragraph,
    /// Info-string words, empty for indented or untagged fenced code.
    Code(Vec<String>),
    /// A list, from its first item to its last: only at top level, as
    /// [`top_blocks`] reports, and with any list right after it that
    /// differs only in its markers.
    List,
    /// A block quote: only at top level, as [`top_blocks`] reports.
    Quote,
    /// Link reference definitions, outside paragraphs.
    LinkDefinitions,
    Html,
    /// A heading or thematic break, which starts a section.
    Break,
}

impl Block {
    /// Whether this block holds Temper code rather than prose: untagged or
    /// tagged `temper` but not `inert`, and not in a block quote. Kotlin
    /// Temper counts quoted code too, but it can't work past one line, as
    /// the `>` markers on later lines lex as operators.
    pub fn is_temper_code(&self) -> bool {
        match &self.kind {
            _ if self.quoted => false,
            Kind::Code(tags) => {
                tags.is_empty() || (tags[0] == "temper" && !tags.iter().any(|t| t == "inert"))
            }
            _ => false,
        }
    }
}

/// Finds all code blocks and paragraphs, at any depth, in document order,
/// as Kotlin Temper's scan does.
pub fn find_blocks(text: &str) -> Vec<Block> {
    let mut blocks = scan(text);
    blocks.retain(|b| matches!(b.kind, Kind::Paragraph | Kind::Code(_)));
    blocks
}

/// Finds the blocks in no list item or block quote, of every kind, in
/// document order: what a declaration's doc is made of
/// (`temper_frontend::docs`).
pub fn top_blocks(text: &str) -> Vec<Block> {
    let mut blocks = scan(text);
    blocks.retain(|b| b.top);
    blocks
}

fn scan(text: &str) -> Vec<Block> {
    let mut scanner = Scanner {
        text: text.to_string(),
        containers: Vec::new(),
        top: None,
        leaf: Leaf::None,
        blocks: Vec::new(),
    };
    for line in lines(text) {
        scanner.line(line);
        if !scanner.containers.is_empty() && !scanner.is_blank(line.start, line.end) {
            if let Some((_, _, last)) = &mut scanner.top {
                *last = line.end;
            }
        }
    }
    scanner.close_leaf();
    scanner.close_top();
    scanner.blocks.sort_by_key(|b| b.begin.start);
    scanner.blocks
}

/// A paragraph's or other prose block's text, as a doc has it: without
/// the indentation its lines share, trailing space, or blank lines around
/// it, as the paragraphs' doc comments the lexer makes have it.
pub fn prose_text(text: &str) -> String {
    (crate::lex::doc_comment(text).and_then(|c| crate::unpack::paragraph_comment_content(&c)))
        .unwrap_or_default()
}

/// `text` with only its Temper code ([`Block::is_temper_code`]) left: every
/// other byte but a line break becomes a space, so offsets stay the same,
/// and code reads as it would in a `.temper` file, apart from indentation.
pub fn blank_prose(text: &str) -> String {
    fn blank(bytes: &mut [u8]) {
        for b in bytes.iter_mut().filter(|b| **b != b'\n') {
            *b = b' ';
        }
    }
    let mut out = text.as_bytes().to_vec();
    let mut from = 0;
    for block in find_blocks(text).iter().filter(|b| b.is_temper_code()) {
        blank(&mut out[from..block.begin.end]);
        from = block.end.start;
    }
    blank(&mut out[from..]);
    // Each blanked character's every byte is a space now.
    String::from_utf8(out).expect("blanking keeps UTF-8")
}

/// A line's start offset, and its content end before any `\r\n` or `\n`.
#[derive(Clone, Copy)]
struct Line {
    start: usize,
    end: usize,
}

fn lines(text: &str) -> Vec<Line> {
    let mut lines = Vec::new();
    let mut start = 0;
    while start < text.len() {
        let rest = &text[start..];
        let (len, next) = match rest.find('\n') {
            Some(i) => (i, start + i + 1),
            None => (rest.len(), text.len()),
        };
        let mut end = start + len;
        if text[start..end].ends_with('\r') {
            end -= 1;
        }
        lines.push(Line { start, end });
        start = next;
    }
    lines
}

enum Container {
    BlockQuote,
    /// Content starts this many columns into the item.
    ListItem {
        indent: usize,
        had_content: bool,
    },
}

enum Leaf {
    None,
    Paragraph {
        lines: Vec<Line>,
        first_starts: Vec<usize>,
    },
    Fenced {
        begin: Range<usize>,
        tags: Vec<String>,
        fence: u8,
        len: usize,
        last: Line,
        quoted: bool,
    },
    Indented {
        begin: usize,
        last: Line,
        quoted: bool,
    },
    Html {
        kind: u8,
        begin: usize,
        last: Line,
    },
}

/// Holds its own copy of the text, which is cheap next to parsing it, and
/// keeps the scanner free of lifetimes.
struct Scanner {
    text: String,
    containers: Vec<Container>,
    /// The outermost container, while one is open: its kind, where its
    /// first line starts, and where its last non-blank line ends.
    top: Option<(Kind, usize, usize)>,
    leaf: Leaf,
    blocks: Vec<Block>,
}

impl Scanner {
    fn bytes(&self) -> &[u8] {
        self.text.as_bytes()
    }

    fn line(&mut self, line: Line) {
        let mut pos = line.start;
        let blank = self.is_blank(pos, line.end);

        // Continue open containers, outermost first.
        let mut matched = 0;
        for i in 0..self.containers.len() {
            let ok = match self.containers[i] {
                Container::BlockQuote => match self.block_quote_marker(pos, line.end) {
                    Some(p) => {
                        pos = p;
                        true
                    }
                    None => false,
                },
                Container::ListItem {
                    indent,
                    had_content,
                } => {
                    if blank {
                        had_content || !matches!(self.leaf, Leaf::None)
                    } else if self.indent(pos, line.end) >= indent {
                        pos = self.advance_columns(pos, line.end, indent);
                        true
                    } else {
                        false
                    }
                }
            };
            if !ok {
                break;
            }
            matched += 1;
        }
        let all_matched = matched == self.containers.len();

        // Inside fenced code or HTML, only a closing line or container end
        // changes anything.
        if all_matched {
            match self.leaf {
                Leaf::Fenced { fence, len, .. } => {
                    if self.closing_fence(pos, line.end, fence, len) {
                        self.close_fenced(Some(line));
                    } else if let Leaf::Fenced { last, .. } = &mut self.leaf {
                        *last = line;
                    }
                    return;
                }
                Leaf::Html { kind, .. } => {
                    if kind >= 6 && blank {
                        self.close_leaf();
                    } else {
                        if let Leaf::Html { last, .. } = &mut self.leaf {
                            *last = line;
                        }
                        if html_ends(kind, &self.text[pos..line.end]) {
                            self.close_leaf();
                        }
                    }
                    return;
                }
                _ => {}
            }
        }

        // Lazy paragraph continuation: unmatched containers stay open. A
        // block start ends it as if no paragraph were open, since the
        // paragraph's container didn't match: so `2.` after `1. a` starts a
        // sibling item, though only `1.` may interrupt a paragraph.
        if !all_matched
            && !blank
            && matches!(self.leaf, Leaf::Paragraph { .. })
            && !self.starts_block(pos, line.end, false)
        {
            self.add_paragraph_line(line, pos);
            return;
        }
        if !all_matched {
            self.close_leaf();
            self.containers.truncate(matched);
            if matched == 0 {
                self.close_top();
            }
        }

        // New containers.
        loop {
            let indent = self.indent(pos, line.end);
            if indent >= 4 {
                break;
            }
            if let Some(p) = self.block_quote_marker(pos, line.end) {
                self.close_leaf();
                self.mark_content();
                self.open_top(Kind::Quote, line.start);
                self.containers.push(Container::BlockQuote);
                pos = p;
                continue;
            }
            let in_paragraph = matches!(self.leaf, Leaf::Paragraph { .. });
            if self.thematic_break(pos, line.end) {
                break;
            }
            if let Some((content, item_indent)) = self.list_marker(pos, line.end, in_paragraph) {
                self.close_leaf();
                self.mark_content();
                self.open_top(Kind::List, line.start);
                self.containers.push(Container::ListItem {
                    indent: item_indent,
                    had_content: false,
                });
                pos = content;
                continue;
            }
            break;
        }

        // Leaf blocks.
        let blank = self.is_blank(pos, line.end);
        if blank {
            match &self.leaf {
                Leaf::Indented { .. } => {}
                _ => self.close_leaf(),
            }
            return;
        }
        self.mark_content();
        let indent = self.indent(pos, line.end);
        let in_paragraph = matches!(self.leaf, Leaf::Paragraph { .. });
        if indent >= 4 {
            match &mut self.leaf {
                Leaf::Paragraph { .. } => self.add_paragraph_line(line, pos),
                Leaf::Indented { last, .. } => *last = line,
                _ => {
                    self.close_leaf();
                    self.leaf = Leaf::Indented {
                        begin: pos,
                        last: line,
                        quoted: self.in_quote(),
                    };
                }
            }
            return;
        }
        let content = self.skip_spaces(pos, line.end);
        let rest = self.text[content..line.end].to_string();
        let rest = rest.as_str();
        if let Some((fence, len, info)) = fence_open(rest) {
            self.close_leaf();
            let tags = info.split_whitespace().map(str::to_string).collect();
            let quoted = self.in_quote();
            self.leaf = Leaf::Fenced {
                begin: line.start..line.end,
                tags,
                fence,
                len,
                last: line,
                quoted,
            };
            return;
        }
        if in_paragraph && all_matched && setext_underline(rest) {
            // The paragraph was a heading.
            if let Leaf::Paragraph { lines, .. } = std::mem::replace(&mut self.leaf, Leaf::None) {
                self.push_prose(Kind::Break, lines[0].start, line.end);
            }
            return;
        }
        if atx_heading(rest) || self.thematic_break(pos, line.end) {
            self.close_leaf();
            self.push_prose(Kind::Break, line.start, line.end);
            return;
        }
        if let Some(kind) = html_start(rest, in_paragraph) {
            self.close_leaf();
            self.leaf = Leaf::Html {
                kind,
                begin: line.start,
                last: line,
            };
            if html_ends(kind, rest) {
                self.close_leaf();
            }
            return;
        }
        if !in_paragraph {
            self.close_leaf();
        }
        self.add_paragraph_line(line, content);
    }

    /// Records that the innermost list item has content, so a blank line
    /// doesn't end it.
    fn mark_content(&mut self) {
        if let Some(Container::ListItem { had_content, .. }) = self.containers.last_mut() {
            *had_content = true;
        }
    }

    fn add_paragraph_line(&mut self, line: Line, content: usize) {
        let content = self.skip_spaces(content, line.end);
        match &mut self.leaf {
            Leaf::Paragraph {
                lines,
                first_starts,
            } => {
                lines.push(line);
                first_starts.push(content);
            }
            _ => {
                self.leaf = Leaf::Paragraph {
                    lines: vec![line],
                    first_starts: vec![content],
                };
            }
        }
    }

    /// Whether a line can start a block that would interrupt a paragraph.
    fn starts_block(&self, pos: usize, end: usize, in_paragraph: bool) -> bool {
        if self.indent(pos, end) >= 4 {
            return false;
        }
        let content = self.skip_spaces(pos, end);
        let rest = &self.text[content..end];
        self.block_quote_marker(pos, end).is_some()
            || self.thematic_break(pos, end)
            || self.list_marker(pos, end, in_paragraph).is_some()
            || fence_open(rest).is_some()
            || atx_heading(rest)
            || html_start(rest, in_paragraph).is_some()
    }

    /// Starts a top-level container at `start`, unless one is open. A list
    /// right after another, past blank lines, continues it.
    fn open_top(&mut self, kind: Kind, start: usize) {
        if !self.containers.is_empty() {
            return;
        }
        let mut start = start;
        if kind == Kind::List {
            let previous = self
                .blocks
                .last()
                .filter(|b| b.kind == Kind::List && self.text[b.end.end..start].trim().is_empty());
            if previous.is_some() {
                start = self.blocks.pop().unwrap().begin.start;
            }
        }
        self.top = Some((kind, start, start));
    }

    fn close_top(&mut self) {
        if let Some((kind, start, last)) = self.top.take() {
            self.push_prose(kind, start, last);
        }
    }

    /// Records a block of prose from `start` to the line ending at `last`.
    fn push_prose(&mut self, kind: Kind, start: usize, last: usize) {
        let after = past_newline(&self.text, last);
        self.blocks.push(Block {
            kind,
            begin: start..start,
            end: after..after,
            quoted: self.in_quote(),
            top: self.containers.is_empty(),
        });
    }

    fn in_quote(&self) -> bool {
        self.containers
            .iter()
            .any(|c| matches!(c, Container::BlockQuote))
    }

    fn close_leaf(&mut self) {
        match std::mem::replace(&mut self.leaf, Leaf::None) {
            Leaf::Paragraph {
                lines,
                first_starts,
            } => {
                // Link reference definitions at the start aren't paragraph text.
                let skip = lines
                    .iter()
                    .zip(&first_starts)
                    .take_while(|(line, start)| {
                        link_reference_definition(&self.text[**start..line.end])
                    })
                    .count();
                if skip > 0 {
                    let start = line_start(&self.text, first_starts[0]);
                    self.push_prose(Kind::LinkDefinitions, start, lines[skip - 1].end);
                }
                if skip < lines.len() {
                    let first = first_starts[skip];
                    let last = lines[lines.len() - 1];
                    let line_start = line_start(&self.text, first);
                    let after = past_newline(&self.text, last.end);
                    self.blocks.push(Block {
                        kind: Kind::Paragraph,
                        begin: line_start..first,
                        end: after..after,
                        quoted: false,
                        top: self.containers.is_empty(),
                    });
                }
            }
            leaf @ Leaf::Fenced { .. } => {
                self.leaf = leaf;
                self.close_fenced(None);
            }
            Leaf::Indented {
                begin,
                last,
                quoted,
            } => {
                let after = past_newline(&self.text, last.end);
                self.blocks.push(Block {
                    kind: Kind::Code(Vec::new()),
                    begin: line_start(&self.text, begin)..begin,
                    end: after..after,
                    quoted,
                    top: self.containers.is_empty(),
                });
            }
            Leaf::Html { begin, last, .. } => self.push_prose(Kind::Html, begin, last.end),
            Leaf::None => {}
        }
    }

    /// Closes fenced code, at its closing line if any. Unclosed code that
    /// runs to the end of the text gets an empty end after its last line.
    fn close_fenced(&mut self, closing: Option<Line>) {
        let Leaf::Fenced {
            begin,
            tags,
            last,
            quoted,
            ..
        } = std::mem::replace(&mut self.leaf, Leaf::None)
        else {
            return;
        };
        let end = match closing {
            Some(line) => line.start..line.end,
            None if past_newline(&self.text, last.end) == self.text.len()
                && last.start != begin.start =>
            {
                let after = self.text.len();
                after..after
            }
            None => last.start..last.end,
        };
        self.blocks.push(Block {
            kind: Kind::Code(tags),
            begin,
            end,
            quoted,
            top: self.containers.is_empty(),
        });
    }

    fn is_blank(&self, pos: usize, end: usize) -> bool {
        self.bytes()[pos..end]
            .iter()
            .all(|&b| b == b' ' || b == b'\t')
    }

    fn skip_spaces(&self, mut pos: usize, end: usize) -> usize {
        while pos < end && matches!(self.bytes()[pos], b' ' | b'\t') {
            pos += 1;
        }
        pos
    }

    /// Indentation in columns from `pos`, with tabs to multiples of 4.
    fn indent(&self, pos: usize, end: usize) -> usize {
        let start = line_start(&self.text, pos);
        let mut col = self.column(start, pos);
        let first = col;
        for &b in &self.bytes()[pos..end] {
            match b {
                b' ' => col += 1,
                b'\t' => col += 4 - col % 4,
                _ => break,
            }
        }
        col - first
    }

    fn column(&self, from: usize, to: usize) -> usize {
        let mut col = 0;
        for &b in &self.bytes()[from..to] {
            col += if b == b'\t' { 4 - col % 4 } else { 1 };
        }
        col
    }

    /// Advances up to `columns` columns of whitespace.
    fn advance_columns(&self, mut pos: usize, end: usize, columns: usize) -> usize {
        let mut taken = 0;
        let start = line_start(&self.text, pos);
        while taken < columns && pos < end {
            let col = self.column(start, pos);
            let width = match self.bytes()[pos] {
                b' ' => 1,
                b'\t' => 4 - col % 4,
                _ => break,
            };
            taken += width;
            pos += 1;
        }
        pos
    }

    /// After a `>` marker and one optional space, if there is one.
    fn block_quote_marker(&self, pos: usize, end: usize) -> Option<usize> {
        if self.indent(pos, end) > 3 {
            return None;
        }
        let p = self.skip_spaces(pos, end);
        if p < end && self.bytes()[p] == b'>' {
            let mut q = p + 1;
            if q < end && matches!(self.bytes()[q], b' ' | b'\t') {
                q += 1;
            }
            Some(q)
        } else {
            None
        }
    }

    fn thematic_break(&self, pos: usize, end: usize) -> bool {
        if self.indent(pos, end) > 3 {
            return false;
        }
        let rest = &self.bytes()[self.skip_spaces(pos, end)..end];
        let Some(&first) = rest.first() else {
            return false;
        };
        if !matches!(first, b'-' | b'*' | b'_') {
            return false;
        }
        let mut count = 0;
        for &b in rest {
            if b == first {
                count += 1;
            } else if b != b' ' && b != b'\t' {
                return false;
            }
        }
        count >= 3
    }

    /// For a list item start: where its content starts, and the item's
    /// content indentation in columns from `pos`.
    fn list_marker(&self, pos: usize, end: usize, in_paragraph: bool) -> Option<(usize, usize)> {
        let lead = self.indent(pos, end);
        if lead > 3 {
            return None;
        }
        let start = self.skip_spaces(pos, end);
        let bytes = &self.bytes()[start..end];
        let marker_len = match bytes.first()? {
            b'-' | b'+' | b'*' => 1,
            b'0'..=b'9' => {
                let digits = bytes.iter().take_while(|b| b.is_ascii_digit()).count();
                if digits > 9 || !matches!(bytes.get(digits), Some(b'.' | b')')) {
                    return None;
                }
                if in_paragraph && &bytes[..digits] != b"1" {
                    return None;
                }
                digits + 1
            }
            _ => return None,
        };
        let after = start + marker_len;
        let rest_blank = self.is_blank(after, end);
        if after < end && !matches!(self.bytes()[after], b' ' | b'\t') {
            return None;
        }
        if in_paragraph && rest_blank {
            return None;
        }
        let spaces = self.indent(after, end);
        let (content, gap) = if rest_blank {
            (end, 1)
        } else if spaces > 4 {
            (self.advance_columns(after, end, 1), 1)
        } else {
            (self.skip_spaces(after, end), spaces)
        };
        Some((content, lead + marker_len + gap))
    }

    fn closing_fence(&self, pos: usize, end: usize, fence: u8, len: usize) -> bool {
        if self.indent(pos, end) > 3 {
            return false;
        }
        let rest = &self.bytes()[self.skip_spaces(pos, end)..end];
        let count = rest.iter().take_while(|&&b| b == fence).count();
        count >= len && rest[count..].iter().all(|&b| b == b' ' || b == b'\t')
    }
}

/// For an opening code fence: its character, length and info string.
fn fence_open(rest: &str) -> Option<(u8, usize, &str)> {
    let bytes = rest.as_bytes();
    let fence = *bytes.first()?;
    if fence != b'`' && fence != b'~' {
        return None;
    }
    let len = bytes.iter().take_while(|&&b| b == fence).count();
    if len < 3 {
        return None;
    }
    let info = rest[len..].trim();
    if fence == b'`' && info.contains('`') {
        return None;
    }
    Some((fence, len, info))
}

fn atx_heading(rest: &str) -> bool {
    let hashes = rest.bytes().take_while(|&b| b == b'#').count();
    (1..=6).contains(&hashes) && matches!(rest.as_bytes().get(hashes), None | Some(b' ' | b'\t'))
}

fn setext_underline(rest: &str) -> bool {
    let trimmed = rest.trim_end();
    let Some(first) = trimmed.bytes().next() else {
        return false;
    };
    (first == b'=' || first == b'-') && trimmed.bytes().all(|b| b == first)
}

/// The CommonMark HTML block kind (1 to 7) a line starts, if any.
fn html_start(rest: &str, in_paragraph: bool) -> Option<u8> {
    if !rest.starts_with('<') {
        return None;
    }
    let lower = rest.to_ascii_lowercase();
    for tag in ["script", "pre", "style", "textarea"] {
        if let Some(after) = lower.strip_prefix('<').and_then(|s| s.strip_prefix(tag)) {
            if after.is_empty() || after.starts_with([' ', '\t', '>']) {
                return Some(1);
            }
        }
    }
    if rest.starts_with("<!--") {
        return Some(2);
    }
    if rest.starts_with("<?") {
        return Some(3);
    }
    if rest.starts_with("<![CDATA[") {
        return Some(5);
    }
    if rest.len() > 2 && rest.starts_with("<!") && rest.as_bytes()[2].is_ascii_alphabetic() {
        return Some(4);
    }
    let name_start = if lower.starts_with("</") { 2 } else { 1 };
    let name: String = lower[name_start..]
        .chars()
        .take_while(|c| c.is_ascii_alphanumeric())
        .collect();
    let after = &lower[name_start + name.len()..];
    if BLOCK_TAGS.contains(&name.as_str())
        && (after.is_empty() || after.starts_with([' ', '\t', '>']) || after.starts_with("/>"))
    {
        return Some(6);
    }
    if !in_paragraph && !name.is_empty() && complete_tag_line(&lower) {
        return Some(7);
    }
    None
}

/// A line holding only a complete open or close tag, for HTML kind 7.
fn complete_tag_line(lower: &str) -> bool {
    let trimmed = lower.trim_end();
    trimmed.ends_with('>') && !trimmed[1..trimmed.len() - 1].contains(['<', '>'])
}

fn html_ends(kind: u8, line: &str) -> bool {
    let lower = line.to_ascii_lowercase();
    match kind {
        1 => ["</script>", "</pre>", "</style>", "</textarea>"]
            .iter()
            .any(|t| lower.contains(t)),
        2 => line.contains("-->"),
        3 => line.contains("?>"),
        4 => line.contains('>'),
        5 => line.contains("]]>"),
        _ => false,
    }
}

const BLOCK_TAGS: &[&str] = &[
    "address",
    "article",
    "aside",
    "base",
    "basefont",
    "blockquote",
    "body",
    "caption",
    "center",
    "col",
    "colgroup",
    "dd",
    "details",
    "dialog",
    "dir",
    "div",
    "dl",
    "dt",
    "fieldset",
    "figcaption",
    "figure",
    "footer",
    "form",
    "frame",
    "frameset",
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
    "head",
    "header",
    "hr",
    "html",
    "iframe",
    "legend",
    "li",
    "link",
    "main",
    "menu",
    "menuitem",
    "nav",
    "noframes",
    "ol",
    "optgroup",
    "option",
    "p",
    "param",
    "search",
    "section",
    "summary",
    "table",
    "tbody",
    "td",
    "tfoot",
    "th",
    "thead",
    "title",
    "tr",
    "track",
    "ul",
];

/// A single-line `[label]: destination "optional title"`.
fn link_reference_definition(line: &str) -> bool {
    let Some(rest) = line.strip_prefix('[') else {
        return false;
    };
    let Some(close) = rest.find("]:") else {
        return false;
    };
    let label = &rest[..close];
    if label.trim().is_empty() || label.contains(['[', ']']) {
        return false;
    }
    let after = rest[close + 2..].trim();
    let (dest, title) = match after.find([' ', '\t']) {
        Some(i) => (&after[..i], after[i..].trim()),
        None => (after, ""),
    };
    !dest.is_empty()
        && (title.is_empty()
            || (title.len() >= 2
                && matches!(
                    (title.as_bytes()[0], title.as_bytes()[title.len() - 1]),
                    (b'"', b'"') | (b'\'', b'\'') | (b'(', b')')
                )))
}

fn line_start(text: &str, pos: usize) -> usize {
    text[..pos].rfind('\n').map_or(0, |i| i + 1)
}

fn past_newline(text: &str, line_end: usize) -> usize {
    match text[line_end..].find('\n') {
        Some(i) => line_end + i + 1,
        None => text.len(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn kinds(text: &str) -> Vec<(String, &str)> {
        find_blocks(text)
            .into_iter()
            .map(|b| {
                let tag = match &b.kind {
                    Kind::Paragraph => "para".to_string(),
                    Kind::Code(tags) => format!("code[{}]", tags.join(" ")),
                    kind => format!("{kind:?}"),
                };
                let body = &text[b.begin.end..b.end.start];
                (tag, body)
            })
            .collect()
    }

    #[test]
    fn prose_and_indented_code() {
        let text = "# Title\n\nSome prose.\n\n    let x = 1;\n\nMore.\n";
        let found: Vec<String> = kinds(text).into_iter().map(|(kind, _)| kind).collect();
        assert_eq!(found, ["para", "code[]", "para"]);
    }

    #[test]
    fn blank_prose_keeps_code_and_offsets() {
        let text = "# T\n\nProsé.\n\n    let x = 1;\n\n```log\nhi\n```\n\n```temper\ny;\n```\n";
        let blanked = blank_prose(text);
        assert_eq!(blanked.len(), text.len());
        let expected =
            "   \n\n       \n\n    let x = 1;\n\n      \n  \n   \n\n         \ny;\n   \n";
        assert_eq!(blanked, expected);
    }

    #[test]
    fn fenced_tags_and_temper_code() {
        let blocks = find_blocks("```temper inert\nx\n```\n\n```log\nhi\n```\n\n```\ny\n```\n");
        let temper: Vec<bool> = blocks.iter().map(Block::is_temper_code).collect();
        assert_eq!(temper, vec![false, false, true]);
    }

    #[test]
    fn indented_lines_continue_a_paragraph() {
        let blocks = find_blocks("Prose\n    still prose\n");
        assert_eq!(blocks.len(), 1);
        assert_eq!(blocks[0].kind, Kind::Paragraph);
    }

    #[test]
    fn link_reference_definitions_are_not_paragraphs() {
        assert!(find_blocks("[a]: https://example.com\n").is_empty());
    }

    /// Each top-level block's kind and text, from its start to its end.
    fn tops(text: &str) -> Vec<(String, &str)> {
        top_blocks(text)
            .into_iter()
            .map(|b| {
                let tag = match &b.kind {
                    Kind::Paragraph => "para".to_string(),
                    Kind::Code(tags) => format!("code[{}]", tags.join(" ")),
                    kind => format!("{kind:?}").to_lowercase(),
                };
                (tag, &text[b.begin.start..b.end.end])
            })
            .collect()
    }

    #[test]
    fn top_blocks_are_each_kind_as_written() {
        let text = "\
# Title

Intro.
[a]: https://a.example
More.

- one
- two
  - nested

  still two
3) other list

> quoted
lazily

<table>
<tr><td>x</td></tr>
</table>

Setext
------

***

```json
{}
```

    let x = 1;
";
        assert_eq!(
            tops(text),
            [
                ("break".into(), "# Title\n"),
                // Definitions at a paragraph's start are their own block.
                ("para".into(), "Intro.\n[a]: https://a.example\nMore.\n"),
                // A list's items, even of another list type, are one block.
                (
                    "list".into(),
                    "- one\n- two\n  - nested\n\n  still two\n3) other list\n"
                ),
                ("quote".into(), "> quoted\nlazily\n"),
                ("html".into(), "<table>\n<tr><td>x</td></tr>\n</table>\n"),
                ("break".into(), "Setext\n------\n"),
                ("break".into(), "***\n"),
                ("code[json]".into(), "```json\n{}\n```"),
                ("code[]".into(), "    let x = 1;\n"),
            ]
        );
    }

    #[test]
    fn top_blocks_split_link_definitions_from_paragraphs() {
        let text = "[a]: https://a.example\n[b]: https://b.example\nText [a].\n\n[c]: /c\n";
        assert_eq!(
            tops(text),
            [
                (
                    "linkdefinitions".into(),
                    "[a]: https://a.example\n[b]: https://b.example\n"
                ),
                ("para".into(), "Text [a].\n"),
                ("linkdefinitions".into(), "[c]: /c\n"),
            ]
        );
    }

    #[test]
    fn html_blocks_end_at_blank_lines_or_their_end() {
        let text = "<div>\na\n\nb\n\n<!-- note\n\nstill -->\nc\n";
        let found: Vec<String> = tops(text)
            .into_iter()
            .map(|(k, t)| format!("{k}: {t}"))
            .collect();
        assert_eq!(
            found,
            [
                "html: <div>\na\n",
                "para: b\n",
                "html: <!-- note\n\nstill -->\n",
                "para: c\n",
            ]
        );
    }

    #[test]
    fn find_blocks_keeps_only_paragraphs_and_code() {
        // As Kotlin Temper's scan, for compat/check_literate.py.
        let text = "# T\n\n- a\n\n> b\n\n<div>\n\n[x]: /x\n\n---\n";
        let found: Vec<String> = kinds(text).into_iter().map(|(k, _)| k).collect();
        assert_eq!(found, ["para", "para"]);
    }
}
