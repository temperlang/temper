local temper = require("temper-core")

---@type integer
local ticks
---@type integer[]
local extras
local parseGrid

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

---@class libraries.Grid
---@field ncols integer
---@field values number[]
---@field get fun(self: libraries.Grid, i: integer, j: integer): number

---@class libraries.Grid.type: temper.Type
---@operator call: libraries.Grid
local Grid = temper.type("libraries.Grid")

---@param this libraries.Grid
---@param ncols integer
---@param values number[]
function Grid.constructor(this, ncols, values)
  this.ncols = ncols
  this.values = values
end

---@param this libraries.Grid
---@param i integer
---@param j integer
---@return number
function Grid.methods.get(this, i, j)
  return temper.list_get(this.values, temper.int32_add(temper.int32_mul(i, this.ncols), j))
end

---@param s string
---@return libraries.Grid|temper.Null
function Grid.parse(s)
  ---@type libraries.Grid|temper.Null
  local t_1
  if not temper.pcall(function()
    t_1 = parseGrid(s)
  end) then
    t_1 = temper.null
  end
  return t_1
end

---@param s string
---@return libraries.Grid
function parseGrid(s)
  if s == "" then
    return temper.bubble()
  end
  return Grid(1, { temper.string_tofloat64(s) })
end

---@class libraries.Casing
---@field name string
---@field eq fun(self: libraries.Casing, other: libraries.Casing): boolean
---@field toString fun(self: libraries.Casing): string
---@field marked string

---@class libraries.Casing.type: temper.Type
---@field first libraries.Casing
---@field last libraries.Casing
---@field mark string
---@operator call: libraries.Casing
local Casing = temper.type("libraries.Casing")

---@param this libraries.Casing
---@param other libraries.Casing
---@return boolean
function Casing.methods.eq(this, other)
  return this.name == other.name
end

---@param this libraries.Casing
---@param name string
function Casing.constructor(this, name)
  this.name = name
end

---@param this libraries.Casing
---@return string
function Casing.methods.toString(this)
  return this.name
end

---@param name string
---@return libraries.Casing
function Casing.named(name)
  return Casing(name)
end

---@param this libraries.Casing
---@return string
function Casing.get.marked(this)
  return Casing.mark .. this.name
end

with_tostring(Casing)

---@class libraries.Shape
---@field size integer

---@class libraries.Shape.type: temper.Type
local Shape = temper.type("libraries.Shape")

---@class libraries.Dot: libraries.Shape
---@field size integer
---@field bigger libraries.Dot|temper.Null
---@field grown integer|temper.Null
---@field scaled fun(self: libraries.Dot, k: integer): integer

---@class libraries.Dot.type: temper.Type
---@operator call: libraries.Dot
local Dot = temper.type("libraries.Dot", Shape)

---@param this libraries.Dot
---@param size integer
function Dot.constructor(this, size)
  this.size = size
end

---@param this libraries.Dot
---@return libraries.Dot|temper.Null
function Dot.get.bigger(this)
  if this.size < 3 then
    return Dot(temper.int32_add(this.size, 1))
  else
    return temper.null
  end
end

---@param this libraries.Dot
---@return integer|temper.Null
function Dot.get.grown(this)
  do
    local subject = this.bigger
    if temper.is_null(subject) then
      return temper.null
    else
      return (subject --[[@as libraries.Dot]]):scaled(10)
    end
  end
end

---@param this libraries.Dot
---@param k integer
---@return integer
function Dot.methods.scaled(this, k)
  return temper.int32_mul(this.size, k)
end

---@class libraries.Line: libraries.Shape
---@field size integer

---@class libraries.Line.type: temper.Type
---@operator call: libraries.Line
local Line = temper.type("libraries.Line", Shape)

---@param this libraries.Line
---@param size integer
function Line.constructor(this, size)
  this.size = size
end

---@class libraries.Carry
---@field carrying boolean
---@field state integer

