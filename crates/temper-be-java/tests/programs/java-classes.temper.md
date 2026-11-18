# Java classes

What the shared `classes` program leaves out of the Java backend's
classes: a static field whose value reads module state, so the module's
initializer assigns it; statics of a class's own that its initializer
gives, one reading another; an interface's static constant; a `Boolean`
property's `isLoud()`; an interface's abstract setter that a class
implements; and a default method inherited through two paths, which the
class forwards to.

    let greeting = "hi";

    export interface Named {
      public get name(): String;
      public get loud(): Boolean;
      public get volume(): Int;
      public set volume(v: Int): Void;
      public greet(times: Int): String { "${greeting} ${name} x${times.toString()}" }
      public static zero = 0;
    }

    export interface Polite extends Named {}

    export class Person(public name: String, public loud: Boolean) extends Polite & Named {
      public var level: Int = 0;
      public get volume(): Int { level }
      public set volume(v: Int): Void { level = v; }
      public static banner = "${greeting}!";
      public static count = 3;
      public static twice = Person.count * 2;
    }

    let p = new Person("Ann", true);
    p.volume = 7;
    console.log(p.greet(2));
    console.log("${p.loud} ${p.level.toString()} ${Person.banner} ${Person.twice.toString()} ${Named.zero.toString()}");
    let n: Named = p;
    console.log(n.greet(1));

```log
hi Ann x2
true 7 hi! 6 0
hi Ann x1
```
