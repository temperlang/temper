local temper = require("temper-core")

---@param builder integer[]
---@param i integer
---@param value integer
local function put(builder, i, value)
  temper.listbuilder_set(builder, i, value)
end

local builder = temper.listbuilder_constructor()
temper.listbuilder_add(builder, 1)
temper.listbuilder_add(builder, 2)
put(builder, 1, 5)
print(temper.listed_join(builder, ", ", function(n) return tostring(n) end))
print("before")
put(builder, -1, 0)
print("not reached")

return {}
