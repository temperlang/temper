# JSON support

The `@json` decorator for Temper classes supports converting values to
and from the JSON data interchange format.

This module provides JSON value representations to support
unmarshalling, converting a JSON text to domain value(s), and it
provides a JSON builder to support marshalling domain values to JSON.

## Standard

For the purposes of this document, "JSON" is defined by [ECMA-404],
"*The JSON data interchange syntax*."

## Context

Producers and consumers may need to engage in [content negotiation].

For example, a marshaller might benefit from having access to a version
header from a Web API request to distinguish clients that can accept the
latest version from those that need a deprecated version.

    export interface InterchangeContext {
      public getHeader(headerName: String): String?;
    }

And for convenience, here's a blank interchange context.

    export class NullInterchangeContext extends InterchangeContext {
      public getHeader(headerName: String): String? { null }

      public static instance = new NullInterchangeContext();
    }

## Marshalling support

    export interface JsonProducer {
      public interchangeContext: InterchangeContext;

      // A JSON object value is specified by a start-object
      // event, zero or more property key/value pairs
      // (see below) and an end-object event.

      public startObject(): Void;
      public endObject(): Void;

      // Within the start and end of an object
      // you may have zero or more pairs of
      // a property key followed by a nested value.

      public objectKey(key: String): Void;

      // To emit an array, start it, emit
      // zero or more nested values, then end it.

      public startArray(): Void;
      public endArray(): Void;

      // Emitters for simple values

      public nullValue(): Void;
      public booleanValue(x: Boolean): Void;

      // Numeric values come in several flavours

      public int32Value(x: Int): Void;
      public int64Value(x: Int64): Void;
      public float64Value(x: Float64): Void;
      /**
       * A number that fits the JSON number grammar to allow
       * interchange of numbers that are not easily representible
       * using numeric types that Temper connects to.
       */
      public numericTokenValue(x: String): Void;

      public stringValue(x: String): Void;
      // TODO: stringValueToken(...) that stores string content that
      // does not decode to a USVString for JSON that conveys
      // byte data as byte pairs with embedded, mismatched surrogates.

      public get parseErrorReceiver(): JsonParseErrorReceiver? { null }
    }

This API does allow for specifying malformed JSON outputs as below.
Implementations must allow for such specifications until allowing
for observation of the produced output.
A streaming JSON implementation may treat flushing a portion of
output to an output channel as an observation and expose an error
then.

An implementation may choose to treat a number of top-level values
other than one (`[] []`) as well-formed or malformed, for example to
support related interchange formats like [JSON Lines].

An implementation may use a variety of strategies to represent
traditionally unrepresentable values like special floating point
*NaN* (not a number) and ±infinities.

TODO: allow an implementation to use a variety of strategies to deal with
a valid JSON string value (whether used as a property key or a value)
that is not a [scalar value string], for example, because it contains a
UTF-16 surrogate that is not part of a well-formed surrogate pair.

```temper inert
//// Malformed object
myJsonProducer1.startObject();
// no end for object or array

// missing start for object or array
myJsonProducer2.endObject();

// mismatch start and end
myJsonProducer3.startArray();
myJsonProducer3.endObject();

// malformed properties
myJsonProducer4.startObject();
// missing key
myJsonProducer4.nullValue();
myJsonProducer4.endObject();

myJsonProducer5.startObject();
myJsonProducer5.objectKey("k");
// missing value
myJsonProducer5.endObject();

//// Malformed values
// questionable numbers
myJsonProducer6.float64Value(NaN);
myJsonProducer7.float64Value(Infinity);
myJsonProducer8.numericTokenValue("1.");
// questionable strings
myJsonProducer9.stringTokenValue("\uD800");

//// No unique top-level value (see MAY above)
myJsonProducer10.nullValue();
myJsonProducer10.nullValue();
```

## Unmarshalling support

Unmarshalling involves turning JSON source texts (which specify state)
into domain values (with state and behaviour).

It is convenient to have a syntax tree for JSON.  Often, unmarshalling
involves looking at select object properties to figure out how to interpret
the whole, by trying rules like the below:

- if it has keys `x` and `y`, delegate to the *Point2d* unmarshaller,
- or if it has keys `radius` and `centroid`, delegate to the *Circle* unmarshaller,
- ...

