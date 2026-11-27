local temper = require("temper-core")

local _connected = {}

do
  ---@param n integer
  ---@return integer
  function _connected.hostFails(n)
    if n == 1 then
      error("host bug " .. n)
    elseif n == 2 then
      error({ bug = n })
    end
    local missing = nil
    return missing.n
  end

  -- LuaUnit's own failure, as its assertions raise.
  function _connected.hostFailsTest()
    require("luaunit").fail("host test failure")
  end
end

---@class host-error-tests.OopsError: temper.Failure

---@class host-error-tests.OopsError.type: temper.Type
local OopsError = temper.type("host-error-tests.OopsError", temper.Failure)

---@class host-error-tests.Oops: host-error-tests.OopsError
---@field message string

---@class host-error-tests.Oops.type: temper.Type
---@operator call: host-error-tests.Oops
local Oops = temper.type("host-error-tests.Oops", OopsError)

---@param this host-error-tests.Oops
function Oops.constructor(this)
end

---@param this host-error-tests.Oops
---@return string
function Oops.get.message(this)
  return "oops"
end

---@param n integer
---@return integer
local function hostFails(n)
  return _connected.hostFails(n)
end

local function hostFailsTest()
  _connected.hostFailsTest()
end

return {
  OopsError = OopsError,
  Oops = Oops,
  hostFails = hostFails,
  hostFailsTest = hostFailsTest,
  ["$test"] = { hostFails = hostFails, hostFailsTest = hostFailsTest },
}
