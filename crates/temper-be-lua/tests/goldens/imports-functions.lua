local temper = require("temper-core")
local normSquared = require("functions/exporter").normSquared

---@param x number
---@param y number
---@return number
local function norm(x, y)
  return math.sqrt(normSquared(x, y))
end

---@param vals number[]
---@return number
local function something(vals)
  return temper.listed_reducefrom(vals, 0.0, normSquared)
end

local a = 3.0
local b = a + 1.0
local values = temper.listed_join({ a, b, norm(a, b) }, ", ", function(it) return temper.float64_tostring(it) end)
temper.log(values .. " triangle")
temper.log(temper.float64_tostring(something({ a, b })))

return { norm = norm, something = something }
