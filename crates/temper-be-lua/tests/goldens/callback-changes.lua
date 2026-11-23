local temper = require("temper-core")

---@type integer[]
local f
---@type integer
local peeks
---@type integer[]
local t
---@type integer[]
local u
---@type integer[]
local s

---@param ls integer[]
---@return string
local function show(ls)
  return temper.listed_join(ls, ",", function(n) return tostring(n) end)
end

---@return string[]
local function words()
  local w = temper.listbuilder_constructor()
  do
    local i = 0
    while i < 3 do
      temper.listbuilder_add(w, "w" .. i)
      i = temper.int32_add(i, 1)
    end
  end
  return w
end

---@return integer
local function reduceFromGrowing()
  local g = temper.list_tolistbuilder({ 1, 2 })
  ---@param sum integer
  ---@param n integer
  ---@return integer
  local function fn(sum, n)
    if temper.listbuilder_length(g) < 4 then
      temper.listbuilder_add(g, 9)
    end
    return temper.int32_add(temper.int32_mul(sum, 10), n)
  end
  return temper.listed_reducefrom(g, 0, fn)
end

---@return integer
local function mapShrinking()
  local w = words()
  ---@param s string
  ---@return string
  local function fn(s)
    if temper.listbuilder_length(w) > 1 then
      temper.listbuilder_removelast(w)
    end
    return s
  end
  return temper.list_length(temper.listed_map(w, fn))
end

---@return integer
local function reduceShrinking()
  local w = words()
  ---@param all string
  ---@param s string
  ---@return string
  local function fn(all, s)
    if temper.listbuilder_length(w) > 1 then
      temper.listbuilder_removelast(w)
    end
    return all
  end
  local all2 = temper.listed_reduce(w, fn)
  if all2 == "" then
    return 0
  else
    return 1
  end
end

---@return integer
local function filterGrowing()
  local g = temper.list_tolistbuilder({ 1, 2 })
  ---@param n integer
  ---@return boolean
  local function fn(n)
    if temper.listbuilder_length(g) < 4 then
      temper.listbuilder_add(g, 9)
    end
    return true
  end
  return temper.list_length(temper.listed_filter(g, fn))
end

---@return integer
local function joinGrowing()
  local g = temper.list_tolistbuilder({ 1, 2 })
  ---@param n integer
  ---@return string
  local function fn(n)
    if temper.listbuilder_length(g) < 4 then
      temper.listbuilder_add(g, 9)
    end
    return tostring(n)
  end
  local joined = temper.listed_join(g, ",", fn)
  if joined == "" then
    return 0
  else
    return 1
  end
end

---@return table<string, integer>
local function letters()
  local m = temper.mapbuilder_constructor()
  temper.mapbuilder_set(m, "a" .. 1, 1)
  temper.mapbuilder_set(m, "b" .. 2, 2)
  temper.mapbuilder_set(m, "c" .. 3, 3)
  return m
end

---@return integer
local function forEachRemoving()
  local m = letters()
  local sum = 0
  ---@param k string
  ---@param v integer
  local function fn(k, v)
    if temper.mapped_has(m, "c3") then
      if not temper.pcall(function()
        temper.mapbuilder_remove(m, "c3")
      end) then
      end
    end
    sum = temper.int32_add(sum, v)
  end
  temper.mapped_foreach(m, fn)
  return sum
end

---@return integer
local function forEachAdding()
  local m = letters()
  local count = 0
  ---@param k string
  ---@param v integer
  local function fn(k, v)
    count = temper.int32_add(count, 1)
    if temper.mapped_length(m) < 30 then
      temper.mapbuilder_set(m, "k" .. temper.mapped_length(m), 1)
    end
  end
  temper.mapped_foreach(m, fn)
  return count
end

---@return integer
local function toListWithRemoving()
  local m = letters()
  ---@param k string
  ---@param v integer
  ---@return integer
  local function fn(k, v)
    if temper.mapped_has(m, "c3") then
      if not temper.pcall(function()
        temper.mapbuilder_remove(m, "c3")
      end) then
      end
    end
    return v
  end
  return temper.list_length(temper.mapped_tolistwith(m, fn))
end

local b = temper.list_tolistbuilder({ 1, 2 })
temper.listbuilder_addall(b, b)
local c = temper.list_tolistbuilder({ 1, 2 })
temper.listbuilder_addall(c, c, 1)
local d = temper.list_tolistbuilder({ 1, 2, 3 })
local removed = temper.listbuilder_splice(d, 1, 1, d)
print("self " .. show(b) .. " " .. show(c) .. " " .. show(d) .. " " .. show(removed))
local e = temper.list_tolistbuilder({ 1, 2 })
local kept = temper.listed_filter(e, function(n) return n > 0 end)
temper.listbuilder_add(e, 3)
print("filter " .. show(kept) .. " " .. show(e))
f = temper.list_tolistbuilder({ 3, 1, 2 })
peeks = 0

---@param a integer
---@param z integer
---@return integer
local function fn2(a, z)
  peeks = temper.int32_add(peeks, temper.listbuilder_length(f))
  return temper.int32_sub(a, z)
end

temper.listbuilder_sort(f, fn2)
print("peeking " .. show(f))
---@type integer
local t_1
if not temper.pcall(function()
  t_1 = reduceFromGrowing()
end) then
  t_1 = -1
end
---@type integer
local t_2
if not temper.pcall(function()
  t_2 = mapShrinking()
end) then
  t_2 = -1
end
---@type integer
local t_3
if not temper.pcall(function()
  t_3 = reduceShrinking()
end) then
  t_3 = -1
end
---@type integer
local t_4
if not temper.pcall(function()
  t_4 = filterGrowing()
end) then
  t_4 = -1
end
---@type integer
local t_5
if not temper.pcall(function()
  t_5 = joinGrowing()
end) then
  t_5 = -1
end
---@type integer
local t_6
if not temper.pcall(function()
  t_6 = forEachRemoving()
end) then
  t_6 = -1
end
---@type integer
local t_7
if not temper.pcall(function()
  t_7 = forEachAdding()
end) then
  t_7 = -1
end
---@type integer
local t_8
if not temper.pcall(function()
  t_8 = toListWithRemoving()
end) then
  t_8 = -1
end
local walked = { t_1, t_2, t_3, t_4, t_5, t_6, t_7, t_8 }
print("walks " .. show(walked))
t = temper.list_tolistbuilder({ 3, 1, 2 })

---@param a integer
---@param z integer
---@return integer
local function fn3(a, z)
  temper.listbuilder_set(t, 0, 99)
  return temper.int32_sub(a, z)
end

temper.listbuilder_sort(t, fn3)
u = temper.list_tolistbuilder({ 3, 1, 2 })

---@param a integer
---@param z integer
---@return integer
local function fn4(a, z)
  local last = temper.listbuilder_removelast(u)
  temper.listbuilder_add(u, temper.int32_add(last, 10))
  return temper.int32_sub(a, z)
end

temper.listbuilder_sort(u, fn4)
print("same length " .. show(t) .. " " .. show(u))
s = temper.list_tolistbuilder({ 3, 1, 2 })

---@param a integer
---@param z integer
---@return integer
local function fn5(a, z)
  if temper.listbuilder_length(s) < 40 then
    temper.listbuilder_add(s, 0)
  end
  return temper.int32_sub(a, z)
end

temper.listbuilder_sort(s, fn5)
print("sorted " .. temper.listbuilder_length(s))

return {}
