local temper = require("temper-core")
local int64_2147483647 = temper.int64_constructor(2147483647)
local int64_neg2147483648 = temper.int64_constructor(-2147483648)
local int64_9007199254740992 = temper.int64_constructor(2097152, 0)
local int64_1 = temper.int64_constructor(1)

---@param a number
---@param b number
---@return boolean
local function gt(a, b)
  return temper.float_cmp(a, b) > 0
end

---@param a number
---@param b number
---@return boolean
local function lt(a, b)
  return temper.float_cmp(a, b) < 0
end

---@param a number
---@param b number
---@return boolean
local function eq(a, b)
  return temper.float_eq(a, b)
end

---@param a number
---@param b number
---@return boolean
local function neq(a, b)
  return not temper.float_eq(a, b)
end

---@param a number
---@param b number
---@return number
local function add(a, b)
  return a + b
end

---@param string string
local function checkParse(string)
  ---@type string
  local converted
  if not temper.pcall(function()
    converted = temper.float64_tostring(temper.string_tofloat64(string))
  end) then
    converted = "failed"
  end
  print("Parse " .. string .. " -> " .. converted)
end

---@param string string
local function checkTiny(string)
  ---@type string
  local positive
  if not temper.pcall(function()
    positive = tostring(temper.float_cmp(temper.string_tofloat64(string), 0.0) > 0)
  end) then
    positive = "failed"
  end
  print("Parse " .. string .. " > 0 -> " .. positive)
end

local negativeZero = -0.0
local zero = 0.0
local one = 1.0
local two = 2.0
local three = 3.0
if gt(one, zero) then
  print("greater than")
end
if lt(zero, one) then
  print("less than")
end
if eq(zero, one) then
  print("equality is wrong")
end
if neq(zero, one) then
  print("inequality")
end
print(temper.float64_tostring(zero))
if neq(negativeZero, zero) then
  print("zeroes not equal")
end
if lt(negativeZero, zero) then
  print("negZero < zero")
end
if temper.float_eq(zero - zero, zero) then
  print("zero subtraction")
end
if temper.float_eq(zero + zero, zero) then
  print("zero addition")
end
if temper.float_eq(negativeZero - zero, negativeZero) then
  print("negative zero minus zero is negative")
end
if temper.float_eq(negativeZero + zero, zero) then
  print("negative zero plus zero is positive")
end
if temper.float_eq(zero - negativeZero, zero) then
  print("zero minus negative zero is zero")
end
if temper.float_eq(zero + negativeZero, zero) then
  print("zero plus negative zero is zero")
end
if temper.float_eq(negativeZero - negativeZero, zero) then
  print("negative zero minus negative zero is zero")
end
if temper.float_eq(negativeZero + negativeZero, negativeZero) then
  print("negative zero plus negative zero is negative zero")
end
print(temper.float64_tostring(negativeZero))
local third = one / three
local half = one / two
local twoThirds = two / three
if gt(third, half) then
  print("oops third > half")
end
if gt(half, twoThirds) then
  print("oops half > twoThirds")
end
if neq(third * two, twoThirds) then
  print("misaligned multiplication")
end
if neq(add(third, twoThirds), one) then
  print("doesn't add back up")
end
print(temper.float64_tostring(half))
print(temper.float64_tostring(4.0 ^ -half))
print(temper.float64_tostring(third))
print(temper.float64_tostring(twoThirds))
print(temper.float64_tostring(one))
local almostBig = 1e25 * one
print(temper.float64_tostring(almostBig))
local big = 99999999999999.0 * 999999999999.0
print(temper.float64_tostring(big))
local bigger = big * big * big * big * big * big * big * big
print(temper.float64_tostring(bigger))
local middling = -1500.0 * one
print(temper.float64_tostring(middling) .. " " .. temper.float64_tostring(1000000.0 * one) .. " " .. temper.float64_tostring(9999999.0 * one) .. " " .. temper.float64_tostring(0.001 * one))
print(temper.float64_tostring(1e21 * one) .. " " .. temper.float64_tostring(1.5e-10 * one) .. " " .. temper.float64_tostring(2e23 * one))
local biggest = bigger * bigger * bigger * bigger * bigger * bigger
print("biggest " .. temper.float64_tostring(biggest))
local tiniest = one / biggest
print("tiniest " .. temper.float64_tostring(tiniest))
local negativeInfinity = biggest * -1.0
print("negative infinity " .. temper.float64_tostring(negativeInfinity))
local infinity = math.huge
print(temper.float64_tostring(infinity))
print("processed an infinity")
local nan = 0.0 / 0.0
print(temper.float64_tostring(nan))
if temper.float_cmp(nan, infinity) > 0 then
  print("NaN is biggest")
