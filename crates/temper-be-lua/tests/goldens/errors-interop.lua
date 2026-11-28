local temper = require("temper-core")

---@class errors-interop.ErrorsInteropError: temper.Failure

---@class errors-interop.ErrorsInteropError.type: temper.Type
local ErrorsInteropError = temper.type("errors-interop.ErrorsInteropError", temper.Failure)

---@class errors-interop.NotFound: errors-interop.ErrorsInteropError
---@field key string
---@field message string

---@class errors-interop.NotFound.type: temper.Type
---@operator call: errors-interop.NotFound
local NotFound = temper.type("errors-interop.NotFound", ErrorsInteropError)

---@param this errors-interop.NotFound
---@param key string
function NotFound.constructor(this, key)
  this.key = key
end

---@param this errors-interop.NotFound
---@return string
function NotFound.get.message(this)
  return "not found (key: " .. this.key .. ")"
end

---@class errors-interop.SignError: errors-interop.ErrorsInteropError

---@class errors-interop.SignError.type: temper.Type
local SignError = temper.type("errors-interop.SignError", ErrorsInteropError)

---@class errors-interop.BadSign: errors-interop.SignError
---@field text string
---@field message string

---@class errors-interop.BadSign.type: temper.Type
---@operator call: errors-interop.BadSign
local BadSign = temper.type("errors-interop.BadSign", SignError)

---@param this errors-interop.BadSign
---@param text string
function BadSign.constructor(this, text)
  this.text = text
end

---@param this errors-interop.BadSign
---@return string
function BadSign.get.message(this)
  return "bad sign (text: " .. this.text .. ")"
end

---@class errors-interop.EmptyText: errors-interop.SignError
---@field message string

---@class errors-interop.EmptyText.type: temper.Type
---@operator call: errors-interop.EmptyText
local EmptyText = temper.type("errors-interop.EmptyText", SignError)

---@param this errors-interop.EmptyText
function EmptyText.constructor(this)
end

---@param this errors-interop.EmptyText
---@return string
function EmptyText.get.message(this)
  return "no text"
end

---@class errors-interop.LookupFailed: errors-interop.ErrorsInteropError
---@field name string
---@field message string

---@class errors-interop.LookupFailed.type: temper.Type
---@operator call: errors-interop.LookupFailed
local LookupFailed = temper.type("errors-interop.LookupFailed", ErrorsInteropError)

---@param this errors-interop.LookupFailed
---@param name string
function LookupFailed.constructor(this, name)
  this.name = name
end

---@param this errors-interop.LookupFailed
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
  local t_1
  local t_2, failure = temper.catch(function()
    t_1 = lookup(name)
  end, NotFound)
  if not t_2 then
    error(temper.with_cause(LookupFailed(name), failure))
  end
  return t_1
end

---@param name string
---@return integer
local function madeBefore(name)
  local failure = LookupFailed(name)
  ---@type integer
  local t_1
  local t_2, failure2 = temper.catch(function()
    t_1 = lookup(name)
  end, NotFound)
  if not t_2 then
    error(temper.with_cause(failure, failure2))
  end
  return t_1
end

---@param name string
---@return integer
local function thrownAgain(name)
  ---@type integer
  local t_1
  local t_4, e = temper.catch(function()
    t_1 = setting(name)
  end, LookupFailed)
  if not t_4 then
    ---@type integer
    local t_2
    local t_3, failure = temper.catch(function()
      t_2 = lookup("q")
    end, NotFound)
    if not t_3 then
      error(temper.with_cause(e, failure))
    end
    t_1 = t_2
  end
  return t_1
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
  local t_1
  local t_2, e = temper.catch(function()
    t_1 = tostring(setting(name))
  end, LookupFailed)
  if not t_2 then
    local cause = temper.failure_cause(e)
    local why = not temper.is_null(cause) and (cause --[[@as temper.Failure]]).message or "nothing"
    t_1 = e.message .. " for " .. e.name .. ", from " .. why
  end
  return t_1
end

---@param text string
---@return string
local function signMessage(text)
  ---@type string
  local t_1
  local t_2, e = temper.catch(function()
    t_1 = tostring(sign(text))
  end, SignError)
  if not t_2 then
    t_1 = e.message
  end
  return t_1
end

---@param name string
---@return string
local function earlyCause(name)
  ---@type string
  local t_1
  local t_2, e = temper.catch(function()
    t_1 = tostring(madeBefore(name))
  end, LookupFailed)
  if not t_2 then
    local cause = temper.failure_cause(e)
    t_1 = not temper.is_null(cause) and (cause --[[@as temper.Failure]]).message or "nothing"
  end
  return t_1
end

---@param e errors-interop.SignError
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
---@param text string
---@return integer
local function mixed(key, text)
  ---@type integer
  local t_1
  if not temper.catch(function()
    t_1 = temper.int32_add(lookup(key), temper.int32_div(10, sign(text)))
  end, ErrorsInteropError) then
    t_1 = -1
  end
  return t_1
end

---@param texts string[]
---@return string
local function firstSign(texts)
  do
    local length = temper.list_length(texts)
    local i = 0
    while i < length do
      local t = temper.list_get(texts, i)
      i = temper.int32_add(i, 1)
      ---@type integer
      local s
      ---@type errors-interop.SignError|temper.Null
      local caught = temper.null
      ---@type integer|temper.Null
      local held = temper.null
      local t_1, failure = temper.catch(function()
        held = sign(t)
      end, SignError)
      if not t_1 then
        caught = failure
      end
      if temper.is_null(held) then
        local e = caught --[[@as errors-interop.SignError]]
        if temper.instance_of(e, BadSign) then
          return "bad " .. (e --[[@as errors-interop.BadSign]]).text
        end
        goto continue
      else
        s = held --[[@as integer]]
      end
      do return tostring(s) end
      ::continue::
    end
  end
  return "none"
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
      ---@type errors-interop.NotFound|temper.Null
      local caught = temper.null
      ---@type integer|temper.Null
      local held = temper.null
      local t_1, failure = temper.catch(function()
        held = lookup(k)
      end, NotFound)
      if not t_1 then
        caught = failure
      end
      if temper.is_null(held) then
        local e = caught --[[@as errors-interop.NotFound]]
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

print(lookup("a") .. " " .. sign("-") .. " " .. setting("a"))
print(mixed("a", "") .. " " .. mixed("z", "+") .. " " .. mixed("a", "-"))
print(explained("port") .. "; " .. explained("a") .. "; " .. signMessage("x"))
print(earlyCause("soon") .. "; " .. firstMissing({ "a", "b", "c" }) .. " " .. firstMissing({ "a" }))
print(firstSign({ "", "+" }) .. " " .. firstSign({ "", "?" }) .. " " .. firstSign({}))

return {
  ErrorsInteropError = ErrorsInteropError,
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
  firstSign = firstSign,
  firstMissing = firstMissing,
}
