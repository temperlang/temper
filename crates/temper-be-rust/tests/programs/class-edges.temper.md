# Class edges

What the shared `classes` program leaves out, for Rust: an interface
below another, and upcasts between them; a default method that returns
`this` (`clone_boxed`); an exported class's `toString`, `@operator("==")`,
and `var` property, which Rust's `Display`, `PartialEq`, and getters and
setters show; and a constructor that reads fields it has set, then
changes one and passes `this` on after it makes the instance. Then a
generic class with a bound and optional constructor parameters, one of
its type parameter's type, made with and without them, whose builder
takes its type parameters. And a `var` that host threads update at
once, through `this` and through a parameter, each update read and
written under one lock, so none is lost.

    export interface Named {
      public get name(): String;
      public greet(other: String): String { "${other}, I am ${name}" }
      public self(): Named { this }
    }

    export interface Titled extends Named {
      public get title(): String;
    }

    export class Person(public var name: String, public title: String) extends Titled {
      public toString(): String { "${title} ${name}" }
      @operator("==")
      public eq(other: Person): Boolean { name == other.name }
    }

    export class Box {
      public var count: Int = 1;
      public twice: Int = count * 2;
      public constructor() {
        count = count + twice;
        log(this);
      }
    }

    let log(b: Box): Void { console.log("box ${b.count}"); }

    let p = new Person("Ann", "Dr");
    console.log(p.toString());
    console.log(p.greet("Bob"));
    let t: Titled = p;
    let pick(x: Titled): Named { x }
    let n = pick(t);
    console.log(n.greet("Cy"));
    console.log(n.self().name);
    p.name = "Bea";
    console.log("${n.name} ${t.title}");
    console.log("${p == new Person("Bea", "X")} ${p is Named} ${n is Person}");
    let b = new Box();
    console.log("${b.count} ${b.twice}");
    let q = n as Person;
    console.log(q.title);

    export class Duo<T extends Named>(
      public first: T,
      public second: T? = null,
      public joiner: String = " & ",
    ) {
      public names(): String {
        let s = second;
        if (s == null) { first.name } else { "${first.name}${joiner}${s.name}" }
      }
    }

    console.log(new Duo<Person>(p).names());
    console.log(new Duo(p, new Person("Cy", "Mr")).names());
    console.log(new Duo<Person>(p, q, " + ").names());

    export class Tally {
      public var count: Int = 0;
      public var other: Int = 0;
      public add(n: Int): Void { count += n; }
      public bump(): Void { count = count + 1; }
      // Under the lock too: only its field, locals, and operators.
      public clampAdd(n: Int): Void {
        count = if (count > 10 && n > 0) { count } else { count + n };
      }
      // Under the lock, `/` bubbling with no panic, so no poisoned lock.
      public split(n: Int): Void throws Bubble { count = count / n; }
      // Not under the lock: another object's field, which may be this
      // one's (no deadlock), another field, a call, and this one's field
      // set on another object.
      public copyFrom(t: Tally): Void { count = t.count + 1; }
      public fromOther(): Void { count = other + count; }
      public plusTwice(): Void { count = count + twice(count); }
      public copyInto(t: Tally): Void { t.count = count + 1; }
    }

    let twice(n: Int): Int { n * 2 }

    export let bumpTwice(tally: Tally): Void { tally.count = 2 + tally.count; }

```log
Dr Ann
Bob, I am Ann
Cy, I am Ann
Ann
Bea Dr
true true true
box 3
3 2
Dr
Bea
Bea & Cy
Bea + Bea
```
