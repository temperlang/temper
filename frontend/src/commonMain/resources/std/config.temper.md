# Temper Standard Library

This library holds code that we distribute with Temper but which can be broken
out from core builtins and also implemented primarily in the Temper language.

    export let name = "std";
    export let version = "0.6.0";

## Metadata

    export let authors = "Temper Contributors";
    export let description = "Optional support library provided with Temper";
    export let homepage = "https://temperlang.dev/";
    export let license = "Apache-2.0 OR MIT";
    export let repository = "https://github.com/temperlang/temper";

## Imports

We might break these out into separate libraries in the future.

    import("./regex");
    import("./testing");
    import("./temporal");
    import("./json");
    import("./net");

## C#

For [NuGet][std-on-nuget], we use TemperLang as a prefix for Temper internal
libraries.

    export let csharpRootNamespace = "TemperLang.Std";

## Java

We've chosen to use `temper` as a package prefix for official Temper packages,
but to conform to Maven standards, we use a reverse owned domain name for the
group ID on [Maven][std-on-maven].

    export let javaGroup = "dev.temperlang";
    export let javaArtifact = "temper-std";
    export let javaPackage = "temper.std";

And as long as the `testing` module is part of `std`, we need junit as a core
dependency of `std`, so its internals can use it. For now, usage is done via
specialized connected methods.

    export let javaDependencies = "org.junit.jupiter:junit-jupiter:5.9.2";

## JS

We use the name below on [npm][std-on-npm].

    export let jsName = "@temperlang/std";

## Python

We use the name below on [pypi][std-on-pypi].

    export let pyName = "temper-std";

## Rust

We use the name below for Cargo/crates.io.

    export let rustName = "temper-std";


[std-on-pypi]: https://pypi.org/project/temper-std/
[std-on-maven]: https://central.sonatype.com/artifact/dev.temperlang/temper-std/
[std-on-npm]: https://www.npmjs.com/package/@temperlang/std
[std-on-nuget]: https://www.nuget.org/packages/TemperLang.Std/
