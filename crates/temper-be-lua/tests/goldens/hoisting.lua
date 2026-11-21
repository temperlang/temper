local temper = require("temper-core")

---@type integer
local calls

---@param b boolean
---@return boolean
local function noisy(b)
  calls = temper.int32_add(calls, 1)
  return b
end

---@param n integer
---@return integer
local function half(n)
  if temper.int32_mod(n, 2) ~= 0 then
    return temper.bubble()
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
    if not temper.pcall(function()
      t = half(n)
    end) then
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
    if not temper.pcall(function()
      t = half(n)
    end) then
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
  if not temper.pcall(function()
    t = half(3)
  end) then
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
      local jump
      repeat
        local t, t_1, t_2 = temper.pcall(function()
          if temper.list_get(ns, i) < 0 then
            i = temper.int32_add(i, 1)
            return "continue", "flow"
          end
          if temper.list_get(ns, i) == 0 then
            return "break", "flow"
          end
          return half(temper.list_get(ns, i)), "return"
        end)
        if not t then
          temper.log("odd " .. temper.list_get(ns, i))
        elseif t_2 == "return" then
          return t_1
        elseif t_1 == "continue" then
          break
        elseif t_1 == "break" then
          jump = "break"
          break
        end
        i = temper.int32_add(i, 1)
      until true
      if jump == "break" then
        break
      end
    end
  end
  return -1
end

---@param n integer
---@return integer
local function halfOr(n)
  local h = 0
  local t, t_1, t_2 = temper.pcall(function()
    if n < 0 then
      return -100, "return"
    end
    h = half(n)
  end)
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
      local jump
      repeat
        do
          local j = 0
          while j < n do
            repeat
              local t_3, t_4, t_5 = temper.pcall(function()
                if j > temper.int32_sub(temper.int32_add(i, half(2)), 1) then
                  i = temper.int32_add(i, 1)
                  return "continue_outer", "flow"
                end
                if i == 3 then
                  return "break_outer", "flow"
                end
                local t, t_1, t_2 = temper.pcall(function()
                  if j == 1 then
                    j = temper.int32_add(j, 1)
                    return "continue", "flow"
                  end
                  count = temper.int32_add(count, temper.int32_add(1, half(0)))
                end)
                if not t then
                  count = temper.int32_add(count, 100)
                elseif t_1 == "continue" then
                  return "continue", "flow"
                end
              end)
              if not t_3 then
                count = temper.int32_add(count, 1000)
              elseif t_4 == "continue_outer" then
                jump = "continue"
                break
              elseif t_4 == "break_outer" then
                jump = "break"
                break
              elseif t_4 == "continue" then
                break
              end
              j = temper.int32_add(j, 1)
            until true
            if jump then
              break
            end
          end
          if jump then
            break
          end
        end
        i = temper.int32_add(i, 1)
      until true
      if jump == "break" then
        break
      end
    end
  end
  return count
end

calls = 0
temper.log(tostring(pick(true, false, true)) .. " " .. tostring(pick(false, false, true)) .. " " .. calls)
temper.log(tostring(both(true, 4)) .. " " .. tostring(both(true, 3)) .. " " .. tostring(both(false, 4)))
temper.log(tostring(either(false, 4)) .. " " .. tostring(either(false, 3)) .. " " .. tostring(either(true, 3)))
---@type integer
local t
if not temper.pcall(function()
  t = half(8)
end) then
  t = 0
end
---@type integer
local t_1
if not temper.pcall(function()
  t_1 = half(7)
end) then
  t_1 = 100
end
temper.log(tostring(sum(t, t_1)))
temper.log(label(temper.null))
temper.log(label("x"))
temper.log(tostring(firstHalf({ 3, -2, 8 })))
temper.log(tostring(firstHalf({ 5, 0, 8 })))
temper.log(halfOr(-1) .. " " .. halfOr(4) .. " " .. halfOr(3))
temper.log(tostring(pairsUpTo(5)))

return {}
