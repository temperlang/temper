local temper = require("temper-core")
local int64_123456789012 = temper.int64_constructor(28, 3197704724)
local JsonAdapter = require("std/json").JsonAdapter
local JsonProducer = require("std/json").JsonProducer
local JsonSyntaxTree = require("std/json").JsonSyntaxTree
local InterchangeContext = require("std/json").InterchangeContext
local JsonObject = require("std/json").JsonObject
local JsonNumeric = require("std/json").JsonNumeric
local OrNullJsonAdapter = require("std/json").OrNullJsonAdapter
local stringJsonAdapter = require("std/json").stringJsonAdapter
local listJsonAdapter = require("std/json").listJsonAdapter
local JsonString = require("std/json").JsonString
local JsonBoolean = require("std/json").JsonBoolean
local JsonTextProducer = require("std/json").JsonTextProducer
local parseJson = require("std/json").parseJson
local NullInterchangeContext = require("std/json").NullInterchangeContext

local Point, Record, Circle, Rect, ShapeJsonAdapter, Hello, Bye, MessageJsonAdapter, Celsius

---@class json-adapters.PointJsonAdapter: std.json.JsonAdapter
---@field encodeToJson fun(self: json-adapters.PointJsonAdapter, x: json-adapters.Point, p: std.json.JsonProducer)
---@field decodeFromJson fun(self: json-adapters.PointJsonAdapter, t: std.json.JsonSyntaxTree, ic: std.json.InterchangeContext): json-adapters.Point

---@class json-adapters.PointJsonAdapter.type: temper.Type
---@operator call: json-adapters.PointJsonAdapter
local PointJsonAdapter = temper.type("json-adapters.PointJsonAdapter", JsonAdapter)

---@param this json-adapters.PointJsonAdapter
function PointJsonAdapter.constructor(this)
end

---@param this json-adapters.PointJsonAdapter
---@param x json-adapters.Point
---@param p std.json.JsonProducer
function PointJsonAdapter.methods.encodeToJson(this, x, p)
  x:encodeToJson(p)
end

---@param this json-adapters.PointJsonAdapter
---@param t std.json.JsonSyntaxTree
---@param ic std.json.InterchangeContext
---@return json-adapters.Point
function PointJsonAdapter.methods.decodeFromJson(this, t, ic)
  return Point.decodeFromJson(t, ic)
end

---@class json-adapters.Point
---@field x integer
---@field y integer
---@field encodeToJson fun(self: json-adapters.Point, p: std.json.JsonProducer)

---@class json-adapters.Point.type: temper.Type
---@operator call: json-adapters.Point
Point = temper.type("json-adapters.Point")

---@param this json-adapters.Point
---@param x integer
---@param y integer
function Point.constructor(this, x, y)
  this.x = x
  this.y = y
end

---@param this json-adapters.Point
---@param p std.json.JsonProducer
function Point.methods.encodeToJson(this, p)
  p:startObject()
  p:objectKey("x")
  p:int32Value(this.x)
  p:objectKey("y")
  p:int32Value(this.y)
  p:endObject()
end

---@param t std.json.JsonSyntaxTree
---@param ic std.json.InterchangeContext
---@return json-adapters.Point
function Point.decodeFromJson(t, ic)
  local obj = temper.cast_to(t, JsonObject)
  local xJson = temper.cast_to(obj:propertyValueOrBubble("x"), JsonNumeric)
  local x = xJson:asInt32()
  local yJson = temper.cast_to(obj:propertyValueOrBubble("y"), JsonNumeric)
  local y = yJson:asInt32()
  return Point(x, y)
end

---@return std.json.JsonAdapter
function Point.jsonAdapter()
  return PointJsonAdapter()
end

---@class json-adapters.RecordJsonAdapter: std.json.JsonAdapter
---@field encodeToJson fun(self: json-adapters.RecordJsonAdapter, x: json-adapters.Record, p: std.json.JsonProducer)
---@field decodeFromJson fun(self: json-adapters.RecordJsonAdapter, t: std.json.JsonSyntaxTree, ic: std.json.InterchangeContext): json-adapters.Record

