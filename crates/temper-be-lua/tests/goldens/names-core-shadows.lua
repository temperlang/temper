local temper = require("temper-core")
local summary = require("core-shadows/mine").summary

---@param console integer
---@return integer
local function bump(console)
  return temper.int32_add(console, 1)
end

print(tostring(summary()))
local s = "core's string"
print(s)
local xs = { 1, 2 }
print(tostring(temper.list_length(xs)))
print(tostring(bump(5)))

return {}
