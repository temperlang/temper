local temper = require("temper-core")

---@param n integer
---@return integer
local function twice(n)
  return temper.int32_mul(n, 2)
end

print("init " .. twice(1))

return { twice = twice, ["$test"] = { twice = twice } }
