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

print("before")
---@type integer
local h
local t, t_1 = temper.pcall(function()
  h = hostFails(1)
end)
if not t then
  error("panic\ncaused by: " .. tostring(t_1))
end
print("after " .. h)

return { hostFails = hostFails }
