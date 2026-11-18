local temper = require("temper-core")

---@param i integer
---@return integer
local function at(i)
  if i == 99 then
    return temper.bubble()
  end
  return i
end

---@return integer[]
local function built()
  local lb = temper.listbuilder_constructor()
  temper.listbuilder_add(lb, 1)
  return lb
end

---@param ls integer[]
---@param i integer
---@return integer
local function read(ls, i)
  return temper.listed_get(ls, at(i))
end

print("reading")
---@type integer
local r
if not temper.pcall(function()
  r = read(built(), -1)
end) then
  r = -1
end
print("read before the start " .. r)

return {}