There may not be an order of keys that makes it easy to consume a stream of
events to implement those rules, and expecting all producers to use the same
key order would lead to brittle unmarshalling.

    export sealed interface JsonSyntaxTree {
      public produce(p: JsonProducer): Void;
    }

    export class JsonObject(
      public properties: Map<String, List<JsonSyntaxTree>>,
    ) extends JsonSyntaxTree {

      /**
       * The JSON value tree associated with the given property key or null
       * if there is no such value.
       *
       * The properties map contains a list of sub-trees because JSON
       * allows duplicate properties.  ECMA-404 §6 notes (emphasis added):
       *
       * > The JSON syntax does not impose any restrictions on the strings
       * > used as names, **does not require that name strings be unique**,
       * > and does not assign any significance to the ordering of
       * > name/value pairs.
       *
       * When widely used JSON parsers need to relate a property key
       * to a single value, they tend to prefer the last key/value pair
       * from a JSON object.  For example:
       *
       * JS:
       *
       *     JSON.parse('{"x":"first","x":"last"}').x === 'last'
       *
       * Python:
       *
       *     import json
       *     json.loads('{"x":"first","x":"last"}')['x'] == 'last'
       *
       * C#:
       *
       *    using System.Text.Json;
       * 		JsonDocument d = JsonDocument.Parse(
       * 			"""
       * 			{"x":"first","x":"last"}
       * 			"""
       * 		);
       * 		d.RootElement.GetProperty("x").GetString() == "last"
       */
      public propertyValueOrNull(propertyKey: String): JsonSyntaxTree? {
        let treeList = this.properties.getOr(propertyKey, []);

        let lastIndex = treeList.length - 1;
        if (lastIndex >= 0) {
          treeList[lastIndex]
        } else {
          null
        }
      }

      public propertyValueOrBubble(propertyKey: String): JsonSyntaxTree throws Bubble {
        propertyValueOrNull(propertyKey) as JsonSyntaxTree
      }

      public produce(p: JsonProducer): Void {
        p.startObject();
        properties.forEach { k: String, vs: List<JsonSyntaxTree> =>
          vs.forEach { v: JsonSyntaxTree =>
            p.objectKey(k);
            v.produce(p);
          }
        }
        p.endObject();
      }
    }

    export class JsonArray(
      public elements: List<JsonSyntaxTree>,
    ) extends JsonSyntaxTree {

      public produce(p: JsonProducer): Void {
        p.startArray();
        elements.forEach { v: JsonSyntaxTree =>
          v.produce(p);
        }
        p.endArray();
      }
    }

    export class JsonBoolean(
      public content: Boolean,
    ) extends JsonSyntaxTree {
      public produce(p: JsonProducer): Void {
        p.booleanValue(content);
      }
    }

    export class JsonNull extends JsonSyntaxTree {
      public produce(p: JsonProducer): Void {
        p.nullValue();
      }
    }

    export class JsonString(
      public content: String,
    ) extends JsonSyntaxTree {
      public produce(p: JsonProducer): Void {
        p.stringValue(content);
      }
    }

    export sealed interface JsonNumeric extends JsonSyntaxTree {
      public asJsonNumericToken(): String;

      public asInt32(): Int throws Bubble;
      public asInt64(): Int64 throws Bubble;
      public asFloat64(): Float64 throws Bubble;
    }

    export let JsonInt = JsonInt32;

    export class JsonInt32(
      public content: Int,
    ) extends JsonNumeric {
      public produce(p: JsonProducer): Void {
        p.int32Value(content);
      }

      public asJsonNumericToken(): String {
        content.toString()
      }

      public asInt32(): Int { content }

      public asInt64(): Int64 { content.toInt64() }

      public asFloat64(): Float64 { content.toFloat64() }
    }

    export class JsonInt64(
      public content: Int64,
    ) extends JsonNumeric {
      public produce(p: JsonProducer): Void {
        p.int64Value(content);
      }

      public asJsonNumericToken(): String {
        content.toString()
      }

      public asInt32(): Int throws Bubble { content.toInt32() }

      public asInt64(): Int64 { content }

      public asFloat64(): Float64 throws Bubble { content.toFloat64() }
    }

    export class JsonFloat64(
      public content: Float64,
    ) extends JsonNumeric {
      public produce(p: JsonProducer): Void {
        p.float64Value(content);
      }

      public asJsonNumericToken(): String {
        content.toString()
      }

      public asInt32(): Int throws Bubble { content.toInt32() }

      public asInt64(): Int64 throws Bubble { content.toInt64() }

      public asFloat64(): Float64 { content }
    }

    export class JsonNumericToken(
      public content: String,
    ) extends JsonNumeric {
      public produce(p: JsonProducer): Void {
        p.numericTokenValue(content);
      }

      public asJsonNumericToken(): String {
        content
      }

      public asInt32(): Int throws Bubble {
        // TODO Prohibit nonzero fractional values.
        content.toInt32() orelse content.toFloat64().toInt32()
      }

      public asInt64(): Int64 throws Bubble {
        // TODO Prohibit nonzero fractional values.
        content.toInt64() orelse content.toFloat64().toInt64()
      }

      public asFloat64(): Float64 throws Bubble { content.toFloat64() }
    }

The *produce* method allows a JSON syntax tree to describe itself to a
JSON producer so JSON syntax trees, while useful during unmarshalling
can also help with marshalling.

## Producing JSON text

