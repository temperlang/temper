# Object Literals

## Basics

Temper has object literals that are syntactically similar to JS, such as
`{a: 1, b: "hi"}`. Unlike JS, these aren't for anonymous, structural types but
rather are implied constructor calls.

We infer the constructor by matching up property names.

Define local types, too. We'll import other types syntactically further down.

    class Person(
      public name: String,
      public age: Int,
    ) extends Stringable {
      public toString(): String {

Tangent on referencing members inside a lambda to check a problem we had in JS.
Yes, the code is silly but it tests the condition. We also had an issue later
with capturing `this` in Rust, so this test is useful.

        let ageText = [0].join("") { unused => age.toString() };
        "Person ${name} of age ${ageText}"
      }
    }

Include a type that has ambiguity on a property named `name`, so we can see
that including the `age` property of `Person` gets around this ambiguity.

    class Named(
      public name: String,
    ) extends Stringable {
      public toString(): String {
        "Named ${name}"
      }
    }

Now create some persons (local type) and some circles (imported type) and prove
that we can call the constructors explicitly and also implicitly through object
literal syntax.

    let person1 = new Person("Alice", 50);
    let person2 = { name: "Bob", age: 45 };
    let circle1 = new Circle(2);
    let circle2 = { radius: 1 };

    let things: List<Stringable> = [person1, person2, circle1, circle2];
    things.map { (thing): Int =>
      console.log("${thing}");
      0
    }

We expect each of the objects to be constructed and printed correctly.

```log
Person Alice of age 50
Person Bob of age 45
Circle of radius 2
Circle of radius 1
```

## Custom and overloaded constructors

Object literals correspond to constructor calls, not to classes and their named
properties. Unfortunately at present, even if we recognize a matching class, we
don't handle incompatible overloaded constructors well in other stages, so the
test for now sticks to single custom constructors rather than overloads.

TODO Update text in this functional test once we finalize plans.

    class Whatever {
      public blah: String;

TODO Test overloaded constructors or factories if/when we support them.

```temper inert
      public constructor(blah: String) {
        this.blah = blah;
      }
```

      public constructor(blech: Int) {
        blah = blech.toString();
      }
    }

```temper inert
    new Whatever("hello");
    { blah: "hi" };
```

    new Whatever(456);
    { blech: 321 };

Again try out imported types as well, since these are handled differently in
the internal processing. And use implied classes/constructors only here.

TODO Overloaded constructors or factories or whatever we move to.

    // { width: 7, height: 8 };
    // IMPORTANT! Don't ever reference `Rectangle` explicitly in this module,
    // so we can test that reorder works on just the implied constructor.
    { squareWidth: 5 };

## Nested scopes

Scope also matters for which constructors are visible, so make a nested scope
with a class in that.

    let bother(): Void {
      class Moreover(
        public blech: Int = 0,
        public bling: Int = 0,
      ) {
        public toString(): String { "${blech} ${bling}" }
      }

The next line would error because `blech` alone is ambiguous with Whatever.

```temper inert
      let whatAmI = { blech: 1 };
```

We need the explicit class for object literal syntax with only `blech`, but it
still provides named properties, which positional constructor args don't.

      let whatIAm = { class: Moreover, blech: 1 };

But with both `blech` and `bling`, it's distinct.

      let moreover = { blech: 2, bling: 3 };
      console.log("Moreover: ${moreover}");

We can use blech alone positionally because bling is optional, but we must be
explicit about the class again.

      let moreover2 = new Moreover(4);
      console.log("Moreover again: ${moreover2}");

Using `bling` alone also works.

      let moreover3 = { bling: 7 };
      console.log("Moreover yet again: ${moreover3}");

We also still have access to Whatever, but we must be explicit.

      let whatever = { class: Whatever, blech: 5 };
      console.log("Whatever: ${whatever.blah}");
    }
    bother();

But to repeat from earlier, out here we can make object literals for `Whatever`
with just `blech` because `Moreover` is invisible from this scope.

    // Assign to vars for fun hover in the editor.
    let whatever = { blech: 6 };
    console.log("Whatever again: ${whatever.blah}");

We print this time to prove the point that things worked.

```log
Moreover: 2 3
Moreover again: 4 0
Moreover yet again: 0 7
Whatever: 5
Whatever again: 6
```

## Field punning and object destructuring

Some of these features are unfinished, but taking apart and putting together
objects is nice with conveniences like destructuring and punning.

    let personAgain = { name: "Carrie", age: 40 };
    let { name, age as someAge is Int } = personAgain;
    console.log("name is ${name} and age is ${someAge}");
    let personMore = { name, age: 35 };
    console.log(personMore.toString());

```log
name is Carrie and age is 40
Person Carrie of age 35
```

## Imports

For test, let's import some types, because internally we handle these
differently than types defined in the current module.

    let { Circle, Rectangle, Stringable } = import("./extra-class");

Imports sometimes matter less, but for here, they're at the bottom just to
prove it works.
