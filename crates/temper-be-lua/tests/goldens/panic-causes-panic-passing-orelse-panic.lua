local temper = require("temper-core")

---@param n integer
---@return integer
local function positive(n)
  if n <= 0 then
    return temper.bubble()
  end
  return n
end

---@param ls integer[]
---@param i integer
---@return integer
local function read(ls, i)
  positive(temper.int32_add(i, 1))
  return temper.list_get(ls, i)
end

print("before")
---@type integer
local r
local t, t_1 = temper.pcall(function()
  r = read({ 1, 2 }, 5)
end)
if not t then
  error("panic\ncaused by: " .. tostring(t_1))
end
print("after " .. r)

return {}
