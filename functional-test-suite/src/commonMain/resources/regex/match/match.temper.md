# Regex Match Test

Match compile-time constant regexes against strings.

Compile-time constant regexes used only for matching can be converted by
backends directly into the backend representation. We don't need to keep our
own representation around at all.

Compile-time transformation isn't yet implemented, but the same behavior is
supported at runtime as well. Once both are supported, we should test both.

## Test Case 1

### Structurally built regex patterns

In addition to a macro to parse our own regex dialect at compile-time, you can
also build regexes structurally. The macro parser builds these objects.

    // Equivalent to: let regex = /[ab]+c/;
    let regex = new Sequence([
      oneOrMore(new CodeSet([new CodePoints("ab")])),
      new CodePoints("c"),
    ]).compiled();
    let full = entire(regex.data).compiled();

### Matching regex objects against strings

Here we test only Boolean matches. We will test found strings and captures in
the future.

    let strings = [
      // Positive found and matches
      "abc",
      "ac",
      "bbabac",
      // Negative found and matches
      "ab",
      "dc",
      // Positive found, negative matches
      "acc",
      "cbc",
    ];
    // TODO(tjp, regex): Use forEach when we have that instead of abusing map.
    strings.forEach { string =>
      console.log("found ${string}: ${regex.found(string)}");
      console.log("matches ${string}: ${full.found(string)}");
    }

### Expected results

```log
found abc: true
matches abc: true
found ac: true
matches ac: true
found bbabac: true
matches bbabac: true
found ab: false
matches ab: false
found dc: false
matches dc: false
found acc: true
matches acc: false
found cbc: true
matches cbc: false
```

## Test Case 2: Match details

### Match found

We can also get detailed match information. Different backends have different
capabilities. For example, .NET can report all repeated groups for a given
capture, but some remember only one, so support only what's common here.

    let catcher = /a+(?intro=b+)c((?option1=d)|(?alphaOption2=e))+/;

Now capture and print everything out.

Also, use supplemental plane code point to start out, and leave a suffix.

    let text = "🌍aaabbceef";
    let match = catcher.find(text);
    let checkGroup(group: Group): Void {
      let { name, value, begin, end } = group;
      let size = text.countBetween(begin, end).toString();
      let begin = text.countBetween(String.begin, begin).toString();
      console.log(
        "match group '${name}': '${value}' at ${begin} size ${size}"
      );
    }
    checkGroup(match.full);
    checkGroup(match.groups["intro"]);
    checkGroup(match.groups["alphaOption2"]);

Map iteration order is defined to be the order in which names appear in the pattern, so this list is reliable.

```log
match group 'full': 'aaabbcee' at 1 size 8
match group 'intro': 'bb' at 4 size 2
match group 'alphaOption2': 'e' at 8 size 1
```

### Find from index

Picking up where we leave off is useful for parsing richer text.

    let thing = /[a-z]+/;
    let multiText = "---abc---xyz---";
    let first = thing.find(multiText);
    let second = thing.find(multiText, first.full.end);
    console.log("Found both ${first.full.value} and ${second.full.value}.");

```log
Found both abc and xyz.
```

### Replace

We can also do replacement.

Note that callbacks are supported in at least dotnet, java, js, and python. They
also more naturally adapt to custom types. And purposely use code points beyond
16 bits in the spacing.

    // let replaced = catcher.replace<Result>("🌍aaabbceef") { result => ... }
    let moreText = "🌍aaabbcee🌎abcd🌏";
    let replaced = catcher.replace(moreText) { match =>
      // Can we compile-time convert callbacks to simple template as applicable?
      let { groups } = match;
      let option1 = groups["option1"].value orelse "";
      let alphaOption2 = groups["alphaOption2"].value orelse "";
      let begin = moreText.countBetween(String.begin, match.full.begin);
      "-${option1}${alphaOption2}${begin}-"
    };
    console.log("replaced: ${replaced}");
    // Also check a no-match case.
    let notReplaced = catcher.replace("nope") { match => "hi" };
    console.log("not replaced: ${notReplaced}");

And, related to discussion above, only one "e" here because the capture is
inside the repeat.

```log
replaced: 🌍-e1-🌎-d10-🌏
not replaced: nope
```

### No match

