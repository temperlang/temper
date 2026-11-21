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
  temper.log(temper.cast_to(x, Apple).name)
  temper.log(temper.cast_to(x, Fuji).name)
  x = Gala()
  ---@type string
  local t
  if not temper.pcall(function()
    t = temper.cast_to(x, Fuji).name
  end) then
    t = "Cast failed!"
  end
  temper.log(t)
  local y = x
  ---@type string
  local t_1
  if not temper.pcall(function()
    t_1 = temper.cast_to(y, Apple).name
  end) then
    t_1 = "Cast from AnyValue failed!"
  end
  temper.log(t_1)
  ---@type string
  local t_2
  if not temper.pcall(function()
    t_2 = temper.cast_to(y, Gala).name
  end) then
    t_2 = "Cast from AnyValue failed!"
  end
  temper.log(t_2)
  ---@type string
  local t_3
  if not temper.pcall(function()
    t_3 = temper.cast_to(y, Fuji).name
  end) then
    t_3 = "Cast from AnyValue failed!"
  end
  temper.log(t_3)
  x = temper.null
  ---@type string
  local t_4
  if not temper.pcall(function()
    temper.cast_to(x, Apple)
    t_4 = "Tasty nothing."
  end) then
    t_4 = "Was null!"
  end
  temper.log(t_4)
end
local thing0 = false
local thing1 = 1
local thing2 = "two"
local thing3 = 3.14

return { thing0 = thing0, thing1 = thing1, thing2 = thing2, thing3 = thing3 }
