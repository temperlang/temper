# Java split

Code past the JVM's limits splits (`split.rs`): a list literal in parts,
each a nested class's method, a long string literal in pieces joined at
run time, and a long static initializer in groups of statements, each a
nested class's `run()`. Its limits are far higher than this program, so
its golden shows it as it is; `split_shape` translates it with limits
low enough that every part splits, and its golden
(`java-split.split.java`) shows how.

    let words: List<String> = ["alpha", "beta", "gamma", "delta", "epsilon", "zeta"];
    let pairs = new Map([new Pair("a", 1), new Pair("b", 2), new Pair("c", 3)]);
    let note = "a long string, é✓𝄞, in pieces";
    let built = new ListBuilder<String>();
    built.add("x");
    built.add("y");
    built.add("z");

    export let numbers(n: Int): List<String> {
      if (n > 0) { ["one", "two", "three", "four"] } else { [] }
    }

    console.log("${words.length.toString()} ${words[5]} ${pairs.length.toString()}");
    console.log(note);
    console.log("${built.toList().join(",") { it => it }} ${numbers(1).join(",") { it => it }}");

```log
6 zeta 3
a long string, é✓𝄞, in pieces
x,y,z one,two,three,four
```
