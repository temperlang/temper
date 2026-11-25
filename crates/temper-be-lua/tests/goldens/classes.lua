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

---@class classes.Shape
---@field area integer
---@field describe fun(self: classes.Shape): string

---@class classes.Shape.type: temper.Type
local Shape = temper.type("classes.Shape")

---@param this classes.Shape
---@return string
function Shape.methods.describe(this)
  return "shape of area " .. this.area
end

---@class classes.Rect: classes.Shape
---@field w integer
---@field h integer
---@field area integer
---@field toString fun(self: classes.Rect): string
---@field scaled fun(self: classes.Rect, k: integer): classes.Rect

---@class classes.Rect.type: temper.Type
---@field unit classes.Rect
---@operator call: classes.Rect
local Rect = temper.type("classes.Rect", Shape)

---@param this classes.Rect
---@param w integer
---@param h integer
function Rect.constructor(this, w, h)
  this.w = w
  this.h = h
end

---@param this classes.Rect
---@return integer
function Rect.get.area(this)
  return temper.int32_mul(this.w, this.h)
end

---@param this classes.Rect
---@return string
function Rect.methods.toString(this)
  return "Rect(" .. this.w .. ", " .. this.h .. ")"
end

---@param this classes.Rect
---@param k integer
---@return classes.Rect
function Rect.methods.scaled(this, k)
  return Rect(temper.int32_mul(this.w, k), temper.int32_mul(this.h, k))
end

with_tostring(Rect)

---@class classes.Square: classes.Shape
---@field side integer
---@field area integer
---@field describe fun(self: classes.Square): string

---@class classes.Square.type: temper.Type
---@operator call: classes.Square
local Square = temper.type("classes.Square", Shape)

---@param this classes.Square
---@param side integer
function Square.constructor(this, side)
  this.side = side
end

---@param this classes.Square
---@return integer
function Square.get.area(this)
  return temper.int32_mul(this.side, this.side)
end

---@param this classes.Square
---@return string
function Square.methods.describe(this)
  return "square of side " .. this.side
end

---@class classes.Counter
---@field count integer
---@field hidden integer
---@field doubled integer
---@field added integer
---@field bump fun(self: classes.Counter): classes.Counter
---@field same fun(self: classes.Counter, other: classes.Counter): boolean

---@class classes.Counter.type: temper.Type
---@operator call: classes.Counter
local Counter = temper.type("classes.Counter")

---@param this classes.Counter
function Counter.constructor(this)
  this.count = 0
  this.hidden = 0
end

---@param this classes.Counter
---@return integer
function Counter.get.doubled(this)
  return temper.int32_mul(this.count, 2)
end

---@param this classes.Counter
---@param value integer
function Counter.set.doubled(this, value)
  this.count = temper.int32_sub(value, this.count)
end

---@param this classes.Counter
---@param value integer
function Counter.set.added(this, value)
  this.count = temper.int32_add(this.count, value)
end

---@param this classes.Counter
---@return classes.Counter
function Counter.methods.bump(this)
  this.count = temper.int32_add(this.count, 1)
  this.hidden = temper.int32_add(this.hidden, 1)
  return this
end

---@param this classes.Counter
---@param other classes.Counter
---@return boolean
function Counter.methods.same(this, other)
  return this.hidden == other.hidden
end

---@return classes.Counter
function Counter.zero()
  return Counter()
end

---@class classes.Point
---@field x integer
---@field y integer
---@field eq fun(self: classes.Point, other: classes.Point): boolean

---@class classes.Point.type: temper.Type
---@operator call: classes.Point
local Point = temper.type("classes.Point")

---@param this classes.Point
---@param x integer
---@param y integer
function Point.constructor(this, x, y)
  this.x = x
  this.y = y
end

---@param this classes.Point
---@param other classes.Point
---@return boolean
function Point.methods.eq(this, other)
  if this.x == other.x then
    return this.y == other.y
  else
    return false
  end
end