---@class json-adapters.RecordJsonAdapter.type: temper.Type
---@operator call: json-adapters.RecordJsonAdapter
local RecordJsonAdapter = temper.type("json-adapters.RecordJsonAdapter", JsonAdapter)

---@param this json-adapters.RecordJsonAdapter
function RecordJsonAdapter.constructor(this)
end

---@param this json-adapters.RecordJsonAdapter
---@param x json-adapters.Record
---@param p std.json.JsonProducer
function RecordJsonAdapter.methods.encodeToJson(this, x, p)
  x:encodeToJson(p)
end

---@param this json-adapters.RecordJsonAdapter
---@param t std.json.JsonSyntaxTree
---@param ic std.json.InterchangeContext
---@return json-adapters.Record
function RecordJsonAdapter.methods.decodeFromJson(this, t, ic)
  return Record.decodeFromJson(t, ic)
end

---@class json-adapters.Record
---@field name string
---@field active boolean
---@field score number
---@field big integer
---@field nickname string|temper.Null
---@field points json-adapters.Point[]
---@field encodeToJson fun(self: json-adapters.Record, p: std.json.JsonProducer)

---@class json-adapters.Record.type: temper.Type
---@operator call: json-adapters.Record
Record = temper.type("json-adapters.Record")

---@param this json-adapters.Record
---@param name string
---@param active boolean
---@param score number
---@param big integer
---@param nickname string|temper.Null
---@param points json-adapters.Point[]
function Record.constructor(this, name, active, score, big, nickname, points)
  this.name = name
  this.active = active
  this.score = score
  this.big = big
  this.nickname = nickname
  this.points = points
end

---@param this json-adapters.Record
---@param p std.json.JsonProducer
function Record.methods.encodeToJson(this, p)
  p:startObject()
  p:objectKey("name")
  p:stringValue(this.name)
  p:objectKey("is-active")
  p:booleanValue(this.active)
  p:objectKey("score")
  p:float64Value(this.score)
  p:objectKey("big")
  p:int64Value(this.big)
  p:objectKey("nickname")
  OrNullJsonAdapter(stringJsonAdapter()):encodeToJson(this.nickname, p)
  p:objectKey("points")
  listJsonAdapter(Point.jsonAdapter()):encodeToJson(this.points, p)
  p:endObject()
end

---@param t std.json.JsonSyntaxTree
---@param ic std.json.InterchangeContext
---@return json-adapters.Record
function Record.decodeFromJson(t, ic)
  local obj = temper.cast_to(t, JsonObject)
  local nameJson = temper.cast_to(obj:propertyValueOrBubble("name"), JsonString)
  local name = nameJson.content
  local activeJson = temper.cast_to(obj:propertyValueOrBubble("is-active"), JsonBoolean)
  local active = activeJson.content
  local scoreJson = temper.cast_to(obj:propertyValueOrBubble("score"), JsonNumeric)
  local score = scoreJson:asFloat64()
  local bigJson = temper.cast_to(obj:propertyValueOrBubble("big"), JsonNumeric)
  local big = bigJson:asInt64()
  local nickname = OrNullJsonAdapter(stringJsonAdapter()):decodeFromJson(obj:propertyValueOrBubble("nickname"), ic)
  local points = listJsonAdapter(Point.jsonAdapter()):decodeFromJson(obj:propertyValueOrBubble("points"), ic)
  return Record(name, active, score, big, nickname, points)
end

---@return std.json.JsonAdapter
function Record.jsonAdapter()
  return RecordJsonAdapter()
end

---@class json-adapters.CircleJsonAdapter: std.json.JsonAdapter
---@field encodeToJson fun(self: json-adapters.CircleJsonAdapter, x: json-adapters.Circle, p: std.json.JsonProducer)
---@field decodeFromJson fun(self: json-adapters.CircleJsonAdapter, t: std.json.JsonSyntaxTree, ic: std.json.InterchangeContext): json-adapters.Circle

