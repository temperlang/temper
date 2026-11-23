local temper = require("temper-core")

---@class sealed-whens.Shape
---@field size integer

---@class sealed-whens.Shape.type: temper.Type
local Shape = temper.type("sealed-whens.Shape")

---@class sealed-whens.Dot: sealed-whens.Shape
---@field size integer

---@class sealed-whens.Dot.type: temper.Type
---@operator call: sealed-whens.Dot
local Dot = temper.type("sealed-whens.Dot", Shape)

---@param this sealed-whens.Dot
---@param size integer
function Dot.constructor(this, size)
  this.size = size
end

---@class sealed-whens.Line: sealed-whens.Shape
---@field size integer

---@class sealed-whens.Line.type: temper.Type
---@operator call: sealed-whens.Line
local Line = temper.type("sealed-whens.Line", Shape)

---@param this sealed-whens.Line
---@param size integer
function Line.constructor(this, size)
  this.size = size
end

---@param n integer
---@return sealed-whens.Shape
local function shapeOf(n)
  if n > 1 then
    return Line(n)
  else
    return Dot(n)
  end
end

---@param s sealed-whens.Shape
---@return string
local function named(s)
  local name = temper.instance_of(s, Dot) and "dot" or (temper.instance_of(s, Line) and "line" or error("panic"))
  return name
end

---@param s sealed-whens.Shape
---@param t sealed-whens.Shape
---@return string
local function pair(s, t)
  if temper.instance_of(s, Dot) then
    if temper.instance_of(t, Dot) then
      return "dd"
    elseif temper.instance_of(t, Line) then
      return "dl"
    else
      error("panic")
    end
  elseif temper.instance_of(s, Line) then
    return "l"
  else
    error("panic")
  end
end

---@param n integer
---@return integer
local function doubled(n)
  return temper.int32_mul(n, 2)
end

---@param s sealed-whens.Shape
---@param early boolean
---@return integer
local function code(s, early)
  if early then
    return temper.instance_of(s, Dot) and 1 or (temper.instance_of(s, Line) and 2 or error("panic"))
  end
  if s.size > 1 then
    if temper.instance_of(s, Dot) then
      return 10
    elseif temper.instance_of(s, Line) then
      return 20
    else
      error("panic")
    end
  else
    return doubled(temper.instance_of(s, Dot) and 3 or (temper.instance_of(s, Line) and 4 or error("panic")))
  end
end

---@class sealed-whens.Namer
---@field name fun(self: sealed-whens.Namer, s: sealed-whens.Shape): string

---@class sealed-whens.Namer.type: temper.Type
---@operator call: sealed-whens.Namer
local Namer = temper.type("sealed-whens.Namer")

---@param this sealed-whens.Namer
function Namer.constructor(this)
end

---@param this sealed-whens.Namer
---@param s sealed-whens.Shape
---@return string
function Namer.methods.name(this, s)
  if temper.instance_of(s, Dot) then
    return "a dot"
  elseif temper.instance_of(s, Line) then
    return "a line"
  else
    error("panic")
  end
end

local dot = shapeOf(1)
local line = shapeOf(2)
print(named(dot) .. " " .. named(line) .. " " .. pair(dot, line) .. " " .. pair(line, dot))
print(code(line, true) .. " " .. code(dot, false) .. " " .. code(line, false) .. " " .. Namer():name(line))

return {}
