local temper = require("temper-core")

---@class lua-failures.LuaFailuresError: temper.Failure

---@class lua-failures.LuaFailuresError.type: temper.Type
local LuaFailuresError = temper.type("lua-failures.LuaFailuresError", temper.Failure)

---@class lua-failures.NotFound: lua-failures.LuaFailuresError
---@field key string
---@field message string

---@class lua-failures.NotFound.type: temper.Type
---@operator call: lua-failures.NotFound
local NotFound = temper.type("lua-failures.NotFound", LuaFailuresError)

---@param this lua-failures.NotFound
---@param key string
function NotFound.constructor(this, key)
  this.key = key
end

---@param this lua-failures.NotFound
---@return string
function NotFound.get.message(this)
  return "not found (key: " .. this.key .. ")"
end

---@class lua-failures.SignError: lua-failures.LuaFailuresError

---@class lua-failures.SignError.type: temper.Type
local SignError = temper.type("lua-failures.SignError", LuaFailuresError)

---@class lua-failures.BadSign: lua-failures.SignError
---@field text string
---@field message string

---@class lua-failures.BadSign.type: temper.Type
---@operator call: lua-failures.BadSign
local BadSign = temper.type("lua-failures.BadSign", SignError)

---@param this lua-failures.BadSign
---@param text string
function BadSign.constructor(this, text)
  this.text = text
end

---@param this lua-failures.BadSign
---@return string
function BadSign.get.message(this)
  return "bad sign (text: " .. this.text .. ")"
end

---@class lua-failures.EmptyText: lua-failures.SignError
---@field message string

---@class lua-failures.EmptyText.type: temper.Type
---@operator call: lua-failures.EmptyText
local EmptyText = temper.type("lua-failures.EmptyText", SignError)

---@param this lua-failures.EmptyText
function EmptyText.constructor(this)
end

---@param this lua-failures.EmptyText
---@return string
function EmptyText.get.message(this)
  return "no text"
end

---@class lua-failures.LookupFailed: lua-failures.LuaFailuresError
---@field name string
---@field message string

---@class lua-failures.LookupFailed.type: temper.Type
---@operator call: lua-failures.LookupFailed
local LookupFailed = temper.type("lua-failures.LookupFailed", LuaFailuresError)

---@param this lua-failures.LookupFailed
---@param name string
function LookupFailed.constructor(this, name)
  this.name = name
end

---@param this lua-failures.LookupFailed
---@return string
function LookupFailed.get.message(this)
  return "lookup failed (name: " .. this.name .. ")"
end

---@param key string
---@return integer
local function lookup(key)
  if key == "a" then
    return 1
  end
  error(NotFound(key))
end

---@param text string
---@return integer
local function sign(text)
  if text == "+" then
    return 1
  elseif text == "-" then
    return -1
  elseif text == "" then
    error(EmptyText())
  else
    error(BadSign(text))
  end
end

---@param name string
---@return integer
local function setting(name)
  ---@type integer
  local t
  local t_1, failure = temper.catch(function()
    t = lookup(name)
  end, NotFound)
  if not t_1 then
    error(temper.with_cause(LookupFailed(name), failure))
  end
  return t
end

---@param name string
---@return integer
local function madeBefore(name)
  local failure = LookupFailed(name)
  ---@type integer
  local t
  local t_1, failure2 = temper.catch(function()
    t = lookup(name)
  end, NotFound)
  if not t_1 then
    error(temper.with_cause(failure, failure2))
  end
  return t
end

---@param name string
---@return integer
local function thrownAgain(name)
  ---@type integer
  local t
  local t_3, e = temper.catch(function()
    t = setting(name)
  end, LookupFailed)
  if not t_3 then
    ---@type integer
    local t_1
    local t_2, failure = temper.catch(function()
      t_1 = lookup("q")
    end, NotFound)
    if not t_2 then
      error(temper.with_cause(e, failure))
    end
    t = t_1
  end
  return t
end

---@param n integer
---@return integer
local function risky(n)
  if n > 1 then
    error("panic")
  end
  return lookup("a")
