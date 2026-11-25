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

---@class host-failures-panic-passes-several-libraries-handler.HostFailuresError: temper.Failure

---@class host-failures-panic-passes-several-libraries-handler.HostFailuresError.type: temper.Type
local HostFailuresError = temper.type("host-failures-panic-passes-several-libraries-handler.HostFailuresError", temper.Failure)

---@class host-failures-panic-passes-several-libraries-handler.NotFound: host-failures-panic-passes-several-libraries-handler.HostFailuresError
---@field key string
---@field message string

---@class host-failures-panic-passes-several-libraries-handler.NotFound.type: temper.Type
---@operator call: host-failures-panic-passes-several-libraries-handler.NotFound
NotFound = temper.type("host-failures-panic-passes-several-libraries-handler.NotFound", HostFailuresError)

---@param this host-failures-panic-passes-several-libraries-handler.NotFound
---@param key string
function NotFound.constructor(this, key)
  this.key = key
end

---@param this host-failures-panic-passes-several-libraries-handler.NotFound
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
end, temper.Bubble, temper.Failure) then
  p = -1
end
print("after " .. p)

return {
  HostFailuresError = HostFailuresError,
  NotFound = NotFound,
  hostLookup = hostLookup,
}
