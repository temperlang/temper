# Map Test

Here we test the `Map` type as well as related types `Mapped`, `MapBuilder`, and
`Pair`. These are similar to `Listed`, `ListBuilder`, and `ListEntry`. We
also have marker interface `MapKey`, which might allow for custom key types in
the future but for now just marks `Int` and `String` as usable keys.


## Read-Only Map

Let's start with a basic Map. But also provide a function on Mapped values for
testing against both read-only Map and read-write MapBuilder.

Also demonstrate use of the ambivalent Mapped type.

    let listKeys<K extends MapKey, V>(mapped: Mapped<K, V>): List<K> {
      // TODO Lambda return type inference doesn't work here.
      mapped.toList().map { (entry): K => entry.key }
    }

TODO Can we compile `Map` construction into map literals on some backends?

TODO Does `key` interface constraint not get enforced? I tried float values.

    let messages = new Map([
      new Pair(200, "OK"),
      new Pair(404, "Not Found"),
      new Pair(500, "Internal Server Error"),
    ]);
    let messageDefined(code: Int): String { messages.has(code).toString() }
    let message = messages[200];
    console.log("Message: ${message}");
    console.log("Has 200: ${messageDefined(200)}, 302: ${messageDefined(302)}");
    console.log("Fallback: ${messages[302] orelse "Found"}");
    console.log(
      "Codes: ${listKeys(messages).join(" ") { it => it.toString() }}"
    );
    console.log("Again: ${messages.keys().join(" ") { it => it.toString() }}");
    console.log("Messages: ${messages.values().join(", ") { it => it }}");
    let groups = messages.toListWith { (key, value): Int => key / 100 };
    console.log("Groups: ${groups.join(" ") { it => it.toString() }}");

```log
Message: OK
Has 200: true, 302: false
Fallback: Found
Codes: 200 404 500
Again: 200 404 500
Messages: OK, Not Found, Internal Server Error
Groups: 2 4 5
```


## Read-Write MapBuilder

Now try out imperative building up.

    let neighbors = do {
      // Use String keys and a more complicated value type, to exercise things.
      let builder = new MapBuilder<String, List<String>>();
      builder["Honduras"] = ["El Salvador", "Guatemala", "Nicaragua"];
      // Exercise get and mapped view from builder itself.
      console.log(
        "From builder: ${builder["Honduras"].join(", ") { it => it }}"
      );
      console.log("Key so far: ${listKeys(builder)[0]}");
      // Add more.
      builder["El Salvador"] = ["Guatemala", "Honduras"];
      builder["Nicaragua"] = ["Costa Rica", "Honduras"];
      builder["Panama"] = ["Colombia", "Costa Rica"];
      // And test removal as well.
      builder.remove("Honduras") orelse void;
      let was = builder.remove("El Salvador");
      console.log("Was there: ${was.join(", ") { it => it }}");
      console.log(builder.remove("El Salvador")[0] orelse "Gone");
      // Finish
      builder.toMap()
    };
    console.log(
      "From built: ${neighbors["Nicaragua"].join(", ") { it => it }}"
    );

```log
From builder: El Salvador, Guatemala, Nicaragua
Key so far: Honduras
Was there: Guatemala, Honduras
Gone
From built: Costa Rica, Honduras
```

# Map length

Let's try getting the length of a Map

    let mapOfNone = new Map<Int, Int>([]);
    let mapOfOne = new Map<String, String>([
      new Pair<String, String>("jack", "all trades"),
    ]);
    let mapOfTwo = new Map<Int, String>([
      new Pair<Int, String>(1, "one"),
      new Pair<Int, String>(2, "electric boogaloo"),
    ]);
    console.log("mapOfNone: ${mapOfNone.length}");
    console.log("mapOfOne: ${mapOfOne.length}");
    console.log("mapOfTwo: ${mapOfTwo.length}");

```log
mapOfNone: 0
mapOfOne: 1
mapOfTwo: 2
```

