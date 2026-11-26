local temper = require("temper-core")
local int64_neg7 = temper.int64_constructor(-7)
local int64_5000000000 = temper.int64_constructor(1, 705032704)
local Regex = require("std/regex").Regex
local RegexNode = require("std/regex").RegexNode
local Repeat = require("std/regex").Repeat
local CodeSet = require("std/regex").CodeSet
local CodePart = require("std/regex").CodePart
local CodeRange = require("std/regex").CodeRange
local JsonError = require("std/json").JsonError
local JsonTextProducer = require("std/json").JsonTextProducer
local JsonSyntaxError = require("std/json").JsonSyntaxError
local parseJson = require("std/json").parseJson
local Date = require("std/temporal").Date
local TemporalError = require("std/temporal").TemporalError

---@class panics.Shape

---@class panics.Shape.type: temper.Type
local Shape = temper.type("panics.Shape")

---@class panics.Square: panics.Shape
---@field side integer

---@class panics.Square.type: temper.Type
---@operator call: panics.Square
local Square = temper.type("panics.Square", Shape)

---@param this panics.Square
---@param side integer
function Square.constructor(this, side)
  this.side = side
end

---@class panics.Circle: panics.Shape

---@class panics.Circle.type: temper.Type
---@operator call: panics.Circle
local Circle = temper.type("panics.Circle", Shape)

---@param this panics.Circle
function Circle.constructor(this)
end

---@param n integer
---@return integer
local function failing(n)
  if n > 0 then
    return temper.bubble()
  end
  return n
end

---@param s panics.Shape
---@return panics.Square
local function downcast(s)
  if temper.instance_of(s, Square) then
    return s --[[@as panics.Square]]
  else
    return temper.bubble()
  end
end

---@param f fun(): integer
---@return integer
local function calling(f)
  ---@type integer
  local t_1
  if not temper.pcall(function()
    t_1 = f()
  end) then
    t_1 = -9
  end
  return t_1
end

---@param codePoint integer
---@return string
local function appending(codePoint)
  local sb = temper.stringbuilder_constructor()
  temper.stringbuilder_appendcodepoint(sb, codePoint)
  return temper.stringbuilder_tostring(sb)
end

---@param text string
---@return string
local function parsing(text)
  parseJson(text)
  return "parsed"
end

---@param n integer
---@return integer
local function explode(n)
  if n > 0 then
    error("panic")
  end
  return temper.bubble()
end

---@param name string
---@return integer
local function noted(name)
  print(name .. " fallback")
  return -2
end

---@return integer
local function inner()
  ---@type integer
  local t_1
  if not temper.pcall(function()
    t_1 = explode(1)
  end) then
    t_1 = noted("inner")
  end
  return t_1
end

---@type integer
local zero
if not temper.pcall(function()
  zero = failing(0)
end) then
  zero = -1
end
---@type integer
local a
if not temper.pcall(function()
  a = failing(1)
end) then
  a = -1
end
---@type integer
local b
if not temper.pcall(function()
  b = downcast(Circle()).side
end) then
  b = -2
end
---@type integer
local e
if not temper.catch(function()
  e = temper.string_toint32("nope")
end, temper.ParseError) then
  e = -5
end
---@type number
local f
if not temper.catch(function()
  f = temper.string_tofloat64("x")
end, temper.ParseError) then
  f = -6.0
end
---@type string
local g
if not temper.catch(function()
  g = temper.string_fromcodepoint(1114112)
end, temper.InvalidCodePoint) then
  g = "?"
end
local digits = Repeat(CodeSet({ CodeRange(48, 57) }, false), 1, temper.null, false):compiled()
---@type string
local h
if not temper.pcall(function()
  h = digits:find("none").full.value
end) then
  h = "no digits"
end
local i = calling(function() return failing(2) end)
print(a .. " " .. b .. " " .. e .. " " .. tostring(temper.float_eq(f, -6.0)) .. " " .. g .. " " .. h .. " " .. i)
---@type integer
local k
if not temper.catch(function()
  k = temper.string_toint64("zz")
end, temper.ParseError) then
  k = int64_neg7
end
---@type integer
local l
if not temper.catch(function()
  l = temper.string_toint32("99999999999")
end, temper.ParseError) then
  l = -8
end
---@type integer
local m
if not temper.catch(function()
  m = temper.float64_toint32(1e20)
end, temper.ParseError) then
  m = -9
end
---@type integer
local n
if not temper.catch(function()
  n = temper.int64_toint32(int64_5000000000)
end, temper.ParseError) then
  n = -10
end
local points = { 65, 1114112 }
---@type string
local o
local t_1, e2 = temper.catch(function()
  o = temper.string_fromcodepoints(points)
end, temper.InvalidCodePoint)
if not t_1 then
  o = temper.int32_tostring(e2.codePoint, 16)
end
---@type string
local q
local t_2, e3 = temper.catch(function()
  q = appending(-1)
end, temper.InvalidCodePoint)
if not t_2 then
  q = tostring(e3.codePoint)
end
local builder = temper.mapbuilder_constructor()
---@type integer
local r
if not temper.catch(function()
  r = temper.mapped_get(builder, "x")
end, temper.MissingKey) then
  r = -11
end
---@type string
local r2
local t_3, e4 = temper.catch(function()
  r2 = tostring(temper.mapbuilder_remove(builder, "y"))
end, temper.MissingKey)
if not t_3 then
  r2 = e4.message
end
---@type string
local s
if not temper.catch(function()
  s = temper.date_tostring(temper.date_constructor(2023, 2, 30))
end, TemporalError) then
  s = "no date"
end
---@type string
local t
if not temper.catch(function()
  t = temper.date_tostring(temper.date_fromisostring("2023-13-01"))
end, TemporalError) then
  t = "bad date"
end
---@type string
local u
if not temper.catch(function()
  u = parsing("[1,")
end, JsonError) then
  u = "bad json"
end
local partial = JsonTextProducer()
partial:startObject()
---@type string
local v
local t_4, e5 = temper.catch(function()
  v = partial:toJsonString()
end, JsonSyntaxError)
if not t_4 then
  v = e5.problem
end
print(temper.int64_tostring(k) .. " " .. l .. " " .. m .. " " .. n .. " " .. o .. " " .. q .. " " .. r .. " " .. r2 .. " " .. s .. " " .. t .. " " .. u)
print(v)
print("panicking")
---@type integer
local p
if not temper.pcall(function()
  p = inner()
end) then
  p = -1
end
print("after the panic " .. p)

return {}
