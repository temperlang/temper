# `std/json`: JSON

```temper
let { JsonTextProducer, parseJson } = import("std/json");
```

`std/json` converts between Temper values and JSON text
([ECMA-404](https://ecma-international.org/publications-and-standards/standards/ecma-404/)).
Most code only decorates its classes with `@json`, which gives a class
a static `jsonAdapter()` that encodes and decodes it:

```temper
let { JsonTextProducer, parseJson, NullInterchangeContext } = import("std/json");

@json
class Point(public x: Int, public y: Int) {}

let p = new JsonTextProducer();
Point.jsonAdapter().encodeToJson(new Point(1, 2), p);
let text = p.toJsonString() orelse panic();
console.log(text); //!outputs "{\"x\":1,\"y\":2}"

let tree = parseJson(text) orelse panic();
let back = Point.jsonAdapter().decodeFromJson(tree, NullInterchangeContext.instance) orelse panic();
console.log(back.y.toString()); //!outputs "2"
// ✅
```

## Writing JSON

A `JsonProducer` takes a stream of events, `startObject()`,
`objectKey(k)`, `stringValue(s)`, `endArray()`, and so on, and
`JsonTextProducer` turns them into text (`toJsonString()`). Adapters
write to any producer, so the same code can build a syntax tree
(`JsonSyntaxTreeProducer`) instead of text.

## Reading JSON

`parseJson(text)` gives a `JsonSyntaxTree`, or bubbles on malformed
input: a `JsonObject` (`propertyValueOrNull(key)`,
`propertyValueOrBubble(key)`), `JsonArray` (`elements`), `JsonString`,
`JsonBoolean`, `JsonNull`, or a number: `JsonInt32` (also `JsonInt`),
`JsonInt64`, `JsonFloat64`, or `JsonNumericToken` for numbers kept as
written. Numbers convert with `asInt32()`, `asInt64()`, and
`asFloat64()`. `parseJsonToProducer(text, producer)` streams the events
instead, and reports errors to the producer's `parseErrorReceiver`.

## Adapters

A `JsonAdapter<T>` has `encodeToJson(x, producer)` and
`decodeFromJson(tree, context)`. `Boolean`, `Int`, `Int64`, `Float64`,
`String`, and `List` have them too (`Int.jsonAdapter()`,
`List.jsonAdapter(Int.jsonAdapter())`), and `OrNullJsonAdapter` makes
one for a nullable type. An `InterchangeContext` lets an adapter look at
the request it's part of, such as a version header
(`NullInterchangeContext.instance` when there's none). `@json` and its
companion `@jsonExtra` are described with the other
[decorators](../language/builtins.md).

Source: [json.temper.md](../../lib/std/json/json.temper.md).
