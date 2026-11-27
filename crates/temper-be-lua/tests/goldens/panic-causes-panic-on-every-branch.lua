local temper = require("temper-core")

---@class panic-causes-panic-on-every-branch.Problem: temper.Failure

---@class panic-causes-panic-on-every-branch.Problem.type: temper.Type
local Problem = temper.type("panic-causes-panic-on-every-branch.Problem", temper.Failure)

---@class panic-causes-panic-on-every-branch.NotPositive: panic-causes-panic-on-every-branch.Problem
---@field n integer
---@field message string

---@class panic-causes-panic-on-every-branch.NotPositive.type: temper.Type
---@operator call: panic-causes-panic-on-every-branch.NotPositive
local NotPositive = temper.type("panic-causes-panic-on-every-branch.NotPositive", Problem)

---@param this panic-causes-panic-on-every-branch.NotPositive
---@param n integer
function NotPositive.constructor(this, n)
  this.n = n
end

---@param this panic-causes-panic-on-every-branch.NotPositive
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
local k
if not temper.catch(function()
  k = positive(1)
end, NotPositive) then
  k = 0
end
if k > 0 then
  error("panic")
else
  local t, t_1 = temper.catch(function()
    positive(-1)
  end, NotPositive)
  if not t then
    error("panic\ncaused by: " .. tostring(t_1))
  end
  error("panic")
end
print("after")

return {}
