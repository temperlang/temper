# Connecteds

Test extra things about user-space connecteds here. (From Kotlin Temper's
CLI tests, `testing/connecteds`.)

    export let name = "connecteds";

## Dependencies

In particular, verify that we can pull in and use dependencies.

    export let csharp: CSharpConfig = {
      dependencies: [
        "Roaring.Net:1.4.1",
      ],
    };

    export let java: JavaConfig = {
      dependencies: [
        "org.roaringbitmap:RoaringBitmap:1.6.20",
      ],
    };

    export let js: JsConfig = {
      dependencies: [
        "roaring-wasm@1.1.0",
      ],
    };

    export let py: PyConfig = {
      dependencies: [
        "pyroaring==1.1.0",
      ],
    };

    export let rust: RustConfig = {
      dependencies: [
        // Old roaring for old rust.
        "roaring@0.11.1",
      ],
    };