# Maps get with defaults

    let colorToHex(color: List<Boolean>): String {
      var c = color.join("", fn(b: Boolean): String {
        if (b) {
          return "FF";
        } else {
          return "00";
        }
      });
      return "#${c}";
    }
    let eightColors = new MapBuilder<String, List<Boolean>>();
    eightColors["black"] = [false, false, false];
    eightColors["red"] = [true, false, false];
    eightColors["green"] = [false, true, false];
    eightColors["blue"] = [false, false, true];
    eightColors["yellow"] = [true, true, false];
    eightColors["cyan"] = [false, true, true];
    eightColors["purple"] = [true, false, true];
    eightColors["white"] = [true, true, true];
    console.log("with 3 bits you can draw ${eightColors.length} colors.");
    eightColors.forEach { color, bits =>
      var foundByGetOr = eightColors.getOr(color, []);
      if (foundByGetOr.length != 3) {
        console.log("getOr(\"${color}\") failed to find key");
      }
      var want = colorToHex(bits);
      var got = colorToHex(foundByGetOr);
      if (want != got) {
        console.log("getOr(\"${color}\") found ${got} but wanted ${want}");
      }
    }

```log
with 3 bits you can draw 8 colors.
```

# Converting Mapped types

## Map to MapBuilder

Our display function, to print out a map

    let printMap(name: String, map: Map<String, String>): Void {
      console.log("--- ${name} ---");
      map.forEach { key, value =>
        console.log("${key} = ${value}");
      }
    }

    let printMapBuilder(name: String, map: MapBuilder<String, String>): Void {
      console.log("--- ${name} ---");
      map.forEach { key, value =>
        console.log("${key} = ${value}");
      }
    }

An immutable Map

    var map = new Map<String, String>([
      new Pair<String, String>("key", "value"),
      new Pair<String, String>("other key", "other value"),
    ]);
    printMap("map", map);

```log
--- map ---
key = value
other key = other value
```

Can be converted into a MapBuilder

    var builder = map.toMapBuilder();
    builder["added key"] = "added value";
    builder["key"] = "replaced value";
    printMapBuilder("builder", builder);

```log
--- builder ---
key = replaced value
other key = other value
added key = added value
```

Even though we added to builder, map did not change

    printMap("map (same as above)", map);

```log
--- map (same as above) ---
key = value
other key = other value
```

## MapBuilder to Map

You can also convert back from a MapBuilder to a Map

    var newMap = builder.toMap();
    printMap("builder to map", newMap);

```log
--- builder to map ---
key = replaced value
other key = other value
added key = added value
```

The resulting map is immutable, so changes to the builder no longer impact it

    builder.remove("other key");
    builder["key"] = "double replaced value";
    printMap("builder to map (same as above)", newMap);

```log
--- builder to map (same as above) ---
key = replaced value
other key = other value
added key = added value
```

We also can clear MapBuilders.

    builder.clear();
    builder["something"] = "alone";
    printMapBuilder("recently cleared", builder);

```log
--- recently cleared ---
something = alone
```

## Map to Map

Converting a Map with .toMap() does not *need* to copy

    var mayOrMayNotCopyMap = map.toMap();

Even if it is typed as Mapped

    var mapped: Mapped<String, String> = map;
    var mayOrMayNotCopyMapped = mapped.toMap();

## MapBuilder to MapBuilder

    var madeBy = new MapBuilder<String, String>();
    madeBy["temper"] = "temper systems";
    printMapBuilder("made by", madeBy);

```log
--- made by ---
temper = temper systems
```

    var madeByAndJokes = madeBy.toMapBuilder();
    madeByAndJokes["milk"] = "milking a cow";
    printMapBuilder("made by (and a joke about milk)", madeByAndJokes);

```log
--- made by (and a joke about milk) ---
temper = temper systems
milk = milking a cow
```

    printMapBuilder("made by (not changed from a change to a copy)", madeBy);

```log
--- made by (not changed from a change to a copy) ---
temper = temper systems
```
