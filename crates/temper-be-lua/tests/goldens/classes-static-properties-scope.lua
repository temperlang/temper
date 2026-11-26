local temper = require("temper-core")

---@class static-properties-scope.Simple

---@class static-properties-scope.Simple.type: temper.Type
---@field value string
---@field other string
---@operator call: static-properties-scope.Simple
local Simple = temper.type("static-properties-scope.Simple")

---@param this static-properties-scope.Simple
function Simple.constructor(this)
end

---@return string
local function readSimpleValue()
  return Simple.value
end

---@return string
local function readSimpleOther()
  return Simple.other
end

Simple.value = "foo"
Simple.other = "bar"
temper.log("Simple.value is " .. readSimpleValue())
temper.log("Simple.other is " .. readSimpleOther())

return {}
