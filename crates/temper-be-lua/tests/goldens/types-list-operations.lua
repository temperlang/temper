local temper = require("temper-core")

---@generic T
---@param list T[]
local function reportLength(list)
  print("Length: " .. temper.listed_length(list))
end

---@param string_ string
---@param sep string
local function reportSplit(string_, sep)
  local strings = temper.string_split(string_, sep)
  local len = tostring(temper.list_length(strings))
  print("Split " .. temper.listed_join(strings, ", ", function(x) return x end) .. " - " .. len)
end

---@param callback fun(arg0: string[])
local function something(callback)
  callback({ "Called", "with", "listed." })
end

local function runSomething()
  something(function(words) print(temper.listed_join(words, " ", function(word) return word end)) end)
end

reportLength({ 4, 5, 6 })
local ls = temper.listed_sorted({ 5, 6, 7, 8, 9, 0, 1, 2, 3, 4 }, function(i, j) return temper.int32_sub(i, j) end)
print("All  " .. temper.listed_join(ls, ", ", function(x) return tostring(x) end))
print("Even " .. temper.listed_join(temper.listed_filter(ls, function(x) return temper.band(x, 1) == 0 end), ", ", function(x) return tostring(x) end))
print("Odd  " .. temper.listed_join(temper.listed_filter(ls, function(x) return temper.band(x, 1) ~= 0 end), ", ", function(x) return tostring(x) end))
print("Neg  " .. temper.listed_join(temper.listed_map(ls, function(x) return temper.int32_unm(x) end), ", ", function(x) return tostring(x) end))
local listed = ls
print("Neg  " .. temper.listed_join(temper.listed_map(listed, function(x) return temper.int32_unm(x) end), ", ", function(x) return tostring(x) end))
local listedList = temper.listed_tolist(listed)
print("Listed list length is " .. temper.list_length(listedList))
print("Looping")
local n = temper.list_length(ls)
do
  local i = 0
  while i < n do
    print("  " .. temper.list_get(ls, i))
    i = temper.int32_add(i, 1)
  end
end
reportSplit("a ; b ; c", " ; ")
reportSplit("def", "")
reportSplit(" g  h i", " ")
reportSplit(" ", " ")
reportSplit("", " ")
reportSplit("", "")
reportSplit("🌍", "")

---@param list string[]
---@return string[]
local function fn11(list)
  local builder = temper.list_tolistbuilder(list)
  temper.listbuilder_sort(builder, function(a, b) return temper.str_cmp(a, b) end)
  return builder
end

local triangleOfFooBar = temper.list_tolistbuilder(temper.listed_map({ {}, { "bar" }, { "bar", "baz" }, { "bar", "baz", "boo" }, { "bar", "baz", "boo", "far" }, { "bar", "baz", "boo", "far", "faz" }, { "far", "faz", "foo", "bar", "baz", "boo" } }, fn11))
do
  local j = 0
  while j < temper.listbuilder_length(triangleOfFooBar) do
    local row = temper.listed_get(triangleOfFooBar, j)
    print("Fwd  [" .. temper.listed_join(row, " ", function(x) return x end) .. "]")
    temper.listbuilder_reverse(row)
    j = temper.int32_add(j, 1)
  end
end
temper.listbuilder_reverse(triangleOfFooBar)
do
  local j2 = 0
  while j2 < temper.listbuilder_length(triangleOfFooBar) do
    local row2 = temper.listed_get(triangleOfFooBar, j2)
    print("Back [" .. temper.listed_join(row2, " ", function(x) return x end) .. "]")
    j2 = temper.int32_add(j2, 1)
  end
