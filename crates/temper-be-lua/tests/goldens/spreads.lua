local temper = require("temper-core")

---@type spreads.Box
local a
---@type temper.PromiseBuilder
local gate

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

---@class spreads.Sized
---@field area fun(self: spreads.Sized): integer

---@class spreads.Sized.type: temper.Type
local Sized = temper.type("spreads.Sized")

---@class spreads.Box: spreads.Sized
---@field w integer
---@field h integer
---@field label string|temper.Null
---@field area fun(self: spreads.Box): integer
---@field toString fun(self: spreads.Box): string

---@class spreads.Box.type: temper.Type
---@operator call: spreads.Box
local Box = temper.type("spreads.Box", Sized)

---@param this spreads.Box
---@param w integer
---@param h integer
---@param label? string|temper.Null
function Box.constructor(this, w, h, label)
  if label == nil then
    label = temper.null
  end
  this.w = w
  this.h = h
  this.label = label
end

---@param this spreads.Box
---@return integer
function Box.methods.area(this)
  return temper.int32_mul(this.w, this.h)
end

---@param this spreads.Box
---@return string
function Box.methods.toString(this)
  local subject = this.label
  return (temper.is_null(subject) and "box" or (subject --[[@as string]])) .. " " .. this.w .. "x" .. this.h
end

with_tostring(Box)

---@class spreads.Two
---@field first any
---@field second any

---@class spreads.Two.type: temper.Type
---@operator call: spreads.Two
local Two = temper.type("spreads.Two")

---@param this spreads.Two
---@param first any
---@param second any
function Two.constructor(this, first, second)
  this.first = first
  this.second = second
end

---@param b spreads.Box
---@return spreads.Box
local function grown(b)
  do
    local spread = b
    return Box(temper.int32_add(b.w, 1), spread.h, spread.label)
  end
end

---@param s spreads.Sized
---@return integer
local function areaOf(s)
  return s:area()
end

---@param s string
---@param n integer
---@return integer
local function said(s, n)
  print(s)
  return n
end

---@param x integer
---@param b spreads.Box
---@return string
local function both(x, b)
  return x .. " " .. b:toString()
end

---@param b spreads.Box
---@param n integer
---@return integer
local function widths(b, n)
  local t = b
  do
    local i = 0
    while i < n do
      local spread = t
      t = Box(t.h, temper.int32_add(t.w, 1), spread.label)
      i = temper.int32_add(i, 1)
    end
  end
  while true do
    local spread = t
    if Box(spread.w, 1, spread.label):area() >= 9 then
      break
    end
    local spread2 = t
    t = Box(temper.int32_add(t.w, 1), spread2.h, spread2.label)
  end
  do
    local i = 0
    while i < 2 do
      local spread = t
      local arg = said("h", t.w)
      local arg2 = said("w", t.h)
      local arg3 = spread.label
      t = Box(arg2, arg, arg3)
      i = temper.int32_add(i, 1)
    end
  end
  if t.w > 0 then
    local spread = b
    local b2 = Box(spread.w, spread.h, "inner")
    print(b2:toString())
  end
  return t.w
end

---@class spreads.Step
---@field n integer
---@field tag string
---@field next fun(self: spreads.Step): spreads.Step

---@class spreads.Step.type: temper.Type
---@operator call: spreads.Step
local Step = temper.type("spreads.Step")

---@param this spreads.Step
---@param n integer
---@param tag string
function Step.constructor(this, n, tag)
  this.n = n
  this.tag = tag
end

---@param this spreads.Step
---@return spreads.Step
function Step.methods.next(this)
  do
    local spread = this
    return Step(temper.int32_add(this.n, 1), spread.tag)
  end
end

---@generic T
---@param t spreads.Two
---@return spreads.Two
local function swapped(t)
  do
    local spread = t
    return Two(t.second, t.first)
  end
end

---@class spreads.Shape

---@class spreads.Shape.type: temper.Type
local Shape = temper.type("spreads.Shape")

---@class spreads.Circle: spreads.Shape
---@field r integer

---@class spreads.Circle.type: temper.Type
---@operator call: spreads.Circle
local Circle = temper.type("spreads.Circle", Shape)

---@param this spreads.Circle
---@param r integer
function Circle.constructor(this, r)
  this.r = r
end

---@class spreads.Square: spreads.Shape
---@field side integer

---@class spreads.Square.type: temper.Type
---@operator call: spreads.Square
local Square = temper.type("spreads.Square", Shape)

---@param this spreads.Square
---@param side integer
function Square.constructor(this, side)
  this.side = side
end

---@param x spreads.Shape
---@return spreads.Shape
local function doubled(x)
  ---@type spreads.Shape
  local shape
  if temper.instance_of(x, Circle) then
    local spread = x --[[@as spreads.Circle]]
    shape = Circle(temper.int32_mul((x --[[@as spreads.Circle]]).r, 2))
  else
    ---@type spreads.Square
    local square
    if temper.instance_of(x, Square) then
      local spread = x --[[@as spreads.Square]]
      square = Square(temper.int32_mul((x --[[@as spreads.Square]]).side, 2))
    else
      error("panic")
    end
    shape = square
  end
  local made = shape
  return made
end

---@param x spreads.Shape
---@return integer
local function size(x)
  local n = temper.instance_of(x, Circle) and (x --[[@as spreads.Circle]]).r or (temper.instance_of(x, Square) and (x --[[@as spreads.Square]]).side or error("panic"))
  return n
