local temper = require("temper-core")

local isEven

---@param how string
local function hello(how)
  temper.log("Called " .. how .. "!")
end

---@class as-values.StringSink
---@field apply fun(self: as-values.StringSink, s: string)

---@class as-values.StringSink.type: temper.Type
local StringSink = temper.type("as-values.StringSink")

---@param f fun(arg0: string)
local function callIt(f)
  f("by a function")
end

---@class as-values.DelayedFunction
---@field f fun(arg0: string)
---@field callIt fun(self: as-values.DelayedFunction)

---@class as-values.DelayedFunction.type: temper.Type
---@operator call: as-values.DelayedFunction
local DelayedFunction = temper.type("as-values.DelayedFunction")

---@param this as-values.DelayedFunction
---@param f fun(arg0: string)
function DelayedFunction.constructor(this, f)
  this.f = f
end

---@param this as-values.DelayedFunction
function DelayedFunction.methods.callIt(this)
  this.f("from a method")
end

---@param i integer
---@return boolean
local function isOdd(i)
  if i == 0 then
    return false
  else
    return isEven(temper.int32_sub(i, 1))
  end
end

---@param i integer
---@return boolean
function isEven(i)
  if i == 0 then
    return true
  else
    return isOdd(temper.int32_sub(i, 1))
  end
end

hello("directly")
callIt(hello)
DelayedFunction(hello):callIt()
temper.log("Is 5 even? " .. tostring(isEven(5)))
temper.log("Is 6 even? " .. tostring(isEven(6)))

return {}
