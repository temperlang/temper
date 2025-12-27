---
title: Quick Tour
temper-extract: false
---

<img src="../images/temper-logo-text-auto.svg" style="width:40rem" alt="[Logo: flower petals fanned out] temper&trade;"/>

# Quick Tour of Temper

Temper is a programming language and toolchain for making *libraries* that can
be used natively from any other language.

Temper compiles to idiomatic types and functions for each target
language: currently Python, JavaScript, C#, Java, and Lua, with plans
for many more. Whether you're an open-source developer or work in an
enterprise, Temper can multiply the reach and effect of your
work. ["Why a new programming language?"](why.md) explains what Temper
lets you do that existing languages don't.

[Get started](tutorial/index.md), or see examples below for a quick tour.


## Examples

### Translate

Temper lets you define rich data types and logic that translate to all supported
backend languages. Usage is idiomatic in each language.

```temper
export class Person(
  public birthDate: Date,
) {
  public ageAt(date: Date): Int {
    // Or support other definitions of age.
    date.yearsSince(birthDate)
  }
}
```

Use from:

=== "C\#"

    ```cs
    using Snippets.Person;

    var person = new Person(new Date(1987, 6, 5));
    var age = person.AgeAt(new Date(2023, 2, 1));
    Console.WriteLine($"Age: {age}");
    ```

=== "Java"

    ```java
    import snippets.person.*;

    class Use {
        public static void main(String[] args) {
            var person = new Person(new Date(1987, 6, 5));
            var age = person.ageAt(new Date(2023, 2, 1));
            System.out.printf("Age: %s\n", age);
        }
    }
    ```

=== "JS / TS"

    ```js
    import { Date, Person } from "snippets/person.js";

    let person = new Person(new Date(1987, 6, 5));
    let age = person.ageAt(new Date(2023, 2, 1));
    console.log(`Age: ${age}`);
    ```

=== "Lua"

    ```lua
    local p = require("snippets.person")

    local person = p.Person(p.Date(1987, 6, 5));
    local age = person:ageAt(p.Date(2023, 2, 1));
    print("Age: " .. math.floor(age));
    ```

=== "Python"

    ```py
    from snippets.person import Date, Person

    person = Person(Date(1987, 6, 5))
    age = person.age_at(Date(2023, 2, 1))
    print(f"Age: {age}")
    ```

### Dispatch

Use interfaces for abstraction. And use familiar syntax.

```temper
export interface Shape {
  public perimeter(): Float64;
}

export class Rectangle(
  public width: Float64,
  public height: Float64,
) extends Shape {
  public perimeter(): Float64 { 2.0 * (width + height) }
}

let tau = 2.0 * Float64.pi;

export class Circle(
  public radius: Float64,
) extends Shape {
  public perimeter(): Float64 { tau * radius }
}
```

### When

Or match on types instead of using virtual dispatch.

```temper
export let area(shape: Shape): Float64 {
  when (shape) {
    is Rectangle -> shape.width * shape.height;
    is Circle -> do {
      let { radius } = shape;
      0.5 * tau * radius * radius
    }
    else -> NaN; // needed until exhaustiveness checks
  }
}
```

### Test

Test your code across languages. Temper `test` integrates with well-known frameworks such as
*[JUnit]*, *[Mocha]*, and *[Pytest]*. You can test your libraries in Temper to build confidence
in its correctness and also build confidence in each translation's fidelity.

```temper
test("age") {
  let person = new Person(new Date(1987, 6, 5));
  let age = person.ageAt(new Date(2023, 2, 1));
  // You can give a custom failure message callback.
  assert(age == 35) { "Bad age: ${age}" }
  // Or just use automatic messaging.
  // Reports "expected age == (36) not (35)"
  assert(age == 36);
}
```

### Document

Embed your Temper code inside of Markdown. Collaborate with others on
lightweight standards documents that include a reference implementation.
Translate the implementation to many programming languages.

```tempermd
## Circle

Tau (τ) is a full turn in radians, rather than the half turn of pi (π).

    let τ = 2.0 * Float64.pi;

    export class Circle(
      public radius: Float64,
    ) {

Using `τ` rather than `π` simplifies some calculations and complicates
others.

      public perimeter(): Float64 { τ * radius }
    }
```

### Develop

We've implemented an initial Temper [language server][LSP] and an extension for Visual Studio Code. Great tooling helps Temper users discover language features and iterate quickly, so we'll continue to prioritize it.

![Screenshot of VSCode with Temper code highlighted starting with red error squiggles under "Float" in `public radius: Float` and a hover box noting "No declaration for Float".  Elsewhere "Float64" appears without error squiggles.](images/editor.png)


## Next steps

Excited about Temper?

- [Get started](tutorial/index.md)
- [or read the reference documentation.](reference/index.md)

## Problems, Questions, Discussion?

- Questions, the [<img src="../images/logo-stackoverflow.svg" style="height: 2ex; width: auto; object-fit: contain; vertical-align: middle" alt="stackoverflow" /> *\[temper\]* tag](https://stackoverflow.com/questions/tagged/temper) is a great place to ask.
- Ideas, or just want to chat?  The [Temper Language Discord](https://discord.com/invite/6Jb83cq5FH) is a great place to share your thoughts on Temper or talk about what you've done with it.
- If you have a problem or feature request, check the [issue tracker](https://github.com/temperlang/temper/issues).

[JUnit]: https://junit.org
[Mocha]: https://mochajs.org
[Pytest]: https://pytest.org
[LSP]: https://microsoft.github.io/language-server-protocol/
