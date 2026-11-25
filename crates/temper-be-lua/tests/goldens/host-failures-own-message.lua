local temper = require("temper-core")

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

---@class host-failures-own-message.HostFailuresError: temper.Failure

---@class host-failures-own-message.HostFailuresError.type: temper.Type
local HostFailuresError = temper.type("host-failures-own-message.HostFailuresError", temper.Failure)

---@class host-failures-own-message.NotFound: host-failures-own-message.HostFailuresError
---@field key string
---@field message string

---@class host-failures-own-message.NotFound.type: temper.Type
---@operator call: host-failures-own-message.NotFound
NotFound = temper.type("host-failures-own-message.NotFound", HostFailuresError)

---@param this host-failures-own-message.NotFound
---@param key string
function NotFound.constructor(this, key)
  this.key = key
end

---@param this host-failures-own-message.NotFound
---@return string
function NotFound.get.message(this)
  return "not found (key: " .. this.key .. ")"
end

---@param key string
---@return integer
local function hostLookup(key)
  return _connected.hostLookup(key)
end

---@class host-failures-own-message.Expired: host-failures-own-message.HostFailuresError
---@field days integer
---@field message string

---@class host-failures-own-message.Expired.type: temper.Type
---@operator call: host-failures-own-message.Expired
local Expired = temper.type("host-failures-own-message.Expired", HostFailuresError)

---@param this host-failures-own-message.Expired
---@param days integer
function Expired.constructor(this, days)
  this.days = days
end

---@param this host-failures-own-message.Expired
---@return string
function Expired.get.message(this)
  return "expired " .. this.days .. " days ago"
end

---@param days integer
local function check(days)
  if days > 0 then
    error(Expired(days))
  end
end

print("before")
check(0)
check(3)
print("after")

return {
  HostFailuresError = HostFailuresError,
  NotFound = NotFound,
  hostLookup = hostLookup,
}
