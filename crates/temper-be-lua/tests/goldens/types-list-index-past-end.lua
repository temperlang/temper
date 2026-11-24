local temper = require("temper-core")

---@param listed string[]
---@param i integer
---@return string
local function at(listed, i)
  return temper.listed_get(listed, i)
end

local letters = { "a", "b", "c" }
print(at(letters, 2))
print("before")
print(at(letters, 3))
print("not reached")

return {}
