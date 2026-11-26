local temper = require("temper-core")

---@param b integer[]
local function f(b)
  if temper.listed_isempty(b) then
    temper.log("empty")
  else
    temper.log("not empty")
  end
end

f({ 2, 3 })
f({})

return {}
