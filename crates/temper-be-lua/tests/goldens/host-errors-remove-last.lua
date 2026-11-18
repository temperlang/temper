local temper = require("temper-core")

---@param i integer
---@return integer
local function at(i)
  if i == 99 then
    return temper.bubble()
  end
  return i
end

---@param lb integer[]
---@param i integer
---@return integer
local function removeLast(lb, i)
  at(i)
  return temper.listbuilder_removelast(lb)
end

print("reading")
---@type integer
local r
if not temper.pcall(function()
  r = removeLast(temper.listbuilder_constructor(), 0)
end) then
  r = -1
end
print("removed from empty " .. r)

return {}
