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

---@param lb integer[]
---@param i integer
---@return integer
local function write(lb, i)
  temper.listbuilder_set(lb, at(i), 3)
  return temper.listbuilder_length(lb)
end

print("reading")
---@type integer
local r
if not temper.pcall(function()
  r = write(built(), 5)
end) then
  r = -1
end
print("set past the end " .. r)

return {}