end

---@param c boolean
---@return spreads.Box
local function picked(c)
  if c then
    do
      local spread = a
      return Box(9, spread.h, spread.label)
    end
  else
    return a
  end
end

---@param f fun(arg0: integer): spreads.Box
---@return spreads.Box
local function applied(f)
  return f(4)
end

---@class spreads.Dial
---@field level integer
---@field note string|temper.Null
---@field twice integer

---@class spreads.Dial.type: temper.Type
---@operator call: spreads.Dial
local Dial = temper.type("spreads.Dial")

---@param this spreads.Dial
---@param level? integer|temper.Null
---@param note? string|temper.Null
---@param twice? integer|temper.Null
function Dial.constructor(this, level, note, twice)
  if temper.is_null(level) then
    level = 5
  end
  ---@cast level integer
  if temper.is_null(note) then
    note = "n"
  end
  ---@cast note string|temper.Null
  if temper.is_null(twice) then
    twice = temper.int32_mul(level, 2)
  end
  ---@cast twice integer
  this.level = level
  this.note = note
  this.twice = twice
end

---@class spreads.Mark
---@field note string|temper.Null
---@field size integer

---@class spreads.Mark.type: temper.Type
---@operator call: spreads.Mark
local Mark = temper.type("spreads.Mark")

---@param this spreads.Mark
---@param note? string|temper.Null
---@param size? integer|temper.Null
function Mark.constructor(this, note, size)
  if temper.is_null(note) then
    note = "n"
  end
  ---@cast note string|temper.Null
  if temper.is_null(size) then
    size = 2
  end
  ---@cast size integer
  this.note = note
  this.size = size
end

---@param n integer
---@return integer
local function positive(n)
  if n < 0 then
    return temper.bubble()
  end
  return n
end

---@param b spreads.Box
---@param n integer
---@return spreads.Box
local function sized(b, n)
  do
    local spread = b
    return Box(positive(n), spread.h, spread.label)
  end
end

a = Box(2, 3)
local b = grown(a)
print(a:toString() .. " " .. b:toString() .. " " .. areaOf(b))
local spread = b
print(Box(spread.w, spread.h, "named"):toString())
local boolean = a.w > 1
if boolean then
  local spread2 = a
  boolean = Box(10, spread2.h, spread2.label):area() > 20
end
local wide = boolean
local p = Two(1, 2)
local spread3 = p
local q = Two(spread3.first, 5)
print(tostring(wide) .. " " .. q.first .. " " .. q.second)
local spread5 = a
local spread4 = Box(7, spread5.h, spread5.label)
local nested = Box(spread4.w, 8, spread4.label)
local int32 = said("one", 1)
local spread6 = a
print(both(int32, Box(spread6.w, said("two", 2), spread6.label)))
local string_ = nested:toString() .. " "
local value = a.w > 5
if not value then
  local spread7 = a
  value = Box(said("three", 3), spread7.h, spread7.label).w == 3
end
print(string_ .. tostring(value))
print(tostring(widths(a, 3)))
local s = Step(1, "s"):next():next()
local spread8 = q
local same = Two(spread8.first, spread8.second)
local words = Two("a", "b")
local flipped = swapped(words)
print(s.n .. " " .. s.tag .. " " .. same.first .. same.second .. " " .. flipped.first .. flipped.second)
print(size(doubled(Circle(3))) .. " " .. size(doubled(Square(5))))

---@param n integer
---@return spreads.Box
local function fn(n)
  do
    local spread = a
    return Box(spread.w, n, spread.label)
  end
end

local made = applied(fn)
print(picked(true):toString() .. " " .. picked(false):toString() .. " " .. made:toString())
local dial = Dial(1, temper.null)
dial.level = 4
local spread9 = dial
local turned = Dial(spread9.level, "m", spread9.twice)
local mark = Mark(temper.null)
local spread10 = mark
local bigger = Mark(spread10.note, 3)
local string_2 = turned.level .. " "
local subject = turned.note
local subject2 = bigger.note
print(string_2 .. (temper.is_null(subject) and "-" or (subject --[[@as string]])) .. " " .. turned.twice .. " " .. (temper.is_null(subject2) and "-" or (subject2 --[[@as string]])) .. " " .. bigger.size)
local spread11 = a
---@type integer
local t_1
if not temper.pcall(function()
  t_1 = positive(-1)
end) then
  t_1 = 0
end
---@type integer
local t_2
if not temper.pcall(function()
  t_2 = positive(5)
end) then
  t_2 = -1
end
local caught = Box(t_1, t_2, spread11.label)
local string_3 = caught:toString() .. " "
---@type string
local t_3
if not temper.pcall(function()
  t_3 = sized(a, 4):toString()
end) then
  t_3 = "-"
end
print(string_3 .. t_3)
---@type string
local t_4
if not temper.pcall(function()
  sized(a, -1)
  t_4 = "sized"
end) then
  t_4 = "bubbled"
end
print(t_4)
gate = temper.promisebuilder_constructor()

local function fn2()
  local spread = a
  local arg = spread.w
  ---@type integer
  local arg2
  if not temper.pcall(function()
    arg2 = temper.promisebuilder_promise(gate):await()
  end) then
    arg2 = -1
  end
  local later = Box(arg, arg2, "later")
  print(later:toString())
end

temper.async(temper.adapt_generator_fn(fn2))
temper.promisebuilder_complete(gate, 6)

return {}
