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

---@class host-failures-host-failure.HostFailuresError: temper.Failure

---@class host-failures-host-failure.HostFailuresError.type: temper.Type
local HostFailuresError = temper.type("host-failures-host-failure.HostFailuresError", temper.Failure)

---@class host-failures-host-failure.NotFound: host-failures-host-failure.HostFailuresError
---@field key string
---@field message string

---@class host-failures-host-failure.NotFound.type: temper.Type
---@operator call: host-failures-host-failure.NotFound
NotFound = temper.type("host-failures-host-failure.NotFound", HostFailuresError)

---@param this host-failures-host-failure.NotFound
---@param key string
function NotFound.constructor(this, key)
  this.key = key
end

---@param this host-failures-host-failure.NotFound
---@return string
function NotFound.get.message(this)
  return "not found (key: " .. this.key .. ")"
end

---@param key string
---@return integer
local function hostLookup(key)
  return _connected.hostLookup(key)
end

print("before")
---@type integer
local n
local t, e = temper.catch(function()
  n = hostLookup("z")
end, NotFound)
if not t then
  print("caught " .. e.key)
  n = -1
end
local string_ = "got " .. n .. " "
---@type integer
local t_1
if not temper.catch(function()
  t_1 = hostLookup("a")
end, NotFound) then
  t_1 = 0
end
print(string_ .. t_1)
hostLookup("y")
print("after")

return {
  HostFailuresError = HostFailuresError,
  NotFound = NotFound,
  hostLookup = hostLookup,
}
