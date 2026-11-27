local temper = require("temper-core")

local _connected = {}

do
  ---@param n integer
  ---@return integer
  function _connected.hostFails(n)
    error("host bug " .. n)
  end
end

---@class host-error-causes.OopsError: temper.Failure

---@class host-error-causes.OopsError.type: temper.Type
local OopsError = temper.type("host-error-causes.OopsError", temper.Failure)

---@class host-error-causes.Oops: host-error-causes.OopsError
---@field message string

---@class host-error-causes.Oops.type: temper.Type
---@operator call: host-error-causes.Oops
local Oops = temper.type("host-error-causes.Oops", OopsError)

---@param this host-error-causes.Oops
function Oops.constructor(this)
end

---@param this host-error-causes.Oops
---@return string
function Oops.get.message(this)
  return "oops"
end

---@param n integer
---@return integer
local function hostFails(n)
  return _connected.hostFails(n)
end

print("before")
---@type integer
local h
local t, t_1 = temper.catch(function()
  h = hostFails(1)
end, Oops)
if not t then
  error("panic\ncaused by: " .. tostring(t_1))
end
print("after " .. h)

return { OopsError = OopsError, Oops = Oops, hostFails = hostFails }
