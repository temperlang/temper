local temper = require("temper-core")

local function yay()
  print("yay")
end

local function meh()
  print("meh")
end

local function boo()
  print("boo")
end

---@class type-checked-locals.Problem: temper.Failure

---@class type-checked-locals.Problem.type: temper.Type
local Problem = temper.type("type-checked-locals.Problem", temper.Failure)

---@class type-checked-locals.Oops: type-checked-locals.Problem
---@field message string

---@class type-checked-locals.Oops.type: temper.Type
---@operator call: type-checked-locals.Oops
local Oops = temper.type("type-checked-locals.Oops", Problem)

---@param this type-checked-locals.Oops
function Oops.constructor(this)
end

---@param this type-checked-locals.Oops
---@return string
function Oops.get.message(this)
  return "oops"
end

---@param x integer
---@return integer
local function f(x)
  return x
end

---@param x integer
---@return integer
local function g(x)
  return x
end

---@param x integer
---@return integer
local function i(x)
  return x
end

print("Checkpoint 0")
if not temper.catch(function()
  ---@type integer
  local a
  a = "1"
  error(Oops())
end, Oops) then
  yay()
end
print("Checkpoint 1")
local a2 = 1
yay()
local _ = nil
print("Checkpoint 2")
do
  local a3 = 1
  if not temper.catch(function()
    a3 = "2"
    error(Oops())
  end, Oops) then
    meh()
  end
  if a3 == 1 then
    meh()
  else
    meh()
  end
end
print("Checkpoint 3")
local a4 = 1
a4 = 2
yay()
print("Checkpoint 4")
if not temper.catch(function()
  f(0)
  yay()
end, Oops) then
  boo()
end
if not temper.catch(function()
  f("0")
  meh()
end, Oops) then
  meh()
end
print("Checkpoint 5")
if not temper.catch(function()
  g(0)
  yay()
end, Oops) then
  boo()
end
if not temper.catch(function()
  g("0")
  meh()
end, Oops) then
  meh()
end
print("Checkpoint 6")
if not temper.catch(function()
  i(0)
  yay()
end, Oops) then
  boo()
end
if not temper.catch(function()
  i("0")
  meh()
end, Oops) then
  meh()
end

return {}