---@class libraries.Carry.type: temper.Type
---@operator call: libraries.Carry
local Carry = temper.type("libraries.Carry")

---@param this libraries.Carry
---@param start integer
function Carry.constructor(this, start)
  this.carrying = false
  this.state = 0
  if start ~= 0 then
    this.carrying = true
    this.state = start
  end
end

---@class libraries.Tagged
---@field label string|temper.Null

---@class libraries.Tagged.type: temper.Type
---@operator call: libraries.Tagged
local Tagged = temper.type("libraries.Tagged")

---@param this libraries.Tagged
---@param label string|temper.Null
function Tagged.constructor(this, label)
  this.label = label
end

---@param label? string|temper.Null
---@return libraries.Tagged
local function tagged(label)
  if label == nil then
    label = temper.null
  end
  return Tagged(label)
end

---@param c integer
---@return integer
local function weight(c)
  local base = c == 42 and 0 or 6
  return temper.int32_add(temper.int32_add(c > 50 and 1 or 2, base), c)
end

---@param s libraries.Shape
---@return string
local function shapeName(s)
  local name = temper.instance_of(s, Dot) and "dot" or (temper.instance_of(s, Line) and "line" or error("panic"))
  return name
end

---@param n integer
---@return libraries.Shape
local function shapeOf(n)
  local shape = n > 1 and Line(n) or Dot(n)
  return shape
end

---@param s libraries.Shape
---@return integer[]
local function sizes(s)
  local found = temper.instance_of(s, Dot) and { (s --[[@as libraries.Dot]]).size, (s --[[@as libraries.Dot]]).size } or {}
  return found
end

---@param values number[]
---@param ncols integer
---@return integer
local function rows(values, ncols)
  ---@type integer
  local t_1
  if not temper.pcall(function()
    t_1 = temper.int32_div(temper.list_length(values), ncols)
  end) then
    error("panic")
  end
  return t_1
end

---@param grid libraries.Grid
---@return libraries.Grid
local function scaled(grid)
  if grid.ncols == 0 then
    return temper.bubble()
  end
  return grid
end

---@param grid libraries.Grid
---@return number[]
local function cubes(grid)
  ---@type number[]
  local t_1
  if not temper.pcall(function()
    local more = temper.listed_map(scaled(grid).values, function(x) return x * x * x end)
    t_1 = more
  end) then
    error("panic")
  end
  return t_1
end

---@param values number[]
---@param f fun(arg0: number)
local function each(values, f)
  do
    local i = 0
    while i < temper.list_length(values) do
      f(temper.list_get(values, i))
      i = temper.int32_add(i, 1)
    end
  end
end

---@param values number[]
---@return number
local function total(values)
  local sum = 0.0
  ---@param x number
  local function fn(x)
    if not temper.pcall(function()
      local part = x / 2.0
      sum = sum + part
    end) then
      error("panic")
    end
  end
  each(values, fn)
  return sum
end

---@param x number
---@return number
local function half(x)
  ---@type number
  local t_1
  if not temper.pcall(function()
    t_1 = x / 2.0
  end) then
    error("panic")
  end
  return t_1
end

---@param x number
---@return number
local function cube(x)
  return x * 0.25
end

---@param grid libraries.Grid
---@return number
local function corner(grid)
  ---@type number
  local t_1
  if not temper.pcall(function()
    t_1 = grid:get(1, 1)
  end) then
    t_1 = -1.0
  end
  return t_1
end

---@param n integer
---@return libraries.Grid|temper.Null
local function maybe(n)
  if n > 0 then
    return Grid(n, { 1.0 })
  else
    return temper.null
  end
end

---@param grid libraries.Grid
---@return number[]
local function mapped(grid)
  ---@type libraries.Grid
  local t_1
  if not temper.pcall(function()
    t_1 = scaled(grid)
  end) then
    error("panic")
  end
  ---@param x number
  ---@return number
  local function fn(x)
    if temper.float_cmp(x, 1.5) > 0 then
      return x * 2.0
    else
      return x
    end
  end
  return temper.listed_map(t_1.values, fn)
