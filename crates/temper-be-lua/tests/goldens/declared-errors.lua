local temper = require("temper-core")

---@class declared-errors.DeclaredErrorsError: temper.Failure

---@class declared-errors.DeclaredErrorsError.type: temper.Type
local DeclaredErrorsError = temper.type("declared-errors.DeclaredErrorsError", temper.Failure)

---@class declared-errors.NotFound: declared-errors.DeclaredErrorsError
---@field key string
---@field message string

---@class declared-errors.NotFound.type: temper.Type
---@operator call: declared-errors.NotFound
local NotFound = temper.type("declared-errors.NotFound", DeclaredErrorsError)

---@param this declared-errors.NotFound
---@param key string
function NotFound.constructor(this, key)
  this.key = key
end

---@param this declared-errors.NotFound
---@return string
function NotFound.get.message(this)
  return "not found (key: " .. this.key .. ")"
end

---@param key string
---@return integer
local function lookup(key)
  if key == "a" then
    return 1
  end
  if key == "b" then
    return 2
  end
  error(NotFound(key))
end

---@param a string
---@param b string
---@return integer
local function sum(a, b)
  local x = lookup(a)
  local y = lookup(b)
  return temper.int32_add(x, y)
end

---@param a string
---@param b string
---@return string
local function shown(a, b)
  ---@type integer
  local total
  ---@type declared-errors.NotFound|temper.Null
  local caught = temper.null
  ---@type integer|temper.Null
  local held = temper.null
  local t_1, failure = temper.catch(function()
    held = sum(a, b)
  end, NotFound)
  if not t_1 then
    caught = failure
  end
  if temper.is_null(held) then
    local e = caught --[[@as declared-errors.NotFound]]
    return "no " .. e.key
  else
    total = held --[[@as integer]]
  end
  return tostring(total)
end

---@class declared-errors.SignError: temper.Failure

---@class declared-errors.SignError.type: temper.Type
local SignError = temper.type("declared-errors.SignError", temper.Failure)

---@class declared-errors.EmptyText: declared-errors.SignError
---@field message string

---@class declared-errors.EmptyText.type: temper.Type
---@operator call: declared-errors.EmptyText
local EmptyText = temper.type("declared-errors.EmptyText", SignError)

---@param this declared-errors.EmptyText
function EmptyText.constructor(this)
end

---@param this declared-errors.EmptyText
---@return string
function EmptyText.get.message(this)
  return "empty text"
end

---@class declared-errors.BadSign: declared-errors.SignError
---@field text string
---@field message string

---@class declared-errors.BadSign.type: temper.Type
---@operator call: declared-errors.BadSign
local BadSign = temper.type("declared-errors.BadSign", SignError)

---@param this declared-errors.BadSign
---@param text string
function BadSign.constructor(this, text)
  this.text = text
end

---@param this declared-errors.BadSign
---@return string
function BadSign.get.message(this)
  return "bad sign (text: " .. this.text .. ")"
end

---@param text string
---@return integer
local function sign(text)
  if text == "+" then
    return 1
  elseif text == "-" then
    return -1
  else
    error(BadSign(text))
  end
end

---@param text string
---@return integer
local function parseSign(text)
  if text == "" then
    error(EmptyText())
  end
  return sign(text)
end

---@param e declared-errors.SignError
---@return string
local function describe(e)
  if temper.instance_of(e, EmptyText) then
    return "empty"
  elseif temper.instance_of(e, BadSign) then
    return "bad sign " .. (e --[[@as declared-errors.BadSign]]).text
  else
    error("panic")
  end
end

---@param p declared-errors.NotFound
---@return integer
local function rethrown(p)
  error(p)
end

---@class declared-errors.ConfigError: declared-errors.DeclaredErrorsError
---@field setting string
---@field message string

---@class declared-errors.ConfigError.type: temper.Type
---@operator call: declared-errors.ConfigError
local ConfigError = temper.type("declared-errors.ConfigError", DeclaredErrorsError)

---@param this declared-errors.ConfigError
---@param setting string
function ConfigError.constructor(this, setting)
  this.setting = setting
end

---@param this declared-errors.ConfigError
---@return string
function ConfigError.get.message(this)
  return "config error (setting: " .. this.setting .. ")"
