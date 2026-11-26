local temper = require("temper-core")

---@param n integer
---@return integer
local function caught(n)
  return temper.int32_add(n, 1)
end

---@param n integer
---@return integer
local function orElse(n)
  return temper.int32_add(n, 2)
end

print(tostring(caught(1)))
print(tostring(orElse(1)))
print("caught")
print("or else")

return {}
