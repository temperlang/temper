### Control flow revamped in frontend

A large scale reorganization of the Temper frontend changes the
intermediate representation of control flow.
This change is aimed at enabling feature work but there are a
number of user visible changes.

#### Capture of local variables in loop bodies

```temper
let lb = new ListBuilder<fn (): Int>();
for (var i = 0; i < 10; ++i) {
  // Locals declared in loop bodies are captured separately per iteration.
  let j = i + 1;
  lb.add(fn (): Int { j });
}
let ls = lb.toList();
ls.forEach { (element: fn (): Int);;
  console.log(element().toString(10));
}
```

Previously, running that in `temper repl` output `10 10 10 10 ... 10` but
now, that outputs `1 2 3 4 ... 10`.

#### Explicit `return bubble()` is equivalent to implicit return of `bubble()`

Previously, these two functions translated subtly differently.

```temper
// Explicit return
let f1(): Bubble { return bubble() }

// Implicit return
let f2(): Bubble { bubble() }
```

The *bubble* function produces no usable value and, when it appears
inside the left of an *orelse*, jumps to the right.

Now, *bubble* is consistently recognized as ending a branch of control
flow without a need to capture a result.

This makes it easier to write backends for languages with strict
"no dead code after `throw`" policies, and leads to more idiomatic
code in existing backends.

#### Fewered labeled break statements in generated code

Previously, our intermediate representation required decompiling
a control-flow graph to recover structured flow control
(`if`, `while`, `break`, `continue`).

The decompiler introduced some labeled blocks in situations where
they were not strictly necessary: nested loops with complex
interactions.

This should lead to more idiomatic translation on many backends
and more performant translation on un-optimized Python, a language
which does not support labeled blocks.