A JSON producer that appends to an internal buffer lets us produce
JSON source text.

    // A state machine lets us figure out when to insert commas.
    let JSON_STATE_OPEN_OBJECT = 0; // Last was "{"
    let JSON_STATE_AFTER_KEY = 1; // Last was property key
    let JSON_STATE_AFTER_PROPERTY = 2; // Last was property value
    let JSON_STATE_OPEN_ARRAY = 3; // Last was "["
    let JSON_STATE_AFTER_ELEMENT = 4; // Last was array element
    let JSON_STATE_NO_VALUE = 5;
    let JSON_STATE_ONE_VALUE = 6;

    export class JsonTextProducer extends JsonProducer {
      public interchangeContext: InterchangeContext;
      private buffer: StringBuilder;
      private stack: ListBuilder<Int>;
      private var wellFormed: Boolean;

      public constructor(
        interchangeContext: InterchangeContext = NullInterchangeContext.instance
      ): Void {
        this.interchangeContext = interchangeContext;
        this.buffer = new StringBuilder();
        this.stack = new ListBuilder<Int>();
        this.stack.add(JSON_STATE_NO_VALUE);
        this.wellFormed = true;
      }

      private state(): Int {
        stack.getOr(stack.length - 1, -1)
      }

      private beforeValue(): Void {
        let currentState = state();
        when (currentState) {
          JSON_STATE_OPEN_ARRAY ->
            stack[stack.length - 1] = JSON_STATE_AFTER_ELEMENT;
          JSON_STATE_AFTER_ELEMENT -> buffer.append(",");
          JSON_STATE_AFTER_KEY ->
            stack[stack.length - 1] = JSON_STATE_AFTER_PROPERTY;
          JSON_STATE_NO_VALUE ->
            stack[stack.length - 1] = JSON_STATE_ONE_VALUE;
          JSON_STATE_ONE_VALUE, JSON_STATE_AFTER_PROPERTY ->
            wellFormed = false;
        }
      }

      public startObject(): Void {
        beforeValue();
        buffer.append("{");
        stack.add(JSON_STATE_OPEN_OBJECT);
      }

      public endObject(): Void {
        buffer.append("}");
        let currentState = state();
        if (JSON_STATE_OPEN_OBJECT == currentState ||
            JSON_STATE_AFTER_PROPERTY == currentState) {
          stack.removeLast();
        } else {
          wellFormed = false;
        }
      }

      public objectKey(key: String): Void {
        let currentState = state();
        when (currentState) {
          JSON_STATE_OPEN_OBJECT -> void;
          JSON_STATE_AFTER_PROPERTY -> buffer.append(",");
          else -> wellFormed = false;
        }
        encodeJsonString(key, buffer);
        buffer.append(":");
        if (currentState >= 0) {
          stack[stack.length - 1] = JSON_STATE_AFTER_KEY;
        }
      }

      public startArray(): Void {
        beforeValue();
        buffer.append("[");
        stack.add(JSON_STATE_OPEN_ARRAY);
      }

      public endArray(): Void {
        buffer.append("]");
        let currentState = state();
        if (JSON_STATE_OPEN_ARRAY == currentState ||
            JSON_STATE_AFTER_ELEMENT == currentState) {
          stack.removeLast();
        } else {
          wellFormed = false;
        }
      }

      public nullValue(): Void {
        beforeValue();
        buffer.append("null");
      }

      public booleanValue(x: Boolean): Void {
        beforeValue();
        buffer.append(if (x) { "true" } else { "false" });
      }

      public int32Value(x: Int): Void {
        beforeValue();
        buffer.append(x.toString());
      }

      public int64Value(x: Int64): Void {
        beforeValue();
        buffer.append(x.toString());
      }

      public float64Value(x: Float64): Void {
        beforeValue();
        buffer.append(x.toString());
      }

      public numericTokenValue(x: String): Void {
        // TODO check syntax of x and maybe set malformed
        beforeValue();
        buffer.append(x);
      }

      public stringValue(x: String): Void {
        beforeValue();
        encodeJsonString(x, buffer);
      }

      public toJsonString(): String throws Bubble {
        if (wellFormed && stack.length == 1 && state() == JSON_STATE_ONE_VALUE) {
          return buffer.toString();
        } else {
          bubble();
        }
      }
    }

    let encodeJsonString(x: String, buffer: StringBuilder): Void {
      buffer.append("\"");
      var i = String.begin;
      var emitted = i;
      while (x.hasIndex(i)) {
        // The choice of which code-points to escape and which
        // escape sequences to use is the same as those made in
        // ES262 § 25.5.2.3: QuoteJSONString
        // as long as the string has no orphaned surrogates.
        // This means that the output will work in many parsers
        // while still allowing use of JSON.stringify in JavaScript
        // where keeping code-size low by not shipping a JSON encoder
        // is important.

        let cp = x[i];
        let replacement = when (cp) {
          char'\b'     -> "\\b";
          char'\t'     -> "\\t";
          char'\n'     -> "\\n";
          char'\f'     -> "\\f";
          char'\r'     -> "\\r";
          char'"'      -> "\\\"";
          char'\\'     -> "\\\\";
          else         ->
            // Control characters and non-USV code-points.
            if (cp < 0x20 || 0xD800 <= cp && cp <= 0xDFFF) {
              "\\u"
            } else {
              ""
            };
        };

        let nextI = x.next(i);

        if (replacement != "") {
          buffer.appendBetween(x, emitted, i);
          buffer.append(replacement);
          if (replacement == "\\u") {
            encodeHex4(cp, buffer);
          }
          emitted = nextI;
        }

        i = nextI;
      }
      buffer.appendBetween(x, emitted, i);
      buffer.append("\"");
    }

    let hexDigits = [
      '0', '1', '2', '3', '4', '5', '6', '7',
      '8', '9', 'a', 'b', 'c', 'd', 'e', 'f',
    ];

    let encodeHex4(cp: Int, buffer: StringBuilder): Void {
      let b0 = (cp / 0x1000) & 0xf;
      let b1 = (cp / 0x100) & 0xf;
      let b2 = (cp / 0x10) & 0xf;
      let b3 = cp & 0xf;
      buffer.append(hexDigits[b0]);
      buffer.append(hexDigits[b1]);
      buffer.append(hexDigits[b2]);
      buffer.append(hexDigits[b3]);
    }

## Parsing JSON

JSON tokens, like `{` and `"foo"` correspond fairly closely to events
like *JsonProducer.startObject* and *JsonProducer.stringValue* respectively.

*JsonSyntaxTree* knows how to describe itself to a *JsonProducer*, but we can
also craft a *JsonProducer* that constructs a syntax tree.

First, we need a way to explain syntax errors, ideally in a way that lets
a *JsonProducer* represent both the valid JSON or the error.

    export interface JsonParseErrorReceiver {
      public explainJsonError(explanation: String): Void;
    }

