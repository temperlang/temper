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

---@class host-failures-cause.HostFailuresError: temper.Failure

---@class host-failures-cause.HostFailuresError.type: temper.Type
local HostFailuresError = temper.type("host-failures-cause.HostFailuresError", temper.Failure)

---@class host-failures-cause.NotFound: host-failures-cause.HostFailuresError
---@field key string
---@field message string

---@class host-failures-cause.NotFound.type: temper.Type
---@operator call: host-failures-cause.NotFound
NotFound = temper.type("host-failures-cause.NotFound", HostFailuresError)

---@param this host-failures-cause.NotFound
---@param key string
function NotFound.constructor(this, key)
  this.key = key
end

---@param this host-failures-cause.NotFound
---@return string
function NotFound.get.message(this)
  return "not found (key: " .. this.key .. ")"
end

---@param key string
---@return integer
local function hostLookup(key)
  return _connected.hostLookup(key)
end

---@class host-failures-cause.LookupFailed: host-failures-cause.HostFailuresError
---@field name string
---@field message string

---@class host-failures-cause.LookupFailed.type: temper.Type
---@operator call: host-failures-cause.LookupFailed
local LookupFailed = temper.type("host-failures-cause.LookupFailed", HostFailuresError)

---@param this host-failures-cause.LookupFailed
---@param name string
function LookupFailed.constructor(this, name)
  this.name = name
end

---@param this host-failures-cause.LookupFailed
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
  HostFailuresError = HostFailuresError,
  NotFound = NotFound,
  hostLookup = hostLookup,
}
