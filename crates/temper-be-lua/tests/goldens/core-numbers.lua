local temper = require("temper-core")
local int64_9223372036854775807 = temper.int64_constructor(2147483647, 4294967295)
local int64_1 = temper.int64_constructor(1)

local one = 1
local big = temper.int32_mul(2147483647, one)
local small = temper.int32_mul(-2147483648, one)
print(temper.int32_add(big, 1) .. " " .. temper.int32_sub(small, 1) .. " " .. temper.int32_add(one, 1) .. " " .. temper.int32_sub(one, 1) .. " " .. temper.int32_add(small, 1) .. " " .. temper.int32_sub(big, 1))
local one64 = temper.int32_toint64(one)
local big64 = int64_9223372036854775807 * one64
local small64 = temper.int64_unm(big64) - one64
print(temper.int64_tostring(big64 + int64_1) .. " " .. temper.int64_tostring(small64 - int64_1) .. " " .. temper.int64_tostring(one64 - int64_1))
local inf = 1e308 * 10.0
local nan = inf - inf
local ninf = 0.0 - inf
print(tostring(temper.float64_near(nan, nan)) .. " " .. tostring(temper.float64_near(inf, inf)) .. " " .. tostring(temper.float64_near(ninf, ninf)) .. " " .. tostring(temper.float64_near(nan, 1.0)) .. " " .. tostring(temper.float64_near(1.0, nan)))
print(tostring(temper.float64_near(inf, 1.0)) .. " " .. tostring(temper.float64_near(inf, ninf)) .. " " .. tostring(temper.float64_near(inf, 1.0, 0.0, 0.0)) .. " " .. tostring(temper.float64_near(inf, inf, 0.0, 0.0)) .. " " .. tostring(temper.float64_near(inf, 1.0, 1.0, inf)) .. " " .. tostring(temper.float64_near(1.0, ninf, 1.0, inf)))
print(tostring(temper.float64_near(1.0, 1.5, nan, 1.0)) .. " " .. tostring(temper.float64_near(1.0, 1.5, 1.0, nan)) .. " " .. tostring(temper.float64_near(1.0, 1.0, nan, nan)) .. " " .. tostring(temper.float64_near(-0.0, 0.0, 0.0, 0.0)))
print(tostring(temper.float64_near(1.0, 1.0 + 1e-10)) .. " " .. tostring(temper.float64_near(1.0, 1.1)) .. " " .. tostring(temper.float64_near(1.0, 1.1, 0.2)) .. " " .. tostring(temper.float64_near(0.0, 1e-300, 1e-9, 1e-12)))

return {}
