---
title: Modules & Libraries
temper-extract: false
---

# Modules & Libraries

Now that we've learned about [classes, functions](01-classfun.md),
[interfaces](02-interface.md), and [control flow](03-controlflow.md),
let's get out of the REPL and get [back](index.md) to writing libraries!
That's what Temper is made for, after all.


## Starting a library

Let's continue our example of geometric shapes. A full-featured library for
shapes might be useful in a variety of graphical applications, although what
we're making here is just very simple. Anyway, let's create a new library:

1. Create an empty directory somewhere called "geometry."
2. In that directory, run `temper init`.

If needed, see the tutorial on [getting started](index.md) with Temper.

Now, in "src/geometry.temper.md", add a variation on our class and function
definitions from [earlier](01-classfun.md), replacing everything in the file
after the Markdown heading with the following code, being sure to replicate the
indentation of the code blocks.

```tempermd
Only exported definitions are visible outside the current file.

    export interface Shape {
      area(): Float64;
      perimeter(): Float64;
      toString(): String;
    }

    export class Rectangle(
      public width: Float64,
      public height: Float64,
    ) extends Shape {
      public area(): Float64 { width * height }
      public perimeter(): Float64 { 2.0 * (width + height) }
      public toString(): String {
        "Rectangle of size ${width} x ${height}"
      }
    }

We can also export simple values.

    export let tau = 2.0 * Float64.pi;

Exporting is optional. (And yeah, `pi` is needlessly indirect here, but it still
provides an example.)

    let pi = 0.5 * tau;

    export class Circle(
      public radius: Float64,
    ) extends Shape {
      public area(): Float64 { pi * radius * radius }
      public perimeter(): Float64 { tau * radius }
      public toString(): String { "Circle of radius ${radius}" }
    }

And we can also export top-level functions.

    export let squaredExtent(shape: Shape): Float64 {
      when (shape) {
        is Circle -> shape.radius * shape.radius;
        is Rectangle -> do {
          let { width, height } = shape;
          (width * width + height * height) / 4.0
        }
        // Because another type could be added later.
        else -> NaN;
      }
    }
```


## Unit tests

Let's also make a unit test to try this out. To make testing as easy as
possible, Temper lets you add tests directly in the same file as the code being
tested. For example, add the following code to "geometry.temper.md" (still
under the "src/" dir):

````tempermd
## Unit tests

Tests can be placed directly inside of library modules.

### Rectangle

Prove that we can create `Rectangle` instances and access their fields.

The `test` macro defines a unit test.

    // Makes this a test rather than production module!
    test("rectangle construction") {

If using the VS Code extension, mouse over `rectangle` for its type.
We expect to continue to improve the language server over time.

      let rectangle = { width: 1.5, height: 0.5 };

Equality on float math is iffy, but we set simple values here.

      assert(rectangle.width == 1.5); // Get default message on failure.
      assert(rectangle.height == 0.5) { "Custom failure message" }
    }
````

How can you add tests to the same module as the code being tested? When
compiling to each backend, Temper extracts the tests to separate files
automatically as needed. Temper also provides test-only access to module
internals for backends that need it, such as JS.

Now run the following line from your command prompt inside your project
directory:

```sh
# Here we run all tests using the py backend.
temper test -b py
```

It can take a while to run, but if you entered everything correctly, after it
finishes, you should see text such as the following:

```
Tests passed: 1 of 1
```

If it worked, try changing both assertions such that the expected height and
width are both wrong. Then test again, and you should see output such as the
following:

```
Test failed (py): rectangle construction - expected rectangle.width == (1.51) not (1.5), Custom failure message
Tests passed: 0 of 1
Test failed
```

Note that the default assertion failure for equality reports expected and actual
values. This works for types with both equality and `toString` defined. In some
cases, you might want custom messages also, which is available through the
optional message block.

Also note that *both* assertions ran despite failure. Temper uses soft
assertions in unit tests, meaning that the test continues even after a failed
assertion.

