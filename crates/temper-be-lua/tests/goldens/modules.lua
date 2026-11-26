local temper = require("temper-core")
require("modules/state")
require("modules/parts/regex")
local greet = require("modules/helpers/deep").greet
local twice = require("modules/helpers").twice
local intDoubled = require("modules/helpers").intDoubled
local base = require("modules/helpers").base
local bump = require("modules/helpers").bump
local Ratio = require("modules/core").Ratio
local share = require("modules/core").share
local ratioOf = require("modules/core").ratioOf
local describe = require("modules/machine").describe

---@type integer
local base2

---@class modules.Engine
---@field name string

---@class modules.Engine.type: temper.Type
---@operator call: modules.Engine
local Engine = temper.type("modules.Engine")

---@param this modules.Engine
---@param name string
function Engine.constructor(this, name)
  this.name = name
end

---@param n integer
---@return integer
local function sum(n)
  return temper.int32_add(temper.int32_add(twice(n), base), base2)
end

local calls2 = require("modules/helpers").calls
base2 = 1
temper.log(greet("modules"))
temper.log("sum " .. sum(3))
temper.log("share " .. share(7, 2) .. " " .. intDoubled(21))
temper.log(Engine("own").name .. ", " .. describe())
bump()
bump()
temper.log("calls " .. calls2)
temper.log("calls now " .. bump())
local half = Ratio(1, 2)
local string_ = tostring(half:eq(Ratio(2, 4))) .. " "
local left = ratioOf(3, 6)
local left2 = half
local right = ratioOf(1, 0)
temper.log(string_ .. tostring(not temper.is_null(left) and (left --[[@as modules.core.Ratio]]):eq(half)) .. " " .. tostring(not (not temper.is_null(right) and left2:eq(right --[[@as modules.core.Ratio]]))))

return { sum = sum }