end

---@param digits integer[]
---@param short boolean
---@param c integer
---@return integer
local function byteAt(digits, short, c)
  local byte = short and temper.int32_mul(temper.list_get(digits, c), 17) or temper.int32_add(temper.int32_mul(temper.list_get(digits, temper.int32_mul(2, c)), 16), temper.list_get(digits, temper.int32_add(temper.int32_mul(2, c), 1)))
  return byte
end

---@param digits integer[]
---@return boolean
local function ascending(digits)
  if temper.list_length(digits) > 1 then
    return temper.int32_add(temper.list_get(digits, 0), 1) == temper.list_get(digits, 1)
  else
    return false
  end
end

---@param digits integer[]
---@return integer
local function firstDrop(digits)
  local i = 1
  while i < temper.list_length(digits) and temper.list_get(digits, temper.int32_sub(i, 1)) <= temper.list_get(digits, i) do
    i = temper.int32_add(i, 1)
  end
  return i
end

---@return integer
local function tick()
  ticks = temper.int32_add(ticks, 1)
  return ticks
end

---@param short boolean
---@return integer
local function tickIf(short)
  local n = short and tick() or temper.int32_add(temper.int32_mul(tick(), 10), tick())
  return n
end

---@return boolean
local function ticked()
  if ticks > 0 then
    return temper.int32_add(temper.int32_mul(tick(), 10), tick()) > 0
  else
    return false
  end
end

---@return integer
local function tickWhile()
  local i = 0
  while i < 3 and tick() < tick() do
    i = temper.int32_add(i, 1)
  end
  return i
end

---@generic T
---@param items T[]
---@return integer
local function sizeWith(items)
  return temper.int32_add(temper.list_length(items), temper.list_length(extras))
end

---@param n integer
---@return string|temper.Null
local function text(n)
  if n > 0 then
    return "x"
  else
    return temper.null
  end
end

---@param n integer
---@return integer|temper.Null
local function count(n)
  if n > 0 then
    return n
  else
    return temper.null
  end
end

---@param n integer
---@return libraries.Casing|temper.Null
local function casing(n)
  if n > 0 then
    return Casing.first
  else
    return temper.null
  end
end

---@param s string
---@return string
local function captured(s)
  ---@type libraries.Grid|temper.Null
  local found
  if not temper.pcall(function()
    found = parseGrid(s)
  end) then
    found = temper.null
  end
  return temper.listed_join(temper.listed_map({ 1, 2 }, function(x) return temper.is_null(found) end), ",", function(b) return tostring(b) end)
end

---@return string
local function bidi()
  return "look\226\128\174out"
end

---@param n integer
---@return string
local function word(n)
  if n > 1 then
    return "b"
  else
    return "a"
  end
end

---@class libraries.Word
---@field text string

---@class libraries.Word.type: temper.Type
---@operator call: libraries.Word
local Word = temper.type("libraries.Word")

---@param this libraries.Word
---@param text string
function Word.constructor(this, text)
  this.text = text
end

---@class libraries.Joiner
---@field out string
---@field addWord fun(self: libraries.Joiner, w: libraries.Word)
---@field addFlags fun(self: libraries.Joiner, x: boolean[])
---@field addTexts fun(self: libraries.Joiner, x: string[])
---@field addMaybes fun(self: libraries.Joiner, x: (string|temper.Null)[])

---@class libraries.Joiner.type: temper.Type
---@operator call: libraries.Joiner
local Joiner = temper.type("libraries.Joiner")

---@param this libraries.Joiner
function Joiner.constructor(this)
  this.out = ""
end

---@param this libraries.Joiner
---@param w libraries.Word
function Joiner.methods.addWord(this, w)
  this.out = this.out .. "w" .. w.text
end

---@param this libraries.Joiner
---@param x boolean[]
function Joiner.methods.addFlags(this, x)
  this.out = this.out .. "f" .. temper.list_length(x)
