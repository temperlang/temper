# Connecteds from Hex

    export let name = "connecteds-hex";

Like `connecteds`, this checks that a library's connected code can use a
dependency its config declares. It is separate because Hex has no roaring
bitmap for Elixir to match the other backends' test with.

    export let elixir = {
      class: ElixirConfig,
      dependencies: [
        "decimal ~> 3.1",
      ],
    };
