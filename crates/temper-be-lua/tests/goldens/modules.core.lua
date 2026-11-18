local temper = require("temper-core")

---@param n integer
---@param d integer
---@return integer
local function share(n, d)
  ---@type integer
  local t
  if not temper.pcall(function()
    t = temper.int32_div(n, d)
  end) then
    t = 0
  end
  return t
end

---@class modules.core.Ratio
---@field n integer
---@field d integer
---@field eq fun(self: modules.core.Ratio, other: modules.core.Ratio): boolean

---@class modules.core.Ratio.type: temper.Type
---@operator call: modules.core.Ratio
local Ratio = temper.type("modules.core.Ratio")

---@param this modules.core.Ratio
---@param n integer
---@param d integer
function Ratio.constructor(this, n, d)
  this.n = n
  this.d = d
end

---@param this modules.core.Ratio
---@param other modules.core.Ratio
---@return boolean
function Ratio.methods.eq(this, other)
  return temper.int32_mul(this.n, other.d) == temper.int32_mul(other.n, this.d)
end

---@param n integer
---@param d integer
---@return modules.core.Ratio|temper.Null
local function ratioOf(n, d)
  if d == 0 then
    return temper.null
  else
    return Ratio(n, d)
  end
end

return { share = share, Ratio = Ratio, ratioOf = ratioOf }