end

---@param this libraries.Joiner
---@param x string[]
function Joiner.methods.addTexts(this, x)
  this.out = this.out .. "t" .. temper.list_length(x)
end

---@param this libraries.Joiner
---@param x (string|temper.Null)[]
function Joiner.methods.addMaybes(this, x)
  this.out = this.out .. "m" .. temper.list_length(x)
end

---@class libraries.Shown
---@field toString fun(self: libraries.Shown): string

---@class libraries.Shown.type: temper.Type
local Shown = temper.type("libraries.Shown")

---@class libraries.Tag: libraries.Shown
---@field name string
---@field toString fun(self: libraries.Tag): string

---@class libraries.Tag.type: temper.Type
---@operator call: libraries.Tag
local Tag = temper.type("libraries.Tag", Shown)

---@param this libraries.Tag
---@param name string
function Tag.constructor(this, name)
  this.name = name
end

---@param this libraries.Tag
---@return string
function Tag.methods.toString(this)
  return "<" .. this.name .. ">"
end

with_tostring(Tag)

---@class libraries.Held
---@field item any
---@field next libraries.Held|temper.Null
---@field toString fun(self: libraries.Held): string

---@class libraries.Held.type: temper.Type
---@operator call: libraries.Held
local Held = temper.type("libraries.Held")

---@param this libraries.Held
---@param item any
---@param next_ libraries.Held|temper.Null
function Held.constructor(this, item, next_)
  this.item = item
  this.next = next_
end

---@param this libraries.Held
---@return string
function Held.methods.toString(this)
  return "Held(" .. this.item:toString() .. ")"
end

with_tostring(Held)

---@class libraries.Effect

---@class libraries.Effect.type: temper.Type
local Effect = temper.type("libraries.Effect")

---@class libraries.Plain: libraries.Effect
---@field text string

---@class libraries.Plain.type: temper.Type
---@operator call: libraries.Plain
local Plain = temper.type("libraries.Plain", Effect)

---@param this libraries.Plain
---@param text string
function Plain.constructor(this, text)
  this.text = text
end

---@class libraries.Happened: libraries.Effect
---@field event any

---@class libraries.Happened.type: temper.Type
---@operator call: libraries.Happened
local Happened = temper.type("libraries.Happened", Effect)

---@param this libraries.Happened
---@param event any
function Happened.constructor(this, event)
  this.event = event
end

---@class libraries.Bundle
---@field effects libraries.Effect[]
---@field first any

---@class libraries.Bundle.type: temper.Type
---@operator call: libraries.Bundle
local Bundle = temper.type("libraries.Bundle")

---@param this libraries.Bundle
---@param effects libraries.Effect[]
---@param first any
function Bundle.constructor(this, effects, first)
  this.effects = effects
  this.first = first
end

---@generic T
---@param into libraries.Effect[]
---@param more libraries.Effect[]
---@return integer
local function joinEffects(into, more)
  do
    local length = temper.list_length(more)
    local i = 0
    while i < length do
      local e = temper.list_get(more, i)
      i = temper.int32_add(i, 1)
      temper.listbuilder_add(into, e)
    end
  end
  return temper.listbuilder_length(into)
end

---@generic T
---@param text string
---@param event T
---@param other T
---@return string
local function effectsOf(text, event, other)
  local into = temper.listbuilder_constructor()
  joinEffects(into, {})
  joinEffects(into, { Plain(text) })
  joinEffects(into, { Happened(event), Plain("!"), Happened(other) })
  local shown = ""
  do
    local list = temper.listbuilder_tolist(into)
    local length = temper.list_length(list)
    local i = 0
    while i < length do
      local e = temper.list_get(list, i)
      i = temper.int32_add(i, 1)
      if temper.instance_of(e, Plain) then
        shown = shown .. (e --[[@as libraries.Plain]]).text
      else
        shown = shown .. "*"
      end
    end
  end
  return shown
end

