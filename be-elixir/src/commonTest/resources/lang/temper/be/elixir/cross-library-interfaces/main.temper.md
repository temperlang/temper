# Classes inheriting from other libraries' interfaces

Three libraries: `base` declares `Named` and `Box`, whose getters,
setter and methods have bodies; `mid` declares `Titled`, which extends
`Named`, adds a getter and overrides a method; this one implements both.
Nothing here redefines what the interfaces give, so every call below
reaches a body declared in another library.

    let { Named, Box } = import("base");
    let { Titled } = import("mid");

    class Direct(public name: String) extends Named {}
    class Chained(public name: String) extends Titled {}
    @imu class Frozen(public name: String) extends Titled {}
    class IntBox(public item: Int) extends Box<Int> {}

Getters, a method with an optional parameter left out and given, a
method, and a getter that calls a private method of the interface.

    let d = new Direct("ada");
    console.log("${d.shout} ${d.greet("bob")} ${d.greet("cy", "?")} ${d.tag()} ${d.nick}");
    d.nick = "al";

```log
ada! hello bob, I am ada. hello cy, I am ada? base ada~
cannot rename ada to al
```

Through `Titled` to `Named`, two libraries away. `mid`'s `tag` is
nearer than `base`'s, so it wins.

    let c = new Chained("eve");
    console.log("${c.shout} ${c.greet("bob")} ${c.title} ${c.tag()}");

```log
eve! hello bob, I am eve. Dr. eve mid
```

The same for an `@imu` class, which is a struct.

    let f = new Frozen("fay");
    console.log("${f.shout} ${f.title} ${f.tag()} ${f.nick}");

```log
fay! Dr. fay mid fay~
```

Through a value typed as the interface, which dispatches at run time.

    let n: Named = c;
    console.log("${n.shout} ${n.tag()} ${n.greet("dan")}");

```log
eve! mid hello dan, I am eve.
```

`is` and `as` know every ancestor, wherever it is declared.

    let anyD: AnyValue = d;
    let anyF: AnyValue = f;
    console.log("${(anyD is Named).toString()} ${(anyD is Titled).toString()} ${(anyF is Named).toString()} ${(anyF is Titled).toString()}");
    let t = (anyF as Titled) orelse panic();
    console.log(t.title);

```log
true false true true
Dr. fay
```

A generic interface, bound to `Int` here.

    console.log(new IntBox(3).both().join(",", fn (i: Int): String { i.toString() }));

```log
3,3
```

An interface of this library in the chain: `Loud` extends `Titled`, so
a `Shouter` has interfaces from all three libraries above it. `Loud`'s
own `shout` is nearer than `Named`'s and wins; the rest still comes from
`mid` and `base`.

    interface Loud extends Titled {
      public get shout(): String { "${name}!!!" }
    }
    class Shouter(public name: String) extends Loud {}
    let s = new Shouter("sal");
    console.log("${s.shout} ${s.title} ${s.tag()} ${s.greet("al")}");

```log
sal!!! Dr. sal mid hello al, I am sal.
```

An `@actor` class runs each inherited body inside the actor, as it would
one copied into it.

    @actor class Agent(public var name: String) extends Named {}
    let a = new Agent("ann");
    console.log("${a.shout} ${a.greet("bo")} ${a.nick}");
    a.name = "amy";
    let an: Named = a;
    console.log(an.shout);

```log
ann! hello bo, I am ann. ann~
amy!
```
