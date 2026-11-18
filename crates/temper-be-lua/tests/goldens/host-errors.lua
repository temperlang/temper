local temper = require("temper-core")

---@param ls integer[]
---@param i integer
---@return integer
local function read(ls, i)
  if i < 0 then
    return temper.bubble()
  end
  return temper.list_get(ls, i)
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
error("panic")

return {}
