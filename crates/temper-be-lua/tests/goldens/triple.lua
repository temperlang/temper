local temper = require("temper-core")

---@param n integer
---@return integer
local function triple(n)
  return temper.int32_mul(n, 3)
end

temper.log("triple(14) = " .. triple(14))

return {}
