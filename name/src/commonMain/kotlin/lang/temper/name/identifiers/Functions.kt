package lang.temper.name.identifiers

import lang.temper.name.identifiers.Tok.Nil

/** Use a breaker to separate an identifier into [Segment]s. */
internal inline fun segments(
    text: String,
    crossinline breaker: WordBreaker,
): List<Segment> = buildList {
    breaker(text) { w, s ->
        add(Segment(w, s.toString()))
    }
}

/** Use a mapper and delimiter to join [Segment]s into an identifier. */
internal inline fun joining(
    segments: Iterable<Segment>,
    crossinline mapper: WordMapper,
    crossinline delimiter: WordDelimiter,
): String = buildString {
    var pw = Nil
    segments.forEach { (w, s) ->
        if (s.isNotEmpty()) {
            append(delimiter(pw, w))
            append(mapper(pw, w, s))
            pw = w
        }
    }
    append(delimiter(pw, Nil))
}

/** Use a breaker, mapper and delimiter to convert an identifier from one form to another. */
internal inline fun convert(
    text: String,
    crossinline breaker: WordBreaker,
    crossinline mapper: WordMapper,
    crossinline delimiter: WordDelimiter,
): String = buildString {
    var pw = Nil
    breaker(text) { w, s ->
        if (s.isNotEmpty()) {
            // Only use a delimiter to resolve ambiguity
            append(delimiter(pw, w))
            append(mapper(pw, w, s))
            pw = w
        }
    }
    append(delimiter(pw, Nil))
}
