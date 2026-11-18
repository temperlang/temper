local temper = require("temper-core")
local tested = require("reach")["$test"]
local luaunit = require("luaunit")
luaunit.FAILURE_PREFIX = temper.test_failure_prefix

---@type integer
local testFactor
---@type integer
local testCount

---@class reach.TestShape
---@field area fun(self: reach.TestShape): integer

---@class reach.TestShape.type: temper.Type
local TestShape = temper.type("reach.TestShape")

---@class reach.TestSquare: reach.TestShape
---@field side integer
---@field area fun(self: reach.TestSquare): integer

---@class reach.TestSquare.type: temper.Type
---@operator call: reach.TestSquare
local TestSquare = temper.type("reach.TestSquare", TestShape)

---@param this reach.TestSquare
---@param side integer
function TestSquare.constructor(this, side)
  this.side = side
end

---@param this reach.TestSquare
---@return integer
function TestSquare.methods.area(this)
  return temper.int32_mul(this.side, this.side)
end

---@param n integer
---@return integer
local function forTests(n)
  return temper.int32_mul(n, testFactor)
end

---@param shape reach.TestShape
local function addArea(shape)
  testCount = temper.int32_add(testCount, shape:area())
end

testFactor = temper.int32_mul(tested.base, 2)
testCount = 0

Test_ = {}

function Test_.test_test_parts()
  temper.test("test parts", function(test)
    local shape = TestSquare(forTests(2))
    addArea(shape)
    testCount = temper.int32_add(testCount, shape:area())
    do
      local actual = testCount
      local expected = 32
      temper.test_assert(test, actual == expected, function() return "expected testCount == (" .. expected .. ") not (" .. actual .. ")" end)
    end
    do
      local actual = tested.checked
      local expected = 3
      temper.test_assert(test, actual == expected, function() return "expected checked == (" .. expected .. ") not (" .. actual .. ")" end)
    end
    do
      local actual = tested.shownCount__get()
      local expected = 2
      temper.test_assert(test, actual == expected, function() return "expected shownCount == (" .. expected .. ") not (" .. actual .. ")" end)
    end
    tested.shownCount__set(0)
    tested.shown(0)
    do
      local actual = tested.shownCount__get()
      local expected = 1
      temper.test_assert(test, actual == expected, function() return "expected shownCount == (" .. expected .. ") not (" .. actual .. ")" end)
    end
  end)
end

luaunit.LuaUnit.run("--pattern", "^Test_%.", ...)
