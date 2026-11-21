//! Splitting what's too big for a target language in one piece, which
//! backends share: some compilers limit a string literal's size (javac's
//! constants to 65,535 bytes, MSVC's literals to 16,380 bytes a piece),
//! or a method's code (the JVM's 64 KB), so a backend writes a long
//! string as pieces ([`split_text`]) and many items as groups, each in
//! a helper of its own ([`groups`], [`in_groups`], [`parts`]), and long
//! runs of statements in groups ([`group_runs`]), only where they're past
//! its limits, so everyday output stays as it was.

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

/// `items`, in order, in groups of the sizes [`groups`] gives; what's
/// left past their total, if any, a group of its own.
pub fn in_groups<T>(items: Vec<T>, sizes: &[usize]) -> Vec<Vec<T>> {
    let mut rest = items.into_iter();
    let mut out: Vec<Vec<T>> = sizes
        .iter()
        .map(|&n| rest.by_ref().take(n).collect())
        .collect();
    let left: Vec<T> = rest.collect();
    if !left.is_empty() {
        out.push(left);
    }
    out
}

/// `items`, in order, in parts of `n` (at least 1), the last maybe
/// fewer; none for no items.
pub fn parts<T>(items: Vec<T>, n: usize) -> Vec<Vec<T>> {
    let n = n.max(1);
    let sizes = vec![n; items.len().div_ceil(n)];
    in_groups(items, &sizes)
}

/// `stmts` with each run of those it may group (`groupable`) longer than
/// `group` in groups of `group`, each made one (`wrap`), such as a
/// closure called at once; all as they are where there are no more than
/// `run`.
pub fn group_runs<T>(
    stmts: Vec<T>,
    run: usize,
    group: usize,
    groupable: impl Fn(&T) -> bool,
    wrap: impl Fn(Vec<T>) -> T,
) -> Vec<T> {
    if stmts.len() <= run {
        return stmts;
    }
    let mut out = Vec::new();
    let mut current: Vec<T> = Vec::new();
    let flush = |current: &mut Vec<T>, out: &mut Vec<T>| match current.len() <= group {
        true => out.append(current),
        false => out.extend(parts(std::mem::take(current), group).into_iter().map(&wrap)),
    };
    for s in stmts {
        if groupable(&s) {
            current.push(s);
        } else {
            flush(&mut current, &mut out);
            out.push(s);
        }
    }
    flush(&mut current, &mut out);
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

    #[test]
    fn items_in_groups_and_parts() {
        assert_eq!(
            in_groups(vec![1, 2, 3, 4, 5], &[2, 1]),
            vec![vec![1, 2], vec![3], vec![4, 5]]
        );
        assert_eq!(
            parts(vec![1, 2, 3, 4, 5], 2),
            vec![vec![1, 2], vec![3, 4], vec![5]]
        );
        assert_eq!(parts(Vec::<i32>::new(), 2), Vec::<Vec<i32>>::new());
    }

    /// Runs of groupable statements (here, positive numbers) past the
    /// group size are grouped (here, summed, negated to tell them),
    /// only where there are more statements than the run allows.
    #[test]
    fn runs_group() {
        let grouped = |stmts: Vec<i32>, run| {
            group_runs(stmts, run, 2, |s| *s > 0, |g| -g.iter().sum::<i32>())
        };
        assert_eq!(grouped(vec![1, 2, 3], 3), vec![1, 2, 3]);
        assert_eq!(grouped(vec![1, 2, 3, 0, 4, 5], 3), vec![-3, -3, 0, 4, 5]);
    }
}
