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

Each specific backend also injects its own std configuration.

## Rust

We use the name below for [Cargo/crates.io][std-on-crates].

    export let rustName = "temper-std";

[std-on-crates]: https://crates.io/crates/temper-std
