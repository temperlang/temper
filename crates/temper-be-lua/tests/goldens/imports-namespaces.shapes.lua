local temper = require("temper-core")

---@param x integer
---@return integer
local function twice(x)
  return temper.int32_mul(x, 2)
end

---@class namespaces.shapes.Box
---@field value any

---@class namespaces.shapes.Box.type: temper.Type
---@operator call: namespaces.shapes.Box
local Box = temper.type("namespaces.shapes.Box")

---@param this namespaces.shapes.Box
---@param value any
function Box.constructor(this, value)
  this.value = value
end

---@class namespaces.shapes.Shape
---@field area fun(self: namespaces.shapes.Shape): integer

---@class namespaces.shapes.Shape.type: temper.Type
local Shape = temper.type("namespaces.shapes.Shape")

---@class namespaces.shapes.Square: namespaces.shapes.Shape
---@field side integer
---@field area fun(self: namespaces.shapes.Square): integer

---@class namespaces.shapes.Square.type: temper.Type
---@operator call: namespaces.shapes.Square
local Square = temper.type("namespaces.shapes.Square", Shape)

---@param this namespaces.shapes.Square
---@param side integer
function Square.constructor(this, side)
  this.side = side
end

---@param this namespaces.shapes.Square
---@return integer
function Square.methods.area(this)
  return temper.int32_mul(this.side, this.side)
end

---@param side integer
---@return namespaces.shapes.Square
function Square.sized(side)
  return Square(side)
end

local unit = 1

return { unit = unit, twice = twice, Box = Box, Shape = Shape, Square = Square }
