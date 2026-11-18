local temper = require("temper-core")
local Stringable = require("object-literals/extra-class").Stringable
local Circle = require("object-literals/extra-class").Circle
local Rectangle = require("object-literals/extra-class").Rectangle

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

---@class object-literals.Person: object-literals.extra-class.Stringable
---@field name string
---@field age integer
---@field toString fun(self: object-literals.Person): string
---@field eq fun(self: object-literals.Person, other: object-literals.Person): boolean

---@class object-literals.Person.type: temper.Type
---@operator call: object-literals.Person
local Person = temper.type("object-literals.Person", Stringable)

---@param this object-literals.Person
---@param name string
---@param age? integer|temper.Null
function Person.constructor(this, name, age)
  if temper.is_null(age) then
    age = -100
  end
  ---@cast age integer
  this.name = name
  this.age = age
end

---@param this object-literals.Person
---@return string
function Person.methods.toString(this)
  local ageText = temper.listed_join({ 0 }, "", function(unused) return tostring(this.age) end)
  return "Person " .. this.name .. " of age " .. ageText
end

---@param this object-literals.Person
---@param other object-literals.Person
---@return boolean
function Person.methods.eq(this, other)
  if this.name == other.name then
    return this.age == other.age
  else
    return false
  end
end

with_tostring(Person)

---@class object-literals.Whatever
---@field blah string

---@class object-literals.Whatever.type: temper.Type
---@operator call: object-literals.Whatever
local Whatever = temper.type("object-literals.Whatever")

---@param this object-literals.Whatever
---@param blech integer
function Whatever.constructor(this, blech)
  this.blah = tostring(blech)
end

---@class object-literals.Moreover
---@field blech integer
---@field bling integer
---@field toString fun(self: object-literals.Moreover): string

---@class object-literals.Moreover.type: temper.Type
---@operator call: object-literals.Moreover
local Moreover = temper.type("object-literals.Moreover")

---@param this object-literals.Moreover
---@param blech? integer|temper.Null
---@param bling? integer|temper.Null
function Moreover.constructor(this, blech, bling)
  if temper.is_null(blech) then
    blech = 0
  end
  ---@cast blech integer
  if temper.is_null(bling) then
    bling = 0
  end
  ---@cast bling integer
  this.blech = blech
  this.bling = bling
end

---@param this object-literals.Moreover
---@return string
function Moreover.methods.toString(this)
  return this.blech .. " " .. this.bling
end

with_tostring(Moreover)

local function bother()
  local whatIAm = Moreover(1)
  local moreover = Moreover(2, 3)
  print("Moreover: " .. moreover:toString())
  local moreover2 = Moreover(4)
  print("Moreover again: " .. moreover2:toString())
  local moreover3 = Moreover(temper.null, 7)
  print("Moreover yet again: " .. moreover3:toString())
  local whatever = Whatever(5)
  print("Whatever: " .. whatever.blah)
end

local person1 = Person("Alice", 50)
local person2 = Person("Bob", 45)
local circle1 = Circle(2)
local circle2 = Circle(1)
local things = { person1, person2, circle1, circle2 }

---@param thing object-literals.extra-class.Stringable
---@return integer
local function fn(thing)
  print(thing:toString())
  return 0
end

temper.listed_map(things, fn)
Whatever(456)
Whatever(321)
Rectangle(7, 8)
bother()
local whatever = Whatever(6)
print("Whatever again: " .. whatever.blah)
local personAgain = Person("Carrie", 40)
local name = personAgain.name
local someAge = personAgain.age
print("name is " .. name .. " and age is " .. someAge)
local personMore = Person(name, 35)
print(personMore:toString())
local twin = Person(person1.name, person1.age)
print("Some are twins: " .. tostring(person1:eq(twin)))
print("All are twins: " .. tostring(person2:eq(twin)))

return { Person = Person }
