local temper = require("temper-core")
local tested = require("testing")["$test"]
local luaunit = require("luaunit")
luaunit.FAILURE_PREFIX = temper.test_failure_prefix

local runs = 0
local base = 10

Test_ = {}

function Test_.test_twice_works()
  temper.test("twice works", function(test)
    local x = tested.twice(2)
    runs = temper.int32_add(temper.int32_add(runs, x), base)
  end)
end

function Test_.test_names_punctuation_quotes_and_n_code()
  temper.test("names: punctuation, 'quotes', and ünïcode", function(t)
    tested.twice(runs)
  end)
end

function Test_.test_locals_a_test_assigns()
  temper.test("locals a test assigns", function(test)
    local n = 0
    do
      local i = 0
      while i < 3 do
        n = temper.int32_add(n, tested.twice(i))
        i = temper.int32_add(i, 1)
      end
    end
    runs = n
  end)
end

luaunit.LuaUnit.run("--pattern", "^Test_%.", ...)