Once you've broken the test, fix it, and then let's take a look at reorganizing
our code some.


## Breaking a library into separate files

A library in Temper is made of one or more modules, and, by default, each module
lives in a separate source directory/folder. Except for library config,
individual file names don't matter. In the future, we also plan to support
parameterized modules such that different variations can be instantiated from
the same source, but that's not supported yet (see [issue#10](https://github.com/temperlang/temper/issues/10)).

Let's break out our single module into three files. Although not really needed,
let's put them in two separate modules, just for practice, where all of these
are under the "src/" dir:

- "extras/constants.temper.md": Move `tau` here, under submodule "extras".
- "shapes.temper.md": Rename "geometry.temper.py" to prove the name doesn't
  matter, leaving the unit test, type definitions, and `pi` here. Or you can
  split these out into separate files if you want. The result would be the same.
- "util.temper": Move function `squaredExtent` here, using file extension
  ".temper", to prove we can mix and match Temper Markdown and plain Temper
  files in a single module. But we'll need to reformat this content.

For example, "extras/constants.temper.md" will look something like follows:

````tempermd
# Geometric Constants

We could define `pi` here as well, but we're not exporting it, so just define it
in the separate module where it's used.

    export let tau = 2.0 * Float64.pi;
````

You'll also need to import dependencies from other modules as appropriate. For
example, prepend the following to "shapes.temper.md". Or you can add this import
to some separate "imports.temper" file if you prefer:

````tempermd
# Geometric Shapes

## Imports

    let { tau } = import("./extras"); // Specify dir only, not file names.

## Shape Interface

Add shape types below, and feel free to split into multiple code blocks. We're
still working out standards for how to structure Markdown for official code
documentation, so just organize in a way that seems reasonable.

...
````

And to be explicit, here is a possible formatting for the content of
"util.temper":

```temper
// And we can also export top-level functions.
export let squaredExtent(shape: Shape): Float64 {
  when (shape) {
    is Circle -> shape.radius * shape.radius;
    is Rectangle -> do {
      let { width, height } = shape;
      (width * width + height * height) / 4.0
    }
    // Only need `else` here until we check exhaustiveness.
    else -> NaN;
  }
}
```

In "config.temper.md", you can also explicitly `import("./extras");` to ensure
the submodule is included in the library. In this case, it's unneeded, because
the submodule is already imported in "shapes.temper.md". But in unusual cases,
you might have submodules that aren't used in other parts of the library. If you
do add explicit imports to "config.temper.md", you also need to `import(".");`
explicitly if you want to include the top-level module.

If you made all changes correctly, the following test run should still pass:

```sh
temper test -b py
```

Feel free to add more tests for additional library features.


## Library configuration

Temper currently supports a small set of library configuration options. Let's
add these to the config to try them out. These include the following:

