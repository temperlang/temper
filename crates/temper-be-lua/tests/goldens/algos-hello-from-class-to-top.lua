local temper = require("temper-core")

---@param message string
local function say(message)
  temper.log(message)
end

---@class hello-from-class-to-top.Something
---@field message string
---@field sayMessage fun(self: hello-from-class-to-top.Something)

---@class hello-from-class-to-top.Something.type: temper.Type
---@operator call: hello-from-class-to-top.Something
local Something = temper.type("hello-from-class-to-top.Something")

---@param this hello-from-class-to-top.Something
---@param message string
function Something.constructor(this, message)
  this.message = message
end

---@param this hello-from-class-to-top.Something
function Something.methods.sayMessage(this)
  say(this.message)
end

local something = Something("Hello, World!")
something:sayMessage()

return {}
