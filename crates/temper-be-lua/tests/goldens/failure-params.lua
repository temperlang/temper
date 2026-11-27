local temper = require("temper-core")
local Match = require("std/regex").Match
local RegexNode = require("std/regex").RegexNode
local Repeat = require("std/regex").Repeat
local CodeSet = require("std/regex").CodeSet
local CodePart = require("std/regex").CodePart
local CodeRange = require("std/regex").CodeRange
local CodePoints = require("std/regex").CodePoints

---@class failure-params.FailureParamsError: temper.Failure

---@class failure-params.FailureParamsError.type: temper.Type
local FailureParamsError = temper.type("failure-params.FailureParamsError", temper.Failure)

---@class failure-params.Missing: failure-params.FailureParamsError
---@field key string
---@field message string

---@class failure-params.Missing.type: temper.Type
---@operator call: failure-params.Missing
local Missing = temper.type("failure-params.Missing", FailureParamsError)

---@param this failure-params.Missing
---@param key string
function Missing.constructor(this, key)
  this.key = key
end

---@param this failure-params.Missing
---@return string
function Missing.get.message(this)
  return "missing (key: " .. this.key .. ")"
end

---@param key string
---@return integer
local function find(key)
  if key == "one" then
    return 1
  end
  if key == "two" then
    return 2
  end
  error(Missing(key))
end

---@class failure-params.Mapper
---@field apply fun(self: failure-params.Mapper, x: any): any

---@class failure-params.Mapper.type: temper.Type
local Mapper = temper.type("failure-params.Mapper")

---@generic T
---@generic R
---@param xs T[]
---@param f fun(arg0: T): R
---@return R[]
local function mapAll(xs, f)
  local out = temper.listbuilder_constructor()
  do
    local length = temper.list_length(xs)
    local i = 0
    while i < length do
      local x = temper.list_get(xs, i)
      i = temper.int32_add(i, 1)
      temper.listbuilder_add(out, f(x))
    end
  end
  return temper.listbuilder_tolist(out)
end

---@param keys string[]
---@return integer[]
local function found(keys)
  return mapAll(keys, function(k) return find(k) end)
end

---@param keys string[]
---@return string
local function shown(keys)
  ---@type integer[]
  local ns
  ---@type failure-params.Missing|temper.Null
  local caught = temper.null
  ---@type integer[]|temper.Null
  local held = temper.null
  local t, failure = temper.catch(function()
    held = found(keys)
  end, Missing)
  if not t then
    caught = failure
  end
  if temper.is_null(held) then
    local e = caught --[[@as failure-params.Missing]]
    return "missing " .. e.key
  else
    ns = held --[=[@as integer[]]=]
  end
  return temper.listed_join(ns, ",", function(n) return tostring(n) end)
end

---@param f fun(arg0: integer): integer
---@param n integer
---@return integer
local function applied(f, n)
  return f(n)
end

---@generic T
---@generic R
---@param xs T[]
---@param f fun(arg0: T): R
---@return integer
local function failures(xs, f)
  local failed = 0
  do
    local length = temper.list_length(xs)
    local i = 0
    while i < length do
      local x = temper.list_get(xs, i)
      i = temper.int32_add(i, 1)
      if not temper.catch(function()
        f(x)
      end, temper.Failure) then
        failed = temper.int32_add(failed, 1)
      end
    end
  end
  return failed
end

---@generic T
---@param xs T[]
---@param f fun(arg0: T): integer
---@return integer
local function sumAll(xs, f)
  local total = 0
  ---@type temper.Failure|temper.Null
  local first = temper.null
  do
    local length = temper.list_length(xs)
    local i = 0
    while i < length do
      local x = temper.list_get(xs, i)
      i = temper.int32_add(i, 1)
      local t, e = temper.catch(function()
        total = temper.int32_add(total, f(x))
      end, temper.Failure)
      if not t then
        if temper.is_null(first) then
          first = e
        end
      end
    end
  end
  local failed = first
  if not temper.is_null(failed) then
    error(failed --[[@as temper.Failure]])
  end
  return total
end

---@param a fun(arg0: string): integer
---@param b fun(arg0: string): integer
---@param key string
---@return integer
local function both(a, b, key)
  return temper.int32_add(a(key), b(key))
end

---@class failure-params.Unknown: failure-params.FailureParamsError
---@field word string
---@field message string

---@class failure-params.Unknown.type: temper.Type
---@operator call: failure-params.Unknown
local Unknown = temper.type("failure-params.Unknown", FailureParamsError)

---@param this failure-params.Unknown
---@param word string
function Unknown.constructor(this, word)
  this.word = word
end

---@param this failure-params.Unknown
---@return string
function Unknown.get.message(this)
  return "unknown (word: " .. this.word .. ")"
end

---@param key string
---@return integer
local function word(key)
  if key == "ten" then
    return 10
  end
  error(Unknown(key))
end

