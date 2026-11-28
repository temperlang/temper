local temper = require("temper-core")
local lookup = require("error-imports/a").lookup
local NotFound = require("error-imports/a").NotFound

---@type integer
local n
if not temper.catch(function()
  n = lookup("x")
end, NotFound) then
  n = -1
end
print(tostring(n))

return {}