else
  print("Infinity is biggest")
end
print("NaN == NaN? " .. tostring(temper.float_eq(nan, 0.0 / 0.0)))
print("Int to float: " .. temper.float64_tostring(temper.int32_tofloat64(temper.float64_toint32unsafe(two))))
print("Positive to int: " .. temper.float64_toint32unsafe(4.9 * one))
print("Negative to int: " .. temper.float64_toint32unsafe(-4.9 * one))
---@type string
local t
if not temper.pcall(function()
  t = tostring(temper.float64_toint32(infinity))
end) then
  t = "no"
end
print("Infinity to int: " .. t)
---@type string
local t_1
if not temper.pcall(function()
  t_1 = tostring(temper.float64_toint32(0.0 / 0.0))
end) then
  t_1 = "no"
end
print("NaN to int: " .. t_1)
local oneInt64 = temper.float64_toint64unsafe(one)
local notSoBig = int64_2147483647 * oneInt64
local notSoSmall = int64_neg2147483648 * oneInt64
local slightlyBigger = temper.float64_toint64unsafe(temper.int64_tofloat64unsafe(notSoBig) + 1.5)
local slightlySmaller = temper.float64_toint64unsafe(temper.int64_tofloat64unsafe(notSoSmall) - 1.5)
local tooBigForFloat64 = int64_9007199254740992 * temper.float64_toint64(one)
local okToConvert64 = temper.int64_tofloat64unsafe(tooBigForFloat64 - int64_1)
local sneakyBig = okToConvert64 + 1.0
print("Bigger to Int64: " .. temper.int64_tostring(slightlyBigger))
print("Smaller to Int64: " .. temper.int64_tostring(slightlySmaller))
print("Barely ok to Int64: " .. temper.int64_tostring(temper.float64_toint64unsafe(okToConvert64)))
---@type string
local t_2
if not temper.pcall(function()
  t_2 = temper.int64_tostring(temper.float64_toint64(sneakyBig))
end) then
  t_2 = "no"
end
print("Sneaky big to Int64: " .. t_2)
---@type string
local t_3
if not temper.pcall(function()
  t_3 = temper.float64_tostring(temper.int64_tofloat64(tooBigForFloat64))
end) then
  t_3 = "no"
end
print("Too big Int64 to Float64: " .. t_3)
---@type string
local t_4
if not temper.pcall(function()
  t_4 = temper.int64_tostring(temper.float64_toint64(infinity))
end) then
  t_4 = "no"
end
print("Infinity to Int64: " .. t_4)
---@type string
local t_5
if not temper.pcall(function()
  t_5 = temper.int64_tostring(temper.float64_toint64(0.0 / 0.0))
end) then
  t_5 = "no"
end
print("NaN to Int64: " .. t_5)
checkParse("-1.5")
checkParse("-0")
checkParse("2.0")
checkParse("2")
checkParse(" 2 ")
checkParse("5e-1")
checkParse("NaN")
checkParse("-Infinity")
checkTiny("2.225e-308")
checkTiny("5e-324")
checkParse("1e-400")
checkParse("-1e-400")
checkParse("2.")
checkParse(".2")
checkParse("2.0.0")
checkParse("-inf")
checkParse("totally")

return {}