---@class json-adapters.CircleJsonAdapter.type: temper.Type
---@operator call: json-adapters.CircleJsonAdapter
local CircleJsonAdapter = temper.type("json-adapters.CircleJsonAdapter", JsonAdapter)

---@param this json-adapters.CircleJsonAdapter
function CircleJsonAdapter.constructor(this)
end

---@param this json-adapters.CircleJsonAdapter
---@param x json-adapters.Circle
---@param p std.json.JsonProducer
function CircleJsonAdapter.methods.encodeToJson(this, x, p)
  x:encodeToJson(p)
end

---@param this json-adapters.CircleJsonAdapter
---@param t std.json.JsonSyntaxTree
---@param ic std.json.InterchangeContext
---@return json-adapters.Circle
function CircleJsonAdapter.methods.decodeFromJson(this, t, ic)
  return Circle.decodeFromJson(t, ic)
end

---@class json-adapters.Shape
---@field encodeToJson fun(self: json-adapters.Shape, p: std.json.JsonProducer)

---@class json-adapters.Shape.type: temper.Type
local Shape = temper.type("json-adapters.Shape")

---@param t std.json.JsonSyntaxTree
---@param ic std.json.InterchangeContext
---@return json-adapters.Shape
function Shape.decodeFromJson(t, ic)
  do
    local obj = temper.cast_to(t, JsonObject)
    local valueForRadius = obj:propertyValueOrNull("radius")
    if not temper.is_null(valueForRadius) then
      return Circle.jsonAdapter():decodeFromJson(obj, ic)
    end
    if temper.is_null(valueForRadius) then
      return Rect.jsonAdapter():decodeFromJson(obj, ic)
    end
    return temper.bubble()
  end
end

---@return std.json.JsonAdapter
function Shape.jsonAdapter()
  return ShapeJsonAdapter()
end

---@class json-adapters.Circle: json-adapters.Shape
---@field radius integer
---@field encodeToJson fun(self: json-adapters.Circle, p: std.json.JsonProducer)

---@class json-adapters.Circle.type: temper.Type
---@operator call: json-adapters.Circle
Circle = temper.type("json-adapters.Circle", Shape)

---@param this json-adapters.Circle
---@param radius integer
function Circle.constructor(this, radius)
  this.radius = radius
end

---@param this json-adapters.Circle
---@param p std.json.JsonProducer
function Circle.methods.encodeToJson(this, p)
  p:startObject()
  p:objectKey("radius")
  p:int32Value(this.radius)
  p:endObject()
end

---@param t std.json.JsonSyntaxTree
---@param ic std.json.InterchangeContext
---@return json-adapters.Circle
function Circle.decodeFromJson(t, ic)
  local obj = temper.cast_to(t, JsonObject)
  local radiusJson = temper.cast_to(obj:propertyValueOrBubble("radius"), JsonNumeric)
  local radius = radiusJson:asInt32()
  return Circle(radius)
end

---@return std.json.JsonAdapter
function Circle.jsonAdapter()
  return CircleJsonAdapter()
end

---@class json-adapters.RectJsonAdapter: std.json.JsonAdapter
---@field encodeToJson fun(self: json-adapters.RectJsonAdapter, x: json-adapters.Rect, p: std.json.JsonProducer)
---@field decodeFromJson fun(self: json-adapters.RectJsonAdapter, t: std.json.JsonSyntaxTree, ic: std.json.InterchangeContext): json-adapters.Rect

---@class json-adapters.RectJsonAdapter.type: temper.Type
---@operator call: json-adapters.RectJsonAdapter
local RectJsonAdapter = temper.type("json-adapters.RectJsonAdapter", JsonAdapter)

---@param this json-adapters.RectJsonAdapter
function RectJsonAdapter.constructor(this)
end

---@param this json-adapters.RectJsonAdapter
---@param x json-adapters.Rect
---@param p std.json.JsonProducer
function RectJsonAdapter.methods.encodeToJson(this, x, p)
  x:encodeToJson(p)
