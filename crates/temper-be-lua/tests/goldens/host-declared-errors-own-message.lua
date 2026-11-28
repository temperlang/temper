local temper = require("temper-core")

local NotFound

-- `NotFound` is the library's own error type, a local of its module,
-- which this is inlined in; calling it makes one, and raising that is a
-- declared error, and a string a host error.
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

---@class host-declared-errors-own-message.HostDeclaredErrorsError: temper.Failure

---@class host-declared-errors-own-message.HostDeclaredErrorsError.type: temper.Type
local HostDeclaredErrorsError = temper.type("host-declared-errors-own-message.HostDeclaredErrorsError", temper.Failure)

---@class host-declared-errors-own-message.NotFound: host-declared-errors-own-message.HostDeclaredErrorsError
---@field key string
---@field message string

---@class host-declared-errors-own-message.NotFound.type: temper.Type
---@operator call: host-declared-errors-own-message.NotFound
NotFound = temper.type("host-declared-errors-own-message.NotFound", HostDeclaredErrorsError)

---@param this host-declared-errors-own-message.NotFound
---@param key string
function NotFound.constructor(this, key)
  this.key = key
end

---@param this host-declared-errors-own-message.NotFound
---@return string
function NotFound.get.message(this)
  return "not found (key: " .. this.key .. ")"
end

---@param key string
---@return integer
local function hostLookup(key)
  return _connected.hostLookup(key)
end

---@class host-declared-errors-own-message.Expired: host-declared-errors-own-message.HostDeclaredErrorsError
---@field days integer
---@field message string

---@class host-declared-errors-own-message.Expired.type: temper.Type
---@operator call: host-declared-errors-own-message.Expired
local Expired = temper.type("host-declared-errors-own-message.Expired", HostDeclaredErrorsError)

---@param this host-declared-errors-own-message.Expired
---@param days integer
function Expired.constructor(this, days)
  this.days = days
end

---@param this host-declared-errors-own-message.Expired
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
  HostDeclaredErrorsError = HostDeclaredErrorsError,
  NotFound = NotFound,
  hostLookup = hostLookup,
}
