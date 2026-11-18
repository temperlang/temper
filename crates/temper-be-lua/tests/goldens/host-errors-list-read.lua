local temper = require("temper-core")

---@param i integer
---@return integer
local function at(i)
  if i == 99 then
    return temper.bubble()
  end
  return i
end

---@param ls integer[]
---@param i integer
---@return integer
local function read(ls, i)
  return temper.list_get(ls, at(i))
end

print("reading")
---@type integer
local r
if not temper.pcall(function()
  r = read({ 1, 2 }, 5)
end) then
  r = -1
end
print("read past the end " .. r)

return {}
