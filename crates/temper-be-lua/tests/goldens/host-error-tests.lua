local temper = require("temper-core")

local _connected = {}

do
  ---@param n integer
  ---@return integer
  function _connected.hostFails(n)
    error("host bug " .. n)
  end
end

---@param n integer
---@return integer
local function hostFails(n)
  return _connected.hostFails(n)
end

return { hostFails = hostFails, ["$test"] = { hostFails = hostFails } }
