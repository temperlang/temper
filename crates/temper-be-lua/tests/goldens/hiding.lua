local temper = require("temper-core")

---@type integer
local calls

---@param goto_ integer
---@return integer
local function end_(goto_)
  return temper.int32_add(goto_, 1)
end

---@param repeat_ boolean
---@param nil_ boolean
---@return boolean
local function print_(repeat_, nil_)
  return not repeat_ and nil_
end

---@param b boolean
---@return boolean
local function tostring_(b)
  calls = temper.int32_add(calls, 1)
  return b
end

---@param a boolean
---@param b boolean
---@return string
local function math_(a, b)
  return tostring(temper.bool_cmp(a, tostring_(b)) <= 0) .. " " .. tostring(a and not b) .. " " .. tostring(a or not b)
end

---@param b boolean
---@return string
local function exports_(b)
  return (b and "yes" or "no") .. "!"
end

---@param b boolean
---@param c boolean
---@return string
local function module_(b, c)
  return tostring(b or c)
end

---@param b boolean
---@param c boolean
---@return string
local function require_(b, c)
  return tostring(not b and c)
end

---@param b boolean
---@param c boolean
---@param d boolean
---@return string
local function _ENV_(b, c, d)
  return tostring(b and c or not b and d)
end

---@param n integer
---@return string
local function shifts(n)
  return temper.shl32(1, n) .. " " .. temper.shr32(-8, n) .. " " .. temper.ushr32(-8, temper.int32_sub(n, 4))
end

local temper_ = "t"
calls = 0
temper.log(end_(1) .. " " .. temper_ .. " " .. tostring(print_(false, true)) .. " " .. tostring(print_(true, false)))
temper.log(math_(true, false) .. " " .. calls .. " " .. exports_(true) .. " " .. exports_(false))
temper.log(module_(false, true) .. " " .. require_(false, true) .. " " .. _ENV_(true, false, true))
temper.log('"it\'s" \\ a\0001 \226\128\174x')
temper.log(shifts(33))

return {}
