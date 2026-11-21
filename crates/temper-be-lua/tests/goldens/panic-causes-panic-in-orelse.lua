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
---@type integer
local p
local t, t_1 = temper.pcall(function()
  p = positive(-1)
end)
if not t then
  error("panic\ncaused by: " .. tostring(t_1))
end
print("after " .. p)

return {}