Now, we are ready to build a *JsonProducer* that produces a syntax tree given
valid JSON, but if given a string that is not valid JSON, it has a syntax error.

    export class JsonSyntaxTreeProducer extends JsonProducer & JsonParseErrorReceiver {
      private stack: ListBuilder<ListBuilder<JsonSyntaxTree>>;
      private var error: String?;
      public get interchangeContext(): InterchangeContext {
        NullInterchangeContext.instance
      }

      public constructor() {
        stack = new ListBuilder<ListBuilder<JsonSyntaxTree>>();
        stack.add(new ListBuilder<JsonSyntaxTree>());
        error = null;
      }

      private storeValue(v: JsonSyntaxTree): Void {
        if (!stack.isEmpty) {
          stack[stack.length - 1].add(v);
        }
      }

      public startObject(): Void {
        stack.add(new ListBuilder<JsonSyntaxTree>());
      }

      public endObject(): Void {
        if (stack.isEmpty) {
          return;
        }
        let ls = stack.removeLast();
        // In the common case, there are no duplicate keys.
        let m = new MapBuilder<String, List<JsonSyntaxTree>>();
        // But we need a way to accumulate them when there are duplicate keys.
        var multis: MapBuilder<String, ListBuilder<JsonSyntaxTree>>? = null;
        for (var i = 0, n = ls.length & -2; i < n;) {
          let keyTree = ls[i++];
          if (!(keyTree is JsonString)) { break }
          let key = (keyTree as JsonString orelse panic()).content;
          let value = ls[i++];

          if (m.has(key)) {
            if (multis == null) {
              multis = new MapBuilder<String, ListBuilder<JsonSyntaxTree>>();
            }
            let mb = multis as MapBuilder<String, ListBuilder<JsonSyntaxTree>> orelse panic();
            if (!mb.has(key)) {
              mb[key] = (m[key] orelse panic()).toListBuilder();
            }
            (mb[key] orelse panic()).add(value);
          } else {
            m[key] = [value];
          }
        }

        let multis = multis; // lock it in down here for inference
        if (multis != null) {
          multis.forEach { k: String, vs: ListBuilder<JsonSyntaxTree> =>
            m[k] = vs.toList();
          }
        }

        storeValue(new JsonObject(m.toMap()));
      }

      public objectKey(key: String): Void {
        storeValue(new JsonString(key));
      }

      public startArray(): Void {
        stack.add(new ListBuilder<JsonSyntaxTree>());
      }

      public endArray(): Void {
        if (stack.isEmpty) {
          return;
        }
        let ls = stack.removeLast();
        storeValue(new JsonArray(ls.toList()));
      }

      public nullValue(): Void {
        storeValue(new JsonNull());
      }

      public booleanValue(x: Boolean): Void {
        storeValue(new JsonBoolean(x));
      }

      public int32Value(x: Int): Void {
        storeValue(new JsonInt(x));
      }

      public int64Value(x: Int64): Void {
        storeValue(new JsonInt64(x));
      }

      public float64Value(x: Float64): Void {
        storeValue(new JsonFloat64(x));
      }

      public numericTokenValue(x: String): Void {
        storeValue(new JsonNumericToken(x));
      }

      public stringValue(x: String): Void {
        storeValue(new JsonString(x));
      }

      public toJsonSyntaxTree(): JsonSyntaxTree throws Bubble {
        if (stack.length != 1 || error != null) { bubble() }
        let ls = stack[0];
        if (ls.length != 1) { bubble() }
        ls[0]
      }

      public get jsonError(): String? { error }

      public get parseErrorReceiver(): JsonParseErrorReceiver { this }

      public explainJsonError(error: String): Void {
        this.error = error;
      }
    }

Some helpers let us route errors:

    let expectedTokenError(
      sourceText: String,
      i: StringIndex,
      out: JsonProducer,
      shortExplanation: String,
    ): Void {
      let gotten = if (sourceText.hasIndex(i)) {
        "`${ sourceText.slice(i, sourceText.end) }`"
      } else {
        "end-of-file"
      };
      storeJsonError(out, "Expected ${shortExplanation}, but got ${gotten}");
    }

    let storeJsonError(out: JsonProducer, explanation: String): Void {
      out.parseErrorReceiver?.explainJsonError(explanation);
    }

Next, a JSON string parser that drives a *JsonProducer*.

    export let parseJsonToProducer(sourceText: String, out: JsonProducer): Void {
      var i = String.begin;
      let afterValue = parseJsonValue(sourceText, i, out);
      if (afterValue is StringIndex) { // else parseJsonValue must have stored an error
        i = skipJsonSpaces(sourceText, afterValue);
        if (sourceText.hasIndex(i) && out.parseErrorReceiver != null) {
          storeJsonError(out, "Extraneous JSON `${sourceText.slice(i, sourceText.end)}`");
        }
      }
    }

