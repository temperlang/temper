Make a name separate from the dir name.

    export let name = "a";

Import current dir and subdir from config so they get included in the library,
but library run/init should still only run top level.

And any explicit config import for now means that we also need to import the
top level explicitly if we want it.

    import(".");
    import("./sub");
