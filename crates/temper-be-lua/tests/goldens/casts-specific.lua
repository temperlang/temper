local temper = require("temper-core")

---@class specific.AlphaInterface

---@class specific.AlphaInterface.type: temper.Type
local AlphaInterface = temper.type("specific.AlphaInterface")

---@class specific.BravoClass: specific.AlphaInterface

---@class specific.BravoClass.type: temper.Type
---@operator call: specific.BravoClass
local BravoClass = temper.type("specific.BravoClass", AlphaInterface)

---@param this specific.BravoClass
function BravoClass.constructor(this)
end

---@class specific.CharlieClass: specific.AlphaInterface

---@class specific.CharlieClass.type: temper.Type
---@operator call: specific.CharlieClass
local CharlieClass = temper.type("specific.CharlieClass", AlphaInterface)

---@param this specific.CharlieClass
function CharlieClass.constructor(this)
end

---@param instance specific.AlphaInterface
local function checkB(instance)
  ---@type string
  local t
  if not temper.pcall(function()
    temper.cast_to(instance, BravoClass)
    t = "yep"
  end) then
    t = "nope"
  end
  temper.log(t)
end

---@param instance specific.AlphaInterface
local function checkIfB(instance)
  temper.log(temper.instance_of(instance, BravoClass) and "yep" or "nope")
end

checkB(BravoClass())
checkB(CharlieClass())
local items = { BravoClass(), CharlieClass() }
checkB(temper.list_get(items, 0))
checkB(temper.list_get(items, 1))
checkIfB(temper.list_get(items, 0))
checkIfB(temper.list_get(items, 1))

return {}
