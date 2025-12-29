    export let name = "apple";

For now, import dirs manually. We plan to auto import the top level. Not sure
automating about lower levels.

    import(".");
    import("./avocado");
    import("./avocado/artichoke");
