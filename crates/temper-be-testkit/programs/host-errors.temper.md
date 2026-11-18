# Host errors

`orelse` handles only what Temper declares may bubble. A builtin that
isn't declared to bubble may still fail on a host, as reading past a
list's end does (the docs call it a panic), and that failure passes
every `orelse`, as a panic does, even inside a function that bubbles
for other reasons:

    let read(ls: List<Int>, i: Int): Int throws Bubble {
      if (i < 0) { bubble() }
      ls[i]
    }
    console.log("reading");
    let r = read([1, 2], 5) orelse -1;
    console.log("read past the end ${r}");
    panic();

So the run fails right after `reading` where reading past the end
fails on the host (Java, C#, Python, and JS, whose runtime's bubble
there fails for want of an import) or panics (Rust, the interpreter):

```log interp csharp java js py rust
reading
```

C++ and Lua still bubble there, as Kotlin's runtimes meant to, until
list reads panic everywhere; the `panic()` after ends their run:

```log cpp lua
reading
read past the end -1
```
