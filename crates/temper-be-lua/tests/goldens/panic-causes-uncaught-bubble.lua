local temper = require("temper-core")

---@param n integer
---@return integer
local function positive(n)
  if n <= 0 then
    return temper.bubble()
  end
  return n
end

print("before")
positive(-1)
print("after")

return {}
