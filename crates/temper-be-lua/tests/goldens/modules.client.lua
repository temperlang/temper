local temper = require("temper-core")
local sum = require("modules").sum

---@param n integer
---@return integer
local function sumTwice(n)
  return temper.int32_mul(sum(n), 2)
end

return { sumTwice = sumTwice }
