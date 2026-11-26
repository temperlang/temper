import {
  ParseError,
  panic,
  requireInstanceOf,
  type,
  withCause,
} from "@temperlang/core";
import {
  InterchangeContext,
  JsonAdapter,
  JsonBoolean,
  JsonError,
  JsonNumeric,
  JsonObject,
  JsonProducer,
  JsonShapeError,
  JsonString,
  JsonSyntaxError,
  JsonSyntaxTree,
  JsonTextProducer,
  NullInterchangeContext,
  OrNullJsonAdapter,
  listJsonAdapter,
  parseJson,
  stringJsonAdapter,
} from "@temperlang/std/json";

/**
 * @template T
 * @param {JsonAdapter<T>} adapter
 * @param {T} value
 * @returns {string}
 */
function encode(adapter, value) {
  const p = new JsonTextProducer();
  adapter.encodeToJson(value, /** @type {JsonProducer} */ (/** @type {unknown} */ (p)));
  try {
    return p.toJsonString();
  } catch (e$) {
    if (!(e$ instanceof JsonSyntaxError)) throw e$;
    return "bad JSON";
  }
}

/**
 * @template T
 * @param {JsonAdapter<T>} adapter
 * @param {string} text
 * @returns {T}
 * @throws {JsonError}
 */
function decode(adapter, text) {
  return adapter.decodeFromJson(parseJson(text), /** @type {InterchangeContext} */ (/** @type {unknown} */ (NullInterchangeContext.instance)));
}

/**
 * @template T
 * @param {JsonAdapter<T>} adapter
 * @param {T} value
 */
function roundTrip(adapter, value) {
  const text = encode(adapter, value);
  /** @type {T} */
  let back;
  try {
    back = decode(adapter, text);
  } catch (e$) {
    if (!(e$ instanceof JsonError)) throw e$;
    back = panic("panic", e$);
  }
  const again = encode(adapter, back);
  console.log(again === text ? text : `${text} became ${again}`);
}

class PointJsonAdapter extends type(JsonAdapter) {
  /**
   * @param {Point} x
   * @param {JsonProducer} p
   */
  encodeToJson(x, p) {
    x.encodeToJson(p);
  }

  /**
   * @param {JsonSyntaxTree} t
   * @param {InterchangeContext} ic
   * @returns {Point}
   * @throws {JsonError}
   */
  decodeFromJson(t, ic) {
    return Point.decodeFromJson(t, ic);
  }
}

class Point extends type() {
  /** @type {number} */
  #x;
  /** @type {number} */
  #y;

  /**
   * @param {{ x: number, y: number }} props
   * @returns {Point}
   */
  static new(props) {
    return new Point(props.x, props.y);
  }

  /**
   * @param {number} x
   * @param {number} y
   */
  constructor(x, y) {
    super();
    this.#x = x;
    this.#y = y;
  }

  /** @returns {number} */
  get x() {
    return this.#x;
  }

  /** @returns {number} */
  get y() {
    return this.#y;
  }

