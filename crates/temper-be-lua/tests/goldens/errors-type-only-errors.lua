local temper = require("temper-core")

---@param x integer
---@return integer
local function good(x)
  return temper.int32_add(x, 1)
end

---@param x integer
---@return integer
local function mismatched(x)
  local s = x
  return 1
end

---@param s string
---@return integer
local function misspelled(s)
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

---@param x integer
---@return string
local function stored(x)
  local s = x
  return s .. "!"
end

---@return integer
local function returned()
  return "text"
end

---@param x integer
---@return integer
local function passedOn(x)
  return x
end

---@class errors-type-only-errors.Box
---@field value integer
---@field get fun(self: errors-type-only-errors.Box): integer
---@field bad fun(self: errors-type-only-errors.Box): string

---@class errors-type-only-errors.Box.type: temper.Type
---@operator call: errors-type-only-errors.Box
local Box = temper.type("errors-type-only-errors.Box")

---@param this errors-type-only-errors.Box
---@param value integer
function Box.constructor(this, value)
  this.value = value
end

---@param this errors-type-only-errors.Box
---@return integer
function Box.methods.get(this)
  return this.value
end

---@param this errors-type-only-errors.Box
---@return string
function Box.methods.bad(this)
  return this.value
end

local box = Box(3)
print(good(1) .. " " .. unknownType(1) .. " " .. afterUnclosed(2) .. " " .. box:get())
print(mismatched(1) .. " " .. stored(1) .. " " .. returned() .. " " .. passedOn("in") .. " " .. box:bad())
misspelled("a")
print("after")

return { good = good }
