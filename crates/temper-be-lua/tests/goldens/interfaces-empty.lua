local temper = require("temper-core")

---@class empty.Greeter

---@class empty.Greeter.type: temper.Type
local Greeter = temper.type("empty.Greeter")

---@class empty.HiGreeter: empty.Greeter
---@field greet fun(self: empty.HiGreeter)

---@class empty.HiGreeter.type: temper.Type
---@operator call: empty.HiGreeter
local HiGreeter = temper.type("empty.HiGreeter", Greeter)

---@param this empty.HiGreeter
function HiGreeter.constructor(this)
end

---@param this empty.HiGreeter
function HiGreeter.methods.greet(this)
  temper.log("Hi!")
end

local greeter = HiGreeter()
temper.cast_to(greeter, HiGreeter):greet()

return {}
