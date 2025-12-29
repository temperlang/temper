# Regex Data Model and Functionality

The structural data model for regex patterns enables direct construction, and
the Temper regex dialect compiles static regex text patterns to these objects.

A focus here is on providing tools people can actually reach for when they need
to do text processing. The execution should be faster on backends like Python
than writing raw code, and the implementation in backends like C should
approximate what you'd like to have written manually.

Due to inadequate and distinct Unicode handling in backend regex engines, the
initial feature set avoids character classes and properties but is still aware
of code points. Parsing focused on limited sets of delimiters works best for
now.

The core feature set here focuses on both the data model and utility functions,
such as matching regexes against strings.

## Regex Data Model

Temper regex representation is composed hierarchically of `RegexNode` objects.
And perhaps the most fundamental `RegexNode` is the [Sequence](#sequence),
because it enables multiple regex components to be strung together. For example,
`/hi./` is a sequence of [CodePoints](#codepoints) `/hi/` and dot `/./`, but all
component types extend the `RegexNode` interface.

```
export interface RegexNode {
```

Before a regex is used, it must be compiled. This transforms a `RegexNode` tree
into a backend-native [Regex](#regex). Some helper functions compile on the fly,
although it is faster to reuse a pre-compiled regex.

```
  // TODO(tjp, regex): Make this into a macro behind the scenes.
  // TODO(tjp, regex): `compiled<T>(): Regex<T>`
  public compiled(): Regex { new Regex(this) }
```

The simplest use of a regular expression is if it is found in a string.
Again, it is better to compile to a `Regex` in advance than to repeatedly call
convenience methods on a `RegexNode`.

TODO Remove convenience methods on RegexNode since we emphasize CompiledRegex
now?

```
  public found(text: String): Boolean { compiled().found(text) }
```

You can also return match details. The returned groups map contains an entry for
each key in the order defined in the regex pattern. If no "full" group is
defined, one is added automatically to capture the full matched text.

In the future, we intend to support customized match types with fields to match
capture groups, statically checked where possible.

```
  // TODO(tjp, regex): Also macro because reification.

  public find(text: String): Match throws Bubble {
    compiled().find(text)
  }
```

Replace and split functions are also available. Both apply to all matches in
the string, replacing all or splitting at all.

```
  public replace(text: String, format: fn (Match): String): String {
    compiled().replace(text, format)
  }

  public split(text: String): List<String> {
    compiled().split(text)
  }
```

That's it for what you can do with regex patterns in Temper today, but there's
much more to say on what kinds of regexes can be built.

```
}
```

## Regex Item Types

A `Regex` is composed of a potential variety of subtypes.

### Groups

Multiple types of groups exist:

- [Capture](#capture) `/(?<name>...)/` to remember match groups for later use.
- Non-capturing group syntax `/(?:...)/`, which is simply a [Regex](#regex)
  instance in the data model.

### Capture

`Capture` is a [group](#groups) that remembers the matched text for later
access. Temper supports only named matches, with current intended syntax
`/(?name = ...)/`.

```
export class Capture(
  public let name: String,
  public /*early*/ let item: RegexNode,
) extends RegexNode {}
```

### CodePart

A component of a [CodeSet][#codeset], aka character class, which applies to a
subset of regex data types.

Here, "code" is short for "code point" although "char" might work better,
depending on expectations.

```
export interface CodePart extends RegexNode {}
```

### CodePoints

One or more verbatim code points, where the sequence matters if within a
[Regex](#regex) or not if within a [CodeSet](#codeset). Some escapes in
textual regex source, such as `/\t/`, can be stored as raw code points.

The `String` here can enable more efficient storage than individual code
points, although the source text may require non-capture grouping. For example,
`/(?:abc)?/` optionally matches the string `"abc"`, whereas `/abc?/` matches
`"ab"` with an optional `"c"`.

```
export class CodePoints(
  public let value: String,
) extends CodePart {}
```

### Specials

A number of special match forms exist. In the data model, these are empty
classes.

- `.` - `Dot` In default mode, matches any Unicode code point except newline.
- `^` - `Begin` in default mode matches zero-length at the beginning of a
  string.
- `$` - `End` in default mode matches zero-length at the end of a string.
- `\b` - `WordBoundary` matches zero-length at the boundary between word and
  non-word code points. More sophisticated Unicode compliance is TBD.
- `\s` (negated as `\S`) - `Space` matches any horizontal space code point.
  Details are TBD.
- `\w` (negated as `\W`) - `Word` matches any word code point. Details are TBD.
  This is currently defined in terms of old ASCII definitions because those are
  clear. Perhaps this will stay that way, and Unicode properties like `\p{L}`
  will be used for human language needs.
- `\X` - `GraphemeCluster` might not be supported, but [here is some discussion
  of how to implement it](
  https://github.com/rust-lang/regex/issues/54#issuecomment-661905060).

<details>

```
export interface Special extends RegexNode {}
export let Begin: Special = do { class Begin extends Special {}; new Begin() };
export let Dot: Special = do { class Dot extends Special {}; new Dot() };
export let End: Special = do { class End extends Special {}; new End() };
// TODO(tjp, regex): We can't easily support this at present across backends.
// export let GraphemeCluster = do {
//   class GraphemeCluster extends Special {}; new GraphemeCluster()
// };
export let WordBoundary: Special = do {
  class WordBoundary extends Special {}; new WordBoundary()
};

export interface SpecialSet extends CodePart & Special {}
export let Digit: SpecialSet = do {
  class Digit extends SpecialSet {}; new Digit()
};
export let Space: SpecialSet = do {
  class Space extends SpecialSet {}; new Space()
};
export let Word: SpecialSet = do {
  class Word extends SpecialSet {}; new Word()
};
```

</details>

### CodeRange

A code point range matches any code point in its inclusive bounds, such as
`/[a-c]/`. In source, `-` is included in a code set either by escaping or by
including it as the first or last character. A `CodeRange` is usually contained
inside a [CodeSet](#codeset), and syntactically always is.

```
export class CodeRange(
  public let min: Int,
  public let max: Int,
) extends CodePart {}
```

### CodeSet

A set of code points, any of which can match, such as `/[abc]/` matching any of
`"a"`, `"b"`, or `"c"`. Alternatively, a negated set is the inverse of the code
points given, such as `/[^abc]/`, matching any code point that's not any of
these. This is also often called a character class.

Further, a subset of [specials](#specials) can also be used in code sets. A
negated code set of just a special set often has custom syntax. For example,
non-space can be said as either `/[^\s]/` or `/\S/`.

```
export class CodeSet(
  public let items: List<CodePart>,
  public let negated: Boolean = false,
) extends RegexNode {}
```

### Or

`Or` matches any one of multiple options, such as `/ab|cd|e*/`.

```
export class Or(
  public /*early*/ let items: List<RegexNode>,
) extends RegexNode {}
```

### Repeat

`Repeat` matches from an minimum to a maximum number of repeats of a
subregex. This can be represented in regex source in a number of ways:

- `?` matches 0 or 1.
- `*` matches 0 or more.
- `+` matches 1 or more.
- `{m}` matches exactly `m` repetitions.
- `{m,n}` matches between `m` and `n`. Missing `n` is a max of infinity. For
  example, `{0,1}` is equivalent to `?`, and `{1,}` is equivalent to `+`.

By default, repetitions are greedy, matching as many repetitions as possible.
In regex source, any of the above can have `?` appended to indicated reluctant
(aka non-greedy), matching as few repetitions as possible.

```
export class Repeat(
  public /*early*/ let item: RegexNode,
  public let min: Int,
  public let max: Int?, // where null means infinite
  public let reluctant: Boolean = false,
) extends RegexNode {}
```

We also have convenience builders.

```
export let entire(item: RegexNode): RegexNode {
  new Sequence([Begin, item, End])
}

export let oneOrMore(item: RegexNode, reluctant: Boolean = false): Repeat {
  { item, min: 1, max: null, reluctant }
}

export let optional(item: RegexNode, reluctant: Boolean = false): Repeat {
  { item, min: 0, max: 1, reluctant }
}
```

### Sequence

`Sequence` strings along multiple other regexes in order.

```
export class Sequence(
  public /*early*/ let items: List<RegexNode>,
) extends RegexNode {}
```

## Match Objects

For detailed match results, call `find` on a regex to get a `Match` object
including a map from `String` keys to `Group` values. The iteration order of the
group map is undefined.

We might support custom match types in the future with static properties instead
of string-keyed groups. For example: `regex.find<MyCapture>("....")`

TODO And sooner than that, we plan connected types for abstract mapping to
backend match objects that might not be maps.

```
export class Match(
  public let full: Group,
  public let groups: Map<String, Group>,
) {} // interface ... <T = Map<String, Group>> {

export class Group(
  public let name: String,
  public let value: String,
  public let begin: StringIndex,
  public let end: StringIndex,
) {}
```

## Compiled Regex Objects

The compiled form of a regex is mostly opaque, but it can be cached for more
efficient reuse than working from a source [RegexNode](#regex-data-model).

<details>

```
// Provides a workaround for access to std/regex from extension methods that
// sometimes get defined in temper-core for some backends. Also useful for
// reference values for the interpreter.
// TODO Avoid defining regex support in temper-core.
class RegexRefs(
  public let codePoints: CodePoints = new CodePoints(""),
  public let group: Group = {
    name: "", value: "", begin: String.begin, end: String.begin
  },
  public let match: Match = {
    full: group,
    groups: new Map<String, Group>([new Pair("", group)]),
  },
  public let orObject: Or = new Or([]),
) {}

let regexRefs = new RegexRefs();
```

</details>

TODO Make Regex a connected type for lighter weight usage?

```
export class Regex {
```

The source `Regex` data is still available on compiled objects in case it's
needed for composition or other purposes.

```
  public let data: RegexNode;

  public constructor(data: RegexNode) {
    this.data = data;
    // TODO Pull formatting out of here into a separate library or module???
    let formatted = RegexFormatter.regexFormat(data);
    compiled = RegexFormatter.regexCompileFormatted(data, formatted);
  }
```

A compiled regex exposes many of the same capabilities as `RegexNode`, but they
are more efficient to use repeatedly.

```
  public found(text: String): Boolean {
    compiledFound(compiled, text)
  }

  public find(text: String, begin: StringIndex = String.begin): Match throws Bubble {
    compiledFind(compiled, text, begin, regexRefs)
  }

  public replace(text: String, format: fn (Match): String): String {
    compiledReplace(compiled, text, format, regexRefs)
  }

  public split(text: String): List<String> {
    compiledSplit(compiled, text, regexRefs)
  }
```

TODO(tjp, regex): Any static checking for stable frontend regex values?

<details>

```
  private let compiled: AnyValue;

  // Extension functions on some backends need the private `compiled` value
  // passed in directly.
  @connected("Regex::compiledFound")
  private compiledFound(compiled: AnyValue, text: String): Boolean;

  @connected("Regex::compiledFind")
  private compiledFind(
    compiled: AnyValue, text: String, begin: StringIndex, regexRefs: RegexRefs
  ): Match throws Bubble;

  @connected("Regex::compiledReplace")
  private compiledReplace(
    compiled: AnyValue,
    text: String,
    format: fn (Match): String,
    regexRefs: RegexRefs,
  ): String;

  @connected("Regex::compiledSplit")
  private compiledSplit(
    compiled: AnyValue,
    text: String,
    regexRefs: RegexRefs,
  ): List<String>;
```

</details>

```
}
```

## Private implementation matters?

Some regex logic can be shared across backends. These features aren't directly
exported to the user, however.

TODO But we do want this exported for pre-compiling where possible in backends.

The intent is that these support features only get included in compiled Temper
code if usage depends on dynamically constructed regexes. If all regex building
is done as stable values, we hope to generated backend compiled regexes purely
at Temper compile time.

### RegexFormatter

<details>

```
class RegexFormatter {
  private let out: StringBuilder = new StringBuilder();

  @connected("Regex::compileFormatted")
  public static regexCompileFormatted(
    data: RegexNode, formatted: String
  ): AnyValue;

  @connected("Regex::format")
  public static regexFormat(data: RegexNode): String {
    new RegexFormatter().format(data)
  }

  public format(regex: RegexNode): String {
    pushRegex(regex)
    out.toString()
  }

  private pushRegex(regex: RegexNode): Void {
    when (regex) {
      // Aggregate types.
      is Capture -> pushCapture(regex);
      is CodePoints -> pushCodePoints(regex, false);
      is CodeRange -> pushCodeRange(regex);
      is CodeSet -> pushCodeSet(regex);
      is Or -> pushOr(regex);
      is Repeat -> pushRepeat(regex);
      is Sequence -> pushSequence(regex);
      // Specials.
      // Some of these will need to be customized on future backends.
      Begin -> out.append("^");
      Dot -> out.append(".");
      End -> out.append("$");
      WordBoundary -> out.append("\\b");
      // Special sets.
      Digit -> out.append("\\d");
      Space -> out.append("\\s");
      Word -> out.append("\\w");
      // ...
    }
  }

  private pushCapture(capture: Capture): Void {
    out.append("(");
    // TODO(tjp, regex): Consistent name validation rules for all backends???
    // TODO(tjp, regex): Validate here or in `Capture` constructor???
    // TODO(tjp, regex): Validate here or where against reused names???
    pushCaptureName(out, capture.name);
    pushRegex(capture.item);
    out.append(")");
  }

  @connected("RegexFormatter::pushCaptureName")
  private pushCaptureName(out: StringBuilder, name: String): Void {
    // All so far except Python use this form.
    out.append("?<${name}>");
  }

  private pushCode(code: Int, insideCodeSet: Boolean): Void { do {
    // Goal here is to be pretty where commonly accepted by regex dialects.
    // Start with pretty escapes to avoid needing numeric escapes.
    let specialEscape = when (code) {
      Codes.carriageReturn -> "r";
      Codes.newline -> "n";
      Codes.tab -> "t";
      else -> "";
    };
    if (specialEscape != "") {
      out.append("\\");
      out.append(specialEscape);
      return;
    }
    // Look up in table for ascii range.
    if (code <= 0x7F) {
      let escapeNeed = escapeNeeds[code];
      if (
        escapeNeed == needsSimpleEscape ||
        (insideCodeSet && code == Codes.dash)
      ) {
        out.append("\\");
        out.append(String.fromCodePoint(code));
        return;
      } else if (escapeNeed == needsNoEscape) {
        out.append(String.fromCodePoint(code));
        return;
      }
    }
    // Not handled, so check additional ranges for plain vs numeric escape.
    if (
      code >= Codes.supplementalMin || (
        code > Codes.highControlMax &&
        !(
          (Codes.surrogateMin <= code && code <= Codes.surrogateMax) ||
          code == Codes.uint16Max
        )
      )
    ) {
      out.append(String.fromCodePoint(code));
    } else {
      // No pretty options above, so go numeric. Each backend often varies here.
      pushCodeTo(out, code, insideCodeSet);
    }
  } orelse panic() } // fromCodePoint bubbles

  @connected("RegexFormatter::pushCodeTo")
  private pushCodeTo(out: StringBuilder, code: Int, insideCodeSet: Boolean): Void;

  private pushCodePoints(codePoints: CodePoints, insideCodeSet: Boolean): Void {
    let value = codePoints.value;
    for (
      var index = String.begin;
      value.hasIndex(index);
      index = value.next(index)
    ) {
      pushCode(value[index], insideCodeSet);
    }
  }

  private pushCodeRange(codeRange: CodeRange): Void {
    out.append("[");
    pushCodeRangeUnwrapped(codeRange);
    out.append("]");
  }

  private pushCodeRangeUnwrapped(codeRange: CodeRange): Void {
    pushCode(codeRange.min, true);
    out.append("-");
    pushCode(codeRange.max, true);
  }

  private pushCodeSet(codeSet: CodeSet): Void {
    let adjusted = adjustCodeSet(codeSet, regexRefs);
    when (adjusted) {
      is CodeSet -> do {
        out.append("[");
        if (adjusted.negated) {
          out.append("^");
        }
        for (var i = 0; i < adjusted.items.length; i += 1) {
          pushCodeSetItem(adjusted.items[i]);
        }
        out.append("]");
      }
      else -> pushRegex(adjusted);
    }
  }

  @connected("RegexFormatter::adjustCodeSet")
  private adjustCodeSet(codeSet: CodeSet, regexRefs: RegexRefs): RegexNode { codeSet }

  private pushCodeSetItem(codePart: CodePart): Void {
    when (codePart) {
      is CodePoints -> pushCodePoints(codePart, true);
      is CodeRange -> pushCodeRangeUnwrapped(codePart);
      is SpecialSet -> pushRegex(codePart);
    }
  }

  private pushOr(or: Or): Void {
    if (!or.items.isEmpty) {
      out.append("(?:");
      // TODO(tjp, regex): See #822. Until `this` works better, no this in funs.
      // TODO(tjp, regex): So just manually loop here. Sometimes faster, anyway?
      pushRegex(or.items[0]);
      for (var i = 1; i < or.items.length; i += 1) {
        out.append("|");
        pushRegex(or.items[i]);
      }
      out.append(")");
    }
  }

  private pushRepeat(repeat: Repeat): Void {
    // Always wrap the main sub-pattern here to make life easy
    out.append("(?:");
    pushRegex(repeat.item);
    out.append(")");
    // Then add the repetition part.
    let min = repeat.min;
    let max = repeat.max;
    if (false) {
    } else if (min == 0 && max == 1) {
      out.append("?");
    } else if (min == 0 && max == null) {
      out.append("*");
    } else if (min == 1 && max == null) {
      out.append("+");
    } else {
      out.append("{${min}");
      if (min != max) {
        out.append(",");
        if (max != null) {
          out.append(max.toString());
        }
      }
      out.append("}");
    }
    if (repeat.reluctant) {
      out.append("?");
    }
  }

  private pushSequence(sequence: Sequence): Void {
    // TODO(tjp, regex): Foreach loop/function would be nice.
    for (var i = 0; i < sequence.items.length; i += 1) {
      pushRegex(sequence.items[i]);
    }
  }

  // Put this here instead of the data model for now because I'm not sure this
  // makes sense to be part of the public api right now.
  public maxCode(codePart: CodePart): Int? {
    when (codePart) {
      is CodePoints -> do {
        // Iterating code points is the hardest of the current cases.
        let value = codePart.value;
        if (value.isEmpty) {
          null
        } else {
          // My kingdom for a fold, or even just a max, in builtins.
          var max = 0;
          for (
            var index = String.begin;
            value.hasIndex(index);
            index = value.next(index)
          ) {
            let next = value[index];
            if (next > max) {
              max = next;
            }
          }
          max
        }
      }
      // Others below are easy for now.
      is CodeRange -> codePart.max;
      Digit -> Codes.digit9;
      Space -> Codes.space;
      Word -> Codes.lowerZ;
      // Actually unexpected, ever, but eh.
      else -> null;
    }
  }
}

// Cache which chars you just but a blackslash in front of for escaping.
let escapeNeeds = buildEscapeNeeds();
let needsNoEscape = 0;
let needsNumericEscape = 1;
let needsSimpleEscape = 2;
let buildEscapeNeeds(): List<Int> {
  let escapeNeeds = new ListBuilder<Int>();
  for (var code = 0; code < 0x7F; code += 1) {
    escapeNeeds.add(
      if (
        // Dash needs escaping in code sets, but we'll handle that specially.
        code == Codes.dash ||
        code == Codes.space ||
        code == Codes.underscore ||
        (Codes.digit0 <= code && code <= Codes.digit9) ||
        (Codes.upperA <= code && code <= Codes.upperZ) ||
        (Codes.lowerA <= code && code <= Codes.lowerZ)
      ) {
        needsNoEscape
      } else if (
        // Ampersand and tilde need escaped only in python for now, but meh.
        code == Codes.ampersand ||
        code == Codes.backslash ||
        code == Codes.caret ||
        code == Codes.curlyLeft ||
        code == Codes.curlyRight ||
        code == Codes.dot ||
        code == Codes.peso ||
        code == Codes.pipe ||
        code == Codes.plus ||
        code == Codes.question ||
        code == Codes.roundLeft ||
        code == Codes.roundRight ||
        code == Codes.slash ||
        code == Codes.squareLeft ||
        code == Codes.squareRight ||
        code == Codes.star ||
        code == Codes.tilde
      ) {
        needsSimpleEscape
      } else {
        // We'll also handle \r, \n, and \t specially.
        needsNumericEscape
      },
    );
  }
  escapeNeeds.toList()
}

class Codes {
  public static ampersand: Int = char'&';
  public static backslash: Int = char'\\';
  public static caret: Int = char'^';
  public static carriageReturn: Int = char'\r';
  public static curlyLeft: Int = char'{';
  public static curlyRight: Int = char'}';
  public static dash: Int = char'-';
  public static dot: Int = char'.';
  public static highControlMin: Int = 0x7F;
  public static highControlMax: Int = 0x9F;
  public static digit0: Int = char'0';
  public static digit9: Int = char'9';
  public static lowerA: Int = char'a';
  public static lowerZ: Int = char'z';
  public static newline: Int = char'\n';
  public static peso: Int = char'$';
  public static pipe: Int = char'|';
  public static plus: Int = char'+';
  public static question: Int = char'?';
  public static roundLeft: Int = char'(';
  public static roundRight: Int = char')';
  public static slash: Int = char'/';
  public static squareLeft: Int = char'[';
  public static squareRight: Int = char']';
  public static star: Int = char'*';
  public static tab: Int = char'\t';
  public static tilde: Int = char'*';
  public static upperA: Int = char'A';
  public static upperZ: Int = char'Z';
  public static space: Int = char' ';
  public static surrogateMin: Int = 0xD800;
  public static surrogateMax: Int = 0xDFFF;
  public static supplementalMin: Int = 0x10000;
  public static uint16Max: Int = 0xFFFF;
  public static underscore: Int = char'_';
}

```

</details>
