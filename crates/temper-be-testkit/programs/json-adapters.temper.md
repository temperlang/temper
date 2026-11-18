# JSON adapters

`@json` types round trip through JSON text: a class's fields, as
built-in types read and written directly, a nullable field, a list of
another `@json` class, and a field `@jsonName` renames; a sealed
interface whose decoder tells its classes apart by their properties,
and one by a `@jsonExtra` value; and a class that encodes itself.
Decoding bubbles on a missing property and on a value no class has.
The log is what the interpreter gives.

    let {
      InterchangeContext,
      JsonAdapter,
      JsonNumeric,
      JsonProducer,
      JsonSyntaxTree,
      JsonTextProducer,
      NullInterchangeContext,
      listJsonAdapter,
      parseJson,
    } = import("std/json");

    @json class Point(public x: Int, public y: Int) {}

    @json class Record(
      public name: String,
      @jsonName("is-active") public active: Boolean,
      public score: Float64,
      public big: Int64,
      public nickname: String?,
      public points: List<Point>,
    ) {}

    @json sealed interface Shape {}
    @json class Circle(public radius: Int) extends Shape {}
    @json class Rect(public width: Int, public height: Int) extends Shape {}

    @json sealed interface Message {}
    @json @jsonExtra("kind", "hello")
    class Hello(public text: String) extends Message {}
    @json @jsonExtra("kind", "bye")
    class Bye(public text: String) extends Message {}

    @json class Celsius(public degrees: Int) {
      public encodeToJson(p: JsonProducer): Void {
        p.int32Value(degrees);
      }

      public static decodeFromJson(
        t: JsonSyntaxTree,
        ic: InterchangeContext,
      ): Celsius throws Bubble {
        new Celsius((t as JsonNumeric).asInt32())
      }
    }

    let encode<T>(adapter: JsonAdapter<T>, value: T): String {
      let p = new JsonTextProducer();
      adapter.encodeToJson(value, p);
      p.toJsonString() orelse "bad JSON"
    }

    let decode<T>(adapter: JsonAdapter<T>, text: String): T throws Bubble {
      adapter.decodeFromJson(parseJson(text), NullInterchangeContext.instance)
    }

    let roundTrip<T>(adapter: JsonAdapter<T>, value: T): Void {
      let text = encode(adapter, value);
      let back = decode(adapter, text) orelse panic();
      let again = encode(adapter, back);
      console.log(if (again == text) { text } else { "${text} became ${again}" });
    }

    roundTrip(Point.jsonAdapter(), new Point(1, -2));
    roundTrip(
      Record.jsonAdapter(),
      new Record("Ada", true, 1.5, 123456789012i64, null, [new Point(3, 4)]),
    );
    let shapes: List<Shape> = [new Circle(2), new Rect(3, 4)];
    roundTrip(List.jsonAdapter(Shape.jsonAdapter()), shapes);
    let messages: List<Message> = [new Hello("hi"), new Bye("so long")];
    roundTrip(List.jsonAdapter(Message.jsonAdapter()), messages);
    roundTrip(Celsius.jsonAdapter(), new Celsius(21));

    let shape = decode(Shape.jsonAdapter(), "{\"height\": 6, \"width\": 5}") orelse panic();
    when (shape) {
      is Rect -> console.log("rect ${shape.width}x${shape.height}");
      else -> console.log("not a rect");
    }
    let record = decode(
      Record.jsonAdapter(),
      "{\"name\": \"Bo\", \"is-active\": false, \"score\": 2, \"big\": 7, \"nickname\": \"B\", \"points\": []}",
    ) orelse panic();
    console.log("${record.name} ${record.active} ${record.nickname ?? "none"} ${record.points.length}");
    console.log(do { decode(Point.jsonAdapter(), "{\"x\": 1}"); "decoded" } orelse "no y");
    console.log(
      do { decode(Message.jsonAdapter(), "{\"kind\": \"what\", \"text\": \"?\"}"); "decoded" }
        orelse "no such kind"
    );

```log
{"x":1,"y":-2}
{"name":"Ada","is-active":true,"score":1.5,"big":123456789012,"nickname":null,"points":[{"x":3,"y":4}]}
[{"radius":2},{"width":3,"height":4}]
[{"text":"hi","kind":"hello"},{"text":"so long","kind":"bye"}]
21
rect 5x6
Bo false B 0
no y
no such kind
```
