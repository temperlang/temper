local temper = require("temper-core")

---@class static-properties.Simple
---@field name string

---@class static-properties.Simple.type: temper.Type
---@field simon static-properties.Simple
---@field value string
---@field other integer
---@operator call: static-properties.Simple
local Simple = temper.type("static-properties.Simple")

---@param this static-properties.Simple
---@param name string
function Simple.constructor(this, name)
  this.name = name
end

---@class static-properties.C

---@class static-properties.C.type: temper.Type
---@operator call: static-properties.C
local C = temper.type("static-properties.C")

---@param this static-properties.C
function C.constructor(this)
end

function C.sayHi()
  temper.log(C.compose("C", "'hi.'"))
end

---@param subject string
---@param message string
---@return string
function C.compose(subject, message)
  return subject .. " says " .. message
end

---@class static-properties.I

---@class static-properties.I.type: temper.Type
local I = temper.type("static-properties.I")

function I.sayBye()
  temper.log("I says 'bye.'")
end

Simple.simon = Simple("foo")
Simple.value = Simple.simon.name
Simple.other = 22
temper.log("Simple.simon.name is " .. Simple.simon.name)
temper.log("Simple.value is " .. Simple.value)
temper.log("Simple.other is " .. Simple.other)
C.sayHi()
I.sayBye()

return {}