end

---@param this json-adapters.RectJsonAdapter
---@param t std.json.JsonSyntaxTree
---@param ic std.json.InterchangeContext
---@return json-adapters.Rect
function RectJsonAdapter.methods.decodeFromJson(this, t, ic)
  return Rect.decodeFromJson(t, ic)
end

---@class json-adapters.Rect: json-adapters.Shape
---@field width integer
---@field height integer
---@field encodeToJson fun(self: json-adapters.Rect, p: std.json.JsonProducer)

---@class json-adapters.Rect.type: temper.Type
---@operator call: json-adapters.Rect
Rect = temper.type("json-adapters.Rect", Shape)

---@param this json-adapters.Rect
---@param width integer
---@param height integer
function Rect.constructor(this, width, height)
  this.width = width
  this.height = height
end

---@param this json-adapters.Rect
---@param p std.json.JsonProducer
function Rect.methods.encodeToJson(this, p)
  p:startObject()
  p:objectKey("width")
  p:int32Value(this.width)
  p:objectKey("height")
  p:int32Value(this.height)
  p:endObject()
end

---@param t std.json.JsonSyntaxTree
---@param ic std.json.InterchangeContext
---@return json-adapters.Rect
function Rect.decodeFromJson(t, ic)
  local obj = temper.cast_to(t, JsonObject)
  local widthJson = temper.cast_to(obj:propertyValueOrBubble("width"), JsonNumeric)
  local width = widthJson:asInt32()
  local heightJson = temper.cast_to(obj:propertyValueOrBubble("height"), JsonNumeric)
  local height = heightJson:asInt32()
  return Rect(width, height)
end

---@return std.json.JsonAdapter
function Rect.jsonAdapter()
  return RectJsonAdapter()
end

---@class json-adapters.ShapeJsonAdapter: std.json.JsonAdapter
---@field encodeToJson fun(self: json-adapters.ShapeJsonAdapter, x: json-adapters.Shape, p: std.json.JsonProducer)
---@field decodeFromJson fun(self: json-adapters.ShapeJsonAdapter, t: std.json.JsonSyntaxTree, ic: std.json.InterchangeContext): json-adapters.Shape

---@class json-adapters.ShapeJsonAdapter.type: temper.Type
---@operator call: json-adapters.ShapeJsonAdapter
ShapeJsonAdapter = temper.type("json-adapters.ShapeJsonAdapter", JsonAdapter)

---@param this json-adapters.ShapeJsonAdapter
function ShapeJsonAdapter.constructor(this)
end

---@param this json-adapters.ShapeJsonAdapter
---@param x json-adapters.Shape
---@param p std.json.JsonProducer
function ShapeJsonAdapter.methods.encodeToJson(this, x, p)
  x:encodeToJson(p)
end

---@param this json-adapters.ShapeJsonAdapter
---@param t std.json.JsonSyntaxTree
---@param ic std.json.InterchangeContext
---@return json-adapters.Shape
function ShapeJsonAdapter.methods.decodeFromJson(this, t, ic)
  return Shape.decodeFromJson(t, ic)
end

---@class json-adapters.HelloJsonAdapter: std.json.JsonAdapter
---@field encodeToJson fun(self: json-adapters.HelloJsonAdapter, x: json-adapters.Hello, p: std.json.JsonProducer)
---@field decodeFromJson fun(self: json-adapters.HelloJsonAdapter, t: std.json.JsonSyntaxTree, ic: std.json.InterchangeContext): json-adapters.Hello

---@class json-adapters.HelloJsonAdapter.type: temper.Type
---@operator call: json-adapters.HelloJsonAdapter
local HelloJsonAdapter = temper.type("json-adapters.HelloJsonAdapter", JsonAdapter)

---@param this json-adapters.HelloJsonAdapter
function HelloJsonAdapter.constructor(this)
end

---@param this json-adapters.HelloJsonAdapter
---@param x json-adapters.Hello
---@param p std.json.JsonProducer
function HelloJsonAdapter.methods.encodeToJson(this, x, p)
  x:encodeToJson(p)
