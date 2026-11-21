local temper = require("temper-core")

---@type string
local message
---@type boolean
local repeat_

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

---@class named-args.Greet

---@class named-args.Greet.type: temper.Type
---@operator call: named-args.Greet
local Greet = temper.type("named-args.Greet")

---@param this named-args.Greet
---@param message? string|temper.Null
---@param repeat_? boolean|temper.Null
function Greet.constructor(this, message, repeat_)
  if temper.is_null(message) then
    message = "Hi!"
  end
  ---@cast message string
  if temper.is_null(repeat_) then
    repeat_ = false
  end
  ---@cast repeat_ boolean
  temper.log(message)
  if repeat_ then
    temper.log(message)
  end
end

---@return string
local function changeRepeat()
  repeat_ = true
  return message
end

---@return boolean
local function changeMessage()
  message = "Namaste!"
  return repeat_
end

---@class named-args.Person
---@field name string
---@field age integer
---@field toString fun(self: named-args.Person): string
---@field greet fun(self: named-args.Person, greeting?: string|temper.Null)

---@class named-args.Person.type: temper.Type
---@operator call: named-args.Person
local Person = temper.type("named-args.Person")

---@param this named-args.Person
---@param name? string|temper.Null
---@param age? integer|temper.Null
function Person.constructor(this, name, age)
  if temper.is_null(name) then
    name = "Alice"
  end
  ---@cast name string
  if temper.is_null(age) then
    age = 40
  end
  ---@cast age integer
  this.name = name
  this.age = age
end

---@param this named-args.Person
---@return string
function Person.methods.toString(this)
  return "{ class: Person, name: " .. this.name .. ", age: " .. this.age .. " }"
end

---@param this named-args.Person
---@param greeting? string|temper.Null
function Person.methods.greet(this, greeting)
  if temper.is_null(greeting) then
    greeting = "Hi"
  end
  ---@cast greeting string
  temper.log(greeting .. ", " .. this.name)
end

with_tostring(Person)

---@class named-args.CalcOptions
---@field scale integer
---@field origin integer

---@class named-args.CalcOptions.type: temper.Type
---@operator call: named-args.CalcOptions
local CalcOptions = temper.type("named-args.CalcOptions")

---@param this named-args.CalcOptions
---@param scale? integer|temper.Null
---@param origin? integer|temper.Null
function CalcOptions.constructor(this, scale, origin)
  if temper.is_null(scale) then
    scale = 1
  end
  ---@cast scale integer
  if temper.is_null(origin) then
    origin = 0
  end
  ---@cast origin integer
  this.scale = scale
  this.origin = origin
end

---@generic T
---@param n integer
---@param transform fun(arg0: integer): T
---@param options? named-args.CalcOptions|temper.Null
---@return T
local function calc(n, transform, options)
  if temper.is_null(options) then
    options = CalcOptions()
  end
  ---@cast options named-args.CalcOptions
  local origin = options.origin
  local scale = options.scale
  return transform(temper.int32_mul(temper.int32_sub(n, origin), scale))
end

Greet()
Greet("Bye!")
Greet("Hola!", true)
Greet("Salut!")
Greet(temper.null, true)
Greet(temper.null, true)
do
  local arg = true
  Greet("Ni hao!", arg)
end
message = "Hi!"
repeat_ = false
do
  local arg2 = changeMessage()
  local arg3 = changeRepeat()
  Greet(arg3, arg2)
end
temper.log(Person():toString())
temper.log(Person("Bob"):toString())
temper.log(Person("Carrie", 50):toString())
Person("Dan"):greet("Hallo")
temper.log(calc(2, function(n) return tostring(n) end))
temper.log(calc(2, function(n) return tostring(n) end, CalcOptions(3, 4)))
temper.log(calc(2, function(n) return tostring(n) end, CalcOptions(3)))
temper.log(calc(2, function(n) return tostring(n) end, CalcOptions(temper.null, 4)))

---@param n integer
---@return table
local function fn5(n)
  temper.log(tostring(n))
  return temper.empty()
end

calc(2, fn5, CalcOptions(-2))

return { Greet = Greet }
