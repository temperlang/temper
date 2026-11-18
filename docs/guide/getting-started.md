# Getting started

Temper is for writing libraries that can be used from anywhere, so let's start
by writing a very simple library.

> [!IMPORTANT]
> Temper hasn't yet reached 1.0, and may have backward incompatible
> changes before then.

> **Note:** `temper watch` and `temper doc` aren't available yet
> ([the command line](../tools/cli.md);
> [inconsistencies](../inconsistencies.md#i9-the-command-line)).

## Getting started overview

- [Set up Temper tools and VS Code support](#set-up-temper-tools-and-vs-code-support)
- [Write a simple library in Temper](#write-a-simple-library-in-temper)
- [Compile the library to C\#, Java, JavaScript (JS), Lua, Python, & Rust](#compile-the-library-to-c-java-js-lua-python--rust)
- [Use the library from C\#](#use-the-library-from-c)
- [Use the library from Java](#use-the-library-from-java)
- [Use the library from JS](#use-the-library-from-js)
- [Use the library from Lua](#use-the-library-from-lua)
- [Use the library from Python](#use-the-library-from-python)
- [Use the library from Rust](#use-the-library-from-rust)

Detailed instructions follow.

## Set up Temper tools and VS Code support

To set up Temper's command-line tool, do the following:

1. Download the compiler for your system from the
   [latest release][LatestRelease]: `temper-VERSION-TARGET.tar.gz`
   (`.zip` on Windows), for Linux (x64 and Arm64), macOS (Apple
   silicon), and Windows (x64 and Arm64). The *riskier*
   [nightly][Nightly] prerelease has the newest `main`, as
   `temper-TARGET.tar.gz`. `SHA256SUMS` beside them checks a download.
2. Extract it somewhere convenient for you. It's one binary,
   `temper`, with Temper's standard library built in.
3. Add that directory to your system path.

Or build it from source, with [Rust](https://rustup.rs/) 1.85 or later,
in a clone of this repository: `cargo build --release -p tempest` writes
`target/release/temper`.

You can try out the install with the following commands:

```
temper version
temper help
```

For VS Code, the same release has the Temper extension, a `.vsix` file
per platform, such as `temper-linux-x64-VERSION.vsix`, each with the
compiler inside. [Install it from the VSIX][VsixInstall]:
`code --install-extension temper-linux-x64-VERSION.vsix`, or the
Extensions view's `...` menu, "Install from VSIX...". It isn't in the
VS Code Marketplace yet ([editors](../tools/editors.md)).

To build and test for each language, you need that language's own
tools: `dotnet` for C#, a JDK and Maven for Java, Node and npm for JS,
Lua, Python, and Cargo for Rust (and CMake and a C++ compiler for C++,
with `-b cpp`).

For best results on Windows, we *strongly* recommend the use of Windows
Terminal because of its Unicode support, among other matters.

## Write a simple library in Temper

To start a library, do the following:

1. Create an empty directory somewhere called "hello".
2. In that directory, run: `temper init`

This creates the following files under the "src/" directory:

- config.temper.md - A library configuration file.
- hello.temper.md - A file to start writing your library code.

The file "src/hello.temper.md" looks like this:

````tempermd
# Implementation for Hello

Library discussion goes here.

    // Library implementation code goes here.

Additional documentation and code blocks may follow.
````

Temper supports [literate programming][LiterateProgramming], where your Temper
source is embedded in Markdown. It goes inside `temper` code blocks, where the
default language is `temper`, so you can leave that off or even just use
indented code blocks, as in the example above. Any markdown outside such blocks
is a comment section ignored by the Temper compiler. If you prefer, you can
also use plain `.temper` files.

For now, replace this comment:

```temper
    // Library implementation code goes here.
```

With this code (still indented):

```temper
    export let greetingFor(name: String): String {
      // The following expression is the return value,
      // if we leave off the trailing semicolon.
      "Hello, ${name}!"
    }
```

Congratulations! You've now written your first library in Temper.

## Compile the library to C\#, Java, JS, Lua, Python, & Rust

Now that we have a library, we can build it:

```sh
temper build
```

This command creates several output files, including the following:

```
# C#
./temper.out/csharp/hello/src/Hello.csproj
./temper.out/csharp/hello/src/HelloGlobal.cs

# Java
./temper.out/java/hello/pom.xml
./temper.out/java/hello/src/main/java/hello/HelloGlobal.java

# JS
./temper.out/js/hello/package.json
./temper.out/js/hello/hello.js

# Lua
./temper.out/lua/hello/hello-dev-1.rockspec
./temper.out/lua/hello/init.lua

# Python
./temper.out/py/hello/pyproject.toml
./temper.out/py/hello/hello/hello.py

# Rust
./temper.out/rust/hello/Cargo.toml
./temper.out/rust/hello/src/mod.rs
```

> **Note:** this list now shows what `temper` writes; Temper's older
> docs showed `src/Hello/HelloGlobal.cs`, `src/main/java/hello/hello/`,
> and `hello.lua` ([inconsistencies](../inconsistencies.md#i6-the-temperout-layout)).

> [!WARNING]
> `temper build` overwrites these generated files each time it runs.
> Don't change them by hand.

Default backends are C#, Java 17, JS, Lua 5.1, Python, and Rust. These are identified
by backend names "csharp", "java", "js", "lua", "py", and "rust".

Each language's package depends on Temper's runtime for that language,
`temper-core`, at exactly this compiler's version, and on Temper's
`std` if the library imports it. `temper build` writes them beside the
library, as `temper.out/LANGUAGE/temper-core` and `std`. They aren't
on package registries yet, so the steps below use them from there.

We plan tools to ease publishing these libraries (see [issue#17](https://github.com/temperlang/temper/issues/17)). You
can also publish them manually using your own credentials to whatever
repositories you choose. But for now, let's take a look at using the
*hello* library from other languages on our local machine.

## Use the library from C\#

Temper automatically generates project files for building and testing projects
with the `dotnet` command-line tool.

To consume a Temper-built C\# project in
.NET, create a project called "greet-csharp" using `dotnet new console`. Then add a reference to the hello library:

```sh
# In the separate dotnet project, add correct path to Temper-built C#.
dotnet add reference ../hello/temper.out/csharp/hello/src/Hello.csproj
```

Then modify your "Program.cs" to say this:

```cs
using Hello;

Console.WriteLine(HelloGlobal.GreetingFor("world"));
```

Then run your new program:

```sh
$ dotnet run
Hello, world!
```

Congratulations! You've used a Temper-built library in C\#.

## Use the library from Java

A variety of package management and build tools exist for Java. Here, we use
Gradle. Also, the "java" backend for Temper requires Java 17+. (Earlier
versions' "java8" backend, for Java 8, isn't available yet.)

To consume a Temper-built Java library using Gradle, create a Gradle project
called "greet-java" using `gradle init --type java-application`. Choose default
options or adjust naming as you see fit. Then in `greet-java/app/build.gradle`,
add the following to `repositories`:

```groovy
    mavenLocal()
```

And the following to `dependencies`:

```groovy
    // Default Maven IDs come from the Temper library name.
    implementation 'hello:hello:0.0.1'
```

Also, run `mvn install` inside `hello/temper.out/java/temper-core`, and then
inside `hello/temper.out/java/hello`. The runtime's `pom.xml` compiles it for
Java 8 with Maven's [toolchains][MavenToolchains], so your
`~/.m2/toolchains.xml` needs a JDK 8. Then modify the Gradle-produced
`App.java` to use our Temper-built library:

```java
// Chose name `greet` for the package using `gradle init`.
package greet;

import hello.HelloGlobal;

public class App {
    public static void main(String[] args) {
        // Each package "Global" class provides top levels from Temper.
        System.out.println(HelloGlobal.greetingFor("world"));
    }
}
```

Now run the app:

```
$ ./gradlew run

> Task :app:run
Hello, world!

BUILD SUCCESSFUL in 1s
2 actionable tasks: 2 executed
```

Congratulations! You've used a Temper-built library in Java.

## Use the library from JS

A variety of package management tools exist for JS. Here, we use Node and NPM.
Within this ecosystem, for access to ECMAScript modules support, Temper requires
a minimum Node version of 14.

> **Note:** a 0.x release note requires Node 18 or later
> ([inconsistencies](../inconsistencies.md#i5-node-and-javascript-versions)).

To consume a Temper-built JS library using Node, create a Node project
called "greet-js" using `npm init`, and [configure
`"type": "module"`][TypeModule].

Then run:

```sh
# In the separate Node project, use correct paths to Temper-built js,
# with ../hello/temper.out/js/std too if the library imports std.
npm install --install-links ../hello/temper.out/js/temper-core ../hello/temper.out/js/hello/
```

`--install-links` copies the packages in, so the library finds the
runtime beside it; run it again after each `temper build`.

And in your Node project, create file "greet.js" with the following content:

```ts
import { greetingFor } from "hello";

console.log(greetingFor("world"));
```

Then run your new program:

```
$ node greet.js
Hello, world!
```

Congratulations! You've used a Temper-built library in JS.

## Use the library from Lua

Temper doesn't yet integrate with any Lua package management system, so just
create a directory manually, such as "greet-lua". Then put this content in a
file in there called "greet.lua":

```lua
local hello = require("hello")

print(hello.greetingFor("world"))
```

You can also set the `LUA_PATH` directly, as appropriate to your directories
and shell, such as in the following examples:

```bash
# bash
export LUA_PATH=../hello/temper.out/lua/?.lua;../hello/temper.out/lua/?/init.lua
```

```bat
:: cmd
set LUA_PATH=../hello/temper.out/lua/?.lua;../hello/temper.out/lua/?/init.lua
```

```ps1
# powershell
$env:LUA_PATH = "../hello/temper.out/lua/?.lua;../hello/temper.out/lua/?/init.lua"
```

Then you can run it like this:

```
$ lua greet.lua
Hello, world!
```

Congratulations! You've used a Temper-built library in Lua.

## Use the library from Python

A variety of package management tools exist for Python. Here, we use a
virtual environment and pip. Python libraries generated in Temper require a
minimum of Python 3.11.

Create a directory called "greet-py", and in it, a virtual environment with
the runtime and the library installed:

```sh
python3 -m venv .venv
# In the separate project, use the correct paths to Temper-generated py,
# with ../hello/temper.out/py/std too if the library imports std.
.venv/bin/pip install ../hello/temper.out/py/temper-core ../hello/temper.out/py/hello/
```

(On Windows, it's `.venv\Scripts\pip`.) Then create file "greet.py" with the
following content:

```py
from hello import greeting_for

# Also note the change to snake_case for Python.
print(greeting_for("world"))
```

Then run your new program:

```
$ .venv/bin/python greet.py
Hello, world!
```

Congratulations! You've used a Temper-built library in Python.

## Use the library from Rust

Temper automatically generates project files for building and testing projects
with the `cargo` command-line tool for Rust. Temper requires a minimum of Rust
1.71.1.

To consume a Temper-built Rust library, first create a project
called "greet-rust" using `cargo init`. Then run:

```sh
# In the separate Cargo project, add correct path to Temper-built Rust.
cargo add hello --path ../hello/temper.out/rust/hello
```

And replace your "main.rs" content with the following:

```rs
use hello::greeting_for;

fn main() {
    // Also note the change to snake_case for Rust.
    println!("{}", greeting_for("world"));
}
```

Then run your new program:

```
$ cargo run
... skipping build progress output here ...
Hello, world!
```

Congratulations! You've used a Temper-built library in Rust.

## Links

- **NEXT:** [Classes and functions](tutorial/01-classes-and-functions.md)
- Reference: [Why yet another programming language](why.md)

[LatestRelease]: https://github.com/temperlang/tempest/releases/latest
[LiterateProgramming]: https://en.wikipedia.org/wiki/Literate_programming
[Nightly]: https://github.com/temperlang/tempest/releases/tag/nightly
[MavenToolchains]: https://maven.apache.org/guides/mini/guide-using-toolchains.html
[TypeModule]: https://nodejs.org/api/packages.html#type
[VsixInstall]: https://code.visualstudio.com/docs/editor/extension-marketplace#_install-from-a-vsix
