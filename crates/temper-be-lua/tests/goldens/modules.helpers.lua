local temper = require("temper-core")
local greet = require("modules/helpers/deep").greet
local exports = {}

---@param x integer
---@return integer
local function twice(x)
  return temper.int32_mul(x, 2)
end

---@param x integer
---@return integer
local function intDoubled(x)
  return twice(x)
end

---@return integer
local function bump()
  exports.calls = temper.int32_add(exports.calls, 1)
  return exports.calls
end

local base = 10
exports.calls = 0
print(greet("helpers"))

exports.twice = twice
exports.intDoubled = intDoubled
exports.base = base
exports.bump = bump
return exports
