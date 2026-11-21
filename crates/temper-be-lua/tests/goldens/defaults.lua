local temper = require("temper-core")
local Pad = require("defaults/helpers").Pad
local wrap = require("defaults/helpers").wrap

---@type integer
local calls

---@return integer
local function next_()
  calls = temper.int32_add(calls, 1)
  return calls
end

---@param start integer
---@param end_? integer|temper.Null
---@param step? integer|temper.Null
---@return string
local function range(start, end_, step)
  if temper.is_null(end_) then
    end_ = temper.int32_add(start, 10)
  end
  ---@cast end_ integer
  if temper.is_null(step) then
    step = next_()
  end
  ---@cast step integer
  return start .. ".." .. end_ .. " by " .. step
end

---@param text string
---@param mark? string|temper.Null
---@return string
local function tag(text, mark)
  if mark == nil then
    mark = temper.null
  end
  if temper.is_null(mark) then
    return text
  else
    return (mark --[[@as string]]) .. text
  end
end

---@param name? string|temper.Null
---@return string
local function named(name)
  if temper.is_null(name) then
    name = "nobody"
  end
  if temper.is_null(name) then
    return "null"
  else
    return name --[[@as string]]
  end
end

---@class defaults.Greeter
---@field greeting string
---@field punct string
---@field greet fun(self: defaults.Greeter, name?: string|temper.Null, times?: integer|temper.Null): string

---@class defaults.Greeter.type: temper.Type
---@operator call: defaults.Greeter
local Greeter = temper.type("defaults.Greeter")

---@param this defaults.Greeter
---@param greeting? string|temper.Null
---@param punct? string|temper.Null
function Greeter.constructor(this, greeting, punct)
  if temper.is_null(greeting) then
    greeting = "Hello"
  end
  ---@cast greeting string
  if temper.is_null(punct) then
    punct = "!"
  end
  ---@cast punct string
  this.greeting = greeting
  this.punct = punct
end

---@param this defaults.Greeter
---@param name? string|temper.Null
---@param times? integer|temper.Null
---@return string
function Greeter.methods.greet(this, name, times)
  if temper.is_null(name) then
    name = "world"
  end
  ---@cast name string
  if temper.is_null(times) then
    times = 1
  end
  ---@cast times integer
  local out = ""
  do
    local i = 0
    while i < times do
      out = out .. this.greeting .. ", " .. name .. this.punct
      i = temper.int32_add(i, 1)
    end
  end
  return out
end

---@param text string
---@param mark? string|temper.Null
---@return string
function Greeter.shout(text, mark)
  if temper.is_null(mark) then
    mark = "!"
  end
  ---@cast mark string
  return text .. mark .. mark
end

---@class defaults.Counter
---@field count integer
---@field add fun(self: defaults.Counter, by?: integer|temper.Null): integer

---@class defaults.Counter.type: temper.Type
---@operator call: defaults.Counter
local Counter = temper.type("defaults.Counter")

---@param this defaults.Counter
---@param start? integer|temper.Null
function Counter.constructor(this, start)
  if temper.is_null(start) then
    start = 10
  end
  ---@cast start integer
  this.count = start
end

---@param this defaults.Counter
---@param by? integer|temper.Null
---@return integer
function Counter.methods.add(this, by)
  if temper.is_null(by) then
    by = 1
  end
  ---@cast by integer
  this.count = temper.int32_add(this.count, by)
  return this.count
end

calls = 0
temper.log(range(1))
temper.log(range(1, 5))
temper.log(range(1, 5, 3))
temper.log(range(2, temper.null, 7))
temper.log("calls " .. calls)
temper.log(tag("plain"))
temper.log(tag("marked", "#"))
temper.log(named())
temper.log(named(temper.null))
temper.log(named("Ada"))
temper.log(Greeter():greet())
temper.log(Greeter("Hi"):greet("you", 2))
temper.log(Greeter(temper.null, "?"):greet(temper.null, 2))
temper.log(Greeter.shout("hey"))
local counter = Counter()
counter:add()
temper.log("count " .. counter:add(5) .. " " .. Counter(1):add())
temper.log(wrap("a"))
temper.log(wrap("b", "<"))
temper.log(wrap("c", "<", ">"))
local pad = Pad()
temper.log(pad:apply("x"))
temper.log(Pad(0, 3):apply("y", "-"))

return {}
