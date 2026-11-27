local temper = require("temper-core")

---@class panic-causes-panic-in-orelse.Problem: temper.Failure

---@class panic-causes-panic-in-orelse.Problem.type: temper.Type
local Problem = temper.type("panic-causes-panic-in-orelse.Problem", temper.Failure)

---@class panic-causes-panic-in-orelse.NotPositive: panic-causes-panic-in-orelse.Problem
---@field n integer
---@field message string

---@class panic-causes-panic-in-orelse.NotPositive.type: temper.Type
---@operator call: panic-causes-panic-in-orelse.NotPositive
local NotPositive = temper.type("panic-causes-panic-in-orelse.NotPositive", Problem)

---@param this panic-causes-panic-in-orelse.NotPositive
---@param n integer
function NotPositive.constructor(this, n)
  this.n = n
end

---@param this panic-causes-panic-in-orelse.NotPositive
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

print("before")
---@type integer
local p
local t, t_1 = temper.catch(function()
  p = positive(-1)
end, NotPositive)
if not t then
  error("panic\ncaused by: " .. tostring(t_1))
end
print("after " .. p)

return {}
