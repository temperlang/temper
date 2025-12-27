We need a name, as always.

    export let name = "use-remote";

Now import a github url. This should only be done in config. From other Temper
files, import from the library name of interest, as defined in the remote source
code.

    import("https://github.com/temperlang/temper-regex-parser/commit/b1ba387762e8324d0b07996bca41a571a2a15ac8");

For bonus fun, import a commit id and subpath, although the current test doesn't
expect this one.

    // import("https://github.com/temperlang/prismora/tree/4a35aa7337854f64cc483375a0f65984fc8528ce/src");
