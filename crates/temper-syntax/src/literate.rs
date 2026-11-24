//! Finds code blocks and paragraphs in literate `.temper.md` files.
//!
//! Temper treats a markdown file as prose with embedded code. Code blocks that
//! are untagged (indented, or a fence without an info string) or tagged
//! `temper` (but not `inert`) are Temper code; everything else is prose.
//! Paragraphs are reported too, since prose next to code can become docs.
//!
//! This follows the CommonMark block structure that Kotlin Temper gets from
//! commonmark-java: block quotes, list items, fenced and indented code, ATX
//! and setext headings, thematic breaks, HTML blocks, link reference
//! definitions and paragraphs. Ranges match Kotlin Temper's `TaggedRange`, in
//! UTF-8 byte offsets.

use std::ops::Range;

/// A code block or paragraph, with ranges for its opening and closing
/// delimiters. Delimiter ranges start at a line start, and are empty where a
/// block has no delimiter, such as indented code or paragraphs.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Block {
    pub kind: Kind,
    pub begin: Range<usize>,
    pub end: Range<usize>,
    /// Whether the block is inside a block quote. Quoted code is prose,
    /// unlike in Kotlin Temper; see [`Block::is_temper_code`].
    pub quoted: bool,
    /// For indented code whose first line ended a list item, the column
    /// from which that line would have continued the item instead: moving
    /// the line there would change the block structure.
    pub item_column: Option<usize>,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Kind {
    Paragraph,
    /// Info-string words, empty for indented or untagged fenced code.
    Code(Vec<String>),
}

impl Block {
    /// Whether this block holds Temper code rather than prose: untagged or
    /// tagged `temper` but not `inert`, and not in a block quote. Kotlin
    /// Temper counts quoted code too, but it can't work past one line, as
    /// the `>` markers on later lines lex as operators.
    pub fn is_temper_code(&self) -> bool {
        match &self.kind {
            _ if self.quoted => false,
            Kind::Paragraph => false,
            Kind::Code(tags) => {
                tags.is_empty() || (tags[0] == "temper" && !tags.iter().any(|t| t == "inert"))
            }
        }
    }
}

/// Finds all code blocks and paragraphs, in document order.
pub fn find_blocks(text: &str) -> Vec<Block> {
    let mut scanner = Scanner {
        text: text.to_string(),
        containers: Vec::new(),
        item_column: None,
        leaf: Leaf::None,
        blocks: Vec::new(),
    };
    for line in lines(text) {
        scanner.line(line);
    }
    scanner.close_leaf();
    scanner.blocks.sort_by_key(|b| b.begin.start);
    scanner.blocks
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
        item_column: Option<usize>,
    },
    Html {
        kind: u8,
    },
}

/// Holds its own copy of the text, which is cheap next to parsing it, and
/// keeps the scanner free of lifetimes.
struct Scanner {
    text: String,
    containers: Vec<Container>,
    /// The column from which the current line would have continued a list
    /// item it ended, if it ended one.
    item_column: Option<usize>,
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
                Leaf::Html { kind } => {
                    if (kind >= 6 && blank) || html_ends(kind, &self.text[pos..line.end]) {
                        self.leaf = Leaf::None;
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
        self.item_column = None;
        if !all_matched {
            if let Container::ListItem { indent, .. } = self.containers[matched] {
                let start = line_start(&self.text, pos);
                self.item_column = Some(self.column(start, pos) + indent);
            }
            self.close_leaf();
            self.containers.truncate(matched);
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
                        item_column: self.item_column,
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
            self.leaf = Leaf::None;
            return;
        }
        if atx_heading(rest) || self.thematic_break(pos, line.end) {
            self.close_leaf();
            return;
        }
        if let Some(kind) = html_start(rest, in_paragraph) {
            self.close_leaf();
            self.leaf = Leaf::Html { kind };
            if html_ends(kind, rest) {
                self.leaf = Leaf::None;
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
                        item_column: None,
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
                item_column,
            } => {
                let after = past_newline(&self.text, last.end);
                self.blocks.push(Block {
                    kind: Kind::Code(Vec::new()),
                    begin: line_start(&self.text, begin)..begin,
                    end: after..after,
                    quoted,
                    item_column,
                });
            }
            Leaf::Html { .. } | Leaf::None => {}
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
            item_column: None,
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
}
