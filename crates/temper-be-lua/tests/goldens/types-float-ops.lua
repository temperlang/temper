local temper = require("temper-core")

---@type number
local two

---@param label string
---@param actual number
---@param expected number
local function near(label, actual, expected)
  local subject = temper.float64_near(actual, expected)
  local result = subject == true and "✅" or "❌ wanted " .. temper.float64_tostring(expected) .. ", was " .. temper.float64_tostring(actual)
  temper.log(label .. ": " .. result)
end

local one = 1.0
two = one + one
local half = one / two
near("half.abs()", math.abs(half), half)
near("(-half).abs()", math.abs(-half), half)
local three = one + two
near("half.acos()", math.acos(half), math.pi / three)
local pi = math.pi
local six = two * three
near("half.asin()", math.asin(half), pi / six)
local four = two * two
near("(-one).atan()", math.atan(-one), -pi / four)
near("one.atan2(-one)", temper.float64_atan2(one, -one), three * pi / four)
near("(four / three).ceil()", math.ceil(four / three), two)
near("(-four / three).ceil()", math.ceil(-four / three), -one)
near("(pi / three).cos()", math.cos(pi / three), half)
local e = temper.float64_e()
near("one.cosh()", temper.float64_cosh(one), (e + one / e) / two)
near("half.exp() == e.sqrt()", math.exp(half), math.sqrt(e))
near("half.expm1()", temper.float64_expm1(half), math.sqrt(e) - one)
near("(four / three).floor()", math.floor(four / three), one)
near("(-four / three).floor()", math.floor(-four / three), -two)
near("e.log()", math.log(temper.float64_e()), one)
local five = one + four
local ten = two * five
near("ten.log10()", temper.float64_log10(ten), one)
near("(e - one).log1p()", temper.float64_log1p(e - one), one)

---@param x number
---@return string
local function fn(x)
  ---@param y number
  ---@return string
  local function fn2(y)
    local pair = temper.listed_join({ temper.float64_max(x, y), temper.float64_min(x, y) }, ", ", function(z) return temper.float64_tostring(z) end)
    return "(" .. pair .. ")"
  end
  return temper.listed_join({ 0.0 / 0.0, two }, ", ", fn2)
end

local maxMinCombos = temper.listed_join({ 0.0 / 0.0, one }, ", ", fn)
temper.log("maxMinCombos: " .. maxMinCombos)
local quarter = half / two
near("2.25 % 2.0", temper.fmod(two + quarter, two), 0.25)
near("2.25 % -2.0", temper.fmod(two + quarter, -two), 0.25)
near("-2.25 % 2.0", temper.fmod(-(two + quarter), two), -0.25)
near("-2.25 % -2.0", temper.fmod(-(two + quarter), -two), -0.25)
near("pi.round()", temper.float64_round(pi), three)
near("(-pi).round()", temper.float64_round(-pi), -three)
near("pi.sign()", temper.float64_sign(pi), one)
near("(-pi).sign()", temper.float64_sign(-pi), -one)
local zero = one - one
temper.log("zero.sign(): " .. temper.float64_tostring(temper.float64_sign(zero)))
temper.log("(-zero).sign(): " .. temper.float64_tostring(temper.float64_sign(-zero)))
near("(pi / six).sin()", math.sin(pi / six), half)
near("one.sinh()", temper.float64_sinh(one), (e - one / e) / two)
near("(-pi / four).tan()", math.tan(-pi / four), -one)
near("(three * pi / four).tan()", math.tan(three * pi / four), -one)
near("one.tanh()", temper.float64_tanh(one), (e ^ 2.0 - 1.0) / (e ^ 2.0 + 1.0))
temper.log("one.near(one + 0.1): " .. tostring(temper.float64_near(one, one + 0.1)))
temper.log("one.near(one + 0.25, absTol = 0.25): " .. tostring(temper.float64_near(one, one + 0.25, nil, 0.25)))
temper.log("one.near(one + 0.1 + 1e-9, absTol = 0.1): " .. tostring(temper.float64_near(one, one + 0.1 + 1e-9, nil, 0.1)))
temper.log("one.near(one - 0.1, absTol = 0.11): " .. tostring(temper.float64_near(one, one - 0.1, nil, 0.11)))
temper.log("ten.near(ten + 0.1, absTol = 0.011): " .. tostring(temper.float64_near(ten, ten + 0.1, nil, 0.011)))
temper.log("ten.near(ten + 0.1, relTol = 0.011): " .. tostring(temper.float64_near(ten, ten + 0.1, 0.011)))

return {}
