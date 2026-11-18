local temper = require("temper-core")

local normSquared

---Gives a class's instances a `__tostring` that calls their `toString`.
---@param class temper.Type
local function with_tostring(class)
  local construct = class.constructor
  ---@type table?
  local meta = nil
  class.constructor = function(this, ...)
    if meta == nil then
      meta = {}
      for k, v in _G.pairs(_G.getmetatable(this)) do
        meta[k] = v
      end
      meta.__tostring = function(self)
        return self:toString()
      end
    end
    _G.setmetatable(this, meta)
    construct(this, ...)
  end
end

---@class asserts.Vec2
---@field x number
---@field y number
---@field toString fun(self: asserts.Vec2): string

---@class asserts.Vec2.type: temper.Type
---@operator call: asserts.Vec2
local Vec2 = temper.type("asserts.Vec2")

---@param this asserts.Vec2
---@param x number
---@param y number
function Vec2.constructor(this, x, y)
  this.x = x
  this.y = y
end

---@param this asserts.Vec2
---@return string
function Vec2.methods.toString(this)
  return "(" .. temper.float64_tostring(this.x) .. ", " .. temper.float64_tostring(this.y) .. ") size squared " .. temper.float64_tostring(normSquared(this))
end

with_tostring(Vec2)

---@param vec asserts.Vec2
---@return number
function normSquared(vec)
  return vec.x * vec.x + vec.y * vec.y
end

---@class asserts.KeptForHost
---@field n integer

---@class asserts.KeptForHost.type: temper.Type
---@operator call: asserts.KeptForHost
local KeptForHost = temper.type("asserts.KeptForHost")

---@param this asserts.KeptForHost
---@param n integer
function KeptForHost.constructor(this, n)
  this.n = n
end

local unitVecs = { Vec2(1.0, 0.0), Vec2(0.0, 1.0) }

return {
  Vec2 = Vec2,
  ["$test"] = { Vec2 = Vec2, normSquared = normSquared, unitVecs = unitVecs },
}