- `version`: The version of your library. We recommend following
  [Semver](https://semver.org/).
- `license`: The license of your library. Where possible, we recommend using a
  [SPDX identifier](https://spdx.org/licenses/).
- `csharpRootNamespace`: The root namespace and assembly name of the generated
  C\# library. The default comes from your Temper library name. Further
  configuration may be provided in the future.
- `javaName`: The name of the generated Java library, such as the Maven
  `artifactId`. Again, the default is based on the Temper name of your library.
  You can also configure the root Java package with `javaPackage`.
- `jsName`: The name of the generated JS library. Again, the default comes from
  your Temper library name.
- `pyName`: The name of the generated Python library. Again, the default comes
  from your Temper library name.
- `rustName`: The name of the generated Rust package. Again, the default comes
  from your Temper library name.

You need to `export` these values for them to be used. After adding them, your
"config.temper.md" file might look something like this:

````tempermd
# Geometry

A library for geometric shapes and calculations.

## Core metadata

    export let name = "geometry";
    export let version = "0.1.0";

    // Just go with a common license for our geometry library here.
    export let license = "MIT";

## Included modules

    import(".");
    // Not needed: import("./extras");

## Backend configuration

Just examples here. You should choose available package names. Some additional
options are available on some backends, and we may add more options in the
future.

    export let csharpRootNamespace = "GeometryNet";
    export let javaName = "geometry-java";
    export let jsName = "geometry-js";
    export let pyName = "geometry-py";
    export let rustName = "geometry-rs";

Lua naming configuration isn't yet supported.

    // Has no effect yet.
    export let luaName = "geometry-lua";
````

We'll likely change some of the structure and introduce more configuration
options in future Temper releases.


## Library use

As for the [first getting started walkthrough](index.md), let's build our
library for C\#, Java, JS, Lua, Python, and Rust:

```sh
temper build
# Also try this out sometime: temper watch -t py
```

After building, you should be able to see the effects of library configuration
(library name, version, and license) in these files:

- temper.out/csharp/geometry/src/GeometryNet.csproj
- temper.out/java/geometry/pom.xml
- temper.out/js/geometry/package.json
- temper.out/lua/geometry/geometry-0.1.0-1.rockspec
- temper.out/py/geometry/pyproject.toml
- temper.out/rust/geometry/Cargo.toml

Now that we've built libraries, following the [steps from earlier](index.md),
try using the libraries from separate C\#, Java, JS, Lua, Python, and Rust
projects. One difference is that you'll need to import from the respective
modules. For example, you could try the following for each backend language:

=== "C\#"

    ```cs
    using GeometryNet;
    using static GeometryNet.GeometryNetGlobal;

    var shapes = new List<IShape> { new Circle(1.0), new Rectangle(1.5, 0.5) };
    Console.WriteLine(string.Join(", ", shapes.Select(SquaredExtent)));
    ```

=== "Java"

    ```java
    package usegeo;

    import geometry_java.Circle;
    import geometry_java.GeometryJavaGlobal;
    import geometry_java.Rectangle;
    import java.util.List;
    import java.util.stream.Collectors;

    class App {
        public static void main(String[] args) {
            var shapes = List.of(new Circle(1.0), new Rectangle(1.5, 0.5));
            System.out.println(
                shapes
                    .stream()
                    .map(GeometryJavaGlobal::squaredExtent)
                    .collect(Collectors.toList())
            );
        }
    }
    ```

=== "JS"

    ```js
    import { Circle, Rectangle, squaredExtent } from "geometry-js";

    const shapes = [new Circle(1.0), new Rectangle(1.5, 0.5)];
    console.log(shapes.map(squaredExtent));
    ```

=== "Lua"

    ```lua
    local geometry = require("geometry")

    local myShapes = {geometry.Circle(1.0), geometry.Rectangle(1.5, 0.5)}

    local values = {}
    for i, shape in ipairs(myShapes) do
        values[i] = geometry.squaredExtent(shape)
    end
    print(table.concat(values, ", "))
    ```

=== "Python"

    ```py
    from geometry_py import Circle, Rectangle, squared_extent

    shapes = [Circle(1.0), Rectangle(1.5, 0.5)]
    print([squared_extent(shape) for shape in shapes])
    ```

=== "Rust"

    ```rs
    use geometry_rs::{squared_extent, Circle, Rectangle, Shape};

    fn main() {
        let shapes = vec![
            Shape::new(Circle::new(1.0)),
            Shape::new(Rectangle::new(1.5, 0.5)),
        ];
        println!(
            "{:?}",
            shapes
                .iter()
                // Clone for reference counting.
                .map(|shape| squared_extent(shape.clone()))
                .collect::<Vec<_>>(),
        );
    }
    ```

Well, now we know a lot about building, testing, and using Temper libraries.
Let's learn more about Temper's own built-in library.


## Links
- **NEXT**: [Builtin Types Overview](05-builtin.md)
- **PREVIOUS**: [Control Flow](03-controlflow.md)
- Reference: [Builtins](../reference/builtins.md),
  [Types](../reference/types.md)
