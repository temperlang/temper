### 🚨Breaking change: Numeric indexing operations panic

Bubbles require declaration or handling. They're best used in cases where they
are either hard to predict or possibly expensive to check for in advance. For
lists and other ordered collections, it's easy to know or to check when an item
isn't available. For that reason, the follow methods now panic instead of
bubble:

- Deque::removeFirst
- List::get
- ListBuilder::add
- ListBuilder::addAll
- ListBuilder::removeLast
- ListBuilder::set
- Listed::get
- Listed::reduce
- String::get

Allowing these to panic simplifies their usage. For example, the default
interpreter implementation of `List::toListBuilder` previously said this:

```temper
@connected("List::toListBuilder")
public toListBuilder(): ListBuilder<T> {
  let result: ListBuilder<T> = new ListBuilder<T>();
  result.addAll(this) orelse panic();
  result
}
```

But `orelse panic()` is no longer needed for the `addAll` call:

```temper
@connected("List::toListBuilder")
public toListBuilder(): ListBuilder<T> {
  let result: ListBuilder<T> = new ListBuilder<T>();
  result.addAll(this);
  result
}
```

Casts, operations on maps, and other similar operations still bubble.
