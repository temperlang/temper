//! Splitting what's too big for a target language in one piece, which
//! backends share: some compilers limit a string literal's size (javac's
//! constants to 65,535 bytes, MSVC's literals to 16,380 bytes a piece),
//! or a method's code (the JVM's 64 KB), so a backend writes a long
//! string as pieces ([`split_text`]) and many items as groups, each in
//! a helper of its own ([`groups`]), only where they're past its limits,
//! so everyday output stays as it was.

/// `text` in pieces, in order, each at most `max` by `size`, a
/// character's size in the target's encoding (`char::len_utf8` for
/// UTF-8), and never splitting a character; one piece if it fits, and a
/// character bigger than `max` a piece of its own. An empty text is one
/// empty piece.
pub fn split_text(text: &str, max: usize, size: impl Fn(char) -> usize) -> Vec<&str> {
    let mut pieces = Vec::new();
    let (mut start, mut used) = (0, 0);
    for (at, c) in text.char_indices() {
        let n = size(c);
        if used + n > max && at > start {
            pieces.push(&text[start..at]);
            (start, used) = (at, 0);
        }
        used += n;
    }
    pieces.push(&text[start..]);
    pieces
}

/// How many of `costs`, in order, go in each group so each group's total
/// is at most `budget`: as many as fit, though at least one, so an item
/// over `budget` is a group of its own. Its lengths add up to
/// `costs.len()`; none for no items.
pub fn groups(costs: &[usize], budget: usize) -> Vec<usize> {
    let mut out = Vec::new();
    let (mut count, mut used) = (0, 0);
    for &cost in costs {
        if count > 0 && used + cost > budget {
            out.push(count);
            (count, used) = (0, 0);
        }
        count += 1;
        used += cost;
    }
    if count > 0 {
        out.push(count);
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn text_splits_at_characters_under_the_limit() {
        let utf8 = |c: char| c.len_utf8();
        assert_eq!(split_text("", 4, utf8), vec![""]);
        assert_eq!(split_text("abcd", 4, utf8), vec!["abcd"]);
        assert_eq!(split_text("abcdef", 4, utf8), vec!["abcd", "ef"]);
        // `é` is 2 bytes, `𝄞` 4: never split, each piece at most 4.
        assert_eq!(split_text("aé𝄞bé", 4, utf8), vec!["aé", "𝄞", "bé"]);
        // A character past the limit is a piece of its own.
        assert_eq!(split_text("a𝄞b", 2, utf8), vec!["a", "𝄞", "b"]);
    }

    #[test]
    fn groups_fill_up_to_the_budget() {
        assert_eq!(groups(&[], 10), Vec::<usize>::new());
        assert_eq!(groups(&[3, 3, 3, 3], 6), vec![2, 2]);
        assert_eq!(groups(&[3, 3, 3, 3], 7), vec![2, 2]);
        assert_eq!(groups(&[3, 3, 3, 3], 9), vec![3, 1]);
        // An item over the budget is a group of its own.
        assert_eq!(groups(&[1, 20, 1], 5), vec![1, 1, 1]);
    }
}
