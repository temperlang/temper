local temper = require("temper-core")

local sumOf3

local _connected = {}

do
  local s = require("work._support")

  ---@param i integer
  ---@param j integer
  ---@param bonus integer
  ---@return integer
  function _connected.sum(i, j, bonus)
    return sumOf3(i, j, bonus)
  end

  ---@param hidden work.Hidden TODO Actually define types in our Lua.
  ---@param j integer
  ---@return integer
  function _connected.prod(hidden, j)
    local support = s.Support.new()
    return support:prod(hidden.i, j)
  end

  ---@param s string|nil Or actually temper.null instead of nil ...
  ---@return integer
  function _connected.length(s)
    if s == temper.null then
      return -1
    else
      return #s
    end
  end
end

---@param a integer
---@param b integer
---@param c integer
---@return integer
function sumOf3(a, b, c)
  return temper.int32_add(temper.int32_add(a, b), c)
end

---@param i integer
---@param j integer
---@param bonus? integer|temper.Null
---@return integer
local function sum(i, j, bonus)
  if temper.is_null(bonus) then
    bonus = 0
  end
  ---@cast bonus integer
  return temper.int32_add(sumOf3(i, j, bonus), 1000)
end

---@param string_? string|temper.Null
---@return integer
local function length(string_)
  if string_ == nil then
    string_ = temper.null
  end
  if temper.is_null(string_) then
    return 999
  else
    return temper.int32_add(temper.string_countbetween(string_ --[[@as string]], 1, temper.string_end(string_ --[[@as string]])), 1000)
  end
end

---@param i integer
---@return integer
local function twice(i)
  return temper.int32_add(temper.int32_mul(i, 2), 1000)
end

---@class work.Hidden
---@field i integer

---@class work.Hidden.type: temper.Type
---@operator call: work.Hidden
local Hidden = temper.type("work.Hidden")

---@param this work.Hidden
---@param i integer
function Hidden.constructor(this, i)
  this.i = i
end

---@param hidden work.Hidden
---@param j integer
---@return integer
local function prod(hidden, j)
  return _connected.prod(hidden, j)
end

---@class work.Hider
---@field hidden work.Hidden
---@field plus fun(self: work.Hider, j: integer): integer
---@field times fun(self: work.Hider, j: integer): integer

---@class work.Hider.type: temper.Type
---@operator call: work.Hider
local Hider = temper.type("work.Hider")

---@param this work.Hider
---@param i integer
function Hider.constructor(this, i)
  this.hidden = Hidden(i)
end

---@param this work.Hider
---@param j integer
---@return integer
function Hider.methods.plus(this, j)
  return sum(this.hidden.i, j)
end

---@param this work.Hider
---@param j integer
---@return integer
function Hider.methods.times(this, j)
  return prod(this.hidden, j)
end

print("sum(1, 2): " .. sum(1, 2))
print('length "" vs null vs (): ' .. length("") .. " vs " .. length(temper.null) .. " vs " .. length())
print("twice(5): " .. twice(5))
local isConnected = sum(1, 2) == 3
if isConnected then
  print("prod(new Hidden(1), 2): " .. prod(Hidden(1), 2))
end
local hider = Hider(3)
print("new Hider(3).plus(4): " .. hider:plus(4))
if isConnected then
  print("new Hider(3).times(4): " .. hider:times(4))
end

return { sum = sum, length = length, twice = twice, Hider = Hider }
