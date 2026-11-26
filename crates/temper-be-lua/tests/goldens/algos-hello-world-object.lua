local temper = require("temper-core")

---@class hello-world-object.C
---@field x string
---@field y string
---@field echo fun(self: hello-world-object.C, input: string)

---@class hello-world-object.C.type: temper.Type
---@operator call: hello-world-object.C
local C = temper.type("hello-world-object.C")

---@param this hello-world-object.C
---@param x string
---@param y string
function C.constructor(this, x, y)
  this.x = x
  this.y = y
end

---@param this hello-world-object.C
---@param input string
function C.methods.echo(this, input)
  temper.log(input)
end

local c = C("Hello", "World")
temper.log(c.x .. ", " .. c.y .. "!")
c:echo("Hello World, again!")

return {}
