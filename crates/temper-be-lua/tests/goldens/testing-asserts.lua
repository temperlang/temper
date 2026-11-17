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

---@class asserts.TestHelper
---@field i integer
---@field j integer

---@class asserts.TestHelper.type: temper.Type
---@operator call: asserts.TestHelper
local TestHelper = temper.type("asserts.TestHelper")

---@param this asserts.TestHelper
---@param i integer
---@param j integer
function TestHelper.constructor(this, i, j)
  this.i = i
  this.j = j
end

---@param t table
---@param helper asserts.TestHelper
---@param strings string[][]
local function assertMoreThings(t, helper, strings)
  do
    local i = 0
    while i < temper.list_length(strings) do
      do
        local j = 0
        while j < temper.list_length(temper.list_get(strings, i)) do
          do
            local actual = helper.i
            local expected = helper.j
            temper.test_assert(t, actual == expected, function() return "expected helper.i == (" .. expected .. ") not (" .. actual .. ")" end)
          end
          local s = temper.list_get(temper.list_get(strings, i), j)
          do
            local actual = s
            local expected = "hi"
            temper.test_assert(t, actual == expected, function() return "expected s == (" .. expected .. ") not (" .. actual .. ")" end)
          end
          j = temper.int32_add(j, 1)
        end
      end
      i = temper.int32_add(i, 1)
    end
  end
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

---@param helper asserts.TestHelper
---@return integer
local function sumOf(helper)
  return temper.int32_add(helper.i, helper.j)
end

---@param helper asserts.TestHelper
---@return integer
local function keptForTests(helper)
  return sumOf(helper)
end

local name = "case"
local unitVecs = { Vec2(1.0, 0.0), Vec2(0.0, 1.0) }

return {
  Vec2 = Vec2,
  ["$test"] = { Vec2 = Vec2, TestHelper = TestHelper, normSquared = normSquared, assertMoreThings = assertMoreThings, unitVecs = unitVecs },
}
