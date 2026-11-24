local temper = require("temper-core")

---@param s string
---@param i integer
---@return integer
local function codeAt(s, i)
  return temper.string_get(s, i)
end

local text = "abc"
print(tostring(codeAt(text, 1)))
print("before")
print(tostring(codeAt(text, temper.string_end(text))))
print("not reached")

return {}
