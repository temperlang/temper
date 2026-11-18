local temper = require("temper-core")

---@type table<string, integer>
local tally

---@class generics.Holder
---@field held any

---@class generics.Holder.type: temper.Type
local Holder = temper.type("generics.Holder")

---@class generics.Box: generics.Holder
---@field value any
---@field held any
---@field pairWith fun(self: generics.Box, other: integer): { key: any, value: integer }

---@class generics.Box.type: temper.Type
---@operator call: generics.Box
local Box = temper.type("generics.Box", Holder)

---@param this generics.Box
---@param value any
function Box.constructor(this, value)
  this.value = value
end

---@param this generics.Box
---@return any
function Box.get.held(this)
  return this.value
end

---@param this generics.Box
---@param other integer
---@return { key: any, value: integer }
function Box.methods.pairWith(this, other)
  return temper.pair_constructor(this.value, other)
end

---@class generics.Named
---@field name string

---@class generics.Named.type: temper.Type
---@operator call: generics.Named
local Named = temper.type("generics.Named")

---@param this generics.Named
---@param name string
function Named.constructor(this, name)
  this.name = name
end

---@class generics.Counter
---@field n integer

---@class generics.Counter.type: temper.Type
---@operator call: generics.Counter
local Counter = temper.type("generics.Counter")

---@param this generics.Counter
---@param n integer
function Counter.constructor(this, n)
  this.n = n
end

---@class generics.Shelf
---@field items any[]
---@field firstName fun(self: generics.Shelf): string|temper.Null
---@field tally fun(self: generics.Shelf, extra: integer): integer

---@class generics.Shelf.type: temper.Type
---@operator call: generics.Shelf
local Shelf = temper.type("generics.Shelf")

---@param this generics.Shelf
---@param items any[]
function Shelf.constructor(this, items)
  this.items = items
end

---@param this generics.Shelf
---@return string|temper.Null
function Shelf.methods.firstName(this)
  if temper.listed_isempty(this.items) then
    return temper.null
  else
    return temper.list_get(this.items, 0).name
  end
end

---@param this generics.Shelf
---@param extra integer
---@return integer
function Shelf.methods.tally(this, extra)
  return temper.int32_add(temper.list_length(this.items), extra)
end

---@class generics.Scene

---@class generics.Scene.type: temper.Type
local Scene = temper.type("generics.Scene")

---@class generics.Stage: generics.Scene

---@class generics.Stage.type: temper.Type
---@operator call: generics.Stage
local Stage = temper.type("generics.Stage", Scene)

---@param this generics.Stage
function Stage.constructor(this)
end

---@generic S
---@generic T
---@param x T
---@return T
local function tagged(x)
  return x
end

---@generic T
---@param n integer
---@return integer
local function countOf(n)
  local b = temper.listbuilder_constructor()
  return temper.int32_add(temper.listbuilder_length(b), n)
end

---@generic S: generics.Scene
---@generic T
---@param x T
---@return T
local function relayed(x)
  return tagged(x)
end

---@param length integer
---@return boolean
local function noneIn(length)
  return length == 0
end

---@generic T
---@param items T[]
---@param fallback T
---@return T
local function first(items, fallback)
  if noneIn(temper.listed_length(items)) then
    return fallback
  else
    return temper.listed_get(items, 0)
  end
end

---@generic T
---@param h generics.Holder
---@return T
local function held(h)
  return h.held
end

---@param ns integer[]
---@return integer
local function total(ns)
  local sum = 0
  do
    local i = 0
    while i < temper.list_length(ns) do
      sum = temper.int32_add(sum, temper.list_get(ns, i))
      i = temper.int32_add(i, 1)
    end
  end
  return sum
end

local function indexed()
  local counts = temper.listbuilder_constructor()
  temper.listbuilder_add(counts, 1)
  temper.listbuilder_add(counts, 10)
  local at = 0
  do
    local k = at
    temper.listbuilder_set(counts, k, temper.int32_add(temper.listed_get(counts, k), 4))
  end
  temper.listbuilder_set(counts, 1, temper.int32_mul(temper.listed_get(counts, 1), 3))
  local t = at
  at = temper.int32_add(t, 1)
  local k2 = t
  local t2 = temper.listed_get(counts, k2)
  temper.listbuilder_set(counts, k2, temper.int32_add(t2, 1))
  local was = t2
  local k3 = at
  local t3 = temper.int32_add(temper.listed_get(counts, k3), 1)
  temper.listbuilder_set(counts, k3, t3)
  local pre = t3
  local t4 = temper.listed_get(counts, 0)
  temper.listbuilder_set(counts, 0, temper.int32_sub(t4, 1))
  local post = t4
  local t5 = temper.int32_sub(temper.listed_get(counts, 0), 1)
  temper.listbuilder_set(counts, 0, t5)
  local down = t5
  print(was .. " " .. pre .. " " .. post .. " " .. down .. " " .. at)
end

