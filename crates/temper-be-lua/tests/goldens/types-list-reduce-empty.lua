local temper = require("temper-core")

---@param values integer[]
---@return integer
local function sum(values)
  return temper.listed_reduce(values, function(a, b) return temper.int32_add(a, b) end)
end

print(tostring(sum({ 1, 2, 3 })))
print("before")
print(tostring(sum({})))
print("not reached")

return {}
