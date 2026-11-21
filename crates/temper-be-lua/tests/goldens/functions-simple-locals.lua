local temper = require("temper-core")

---@param i integer
---@param f fun(arg0: integer): integer
---@return integer
local function callIt(i, f)
  return temper.int32_mul(3, f(i))
end

---@param i integer
local function enclose(i)
  local result = callIt(2, function(j) return temper.int32_add(i, j) end)
  temper.log(tostring(result))
end

enclose(1)

return {}
