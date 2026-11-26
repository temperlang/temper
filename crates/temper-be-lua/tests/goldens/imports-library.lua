local temper = require("temper-core")
local Ratio = require("modules/core").Ratio
local ratioOf = require("modules/core").ratioOf
local Checked = require("renamed").Checked
local Builtins = require("renamed").Builtins
local lambda = require("renamed").lambda
local native = require("renamed").native
local Spot = require("modules/state").Spot
local sum = require("modules").sum
local twice = require("modules/helpers").twice
local base = require("modules/helpers").base

---@param n integer
---@return integer
local function both(n)
  return temper.int32_add(temper.int32_add(sum(n), twice(n)), base)
end

print("both " .. both(2))
local third = Ratio(1, 3)
local string_ = "ratio " .. tostring(third:eq(Ratio(3, 9))) .. " "
local left = ratioOf(2, 6)
local left2 = ratioOf(1, 0)
print(string_ .. tostring(not temper.is_null(left) and (left --[[@as modules.core.Ratio]]):eq(third)) .. " " .. tostring(not temper.is_null(left2) and (left2 --[[@as modules.core.Ratio]]):eq(third)))
local checked = Checked(lambda(native))
print("renamed " .. checked:delete() .. " " .. checked.local_ .. " " .. checked.end_)
local b = Builtins(4)
print("builtins " .. b.hash .. " " .. b:get(1) .. " " .. b:set(3) .. " " .. b:map(5) .. " " .. b:format() .. " " .. b.int .. " " .. b:later())
print(b:print())
local spread = Spot(3, 4)
local far = Spot(9, spread.y)
print("spot far " .. far.x .. " " .. far.y)

return { both = both }
