local temper = require("temper-core")

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
local twelve
if not temper.catch(function()
  twelve = both(find, function(k) return 10 end, "two")
end, Missing) then
  twelve = -1
end
---@type integer
local lost
local t, e = temper.catch(function()
  lost = both(find, find, "six")
end, Missing)
if not t then
  print("lost " .. e.key)
  lost = -1
end
print(twelve .. " " .. lost)
print(either("one") .. " " .. either("ten"))
---@type string
local t_1
if not temper.catch(function()
  t_1 = parts({ "one", "two" })
end, Missing) then
  t_1 = "?"
end
print(t_1)
---@type string
local t_2
local t_3, e2 = temper.catch(function()
  t_2 = parts({ "one", "six" })
end, Missing)
if not t_3 then
  t_2 = "missing " .. e2.key
end
print(t_2)
print(temper.listed_join(temper.listed_sorted({ 3, 1, 2 }, function(a, b) return temper.int32_sub(a, b) end), ",", function(n) return tostring(n) end))

return {}
