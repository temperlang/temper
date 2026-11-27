local temper = require("temper-core")
local tested = require("test-fails")["$test"]
local luaunit = require("luaunit")
luaunit.FAILURE_PREFIX = temper.test_failure_prefix

---@class test-fails.Problem: temper.Failure

---@class test-fails.Problem.type: temper.Type
local Problem = temper.type("test-fails.Problem", temper.Failure)

---@class test-fails.Oops: test-fails.Problem
---@field message string

---@class test-fails.Oops.type: temper.Type
---@operator call: test-fails.Oops
local Oops = temper.type("test-fails.Oops", Problem)

---@param this test-fails.Oops
function Oops.constructor(this)
end

---@param this test-fails.Oops
---@return string
function Oops.get.message(this)
  return "oops"
end

---@param f fun()
---@return boolean
local function tries(f)
  local finished = true
  if not temper.catch(function()
    f()
  end, Oops) then
    finished = false
  end
  return finished
end

---@param key string
---@return integer|temper.Null
local function find(key)
  if key == "one" then
    return 1
  else
    return temper.null
  end
end

Test_ = {}

function Test_.test_passes_before()
  temper.test("passes before", function(test)
    local function fn()
      do
        local actual = find("one")
        local expected = 1
        temper.test_assert(test, actual == expected, function() return 'expected find("one") == (' .. expected .. ") not (" .. (temper.is_null(actual) and "null" or tostring(actual --[[@as integer]])) .. ")" end)
      end
    end
    temper.test_assert(test, tries(fn), function() return 'expected tries { assert(find("one") == 1); }' end)
  end)
end

function Test_.test_fails_past_an_orelse()
  temper.test("fails past an orelse", function(test)
    local function fn()
      temper.test_fail(test, "failed on purpose")
      error("panic")
    end
    tries(fn)
    temper.test_fail(test, "swallowed")
    error("panic")
  end)
end

function Test_.test_narrows_after_fail()
  temper.test("narrows after fail", function(test)
    local n = find("one")
    if temper.is_null(n) then
      temper.test_fail(test, "one not found")
      error("panic")
    end
    do
      local actual = temper.int32_add(n --[[@as integer]], 1)
      local expected = 2
      temper.test_assert(test, actual == expected, function() return "expected n + 1 == (" .. expected .. ") not (" .. actual .. ")" end)
    end
  end)
end

function Test_.test_fails_before_narrowing()
  temper.test("fails before narrowing", function(test)
    local n = find("two")
    if temper.is_null(n) then
      temper.test_fail(test, "two not found")
      error("panic")
    end
    temper.test_assert(test, temper.int32_add(n --[[@as integer]], 1) == 3, function() return "went on" end)
  end)
end

function Test_.test_collects_soft_failures()
  temper.test("collects soft failures", function(test)
    temper.test_assert(test, temper.int32_add(1, 1) == 3, function() return "first" end)
    temper.test_assert(test, true, function() return "not shown" end)
    temper.test_assert(test, temper.int32_add(2, 2) == 5, function() return "second" end)
  end)
end

function Test_.test_fails_hard_after_soft_failures()
  temper.test("fails hard after soft failures", function(test)
    temper.test_assert(test, false, function() return "soft before" end)
    temper.test_fail(test, "then hard")
    error("panic")
  end)
end

function Test_.test_fails_a_hard_assertion_past_an_orelse()
  temper.test("fails a hard assertion past an orelse", function(test)
    tries(function() temper.test_asserthard(test, temper.int32_add(1, 1) == 3, function() return "hard assertion" end) end)
    temper.test_fail(test, "swallowed")
    error("panic")
  end)
end

function Test_.test_hardens_soft_failures_past_an_orelse()
  temper.test("hardens soft failures past an orelse", function(test)
    temper.test_assert(test, false, function() return "soft only" end)
    tries(function() temper.test_softfailtohard(test) end)
    temper.test_fail(test, "swallowed")
    error("panic")
  end)
end

function Test_.test_passes_after()
  temper.test("passes after", function(test)
    do
      local actual = find("one")
      local expected = 1
      temper.test_assert(test, actual == expected, function() return 'expected find("one") == (' .. expected .. ") not (" .. (temper.is_null(actual) and "null" or tostring(actual --[[@as integer]])) .. ")" end)
    end
  end)
end

luaunit.LuaUnit.run("--pattern", "^Test_%.", ...)
