local temper = require("temper-core")
local tested = require("host-errors")["$test"]
local Match = require("std/regex").Match
local RegexNode = require("std/regex").RegexNode
local CodePoints = require("std/regex").CodePoints
local luaunit = require("luaunit")
luaunit.FAILURE_PREFIX = temper.test_failure_prefix

---@class host-errors.Problem: temper.Failure

---@class host-errors.Problem.type: temper.Type
local Problem = temper.type("host-errors.Problem", temper.Failure)

---@class host-errors.Oops: host-errors.Problem
---@field message string

---@class host-errors.Oops.type: temper.Type
---@operator call: host-errors.Oops
local Oops = temper.type("host-errors.Oops", Problem)

---@param this host-errors.Oops
function Oops.constructor(this)
end

---@param this host-errors.Oops
---@return string
function Oops.get.message(this)
  return "oops"
end

---@param i integer
---@return integer
local function at(i)
  if i == 99 then
    error(Oops())
  end
  return i
end

---@return integer[]
local function built()
  local lb = temper.listbuilder_constructor()
  temper.listbuilder_add(lb, 1)
  return lb
end

---@param ls integer[]
---@param i integer
---@return integer
local function read(ls, i)
  return temper.listed_get(ls, at(i))
end

---@param lb integer[]
---@param i integer
---@return integer
local function write(lb, i)
  temper.listbuilder_set(lb, at(i), 3)
  return temper.listbuilder_length(lb)
end

---@param lb integer[]
---@param i integer
---@return integer
local function insert(lb, i)
  temper.listbuilder_add(lb, 2, at(i))
  return temper.listbuilder_length(lb)
end

---@param lb integer[]
---@param i integer
---@return integer
local function removeLast(lb, i)
  at(i)
  return temper.listbuilder_removelast(lb)
end

---@param d temper.Deque
---@param i integer
---@return integer
local function removeFirst(d, i)
  at(i)
  return temper.deque_removefirst(d)
end

---@param ls integer[]
---@param i integer
---@return integer
local function sum(ls, i)
  at(i)
  return temper.listed_reduce(ls, function(a, b) return temper.int32_add(a, b) end)
end

---@param s string
---@param i integer
---@return integer
local function codeAt(s, i)
  at(i)
  return temper.string_get(s, temper.string_end(s))
end

---@param i integer
---@return string
local function replaced(i)
  at(i)
  return CodePoints("b"):compiled():replace("abc", function(match) return temper.list_get({ "x" }, i) end)
end

Test_ = {}

function Test_.test_list_read()
  temper.test("list read", function(test)
    ---@type integer
    local r
    if not temper.catch(function()
      r = read({ 1, 2 }, 5)
    end, Oops) then
      r = -1
    end
    temper.test_assert(test, r ~= -1, function() return "caught" end)
  end)
end

function Test_.test_list_read_at_the_end()
  temper.test("list read at the end", function(test)
    local ls = { 1, 2 }
    ---@type integer
    local r
    if not temper.catch(function()
      r = read(ls, temper.list_length(ls))
    end, Oops) then
      r = -1
    end
    temper.test_assert(test, r ~= -1, function() return "caught" end)
  end)
end

function Test_.test_negative_index()
  temper.test("negative index", function(test)
    ---@type integer
    local r
    if not temper.catch(function()
      r = read(built(), -1)
    end, Oops) then
      r = -1
    end
    temper.test_assert(test, r ~= -1, function() return "caught" end)
  end)
end

function Test_.test_set()
  temper.test("set", function(test)
    ---@type integer
    local r
    if not temper.catch(function()
      r = write(built(), 5)
    end, Oops) then
      r = -1
    end
    temper.test_assert(test, r ~= -1, function() return "caught" end)
  end)
end

function Test_.test_set_at_the_end()
  temper.test("set at the end", function(test)
    local lb = built()
    ---@type integer
    local r
    if not temper.catch(function()
      r = write(lb, temper.listbuilder_length(lb))
    end, Oops) then
      r = -1
    end
    temper.test_assert(test, r ~= -1, function() return "caught" end)
  end)
end

function Test_.test_insert()
  temper.test("insert", function(test)
    ---@type integer
    local r
    if not temper.catch(function()
      r = insert(built(), 5)
    end, Oops) then
      r = -1
    end
    temper.test_assert(test, r ~= -1, function() return "caught" end)
  end)
end

function Test_.test_insert_past_the_end()
  temper.test("insert past the end", function(test)
    local lb = built()
    ---@type integer
    local r
    if not temper.catch(function()
      r = insert(lb, temper.int32_add(temper.listbuilder_length(lb), 1))
    end, Oops) then
      r = -1
    end
    temper.test_assert(test, r ~= -1, function() return "caught" end)
  end)
end

function Test_.test_insert_at_the_end_appends()
  temper.test("insert at the end appends", function(test)
    local lb = built()
    ---@type integer
    local n
    if not temper.catch(function()
      n = insert(lb, temper.listbuilder_length(lb))
    end, Oops) then
      n = -1
    end
    temper.test_assert(test, n == 2 and temper.listed_get(lb, 1) == 2, function() return "didn't append" end)
  end)
end

function Test_.test_remove_last()
  temper.test("remove last", function(test)
    ---@type integer
    local r
    if not temper.catch(function()
      r = removeLast(temper.listbuilder_constructor(), 0)
    end, Oops) then
      r = -1
    end
    temper.test_assert(test, r ~= -1, function() return "caught" end)
  end)
end

function Test_.test_remove_first()
  temper.test("remove first", function(test)
    ---@type integer
    local r
    if not temper.catch(function()
      r = removeFirst(temper.deque_constructor(), 0)
    end, Oops) then
      r = -1
    end
    temper.test_assert(test, r ~= -1, function() return "caught" end)
  end)
end

function Test_.test_reduce()
  temper.test("reduce", function(test)
    ---@type integer
    local r
    if not temper.catch(function()
      r = sum({}, 0)
    end, Oops) then
      r = -1
    end
    temper.test_assert(test, r ~= -1, function() return "caught" end)
  end)
end

function Test_.test_string_read()
  temper.test("string read", function(test)
    ---@type integer
    local r
    if not temper.catch(function()
      r = codeAt("ab", 0)
    end, Oops) then
      r = -1
    end
    temper.test_assert(test, r ~= -1, function() return "caught" end)
  end)
end

function Test_.test_replace_callback()
  temper.test("replace callback", function(test)
    ---@type string
    local r
    if not temper.catch(function()
      r = replaced(3)
    end, Oops) then
      r = ""
    end
    temper.test_assert(test, r ~= "", function() return "caught" end)
  end)
end

luaunit.LuaUnit.run("--pattern", "^Test_%.", ...)