A recursive descent parser without backtracking works just fine for JSON because the
next non-space character perfectly predicts the next production.
All `// > ` comments are followed by quotes from ECMA-404 2nd edition.
Each parsing helper takes the *StringIndex* before parsing and returns
the *StringIndex* at the end of the content it parsed, or maybe *null* if parsing
failed.

    // > Insignificant whitespace is allowed before or after any token.
    // > Whitespace is any sequence of one or more of the following code
    // > points:
    // >   character tabulation (U+0009),
    // >   line feed (U+000A),
    // >   carriage return (U+000D), and
    // >   space (U+0020).
    let skipJsonSpaces(sourceText: String, var i: StringIndex): StringIndex {
      while (sourceText.hasIndex(i)) {
        when (sourceText[i]) {
          0x9, 0xA, 0xD, 0x20 -> void;
          else -> break;
        }
        i = sourceText.next(i);
      }
      return i;
    }

    let parseJsonValue(
      sourceText: String, var i: StringIndex, out: JsonProducer
    ): StringIndexOption {
      i = skipJsonSpaces(sourceText, i);
      if (!sourceText.hasIndex(i)) {
        expectedTokenError(sourceText, i, out, "JSON value");
        return StringIndex.none
      }
      when (sourceText[i]) {
        char'{' -> parseJsonObject(sourceText, i, out);
        char'[' -> parseJsonArray(sourceText, i, out);
        char'"' -> parseJsonString(sourceText, i, out);
        char't', char'f' -> parseJsonBoolean(sourceText, i, out);
        char'n' -> parseJsonNull(sourceText, i, out);
        else -> parseJsonNumber(sourceText, i, out);
      }
    }

For a JSON object, parsing looks for a '{', then optionally a property.
After a property, if there is a ',' parsing looks for another property.
After the last property or lone '{', there must be a '}'.

    let parseJsonObject(
      sourceText: String, var i: StringIndex, out: JsonProducer
    ): StringIndexOption { do {
      if (!sourceText.hasIndex(i) || sourceText[i] != char'{') {
        expectedTokenError(sourceText, i, out, "'{'");
        return StringIndex.none;
      }
      out.startObject();
      i = skipJsonSpaces(sourceText, sourceText.next(i));
      if (sourceText.hasIndex(i) && sourceText[i] != char'}') {
        while (true) {
          let keyBuffer = new StringBuilder();
          let afterKey = parseJsonStringTo(sourceText, i, keyBuffer, out);
          if (!(afterKey is StringIndex)) { return StringIndex.none; }
          out.objectKey(keyBuffer.toString());
          i = skipJsonSpaces(sourceText, afterKey as StringIndex orelse panic());
          if (sourceText.hasIndex(i) && sourceText[i] == char':') {
            i = sourceText.next(i);
            let afterPropertyValue = parseJsonValue(sourceText, i, out);
            if (!(afterPropertyValue is StringIndex)) {
              return StringIndex.none;
            }
            i = afterPropertyValue as StringIndex;
          } else {
            expectedTokenError(sourceText, i, out, "':'");
            return StringIndex.none;
          }

          i = skipJsonSpaces(sourceText, i);
          if (sourceText.hasIndex(i) && sourceText[i] == char',') {
            i = skipJsonSpaces(sourceText, sourceText.next(i));
            continue;
          } else {
            break;
          }
        }
      }

      if (sourceText.hasIndex(i) && sourceText[i] == char'}') {
        out.endObject();
        return sourceText.next(i);
      } else {
        expectedTokenError(sourceText, i, out, "'}'");
        return StringIndex.none;
      }
    } orelse panic() } // See https://github.com/temperlang/temper/issues/203

For a JSON array, parsing looks for a '\[', and an optional element value.
After each element value, if there is a ',', parsing looks for another element
value.  After the last element value or lone '\[', there must be a '\]'.

    let parseJsonArray(
      sourceText: String, var i: StringIndex, out: JsonProducer
    ): StringIndexOption { do {
      if (!sourceText.hasIndex(i) || sourceText[i] != char'[') {
        expectedTokenError(sourceText, i, out, "'['");
        return StringIndex.none;
      }
      out.startArray();
      i = skipJsonSpaces(sourceText, sourceText.next(i));
      if (sourceText.hasIndex(i) && sourceText[i] != char']') {
        while (true) {
          let afterElementValue = parseJsonValue(sourceText, i, out);
          if (!(afterElementValue is StringIndex)) {
            return StringIndex.none;
          }
          i = afterElementValue as StringIndex;

          i = skipJsonSpaces(sourceText, i);
          if (sourceText.hasIndex(i) && sourceText[i] == char',') {
            i = skipJsonSpaces(sourceText, sourceText.next(i));
            continue;
          } else {
            break;
          }
        }
      }

      if (sourceText.hasIndex(i) && sourceText[i] == char']') {
        out.endArray();
        return sourceText.next(i);
      } else {
        expectedTokenError(sourceText, i, out, "']'");
        return StringIndex.none;
      }
    } orelse panic() } // See https://github.com/temperlang/temper/issues/203

A string consists of double-quotes with a select group of C-style escape sequences.
The characters that are allowed unescaped inside the quotes are any except ASCII
control characters.

> To escape a code point that is not in the Basic Multilingual Plane,
> the character may be represented as a twelve-character sequence,
> encoding the UTF-16 surrogate pair corresponding to the code point.

