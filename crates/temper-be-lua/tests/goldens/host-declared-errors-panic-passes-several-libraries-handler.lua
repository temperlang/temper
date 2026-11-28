local temper = require("temper-core")
local parseCount = require("count-errors").parseCount

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

---@class host-declared-errors-panic-passes-several-libraries-handler.HostDeclaredErrorsError: temper.Failure

---@class host-declared-errors-panic-passes-several-libraries-handler.HostDeclaredErrorsError.type: temper.Type
local HostDeclaredErrorsError = temper.type("host-declared-errors-panic-passes-several-libraries-handler.HostDeclaredErrorsError", temper.Failure)

---@class host-declared-errors-panic-passes-several-libraries-handler.NotFound: host-declared-errors-panic-passes-several-libraries-handler.HostDeclaredErrorsError
---@field key string
---@field message string

---@class host-declared-errors-panic-passes-several-libraries-handler.NotFound.type: temper.Type
---@operator call: host-declared-errors-panic-passes-several-libraries-handler.NotFound
NotFound = temper.type("host-declared-errors-panic-passes-several-libraries-handler.NotFound", HostDeclaredErrorsError)

---@param this host-declared-errors-panic-passes-several-libraries-handler.NotFound
---@param key string
function NotFound.constructor(this, key)
  this.key = key
end

---@param this host-declared-errors-panic-passes-several-libraries-handler.NotFound
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
local p
if not temper.catch(function()
  local n = parseCount("two")
  local m = hostLookup("a")
  if temper.int32_add(n, m) > 2 then
    error("panic")
  end
  p = temper.int32_div(10, temper.int32_sub(n, m))
end, temper.Failure) then
  p = -1
end
print("after " .. p)

return {
  HostDeclaredErrorsError = HostDeclaredErrorsError,
  NotFound = NotFound,
  hostLookup = hostLookup,
}
