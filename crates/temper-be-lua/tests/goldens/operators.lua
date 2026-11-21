local temper = require("temper-core")

---@param a boolean
---@param b boolean
---@return string
local function same(a, b)
  return tostring(a == b) .. " " .. tostring(a ~= b) .. " " .. temper.bool_cmp(a, b)
end

---@param a integer
---@param b integer
---@return integer
local function quot(a, b)
  return temper.int32_div(a, b)
end

---@param a integer
---@param b integer
---@return integer
local function rem(a, b)
  return temper.int32_mod(a, b)
end

---@param a string
---@param b string
---@return string
local function words(a, b)
  if a < b then
    return a .. " < " .. b
  end
  if a <= b then
    return a .. " == " .. b
  else
    return a .. " > " .. b
  end
end

local one = 1
local big = temper.int32_mul(2147483647, one)
local small = temper.int32_mul(-2147483648, one)
temper.log(temper.int32_add(big, 1) .. " " .. temper.int32_sub(small, 1) .. " " .. temper.int32_mul(big, 4) .. " " .. temper.int32_unm(small))
temper.log(temper.shr32(temper.int32_mul(-7, one), 1) .. " " .. temper.ushr32(temper.int32_mul(-7, one), 28) .. " " .. temper.shl32(one, 33) .. " " .. temper.bnot(one) .. " " .. temper.band(5, 3) .. " " .. temper.bor(5, 3) .. " " .. temper.bxor(5, 3))
temper.log(temper.int_cmp(3, 5) .. " " .. temper.str_cmp("b", "a"))
temper.log(same(true, true) .. " " .. same(true, false) .. " " .. same(false, true))
temper.log(quot(-7, 2) .. " " .. rem(-7, 2) .. " " .. quot(small, -1))
temper.log(words("apple", "banana"))
temper.log(words("pear", "pear"))
temper.log(words("zoo", "yak"))
temper.log(tostring(words("a", "b") == "a < b"))
local n = 0
local odd = 0
repeat
  n = temper.int32_add(n, 1)
  if temper.int32_mod(n, 2) ~= 0 then
    odd = temper.int32_add(odd, n)
  end
until n >= 9
temper.log("odd " .. odd)
local found = -1
local i = 0
while i < 5 and found < 0 do
  local j = 0
  while j < 5 do
    if temper.int32_mul(i, j) == 6 then
      found = temper.int32_add(temper.int32_mul(i, 10), j)
      break
    end
    j = temper.int32_add(j, 1)
  end
  i = temper.int32_add(i, 1)
end
temper.log("found " .. found)
local empty = ""
local x = "x"
temper.log(tostring(empty == "" and x ~= ""))

return {}
