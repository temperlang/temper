local temper = require("temper-core")

---@class panic-causes-panic-passing-orelse-panic.Problem: temper.Failure

---@class panic-causes-panic-passing-orelse-panic.Problem.type: temper.Type
local Problem = temper.type("panic-causes-panic-passing-orelse-panic.Problem", temper.Failure)

---@class panic-causes-panic-passing-orelse-panic.NotPositive: panic-causes-panic-passing-orelse-panic.Problem
---@field n integer
---@field message string

---@class panic-causes-panic-passing-orelse-panic.NotPositive.type: temper.Type
---@operator call: panic-causes-panic-passing-orelse-panic.NotPositive
local NotPositive = temper.type("panic-causes-panic-passing-orelse-panic.NotPositive", Problem)

---@param this panic-causes-panic-passing-orelse-panic.NotPositive
---@param n integer
function NotPositive.constructor(this, n)
  this.n = n
end

---@param this panic-causes-panic-passing-orelse-panic.NotPositive
---@return string
function NotPositive.get.message(this)
  return "not positive (n: " .. this.n .. ")"
end

---@param n integer
---@return integer
local function positive(n)
  if n <= 0 then
    error(NotPositive(n))
  end
  return n
end

---@param ls integer[]
---@param i integer
---@return integer
local function read(ls, i)
  positive(temper.int32_add(i, 1))
  return temper.list_get(ls, i)
end

print("before")
---@type integer
local r
local t, t_1 = temper.catch(function()
  r = read({ 1, 2 }, 5)
end, NotPositive)
if not t then
  error("panic\ncaused by: " .. tostring(t_1))
end
print("after " .. r)

return {}
