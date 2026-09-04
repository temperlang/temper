# Connecteds

Test extra things about user-space connecteds here.

    export let name = "connecteds";

## Dependencies

In particular, verify that we can pull in and use dependencies.

    export let java = {
      class: JavaConfig,
      dependencies: [
        "org.roaringbitmap:RoaringBitmap:1.6.20",
      ],
    };