---@class classes.Loose
---@field n integer
---@field eq fun(self: classes.Loose, other: classes.Loose): boolean
---@field mirrors fun(self: classes.Loose, other: classes.Loose): boolean

---@class classes.Loose.type: temper.Type
---@operator call: classes.Loose
local Loose = temper.type("classes.Loose")

---@param this classes.Loose
---@param n integer
function Loose.constructor(this, n)
  this.n = n
end

---@param this classes.Loose
---@param other classes.Loose
---@return boolean
function Loose.methods.eq(this, other)
  if this.n == other.n then
    return true
  else
    return this.n == temper.int32_unm(other.n)
  end
end

---@param this classes.Loose
---@param other classes.Loose
---@return boolean
function Loose.methods.mirrors(this, other)
  if other:eq(this) then
    return this:eq(other)
  else
    return false
  end
end

---@param n integer
---@return classes.Loose|temper.Null
local function maybeLoose(n)
  if n == 0 then
    return temper.null
  else
    return Loose(n)
  end
end

---@class classes.Boxed
---@field value any
---@field tag integer
---@field eq fun(self: classes.Boxed, other: classes.Boxed): boolean
---@field sameAs fun(self: classes.Boxed, other: classes.Boxed): boolean

---@class classes.Boxed.type: temper.Type
---@operator call: classes.Boxed
local Boxed = temper.type("classes.Boxed")

---@param this classes.Boxed
---@param value any
---@param tag integer
function Boxed.constructor(this, value, tag)
  this.value = value
  this.tag = tag
end

---@param this classes.Boxed
---@param other classes.Boxed
---@return boolean
function Boxed.methods.eq(this, other)
  return this.tag == other.tag
end

---@param this classes.Boxed
---@param other classes.Boxed
---@return boolean
function Boxed.methods.sameAs(this, other)
  if this:eq(other) then
    return other:eq(this)
  else
    return false
  end
end

---@class classes.Holder
---@field loose classes.Loose
---@field tag string
---@field eq fun(self: classes.Holder, other: classes.Holder): boolean

---@class classes.Holder.type: temper.Type
---@operator call: classes.Holder
local Holder = temper.type("classes.Holder")

---@param this classes.Holder
---@param loose classes.Loose
---@param tag string
function Holder.constructor(this, loose, tag)
  this.loose = loose
  this.tag = tag
end

---@param this classes.Holder
---@param other classes.Holder
---@return boolean
function Holder.methods.eq(this, other)
  if this.loose:eq(other.loose) then
    return this.tag == other.tag
  else
    return false
  end
end

---@class classes.Version
---@field major integer
---@field minor integer
---@field compareTo fun(self: classes.Version, other: classes.Version): integer

---@class classes.Version.type: temper.Type
---@operator call: classes.Version
local Version = temper.type("classes.Version")

---@param this classes.Version
---@param major integer
---@param minor integer
function Version.constructor(this, major, minor)
  this.major = major
  this.minor = minor
end

---@param this classes.Version
---@param other classes.Version
---@return integer
function Version.methods.compareTo(this, other)
  if this.major ~= other.major then
    return temper.int_cmp(this.major, other.major)
  else
    return temper.int_cmp(this.minor, other.minor)
  end
end

---@class classes.Stock
---@field count integer
---@field itemsVar integer[]
---@field items integer[]
---@field size integer
---@field sizeNow fun(self: classes.Stock): integer

---@class classes.Stock.type: temper.Type
---@operator call: classes.Stock
local Stock = temper.type("classes.Stock")

---@param this classes.Stock
---@return integer[]
function Stock.get.items(this)
  return this.itemsVar
end

---@param this classes.Stock
---@param value integer[]
function Stock.set.items(this, value)
  this.itemsVar = value
end

---@param this classes.Stock
---@param items integer[]
---@param count? integer|temper.Null
function Stock.constructor(this, items, count)
  if temper.is_null(count) then
    count = temper.list_length(items)
  end
  ---@cast count integer
  this.count = count
  this.itemsVar = items
  this.size = temper.list_length(items)
end