end

---@param this json-adapters.HelloJsonAdapter
---@param t std.json.JsonSyntaxTree
---@param ic std.json.InterchangeContext
---@return json-adapters.Hello
function HelloJsonAdapter.methods.decodeFromJson(this, t, ic)
  return Hello.decodeFromJson(t, ic)
end

---@class json-adapters.Message
---@field encodeToJson fun(self: json-adapters.Message, p: std.json.JsonProducer)

---@class json-adapters.Message.type: temper.Type
local Message = temper.type("json-adapters.Message")

---@param t std.json.JsonSyntaxTree
---@param ic std.json.InterchangeContext
---@return json-adapters.Message
function Message.decodeFromJson(t, ic)
  do
    local obj = temper.cast_to(t, JsonObject)
    local valueForKind = obj:propertyValueOrNull("kind")
    if not temper.is_null(valueForKind) and temper.instance_of(valueForKind --[[@as std.json.JsonSyntaxTree]], JsonString) then
      local subject = (valueForKind --[[@as std.json.JsonString]]).content
      if subject == "hello" then
        return Hello.jsonAdapter():decodeFromJson(obj, ic)
      elseif subject == "bye" then
        return Bye.jsonAdapter():decodeFromJson(obj, ic)
      end
    end
    return temper.bubble()
  end
end

---@return std.json.JsonAdapter
function Message.jsonAdapter()
  return MessageJsonAdapter()
end

---@class json-adapters.Hello: json-adapters.Message
---@field text string
---@field encodeToJson fun(self: json-adapters.Hello, p: std.json.JsonProducer)

---@class json-adapters.Hello.type: temper.Type
---@operator call: json-adapters.Hello
Hello = temper.type("json-adapters.Hello", Message)

---@param this json-adapters.Hello
---@param text string
function Hello.constructor(this, text)
  this.text = text
end

---@param this json-adapters.Hello
---@param p std.json.JsonProducer
function Hello.methods.encodeToJson(this, p)
  p:startObject()
  p:objectKey("text")
  p:stringValue(this.text)
  p:objectKey("kind")
  p:stringValue("hello")
  p:endObject()
end

---@param t std.json.JsonSyntaxTree
---@param ic std.json.InterchangeContext
---@return json-adapters.Hello
function Hello.decodeFromJson(t, ic)
  local obj = temper.cast_to(t, JsonObject)
  local textJson = temper.cast_to(obj:propertyValueOrBubble("text"), JsonString)
  local text = textJson.content
  return Hello(text)
end

---@return std.json.JsonAdapter
function Hello.jsonAdapter()
  return HelloJsonAdapter()
end

---@class json-adapters.ByeJsonAdapter: std.json.JsonAdapter
---@field encodeToJson fun(self: json-adapters.ByeJsonAdapter, x: json-adapters.Bye, p: std.json.JsonProducer)
---@field decodeFromJson fun(self: json-adapters.ByeJsonAdapter, t: std.json.JsonSyntaxTree, ic: std.json.InterchangeContext): json-adapters.Bye

---@class json-adapters.ByeJsonAdapter.type: temper.Type
---@operator call: json-adapters.ByeJsonAdapter
local ByeJsonAdapter = temper.type("json-adapters.ByeJsonAdapter", JsonAdapter)

---@param this json-adapters.ByeJsonAdapter
function ByeJsonAdapter.constructor(this)
end

---@param this json-adapters.ByeJsonAdapter
---@param x json-adapters.Bye
---@param p std.json.JsonProducer
function ByeJsonAdapter.methods.encodeToJson(this, x, p)
  x:encodeToJson(p)
end

---@param this json-adapters.ByeJsonAdapter
---@param t std.json.JsonSyntaxTree
---@param ic std.json.InterchangeContext
---@return json-adapters.Bye
function ByeJsonAdapter.methods.decodeFromJson(this, t, ic)
  return Bye.decodeFromJson(t, ic)
