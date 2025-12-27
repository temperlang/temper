# Parse JSON tests

(These tests should probably be `test { ... }` blocks in std/json.temper.md
but we don't auto-run those on all backends as of this writing: 2024-8)

    let {
      JsonArray,
      JsonNull,
      JsonNumeric,
      JsonObject,
      JsonSyntaxTreeProducer,
      JsonTextProducer,
      OrNullJsonAdapter,
      NullInterchangeContext,
      int32JsonAdapter,
      int64JsonAdapter,
      parseJson,
      parseJsonToProducer,
    } = import("std/json");

## Empty list

    for (let s of ['[]', ' [] ', ' [ ] ', '[1]', 'null']) {
      let jsonValue = parseJson(s) orelse null;
      console.log(
          "`${s}` is empty array: ${
            (jsonValue is JsonArray && jsonValue.elements.isEmpty).toString()
          }"
      );
    }

```log
`[]` is empty array: true
` [] ` is empty array: true
` [ ] ` is empty array: true
`[1]` is empty array: false
`null` is empty array: false
```

## Malformed "lists"

    for (let s of ['[', ']', '[,]', '[]]', '[1,]', '[1,,2]']) {
      var bubbled = false;
      let p = new JsonSyntaxTreeProducer();
      parseJsonToProducer(s, p);
      p.toJsonSyntaxTree() orelse do { bubbled = true; }
      let error = p.jsonError;
      console.log(
        "`${s}` has${if (bubbled) { "" } else { " no" }} error: ${error ?? "MISSING ERROR"}"
      );
    }

```log
`[` has error: Expected ']', but got end-of-file
`]` has error: Expected JSON value, but got `]`
`[,]` has error: Expected JSON value, but got `,]`
`[]]` has error: Extraneous JSON `]`
`[1,]` has error: Expected JSON value, but got `]`
`[1,,2]` has error: Expected JSON value, but got `,2]`
```

## String encoding

Encoding a string mirrors the behaviour of JavaScript's *JSON.stringify*
so that our output can exactly match that of a platform where code-size
is so important that shipping a JSON encoder/decoder is a bad idea.

     let selectCodePoints = "\x00\x01\x02\x03\x04\x05\x06\x07\x08\x09\x0a\x0b\x0c\x0d\x0e\x0f\x10\x11\x12\x13\x14\x15\x16\x17\x18\x19\x1a\x1b\x1c\x1d\x1e\x1f\x20-\u{10000}-\ue000-\ud7ff\u2028\u2029\u0085";

That string contains all ASCII control characters, a number of code-points
that often trigger boundary conditions, non-ASCII line terminators, and a
supplementary code point. We can't test orphaned surrogates from pure Temper
because Temper itself can only create strings with valid Unicode.

    do {
      let p = new JsonTextProducer(NullInterchangeContext.instance);
      p.stringValue(selectCodePoints);
      console.log(
        "string with select code points: ${
          p.toJsonString() orelse "?!"
        }"
      );
    }

The output below was derived by running JavaScript *JSON.stringify*.

```log
string with select code points: "\u0000\u0001\u0002\u0003\u0004\u0005\u0006\u0007\b\t\n\u000b\f\r\u000e\u000f\u0010\u0011\u0012\u0013\u0014\u0015\u0016\u0017\u0018\u0019\u001a\u001b\u001c\u001d\u001e\u001f -𐀀--퟿  "
```

## Nullable fields

The *OrNullJsonAdapter* maps between JSON `null` and Temper `null`.
It's used in `@json` generation of adapters for classes with nullable fields.
`null` type parameters are a throny translation issue in some backends
(especially C# and its generic reification and bounded monomorphization).

Let's encode and decode a JSON object with one null property and one-non-null.

    do {
      let p = new JsonTextProducer(NullInterchangeContext.instance);
      p.startObject();
      let intOrNullAdapter = new OrNullJsonAdapter<Int>(Int.jsonAdapter());
      var x: Int? = null;
      var y: Int? = 123;
      p.objectKey("x");
      intOrNullAdapter.encodeToJson(x, p);
      p.objectKey("y");
      intOrNullAdapter.encodeToJson(y, p);
      p.endObject();

      console.log(p.toJsonString());
    }

```log
{"x":null,"y":123}
```

Now to decode:

    do {
      let s = '{"x":null,"y":123,"z":123456789012}';
      let ast = parseJson(s);

      let obj = ast as JsonObject;
      let xAst = obj.propertyValueOrBubble("x");
      let yAst = obj.propertyValueOrBubble("y");

      console.log("for x, got JSON null: ${xAst is JsonNull}");
      console.log("for y, got JSON numeric: ${yAst is JsonNumeric}");

      let intOrNullAdapter = new OrNullJsonAdapter<Int>(Int.jsonAdapter());
      let x: Int? = intOrNullAdapter.decodeFromJson(xAst, NullInterchangeContext.instance);
      let y: Int? = intOrNullAdapter.decodeFromJson(yAst, NullInterchangeContext.instance);

      console.log("x ?? -1 -> ${x ?? -1}");
      console.log("y ?? -1 -> ${y ?? -1}");

Also prove that Int64 works.

      let zAst = obj.propertyValueOrBubble("z");
      let int64OrNullAdapter = new OrNullJsonAdapter<Int64>(Int64.jsonAdapter());
      let z: Int64? = int64OrNullAdapter.decodeFromJson(zAst, NullInterchangeContext.instance);
      console.log("z ?? -1 -> ${z ?? -1i64}");
    }

```log
for x, got JSON null: true
for y, got JSON numeric: true
x ?? -1 -> -1
y ?? -1 -> 123
z ?? -1 -> 123456789012
```

## Parsing can normalize JSON

If we just parse JSON straight to a JSON text producer, we get the original JSON but normalized.

     let normalJson = new JsonTextProducer();
     parseJsonToProducer(
       """
       "{
       "  "foo": "bar",
       "  "baz": [
       "    "boo",
       "    { "f": 1, "a": null, "r": 123.0 }
       "  ]
       "}
       ,
       normalJson,
     );
     console.log("Normalized:");
     console.log(normalJson.toJsonString());

```log
Normalized:
{"foo":"bar","baz":["boo",{"f":1,"a":null,"r":123.0}]}
```
