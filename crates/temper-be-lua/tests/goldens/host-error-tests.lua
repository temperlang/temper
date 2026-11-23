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

---@param n integer
---@return integer
local function hostFails(n)
  return _connected.hostFails(n)
end

local function hostFailsTest()
  _connected.hostFailsTest()
end

return {
  hostFails = hostFails,
  hostFailsTest = hostFailsTest,
  ["$test"] = { hostFails = hostFails, hostFailsTest = hostFailsTest },
}
