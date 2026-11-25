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

---@class host-failures-panic-passes-catch.HostFailuresError: temper.Failure

---@class host-failures-panic-passes-catch.HostFailuresError.type: temper.Type
local HostFailuresError = temper.type("host-failures-panic-passes-catch.HostFailuresError", temper.Failure)

---@class host-failures-panic-passes-catch.NotFound: host-failures-panic-passes-catch.HostFailuresError
---@field key string
---@field message string

---@class host-failures-panic-passes-catch.NotFound.type: temper.Type
---@operator call: host-failures-panic-passes-catch.NotFound
NotFound = temper.type("host-failures-panic-passes-catch.NotFound", HostFailuresError)

---@param this host-failures-panic-passes-catch.NotFound
---@param key string
function NotFound.constructor(this, key)
  this.key = key
end

---@param this host-failures-panic-passes-catch.NotFound
---@return string
function NotFound.get.message(this)
  return "not found (key: " .. this.key .. ")"
end

---@param key string
---@return integer
local function hostLookup(key)
  return _connected.hostLookup(key)
end

---@param n integer
---@return integer
local function risky(n)
  if n > 1 then
    error("panic")
  end
  if n > 0 then
    error(NotFound("n"))
  end
  return n
end

print("before")
---@type integer
local r
if not temper.catch(function()
  r = risky(2)
end, NotFound) then
  r = -1
end
print("after " .. r)

return {
  HostFailuresError = HostFailuresError,
  NotFound = NotFound,
  hostLookup = hostLookup,
}
