local temper = require("temper-core")
local tested = require("host-error-tests")["$test"]
local luaunit = require("luaunit")
luaunit.FAILURE_PREFIX = temper.test_failure_prefix

Test_ = {}

function Test_.test_host_errors_are_panics()
  temper.test("host errors are panics", function(test)
    local function fn()
      ---@type integer
      local r
      if not temper.pcall(function()
        r = tested.hostFails(1)
      end) then
        r = -1
      end
      temper.test_assert(test, r ~= -1, function() return "expected r != -1" end)
    end
    temper.test_assertpanics(test, fn, "host error 1")
    local function fn3()
      ---@type integer
      local r
      if not temper.pcall(function()
        r = tested.hostFails(2)
      end) then
        r = -1
      end
      temper.test_assert(test, r ~= -1, function() return "expected r != -1" end)
    end
    temper.test_assertpanics(test, fn3, "host error 2")
    local function fn4()
      ---@type integer
      local r
      if not temper.pcall(function()
        r = tested.hostFails(3)
      end) then
        r = -1
      end
      temper.test_assert(test, r ~= -1, function() return "expected r != -1" end)
    end
    temper.test_assertpanics(test, fn4, "host error 3")
  end)
end

function Test_.test_host_test_failure_is_no_panic()
  temper.test("host test failure is no panic", function(test)
    temper.test_assertpanics(test, function() tested.hostFailsTest() end)
  end)
end

luaunit.LuaUnit.run("--pattern", "^Test_%.", ...)
