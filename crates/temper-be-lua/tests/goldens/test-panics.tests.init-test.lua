local temper = require("temper-core")
local tested = require("test-panics")["$test"]
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

Test_ = {}

function Test_.test_passes_before()
  temper.test("passes before", function(test)
    do
      local actual = temper.int32_add(1, 1)
      local expected = 2
      temper.test_assert(test, actual == expected, function() return "expected 1 + 1 == (" .. expected .. ") not (" .. actual .. ")" end)
    end
  end)
end

function Test_.test_panics_reading_past_the_end()
  temper.test("panics reading past the end", function(test)
    ---@type integer
    local n
    if not temper.pcall(function()
      n = at({ 1, 2 }, 5)
    end) then
      n = -1
    end
    do
      local actual = n
      local expected = -1
      temper.test_assert(test, actual == expected, function() return "expected n == (" .. expected .. ") not (" .. actual .. ")" end)
    end
  end)
end

function Test_.test_panics_outright()
  temper.test("panics outright", function(test)
    error("panic")
  end)
end

function Test_.test_fails_an_assertion()
  temper.test("fails an assertion", function(test)
    temper.test_assert(test, temper.int32_add(1, 1) == 3, function() return "wrong on purpose" end)
  end)
end

function Test_.test_passes_after()
  temper.test("passes after", function(test)
    do
      local actual = at({ 1, 2 }, 1)
      local expected = 2
      temper.test_assert(test, actual == expected, function() return "expected at([1, 2], 1) == (" .. expected .. ") not (" .. actual .. ")" end)
    end
  end)
end

luaunit.LuaUnit.run("--pattern", "^Test_%.", ...)