local function both()
  local x = 1
  local y = 10
  local t = x
  x = temper.int32_add(t, 1)
  local t2 = y
  y = temper.int32_add(t2, 1)
  local locals = temper.int32_add(t, t2)
  local p = Counter(100)
  local q = Counter(1000)
  local t3 = p.n
  p.n = temper.int32_add(t3, 1)
  local t4 = q.n
  q.n = temper.int32_add(t4, 1)
  local fields = temper.int32_add(t3, t4)
  local t5 = p.n
  p.n = temper.int32_add(t5, 1)
  local t6 = p.n
  p.n = temper.int32_add(t6, 1)
  local same = temper.int32_add(t5, t6)
  local a = temper.listbuilder_constructor()
  temper.listbuilder_add(a, 3)
  local b = temper.listbuilder_constructor()
  temper.listbuilder_add(b, 30)
  local i = 0
  local k = i
  local t7 = temper.listed_get(a, k)
  temper.listbuilder_set(a, k, temper.int32_add(t7, 1))
  local k2 = i
  local t8 = temper.listed_get(b, k2)
  temper.listbuilder_set(b, k2, temper.int32_add(t8, 1))
  local items = temper.int32_add(t7, t8)
  print(locals .. " " .. x .. " " .. y .. " " .. fields .. " " .. same .. " " .. p.n .. " " .. q.n .. " " .. items .. " " .. temper.listed_get(a, 0) .. " " .. temper.listed_get(b, 0))
end

---@param key string
---@return integer
local function bump(key)
  temper.mapbuilder_set(tally, key, temper.int32_sub(temper.mapped_get(tally, key), 1))
  local t = temper.mapped_get(tally, key)
  temper.mapbuilder_set(tally, key, temper.int32_add(t, 1))
  local before = t
  local t2 = temper.int32_add(temper.mapped_get(tally, key), 1)
  temper.mapbuilder_set(tally, key, t2)
  local after = t2
  return temper.int32_add(before, after)
end

---@param a integer[]
---@param b integer[]
---@return boolean
local function firstSame(a, b)
  return temper.list_get(a, 0) == temper.list_get(b, 0)
end

local ns = { 1, 2, 3 }
local none = {}
print(total(ns) .. " " .. first(ns, 0) .. " " .. first(none, -1))
print(first({ "a", "b" }, "z"))
local box = Box("boxed")
print(held(box))
local pair = box:pairWith(7)
print(pair.key .. " " .. pair.value)
local shelf = Shelf({ Named("ann"), Named("bo") })
local subject = shelf:firstName()
print(temper.is_null(subject) and "none" or (subject --[[@as string]]))
local noNames = {}
local empty = Shelf(noNames)
local subject2 = empty:firstName()
print(temper.is_null(subject2) and "none" or (subject2 --[[@as string]]))
local n = temper.int32_add(shelf:tally(1), shelf:tally(2))
print(tagged(4) .. " " .. tagged("t") .. " " .. countOf(3) .. " " .. relayed(5) .. " " .. n)
local builder = temper.listbuilder_constructor()
temper.listbuilder_add(builder, "x")
temper.listbuilder_add(builder, "y")
temper.listbuilder_add(builder, "w", 0)
print(temper.listbuilder_length(builder) .. " " .. temper.listed_get(builder, 0) .. " " .. temper.listbuilder_removelast(builder))
local built = temper.listbuilder_tolist(builder)
print(temper.list_length(built) .. " " .. temper.listed_getor(built, 5, "?") .. " " .. temper.list_get(temper.listed_slice(built, 1, 2), 0))
local m = temper.map_constructor({ temper.pair_constructor("a", 1), temper.pair_constructor("b", 2) })
---@type integer
local t_1
if not temper.pcall(function()
  t_1 = temper.mapped_get(m, "b")
end) then
  t_1 = -1
end
---@type integer
local t_2
if not temper.pcall(function()
  t_2 = temper.mapped_get(m, "c")
end) then
  t_2 = -1
end
print(t_1 .. " " .. t_2 .. " " .. tostring(temper.mapped_has(m, "a")) .. " " .. temper.mapped_length(m))
local mb = temper.mapbuilder_constructor()
temper.mapbuilder_set(mb, "q", 5)
temper.mapbuilder_set(mb, "n", temper.null)
local subject3 = temper.mapped_getor(mb, "q", 0)
local subject4 = temper.mapped_getor(mb, "n", 0)
print((temper.is_null(subject3) and -2 or (subject3 --[[@as integer]])) .. " " .. (temper.is_null(subject4) and -2 or (subject4 --[[@as integer]])) .. " " .. tostring(temper.mapped_has(mb, "n")))
---@type integer|temper.Null
local subject6
if not temper.pcall(function()
  subject6 = temper.mapbuilder_remove(mb, "q")
end) then
  subject6 = 0
end
local subject5 = temper.is_null(subject6) and temper.null or tostring(subject6 --[[@as integer]])
print(temper.is_null(subject5) and "null" or (subject5 --[[@as string]]))
print(tostring(temper.listed_length(temper.mapped_keys(mb))))
indexed()
both()
tally = temper.mapbuilder_constructor()
temper.mapbuilder_set(tally, "k", 2)
---@type integer
local t_3
if not temper.pcall(function()
  t_3 = bump("k")
end) then
  t_3 = -1
end
---@type integer
local t_4
if not temper.pcall(function()
  t_4 = bump("z")
end) then
  t_4 = -1
end
---@type integer
local t_5
if not temper.pcall(function()
  t_5 = temper.mapped_get(tally, "k")
end) then
  t_5 = -1
end
print(t_3 .. " " .. t_4 .. " " .. t_5)
print(tostring(firstSame({ 1000 }, { 1000 })) .. " " .. tostring(firstSame({ 1000 }, { 7 })))

return {
  Holder = Holder,
  Box = Box,
  Named = Named,
  Counter = Counter,
  Shelf = Shelf,
  Scene = Scene,
  Stage = Stage,
  first = first,
}