end

---@param name string
---@return string
local function explained(name)
  ---@type string
  local t
  local t_1, e = temper.catch(function()
    t = tostring(setting(name))
  end, LookupFailed)
  if not t_1 then
    local cause = temper.failure_cause(e)
    local why = not temper.is_null(cause) and (cause --[[@as temper.Failure]]).message or "nothing"
    t = e.message .. ", from " .. why
  end
  return t
end

---@param text string
---@return string
local function signMessage(text)
  ---@type string
  local t
  local t_1, e = temper.catch(function()
    t = tostring(sign(text))
  end, SignError)
  if not t_1 then
    t = e.message
  end
  return t
end

---@param name string
---@return string
local function earlyCause(name)
  ---@type string
  local t
  local t_1, e = temper.catch(function()
    t = tostring(madeBefore(name))
  end, LookupFailed)
  if not t_1 then
    local cause = temper.failure_cause(e)
    t = not temper.is_null(cause) and (cause --[[@as temper.Failure]]).message or "nothing"
  end
  return t
end

---@param e lua-failures.SignError
---@return string
local function signName(e)
  if temper.instance_of(e, BadSign) then
    return "bad sign"
  elseif temper.instance_of(e, EmptyText) then
    return "empty text"
  else
    error("panic")
  end
end

---@param key string
---@param n integer
---@return integer
local function mixed(key, n)
  ---@type integer
  local t
  if not temper.catch(function()
    t = temper.int32_add(lookup(key), temper.int32_div(10, n))
  end, temper.Bubble, NotFound) then
    t = -1
  end
  return t
end

---@param keys string[]
---@return string
local function firstMissing(keys)
  do
    local length = temper.list_length(keys)
    local i = 0
    while i < length do
      local k = temper.list_get(keys, i)
      i = temper.int32_add(i, 1)
      ---@type integer
      local n
      ---@type lua-failures.NotFound|temper.Null
      local caught = temper.null
      ---@type integer|temper.Null
      local held = temper.null
      local t, failure = temper.catch(function()
        held = lookup(k)
      end, NotFound)
      if not t then
        caught = failure
      end
      if temper.is_null(held) then
        local e = caught --[[@as lua-failures.NotFound]]
        return e.key
      else
        n = held --[[@as integer]]
      end
      if n < 0 then
        break
      end
    end
  end
  return "none"
end

---@param keys string[]
---@return string
local function scan(keys)
  local out = ""
  do
    local length = temper.list_length(keys)
    local i = 0
    while i < length do
      local k = temper.list_get(keys, i)
      i = temper.int32_add(i, 1)
      local t, t_1, t_2 = temper.catch(function()
        if k == "stop" then
          return "break", "flow"
        end
        out = out .. lookup(k)
      end, NotFound)
      if not t then
        local e = t_1
        out = out .. "!" .. e.key
      elseif t_1 == "break" then
        break
      end
    end
  end
  return out
end

print(lookup("a") .. " " .. sign("-") .. " " .. setting("a"))
print(mixed("a", 0) .. " " .. mixed("z", 1) .. " " .. mixed("a", 5))
print(explained("port") .. "; " .. explained("a") .. "; " .. signMessage("x"))
print(earlyCause("soon") .. "; " .. firstMissing({ "a", "b", "c" }) .. " " .. firstMissing({ "a" }))
print(scan({ "a", "x", "stop", "a" }))

return {
  LuaFailuresError = LuaFailuresError,
  NotFound = NotFound,
  SignError = SignError,
  BadSign = BadSign,
  EmptyText = EmptyText,
  LookupFailed = LookupFailed,
  lookup = lookup,
  sign = sign,
  setting = setting,
  madeBefore = madeBefore,
  thrownAgain = thrownAgain,
  risky = risky,
  explained = explained,
  signMessage = signMessage,
  earlyCause = earlyCause,
  signName = signName,
  mixed = mixed,
  firstMissing = firstMissing,
  scan = scan,
}
