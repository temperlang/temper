local temper = require("temper-core")

---@param n integer
---@return string
local function whatever(n)
  if n == 0 then
    temper.log("0")
  end
  return "nil"
end

---@class angle-call.Classy
---@field index integer
---@field indexed fun(self: angle-call.Classy, things: any[]): any

---@class angle-call.Classy.type: temper.Type
---@operator call: angle-call.Classy
local Classy = temper.type("angle-call.Classy")

---@param this angle-call.Classy
---@param index integer
function Classy.constructor(this, index)
  this.index = index
end

---@generic T
---@param things T[]
---@return T
function Classy.first(things)
  local thing = temper.list_get(things, 0)
  return thing
end

---@param this angle-call.Classy
---@param things any[]
---@return any
function Classy.methods.indexed(this, things)
  local thing = temper.list_get(things, this.index)
  return thing
end

---@generic T
---@param things T[]
---@return T
local function lastOf(things)
  local thing = temper.list_get(things, temper.int32_sub(temper.list_length(things), 1))
  return thing
end

---@class angle-call.Picker
---@field pick fun(self: angle-call.Picker, things: any[]): any

---@class angle-call.Picker.type: temper.Type
---@operator call: angle-call.Picker
local Picker = temper.type("angle-call.Picker")

---@param this angle-call.Picker
function Picker.constructor(this)
end

---@param this angle-call.Picker
---@param things any[]
---@return any
function Picker.methods.pick(this, things)
  return lastOf(things)
end

whatever(1)
local someThings = { "a", "b" }
local first = Classy.first(someThings)
local second = Classy(1):indexed(someThings)
temper.log("Some things: " .. first .. ", " .. second)
temper.log("Picked: " .. Picker():pick(someThings))

return {}
