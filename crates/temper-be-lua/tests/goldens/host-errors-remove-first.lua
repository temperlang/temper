local temper = require("temper-core")

---@param i integer
---@return integer
local function at(i)
  if i == 99 then
    return temper.bubble()
  end
  return i
end

---@param d temper.Deque
---@param i integer
---@return integer
local function removeFirst(d, i)
  at(i)
  return temper.deque_removefirst(d)
end

print("reading")
---@type integer
local r
if not temper.pcall(function()
  r = removeFirst(temper.deque_constructor(), 0)
end) then
  r = -1
end
print("removed from empty " .. r)

return {}