---@param this classes.Stock
---@return integer
function Stock.methods.sizeNow(this)
  return temper.list_length(this.items)
end

---@param x classes.Shape
---@return classes.Shape
local function shape(x)
  return x
end

---@class classes.Theme
---@field fg string
---@field bg string
---@field size integer
---@field bold boolean
---@field weight string
---@field toString fun(self: classes.Theme): string

---@class classes.Theme.type: temper.Type
---@operator call: classes.Theme
local Theme = temper.type("classes.Theme")

---@param this classes.Theme
---@param fg string
---@param bg string
---@param size? integer|temper.Null
---@param bold? boolean|temper.Null
function Theme.constructor(this, fg, bg, size, bold)
  if temper.is_null(size) then
    size = 12
  end
  ---@cast size integer
  if temper.is_null(bold) then
    bold = false
  end
  ---@cast bold boolean
  this.fg = fg
  this.bg = bg
  this.size = size
  this.bold = bold
end

---@param this classes.Theme
---@return string
function Theme.get.weight(this)
  if this.bold then
    return "bold"
  else
    return "plain"
  end
end

---@param this classes.Theme
---@return string
function Theme.methods.toString(this)
  return this.fg .. "/" .. this.bg .. " " .. this.size .. " " .. this.weight
end

with_tostring(Theme)

---@param t classes.Theme
---@param why string
---@return classes.Theme
local function picked(t, why)
  print(why)
  return t
end

---@param s string
---@return string
local function noted(s)
  print(s)
  return s
end

---@param eq fun(arg0: classes.Loose|temper.Null, arg1: classes.Loose|temper.Null): boolean
---@return string
local function compared(eq)
  local ns = { 0, 1, -1, 2 }
  local out = temper.listbuilder_constructor()
  do
    local length = temper.list_length(ns)
    local i = 0
    while i < length do
      local a = temper.list_get(ns, i)
      i = temper.int32_add(i, 1)
      do
        local length2 = temper.list_length(ns)
        local i2 = 0
        while i2 < length2 do
          local b = temper.list_get(ns, i2)
          i2 = temper.int32_add(i2, 1)
          temper.listbuilder_add(out, eq(maybeLoose(a), maybeLoose(b)) and "T" or "f")
        end
      end
    end
  end
  return temper.listed_join(out, "", function(s) return s end)
end

---@param n integer
---@return string
local function typeNamed(n)
  if n == 0 then
    return "nil"
  elseif n == 1 then
    return "boolean"
  elseif n == 2 then
    return "number"
  elseif n == 3 then
    return "number"
  elseif n == 4 then
    return "number"
  else
    return "string"
  end
end

---@param t string
---@return string
local function same(t)
  return t
end

---@class classes.Sql
---@field parts string[]
---@field appendSafe fun(self: classes.Sql, s: string)
---@field appendString fun(self: classes.Sql, x: string|temper.Null)
---@field appendInt fun(self: classes.Sql, x: integer)
---@field appendShape fun(self: classes.Sql, x: classes.Shape)
---@field appendRect fun(self: classes.Sql, x: classes.Rect)
---@field accumulated string

---@class classes.Sql.type: temper.Type
---@operator call: classes.Sql
local Sql = temper.type("classes.Sql")

---@param this classes.Sql
function Sql.constructor(this)
  this.parts = temper.listbuilder_constructor()
end

---@param this classes.Sql
---@param s string
function Sql.methods.appendSafe(this, s)
  temper.listbuilder_add(this.parts, s)
end

---@param this classes.Sql
---@param x string|temper.Null
function Sql.methods.appendString(this, x)
  temper.listbuilder_add(this.parts, temper.is_null(x) and "NULL" or "'" .. (x --[[@as string]]) .. "'")
end

---@param this classes.Sql
---@param x integer
function Sql.methods.appendInt(this, x)
  temper.listbuilder_add(this.parts, tostring(x))
end

---@param this classes.Sql
---@param x classes.Shape
function Sql.methods.appendShape(this, x)
  temper.listbuilder_add(this.parts, "shape " .. x.area)
