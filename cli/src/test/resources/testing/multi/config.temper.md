# Multiple modules in test

We're getting confused imports between modules in some cases, and this tries to
replicate that.

    export let name = "test-me";
    export let authors = "Temper Contributors";

Testing various bonus imports.

    // import("./lib");
    // import("./util");
    import("./multi");
    import("./test");
