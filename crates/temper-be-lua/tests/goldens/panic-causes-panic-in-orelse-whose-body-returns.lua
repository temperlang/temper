local temper = require("temper-core")

---@param n integer
---@return integer
local function positive(n)
  if n <= 0 then
    return temper.bubble()
  end
  return n
end

---@param n integer
---@return integer
local function checked(n)
  local t, t_1, t_2 = temper.pcall(function()
    if n == 0 then
      return 0, "return"
    end
    positive(n)
  end)
  if not t then
    error("panic\ncaused by: " .. tostring(t_1))
  elseif t_2 == "return" then
    return t_1
  end
  return n
end

print("before")
print("after " .. checked(-1))

return {}
