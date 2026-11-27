local temper = require("temper-core")
local exports = {}

---@param next_ integer
---@param math_ integer
---@return integer
local function type_(next_, math_)
  exports.pairs = temper.int32_add(exports.pairs, 1)
  return temper.int32_mul(next_, math_)
end

---@class globals.Problem: temper.Failure

---@class globals.Problem.type: temper.Type
local Problem = temper.type("globals.Problem", temper.Failure)

---@class globals.Odd: globals.Problem
---@field message string

---@class globals.Odd.type: temper.Type
---@operator call: globals.Odd
local Odd = temper.type("globals.Odd", Problem)

---@param this globals.Odd
function Odd.constructor(this)
end

---@param this globals.Odd
---@return string
function Odd.get.message(this)
  return "odd"
end

---@param error_ integer
---@return integer
local function half(error_)
  if temper.int32_mod(error_, 2) ~= 0 then
    error(Odd())
  end
  return temper.int32_div(error_, 2)
end

---@param error_ integer
---@param table_ integer[]
---@return integer
local function select_(error_, table_)
  ---@type integer
  local string_
  local t, t_1 = temper.catch(function()
    string_ = half(error_)
  end, Odd)
  if not t then
    error("panic\ncaused by: " .. tostring(t_1))
  end
  local select_ = 0
  do
    local length = temper.list_length(table_)
    local i = 0
    while i < length do
      local next_ = temper.list_get(table_, i)
      i = temper.int32_add(i, 1)
      select_ = temper.int32_add(select_, next_)
    end
  end
  return temper.int32_add(select_, string_)
end

---@param math_ number
---@return number
local function huge(math_)
  return math_ * math.huge
end

local table_ = { 1, 2, 3 }
local string_ = "s"
exports.pairs = 0
string_ = type_(2, 3) .. " " .. select_(4, table_)
print(string_ .. " " .. temper.float64_tostring(huge(2.0)) .. " " .. tostring(type_(1, 1) == select_(2, {})) .. " " .. exports.pairs)
do
  local list = { "ab", "c" }
  local length = temper.list_length(list)
  local i = 0
  while i < length do
    local string_2 = temper.list_get(list, i)
    i = temper.int32_add(i, 1)
    print(string_2 .. "!")
  end
end

exports.string = string_
exports.type = type_
return exports