This suggests that when only one of a surrogate pair is escaped, that it is
treated as multiple code points.  This implementation collapses them into
a single code-point for consistency with backends with native UTF-16 strings
where such a distinction is not possible to represent.

    let parseJsonString(
      sourceText: String, var i: StringIndex, out: JsonProducer
    ): StringIndexOption {
      let sb = new StringBuilder();
      let after = parseJsonStringTo(sourceText, i, sb, out);
      if (after is StringIndex) {
        out.stringValue(sb.toString());
      }
      return after;
    }

    let parseJsonStringTo(
      sourceText: String, var i: StringIndex, sb: StringBuilder,
      errOut: JsonProducer
    ): StringIndexOption {
      if (!sourceText.hasIndex(i) || sourceText[i] != char'"') {
        expectedTokenError(sourceText, i, errOut, '"');
        return StringIndex.none;
      }
      i = sourceText.next(i);

      // Hold onto lead surrogates until we find out whether there's a trailing
      // surrogate or not.
      var leadSurrogate: Int = -1;

      var consumed = i;
      while (sourceText.hasIndex(i)) {
        let cp = sourceText[i];
        if (cp == char'"') { break }
        var iNext = sourceText.next(i);
        let end = sourceText.end;

        // Emit anything between consumed and i if there
        // is a pending surrogate or escaped characters.
        var needToFlush = false;

        // Decode one code-point or UTF-16 surrogate
        var decodedCp = if (cp != char'\\') {
          cp
        } else {
          needToFlush = true;
          if (!sourceText.hasIndex(iNext)) {
            expectedTokenError(sourceText, iNext, errOut, "escape sequence");
            return StringIndex.none;
          }
          let esc0 = sourceText[iNext];
          iNext = sourceText.next(iNext);
          when (esc0) {
            char'"', char'\\', char'/' -> esc0;
            char'b' -> char'\b';
            char'f' -> char'\f';
            char'n' -> char'\n';
            char'r' -> char'\r';
            char't' -> char'\t';
            char'u' -> do {
              let hex: Int = if (sourceText.hasAtLeast(iNext, end, 4)) {
                let startHex = iNext;
                iNext = sourceText.next(iNext);
                iNext = sourceText.next(iNext);
                iNext = sourceText.next(iNext);
                iNext = sourceText.next(iNext);
                decodeHexUnsigned(sourceText, startHex, iNext)
              } else {
                -1
              };
              if (hex < 0) {
                expectedTokenError(sourceText, iNext, errOut, "four hex digits");
                return StringIndex.none;
              }
              hex
            }
            else -> do {
              expectedTokenError(sourceText, iNext, errOut, "escape sequence");
              return StringIndex.none;
            }
          }
        };

        // If we have two surrogates, combine them into one code-point.
        // If we have a lead surrogate, make sure we can wait.
        if (leadSurrogate >= 0) {
          needToFlush = true;
          let lead = leadSurrogate;
          if (0xDC00 <= decodedCp && decodedCp <= 0xDFFF) {
            leadSurrogate = -1;
            decodedCp = (
              0x10000 +
              (((lead - 0xD800) * 0x400) | (decodedCp - 0xDC00))
            );
          }
        } else if (0xD800 <= decodedCp && decodedCp <= 0xDBFF) {
          needToFlush = true;
        }

        // Consume characters from sourceText onto sb if it's timely
        // to do so.
        if (needToFlush) {
          sb.appendBetween(sourceText, consumed, i);
          if (leadSurrogate >= 0) {
            // Not combined with a trailing surrogate.
            sb.appendCodePoint(leadSurrogate) orelse panic();
          }
          if (0xD800 <= decodedCp && decodedCp <= 0xDBFF) {
            leadSurrogate = decodedCp;
          } else {
            leadSurrogate = -1;
            sb.appendCodePoint(decodedCp) orelse panic();
          }
          consumed = iNext;
        }

        i = iNext;
      }

      if (!sourceText.hasIndex(i) || sourceText[i] != char'"') {
        expectedTokenError(sourceText, i, errOut, '"');
        StringIndex.none
      } else {
        if (leadSurrogate >= 0) {
          sb.appendCodePoint(leadSurrogate) orelse panic();
        } else {
          sb.appendBetween(sourceText, consumed, i);
        }
        i = sourceText.next(i); // Consume closing quote
        i
      }
    }

    let decodeHexUnsigned(
      sourceText: String, start: StringIndex, limit: StringIndex
    ): Int {
      var n = 0;
      var i = start;
      while (i.compareTo(limit) < 0) {
        let cp = sourceText[i];
        let digit = if (char'0' <= cp && cp <= char'0') {
          cp - char'0'
        } else if (char'A' <= cp && cp <= char'F') {
          cp - char'A' + 10
        } else if (char'a' <= cp && cp <= char'f') {
          cp - char'a' + 10
        } else {
          return -1;
        }
        n = (n * 16) + digit;
        i = sourceText.next(i);
      }
      n
    }

    let parseJsonBoolean(
      sourceText: String, var i: StringIndex, out: JsonProducer
    ): StringIndexOption {
      let ch0 = if (sourceText.hasIndex(i)) {
        sourceText[i]
      } else {
        0
      };
      let end = sourceText.end;

      let keyword: String?;
      let n: Int;
      when (ch0) {
        char'f' -> do { keyword = "false"; n = 5 };
        char't' -> do { keyword = "true"; n = 4 };
        else    -> do { keyword = null; n = 0 };
      }

      if (keyword != null) {
        if (sourceText.hasAtLeast(i, end, n)) {
          let after = afterSubstring(sourceText, i, keyword);
          if (after is StringIndex) {
            out.booleanValue(n == 4);
            return after;
          }
        }
      }

      expectedTokenError(sourceText, i, out, "`false` or `true`");
      return StringIndex.none;
    }

    let parseJsonNull(
      sourceText: String, i: StringIndex, out: JsonProducer
    ): StringIndexOption {
      let after = afterSubstring(sourceText, i, "null");

      if (after is StringIndex) {
        out.nullValue();
        return after;
      }

      expectedTokenError(sourceText, i, out, "`null`");
      return StringIndex.none;
    }

    let afterSubstring(
      string: String,
      inString: StringIndex,
      substring: String
    ): StringIndexOption {
      var i = inString;
      var j = String.begin;
      while (substring.hasIndex(j)) {
        if (!string.hasIndex(i)) {
          return StringIndex.none;
        }
        if (string[i] != substring[j]) {
          return StringIndex.none;
        }
        i = string.next(i);
        j = substring.next(j);
      }
      i
    }

