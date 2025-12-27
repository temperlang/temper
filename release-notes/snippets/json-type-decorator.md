### *@json* type declaration decorator

Applying *@json* to a type declaration adds instructions for
encoding/decoding instances of the type to/from JSON.

```temper
// JSON definitions are from std/json
let {
  InterchangeContext,
  NullInterchangeContext,
  JsonTextProducer,
  parseJson,
} = import("std/json");

// @json applies to class and interface declarations
@json class Point {
  public x: Int;
  public y: Int;
}

let myPoint = new Point(1, 2);
// JSON adapters allow encoding
let myJsonTextProducer = new JsonTextProducer();
Point.jsonAdapter().encodeToJson(
  myPoint,
  jsonTextProducer
);
console.log(myJsonTextProducer.toJsonString());
//!outputs "{\"x\":1,\"y\":2}"

// They also allow decoding
let anotherPoint = Point.jsonAdapter().decodeFromJson(
  "{\"x\":3,\"y\":4}",
  NullInterchangeContext.instance
);
```
