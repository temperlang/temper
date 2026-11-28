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

---@class host-declared-errors-host-error.HostDeclaredErrorsError: temper.Failure

---@class host-declared-errors-host-error.HostDeclaredErrorsError.type: temper.Type
local HostDeclaredErrorsError = temper.type("host-declared-errors-host-error.HostDeclaredErrorsError", temper.Failure)

---@class host-declared-errors-host-error.NotFound: host-declared-errors-host-error.HostDeclaredErrorsError
---@field key string
---@field message string

---@class host-declared-errors-host-error.NotFound.type: temper.Type
---@operator call: host-declared-errors-host-error.NotFound
NotFound = temper.type("host-declared-errors-host-error.NotFound", HostDeclaredErrorsError)

---@param this host-declared-errors-host-error.NotFound
---@param key string
function NotFound.constructor(this, key)
  this.key = key
end

---@param this host-declared-errors-host-error.NotFound
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
  HostDeclaredErrorsError = HostDeclaredErrorsError,
  NotFound = NotFound,
  hostLookup = hostLookup,
}