end

---@param name string
---@return integer
local function setting(name)
  ---@type integer
  local t_1
  local t_2, e = temper.catch(function()
    t_1 = lookup(name)
  end, NotFound)
  if not t_2 then
    error(temper.with_cause(ConfigError(name .. ": missing " .. e.key), e))
  end
  return t_1
end

---@param a string
---@param b string
---@return string
local function pair(a, b)
  ---@type string
  local t_1
  local t_2, e = temper.catch(function()
    local x = lookup(a)
    ---@type integer
    local y
    if not temper.catch(function()
      y = lookup(b)
    end, NotFound) then
      y = 0
    end
    t_1 = x .. "+" .. y
  end, NotFound)
  if not t_2 then
    t_1 = "outer " .. e.key
  end
  return t_1
end

---@type integer
local found
if not temper.catch(function()
  found = lookup("a")
end, NotFound) then
  found = -1
end
---@type integer
local missing
if not temper.catch(function()
  missing = lookup("z")
end, NotFound) then
  missing = -1
end
print(found .. " " .. missing)
---@type integer
local n
local t_1, e = temper.catch(function()
  n = lookup("q")
end, NotFound)
if not t_1 then
  print("not found: " .. e.key)
  n = 0
end
---@type integer
local m
if not temper.catch(function()
  m = lookup("b")
end, NotFound) then
  m = -1
end
print(n .. " " .. m)
print(shown("a", "b") .. " " .. shown("a", "x") .. " " .. shown("y", "z"))
do
  local list = { "+", "-", "", "x" }
  local length = temper.list_length(list)
  local i = 0
  while i < length do
    local t = temper.list_get(list, i)
    i = temper.int32_add(i, 1)
    ---@type integer
    local s
    ---@type declared-errors.SignError|temper.Null
    local caught = temper.null
    ---@type integer|temper.Null
    local held = temper.null
    local t_2, failure = temper.catch(function()
      held = parseSign(t)
    end, SignError)
    if not t_2 then
      caught = failure
    end
    if temper.is_null(held) then
      local e2 = caught --[[@as declared-errors.SignError]]
      print(describe(e2))
      goto continue
    else
      s = held --[[@as integer]]
    end
    print(tostring(s))
    ::continue::
  end
end
---@type integer
local code
local t_3, e3 = temper.catch(function()
  code = parseSign("?")
end, SignError)
if not t_3 then
  code = temper.instance_of(e3, EmptyText) and 0 or (temper.instance_of(e3, BadSign) and 9 or error("panic"))
end
print(tostring(code))
local kept = NotFound("none")
---@type integer
local v
local t_4, e4 = temper.catch(function()
  v = lookup("k")
end, NotFound)
if not t_4 then
  kept = e4
  v = -1
end
local problems = { NotFound("x"), kept }
print(v .. " " .. temper.listed_join(problems, ",", function(p) return p.key end))
---@type integer
local w
local t_5, e5 = temper.catch(function()
  w = rethrown(kept)
end, NotFound)
if not t_5 then
  print("again " .. e5.key)
  w = 0
end
---@type integer
local port
local t_6, e6 = temper.catch(function()
  port = setting("port")
end, ConfigError)
if not t_6 then
  print(e6.setting)
  port = -1
end
---@type integer
local first
if not temper.catch(function()
  first = setting("a")
end, ConfigError) then
  first = -1
end
print(port .. " " .. first)
print(pair("a", "z") .. " " .. pair("z", "a"))
local total = 0
do
  local list2 = { "a", "b", "c", "a" }
  local length2 = temper.list_length(list2)
  local i2 = 0
  while i2 < length2 do
    local k = temper.list_get(list2, i2)
    i2 = temper.int32_add(i2, 1)
    local int32 = total
    ---@type integer
    local t2
    ---@type integer|temper.Null
    local held2 = temper.null
    if not temper.catch(function()
      held2 = lookup(k)
    end, NotFound) then
    end
    if temper.is_null(held2) then
      break
    else
      t2 = held2 --[[@as integer]]
    end
    total = temper.int32_add(int32, t2)
  end
end
print(tostring(total))

return {}
