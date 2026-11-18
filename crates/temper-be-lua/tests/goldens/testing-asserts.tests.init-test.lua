local temper = require("temper-core")
local tested = require("asserts")["$test"]
local luaunit = require("luaunit")
luaunit.FAILURE_PREFIX = temper.test_failure_prefix

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

Test_ = {}

function Test_.test_a_test_case()
  temper.test("a test case", function(test)
    temper.test_assert(test, temper.float_eq(tested.normSquared(tested.Vec2(0.0, 0.0)), 0.0), function() return "Oh noes" end)
  end)
end

function Test_.test_a_failing_test()
  temper.test("a failing test", function(test)
    temper.test_assert(test, false, function() return "because it's wrong" end)
    local helper = TestHelper(3, 2)
    assertMoreThings(test, helper, { { "bye" } })
  end)
end

function Test_.test_exported()
  temper.test("exported", function(test)
    local vec = tested.Vec2(1.5, 2.5)
    do
      local actual = vec:toString()
      local expected = "(1.5, 2.5) size squared 8.5"
      temper.test_assert(test, actual == expected, function() return "expected vec.toString() == (" .. expected .. ") not (" .. actual .. ")" end)
    end
  end)
end

function Test_.test_module_values()
  temper.test("module values", function(test)
    do
      local length = temper.list_length(tested.unitVecs)
      local i = 0
      while i < length do
        local vec = temper.list_get(tested.unitVecs, i)
        i = temper.int32_add(i, 1)
        do
          local actual = tested.normSquared(vec)
          local expected = 1.0
          temper.test_assert(test, temper.float_eq(actual, expected), function() return "expected normSquared(vec) == (" .. temper.float64_tostring(expected) .. ") not (" .. temper.float64_tostring(actual) .. ")" end)
        end
      end
    end
  end)
end

luaunit.LuaUnit.run("--pattern", "^Test_%.", ...)