As usual, the number grammar is the single largest sub-grammar.
We accumulate an integer portion separately from a decimal portion.
If either of those is past the common representability limits for *Int*
or *Float64*, then we use the lossless numeric syntax tree variant.

    let parseJsonNumber(
      sourceText: String, var i: StringIndex, out: JsonProducer
    ): StringIndexOption { do {
      var isNegative = false;
      let startOfNumber = i;
      if (sourceText.hasIndex(i) && sourceText[i] == char'-') {
        isNegative = true;
        i = sourceText.next(i);
      }

      // Find the whole portion, the portion before any fraction
      // or exponent.
      // 0 | [1-9][0-9]*
      let digit0 = if (sourceText.hasIndex(i)) { sourceText[i] } else { -1 };
      if (digit0 < char'0' || char'9' < digit0) {
        // parseJsonValue comes here for any unrecognized code-points
        let error = if (!isNegative && digit0 != char'.') {
          "JSON value"
        } else {
          "digit"
        };
        expectedTokenError(sourceText, i, out, error);
        return StringIndex.none;
      }
      i = sourceText.next(i);
      var nDigits = 1;
      var tentativeFloat64 = (digit0 - char'0').toFloat64();
      var tentativeInt64 = (digit0 - char'0').toInt64();
      var overflowInt64 = false;
      if (char'0' != digit0) {
        while (sourceText.hasIndex(i)) {
          let possibleDigit = sourceText[i];
          if (char'0' <= possibleDigit && possibleDigit <= char'9') {
            i = sourceText.next(i);
            nDigits += 1;
            let nextDigit = possibleDigit - char'0';
            tentativeFloat64 = tentativeFloat64 * 10.0 + nextDigit.toFloat64();
            let oldInt64 = tentativeInt64;
            tentativeInt64 = tentativeInt64 * 10i64 + nextDigit.toInt64();
            if (tentativeInt64 < oldInt64) {
              // Overflow happened.
              if (
                minInt64 - oldInt64 == -nextDigit.toInt64() &&
                isNegative &&
                oldInt64 > 0i64
              ) {
                // Ok because we landed exactly once on min int64.
              } else {
                overflowInt64 = true;
              }
            }
          } else {
            break;
          }
        }
      }

      // optional fraction component
      // '.' [0-9]+
      var nDigitsAfterPoint = 0;
      if (sourceText.hasIndex(i) && char'.' == sourceText[i]) {
        i = sourceText.next(i);
        let afterPoint = i;
        while (sourceText.hasIndex(i)) {
          let possibleDigit = sourceText[i];
          if (char'0' <= possibleDigit && possibleDigit <= char'9') {
            i = sourceText.next(i);
            nDigits += 1;
            nDigitsAfterPoint += 1;
            tentativeFloat64 = tentativeFloat64 * 10.0 +
                (possibleDigit - char'0').toFloat64();
          } else {
            break;
          }
        }
        if (i == afterPoint) {
          // ECMA-404 does not allow "0." with no digit after the point.
          expectedTokenError(sourceText, i, out, "digit");
          return StringIndex.none;
        }
      }

      // optional exponent
      // [eE] [+\-]? [0-9]+
      var nExponentDigits = 0;
      if (sourceText.hasIndex(i) && char'e' == (sourceText[i] | 32)) {
        i = sourceText.next(i);
        if (!sourceText.hasIndex(i)) {
          expectedTokenError(sourceText, i, out, "sign or digit");
          return StringIndex.none;
        }
        let afterE = sourceText[i];
        if (afterE == char'+' || afterE == char'-') {
          i = sourceText.next(i);
        }
        while (sourceText.hasIndex(i)) {
          let possibleDigit = sourceText[i];
          if (char'0' <= possibleDigit && possibleDigit <= char'9') {
            i = sourceText.next(i);
            nExponentDigits += 1;
          } else {
            break;
          }
        }
        if (nExponentDigits == 0) {
          expectedTokenError(sourceText, i, out, "exponent digit");
          return StringIndex.none;
        }
      }
      let afterExponent = i;

      // TODO Allow exponents within range and/or explicit zero fractions for ints.
      if (nExponentDigits == 0 && nDigitsAfterPoint == 0 && !overflowInt64) {
        // An integer literal.
        let value = if (isNegative) { -tentativeInt64 } else { tentativeInt64 };
        if (-0x8000_0000_i64 <= value && value <= 0x7fff_ffff_i64) {
          // Guaranteed representable int value.
          out.int32Value(value.toInt32Unsafe());
        } else {
          out.int64Value(value);
        }
        return i;
      }

      let numericTokenString = sourceText.slice(startOfNumber, i);
      var doubleValue = NaN;
      if (nExponentDigits != 0 || nDigitsAfterPoint != 0) {
        do {
          doubleValue = numericTokenString.toFloat64();
        } orelse do {
          // Fall back to numeric token below
          ;
        }
      }

      if (doubleValue != -Infinity && doubleValue != Infinity &&
          doubleValue != NaN) {
        out.float64Value(doubleValue);
      } else {
        out.numericTokenValue(numericTokenString);
      }
      return i;
    } orelse panic() } // See https://github.com/temperlang/temper/issues/203

    // TODO Define as `Int64.minValue`.
    let minInt64 = -0x8000_0000_0000_0000_i64;