---@generic T: libraries.Shown
---@param a libraries.Held|temper.Null
---@param b libraries.Held|temper.Null
---@return boolean
local function sameHeld(a, b)
  local left = a
  local right = b
  return temper.is_null(left) and temper.is_null(right) or not temper.is_null(left) and (not temper.is_null(right) and (left --[[@as libraries.Held]]) == (right --[[@as libraries.Held]]))
end

---@class libraries.Stepper
---@field step fun(self: libraries.Stepper): any[]

---@class libraries.Stepper.type: temper.Type
local Stepper = temper.type("libraries.Stepper")

---@class libraries.Counting: libraries.Stepper
---@field count integer
---@field made fun(self: libraries.Counting): any
---@field step fun(self: libraries.Counting): any[]

---@class libraries.Counting.type: temper.Type
local Counting = temper.type("libraries.Counting", Stepper)

---@param this libraries.Counting
---@return any[]
function Counting.methods.step(this)
  this.count = temper.int32_add(this.count, 1)
  return { this:made() }
end

---@class libraries.Ticks: libraries.Counting
---@field n integer
---@field count integer
---@field made fun(self: libraries.Ticks): string

---@class libraries.Ticks.type: temper.Type
---@operator call: libraries.Ticks
local Ticks = temper.type("libraries.Ticks", Counting)

---@param this libraries.Ticks
function Ticks.constructor(this)
  this.n = 0
end

---@param this libraries.Ticks
---@return integer
function Ticks.get.count(this)
  return this.n
end

---@param this libraries.Ticks
---@param c integer
function Ticks.set.count(this, c)
  this.n = c
end

---@param this libraries.Ticks
---@return string
function Ticks.methods.made(this)
  return "tick" .. this.n
end

---@class libraries.Sink
---@field take fun(self: libraries.Sink, e: any): integer

---@class libraries.Sink.type: temper.Type
local Sink = temper.type("libraries.Sink")

---@class libraries.Names: libraries.Sink
---@field take fun(self: libraries.Names, e: libraries.Tag): integer

---@class libraries.Names.type: temper.Type
---@operator call: libraries.Names
local Names = temper.type("libraries.Names", Sink)

---@param this libraries.Names
function Names.constructor(this)
end

---@param this libraries.Names
---@param e libraries.Tag
---@return integer
function Names.methods.take(this, e)
  if e.name == "abc" then
    return 3
  else
    return 0
  end
end

---@class libraries.Escapes
---@field escape fun(self: libraries.Escapes, s: string|temper.Null): string
---@field escapeFlag fun(self: libraries.Escapes, b: boolean): string

---@class libraries.Escapes.type: temper.Type
local Escapes = temper.type("libraries.Escapes")

---@param this libraries.Escapes
---@param b boolean
---@return string
function Escapes.methods.escapeFlag(this, b)
  return this:escape(b and "T" or "F")
end

---@class libraries.Quoting: libraries.Escapes
---@field escape fun(self: libraries.Quoting, s: string|temper.Null): string

---@class libraries.Quoting.type: temper.Type
---@operator call: libraries.Quoting
local Quoting = temper.type("libraries.Quoting", Escapes)

---@param this libraries.Quoting
function Quoting.constructor(this)
end

---@param this libraries.Quoting
---@param s string|temper.Null
---@return string
function Quoting.methods.escape(this, s)
  return "'" .. (temper.is_null(s) and "null" or (s --[[@as string]])) .. "'"
end

---@param n integer
---@return string|temper.Null
local function problem(n)
  local p = n == 0 and temper.null or error("panic")
  return p
end

---@param n integer
---@return string|temper.Null
local function message(n)
  local p = n == 0 and temper.null or (n == 1 and "one" or (n == 2 and temper.null or error("panic")))
  return p
end

