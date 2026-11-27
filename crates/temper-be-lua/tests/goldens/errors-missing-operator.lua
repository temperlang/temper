local temper = require("temper-core")

---@param x integer
---@return integer
local function good(x)
  return temper.int32_add(x, 1)
end

---@param x any
---@return integer
local function unknownType(x)
  return 1
end

---@param x integer
---@return integer
local function afterUnclosed(x)
  return temper.int32_mul(x, 3)
end

---@param cents integer
---@return string
local function dropped(cents)
  error("panic")
end

---@class errors-missing-operator.Box
---@field value integer
---@field get fun(self: errors-missing-operator.Box): integer
---@field bad fun(self: errors-missing-operator.Box): string

---@class errors-missing-operator.Box.type: temper.Type
---@operator call: errors-missing-operator.Box
local Box = temper.type("errors-missing-operator.Box")

---@param this errors-missing-operator.Box
---@param value integer
function Box.constructor(this, value)
  this.value = value
end

---@param this errors-missing-operator.Box
---@return integer
function Box.methods.get(this)
  return this.value
end

---@param this errors-missing-operator.Box
---@return string
function Box.methods.bad(this)
  return this.value
end

local box = Box(3)
print(good(1) .. " " .. unknownType(1) .. " " .. afterUnclosed(2) .. " " .. box:get())
dropped(1)
print("after")

return { good = good }
