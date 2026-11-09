# Getters read from other members

A getter that only reads can be called from any other member, even when
its property has a setter. Some backends mark members that don't change
`this`, such as C++'s `const`, and a marked member can only call marked
ones.

    interface HasState {
      get state(): Int;
      set state(x: Int): Void;
    }

    class Holder extends HasState {
      private var current: Int = 0;
      public get state(): Int { current }
      public set state(x: Int): Void { current = x; }
      public describe(): String { "state ${state}" }
    }

    let holder = new Holder();
    holder.state = 2;
    console.log(holder.describe());
    let hasState: HasState = holder;
    hasState.state = 3;
    console.log(hasState.state.toString());

A getter or method that hands out `this` can't be marked, so neither can
members that call it.

    class Wrapper(public node: Node) {}

    class Node(public name: String) {
      public get me(): Node { this }
      public get myName(): String { me.name }
      public wrap(): Wrapper { new Wrapper(this) }
      public wrappedName(): String { wrap().node.name }
    }

    let node = new Node("n");
    console.log(node.myName);
    console.log(node.wrappedName());

```log
state 2
3
n
n
```
