---
title: Getting Started
temper-extract: false
---

# Getting started

Temper is for writing libraries that can be used from anywhere, so let's start
by writing a very simple library.

**NOTICE:** Temper hasn't yet reached 1.0 and may have backward incompatible
changes before that point.


## Getting started overview

- [Set up Temper tools and VS Code support](#setup)
- [Write a simple library in Temper](#write)
- [Compile the library to C\#, Java, JavaScript (JS), Lua, Python, & Rust](#build)
- [Use the library from C\#](#use-csharp)
- [Use the library from Java](#use-java)
- [Use the library from JS](#use-js)
- [Use the library from Lua](#use-lua)
- [Use the library from Python](#use-py)
- [Use the library from Rust](#use-rust)

Detailed instructions follow.


<div id="setup"></div>

## Set up Temper tools and VS Code support

To set up Temper command-line tools, do the following:

1. [Download the latest self-contained binary tarball][LatestRelease] for your
  system. Until official builds are available, you can use a *riskier*
  automated [dev release][DevRelease].
2. Extract it somewhere convenient for you.
3. Add it to your system path.

You can try out the install with the following commands:

```
temper version
temper help
```

[To install in VS Code][VsixInstall], you can also download a `.vsix` file for
your platform from the release link. These files bundle Temper internally.
In the future, we also intend to publish the extension in the VS Code
Marketplace (see [issue#15]).

A couple more notes:

- If you don't want to install backend languages, we also provide a
  [helper tool][TemperDocker] that automatically runs Temper in a Docker
  container with all supported backends already installed.
- For best results on Windows, we *strongly* recommend the use of Windows
  Terminal because of its Unicode support, among other matters.


<div id="write"></div>

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


<div id="build"></div>

## Compile the library to C\#, Java, JS, Lua, Python, & Rust

Now that we have a library, we can build it:

```sh
temper build
```

This command creates several output files, including the following:

```
# C#
./temper.out/csharp/hello/src/Hello.csproj
./temper.out/csharp/hello/src/Hello/HelloGlobal.cs

# Java
./temper.out/java/hello/pom.xml
./temper.out/java/hello/src/main/java/hello/hello/HelloGlobal.java

# JS
./temper.out/js/hello/package.json
./temper.out/js/hello/hello.js

# Lua
./temper.out/lua/hello/hello-dev-1.rockspec
./temper.out/lua/hello/hello.lua

# Python
./temper.out/py/hello/pyproject.toml
./temper.out/py/hello/hello/hello.py

# Rust
./temper.out/rust/hello/Cargo.toml
./temper.out/rust/hello/src/mod.rs
```

**WARNING:** These generated files will be overwritten on future runs of `temper
build`. Don't change them manually.

⎀ default-backends

We plan tools to ease publishing these libraries (see [issue#17]). You
can also publish them manually using your own credentials to whatever
repositories you choose. But for now, let's take a look at using the
*hello* library from other languages on our local machine.


<div id="use-csharp"></div>

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


<div id="use-java"></div>

## Use the library from Java

A variety of package management and build tools exist for Java. Here, we use
Gradle. Also, the "java" backend for Temper requires Java 17+. (You can also use
Temper backend "java8" for Java 8 support.)

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

Also, inside `hello/temper.out/java/hello`, run `mvn install`. Then modify the
Gradle-produced `App.java` to use our Temper-built library:

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


<div id="use-js"></div>

## Use the library from JS

A variety of package management tools exist for JS. Here, we use Node and NPM.
Within this ecosystem, for access to ECMAScript modules support, Temper requires
a minimum Node version of 14.

We first need to ensure the generated library has its own dependencies:

```sh
# Under hello/temper.out/js/hello/, install dependencies.
npm install
```

Now, to consume a Temper-built JS library using Node, create a Node project
called "greet-js" using `npm init`, and [configure
`"type": "module"`][TypeModule].

Then run:

```sh
# In the separate Node project, use correct path to Temper-built js.
npm install ../hello/temper.out/js/hello/
```

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


<div id="use-lua"></div>

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


<div id="use-py"></div>

## Use the library from Python

A variety of package management tools exist for Python. Here, we use Poetry.
Python libraries generated in Temper require a minimum of Python 3.11.

To consume a Temper-built Python library using Poetry, first create a project
called "greet-py" using `poetry init`. Then run:

```sh
# In the separate Poetry project, use the correct path to Temper-generated py.
poetry add --editable ../hello/temper.out/py/hello/
```

And in your Poetry project, create file "greet.py" with the following content:

```py
from hello import greeting_for

# Also note the change to snake_case for Python.
print(greeting_for("world"))
```

Then run your new program:

```
$ poetry run python greet.py
Hello, world!
```

Congratulations! You've used a Temper-built library in Python.


<div id="use-rust"></div>

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

Temper-built Rust currently still causes a number of compiler warnings, but you
can silence them from your dependencies, such as in the following examples:

```bash
# bash
export RUSTFLAGS=--cap-lints=allow
```

```bat
:: cmd
set RUSTFLAGS=--cap-lints=allow
```

```ps1
# powershell
$env:RUSTFLAGS = "--cap-lints=allow"
```

Then run your new program:

```
$ cargo run
... skipping build progress output here ...
Hello, world!
```

Congratulations! You've used a Temper-built library in Rust.


## Links

- **NEXT:** [Classes and functions](01-classfun.md)
- Reference: [Why yet another programming language](../why.md)


[DevRelease]: https://github.com/temperlang/temper/releases/tag/dev
[LatestRelease]: https://github.com/temperlang/temper/releases/latest
[LiterateProgramming]: https://en.wikipedia.org/wiki/Literate_programming
[TemperDocker]: https://github.com/temperlang/temper-docker/
[TypeModule]: https://nodejs.org/api/packages.html#type
[VsCodeExtension]: https://marketplace.visualstudio.com/items?itemName=temper.temper
[VsixInstall]: https://code.visualstudio.com/docs/editor/extension-marketplace#_install-from-a-vsix
