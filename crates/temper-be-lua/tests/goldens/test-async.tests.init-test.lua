local temper = require("temper-core")
local tested = require("test-async")["$test"]
local luaunit = require("luaunit")
luaunit.FAILURE_PREFIX = temper.test_failure_prefix

Test_ = {}

function Test_.test_fails_in_an_async_block()
  temper.test("fails in an async block", function(test)
    local function fn()
      temper.test_assert(test, temper.int32_add(1, 1) == 3, function() return "failed in async" end)
    end
    temper.async(temper.adapt_generator_fn(fn))
  end)
end

function Test_.test_passes_in_an_async_block()
  temper.test("passes in an async block", function(test)
    local function fn()
      temper.test_assert(test, temper.int32_add(1, 1) == 2, function() return "not shown" end)
    end
    temper.async(temper.adapt_generator_fn(fn))
  end)
end

function Test_.test_fails_in_a_later_turn()
  temper.test("fails in a later turn", function(test)
    local pb = temper.promisebuilder_constructor()
    local function fn()
      ---@type integer
      local n
      if not temper.pcall(function()
        n = temper.promisebuilder_promise(pb):await()
      end) then
        n = -1
      end
      temper.test_assert(test, n == 2, function() return "later turn got " .. n end)
    end
    temper.async(temper.adapt_generator_fn(fn))
    local function fn3()
      temper.promisebuilder_complete(pb, 1)
    end
    temper.async(temper.adapt_generator_fn(fn3))
  end)
end

function Test_.test_passes_after_a_later_turn()
  temper.test("passes after a later turn", function(test)
    do
      local actual = temper.int32_add(1, 1)
      local expected = 2
      temper.test_assert(test, actual == expected, function() return "expected 1 + 1 == (" .. expected .. ") not (" .. actual .. ")" end)
    end
  end)
end

function Test_.test_fails_hard_in_an_async_block()
  temper.test("fails hard in an async block", function(test)
    local function fn()
      temper.test_fail(test, "failed hard in async")
      error("panic")
    end
    temper.async(temper.adapt_generator_fn(fn))
  end)
end

function Test_.test_panics_in_an_async_block()
  temper.test("panics in an async block", function(test)
    local function fn()
      error("panic")
    end
    temper.async(temper.adapt_generator_fn(fn))
  end)
end

function Test_.test_passes_after()
  temper.test("passes after", function(test)
    do
      local actual = temper.int32_add(1, 1)
      local expected = 2
      temper.test_assert(test, actual == expected, function() return "expected 1 + 1 == (" .. expected .. ") not (" .. actual .. ")" end)
    end
  end)
end

luaunit.LuaUnit.run("--pattern", "^Test_%.", ...)