end

---@class json-adapters.Bye: json-adapters.Message
---@field text string
---@field encodeToJson fun(self: json-adapters.Bye, p: std.json.JsonProducer)

---@class json-adapters.Bye.type: temper.Type
---@operator call: json-adapters.Bye
Bye = temper.type("json-adapters.Bye", Message)

---@param this json-adapters.Bye
---@param text string
function Bye.constructor(this, text)
  this.text = text
end

---@param this json-adapters.Bye
---@param p std.json.JsonProducer
function Bye.methods.encodeToJson(this, p)
  p:startObject()
  p:objectKey("text")
  p:stringValue(this.text)
  p:objectKey("kind")
  p:stringValue("bye")
  p:endObject()
end

---@param t std.json.JsonSyntaxTree
---@param ic std.json.InterchangeContext
---@return json-adapters.Bye
function Bye.decodeFromJson(t, ic)
  local obj = temper.cast_to(t, JsonObject)
  local textJson = temper.cast_to(obj:propertyValueOrBubble("text"), JsonString)
  local text = textJson.content
  return Bye(text)
end

---@return std.json.JsonAdapter
function Bye.jsonAdapter()
  return ByeJsonAdapter()
end

---@class json-adapters.MessageJsonAdapter: std.json.JsonAdapter
---@field encodeToJson fun(self: json-adapters.MessageJsonAdapter, x: json-adapters.Message, p: std.json.JsonProducer)
---@field decodeFromJson fun(self: json-adapters.MessageJsonAdapter, t: std.json.JsonSyntaxTree, ic: std.json.InterchangeContext): json-adapters.Message

---@class json-adapters.MessageJsonAdapter.type: temper.Type
---@operator call: json-adapters.MessageJsonAdapter
MessageJsonAdapter = temper.type("json-adapters.MessageJsonAdapter", JsonAdapter)

---@param this json-adapters.MessageJsonAdapter
function MessageJsonAdapter.constructor(this)
end

---@param this json-adapters.MessageJsonAdapter
---@param x json-adapters.Message
---@param p std.json.JsonProducer
function MessageJsonAdapter.methods.encodeToJson(this, x, p)
  x:encodeToJson(p)
end

---@param this json-adapters.MessageJsonAdapter
---@param t std.json.JsonSyntaxTree
---@param ic std.json.InterchangeContext
---@return json-adapters.Message
function MessageJsonAdapter.methods.decodeFromJson(this, t, ic)
  return Message.decodeFromJson(t, ic)
end

---@class json-adapters.CelsiusJsonAdapter: std.json.JsonAdapter
---@field encodeToJson fun(self: json-adapters.CelsiusJsonAdapter, x: json-adapters.Celsius, p: std.json.JsonProducer)
---@field decodeFromJson fun(self: json-adapters.CelsiusJsonAdapter, t: std.json.JsonSyntaxTree, ic: std.json.InterchangeContext): json-adapters.Celsius

---@class json-adapters.CelsiusJsonAdapter.type: temper.Type
---@operator call: json-adapters.CelsiusJsonAdapter
local CelsiusJsonAdapter = temper.type("json-adapters.CelsiusJsonAdapter", JsonAdapter)

---@param this json-adapters.CelsiusJsonAdapter
function CelsiusJsonAdapter.constructor(this)
end

---@param this json-adapters.CelsiusJsonAdapter
---@param x json-adapters.Celsius
---@param p std.json.JsonProducer
function CelsiusJsonAdapter.methods.encodeToJson(this, x, p)
  x:encodeToJson(p)
end

---@param this json-adapters.CelsiusJsonAdapter
---@param t std.json.JsonSyntaxTree
---@param ic std.json.InterchangeContext
---@return json-adapters.Celsius
function CelsiusJsonAdapter.methods.decodeFromJson(this, t, ic)
  return Celsius.decodeFromJson(t, ic)
end

---@class json-adapters.Celsius
---@field degrees integer
---@field encodeToJson fun(self: json-adapters.Celsius, p: std.json.JsonProducer)

