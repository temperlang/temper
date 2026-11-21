local temper = require("temper-core")

local Counter

local _connected = {}

do
  -- Temper's connected Counter class, with the members Temper would generate:
  -- call the class to construct, and `:` for methods.
  local Counter = {}
  Counter.__index = Counter
  setmetatable(Counter, {
    __call = function(cls)
      return setmetatable({ _total = 0 }, cls)
    end,
  })
  _connected.Counter = Counter

  ---@param i integer
  function Counter:add(i)
    self._total = self._total + i
  end

  ---@return integer
  function Counter:total()
    return self._total
  end
end

---@class work.Counter
---@field count integer
---@field add fun(self: work.Counter, i: integer)
---@field total fun(self: work.Counter): integer

---@class work.Counter.type: temper.Type
---@operator call: work.Counter
Counter = temper.type("work.Counter")

---@param this work.Counter
function Counter.constructor(this)
  this.count = 0
end

---@param this work.Counter
---@param i integer
function Counter.methods.add(this, i)
  this.count = temper.int32_add(this.count, i)
end

---@param this work.Counter
---@return integer
function Counter.methods.total(this)
  return temper.int32_add(this.count, 1000)
end

---@class work.Doubler
---@field twice fun(self: work.Doubler, i: integer): integer

---@class work.Doubler.type: temper.Type
---@operator call: work.Doubler
local Doubler = temper.type("work.Doubler")

---@param this work.Doubler
function Doubler.constructor(this)
end

---@param this work.Doubler
---@param i integer
---@return integer
function Doubler.methods.twice(this, i)
  return temper.int32_add(temper.int32_mul(i, 2), 1000)
end

local counter = Counter()
counter:add(2)
counter:add(3)
temper.log("counter.total(): " .. counter:total())
temper.log("new Doubler().twice(5): " .. Doubler():twice(5))

return { Counter = Counter, Doubler = Doubler }
