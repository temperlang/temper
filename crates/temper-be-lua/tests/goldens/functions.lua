local temper = require("temper-core")

---@class functions.Counter
---@field apply fun(self: functions.Counter): integer

---@class functions.Counter.type: temper.Type
local Counter = temper.type("functions.Counter")

---@class functions.IntOp
---@field apply fun(self: functions.IntOp, x: integer): integer

---@class functions.IntOp.type: temper.Type
local IntOp = temper.type("functions.IntOp")

---@param start integer
---@return fun(): integer
local function makeCounter(start)
  local count = start
  ---@return integer
  local function fn()
    count = temper.int32_add(count, 1)
    return count
  end
  return fn
end

---@param f fun(arg0: integer): integer
---@return fun(arg0: integer): integer
local function twice(f)
  return function(x) return f(f(x)) end
end

---@param f fun(arg0: integer): integer
---@param x integer
---@return integer
local function applyTo(f, x)
  return f(x)
end

---@param x integer
---@return integer
local function addOne(x)
  return temper.int32_add(x, 1)
end

---@class functions.Scaler

---@class functions.Scaler.type: temper.Type
---@operator call: functions.Scaler
local Scaler = temper.type("functions.Scaler")

---@param this functions.Scaler
function Scaler.constructor(this)
end

---@param x integer
---@return integer
function Scaler.triple(x)
  return temper.int32_mul(x, 3)
end

---@class functions.Button
---@field label string
---@field onPress fun(): integer
---@field press fun(self: functions.Button): string

---@class functions.Button.type: temper.Type
---@operator call: functions.Button
local Button = temper.type("functions.Button")

---@param this functions.Button
---@param label string
---@param onPress fun(): integer
function Button.constructor(this, label, onPress)
  this.label = label
  this.onPress = onPress
end

---@param this functions.Button
---@return string
function Button.methods.press(this)
  return this.label .. " " .. this.onPress()
end

---@param steps integer
---@param body fun(): temper.Generator
local function drive(steps, body)
  local g = body()
  do
    local s = 0
    while s < steps do
      temper.generator_next(g)
      s = temper.int32_add(s, 1)
    end
  end
end

---@param xs integer[]
---@param least integer
---@return integer
local function sumFrom(xs, least)
  local sum = 0
  do
    local length = temper.list_length(xs)
    local i = 0
    while i < length do
      local x = temper.list_get(xs, i)
      i = temper.int32_add(i, 1)
      if x < least then
        goto continue
      end
      sum = temper.int32_add(sum, x)
      ::continue::
    end
  end
  return sum
end

---@param s string
---@return integer
local function doubles(s)
  local n = 0
  local end_ = temper.string_end(s)
  do
    local i = 1
    local prev = i
    while i - end_ < 0 do
      if prev - i < 0 and temper.string_get(s, prev) == temper.string_get(s, i) then
        n = temper.int32_add(n, 1)
      end
      prev = i
      i = temper.string_next(s, i)
    end
  end
  return n
end

---@param x integer
---@return integer
local function climb(x)
  ---@type integer
  local d
  if x == 0 then
    ---@param n integer
    ---@return integer
    local function down(n)
      if n <= x then
        return 0
      else
        return temper.int32_add(down(temper.int32_sub(n, 1)), 1)
      end
    end
    d = down(3)
  else
    return -1
  end
  d = temper.int32_add(d, 1)
  if d == 4 then
    local top = temper.int32_add(d, 1)
    ---@param n integer
    ---@return integer
    local function up(n)
      if n >= top then
        return n
      else
        return up(temper.int32_add(n, 1))
      end
    end
    return up(d)
  else
    return d
  end
end

---@class functions.Gate
---@field check (fun(arg0: integer): integer)|temper.Null
---@field allows fun(self: functions.Gate, n: integer): boolean
---@field passed fun(self: functions.Gate, n: integer): integer

---@class functions.Gate.type: temper.Type
---@operator call: functions.Gate
local Gate = temper.type("functions.Gate")

---@param this functions.Gate
function Gate.constructor(this)
  this.check = temper.null
end

---@param this functions.Gate
---@param n integer
---@return boolean
function Gate.methods.allows(this, n)
  local c = this.check
  if temper.is_null(c) then
    return true
  else
    return (c --[[@as fun(arg0: integer): integer]])(n) > 0
  end
end

