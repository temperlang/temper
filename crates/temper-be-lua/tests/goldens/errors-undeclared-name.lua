local temper = require("temper-core")

---@param x integer
---@return integer
local function good(x)
  return temper.int32_add(x, 1)
end

---@return integer
local function undeclared()
  error("panic")
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

---@class errors-undeclared-name.Box
---@field value integer
---@field get fun(self: errors-undeclared-name.Box): integer
---@field bad fun(self: errors-undeclared-name.Box): string

---@class errors-undeclared-name.Box.type: temper.Type
---@operator call: errors-undeclared-name.Box
local Box = temper.type("errors-undeclared-name.Box")

---@param this errors-undeclared-name.Box
---@param value integer
function Box.constructor(this, value)
  this.value = value
end

---@param this errors-undeclared-name.Box
---@return integer
function Box.methods.get(this)
  return this.value
end

---@param this errors-undeclared-name.Box
---@return string
function Box.methods.bad(this)
  return this.value
end

local box = Box(3)
print(good(1) .. " " .. unknownType(1) .. " " .. afterUnclosed(2) .. " " .. box:get())
undeclared()
print("after")

return { good = good }
