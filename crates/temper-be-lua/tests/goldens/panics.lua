local temper = require("temper-core")
local int64_1 = temper.int64_constructor(1)
local Regex = require("std/regex").Regex
local RegexNode = require("std/regex").RegexNode
local Repeat = require("std/regex").Repeat
local CodeSet = require("std/regex").CodeSet
local CodePart = require("std/regex").CodePart
local CodeRange = require("std/regex").CodeRange

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
  return temper.cast_to(s, Square)
end

---@param f fun(): integer
---@return integer
local function calling(f)
  ---@type integer
  local t
  if not temper.pcall(function()
    t = f()
  end) then
    t = -9
  end
  return t
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
  local t
  if not temper.pcall(function()
    t = explode(1)
  end) then
    t = noted("inner")
  end
  return t
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
local c
if not temper.pcall(function()
  c = temper.int32_div(1, zero)
end) then
  c = -3
end
---@type integer
local d
if not temper.pcall(function()
  d = temper.int64_toint32(temper.int64_div(int64_1, temper.int32_toint64(zero)))
end) then
  d = -4
end
---@type integer
local e
if not temper.pcall(function()
  e = temper.string_toint32("nope")
end) then
  e = -5
end
---@type number
local f
if not temper.pcall(function()
  f = temper.string_tofloat64("x")
end) then
  f = -6.0
end
---@type string
local g
if not temper.pcall(function()
  g = temper.string_fromcodepoint(1114112)
end) then
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
print(a .. " " .. b .. " " .. c .. " " .. d .. " " .. e .. " " .. tostring(temper.float_eq(f, -6.0)) .. " " .. g .. " " .. h .. " " .. i)
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
