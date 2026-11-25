local temper = require("temper-core")

---@class as-expr.Apple
---@field name string

---@class as-expr.Apple.type: temper.Type
local Apple = temper.type("as-expr.Apple")

---@class as-expr.Fuji: as-expr.Apple
---@field name string

---@class as-expr.Fuji.type: temper.Type
---@operator call: as-expr.Fuji
local Fuji = temper.type("as-expr.Fuji", Apple)

---@param this as-expr.Fuji
function Fuji.constructor(this)
end

---@param this as-expr.Fuji
---@return string
function Fuji.get.name(this)
  return "Fuji"
end

---@class as-expr.Gala: as-expr.Apple
---@field name string

---@class as-expr.Gala.type: temper.Type
---@operator call: as-expr.Gala
local Gala = temper.type("as-expr.Gala", Apple)

---@param this as-expr.Gala
function Gala.constructor(this)
end

---@param this as-expr.Gala
---@return string
function Gala.get.name(this)
  return "Gala"
end

---@param v any|temper.Null
---@return string
local function describe(v)
  if not temper.is_null(v) and temper.instance_of(v --[[@as any]], Fuji) then
    return "Fuji " .. (v --[[@as as-expr.Fuji]]).name
  end
  if not temper.is_null(v) and temper.instance_of(v --[[@as any]], Apple) then
    return (v --[[@as as-expr.Apple]]).name
  else
    return "other"
  end
end

do
  ---@type as-expr.Apple|temper.Null
  local x
  x = Fuji()
  print(temper.cast_to(x, Apple).name)
  print(temper.cast_to(x, Fuji).name)
  x = Gala()
  print(not temper.is_null(x) and temper.instance_of(x --[[@as as-expr.Apple]], Fuji) and temper.cast_to(x, Fuji).name or "Cast failed!")
  local y = x
  print(temper.cast_to(y, Apple).name)
  print(temper.cast_to(y, Gala).name)
  x = temper.null
  print(not temper.is_null(x) and "Tasty nothing." or "Was null!")
end
local thing2 = "two"
local thing1 = 1
print(describe(Fuji()))
print(describe(Gala()))
print(describe(thing2))
print(describe(thing1))
print(describe(temper.null))
local thing0 = false
local thing3 = 3.14

return { thing2 = thing2, thing1 = thing1, thing0 = thing0, thing3 = thing3 }
