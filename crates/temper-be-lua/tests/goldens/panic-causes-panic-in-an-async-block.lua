local temper = require("temper-core")

---@param ls integer[]
---@return integer
local function third(ls)
  return temper.list_get(ls, 2)
end

print("before")

local function fn()
  print("in async")
  third({ 1, 2 })
end

temper.async(temper.adapt_generator_fn(fn))

return {}
