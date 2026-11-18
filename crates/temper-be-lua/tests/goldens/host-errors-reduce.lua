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
local function sum(ls, i)
  at(i)
  return temper.listed_reduce(ls, function(a, b) return temper.int32_add(a, b) end)
end

print("reading")
---@type integer
local r
if not temper.pcall(function()
  r = sum({}, 0)
end) then
  r = -1
end
print("reduced empty " .. r)

return {}
