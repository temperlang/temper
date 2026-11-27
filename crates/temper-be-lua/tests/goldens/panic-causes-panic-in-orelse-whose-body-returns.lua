local temper = require("temper-core")

---@class panic-causes-panic-in-orelse-whose-body-returns.Problem: temper.Failure

---@class panic-causes-panic-in-orelse-whose-body-returns.Problem.type: temper.Type
local Problem = temper.type("panic-causes-panic-in-orelse-whose-body-returns.Problem", temper.Failure)

---@class panic-causes-panic-in-orelse-whose-body-returns.NotPositive: panic-causes-panic-in-orelse-whose-body-returns.Problem
---@field n integer
---@field message string

---@class panic-causes-panic-in-orelse-whose-body-returns.NotPositive.type: temper.Type
---@operator call: panic-causes-panic-in-orelse-whose-body-returns.NotPositive
local NotPositive = temper.type("panic-causes-panic-in-orelse-whose-body-returns.NotPositive", Problem)

---@param this panic-causes-panic-in-orelse-whose-body-returns.NotPositive
---@param n integer
function NotPositive.constructor(this, n)
  this.n = n
end

---@param this panic-causes-panic-in-orelse-whose-body-returns.NotPositive
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

---@param n integer
---@return integer
local function checked(n)
  local t, t_1, t_2 = temper.catch(function()
    local p = positive(n)
    if p == 1 then
      return 0, "return"
    end
  end, NotPositive)
  if not t then
    error("panic\ncaused by: " .. tostring(t_1))
  elseif t_2 == "return" then
    return t_1
  end
  return n
end

print("before")
print("after " .. checked(-1))

return {}
