local temper = require("temper-core")
local parseCount = require("count-failures").parseCount

local NotFound

-- `NotFound` is the library's own failure type, a local of its module,
-- which this is inlined in; calling it makes one, and raising that is a
-- failure, and a string a host error.
local _connected = {}

do
  ---@param key string
  ---@return integer
  function _connected.hostLookup(key)
    if key == "bug" then
      error("host bug")
    end
    if key == "a" then
      return 1
    end
    error(NotFound(key))
  end
end

---@class host-failures-several-libraries.HostFailuresError: temper.Failure

---@class host-failures-several-libraries.HostFailuresError.type: temper.Type
local HostFailuresError = temper.type("host-failures-several-libraries.HostFailuresError", temper.Failure)

---@class host-failures-several-libraries.NotFound: host-failures-several-libraries.HostFailuresError
---@field key string
---@field message string

---@class host-failures-several-libraries.NotFound.type: temper.Type
---@operator call: host-failures-several-libraries.NotFound
NotFound = temper.type("host-failures-several-libraries.NotFound", HostFailuresError)

---@param this host-failures-several-libraries.NotFound
---@param key string
function NotFound.constructor(this, key)
  this.key = key
end

---@param this host-failures-several-libraries.NotFound
---@return string
function NotFound.get.message(this)
  return "not found (key: " .. this.key .. ")"
end

---@param key string
---@return integer
local function hostLookup(key)
  return _connected.hostLookup(key)
end

---@param key string
---@param count string
---@return integer
local function total(key, count)
  ---@type integer
  local t
  local t_1, e = temper.catch(function()
    t = temper.int32_add(hostLookup(key), parseCount(count))
  end, temper.Failure)
  if not t_1 then
    print("caught " .. e.message)
    t = temper.instance_of(e, NotFound) and -1 or -2
  end
  return t
end

---@class host-failures-several-libraries.Unreadable: host-failures-several-libraries.HostFailuresError
---@field key string
---@field message string

---@class host-failures-several-libraries.Unreadable.type: temper.Type
---@operator call: host-failures-several-libraries.Unreadable
local Unreadable = temper.type("host-failures-several-libraries.Unreadable", HostFailuresError)

---@param this host-failures-several-libraries.Unreadable
---@param key string
function Unreadable.constructor(this, key)
  this.key = key
end

---@param this host-failures-several-libraries.Unreadable
---@return string
function Unreadable.get.message(this)
  return "unreadable (key: " .. this.key .. ")"
end

---@param key string
---@param count string
---@return integer
local function reading(key, count)
  ---@type integer
  local t
  local t_1, failure = temper.catch(function()
    t = temper.int32_add(hostLookup(key), parseCount(count))
  end, temper.Failure)
  if not t_1 then
    error(temper.with_cause(Unreadable(key), failure))
  end
  return t
end

---@param key string
---@param count string
---@return integer
local function ratio(key, count)
  ---@type integer
  local t
  if not temper.catch(function()
    t = temper.int32_div(hostLookup(key), parseCount(count))
  end, temper.Bubble, temper.Failure) then
    t = -9
  end
  return t
end

print("before")
print(total("a", "two") .. " " .. total("z", "two") .. " " .. total("a", "x"))
---@type integer
local r
local t, e = temper.catch(function()
  r = reading("a", "x")
end, Unreadable)
if not t then
  local string_ = e.message .. ", caused by "
  local subject2 = temper.failure_cause(e)
  local subject = temper.is_null(subject2) and temper.null or (subject2 --[[@as temper.Failure]]).message
  print(string_ .. (temper.is_null(subject) and "nothing" or (subject --[[@as string]])))
  r = 0
end
print(r .. " " .. ratio("a", "two") .. " " .. ratio("a", "zero") .. " " .. ratio("a", "x") .. " " .. ratio("z", "two"))
parseCount("x")
print("after")

return {
  HostFailuresError = HostFailuresError,
  NotFound = NotFound,
  hostLookup = hostLookup,
}
