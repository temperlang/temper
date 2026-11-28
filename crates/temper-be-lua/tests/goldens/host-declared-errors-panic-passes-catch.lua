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

---@class host-declared-errors-panic-passes-catch.HostDeclaredErrorsError: temper.Failure

---@class host-declared-errors-panic-passes-catch.HostDeclaredErrorsError.type: temper.Type
local HostDeclaredErrorsError = temper.type("host-declared-errors-panic-passes-catch.HostDeclaredErrorsError", temper.Failure)

---@class host-declared-errors-panic-passes-catch.NotFound: host-declared-errors-panic-passes-catch.HostDeclaredErrorsError
---@field key string
---@field message string

---@class host-declared-errors-panic-passes-catch.NotFound.type: temper.Type
---@operator call: host-declared-errors-panic-passes-catch.NotFound
NotFound = temper.type("host-declared-errors-panic-passes-catch.NotFound", HostDeclaredErrorsError)

---@param this host-declared-errors-panic-passes-catch.NotFound
---@param key string
function NotFound.constructor(this, key)
  this.key = key
end

---@param this host-declared-errors-panic-passes-catch.NotFound
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
  HostDeclaredErrorsError = HostDeclaredErrorsError,
  NotFound = NotFound,
  hostLookup = hostLookup,
}