---@class json-adapters.Celsius.type: temper.Type
---@operator call: json-adapters.Celsius
Celsius = temper.type("json-adapters.Celsius")

---@param this json-adapters.Celsius
---@param degrees integer
function Celsius.constructor(this, degrees)
  this.degrees = degrees
end

---@param this json-adapters.Celsius
---@param p std.json.JsonProducer
function Celsius.methods.encodeToJson(this, p)
  p:int32Value(this.degrees)
end

---@param t std.json.JsonSyntaxTree
---@param ic std.json.InterchangeContext
---@return json-adapters.Celsius
function Celsius.decodeFromJson(t, ic)
  return Celsius(temper.cast_to(t, JsonNumeric):asInt32())
end

---@return std.json.JsonAdapter
function Celsius.jsonAdapter()
  return CelsiusJsonAdapter()
end

---@generic T
---@param adapter std.json.JsonAdapter
---@param value T
---@return string
local function encode(adapter, value)
  local p = JsonTextProducer()
  adapter:encodeToJson(value, p)
  ---@type string
  local t_1
  if not temper.pcall(function()
    t_1 = p:toJsonString()
  end) then
    t_1 = "bad JSON"
  end
  return t_1
end

---@generic T
---@param adapter std.json.JsonAdapter
---@param text string
---@return T
local function decode(adapter, text)
  return adapter:decodeFromJson(parseJson(text), NullInterchangeContext.instance)
end

---@generic T
---@param adapter std.json.JsonAdapter
---@param value T
local function roundTrip(adapter, value)
  local text = encode(adapter, value)
  ---@type any
  local back
  local t_1, t_2 = temper.pcall(function()
    back = decode(adapter, text)
  end)
  if not t_1 then
    error("panic\ncaused by: " .. tostring(t_2))
  end
  local again = encode(adapter, back)
  print(again == text and text or text .. " became " .. again)
end

roundTrip(Point.jsonAdapter(), Point(1, -2))
roundTrip(Record.jsonAdapter(), Record("Ada", true, 1.5, int64_123456789012, temper.null, { Point(3, 4) }))
local shapes = { Circle(2), Rect(3, 4) }
roundTrip(listJsonAdapter(Shape.jsonAdapter()), shapes)
local messages = { Hello("hi"), Bye("so long") }
roundTrip(listJsonAdapter(Message.jsonAdapter()), messages)
roundTrip(Celsius.jsonAdapter(), Celsius(21))
---@type json-adapters.Shape
local shape
local t_1, t_2 = temper.pcall(function()
  shape = decode(Shape.jsonAdapter(), '{"height": 6, "width": 5}')
end)
if not t_1 then
  error("panic\ncaused by: " .. tostring(t_2))
end
if temper.instance_of(shape, Rect) then
  print("rect " .. (shape --[[@as json-adapters.Rect]]).width .. "x" .. (shape --[[@as json-adapters.Rect]]).height)
else
  print("not a rect")
end
---@type json-adapters.Record
local record
local t_3, t_4 = temper.pcall(function()
  record = decode(Record.jsonAdapter(), '{"name": "Bo", "is-active": false, "score": 2, "big": 7, "nickname": "B", "points": []}')
end)
if not t_3 then
  error("panic\ncaused by: " .. tostring(t_4))
end
local string_ = record.name .. " " .. tostring(record.active) .. " "
local subject = record.nickname
print(string_ .. (temper.is_null(subject) and "none" or (subject --[[@as string]])) .. " " .. temper.list_length(record.points))
---@type string
local t_5
if not temper.pcall(function()
  decode(Point.jsonAdapter(), '{"x": 1}')
  t_5 = "decoded"
end) then
  t_5 = "no y"
end
print(t_5)
---@type string
local t_6
if not temper.pcall(function()
  decode(Message.jsonAdapter(), '{"kind": "what", "text": "?"}')
  t_6 = "decoded"
end) then
  t_6 = "no such kind"
end
print(t_6)

return {}
