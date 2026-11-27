local temper = require("temper-core")

---@type integer
local calls

---@param b boolean
---@return boolean
local function noisy(b)
  calls = temper.int32_add(calls, 1)
  return b
end

---@class hoisting.Problem: temper.Failure

---@class hoisting.Problem.type: temper.Type
local Problem = temper.type("hoisting.Problem", temper.Failure)

---@class hoisting.Odd: hoisting.Problem
---@field message string

---@class hoisting.Odd.type: temper.Type
---@operator call: hoisting.Odd
local Odd = temper.type("hoisting.Odd", Problem)

---@param this hoisting.Odd
function Odd.constructor(this)
end

---@param this hoisting.Odd
---@return string
function Odd.get.message(this)
  return "odd"
end

---@param n integer
---@return integer
local function half(n)
  if temper.int32_mod(n, 2) ~= 0 then
    error(Odd())
  end
  return temper.int32_div(n, 2)
end

---@param c boolean
---@param a boolean
---@param b boolean
---@return boolean
local function pick(c, a, b)
  if noisy(c) then
    return a
  else
    return b
  end
end

---@param n integer
---@return boolean
local function isPos(n)
  return n > 0
end

---@param a boolean
---@param n integer
---@return boolean
local function both(a, n)
  if a then
    ---@type integer
    local t
    if not temper.catch(function()
      t = half(n)
    end, Odd) then
      t = 0
    end
    return isPos(t)
  else
    return false
  end
end

---@param a boolean
---@param n integer
---@return boolean
local function either(a, n)
  if a then
    return true
  else
    ---@type integer
    local t
    if not temper.catch(function()
      t = half(n)
    end, Odd) then
      t = 0
    end
    return isPos(t)
  end
end

---@param a integer
---@param b integer
---@return integer
local function sum(a, b)
  return temper.int32_add(a, b)
end

---@param x string|temper.Null
---@return string
local function label(x)
  ---@type integer
  local t
  if not temper.catch(function()
    t = half(3)
  end, Odd) then
    t = -1
  end
  return "got " .. (temper.is_null(x) and "nothing" or (x --[[@as string]])) .. " and " .. t
end

---@param ns integer[]
---@return integer
local function firstHalf(ns)
  do
    local i = 0
    while i < temper.list_length(ns) do
      local t, t_1, t_2 = temper.catch(function()
        if temper.list_get(ns, i) < 0 then
          i = temper.int32_add(i, 1)
          return "continue", "flow"
        end
        if temper.list_get(ns, i) == 0 then
          return "break", "flow"
        end
        return half(temper.list_get(ns, i)), "return"
      end, Odd)
      if not t then
        print("odd " .. temper.list_get(ns, i))
      elseif t_2 == "return" then
        return t_1
      elseif t_1 == "continue" then
        goto continue
      elseif t_1 == "break" then
        break
      end
      i = temper.int32_add(i, 1)
      ::continue::
    end
  end
  return -1
end

---@param n integer
---@return integer
local function halfOr(n)
  local h = 0
  local t, t_1, t_2 = temper.catch(function()
    if n < 0 then
      return -100, "return"
    end
    h = half(n)
  end, Odd)
  if not t then
    h = 7
  elseif t_2 == "return" then
    return t_1
  end
  return temper.int32_add(h, 1)
end

---@param n integer
---@return integer
local function pairsUpTo(n)
  local count = 0
  do
    local i = 0
    while i < n do
      do
        local j = 0
        while j < n do
          local t_3, t_4, t_5 = temper.catch(function()
            if j > temper.int32_sub(temper.int32_add(i, half(2)), 1) then
              i = temper.int32_add(i, 1)
              return "continue_outer", "flow"
            end
            if i == 3 then
              return "break_outer", "flow"
            end
            local t, t_1, t_2 = temper.catch(function()
              if j == 1 then
                j = temper.int32_add(j, 1)
                return "continue", "flow"
              end
              count = temper.int32_add(count, temper.int32_add(1, half(0)))
            end, Odd)
            if not t then
              count = temper.int32_add(count, 100)
            elseif t_1 == "continue" then
              return "continue", "flow"
            end
          end, Odd)
          if not t_3 then
            count = temper.int32_add(count, 1000)
          elseif t_4 == "continue_outer" then
            goto continue_outer
          elseif t_4 == "break_outer" then
            goto break_outer
          elseif t_4 == "continue" then
            goto continue
          end
          j = temper.int32_add(j, 1)
          ::continue::
        end
      end
      i = temper.int32_add(i, 1)
      ::continue_outer::
    end
    ::break_outer::
  end
  return count
end

calls = 0
print(tostring(pick(true, false, true)) .. " " .. tostring(pick(false, false, true)) .. " " .. calls)
print(tostring(both(true, 4)) .. " " .. tostring(both(true, 3)) .. " " .. tostring(both(false, 4)))
print(tostring(either(false, 4)) .. " " .. tostring(either(false, 3)) .. " " .. tostring(either(true, 3)))
---@type integer
local t
if not temper.catch(function()
  t = half(8)
end, Odd) then
  t = 0
end
---@type integer
local t_1
if not temper.catch(function()
  t_1 = half(7)
end, Odd) then
  t_1 = 100
end
print(tostring(sum(t, t_1)))
print(label(temper.null))
print(label("x"))
print(tostring(firstHalf({ 3, -2, 8 })))
print(tostring(firstHalf({ 5, 0, 8 })))
print(halfOr(-1) .. " " .. halfOr(4) .. " " .. halfOr(3))
print(tostring(pairsUpTo(5)))

return {}
