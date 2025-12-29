---
title: Memory Management
temper-extract: false
---

# Memory Management

Temper is designed for creating libraries in many other languages, meeting them
where they are. Some languages have garbage collection (GC), and some don't. And
for languages without it, implementing GC adds too much runtime weight for a
library. So Temper needs a strategy to support these differences. Temper also
prioritizes safe and reliable semantics.


## Reference counting

Different strategies could address differences in backend garbage collection.
Temper goes the route of using reference counting (RC) where GC isn't available.
It's easy to employ with predictable semantics and memory safety. But one
well-known weakness of straightforward RC is the inability to clean up reference
cycles. More expensive cyclical collection can be employed, but Temper avoids
reliance on this. Another way to break cycles is with weak references, but some
backends might not support these.

Because of these constraints, Temper is designed to prevent reference cycles
entirely. Features preventing cycles aren't yet implemented, but for now, you
should avoid designing data schemas that depend on them, because they will
cause compiler errors in the future.

See [issue#5] and [issue#7] for tracking issues on preventing reference
cycles in Temper.


## Designing software to avoid reference cycles

Here are some quick examples on ways to avoid reference cycles.

### Avoid parent references

This code is incorrect Temper:

```temper
// Bad Temper code! Will be prohibitied by the compiler in future releases!
class Node<T>(
  public value: T,
) {
  // This is clearly designed for building reference cycles.
  public var parent: Node<T>? = null;
  // And we don't plan for cycles with kids, but Temper avoids explicit
  // lifetimes, so there's no way to guarantee against cycles here, either.
  public get kids(): Listed<Node<T>> { kidsBuilder }
  private kidsBuilder: ListBuilder<Node<T>> = new ListBuilder<Node<T>>();
  public addKid(kid: Node<T>): Void {
    kid.parent = this;
    kidsBuilder.add(kid);
  }
}
// Yep, this builds them.
let parent = new Node("parent");
parent.addKid(new Node("kid"));
// Viewing `parent` in the repl today causes stack overflow.
```

To make this work in Temper, we have to lose the `parent` and also make the kids
list immutable:

```temper
// Now avoiding cycles!
class Node<T>(
  // If mutable var, T can't be Node<T>.
  // We'll be able to enforce this in a future release of Temper.
  public var value: T,
  // Immutable list now, where no kid is allowed to be `this`.
  // Again, we'll be able to enforce this in the future.
  public kids: List<Node<T>> = [],
) {}
// Still no cycles. This might better be immutable by now, but eh.
let parent = new Node("parent", [new Node("kid")]);
```

But then you can't arbitrarily navigate up the tree from any given node. What if
you want to access ancestor nodes for some reason? If you need that information
during traversal, you can just pass it down as you go:

```temper
let walk<T>(
  node: Node<T>,
  handler: fn (Node<T>, Node<T>?): Void,
  // Optional parameters must follow required ones.
  parent: Node<T>? = null,
): Void {
  handler(node, parent);
  // And yes, we intend to provide "for each" loops in some fashion.
  for (var k = 0; k < node.kids.length; k += 1) {
    walk(node.kids[k], handler, node);
  }
}
```

Then that function can be used like this, getting a parent at the same time as a
given node:

```temper
$ walk(parent) { node: Node<String>, parent: Node<String>? =>
    let extra = " under ${(parent as Node<String>).value}" orelse "";
    console.log("${node.value}${extra}");
  }
parent
kid under parent
interactive#19: void
```

A list of all ancestors could also be passed in, or other recursive algorithms
could be designed.

### Use array lists of nodes

If you really need either cyclical data or mutable recursive data, one option is
to make a `List` of nodes then represent references using indices in the list
(and see [issue#31] for potential index types):

```temper
// We've discussed the idea of specialized typed index references, but there
// aren't any definitive plans yet. Meanwhile, just use a simple typedef.
let NodeRef = Int;

class Node<T>(
  public value: T,
  // Alternatively, put the full adjacency matrix in the graph itself.
  public neighbors: List<NodeRef>,
) {}

class Graph<T>(
  public nodes: List<Node<T>>,
) {
  // Here's where a unified adjacency matrix would go, if you prefer that.

  // Here's an example higher level convenience method.
  public mapNeighbors<O>(
    node: Node<T>, transform: fn (Node<T>): O
  ): List<O> throws Bubble {
    let outputs = new ListBuilder<O>();
    for (var n = 0; n < node.neighbors.length; n += 1) {
      // Accessing the `nodes` list could fail if bad `NodeRef` indices.
      outputs.add(transform(nodes[node.neighbors[n]]));
    }
    outputs.toList()
    // Alternative implementation, but `map` currently doesn't propagate
    // Bubble. We want Bubble polymorphism for that.
    // node.neighbors.map { (n): O => transform(nodes[node.neighbors[n]]) }
  }
}
```

Given those definitions, we can make and use some data:

```temper
$ let graph = {
    nodes: [
        // Other abstractions or specialized index types might make indices
        // easier to get right. Or just building these up from algorithms.
        // Meanwhile, note that "a" connects to all nodes, including itself.
        { value: "a", neighbors: [0, 1, 2] },
        { value: "b", neighbors: [2] },
        { value: "c", neighbors: [0, 1] },
      ]
  };
interactive#25: void
$ graph.mapNeighbors(graph.nodes[0]) { (node): String =>
    // We could traverse the graph deeper here if we want.
    node.value
  }
interactive#26: ["a","b","c"]
```

Other resources may exist on developing data structures and algorithms without
reference cycles, but this gives a taste of how you can work through things.


## Borrowed references

Some languages have formal or informal notions of temporarily borrowing a
reference. Requiring reference counting in these cases is heavy weight. We
intend to support some mechanism to express simple borrows and check them at
compile time.

Once we work out the details, we'll add those here along with code examples.


## Other kinds of resources

Memory is by no means the only kind of resource that software needs to manage.
Files, network connections, locks, and more might need closed or released.
However, Temper has no inherent ability to access any of these resources. They
might be passed in as capabilities (such as by interface implementations) from
backend software, however. So it's possible that Temper in the future will
support features to help close resources in an organized fashion (see
[issue#24]).

Meanwhile, now that we know how Temper manages memory, let's get to that regular
expression library.


## Links
- **NEXT**: [Regular Expressions](07-regex.md)
- **PREVIOUS**: [Builtin Types Overview](05-builtin.md)
- Reference: [Builtins](../reference/builtins.md),
  [Types](../reference/types.md)
