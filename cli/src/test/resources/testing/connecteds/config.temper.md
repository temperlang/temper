# Connecteds

Test extra things about user-space connecteds here.

    export let name = "connecteds";

## Dependencies

In particular, verify that we can pull in and use dependencies.

    export let csharp = {
      class: CSharpConfig,
      dependencies: [
        "Roaring.Net:1.4.1",
      ],
    };

    export let java = {
      class: JavaConfig,
      dependencies: [
        "org.roaringbitmap:RoaringBitmap:1.6.20",
      ],
    };
