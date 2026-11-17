local temper = require("temper-core")
local int64_neg1 = temper.int64_constructor(-1)
local JsonTextProducer = require("std/json").JsonTextProducer
local JsonProducer = require("std/json").JsonProducer
local JsonSyntaxTree = require("std/json").JsonSyntaxTree
local parseJson = require("std/json").parseJson
local JsonArray = require("std/json").JsonArray
local JsonSyntaxTreeProducer = require("std/json").JsonSyntaxTreeProducer
local parseJsonToProducer = require("std/json").parseJsonToProducer
local InterchangeContext = require("std/json").InterchangeContext
local NullInterchangeContext = require("std/json").NullInterchangeContext
local OrNullJsonAdapter = require("std/json").OrNullJsonAdapter
local int32JsonAdapter = require("std/json").int32JsonAdapter
local JsonObject = require("std/json").JsonObject
local JsonNull = require("std/json").JsonNull
local JsonNumeric = require("std/json").JsonNumeric
local int64JsonAdapter = require("std/json").int64JsonAdapter

do
  local list = { "[]", " [] ", " [ ] ", "[1]", "null" }
  local length = temper.list_length(list)
  local i = 0
  while i < length do
    local s = temper.list_get(list, i)
    i = temper.int32_add(i, 1)
    ---@type std.json.JsonSyntaxTree|temper.Null
    local jsonValue
    if not temper.pcall(function()
      jsonValue = parseJson(s)
    end) then
      jsonValue = temper.null
    end
    print("`" .. s .. "` is empty array: " .. tostring(not temper.is_null(jsonValue) and temper.instance_of(jsonValue --[[@as std.json.JsonSyntaxTree]], JsonArray) and temper.listed_isempty((jsonValue --[[@as std.json.JsonArray]]).elements)))
  end
end
do
  local list2 = { "[", "]", "[,]", "[]]", "[1,]", "[1,,2]" }
  local length2 = temper.list_length(list2)
  local i2 = 0
  while i2 < length2 do
    local s2 = temper.list_get(list2, i2)
    i2 = temper.int32_add(i2, 1)
    local bubbled = false
    local p = JsonSyntaxTreeProducer()
    parseJsonToProducer(s2, p)
    if not temper.pcall(function()
      p:toJsonSyntaxTree()
    end) then
      bubbled = true
    end
    local error_ = p.jsonError
    print("`" .. s2 .. "` has" .. (bubbled and "" or " no") .. " error: " .. (temper.is_null(error_) and "MISSING ERROR" or (error_ --[[@as string]])))
  end
end
local selectCodePoints = "\000\001\002\003\004\005\006\007\008\t\n\011\012\r\014\015\016\017\018\019\020\021\022\023\024\025\026\027\028\029\030\031 -𐀀--퟿\226\128\168\226\128\169\194\133"
do
  local p2 = JsonTextProducer(NullInterchangeContext.instance)
  p2:stringValue(selectCodePoints)
  ---@type string
  local t
  if not temper.pcall(function()
    t = p2:toJsonString()
  end) then
    t = "?!"
  end
  print("string with select code points: " .. t)
end
do
  local p3 = JsonTextProducer(NullInterchangeContext.instance)
  p3:startObject()
  local intOrNullAdapter = OrNullJsonAdapter(int32JsonAdapter())
  ---@type integer|temper.Null
  local x = temper.null
  ---@type integer|temper.Null
  local y = 123
  p3:objectKey("x")
  intOrNullAdapter:encodeToJson(x, p3)
  p3:objectKey("y")
  intOrNullAdapter:encodeToJson(y, p3)
  p3:endObject()
  print(p3:toJsonString())
end
do
  local s3 = '{"x":null,"y":123,"z":123456789012}'
  local ast = parseJson(s3)
  local obj = temper.cast_to(ast, JsonObject)
  local xAst = obj:propertyValueOrBubble("x")
  local yAst = obj:propertyValueOrBubble("y")
  print("for x, got JSON null: " .. tostring(temper.instance_of(xAst, JsonNull)))
  print("for y, got JSON numeric: " .. tostring(temper.instance_of(yAst, JsonNumeric)))
  local intOrNullAdapter2 = OrNullJsonAdapter(int32JsonAdapter())
  local x2 = intOrNullAdapter2:decodeFromJson(xAst, NullInterchangeContext.instance)
  local y2 = intOrNullAdapter2:decodeFromJson(yAst, NullInterchangeContext.instance)
  print("x ?? -1 -> " .. (temper.is_null(x2) and -1 or (x2 --[[@as integer]])))
  print("y ?? -1 -> " .. (temper.is_null(y2) and -1 or (y2 --[[@as integer]])))
  local zAst = obj:propertyValueOrBubble("z")
  local int64OrNullAdapter = OrNullJsonAdapter(int64JsonAdapter())
  local z = int64OrNullAdapter:decodeFromJson(zAst, NullInterchangeContext.instance)
  print("z ?? -1 -> " .. temper.int64_tostring(temper.is_null(z) and int64_neg1 or (z --[[@as integer]])))
end
local normalJson = JsonTextProducer()
parseJsonToProducer('{\n  "foo": "bar",\n  "baz": [\n    "boo",\n    { "f": 1, "a": null, "r": 123.0 }\n  ]\n}\n', normalJson)
print("Normalized:")
print(normalJson:toJsonString())

return {}
