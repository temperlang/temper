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
