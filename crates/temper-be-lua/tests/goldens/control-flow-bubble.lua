local temper = require("temper-core")

---@param x integer|temper.Null
---@return integer
local function more(x)
  return temper.bubble()
end

---@param x string|temper.Null
---@return string
local function something(x)
  if not temper.is_null(x) then
    return x --[[@as string]]
  else
    return temper.bubble()
  end
end

---@param x string|temper.Null
local function report(x)
  ---@type string
  local message
  if not temper.pcall(function()
    message = something(x)
  end) then
    message = "null"
  end
  ---@type string
  local suffix
  if not temper.pcall(function()
    ---@type integer
    local t_1
    if not temper.pcall(function()
      temper.cast_to_string(x)
      t_1 = 1
    end) then
      t_1 = 0
    end
    suffix = tostring(more(t_1))
  end) then
    suffix = ""
  end
  temper.log(message .. suffix)
end

---@param cols integer
---@param total integer
---@return integer
local function calcRows(cols, total)
  temper.log("calcRows(" .. cols .. ", " .. total .. ")")
  if temper.int32_mod(total, cols) ~= 0 then
    return temper.bubble()
  end
  return temper.int32_div(total, cols)
end

---@param s string
---@return integer
local function countBelow(s)
  local i = 0
  while i < temper.string_toint32(s) do
    i = temper.int32_add(i, 1)
  end
  return i
end

---@param n integer
---@return integer
local function firstOver(n)
  local i = 0
  while i < 9 do
    i = temper.int32_add(i, 1)
    if i > n then
      break
    end
  end
  return i
end

---@param n integer
---@return integer
local function sumSkipping(n)
  local sum = 0
  do
    local i = 0
    while i < 5 do
      repeat
        if i == n then
          i = temper.int32_add(i, 1)
          break
        end
        sum = temper.int32_add(sum, i)
        i = temper.int32_add(i, 1)
      until true
    end
  end
  return sum
end

---@param n integer
---@return integer
local function tenTimes(n)
  do
    local i = 0
    while i < 9 do
      if i == n then
        return temper.int32_mul(i, 10)
      end
      i = temper.int32_add(i, 1)
    end
  end
  return temper.bubble()
end

---@param r integer|temper.Null
---@return boolean
local function isNone(r)
  return temper.is_null(r)
end

---@param n integer
---@return string
local function viaOrElse(n)
  ---@type integer
  local t_1
  if not temper.pcall(function()
    t_1 = firstOver(n)
  end) then
    t_1 = -1
  end
  ---@type integer
  local t_2
  if not temper.pcall(function()
    t_2 = sumSkipping(n)
  end) then
    t_2 = -1
  end
  ---@type integer
  local t_3
  if not temper.pcall(function()
    t_3 = tenTimes(n)
  end) then
    t_3 = -1
  end
  return t_1 .. " " .. t_2 .. " " .. t_3
end

---@return integer
local function unreachableInt()
  error("panic")
end

---@generic T
---@param fallback T
---@return T
local function failAs(fallback)
  return temper.bubble()
end

local function bail()
  temper.bubble()
end

---@return string|temper.Null
local function noName()
  return temper.null
end

local function stop()
  error("panic")
end

---@param n integer
---@return integer
local function half(n)
  if temper.int32_mod(n, 2) ~= 0 then
    bail()
    error("panic")
  end
  if n < 0 then
    return failAs(n)
  else
    return temper.int32_div(n, 2)
  end
end

---@param b boolean
---@return integer
local function pick(b)
  if b then
    return 1
  else
    return unreachableInt()
  end
end

---@param b boolean
---@return string|temper.Null
local function named(b)
  if b then
    return "named"
  else
    return noName()
  end
end

---@param b boolean
---@return integer
local function chosen(b)
  ---@type integer
  local x
  if b then
    x = 3
    temper.log("chosen")
  else
    unreachableInt()
    error("panic")
  end
  return x
end

---@param s string|temper.Null
---@return string
local function shout(s)
  if temper.is_null(s) then
    stop()
    error("panic")
  end
  local t = s --[[@as string]]
  return t .. "!"
end

---@param a integer
---@param b integer
local function branch(a, b)
  local c = a == 0 and (b == 0 and "00" or (b == 1 and "01" or (b == 2 and "02" or "0x"))) or (a == 1 and "1x" or (a == 2 and "2x" or (a == 3 and "3x" or "xx")))
  temper.log("branch(" .. a .. ", " .. b .. ") -> " .. c)
end

report("hi")
report(temper.null)
temper.log(tostring(calcRows(3, 6)))
---@type integer
local t_1
if not temper.pcall(function()
  t_1 = countBelow("abc")
end) then
  t_1 = -1
end
temper.log(tostring(t_1))
---@type integer
local t_2
if not temper.pcall(function()
  t_2 = firstOver(1)
end) then
  t_2 = -1
end
---@type integer|temper.Null
local t_3
if not temper.pcall(function()
  t_3 = firstOver(1)
end) then
  t_3 = temper.null
end
temper.log(t_2 .. " " .. tostring(isNone(t_3)))
temper.log(viaOrElse(2))
temper.log(viaOrElse(12))
local string_ = pick(true) .. " "
local subject = named(true)
local subject2 = named(false)
temper.log(string_ .. (temper.is_null(subject) and "-" or (subject --[[@as string]])) .. " " .. (temper.is_null(subject2) and "null" or (subject2 --[[@as string]])))
---@type integer
local t_4
if not temper.pcall(function()
  t_4 = half(4)
end) then
  t_4 = -1
end
---@type integer
local t_5
if not temper.pcall(function()
  t_5 = half(3)
end) then
  t_5 = -1
end
---@type integer
local t_6
if not temper.pcall(function()
  t_6 = half(-2)
end) then
  t_6 = -1
end
temper.log(t_4 .. " " .. t_5 .. " " .. t_6)
temper.log(chosen(true) .. " " .. shout("hey"))
branch(0, 2)
branch(2, 0)

return {}