end
local messageBuilder = temper.listbuilder_constructor()
temper.listbuilder_add(messageBuilder, "hello")
temper.listbuilder_set(messageBuilder, 0, "hi")
temper.listbuilder_addall(messageBuilder, { "there", "y'all" })
temper.listbuilder_add(messageBuilder, "say", 0)
local copyThat = temper.listbuilder_tolistbuilder(messageBuilder)
temper.listbuilder_addall(messageBuilder, { "more", "than" }, 1)
temper.listbuilder_addall(copyThat, messageBuilder)
print(temper.listed_join(messageBuilder, " ", function(x) return x end))
print(temper.listed_join(temper.listed_sorted(copyThat, function(a, b) return temper.str_cmp(b, a) end), " ", function(x) return x end))
print(temper.listed_join(copyThat, " ", function(x) return x end))
local typed = messageBuilder
local oldLast = temper.listbuilder_removelast(copyThat)
print("Removed " .. oldLast .. " so " .. temper.listed_join(copyThat, " ", function(x) return x end))
local nextLast = temper.listbuilder_splice(copyThat, temper.int32_sub(temper.listbuilder_length(copyThat), 1), 1)
local oldWords = temper.listbuilder_splice(copyThat, 4, 3, nextLast)
temper.listbuilder_splice(copyThat, nil, 0, oldWords)
temper.listbuilder_splice(copyThat, temper.listbuilder_length(copyThat), nil, { "and", "the", "end" })
print(temper.listed_join(copyThat, " ", function(x) return x end))
temper.listbuilder_splice(copyThat, nil, nil, { "gone" })
print(temper.listed_join(copyThat, " ", function(x) return x end))
temper.listbuilder_clear(messageBuilder)
temper.listbuilder_add(messageBuilder, "Bye")
print(temper.listed_join(messageBuilder, " ", function(x) return x end))
do
  local i2 = -2
  while i2 <= 10 do
    print("Slice(" .. i2 .. ", " .. temper.int32_add(i2, 2) .. "): [" .. temper.listed_join(temper.listed_slice(ls, i2, temper.int32_add(i2, 2)), ", ", function(x) return tostring(x) end) .. "]")
    i2 = temper.int32_add(i2, 1)
  end
end
print("Outside fallback: " .. temper.listed_getor(ls, temper.list_length(ls), 123))
do
  ---@param b boolean
  ---@return string
  local function booleanToString(b)
    return tostring(b)
  end
  local ls2 = { true, false }
  local joined = temper.listed_join(ls2, ", ", booleanToString)
  local inverted = temper.listed_map(ls2, function(b) return not b end)
  ---@param b boolean
  ---@return string
  local function fn22(b)
    if b then
      return "not false"
    else
      return "not true"
    end
  end
  local joinedAndInverted = temper.listed_join(inverted, ", ", fn22)
  print("list = [" .. joined .. "]")
  print("list (each inverted) = [" .. joinedAndInverted .. "]")
  local onlyTrue = temper.listed_filter(ls2, function(b) return b end)
  local onlyFalse = temper.listed_filter(ls2, function(b) return not b end)
  print("list (== true only) = [" .. temper.listed_join(onlyTrue, ", ", booleanToString) .. "]")
  print("list (== false only) = [" .. temper.listed_join(onlyFalse, ", ", booleanToString) .. "]")
end
do
  local sizes = { 0.25, 3.0 }
  ---@param f number
  ---@return string
  local function fn12(f)
    if temper.float_cmp(f, 1.0) < 0 then
      return "small"
    else
      return "big"
    end
  end
  local labels = temper.listed_map(sizes, fn12)
  print("sizes = [" .. temper.listed_join(labels, ", ", function(s) return s end) .. "]")
end
runSomething()
do
  local aListBuilder = temper.listbuilder_constructor()
  temper.listbuilder_add(aListBuilder, "first")
  local builtFirst = temper.listbuilder_tolist(aListBuilder)
  temper.listbuilder_add(aListBuilder, "second")
  local builtSecond = temper.listed_tolist(aListBuilder)
  temper.listbuilder_add(aListBuilder, "third")
  print("builtFirst has " .. temper.listed_join(builtFirst, ", ", function(s) return s end))
  print("builtSecond has " .. temper.listed_join(builtSecond, ", ", function(s) return s end))
end

return {}
