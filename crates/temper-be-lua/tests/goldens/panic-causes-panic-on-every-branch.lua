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
local k
if not temper.pcall(function()
  k = positive(1)
end) then
  k = 0
end
if k > 0 then
  error("panic")
else
  local t, t_1 = temper.pcall(function()
    positive(-1)
  end)
  if not t then
    error("panic\ncaused by: " .. tostring(t_1))
  end
  error("panic")
end
print("after")

return {}
