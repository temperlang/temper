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

---@class host-declared-errors-cause.HostDeclaredErrorsError: temper.Failure

---@class host-declared-errors-cause.HostDeclaredErrorsError.type: temper.Type
local HostDeclaredErrorsError = temper.type("host-declared-errors-cause.HostDeclaredErrorsError", temper.Failure)

---@class host-declared-errors-cause.NotFound: host-declared-errors-cause.HostDeclaredErrorsError
---@field key string
---@field message string

---@class host-declared-errors-cause.NotFound.type: temper.Type
---@operator call: host-declared-errors-cause.NotFound
NotFound = temper.type("host-declared-errors-cause.NotFound", HostDeclaredErrorsError)

---@param this host-declared-errors-cause.NotFound
---@param key string
function NotFound.constructor(this, key)
  this.key = key
end

---@param this host-declared-errors-cause.NotFound
---@return string
function NotFound.get.message(this)
  return "not found (key: " .. this.key .. ")"
end

---@param key string
---@return integer
local function hostLookup(key)
  return _connected.hostLookup(key)
end

---@class host-declared-errors-cause.LookupFailed: host-declared-errors-cause.HostDeclaredErrorsError
---@field name string
---@field message string

---@class host-declared-errors-cause.LookupFailed.type: temper.Type
---@operator call: host-declared-errors-cause.LookupFailed
local LookupFailed = temper.type("host-declared-errors-cause.LookupFailed", HostDeclaredErrorsError)

---@param this host-declared-errors-cause.LookupFailed
---@param name string
function LookupFailed.constructor(this, name)
  this.name = name
end

---@param this host-declared-errors-cause.LookupFailed
---@return string
function LookupFailed.get.message(this)
  return "lookup failed (name: " .. this.name .. ")"
end

---@param name string
---@return integer
local function setting(name)
  ---@type integer
  local t
  local t_1, failure = temper.catch(function()
    t = hostLookup(name)
  end, NotFound)
  if not t_1 then
    error(temper.with_cause(LookupFailed(name), failure))
  end
  return t
end

print("before")
setting("port")
print("after")

return {
  HostDeclaredErrorsError = HostDeclaredErrorsError,
  NotFound = NotFound,
  hostLookup = hostLookup,
}
