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
local thing0 = false
local thing1 = 1
local thing2 = "two"
local thing3 = 3.14

return { thing0 = thing0, thing1 = thing1, thing2 = thing2, thing3 = thing3 }
