local temper = require("temper-core")

---@class pure-virtual.Greeter
---@field greet fun(self: pure-virtual.Greeter)

---@class pure-virtual.Greeter.type: temper.Type
local Greeter = temper.type("pure-virtual.Greeter")

---@class pure-virtual.HiGreeter: pure-virtual.Greeter
---@field greet fun(self: pure-virtual.HiGreeter)

---@class pure-virtual.HiGreeter.type: temper.Type
---@operator call: pure-virtual.HiGreeter
local HiGreeter = temper.type("pure-virtual.HiGreeter", Greeter)

---@param this pure-virtual.HiGreeter
function HiGreeter.constructor(this)
end

---@param this pure-virtual.HiGreeter
function HiGreeter.methods.greet(this)
  temper.log("Hi, virtually!")
end

---@class pure-virtual.Wrapper
---@field wrap fun(self: pure-virtual.Wrapper, item: any): any

---@class pure-virtual.Wrapper.type: temper.Type
local Wrapper = temper.type("pure-virtual.Wrapper")

---@class pure-virtual.Box
---@field label string

---@class pure-virtual.Box.type: temper.Type
---@operator call: pure-virtual.Box
local Box = temper.type("pure-virtual.Box")

---@param this pure-virtual.Box
---@param label string
function Box.constructor(this, label)
  this.label = label
end

---@class pure-virtual.BoxWrapper: pure-virtual.Wrapper
---@field wrap fun(self: pure-virtual.BoxWrapper, item: pure-virtual.Box): pure-virtual.Box

---@class pure-virtual.BoxWrapper.type: temper.Type
---@operator call: pure-virtual.BoxWrapper
local BoxWrapper = temper.type("pure-virtual.BoxWrapper", Wrapper)

---@param this pure-virtual.BoxWrapper
function BoxWrapper.constructor(this)
end

---@param this pure-virtual.BoxWrapper
---@param item pure-virtual.Box
---@return pure-virtual.Box
function BoxWrapper.methods.wrap(this, item)
  item = Box("[" .. item.label .. "]")
  return item
end

---@class pure-virtual.IntWrapper: pure-virtual.Wrapper
---@field wrap fun(self: pure-virtual.IntWrapper, item: integer): integer

---@class pure-virtual.IntWrapper.type: temper.Type
---@operator call: pure-virtual.IntWrapper
local IntWrapper = temper.type("pure-virtual.IntWrapper", Wrapper)

---@param this pure-virtual.IntWrapper
function IntWrapper.constructor(this)
end

---@param this pure-virtual.IntWrapper
---@param item integer
---@return integer
function IntWrapper.methods.wrap(this, item)
  return temper.int32_add(item, 1)
end

local greeter = HiGreeter()
greeter:greet()
local boxWrapper = BoxWrapper()
local intWrapper = IntWrapper()
temper.log(boxWrapper:wrap(Box("box")).label)
temper.log(tostring(intWrapper:wrap(41)))

return {}