  /** @param {JsonProducer} p */
  encodeToJson(p) {
    p.startObject();
    p.objectKey("x");
    p.int32Value(this.#x);
    p.objectKey("y");
    p.int32Value(this.#y);
    p.endObject();
  }

  /**
   * @param {JsonSyntaxTree} t
   * @param {InterchangeContext} ic
   * @returns {Point}
   * @throws {JsonError}
   */
  static decodeFromJson(t, ic) {
    const obj = t instanceof JsonObject ? /** @type {JsonObject} */ (/** @type {unknown} */ (t)) : (() => { throw new JsonShapeError("expected Point JSON object"); })();
    const xTree = obj.propertyValueOrBubble("x");
    const xJson = xTree instanceof JsonNumeric ? /** @type {JsonNumeric} */ (/** @type {unknown} */ (xTree)) : (() => { throw new JsonShapeError("expected Int for x"); })();
    /** @type {number} */
    let x;
    try {
      x = xJson.asInt32();
    } catch (failure) {
      if (!(failure instanceof ParseError)) throw failure;
      throw withCause(new JsonShapeError("expected Int for x"), failure);
    }
    const yTree = obj.propertyValueOrBubble("y");
    const yJson = yTree instanceof JsonNumeric ? /** @type {JsonNumeric} */ (/** @type {unknown} */ (yTree)) : (() => { throw new JsonShapeError("expected Int for y"); })();
    /** @type {number} */
    let y;
    try {
      y = yJson.asInt32();
    } catch (failure2) {
      if (!(failure2 instanceof ParseError)) throw failure2;
      throw withCause(new JsonShapeError("expected Int for y"), failure2);
    }
    return new Point(x, y);
  }

  /** @returns {JsonAdapter<Point>} */
  static jsonAdapter() {
    return /** @type {JsonAdapter<Point>} */ (/** @type {unknown} */ (new PointJsonAdapter()));
  }
}

class RecordJsonAdapter extends type(JsonAdapter) {
  /**
   * @param {Record} x
   * @param {JsonProducer} p
   */
  encodeToJson(x, p) {
    x.encodeToJson(p);
  }

  /**
   * @param {JsonSyntaxTree} t
   * @param {InterchangeContext} ic
   * @returns {Record}
   * @throws {JsonError}
   */
  decodeFromJson(t, ic) {
    return Record.decodeFromJson(t, ic);
  }
}

class Record extends type() {
  /** @type {string} */
  #name;
  /** @type {boolean} */
  #active;
  /** @type {number} */
  #score;
  /** @type {bigint} */
  #big;
  /** @type {string | null} */
  #nickname;
  /** @type {readonly Point[]} */
  #points;

  /**
   * @param {{ name: string, active: boolean, score: number, big: bigint, nickname: string | null, points: readonly Point[] }} props
   * @returns {Record}
   */
  static new(props) {
    return new Record(props.name, props.active, props.score, props.big, props.nickname, props.points);
  }

  /**
   * @param {string} name
   * @param {boolean} active
   * @param {number} score
   * @param {bigint} big
   * @param {string | null} nickname
   * @param {readonly Point[]} points
   */
  constructor(name, active, score, big, nickname, points) {
    super();
    this.#name = name;
    this.#active = active;
    this.#score = score;
    this.#big = big;
    this.#nickname = nickname;
    this.#points = points;
  }

  /** @returns {string} */
  get name() {
    return this.#name;
  }

  /** @returns {boolean} */
  get active() {
    return this.#active;
  }

  /** @returns {number} */
  get score() {
    return this.#score;
  }

  /** @returns {bigint} */
  get big() {
    return this.#big;
  }

  /** @returns {string | null} */
  get nickname() {
    return this.#nickname;
  }

  /** @returns {readonly Point[]} */
  get points() {
    return this.#points;
  }

  /** @param {JsonProducer} p */
  encodeToJson(p) {
    p.startObject();
    p.objectKey("name");
    p.stringValue(this.#name);
    p.objectKey("is-active");
    p.booleanValue(this.#active);
    p.objectKey("score");
    p.float64Value(this.#score);
    p.objectKey("big");
    p.int64Value(this.#big);
    p.objectKey("nickname");
    new OrNullJsonAdapter(stringJsonAdapter()).encodeToJson(this.#nickname, p);
    p.objectKey("points");
    listJsonAdapter(Point.jsonAdapter()).encodeToJson(this.#points, p);
    p.endObject();
  }

  /**
   * @param {JsonSyntaxTree} t
   * @param {InterchangeContext} ic
   * @returns {Record}
   * @throws {JsonError}
   */
  static decodeFromJson(t, ic) {
    const obj = t instanceof JsonObject ? /** @type {JsonObject} */ (/** @type {unknown} */ (t)) : (() => { throw new JsonShapeError("expected Record JSON object"); })();
    const nameTree = obj.propertyValueOrBubble("name");
    const nameJson = nameTree instanceof JsonString ? /** @type {JsonString} */ (/** @type {unknown} */ (nameTree)) : (() => { throw new JsonShapeError("expected String for name"); })();
    const name = nameJson.content;
    const activeTree = obj.propertyValueOrBubble("is-active");
    const activeJson = activeTree instanceof JsonBoolean ? /** @type {JsonBoolean} */ (/** @type {unknown} */ (activeTree)) : (() => { throw new JsonShapeError("expected Boolean for is-active"); })();
    const active = activeJson.content;
    const scoreTree = obj.propertyValueOrBubble("score");
    const scoreJson = scoreTree instanceof JsonNumeric ? /** @type {JsonNumeric} */ (/** @type {unknown} */ (scoreTree)) : (() => { throw new JsonShapeError("expected Float64 for score"); })();
    /** @type {number} */
    let score;
    try {
      score = scoreJson.asFloat64();
    } catch (failure) {
      if (!(failure instanceof ParseError)) throw failure;
      throw withCause(new JsonShapeError("expected Float64 for score"), failure);
    }
    const bigTree = obj.propertyValueOrBubble("big");
    const bigJson = bigTree instanceof JsonNumeric ? /** @type {JsonNumeric} */ (/** @type {unknown} */ (bigTree)) : (() => { throw new JsonShapeError("expected Int64 for big"); })();
    /** @type {bigint} */
    let big;
    try {
      big = bigJson.asInt64();
    } catch (failure2) {
      if (!(failure2 instanceof ParseError)) throw failure2;
      throw withCause(new JsonShapeError("expected Int64 for big"), failure2);
    }
    const nickname = new OrNullJsonAdapter(stringJsonAdapter()).decodeFromJson(obj.propertyValueOrBubble("nickname"), ic);
    /** @type {readonly Point[]} */
    const points = listJsonAdapter(Point.jsonAdapter()).decodeFromJson(obj.propertyValueOrBubble("points"), ic);
    return new Record(name, active, score, big, nickname, points);
  }

  /** @returns {JsonAdapter<Record>} */
  static jsonAdapter() {
    return /** @type {JsonAdapter<Record>} */ (/** @type {unknown} */ (new RecordJsonAdapter()));
  }
}

class CircleJsonAdapter extends type(JsonAdapter) {
  /**
   * @param {Circle} x
   * @param {JsonProducer} p
   */
  encodeToJson(x, p) {
    x.encodeToJson(p);
  }

  /**
   * @param {JsonSyntaxTree} t
   * @param {InterchangeContext} ic
   * @returns {Circle}
   * @throws {JsonError}
   */
  decodeFromJson(t, ic) {
    return Circle.decodeFromJson(t, ic);
  }
}

class Shape extends type() {
  /** @type {(p: JsonProducer) => void} */
  encodeToJson = /** @type {any} */ (undefined);

  /**
   * @param {JsonSyntaxTree} t
   * @param {InterchangeContext} ic
   * @returns {Shape}
   * @throws {JsonError}
   */
  static decodeFromJson(t, ic) {
    {
      const obj = t instanceof JsonObject ? /** @type {JsonObject} */ (/** @type {unknown} */ (t)) : (() => { throw new JsonShapeError("expected Shape JSON object"); })();
      const valueForRadius = obj.propertyValueOrNull("radius");
      {
        if (valueForRadius != null) {
          return Circle.jsonAdapter().decodeFromJson(/** @type {JsonSyntaxTree} */ (/** @type {unknown} */ (obj)), ic);
        }
        if (valueForRadius == null) {
          return Rect.jsonAdapter().decodeFromJson(/** @type {JsonSyntaxTree} */ (/** @type {unknown} */ (obj)), ic);
        }
      }
      throw new JsonShapeError("expected one of Shape's JSON shapes");
    }
  }

  /** @returns {JsonAdapter<Shape>} */
  static jsonAdapter() {
    return /** @type {JsonAdapter<Shape>} */ (/** @type {unknown} */ (new ShapeJsonAdapter()));
  }
}

class Circle extends type(Shape) {
  /** @type {number} */
  #radius;

  /** @param {number} radius */
  constructor(radius) {
    super();
    this.#radius = radius;
  }

  /** @returns {number} */
  get radius() {
    return this.#radius;
  }

  /** @param {JsonProducer} p */
  encodeToJson(p) {
    p.startObject();
    p.objectKey("radius");
    p.int32Value(this.#radius);
    p.endObject();
  }

  /**
   * @param {JsonSyntaxTree} t
   * @param {InterchangeContext} ic
   * @returns {Circle}
   * @throws {JsonError}
   */
  static decodeFromJson(t, ic) {
    const obj = t instanceof JsonObject ? /** @type {JsonObject} */ (/** @type {unknown} */ (t)) : (() => { throw new JsonShapeError("expected Circle JSON object"); })();
    const radiusTree = obj.propertyValueOrBubble("radius");
    const radiusJson = radiusTree instanceof JsonNumeric ? /** @type {JsonNumeric} */ (/** @type {unknown} */ (radiusTree)) : (() => { throw new JsonShapeError("expected Int for radius"); })();
    /** @type {number} */
    let radius;
    try {
      radius = radiusJson.asInt32();
    } catch (failure) {
      if (!(failure instanceof ParseError)) throw failure;
      throw withCause(new JsonShapeError("expected Int for radius"), failure);
    }
    return new Circle(radius);
  }

  /** @returns {JsonAdapter<Circle>} */
  static jsonAdapter() {
    return /** @type {JsonAdapter<Circle>} */ (/** @type {unknown} */ (new CircleJsonAdapter()));
  }
}

class RectJsonAdapter extends type(JsonAdapter) {
  /**
   * @param {Rect} x
   * @param {JsonProducer} p
   */
  encodeToJson(x, p) {
    x.encodeToJson(p);
  }

  /**
   * @param {JsonSyntaxTree} t
   * @param {InterchangeContext} ic
   * @returns {Rect}
   * @throws {JsonError}
   */
  decodeFromJson(t, ic) {
    return Rect.decodeFromJson(t, ic);
  }
}

class Rect extends type(Shape) {
  /** @type {number} */
  #width;
  /** @type {number} */
  #height;

  /**
   * @param {{ width: number, height: number }} props
   * @returns {Rect}
   */
  static new(props) {
    return new Rect(props.width, props.height);
  }

  /**
   * @param {number} width
   * @param {number} height
   */
  constructor(width, height) {
    super();
    this.#width = width;
    this.#height = height;
  }

  /** @returns {number} */
  get width() {
    return this.#width;
  }

  /** @returns {number} */
  get height() {
    return this.#height;
  }

  /** @param {JsonProducer} p */
  encodeToJson(p) {
    p.startObject();
    p.objectKey("width");
    p.int32Value(this.#width);
    p.objectKey("height");
    p.int32Value(this.#height);
    p.endObject();
  }

  /**
   * @param {JsonSyntaxTree} t
   * @param {InterchangeContext} ic
   * @returns {Rect}
   * @throws {JsonError}
   */
  static decodeFromJson(t, ic) {
    const obj = t instanceof JsonObject ? /** @type {JsonObject} */ (/** @type {unknown} */ (t)) : (() => { throw new JsonShapeError("expected Rect JSON object"); })();
    const widthTree = obj.propertyValueOrBubble("width");
    const widthJson = widthTree instanceof JsonNumeric ? /** @type {JsonNumeric} */ (/** @type {unknown} */ (widthTree)) : (() => { throw new JsonShapeError("expected Int for width"); })();
    /** @type {number} */
    let width;
    try {
      width = widthJson.asInt32();
    } catch (failure) {
      if (!(failure instanceof ParseError)) throw failure;
      throw withCause(new JsonShapeError("expected Int for width"), failure);
    }
    const heightTree = obj.propertyValueOrBubble("height");
    const heightJson = heightTree instanceof JsonNumeric ? /** @type {JsonNumeric} */ (/** @type {unknown} */ (heightTree)) : (() => { throw new JsonShapeError("expected Int for height"); })();
    /** @type {number} */
    let height;
    try {
      height = heightJson.asInt32();
    } catch (failure2) {
      if (!(failure2 instanceof ParseError)) throw failure2;
      throw withCause(new JsonShapeError("expected Int for height"), failure2);
    }
    return new Rect(width, height);
  }

  /** @returns {JsonAdapter<Rect>} */
  static jsonAdapter() {
    return /** @type {JsonAdapter<Rect>} */ (/** @type {unknown} */ (new RectJsonAdapter()));
  }
}

class ShapeJsonAdapter extends type(JsonAdapter) {
  /**
   * @param {Shape} x
   * @param {JsonProducer} p
   */
  encodeToJson(x, p) {
    x.encodeToJson(p);
  }

  /**
   * @param {JsonSyntaxTree} t
   * @param {InterchangeContext} ic
   * @returns {Shape}
   * @throws {JsonError}
   */
  decodeFromJson(t, ic) {
    return Shape.decodeFromJson(t, ic);
  }
}

class HelloJsonAdapter extends type(JsonAdapter) {
  /**
   * @param {Hello} x
   * @param {JsonProducer} p
   */
  encodeToJson(x, p) {
    x.encodeToJson(p);
  }

  /**
   * @param {JsonSyntaxTree} t
   * @param {InterchangeContext} ic
   * @returns {Hello}
   * @throws {JsonError}
   */
  decodeFromJson(t, ic) {
    return Hello.decodeFromJson(t, ic);
  }
}

class Message extends type() {
  /** @type {(p: JsonProducer) => void} */
  encodeToJson = /** @type {any} */ (undefined);

  /**
   * @param {JsonSyntaxTree} t
   * @param {InterchangeContext} ic
   * @returns {Message}
   * @throws {JsonError}
   */
  static decodeFromJson(t, ic) {
    {
      const obj = t instanceof JsonObject ? /** @type {JsonObject} */ (/** @type {unknown} */ (t)) : (() => { throw new JsonShapeError("expected Message JSON object"); })();
      const valueForKind = obj.propertyValueOrNull("kind");
      {
        if (valueForKind != null && valueForKind instanceof JsonString) {
          const subject = /** @type {JsonString} */ (/** @type {unknown} */ (valueForKind)).content;
          if (subject === "hello") {
            return Hello.jsonAdapter().decodeFromJson(/** @type {JsonSyntaxTree} */ (/** @type {unknown} */ (obj)), ic);
          } else if (subject === "bye") {
            return Bye.jsonAdapter().decodeFromJson(/** @type {JsonSyntaxTree} */ (/** @type {unknown} */ (obj)), ic);
          }
        }
      }
      throw new JsonShapeError("expected one of Message's JSON shapes");
    }
  }

  /** @returns {JsonAdapter<Message>} */
  static jsonAdapter() {
    return /** @type {JsonAdapter<Message>} */ (/** @type {unknown} */ (new MessageJsonAdapter()));
  }
}

class Hello extends type(Message) {
  /** @type {string} */
  #text;

  /** @param {string} text */
  constructor(text) {
    super();
    this.#text = text;
  }

  /** @returns {string} */
  get text() {
    return this.#text;
  }

  /** @param {JsonProducer} p */
  encodeToJson(p) {
    p.startObject();
    p.objectKey("text");
    p.stringValue(this.#text);
    p.objectKey("kind");
    p.stringValue("hello");
    p.endObject();
  }

  /**
   * @param {JsonSyntaxTree} t
   * @param {InterchangeContext} ic
   * @returns {Hello}
   * @throws {JsonError}
   */
  static decodeFromJson(t, ic) {
    const obj = t instanceof JsonObject ? /** @type {JsonObject} */ (/** @type {unknown} */ (t)) : (() => { throw new JsonShapeError("expected Hello JSON object"); })();
    const textTree = obj.propertyValueOrBubble("text");
    const textJson = textTree instanceof JsonString ? /** @type {JsonString} */ (/** @type {unknown} */ (textTree)) : (() => { throw new JsonShapeError("expected String for text"); })();
    const text = textJson.content;
    return new Hello(text);
  }

  /** @returns {JsonAdapter<Hello>} */
  static jsonAdapter() {
    return /** @type {JsonAdapter<Hello>} */ (/** @type {unknown} */ (new HelloJsonAdapter()));
  }
}

class ByeJsonAdapter extends type(JsonAdapter) {
  /**
   * @param {Bye} x
   * @param {JsonProducer} p
   */
  encodeToJson(x, p) {
    x.encodeToJson(p);
  }

  /**
   * @param {JsonSyntaxTree} t
   * @param {InterchangeContext} ic
   * @returns {Bye}
   * @throws {JsonError}
   */
  decodeFromJson(t, ic) {
    return Bye.decodeFromJson(t, ic);
  }
}

class Bye extends type(Message) {
  /** @type {string} */
  #text;

  /** @param {string} text */
  constructor(text) {
    super();
    this.#text = text;
  }

  /** @returns {string} */
  get text() {
    return this.#text;
  }

  /** @param {JsonProducer} p */
  encodeToJson(p) {
    p.startObject();
    p.objectKey("text");
    p.stringValue(this.#text);
    p.objectKey("kind");
    p.stringValue("bye");
    p.endObject();
  }

  /**
   * @param {JsonSyntaxTree} t
   * @param {InterchangeContext} ic
   * @returns {Bye}
   * @throws {JsonError}
   */
  static decodeFromJson(t, ic) {
    const obj = t instanceof JsonObject ? /** @type {JsonObject} */ (/** @type {unknown} */ (t)) : (() => { throw new JsonShapeError("expected Bye JSON object"); })();
    const textTree = obj.propertyValueOrBubble("text");
    const textJson = textTree instanceof JsonString ? /** @type {JsonString} */ (/** @type {unknown} */ (textTree)) : (() => { throw new JsonShapeError("expected String for text"); })();
    const text = textJson.content;
    return new Bye(text);
  }

  /** @returns {JsonAdapter<Bye>} */
  static jsonAdapter() {
    return /** @type {JsonAdapter<Bye>} */ (/** @type {unknown} */ (new ByeJsonAdapter()));
  }
}

class MessageJsonAdapter extends type(JsonAdapter) {
  /**
   * @param {Message} x
   * @param {JsonProducer} p
   */
  encodeToJson(x, p) {
    x.encodeToJson(p);
  }

  /**
   * @param {JsonSyntaxTree} t
   * @param {InterchangeContext} ic
   * @returns {Message}
   * @throws {JsonError}
   */
  decodeFromJson(t, ic) {
    return Message.decodeFromJson(t, ic);
  }
}

class CelsiusJsonAdapter extends type(JsonAdapter) {
  /**
   * @param {Celsius} x
   * @param {JsonProducer} p
   */
  encodeToJson(x, p) {
    x.encodeToJson(p);
  }

  /**
   * @param {JsonSyntaxTree} t
   * @param {InterchangeContext} ic
   * @returns {Celsius}
   * @throws {JsonError}
   */
  decodeFromJson(t, ic) {
    return Celsius.decodeFromJson(t, ic);
  }
}

class Celsius extends type() {
  /** @type {number} */
  #degrees;

  /** @param {number} degrees */
  constructor(degrees) {
    super();
    this.#degrees = degrees;
  }

  /** @returns {number} */
  get degrees() {
    return this.#degrees;
  }

  /** @param {JsonProducer} p */
  encodeToJson(p) {
    p.int32Value(this.#degrees);
  }

  /**
   * @param {JsonSyntaxTree} t
   * @param {InterchangeContext} ic
   * @returns {Celsius}
   * @throws {JsonError}
   */
  static decodeFromJson(t, ic) {
    /** @type {number} */
    let t$0;
    try {
      t$0 = requireInstanceOf(t, JsonNumeric).asInt32();
    } catch (failure) {
      if (!(failure instanceof ParseError)) throw failure;
      throw withCause(new JsonShapeError("expected degrees"), failure);
    }
    return new Celsius(t$0);
  }

  /** @returns {JsonAdapter<Celsius>} */
  static jsonAdapter() {
    return /** @type {JsonAdapter<Celsius>} */ (/** @type {unknown} */ (new CelsiusJsonAdapter()));
  }
}

roundTrip(Point.jsonAdapter(), new Point(1, -2));
roundTrip(Record.jsonAdapter(), new Record("Ada", true, 1.5, 123456789012n, null, Object.freeze([new Point(3, 4)])));
/** @type {readonly Shape[]} */
const shapes = Object.freeze([new Circle(2), new Rect(3, 4)]);
roundTrip(listJsonAdapter(Shape.jsonAdapter()), shapes);
/** @type {readonly Message[]} */
const messages = Object.freeze([new Hello("hi"), new Bye("so long")]);
roundTrip(listJsonAdapter(Message.jsonAdapter()), messages);
roundTrip(Celsius.jsonAdapter(), new Celsius(21));
/** @type {Shape} */
let shape;
try {
  shape = decode(Shape.jsonAdapter(), '{"height": 6, "width": 5}');
} catch (e$) {
  if (!(e$ instanceof JsonError)) throw e$;
  shape = panic("panic", e$);
}
{
  if (shape instanceof Rect) {
    console.log(`rect ${/** @type {Rect} */ (/** @type {unknown} */ (shape)).width}x${/** @type {Rect} */ (/** @type {unknown} */ (shape)).height}`);
  } else {
    console.log("not a rect");
  }
}
/** @type {Record} */
let record;
try {
  record = decode(Record.jsonAdapter(), '{"name": "Bo", "is-active": false, "score": 2, "big": 7, "nickname": "B", "points": []}');
} catch (e$) {
  if (!(e$ instanceof JsonError)) throw e$;
  record = panic("panic", e$);
}
console.log(`${record.name} ${record.active} ${record.nickname ?? "none"} ${record.points.length}`);
/** @type {string} */
let t$1;
try {
  decode(Point.jsonAdapter(), '{"x": 1}');
  t$1 = "decoded";
} catch (e) {
  if (!(e instanceof JsonError)) throw e;
  t$1 = /** @type {Error} */ (/** @type {unknown} */ (e)).message;
}
console.log(t$1);
/** @type {string} */
let t$2;
try {
  decode(Message.jsonAdapter(), '{"kind": "what", "text": "?"}');
  t$2 = "decoded";
} catch (e2) {
  if (!(e2 instanceof JsonError)) throw e2;
  t$2 = /** @type {Error} */ (/** @type {unknown} */ (e2)).message;
}
console.log(t$2);
