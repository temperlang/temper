local temper = require("temper-core")

local function yay()
  temper.log("yay")
end

local function meh()
  temper.log("meh")
end

local function boo()
  temper.log("boo")
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

temper.log("Checkpoint 0")
if not temper.pcall(function()
  ---@type integer
  local a
  a = "1"
  temper.bubble()
end) then
  yay()
end
temper.log("Checkpoint 1")
local a2 = 1
yay()
local _ = nil
temper.log("Checkpoint 2")
do
  local a3 = 1
  if not temper.pcall(function()
    a3 = "2"
    temper.bubble()
    boo()
  end) then
    meh()
  end
  if a3 == 1 then
    meh()
  else
    meh()
  end
end
temper.log("Checkpoint 3")
local a4 = 1
a4 = 2
yay()
temper.log("Checkpoint 4")
if not temper.pcall(function()
  f(0)
  yay()
end) then
  boo()
end
if not temper.pcall(function()
  f("0")
  meh()
end) then
  meh()
end
temper.log("Checkpoint 5")
if not temper.pcall(function()
  g(0)
  yay()
end) then
  boo()
end
if not temper.pcall(function()
  g("0")
  meh()
end) then
  meh()
end
temper.log("Checkpoint 6")
if not temper.pcall(function()
  i(0)
  yay()
end) then
  boo()
end
if not temper.pcall(function()
  i("0")
  meh()
end) then
  meh()
end

return {}