As a convenience, the *parseJson* helper just creates a tree producer,
calls the parser and gets the tree.

    export let parseJson(sourceText: String): JsonSyntaxTree throws Bubble {
      let p = new JsonSyntaxTreeProducer();
      parseJsonToProducer(sourceText, p);
      // TODO: if there is a syntax error, produce it.
      p.toJsonSyntaxTree()
    }

## Type adapters

Type adapters allow converting values of a type to and from JSON.
See the `@json` type decorator for details which makes sure that
the decorated type has a static method that gets an adapter for the
type.

    export interface JsonAdapter<T> {
      public encodeToJson(x: T, p: JsonProducer): Void;
      public decodeFromJson(t: JsonSyntaxTree, ic: InterchangeContext): T throws Bubble;
    }

Our intrinsic types, like *Boolean* need json adapters.  Static extensions
let us make *Boolean.jsonAdapter()* work as if it were built in.

    class BooleanJsonAdapter extends JsonAdapter<Boolean> {
      public encodeToJson(x: Boolean, p: JsonProducer): Void {
        p.booleanValue(x);
      }
      public decodeFromJson(t: JsonSyntaxTree, ic: InterchangeContext): Boolean throws Bubble {
        (t as JsonBoolean).content
      }
    }

    @staticExtension(Boolean, "jsonAdapter")
    export let booleanJsonAdapter(): JsonAdapter<Boolean> {
      new BooleanJsonAdapter()
    }

    class Float64JsonAdapter extends JsonAdapter<Float64> {
      public encodeToJson(x: Float64, p: JsonProducer): Void {
        p.float64Value(x);
      }
      public decodeFromJson(t: JsonSyntaxTree, ic: InterchangeContext): Float64 throws Bubble {
        (t as JsonNumeric).asFloat64()
      }
    }

    @staticExtension(Float64, "jsonAdapter")
    export let float64JsonAdapter(): JsonAdapter<Float64> {
      new Float64JsonAdapter()
    }

    class Int32JsonAdapter extends JsonAdapter<Int> {
      public encodeToJson(x: Int, p: JsonProducer): Void {
        p.int32Value(x);
      }
      public decodeFromJson(t: JsonSyntaxTree, ic: InterchangeContext): Int throws Bubble {
        (t as JsonNumeric).asInt32()
      }
    }

    @staticExtension(Int, "jsonAdapter")
    export let int32JsonAdapter(): JsonAdapter<Int> {
      new Int32JsonAdapter()
    }

    class Int64JsonAdapter extends JsonAdapter<Int64> {
      public encodeToJson(x: Int64, p: JsonProducer): Void {
        p.int64Value(x);
      }
      public decodeFromJson(t: JsonSyntaxTree, ic: InterchangeContext): Int64 throws Bubble {
        (t as JsonNumeric).asInt64()
      }
    }

    @staticExtension(Int64, "jsonAdapter")
    export let int64JsonAdapter(): JsonAdapter<Int64> {
      new Int64JsonAdapter()
    }

    class StringJsonAdapter extends JsonAdapter<String> {
      public encodeToJson(x: String, p: JsonProducer): Void {
        p.stringValue(x);
      }
      public decodeFromJson(t: JsonSyntaxTree, ic: InterchangeContext): String throws Bubble {
        (t as JsonString).content
      }
    }

    @staticExtension(String, "jsonAdapter")
    export let stringJsonAdapter(): JsonAdapter<String> {
      new StringJsonAdapter()
    }

    class ListJsonAdapter<T>(
      private adapterForT: JsonAdapter<T>,
    ) extends JsonAdapter<List<T>> {
      public encodeToJson(x: List<T>, p: JsonProducer): Void {
        p.startArray();
        for (let el of x) {
          adapterForT.encodeToJson(el, p);
        }
        p.endArray();
      }
      public decodeFromJson(t: JsonSyntaxTree, ic: InterchangeContext): List<T> throws Bubble {
        let b = new ListBuilder<T>();
        let elements = (t as JsonArray).elements;
        let n = elements.length;
        var i = 0;
        while (i < n) {
          let el = elements[i];
          i += 1;
          b.add(adapterForT.decodeFromJson(el, ic));
        }
        b.toList()
      }
    }

    @staticExtension(List, "jsonAdapter")
    export let listJsonAdapter<T>(adapterForT: JsonAdapter<T>): JsonAdapter<List<T>> {
      new ListJsonAdapter<T>(adapterForT)
    }

    export class OrNullJsonAdapter<T>(
      private adapterForT: JsonAdapter<T>,
    ) extends JsonAdapter<T?> {
      public encodeToJson(x: T?, p: JsonProducer): Void {
        if (x == null) {
          p.nullValue();
        } else {
          adapterForT.encodeToJson(x, p);
        }
      }
      public decodeFromJson(t: JsonSyntaxTree, ic: InterchangeContext): T? throws Bubble {
        if (t is JsonNull) {
          null
        } else {
          adapterForT.decodeFromJson(t, ic)
        }
      }
    }

[ECMA-404]: https://ecma-international.org/publications-and-standards/standards/ecma-404/
[content negotiation]: https://developer.mozilla.org/en-US/docs/Web/HTTP/Content_negotiation
[JSON Lines]: https://jsonlines.org/
[scalar value string]: https://infra.spec.whatwg.org/#scalar-value-string