---@param this functions.Gate
---@param n integer
---@return integer
function Gate.methods.passed(this, n)
  local c = this.check
  if not temper.is_null(c) then
    return (c --[[@as fun(arg0: integer): integer]])(n)
  else
    return n
  end
end

---@param s string
---@return boolean
local function stringIsPalindrome(s)
  local i = 1
  local j = temper.string_end(s)
  while i - j < 0 do
    j = temper.string_prev(s, j)
    if temper.string_get(s, i) ~= temper.string_get(s, j) then
      return false
    end
    i = temper.string_next(s, i)
  end
  return true
end

---@param n integer|temper.Null
---@return integer
local function intOrZero(n)
  if temper.is_null(n) then
    return 0
  else
    return n --[[@as integer]]
  end
end

---@generic T
---@param ls T[]
---@param d T
---@return T
local function listFirstOr(ls, d)
  if temper.listed_isempty(ls) then
    return d
  else
    return temper.list_get(ls, 0)
  end
end

---@param s string
---@param n integer
---@param fill? string|temper.Null
---@return string
local function stringPadded(s, n, fill)
  if temper.is_null(fill) then
    fill = "."
  end
  ---@cast fill string
  local out = s
  do
    local i = 0
    while i < n do
      out = out .. fill
      i = temper.int32_add(i, 1)
    end
  end
  return out
end

---@param g functions.Gate
---@param s string
---@return string
local function gatePassed(g, s)
  return s .. " " .. tostring(g:allows(1))
end

local counter = makeCounter(10)
counter()
print("count " .. counter())
local other = makeCounter(0)
print("other " .. other() .. " count " .. counter())
local addTwo = twice(addOne)
print("twice " .. addTwo(5) .. " " .. applyTo(addOne, 1))
print("triple " .. applyTo(Scaler.triple, 4) .. " " .. twice(Scaler.triple)(2))
local ns = { 5, 2, 8, 1, 4 }
local doubled = temper.listed_map(ns, function(n) return temper.int32_mul(n, 2) end)
local big = temper.listed_filter(doubled, function(n) return n > 4 end)
print(temper.listed_join(big, ", ", function(n) return tostring(n) end))
local order = temper.listed_sorted(ns, function(a, b) return temper.int32_sub(a, b) end)
print(temper.listed_join(order, " ", function(n) return tostring(n) end))
local total = 0
do
  local length = temper.list_length(ns)
  local i2 = 0
  while i2 < length do
    local n = temper.list_get(ns, i2)
    i2 = temper.int32_add(i2, 1)
    total = temper.int32_add(total, n)
  end
end
print("total " .. total)
local button = Button("hits", makeCounter(0))
button:press()
print(button:press())
local makers = temper.listbuilder_constructor()
do
  local i = 0
  while i < 3 do
    local k = temper.int32_mul(i, 10)
    temper.listbuilder_add(makers, function() return k end)
    i = temper.int32_add(i, 1)
  end
end
print(temper.listed_join(makers, " ", function(c) return tostring(c()) end))

local function fn8()
  local fs = temper.listbuilder_constructor()
  do
    local i = 0
    while i < 3 do
      local j = temper.int32_mul(i, 10)
      temper.listbuilder_add(fs, function() return j end)
      temper.yield()
      temper.listbuilder_add(fs, function() return temper.int32_add(j, 1) end)
      i = temper.int32_add(i, 1)
    end
  end
  print(temper.listed_join(fs, " ", function(c) return tostring(c()) end))
  temper.yield()
end

drive(5, temper.adapt_generator_fn(fn8))
print("sum " .. sumFrom(ns, 4) .. " doubles " .. doubles("bookkeeper"))
print("climb " .. climb(0) .. " " .. climb(1))
local gate = Gate()
print("gate " .. tostring(gate:allows(0)) .. " " .. gate:passed(4))
gate.check = function(x) return temper.int32_sub(x, 1) end
print("gate " .. tostring(gate:allows(1)) .. " " .. tostring(gate:allows(2)) .. " " .. gate:passed(4))
local none = gate:allows(0) and 3 or temper.null
print(tostring(stringIsPalindrome("step on no pets")) .. " " .. tostring(stringIsPalindrome("steps")))
print(intOrZero(none) .. " " .. listFirstOr({ 4, 5 }, 0) .. " " .. listFirstOr({ "x" }, "-"))
print(stringPadded("ab", 2) .. " " .. stringPadded("ab", 1, "*"))
print(gate:passed(4) .. " " .. gatePassed(gate, "gate"))

return {}
