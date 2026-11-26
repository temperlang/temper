local temper = require("temper-core")
local greet = require("core-named/core").greet
local share = require("core-named/core").share

---@class core-named.String
---@field n integer

---@class core-named.String.type: temper.Type
---@operator call: core-named.String
local String = temper.type("core-named.String")

---@param this core-named.String
---@param n integer
function String.constructor(this, n)
  this.n = n
end

---@param core integer
---@return integer
local function twice(core)
  return temper.int32_mul(core, 2)
end

local core = greet("a module named core")
print(core)
print("shares " .. share(7, 2) .. " " .. share(1, 0))
local builtins = 1
builtins = temper.int32_add(builtins, 1)
print("builtins is " .. builtins)
local s = "core's String"
local mine = String(3)
print(s .. ", and mine holds " .. mine.n)
print(tostring(twice(21)))

return {}
