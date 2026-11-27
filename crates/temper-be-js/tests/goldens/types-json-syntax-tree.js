import { listedGet, requireInstanceOf } from "@temperlang/core";
import {
  InterchangeContext,
  JsonArray,
  JsonError,
  JsonNull,
  JsonNumeric,
  JsonObject,
  JsonProducer,
  JsonSyntaxError,
  JsonSyntaxTree,
  JsonSyntaxTreeProducer,
  JsonTextProducer,
  NullInterchangeContext,
  OrNullJsonAdapter,
  int32JsonAdapter,
  int64JsonAdapter,
  parseJson,
  parseJsonToProducer,
} from "@temperlang/std/json";

{
  /** @type {readonly string[]} */
  const list = Object.freeze(["[]", " [] ", " [ ] ", "[1]", "null"]);
  const length = list.length;
  let i = 0;
  while (i < length) {
    const s = listedGet(/** @type {string[]} */ (list), i);
    i = (i + 1) | 0;
    /** @type {JsonSyntaxTree | null} */
    let jsonValue;
    try {
      jsonValue = parseJson(s);
    } catch (e$) {
      if (!(e$ instanceof JsonSyntaxError)) throw e$;
      jsonValue = null;
    }
    console.log(`\`${s}\` is empty array: ${jsonValue != null && jsonValue instanceof JsonArray && !/** @type {JsonArray} */ (/** @type {unknown} */ (jsonValue)).elements.length}`);
  }
}
{
  /** @type {readonly string[]} */
  const list2 = Object.freeze(["[", "]", "[,]", "[]]", "[1,]", "[1,,2]"]);
  const length2 = list2.length;
  let i2 = 0;
  while (i2 < length2) {
    const s2 = listedGet(/** @type {string[]} */ (list2), i2);
    i2 = (i2 + 1) | 0;
    let failed = false;
    const p = new JsonSyntaxTreeProducer();
    parseJsonToProducer(s2, /** @type {JsonProducer} */ (/** @type {unknown} */ (p)));
    try {
      p.toJsonSyntaxTree();
    } catch (e$) {
      if (!(e$ instanceof JsonSyntaxError)) throw e$;
      failed = true;
    }
    const error = p.jsonError;
    console.log(`\`${s2}\` has${failed ? "" : " no"} error: ${error == null ? "MISSING ERROR" : error}`);
  }
}
{
  /** @type {readonly string[]} */
  const list3 = Object.freeze(["[1,]", '"a"', "7"]);
  const length3 = list3.length;
  let i3 = 0;
  while (i3 < length3) {
    const s3 = listedGet(/** @type {string[]} */ (list3), i3);
    i3 = (i3 + 1) | 0;
    /** @type {string} */
    let decoded;
    try {
      const tree = parseJson(s3);
      decoded = int32JsonAdapter().decodeFromJson(tree, /** @type {InterchangeContext} */ (/** @type {unknown} */ (NullInterchangeContext.instance))).toString();
    } catch (e) {
      if (!(e instanceof JsonError)) throw e;
      decoded = /** @type {Error} */ (/** @type {unknown} */ (e)).message;
    }
    console.log(decoded);
  }
}
const selectCodePoints = "\x00\x01\x02\x03\x04\x05\x06\x07\x08\t\n\x0b\x0c\r\x0e\x0f\x10\x11\x12\x13\x14\x15\x16\x17\x18\x19\x1a\x1b\x1c\x1d\x1e\x1f -𐀀--퟿\u2028\u2029\x85";
{
  const p2 = new JsonTextProducer(/** @type {InterchangeContext} */ (/** @type {unknown} */ (NullInterchangeContext.instance)));
  p2.stringValue(selectCodePoints);
  /** @type {string} */
  let t$0;
  try {
    t$0 = p2.toJsonString();
  } catch (e$) {
    if (!(e$ instanceof JsonSyntaxError)) throw e$;
    t$0 = "?!";
  }
  console.log(`string with select code points: ${t$0}`);
}
{
  const p3 = new JsonTextProducer(/** @type {InterchangeContext} */ (/** @type {unknown} */ (NullInterchangeContext.instance)));
  p3.startObject();
  /** @type {OrNullJsonAdapter<number>} */
  const intOrNullAdapter = new OrNullJsonAdapter(int32JsonAdapter());
  /** @type {number | null} */
  const x = null;
  const y = 123;
  p3.objectKey("x");
  intOrNullAdapter.encodeToJson(x, /** @type {JsonProducer} */ (/** @type {unknown} */ (p3)));
  p3.objectKey("y");
  intOrNullAdapter.encodeToJson(y, /** @type {JsonProducer} */ (/** @type {unknown} */ (p3)));
  p3.endObject();
  console.log(p3.toJsonString());
}
{
  const s4 = '{"x":null,"y":123,"z":123456789012}';
  const ast = parseJson(s4);
  const obj = requireInstanceOf(ast, JsonObject);
  const xAst = obj.propertyValueOrThrow("x");
  const yAst = obj.propertyValueOrThrow("y");
  console.log(`for x, got JSON null: ${xAst instanceof JsonNull}`);
  console.log(`for y, got JSON numeric: ${yAst instanceof JsonNumeric}`);
  /** @type {OrNullJsonAdapter<number>} */
  const intOrNullAdapter2 = new OrNullJsonAdapter(int32JsonAdapter());
  const x2 = intOrNullAdapter2.decodeFromJson(xAst, /** @type {InterchangeContext} */ (/** @type {unknown} */ (NullInterchangeContext.instance)));
  const y2 = intOrNullAdapter2.decodeFromJson(yAst, /** @type {InterchangeContext} */ (/** @type {unknown} */ (NullInterchangeContext.instance)));
  console.log(`x ?? -1 -> ${x2 == null ? -1 : x2}`);
  console.log(`y ?? -1 -> ${y2 == null ? -1 : y2}`);
  const zAst = obj.propertyValueOrThrow("z");
  /** @type {OrNullJsonAdapter<bigint>} */
  const int64OrNullAdapter = new OrNullJsonAdapter(int64JsonAdapter());
  const z = int64OrNullAdapter.decodeFromJson(zAst, /** @type {InterchangeContext} */ (/** @type {unknown} */ (NullInterchangeContext.instance)));
  console.log(`z ?? -1 -> ${(z == null ? -1n : z).toString()}`);
}
const normalJson = new JsonTextProducer();
parseJsonToProducer('{\n  "foo": "bar",\n  "baz": [\n    "boo",\n    { "f": 1, "a": null, "r": 123.0 }\n  ]\n}\n', /** @type {JsonProducer} */ (/** @type {unknown} */ (normalJson)));
console.log("Normalized:");
console.log(normalJson.toJsonString());
