local temper = require("temper-core")
local int64_1 = temper.int64_constructor(1)
local int64_9223372036854775807 = temper.int64_constructor(2147483647, 4294967295)
local int64_neg9223372036854775808 = temper.int64_constructor(-2147483648, 0)
local int64_neg1 = temper.int64_constructor(-1)

---@param string_ string
local function checkParse32(string_)
  ---@type string
  local converted
  if not temper.pcall(function()
    converted = tostring(temper.string_toint32(string_, 16))
  end) then
    converted = "failed"
  end
  temper.log("Parse " .. string_ .. " -> " .. converted)
end

---@param string_ string
local function checkParse64(string_)
  ---@type string
  local converted
  if not temper.pcall(function()
    converted = temper.int64_tostring(temper.string_toint64(string_, 16))
  end) then
    converted = "failed"
  end
  temper.log("Parse " .. string_ .. " -> " .. converted)
end

temper.log("Int32")
local one = 1
local big = temper.int32_mul(2147483647, one)
local small = temper.int32_mul(-2147483648, one)
temper.log(big .. " + 1 == " .. temper.int32_add(big, 1))
temper.log(small .. " - 1 == " .. temper.int32_sub(small, 1))
temper.log(big .. " * 4 == " .. temper.int32_mul(big, 4))
temper.log(small .. " * 4 == " .. temper.int32_mul(small, 4))
temper.log(temper.int32_add(small, 1) .. " * 4 == " .. temper.int32_mul(temper.int32_add(small, 1), 4))
temper.log(temper.int32_add(small, 1) .. " * " .. big .. " == " .. temper.int32_mul(temper.int32_add(small, 1), big))
temper.log(small .. " / -1 == " .. temper.int32_div(small, -1))
temper.log(small .. " % -1 == " .. temper.int32_mod(small, -1))
temper.log("-(" .. small .. ") == " .. temper.int32_unm(small))
local three = temper.int32_mul(3, one)
local five = temper.int32_mul(5, one)
temper.log(five .. " / " .. three .. " == " .. temper.int32_div(five, three))
temper.log(five .. " / -" .. three .. " == " .. temper.int32_div(five, temper.int32_unm(three)))
temper.log("-" .. five .. " / " .. three .. " == " .. temper.int32_div(temper.int32_unm(five), three))
temper.log("-" .. five .. " / -" .. three .. " == " .. temper.int32_div(temper.int32_unm(five), temper.int32_unm(three)))
temper.log(five .. " % " .. three .. " == " .. temper.int32_mod(five, three))
temper.log(five .. " % -" .. three .. " == " .. temper.int32_mod(five, temper.int32_unm(three)))
temper.log("-" .. five .. " % " .. three .. " == " .. temper.int32_mod(temper.int32_unm(five), three))
temper.log("-" .. five .. " % -" .. three .. " == " .. temper.int32_mod(temper.int32_unm(five), temper.int32_unm(three)))
temper.log("Int64")
local notSoBig = temper.int32_toint64(big)
local notSoSmall = temper.int32_toint64(small)
temper.log(temper.int64_tostring(notSoBig) .. " + 1 == " .. temper.int64_tostring(notSoBig + int64_1))
temper.log(temper.int64_tostring(notSoSmall) .. " - 1 == " .. temper.int64_tostring(notSoSmall - int64_1))
local big64 = int64_9223372036854775807 * temper.int32_toint64(one)
local small64 = int64_neg9223372036854775808 * temper.int32_toint64(one)
temper.log(temper.int64_tostring(big64) .. " + 1 == " .. temper.int64_tostring(big64 + int64_1))
temper.log(temper.int64_tostring(small64) .. " - 1 == " .. temper.int64_tostring(small64 - int64_1))
temper.log(temper.int64_tostring(small64) .. " / -1 == " .. temper.int64_tostring(temper.int64_div(small64, int64_neg1)))
temper.log(temper.int64_tostring(small64) .. " % -1 == " .. temper.int64_tostring(temper.int64_mod(small64, int64_neg1)))
temper.log("-(" .. temper.int64_tostring(small64) .. ") == " .. temper.int64_tostring(temper.int64_unm(small64)))
local three64 = temper.int32_toint64(three)
local five64 = temper.int32_toint64(five)
temper.log(temper.int64_tostring(five64) .. " / " .. temper.int64_tostring(three64) .. " == " .. temper.int64_tostring(temper.int64_div(five64, three64)))
temper.log(temper.int64_tostring(five64) .. " / -" .. temper.int64_tostring(three64) .. " == " .. temper.int64_tostring(temper.int64_div(five64, temper.int64_unm(three64))))
temper.log("-" .. temper.int64_tostring(five64) .. " / " .. temper.int64_tostring(three64) .. " == " .. temper.int64_tostring(temper.int64_div(temper.int64_unm(five64), three64)))
temper.log("-" .. temper.int64_tostring(five64) .. " / -" .. temper.int64_tostring(three64) .. " == " .. temper.int64_tostring(temper.int64_div(temper.int64_unm(five64), temper.int64_unm(three64))))
temper.log(temper.int64_tostring(five64) .. " % " .. temper.int64_tostring(three64) .. " == " .. temper.int64_tostring(temper.int64_mod(five64, three64)))
temper.log(temper.int64_tostring(five64) .. " % -" .. temper.int64_tostring(three64) .. " == " .. temper.int64_tostring(temper.int64_mod(five64, temper.int64_unm(three64))))
temper.log("-" .. temper.int64_tostring(five64) .. " % " .. temper.int64_tostring(three64) .. " == " .. temper.int64_tostring(temper.int64_mod(temper.int64_unm(five64), three64)))
temper.log("-" .. temper.int64_tostring(five64) .. " % -" .. temper.int64_tostring(three64) .. " == " .. temper.int64_tostring(temper.int64_mod(temper.int64_unm(five64), temper.int64_unm(three64))))
checkParse32("7FFFFFFF")
checkParse32("-80000000")
checkParse32("80000000")
checkParse32("-80000001")
temper.log("Not so big: " .. temper.int64_toint32unsafe(notSoBig))
---@type string
local t
if not temper.pcall(function()
  t = tostring(temper.int64_toint32(big64))
end) then
  t = "no"
end
temper.log("Actually big: " .. t)
checkParse64("7FFFFFFFFFFFFFFF")
checkParse64("-8000000000000000")
checkParse64("8000000000000000")
checkParse64("-8000000000000001")
temper.log(temper.int64_tostring(temper.int64_min(small64, big64)) .. " < " .. temper.int64_tostring(temper.int64_max(small64, big64)))

return {}
