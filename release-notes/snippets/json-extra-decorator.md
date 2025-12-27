### *\@jsonExtra* decorator

The new *\@jsonExtra* decorator allows for decoding of *sealed
interface*s from JSON that would otherwise be ambiguous because two or
more subtypes have the same expected JSON structure.

With the new decorator, a subtype can specify that it expects a
JSON property to have a specific value.  This property does not need
to correspond to a constructor input or field of the Temper type.


```temper
let {
  JsonTextProducer,
} = import("std/json");

@json @jsonExtra("jsonFormIsEmpty", false)
class Empty {}

let jsonTextProducer = new JsonTextProducer();
Empty.jsonAdapter().encodeToJson(new Empty(), jsonTextProducer);

jsonTextProducer.toJsonString() == """
    {"jsonFormIsEmpty":false}
    """
```

And this comes in handy when disambiguating sealed interface subtypes.

```temper
let {
  InterchangeContext,
  NullInterchangeContext,
  parseJson,
} = import("std/json");

// A sealed interface with two subtypes: Foo and Bar.
@json
sealed interface Sup {}

@json @jsonExtra("type", "Foo")
class Foo(public i: Int) extends Sup {}

@json @jsonExtra("type", "Bar")
class Bar(public i: Int) extends Sup {}


// Decoding from JSON with the "type" parameter
// determines the variant.

let fooTree = parseJson("""
  {"type": "Foo", "i": 1}
  """);
let barTree = parseJson("""
  {"type": "Bar", "i": -1}
  """);

// We can ask Sup's adapter to decode the JSON above.
Sup.jsonAdapter()
  .decodeFromJson(fooTree, NullInterchangeContext.instance)
  .is<Foo>()

&&

Sup.jsonAdapter()
  .decodeFromJson(barTree, NullInterchangeContext.instance)
  .is<Bar>()
```
