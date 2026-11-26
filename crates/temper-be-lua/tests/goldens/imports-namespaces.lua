local temper = require("temper-core")
local Box = require("namespaces/shapes").Box
local Shape = require("namespaces/shapes").Shape
local Square = require("namespaces/shapes").Square
local unit = require("namespaces/shapes").unit
local twice = require("namespaces/shapes").twice

---@class namespaces.Circle: namespaces.shapes.Shape
---@field r integer
---@field area fun(self: namespaces.Circle): integer

---@class namespaces.Circle.type: temper.Type
---@operator call: namespaces.Circle
local Circle = temper.type("namespaces.Circle", Shape)

---@param this namespaces.Circle
---@param r integer
function Circle.constructor(this, r)
  this.r = r
end

---@param this namespaces.Circle
---@return integer
function Circle.methods.area(this)
  return temper.int32_mul(temper.int32_mul(3, this.r), this.r)
end

---@param shapes integer
---@return integer
local function bump(shapes)
  return temper.int32_add(shapes, 1)
end

print(tostring(unit))
print(tostring(twice(21)))
local box = Box(3)
local squares = { Square(4), Square(unit), Square.sized(5) }
do
  local length = temper.list_length(squares)
  local i = 0
  while i < length do
    local s = temper.list_get(squares, i)
    i = temper.int32_add(i, 1)
    print("area " .. s:area())
  end
end
local mixed = { Circle(2), Square(2) }
do
  local length2 = temper.list_length(mixed)
  local i2 = 0
  while i2 < length2 do
    local s2 = temper.list_get(mixed, i2)
    i2 = temper.int32_add(i2, 1)
    if temper.instance_of(s2, Square) then
      print("a square of " .. (s2 --[[@as namespaces.shapes.Square]]):area())
    else
      print("something else of " .. s2:area())
    end
  end
end
print(tostring(bump(box.value)))

return {}