---@param xs string[]
---@return integer
local function sumParsed(xs)
  local total = 0
  do
    local i = 0
    while i < temper.list_length(xs) do
      repeat
        local x = temper.list_get(xs, i)
        ---@type integer
        local n
        ---@type integer|temper.Null
        local held = temper.null
        if not temper.pcall(function()
          held = temper.string_toint32(x)
        end) then
        end
        if temper.is_null(held) then
          i = temper.int32_add(i, 1)
          break
        else
          n = held --[[@as integer]]
        end
        total = temper.int32_add(total, n)
        i = temper.int32_add(i, 1)
      until true
    end
  end
  do
    local length = temper.list_length(xs)
    local i = 0
    while i < length do
      repeat
        local x = temper.list_get(xs, i)
        i = temper.int32_add(i, 1)
        ---@type integer
        local m
        ---@type integer|temper.Null
        local held = temper.null
        if not temper.pcall(function()
          held = temper.string_toint32(x)
        end) then
        end
        if temper.is_null(held) then
          break
        else
          m = held --[[@as integer]]
        end
        total = temper.int32_add(total, temper.int32_mul(m, 10))
      until true
    end
  end
  return total
end

---@param xs integer[]
---@return integer
local function backwards(xs)
  local t = 0
  do
    local i = temper.list_length(xs)
    while true do
      i = temper.int32_sub(i, 1)
      if i < 0 then
        break
      end
      t = temper.int32_add(temper.int32_mul(t, 10), temper.list_get(xs, i))
    end
  end
  return t
end

