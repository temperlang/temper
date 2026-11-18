local temper = require("temper-core")

---@param i integer
---@return integer
local function at(i)
  if i == 99 then
    return temper.bubble()
  end
  return i
end

---@param s string
---@param i integer
---@return integer
local function codeAt(s, i)
  at(i)
  return temper.string_get(s, temper.string_end(s))
end

print("reading")
---@type integer
local r
if not temper.pcall(function()
  r = codeAt("ab", 0)
end) then
  r = -1
end
print("read the end " .. r)

return {}