And when we have no match, can use `orelse` for a default result.

    let noMatch = catcher.find("not here").full.value orelse "sorry";
    console.log("No match says ${noMatch}");

```log
No match says sorry
```

## Additional Tests

At some point, we should port the randomized property testing of regexes from
Kotlin to Temper, but for now, here are a few hand-designed tests of additional
features.

This still doesn't test everything, but it tests at least some things.

### Or options

We also have convenience methods on uncompiled regex data. They require
inefficient compiling on the fly, but they should still work.

    let or = /ab|bc/.data;
    let option1 = "ab";
    let option2 = "bc";
    let optionNone = "cb";
    console.log("or option 1: ${or.found(option1)}");
    console.log("or option 2: ${or.found(option2)}");
    console.log("or option none: ${or.found(optionNone)}");

```log
or option 1: true
or option 2: true
or option none: false
```

### String begin

    let begin = /^a/;
    let beginsWithATrue = "ab";
    let beginsWithAFalse = "bab";
    console.log("begin true: ${begin.found(beginsWithATrue)}");
    console.log("begin false: ${begin.found(beginsWithAFalse)}");

```log
begin true: true
begin false: false
```

### Negated set implies supplementary code range

These are tricky in JS, at least for V8 and SpiderMonkey. Starting with simple
char 'a' before the inverted code range is what's tricky.

    let negatedBasic = entire(/a[^a-c]/.data).compiled();
    let nbTrue = "a🌍";
    let nbFalse = "ac";
    console.log("negated basic true: ${negatedBasic.found(nbTrue)}");
    console.log("negated basic false: ${
      negatedBasic.found(nbFalse).toString()
    }");

```log
negated basic true: true
negated basic false: false
```

### Word boundaries

Define a regex for simple identifiers with word boundaries. For this case, be
explicit rather than using `Word` for content.

TODO Use regex interpolation once we have that.

    let idStart = /[a-zA-Z_]/.data;
    let idContinue = new Or([idStart, /[0-9]/.data]);
    let word = new Sequence([
      WordBoundary,
      idStart,
      new Repeat(idContinue, 0, null),
      WordBoundary,
    ]).compiled();

Try it out, where `"123_abc"` starts with digits, and because word boundaries,
we also should skip the `"_abc"`, where the `"_"` also matters here. This tests
a specific case we had trouble with in Lua. This whole thing ends up exercising
more than the Lua issue, and that's ok. Helped find an issue in JS also.

    let expected = "def_456";
    let found = word.find("123_abc ${expected} ghi_789").full.value;
    console.log("Found id: ${found}");

```log
Found id: def_456
```

### Regex string interpolation

We don't yet support interpolating regex values, but we can interpolate string
values into regexes, which supports automatic escaping as appropriate. So let's
start with some chars that normally need escaping to be treated as text in a
regex.

    let stringToEscape = "$(*^";

Also, `rgx` is the current prefix for regex literals that need interpolation.
This name might change in the future.

    let regexWithInterpolatedString = rgx".${stringToEscape}+.";
    let stringToEscapeAndMore = "a${stringToEscape}${stringToEscape}b";
    let escapedMatch = regexWithInterpolatedString.find(stringToEscapeAndMore);
    console.log(escapedMatch.full.value);

```log
a$(*^$(*^b
```

### Explicit full group

Any custom group named full goes under `groups`, which is different from the
full match that goes directly under the `full` property.

This behavior isn't really surprising, but we used to have different semantics
for "full", so this test evaluates the transition somewhat.

    let ownFullMatch = /.(?full=.)/.find("ab");
    let ownFull = ownFullMatch.groups["full"].value;
    console.log("Own full: ${ownFull} vs ${ownFullMatch.full.value}");

```log
Own full: b vs ab
```

### Split

Sometimes, splitting by regex is much more convenient that splitting by fixed
strings.

    let comboLines =
      "It was the best\nof times.\r\n\rIt was the worst of times.";
    // TODO Replace with for-of once it's supported across backends.
    /\n|\r\n?/.split(comboLines).forEach { line =>
      console.log("${line};");
    }

```log
It was the best;
of times.;
;
It was the worst of times.;
```

## Import

Import at the end to prove we can. That was failing for this test case in the
past.

    let { ... } = import("std/regex");