Casing.first = Casing("first")
Casing.last = Casing.named("last")
Casing.mark = "#"
ticks = 0
extras = { 1 }
local grid = Grid(2, { 1.0, 2.0, 3.0, 4.0 })
temper.log(rows(grid.values, 2) .. " " .. temper.float64_tostring(half(3.0)) .. " " .. temper.float64_tostring(cube(8.0)))
temper.log(temper.float64_tostring(corner(grid)))
temper.log(temper.float64_tostring(total(grid.values)))
local none = text(0)
local some = count(3)
local part = none
local part2 = some
local subject = maybe(0)
local part3 = temper.is_null(subject) and temper.null or (subject --[[@as libraries.Grid]]).ncols
temper.log((temper.is_null(part) and "null" or (part --[[@as string]])) .. " " .. (temper.is_null(part2) and "null" or tostring(part2 --[[@as integer]])) .. " " .. (temper.is_null(part3) and "null" or tostring(part3 --[[@as integer]])))
temper.log(tostring(not temper.is_null(maybe(1))) .. " " .. tostring(not temper.is_null(maybe(0))) .. " " .. tostring(temper.is_null(Grid.parse(""))))
temper.log(Casing.first:toString())
temper.log(temper.listed_join(mapped(grid), ",", function(x) return temper.float64_tostring(x) end))
temper.log(temper.listed_join(cubes(grid), ",", function(x) return temper.float64_tostring(x) end))
local left = casing(1)
local left2 = casing(0)
local left3 = casing(0)
local right = casing(0)
temper.log(tostring(not temper.is_null(left) and (left --[[@as libraries.Casing]]):eq(Casing.first)) .. " " .. tostring(not temper.is_null(left2) and (left2 --[[@as libraries.Casing]]):eq(Casing.first)) .. " " .. tostring(not (temper.is_null(left3) and temper.is_null(right) or not temper.is_null(left3) and (not temper.is_null(right) and (left3 --[[@as libraries.Casing]]):eq(right --[[@as libraries.Casing]])))))
temper.log(captured("") .. " " .. captured("2.5"))
temper.log(tostring(bidi() ~= "lookout"))
temper.log(temper.str_cmp(word(1), word(2)) .. " " .. temper.str_cmp(word(2), word(1)) .. " " .. temper.str_cmp(word(2), word(3)))
local named = word(2)
temper.log(tostring(named == word(3) and named .. "!" ~= named))
local digits = { 1, 2, 4, 3 }
temper.log(byteAt(digits, true, 1) .. " " .. byteAt(digits, false, 0) .. " " .. tostring(ascending(digits)) .. " " .. firstDrop(digits) .. " " .. sizeWith(digits))
temper.log(tickIf(true) .. " " .. tickIf(false) .. " " .. tostring(ticked()) .. " " .. tickWhile())
local string_ = shapeName(shapeOf(1)) .. " " .. shapeName(shapeOf(2)) .. " " .. temper.list_length(sizes(shapeOf(1))) .. " " .. temper.list_length(sizes(shapeOf(2))) .. " "
local part4 = Dot(1).grown
local part5 = Dot(3).grown
temper.log(string_ .. (temper.is_null(part4) and "null" or tostring(part4 --[[@as integer]])) .. " " .. (temper.is_null(part5) and "null" or tostring(part5 --[[@as integer]])) .. " " .. Casing.last:toString() .. " " .. Casing.first.marked)
local carry = Carry(3)
local value = tostring(carry.carrying) .. " " .. carry.state .. " " .. tostring(Carry(0).carrying) .. " "
local part6 = tagged().label
local part7 = tagged("x").label
temper.log(value .. (temper.is_null(part6) and "null" or (part6 --[[@as string]])) .. " " .. (temper.is_null(part7) and "null" or (part7 --[[@as string]])) .. " " .. weight(42) .. " " .. weight(60))
local b = temper.listbuilder_constructor()
temper.listbuilder_add(b, true)
temper.listbuilder_add(b, false)
local flags = temper.listbuilder_tolist(b)
local joiner = Joiner()
joiner:addWord(Word("a"))
joiner:addFlags({ true })
joiner:addTexts({ "x", "y" })
local maybes = { temper.null, "z" }
joiner:addMaybes(maybes)
temper.log(joiner.out)
local held = Held(Tag("a"), temper.null)
local other = Held(Tag("b"), held)
local nobody = temper.null
local string_2 = held:toString() .. " "
local subject3 = other.next
local subject2 = temper.is_null(subject3) and temper.null or (subject3 --[[@as libraries.Held]]).item
temper.log(string_2 .. (temper.is_null(subject2) and Tag("none") or (subject2 --[[@as libraries.Tag]])):toString() .. " " .. tostring(sameHeld(held, held)) .. " " .. tostring(sameHeld(held, other)) .. " " .. tostring(sameHeld(nobody, nobody)) .. " " .. tostring(sameHeld(nobody, held)))
temper.log(effectsOf("ab", 1, 2) .. " " .. Bundle({}, "2").first .. " " .. temper.list_length(flags))
local counter = Ticks()
local stepper = counter
stepper:step()
temper.log(temper.list_get(stepper:step(), 0) .. " " .. counter.count .. " " .. Names():take(Tag("abc")) .. " " .. Quoting():escapeFlag(true))
local subject4 = problem(0)
local subject5 = message(1)
local subject6 = message(2)
temper.log((temper.is_null(subject4) and "none" or (subject4 --[[@as string]])) .. " " .. (temper.is_null(subject5) and "none" or (subject5 --[[@as string]])) .. " " .. (temper.is_null(subject6) and "none" or (subject6 --[[@as string]])) .. " " .. sumParsed({ "1", "x", "2" }) .. " " .. backwards({ 1, 2, 3 }))

return {
  Grid = Grid,
  parseGrid = parseGrid,
  Casing = Casing,
  Shape = Shape,
  Dot = Dot,
  Line = Line,
  Carry = Carry,
  Tagged = Tagged,
  sizeWith = sizeWith,
  Word = Word,
  Joiner = Joiner,
  Shown = Shown,
  Tag = Tag,
  Held = Held,
  Effect = Effect,
  Plain = Plain,
  Happened = Happened,
  Bundle = Bundle,
  effectsOf = effectsOf,
  sameHeld = sameHeld,
  Stepper = Stepper,
  Counting = Counting,
  Ticks = Ticks,
  Sink = Sink,
  Names = Names,
  Escapes = Escapes,
  Quoting = Quoting,
}
