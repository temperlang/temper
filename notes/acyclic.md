# No reference cycles

Part of the [rewrite plan](README.md). A design sketch, not yet agreed
with the Temper team; Kotlin Temper has always planned this but hasn't
worked it out or built it.

## Why

Where a backend has no garbage collector (C++, Rust), Temper's objects
are reference counted, which can't free cycles. Temper won't rely on a
cycle collector, and some backends lack weak references. So Temper has
always meant to make reference cycles a compile-time error. Users are
told so already ([the memory tutorial][tutorial]: code that builds
cycles "will cause compiler errors in the future"), with ways around
them: pass parents down while walking, or keep a graph's nodes in a
list and refer to them by index.

Sources: the tutorial, issues [#5][i5] ("does not reference" bounds on
type parameters) and [#7][i7] (`early` and `late` properties), from Mike
Samuel's early design, and the functional test
`semantics/mutually-referencing-types`, which shows what stays legal.

[tutorial]: ../docs/guide/tutorial/06-memory.md
[i5]: https://github.com/temperlang/temper/issues/5
[i7]: https://github.com/temperlang/temper/issues/7

## The idea: cycles need mutation or a leaked `this`

An object whose references are all set while it's built, to objects that
already exist, can only point to older objects, so immutable objects
can't form a cycle. A cycle needs one of:

- a **mutable reference** that can lead back to its owner: a `var`
  field, or an element of a builder (`ListBuilder`, `MapBuilder`), or a
  closure that captures a `var`; or
- **`this` escaping during construction**, so something older than the
  object comes to point at it, or the object at itself.

So mutation itself is fine. What's ruled out is a mutable reference whose
*type* could lead back to its owner. A `var count: Int` is fine, and so
are `Foo` and `Bar` each holding an immutable field of the other's type
(the functional test).

## The check

After typing, per library, over its types and those it imports:

1. **A "may reference" graph over types.** An edge from `A` to `B` when
   an `A` can hold a `B`: a field, a list element, a map key or value, or
   a captured variable, counting `B`'s subtypes, so an interface reaches
   every implementation. An edge is *mutable* when it's a `var` field, a
   builder's element, or a captured `var`.
2. **Every loop through a mutable edge is an error**, reported at that
   field, with the loop spelled out ("`Node.parent` can lead back to its
   `Node`: `Node.parent` → `Node`"). Loops of only immutable edges are
   fine. Strongly connected components find these in linear time.
3. **Generic instantiations** are checked where they happen: in
   `class Box<T>(public var value: T)`, `Box<Int>` is fine, but
   `Box<AnyValue>` could hold itself. Issue #5's bounds, spelled with the
   keyword `forbids` (the issue's placeholder was `⊣`), as in
   `class Box<T forbids Box<T>>`, would state the rule in the
   declaration, so the error is at the instantiation, and readers see why.
4. **No `this` escaping a constructor** (issue #7's `early`): until
   construction ends, `this` isn't stored, passed, returned, or captured.
   Flow facts ([typing.md](typing.md)) can check this. Its first half is
   done: until every field is set, `this` doesn't escape at all
   ([typing.md](typing.md), "Fields first", decided 2026-11-02).

## Open questions

- **Top types and open interfaces.** A `var` of type `AnyValue`, or of
  an interface anyone could implement later (including backend code
  behind `@connected`), may reach anything. Options: only interfaces
  whose implementations the library sees, bounds like #5's, or treating
  such fields as errors unless their declared type can't reach back.
- **Closures,** the usual source of real cycles (a handler stored on the
  object it handles). Capturing `this` or a `var` that may hold it would
  count as an edge; how much ordinary code that rejects needs trying.
- **Builders and core types:** `ListBuilder<T>`, `MapBuilder<K, V>`,
  `DenseBitVector`, promises, and generators all hold mutable references,
  so core's declarations need the same rules, maybe the bounds above.
- **Details of `forbids`** (where it may appear, and how it combines
  with other bounds), and whether #7's `early` still matters
  after the constructor changes (an open question on the issue).
- **Errors people can act on:** each should name the loop and suggest
  the usual fixes (drop the back reference, use indices, or pass context
  down).
- Matching Kotlin doesn't apply, since it doesn't check this yet, so the
  rules need agreeing with the team, and tests here and in Kotlin's
  functional tests.

## Where it fits

A slice late in [milestone 4](history/milestone-4.md), after classes, generics,
and flow facts, since it needs all three. Its errors are new, so it
could start as warnings until the rules are agreed.
