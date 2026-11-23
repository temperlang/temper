local temper = require("temper-core")
local tested = require("host-error-tests")["$test"]
local luaunit = require("luaunit")
luaunit.FAILURE_PREFIX = temper.test_failure_prefix

Test_ = {}

function Test_.test_host_error_is_no_panic()
  temper.test("host error is no panic", function(test)
    local function fn()
      ---@type integer
      local r
      if not temper.pcall(function()
        r = tested.hostFails(1)
      end) then
        r = -1
      end
      temper.test_assert(test, r ~= -1, function() return "caught" end)
    end
    temper.test_assertpanics(test, fn)
  end)
end

luaunit.LuaUnit.run("--pattern", "^Test_%.", ...)
