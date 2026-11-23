local temper = require("temper-core")

---@class core-classes.Thing

---@class core-classes.Thing.type: temper.Type
---@operator call: core-classes.Thing
local Thing = temper.type("core-classes.Thing")

---@param this core-classes.Thing
function Thing.constructor(this)
end

---@param x any
---@return string
local function describe(x)
  if temper.instance_of(x, Thing) then
    return "thing"
  end
  ---@type string
  local t
  if not temper.pcall(function()
    temper.cast_to(x, Thing)
    t = "thing, as"
  end) then
    t = "other"
  end
  return t
end

---@param x any
---@return boolean
local function isThing(x)
  return temper.instance_of(x, Thing)
end

local bits = temper.densebitvector_constructor(8)
local ints = { 1, 2 }
print(describe(Thing()))
print(describe(temper.pair_constructor("a", 1)))
print(describe(bits))
print(describe("s"))
print(describe(1))
print(describe(ints))
print(describe(temper.map_constructor({ temper.pair_constructor("m", 2) })))
print(describe(temper.listbuilder_constructor()))
print(describe(temper.deque_constructor()))
print(tostring(isThing(Thing())))
print(tostring(isThing(bits)))

return {}
