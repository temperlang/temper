Here we have a second library with source located inside another library. They
are officially independent despite the path relationship that still matters for
relative imports.

    export let name = "banana";
    import(".");

And configure the C\# root namespace with a dot in it, because we had a bug in
that.

    export let csharpRootNamespace = "Qualified.Banana"
