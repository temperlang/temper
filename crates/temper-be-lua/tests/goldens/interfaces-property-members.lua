local temper = require("temper-core")

---Gives a class's instances a `__tostring` that calls their `toString`.
---@param class temper.Type
local function with_tostring(class)
  local construct = class.constructor
  ---@type table?
  local meta = nil
  class.constructor = function(this, ...)
    if meta == nil then
      meta = {}
      for k, v in _G.pairs(_G.getmetatable(this)) do
        meta[k] = v
      end
      meta.__tostring = function(self)
        return self:toString()
      end
    end
    _G.setmetatable(this, meta)
    construct(this, ...)
  end
end

---@class property-members.I
---@field x string

---@class property-members.I.type: temper.Type
local I = temper.type("property-members.I")

---@class property-members.J: property-members.I

---@class property-members.J.type: temper.Type
local J = temper.type("property-members.J", I)

---@generic IT: property-members.I
---@param a IT
---@param b IT
---@return IT
local function leastX(a, b)
  if a.x < b.x then
    return a
  else
    return b
  end
end

---@class property-members.C: property-members.J
---@field x string

---@class property-members.C.type: temper.Type
---@operator call: property-members.C
local C = temper.type("property-members.C", J)

---@param this property-members.C
---@param x string
function C.constructor(this, x)
  this.x = x
end

---@class property-members.D
---@field thing any

---@class property-members.D.type: temper.Type
---@operator call: property-members.D
local D = temper.type("property-members.D")

---@param this property-members.D
---@param thing any
function D.constructor(this, thing)
  this.thing = thing
end

---@class property-members.SI
---@field thing any

---@class property-members.SI.type: temper.Type
local SI = temper.type("property-members.SI")

---@class property-members.SSub: property-members.SI
---@field thing any

---@class property-members.SSub.type: temper.Type
---@operator call: property-members.SSub
local SSub = temper.type("property-members.SSub", SI)

---@param this property-members.SSub
---@param thing any
function SSub.constructor(this, thing)
  this.thing = thing
end

---@class property-members.Holder
---@field held any
---@field heldAnd fun(self: property-members.Holder, other: any): any[]

---@class property-members.Holder.type: temper.Type
local Holder = temper.type("property-members.Holder")

---@param this property-members.Holder
---@param other any
---@return any[]
function Holder.methods.heldAnd(this, other)
  return { this.held, other }
end

---@class property-members.Labeled: property-members.Holder
---@field label fun(self: property-members.Labeled, l: any): string

---@class property-members.Labeled.type: temper.Type
local Labeled = temper.type("property-members.Labeled", Holder)

---@class property-members.LabeledSub: property-members.Labeled
---@field held property-members.C
---@field label fun(self: property-members.LabeledSub, l: string): string

---@class property-members.LabeledSub.type: temper.Type
---@operator call: property-members.LabeledSub
local LabeledSub = temper.type("property-members.LabeledSub", Labeled)

---@param this property-members.LabeledSub
---@param held property-members.C
function LabeledSub.constructor(this, held)
  this.held = held
end

---@param this property-members.LabeledSub
---@param l string
---@return string
function LabeledSub.methods.label(this, l)
  return l .. ": " .. this.held.x
end

---@class property-members.High
---@field toString fun(self: property-members.High): string
---@field eq fun(self: property-members.High, other: property-members.High): boolean

---@class property-members.High.type: temper.Type
local High = temper.type("property-members.High")

---@class property-members.Low1: property-members.High
---@field i integer
---@field toString fun(self: property-members.Low1): string
---@field eq fun(self: property-members.Low1, other: property-members.High): boolean

---@class property-members.Low1.type: temper.Type
---@operator call: property-members.Low1
local Low1 = temper.type("property-members.Low1", High)

---@param this property-members.Low1
---@param i integer
function Low1.constructor(this, i)
  this.i = i
end

---@param this property-members.Low1
---@return string
function Low1.methods.toString(this)
  return "Low1(" .. this.i .. ")"
end

---@param this property-members.Low1
---@param other property-members.High
---@return boolean
function Low1.methods.eq(this, other)
  if temper.instance_of(other, Low1) then
    return this.i == (other --[[@as property-members.Low1]]).i
  else
    return false
  end
end

with_tostring(Low1)

---@class property-members.Mid: property-members.High

---@class property-members.Mid.type: temper.Type
local Mid = temper.type("property-members.Mid", High)

---@class property-members.Low2: property-members.Mid
---@field s string
---@field toString fun(self: property-members.Low2): string
---@field eq fun(self: property-members.Low2, other: property-members.High): boolean

---@class property-members.Low2.type: temper.Type
---@operator call: property-members.Low2
local Low2 = temper.type("property-members.Low2", Mid)

---@param this property-members.Low2
---@param s string
function Low2.constructor(this, s)
  this.s = s
end

---@param this property-members.Low2
---@return string
function Low2.methods.toString(this)
  return "Low2(" .. this.s .. ")"
end

---@param this property-members.Low2
---@param other property-members.High
---@return boolean
function Low2.methods.eq(this, other)
  if temper.instance_of(other, Low2) then
    return this.s == (other --[[@as property-members.Low2]]).s
  else
    return false
  end
end

with_tostring(Low2)

---@class property-members.Low3: property-members.Mid
---@field toString fun(self: property-members.Low3): string
---@field eq fun(self: property-members.Low3, other: property-members.High): boolean

---@class property-members.Low3.type: temper.Type
---@operator call: property-members.Low3
local Low3 = temper.type("property-members.Low3", Mid)

---@param this property-members.Low3
function Low3.constructor(this)
end

---@param this property-members.Low3
---@return string
function Low3.methods.toString(this)
  return "Low3"
end

---@param this property-members.Low3
---@param other property-members.High
---@return boolean
function Low3.methods.eq(this, other)
  return temper.instance_of(other, Low3)
end

with_tostring(Low3)

local a = C("foo")
local b = C("bar")
print("leastX of " .. a.x .. " and " .. b.x .. " is " .. leastX(a, b).x .. ".")
local i = a
print("As an I, a.x is " .. i.x .. ".")
local di = D(a)
local dj = D(a)
print("Generically, x is " .. di.thing.x .. ".")
print("Genericallier, x is still " .. dj.thing.x .. ".")
local si = SSub(a)
print("Sealed thing is " .. si.thing.x .. ".")
local labeled = LabeledSub(a)
local heldAnd = labeled:heldAnd(b)
print("Labeled thing is " .. labeled:label("first") .. ".")
print("Held are " .. labeled.held.x .. " and " .. temper.list_get(heldAnd, 1).x .. ".")
local high1 = Low1(11)
local high1b = Low1(12)
local high1c = Low1(12)
local high2 = Low2("two")
local high2b = Low2("to be")
local high2c = Low2("to be")
local high3 = Low3()
local high3b = Low3()
local pairs_ = { temper.pair_constructor(high1, high1b), temper.pair_constructor(high1b, high1c), temper.pair_constructor(high1, high2), temper.pair_constructor(high2, high2b), temper.pair_constructor(high2b, high2c), temper.pair_constructor(high1, high3), temper.pair_constructor(high3, high3b) }
do
  local length = temper.list_length(pairs_)
  local i2 = 0
  while i2 < length do
    local pair = temper.list_get(pairs_, i2)
    i2 = temper.int32_add(i2, 1)
    print(pair.key:toString() .. " == " .. pair.value:toString() .. ": " .. tostring(pair.key:eq(pair.value)))
  end
end

return { I = I, J = J, SI = SI }
