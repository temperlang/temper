local temper = require("temper-core")

---@class panic-causes-uncaught-failure.Problem: temper.Failure

---@class panic-causes-uncaught-failure.Problem.type: temper.Type
local Problem = temper.type("panic-causes-uncaught-failure.Problem", temper.Failure)

---@class panic-causes-uncaught-failure.NotPositive: panic-causes-uncaught-failure.Problem
---@field n integer
---@field message string

---@class panic-causes-uncaught-failure.NotPositive.type: temper.Type
---@operator call: panic-causes-uncaught-failure.NotPositive
local NotPositive = temper.type("panic-causes-uncaught-failure.NotPositive", Problem)

---@param this panic-causes-uncaught-failure.NotPositive
---@param n integer
function NotPositive.constructor(this, n)
  this.n = n
end

---@param this panic-causes-uncaught-failure.NotPositive
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
positive(-1)
print("after")

return {}