---@param key string
---@return string
local function either(key)
  ---@type integer
  local n
  ---@type failure-params.FailureParamsError|temper.Null
  local caught = temper.null
  ---@type integer|temper.Null
  local held = temper.null
  local t, failure = temper.catch(function()
    held = both(function(x) return find(x) end, function(x2) return word(x2) end, key)
  end, FailureParamsError)
  if not t then
    caught = failure
  end
  if temper.is_null(held) then
    local e = caught --[[@as failure-params.FailureParamsError]]
    return temper.instance_of(e, Missing) and "no " .. (e --[[@as failure-params.Missing]]).key or (temper.instance_of(e, Unknown) and "unknown " .. (e --[[@as failure-params.Unknown]]).word or error("panic"))
  else
    n = held --[[@as integer]]
  end
  return tostring(n)
end

---@param keys string[]
---@return string
local function parts(keys)
  local found = temper.listed_filter(keys, function(k) return find(k) > 1 end)
  local total = temper.listed_reducefrom(keys, 0, function(n, k2) return temper.int32_add(n, find(k2)) end)
  return temper.listed_join(found, ",", function(k3) return k3 end) .. " " .. total .. " " .. temper.listed_join(keys, "+", function(k4) return tostring(find(k4)) end)
end

---@generic T
---@param xs T[]
---@param f fun(arg0: T): T
---@return T[]
local function mapTwice(xs, f)
  local once = mapAll(xs, f)
  return temper.listed_map(once, f)
end

---@param n integer
---@return integer
local function upToTwo(n)
  if n > 2 then
    error(Missing(tostring(n)))
  end
  return temper.int32_add(n, 1)
end

---@param text string
---@return string
local function keyed(text)
  ---@param m std.regex.Match
  ---@return string
  local function fn(m)
    print("seen " .. m.full.value)
    return tostring(find(m.full.value))
  end
  return Repeat(CodeSet({ CodeRange(97, 122) }, false), 1, temper.null, false):compiled():replace(text, fn)
end

---@param m std.regex.Match
---@return string
local function bracketed(m)
  return "<" .. m.full.value .. ">"
end

local doubled = mapAll({ 1, 2, 3 }, function(n) return temper.int32_mul(n, 2) end)
local safe = function(n) return temper.int32_add(n, 1) end
print(temper.listed_join(doubled, ",", function(n) return tostring(n) end) .. " " .. safe(1))
---@type integer[]
local named
if not temper.catch(function()
  named = mapAll({ "two", "one" }, find)
end, Missing) then
  named = {}
end
print(shown({ "one", "two" }) .. " " .. shown({ "one", "six", "ten" }) .. " " .. temper.list_length(named))
---@type integer
local six
if not temper.catch(function()
  six = applied(function(n) return temper.int32_mul(n, 3) end, 2)
end, Missing) then
  six = -1
end
print(tostring(six))
local some = failures({ "one", "six", "two", "ten" }, find)
local none = failures({ 1, 2 }, function(n) return n end)
print(some .. " " .. none)
---@type integer
local summed
if not temper.catch(function()
  summed = sumAll({ "one", "two" }, find)
end, Missing) then
  summed = -1
end
---@type integer
local unsummed
local t, e = temper.catch(function()
  unsummed = sumAll({ "six", "one", "ten" }, find)
end, Missing)
if not t then
  print("first missing " .. e.key)
  unsummed = -1
end
print(summed .. " " .. unsummed .. " " .. sumAll({ 1, 2 }, safe))
---@type integer
local twelve
if not temper.catch(function()
  twelve = both(find, function(k) return 10 end, "two")
end, Missing) then
  twelve = -1
end
---@type integer
local lost
local t_1, e2 = temper.catch(function()
  lost = both(find, find, "six")
end, Missing)
if not t_1 then
  print("lost " .. e2.key)
  lost = -1
end
print(twelve .. " " .. lost)
print(either("one") .. " " .. either("ten"))
---@type string
local t_2
if not temper.catch(function()
  t_2 = parts({ "one", "two" })
end, Missing) then
  t_2 = "?"
end
print(t_2)
---@type string
local t_3
local t_4, e3 = temper.catch(function()
  t_3 = parts({ "one", "six" })
end, Missing)
if not t_4 then
  t_3 = "missing " .. e3.key
end
print(t_3)
print(temper.listed_join(temper.listed_sorted({ 3, 1, 2 }, function(a, b) return temper.int32_sub(a, b) end), ",", function(n) return tostring(n) end))
local bumped = temper.listed_map({ 1, 2 }, safe)
local twice = mapTwice({ 1, 2 }, safe)
---@type integer[]
local failed
local t_5, e4 = temper.catch(function()
  failed = mapTwice({ 1, 2 }, upToTwo)
end, Missing)
if not t_5 then
  print("missing " .. e4.key)
  failed = {}
end
print(temper.listed_join(bumped, ",", function(n) return tostring(n) end) .. " " .. temper.listed_join(twice, ",", function(n) return tostring(n) end) .. " " .. temper.list_length(failed))
---@type string
local allKeyed
if not temper.catch(function()
  allKeyed = keyed("one+two")
end, Missing) then
  allKeyed = "?"
end
---@type string
local noneKeyed
local t_6, e5 = temper.catch(function()
  noneKeyed = keyed("one+six+ten")
end, Missing)
if not t_6 then
  noneKeyed = "missing " .. e5.key
end
local plain = CodePoints("o"):compiled():replace("two", function(m) return "0" end)
print(allKeyed .. " " .. noneKeyed .. " " .. plain .. " " .. CodePoints("w"):compiled():replace("two", bracketed))

return {}