end

---@param this classes.Sql
---@param x classes.Rect
function Sql.methods.appendRect(this, x)
  temper.listbuilder_add(this.parts, "rect " .. x:toString())
end

---@param this classes.Sql
---@return string
function Sql.get.accumulated(this)
  return temper.listed_join(this.parts, "", function(p) return p end)
end

---@class classes.Limited
---@field next fun(self: classes.Limited, n: integer): integer
---@field tooFar fun(self: classes.Limited): integer

---@class classes.Limited.type: temper.Type
local Limited = temper.type("classes.Limited")

---@class classes.Stepper: classes.Limited
---@field limit integer
---@field next fun(self: classes.Stepper, n: integer): integer
---@field tooFar fun(self: classes.Stepper): integer

---@class classes.Stepper.type: temper.Type
---@operator call: classes.Stepper
local Stepper = temper.type("classes.Stepper", Limited)

---@param this classes.Stepper
---@param limit integer
function Stepper.constructor(this, limit)
  this.limit = limit
end

---@param this classes.Stepper
---@param n integer
---@return integer
function Stepper.methods.next(this, n)
  if n < this.limit then
    return temper.int32_add(n, 1)
  else
    return this:tooFar()
  end
end

---@param this classes.Stepper
---@return integer
function Stepper.methods.tooFar(this)
  error("panic")
end

---@class classes.Failing
---@field fail fun(self: classes.Failing): any

---@class classes.Failing.type: temper.Type
local Failing = temper.type("classes.Failing")

---@class classes.IntFailing: classes.Failing
---@field fail fun(self: classes.IntFailing): integer

---@class classes.IntFailing.type: temper.Type
---@operator call: classes.IntFailing
local IntFailing = temper.type("classes.IntFailing", Failing)

---@param this classes.IntFailing
function IntFailing.constructor(this)
end

---@param this classes.IntFailing
---@return integer
function IntFailing.methods.fail(this)
  error("panic")
end

---@class classes.AnyFailing: classes.Failing
---@field fail fun(self: classes.AnyFailing): any

---@class classes.AnyFailing.type: temper.Type
---@operator call: classes.AnyFailing
local AnyFailing = temper.type("classes.AnyFailing", Failing)

---@param this classes.AnyFailing
function AnyFailing.constructor(this)
end

---@param this classes.AnyFailing
---@return any
function AnyFailing.methods.fail(this)
  error("panic")
end

---@generic T
---@param ok boolean
---@param x T
---@param f classes.Failing
---@return T
local function orFail(ok, x, f)
  if ok then
    return x
  else
    return f:fail()
  end
end

Rect.unit = Rect(1, 1)
local r = Rect(2, 3)
print(r:toString())
print(tostring(r.area))
local s = shape(r)
print(s:describe())
local q = shape(Square(4))
print(q:describe())
print(tostring(q.area))
print(tostring(r:scaled(2):scaled(3).area))
print(Rect.unit:scaled(5):toString())
local c = Counter.zero()
c:bump():bump()
print(tostring(c.count))
c.doubled = 10
print(tostring(c.doubled))
print(tostring(c:same(Counter():bump():bump())))
c.added = 5
local t = 2
c.added = t
local t2 = t
c.added = t2
local added = t2
print(added .. " " .. c.count)
print(tostring(Point(1, 2):eq(Point(1, 2))) .. " " .. tostring(not Point(1, 2):eq(Point(2, 1))))
print(tostring(Holder(Loose(3), "a"):eq(Holder(Loose(-3), "a"))))
local loose = Loose(2)
local string_ = tostring(loose:mirrors(Loose(-2))) .. " "
local left = maybeLoose(-2)
local left2 = loose
local right = maybeLoose(0)
local left3 = maybeLoose(0)
print(string_ .. tostring(not temper.is_null(left) and (left --[[@as classes.Loose]]):eq(loose)) .. " " .. tostring(not temper.is_null(right) and left2:eq(right --[[@as classes.Loose]])) .. " " .. tostring(not (not temper.is_null(left3) and (left3 --[[@as classes.Loose]]):eq(loose))))
local subject = maybeLoose(-2)
local matched = not temper.is_null(subject) and (subject --[[@as classes.Loose]]):eq(loose) and "loose" or "other"
print(matched)
local b1 = Boxed("x", 1)
local b3 = Boxed("z", 2)
print(b1.value .. " " .. tostring(b1:eq(Boxed("y", 1))) .. " " .. tostring(not b1:eq(b3)) .. " " .. tostring(b1:sameAs(Boxed("y", 1))) .. " " .. tostring(b3:sameAs(b1)))
print(tostring(Version(1, 2):compareTo(Version(1, 3)) < 0) .. " " .. tostring(Version(2, 0):compareTo(Version(1, 9)) >= 0))
print(tostring(temper.instance_of(q, Square)) .. " " .. tostring(temper.instance_of(s, Square)))
local sq = temper.cast_to(q, Square)
print(tostring(sq.area))
if temper.instance_of(s, Rect) then
  print(tostring((s --[[@as classes.Rect]]).w))
