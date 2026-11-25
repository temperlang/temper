local temper = require("temper-core")
local int64_1 = temper.int64_constructor(1)
local int64_0 = temper.int64_constructor(0)
local tested = require("test-assert-panics")["$test"]
local luaunit = require("luaunit")
luaunit.FAILURE_PREFIX = temper.test_failure_prefix

---@param ls integer[]
---@param i integer
---@return integer
local function at(ls, i)
  if i == 99 then
    return temper.bubble()
  end
  return temper.list_get(ls, i)
end

---@class test-assert-panics.Shape

---@class test-assert-panics.Shape.type: temper.Type
local Shape = temper.type("test-assert-panics.Shape")

---@class test-assert-panics.Square: test-assert-panics.Shape

---@class test-assert-panics.Square.type: temper.Type
---@operator call: test-assert-panics.Square
local Square = temper.type("test-assert-panics.Square", Shape)

---@param this test-assert-panics.Square
function Square.constructor(this)
end

---@class test-assert-panics.Circle: test-assert-panics.Shape

---@class test-assert-panics.Circle.type: temper.Type
---@operator call: test-assert-panics.Circle
local Circle = temper.type("test-assert-panics.Circle", Shape)

---@param this test-assert-panics.Circle
function Circle.constructor(this)
end

---@param a integer
---@param b integer
---@return integer
local function divide(a, b)
  return temper.int32_div(a, b)
end

---@param a integer
---@param b integer
---@return integer
local function remainder(a, b)
  return temper.int64_mod(a, b)
end

---@param s test-assert-panics.Shape
---@return test-assert-panics.Square
local function squared(s)
  return temper.cast_to(s, Square)
end

Test_ = {}

function Test_.test_passes_before()
  temper.test("passes before", function(test)
    do
      local actual = at({ 1, 2 }, 1)
      local expected = 2
      temper.test_assert(test, actual == expected, function() return "expected at([1, 2], 1) == (" .. expected .. ") not (" .. actual .. ")" end)
    end
  end)
end

function Test_.test_passes_when_the_block_panics()
  temper.test("passes when the block panics", function(test)
    temper.test_assertpanics(test, function() at({ 1, 2 }, 5) end)
    temper.test_assertpanics(test, function() error("panic") end)
  end)
end

function Test_.test_passes_when_division_by_zero_or_a_cast_panics()
  temper.test("passes when division by zero or a cast panics", function(test)
    temper.test_assertpanics(test, function() divide(1, 0) end)
    temper.test_assertpanics(test, function() remainder(int64_1, int64_0) end)
    temper.test_assertpanics(test, function() squared(Circle()) end)
    squared(Square())
    do
      local actual = divide(-7, 2)
      local expected = -3
      temper.test_assert(test, actual == expected, function() return "expected divide(-7, 2) == (" .. expected .. ") not (" .. actual .. ")" end)
    end
  end)
end

function Test_.test_passes_past_an_orelse()
  temper.test("passes past an orelse", function(test)
    local function fn()
      ---@type integer
      local n
      if not temper.pcall(function()
        n = at({ 1, 2 }, 5)
      end) then
        n = -1
      end
      temper.test_assert(test, n == -1, function() return "caught by an orelse" end)
    end
    temper.test_assertpanics(test, fn)
  end)
end

function Test_.test_fails_when_the_block_returns()
  temper.test("fails when the block returns", function(test)
    temper.test_assertpanics(test, function() at({ 1, 2 }, 0) end)
  end)
end

function Test_.test_fails_with_its_message()
  temper.test("fails with its message", function(test)
    temper.test_assertpanics(test, function() at({ 1, 2 }, 1) end, "reading index 1 panics")
  end)
end

function Test_.test_fails_when_the_block_bubbles()
  temper.test("fails when the block bubbles", function(test)
    temper.test_assertpanics(test, function() at({ 1, 2 }, 99) end)
  end)
end

function Test_.test_fails_with_its_message_when_the_block_bubbles()
  temper.test("fails with its message when the block bubbles", function(test)
    temper.test_assertpanics(test, function() at({ 1, 2 }, 99) end, "reading index 99 panics")
  end)
end

function Test_.test_goes_on_after_failing()
  temper.test("goes on after failing", function(test)
    temper.test_assertpanics(test, function() at({ 1, 2 }, 0) end)
    temper.test_assert(test, false, function() return "went on" end)
  end)
end

function Test_.test_nests()
  temper.test("nests", function(test)
    local function fn()
      temper.test_assertpanics(test, function() error("panic") end)
      temper.test_assertpanics(test, function() at({ 1, 2 }, 0) end, "inner returns")
      error("panic")
    end
    temper.test_assertpanics(test, fn)
  end)
end

function Test_.test_keeps_what_the_block_changed()
  temper.test("keeps what the block changed", function(test)
    local n = 0
    local function fn()
      n = temper.int32_add(n, 1)
      error("panic")
    end
    temper.test_assertpanics(test, fn)
    temper.test_assert(test, n == 1, function() return "lost the change" end)
  end)
end

function Test_.test_fails_on_a_panic_after_the_block()
  temper.test("fails on a panic after the block", function(test)
    temper.test_assertpanics(test, function() error("panic") end)
    at({ 1, 2 }, 7)
  end)
end

function Test_.test_ends_on_a_fail_in_the_block()
  temper.test("ends on a fail in the block", function(test)
    local function fn()
      temper.test_fail(test, "failed inside")
      error("panic")
    end
    temper.test_assertpanics(test, fn)
    temper.test_fail(test, "swallowed")
    error("panic")
  end)
end

function Test_.test_passes_after()
  temper.test("passes after", function(test)
    do
      local actual = at({ 1, 2 }, 0)
      local expected = 1
      temper.test_assert(test, actual == expected, function() return "expected at([1, 2], 0) == (" .. expected .. ") not (" .. actual .. ")" end)
    end
  end)
end

luaunit.LuaUnit.run("--pattern", "^Test_%.", ...)
