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

---@return integer
local function undeclared()
  return temper.bubble()
end

---@param s string
---@return integer
local function misspelled(s)
  return temper.bubble()
end

---@param s string
---@return integer
local function misspelledMethod(s)
  return temper.bubble()
end

---@return integer
local function wrongArity()
  return temper.bubble()
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
  return temper.bubble()
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

---@class errors.Box
---@field value integer
---@field get fun(self: errors.Box): integer
---@field bad fun(self: errors.Box): string

---@class errors.Box.type: temper.Type
---@operator call: errors.Box
local Box = temper.type("errors.Box")

---@param this errors.Box
---@param value integer
function Box.constructor(this, value)
  this.value = value
end

---@param this errors.Box
---@return integer
function Box.methods.get(this)
  return this.value
end

---@param this errors.Box
---@return string
function Box.methods.bad(this)
  return this.value
end

---@type integer
local a
if not temper.pcall(function()
  a = mismatched(1)
end) then
  a = -1
end
---@type integer
local b
if not temper.pcall(function()
  b = undeclared()
end) then
  b = -1
end
---@type integer
local c
if not temper.pcall(function()
  c = misspelled("a")
end) then
  c = -1
end
---@type integer
local d
if not temper.pcall(function()
  d = misspelledMethod("a")
end) then
  d = -1
end
---@type integer
local e
if not temper.pcall(function()
  e = wrongArity()
end) then
  e = -1
end
local box = Box(3)
---@type string
local f
if not temper.pcall(function()
  f = box:bad()
end) then
  f = "caught"
end
temper.log(good(1) .. " " .. a .. " " .. b .. " " .. c .. " " .. d .. " " .. e)
temper.log(unknownType(1) .. " " .. afterUnclosed(2) .. " " .. box:get() .. " " .. f)
---@type string
local g
if not temper.pcall(function()
  g = stored(1)
end) then
  g = "caught"
end
---@type integer
local h
if not temper.pcall(function()
  h = returned()
end) then
  h = -1
end
---@type integer
local i
if not temper.pcall(function()
  i = passedOn("in")
end) then
  i = -1
end
---@type string
local j
if not temper.pcall(function()
  j = dropped(1)
end) then
  j = "dropped"
end
temper.log(g .. " " .. h .. " " .. i .. " " .. j)

return { good = good }