end
local st = Stock({ 4, 5 })
st.items = { 1, 2, 3 }
print(st.count .. " " .. st.size .. " " .. st:sizeNow() .. " " .. temper.list_length(st.items))
local plain = Theme("black", "white")
local spread = plain
local loud = Theme(spread.fg, spread.bg, 20, true)
local spread2 = loud
local dark = Theme(plain.bg, plain.fg, spread2.size, spread2.bold)
print(plain:toString() .. " | " .. loud:toString() .. " | " .. dark:toString())
local spread3 = picked(dark, "old")
local arg = spread3.bg
local arg2 = noted("red")
local arg3 = spread3.bold
local mixed = Theme(arg2, arg, 9, arg3)
print(mixed:toString())
print(compared(function(a, b) return temper.is_null(a) and temper.is_null(b) or not temper.is_null(a) and (not temper.is_null(b) and (a --[[@as classes.Loose]]):eq(b --[[@as classes.Loose]])) end))
print(compared(function(a, b) return not (temper.is_null(a) and temper.is_null(b) or not temper.is_null(a) and (not temper.is_null(b) and (a --[[@as classes.Loose]]):eq(b --[[@as classes.Loose]]))) end))
print(compared(function(a, b) return temper.is_null(b) and temper.is_null(a) or not temper.is_null(b) and (not temper.is_null(a) and (b --[[@as classes.Loose]]):eq(a --[[@as classes.Loose]])) end))

---@param a classes.Loose|temper.Null
---@param b classes.Loose|temper.Null
---@return boolean
local function fn4(a, b)
  if temper.is_null(a) then
    return false
  end
  if temper.is_null(b) then
    return false
  end
  return (a --[[@as classes.Loose]]):mirrors(b --[[@as classes.Loose]])
end

print(compared(fn4))
local favorite = same("number")
local named = 0
do
  local i = 0
  while i < 6 do
    typeNamed(i)
    named = temper.int32_add(named, 1)
    i = temper.int32_add(i, 1)
  end
end
print(named .. " types")
local sql = Sql()
sql:appendString("it")
sql:appendString(temper.null)
sql:appendInt(3)
sql:appendShape(Square(2))
sql:appendRect(Rect(1, 2))
print(sql.accumulated)
local seven = 7
local accumulator = Sql()
accumulator:appendSafe("SELECT ")
accumulator:appendInt(seven)
accumulator:appendSafe(", ")
accumulator:appendString("x")
accumulator:appendSafe(" FROM ")
accumulator:appendRect(Rect(2, 2))
print(accumulator.accumulated)
local stepper = Stepper(3)
print("next " .. stepper:next(1))
print(orFail(true, 4, IntFailing()) .. " " .. orFail(true, "s", AnyFailing()))

return {
  Shape = Shape,
  Rect = Rect,
  Square = Square,
  Limited = Limited,
  Stepper = Stepper,
}
