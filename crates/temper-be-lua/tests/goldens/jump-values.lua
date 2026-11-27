local temper = require("temper-core")

---@type integer
local steps

---@param n integer
---@return integer
local function step(n)
  steps = temper.int32_add(steps, 1)
  print("step " .. n)
  return n
end

---@param a integer
---@param b integer
---@return integer
local function pair(a, b)
  return temper.int32_add(temper.int32_mul(a, 10), b)
end

---@param s string
---@return integer
local function parse(s)
  return temper.string_toint32(s)
end

---@param y integer|temper.Null
---@return integer|temper.Null
local function next_(y)
  ---@type integer
  local x
  if temper.is_null(y) then
    return temper.null
  else
    x = y --[[@as integer]]
  end
  return temper.int32_add(x, 1)
end

---@param ys (integer|temper.Null)[]
---@return integer
local function sumPresent(ys)
  local sum = 0
  do
    local i = 0
    while i < temper.list_length(ys) do
      ---@type integer
      local t
      local subject = temper.list_get(ys, i)
      if temper.is_null(subject) then
        i = temper.int32_add(i, 1)
        goto continue
      else
        t = subject --[[@as integer]]
      end
      sum = temper.int32_add(sum, t)
      i = temper.int32_add(i, 1)
      ::continue::
    end
  end
  return sum
end

---@param words string[]
---@return integer
local function sumUntilBad(words)
  local sum = 0
  local i = 0
  while i < temper.list_length(words) do
    ---@type integer
    local t
    ---@type integer|temper.Null
    local held = temper.null
    if not temper.catch(function()
      held = parse(temper.list_get(words, i))
    end, temper.ParseError) then
    end
    if temper.is_null(held) then
      break
    else
      t = held --[[@as integer]]
    end
    sum = temper.int32_add(sum, t)
    i = temper.int32_add(i, 1)
  end
  return sum
end

---@param s string
---@return integer
local function parsedOr(s)
  ---@type integer
  local t
  ---@type integer|temper.Null
  local held = temper.null
  if not temper.catch(function()
    held = parse(s)
  end, temper.ParseError) then
  end
  if temper.is_null(held) then
    return -1
  else
    t = held --[[@as integer]]
  end
  return t
end

---@param i integer
---@return integer|temper.Null
local function after(i)
  if i < 3 then
    return temper.int32_add(i, 1)
  else
    return temper.null
  end
end

---@return string
local function stepped()
  local seen = ""
  do
    local i = 0
    while i < 6 do
      if i == 1 then
        local subject = after(i)
        i = temper.is_null(subject) and 9 or (subject --[[@as integer]])
        goto continue
      end
      seen = seen .. i
      local subject2 = after(i)
      i = temper.is_null(subject2) and 9 or (subject2 --[[@as integer]])
      ::continue::
    end
  end
  return seen
end

---@param y integer|temper.Null
---@return integer
local function paired(y)
  local int32 = step(1)
  ---@type integer
  local t
  if temper.is_null(y) then
    return -2
  else
    t = y --[[@as integer]]
  end
  return pair(int32, t)
end

---@param y integer|temper.Null
---@param z integer|temper.Null
---@return integer[]
local function listed(y, z)
  ---@type integer
  local t
  if temper.is_null(y) then
    return {}
  else
    t = y --[[@as integer]]
  end
  ---@type integer
  local t2
  if temper.is_null(z) then
    return { 9 }
  else
    t2 = z --[[@as integer]]
  end
  return { 1, t, 3, t2 }
end

---@param y integer|temper.Null
---@return string
local function shown(y)
  ---@type integer
  local t
  if temper.is_null(y) then
    return "none"
  else
    t = y --[[@as integer]]
  end
  return "y=" .. t
end

---@param t boolean
---@param y integer|temper.Null
---@return string
local function both(t, y)
  ---@type boolean
  local ok
  if t and not temper.is_null(y) then
    ok = true
  else
    return "no"
  end
  return "ok " .. tostring(ok)
end

---@param n integer
---@return string
local function sign(n)
  ---@type integer
  local t
  if n > 0 then
    t = 1
  elseif n < 0 then
    t = -1
  else
    return "zero"
  end
  return tostring(pair(0, t))
end

---@param text string|temper.Null
local function firstWord(text)
  ---@type string
  local t
  if temper.is_null(text) then
    return
  else
    t = text --[[@as string]]
  end
  print("first " .. temper.list_get(temper.string_split(t, " "), 0))
end

---@return integer
local function early()
  return 7
end

---@return integer
local function partly()
  step(2)
  return step(3)
end

---@param ys (integer|temper.Null)[]
---@return string
local function doJumps(ys)
  local seen = ""
  do
    local i = 0
    while i < temper.list_length(ys) do
      ---@type integer
      local v
      local subject = temper.list_get(ys, i)
      if temper.is_null(subject) then
        seen = seen .. "a"
        if i == 1 then
          i = temper.int32_add(i, 1)
          goto continue
        end
        seen = seen .. "b"
        break
      else
        v = subject --[[@as integer]]
      end
      seen = seen .. v
      i = temper.int32_add(i, 1)
      ::continue::
    end
  end
  return seen
end

---@param y integer|temper.Null
---@param z integer
---@return string
local function doReturn(y, z)
  ---@type integer
  local int32
  if temper.is_null(y) then
    if z > 0 then
      if z > 5 then
        return "big"
      end
      return "pos"
    end
    int32 = 0
  else
    int32 = y --[[@as integer]]
  end
  local v = int32
  return "v" .. v
end

---@param a integer
---@param b integer
---@param c integer
---@return integer
local function trio(a, b, c)
  return temper.int32_add(temper.int32_add(a, b), c)
end

---@param ys (integer|temper.Null)[]
---@return integer
local function ordered(ys)
  local sum = 0
  do
    local i = 0
    while i < temper.list_length(ys) do
      local int32 = step(temper.int32_add(10, i))
      ---@type integer
      local t
      local subject = temper.list_get(ys, i)
      if temper.is_null(subject) then
        step(temper.int32_add(20, i))
        i = temper.int32_add(i, 1)
        goto continue
      else
        t = subject --[[@as integer]]
      end
      sum = temper.int32_add(sum, trio(int32, t, step(temper.int32_add(30, i))))
      i = temper.int32_add(i, 1)
      ::continue::
    end
  end
  return sum
end

---@param xs (integer|temper.Null)[]
---@param ys (integer|temper.Null)[]
---@return string
local function nested(xs, ys)
  local seen = ""
  do
    local i = 0
    while i < temper.list_length(xs) do
      ---@type integer
      local v
      local subject = temper.list_get(xs, i)
      if temper.is_null(subject) then
        local subject2 = temper.list_get(ys, i)
        if temper.is_null(subject2) then
          break
        else
          v = subject2 --[[@as integer]]
        end
      else
        v = subject --[[@as integer]]
      end
      seen = seen .. v
      i = temper.int32_add(i, 1)
    end
  end
  return seen
end

---@param rows ((integer|temper.Null)[])[]
---@return string
local function gridded(rows)
  local seen = ""
  do
    local i = 0
    while i < temper.list_length(rows) do
      local row = temper.list_get(rows, i)
      do
        local j = 0
        while j < temper.list_length(row) do
          ---@type integer
          local v
          local subject = temper.list_get(row, j)
          if temper.is_null(subject) then
            goto break_outer
          else
            v = subject --[[@as integer]]
          end
          ---@type integer
          local w
          local subject2 = v < 0 and temper.null or v
          if temper.is_null(subject2) then
            seen = seen .. "-"
            i = temper.int32_add(i, 1)
            goto continue_outer
          else
            w = subject2 --[[@as integer]]
          end
          seen = seen .. w
          j = temper.int32_add(j, 1)
        end
      end
      seen = seen .. "|"
      i = temper.int32_add(i, 1)
      ::continue_outer::
    end
    ::break_outer::
  end
  return seen
end

---@param rows ((integer|temper.Null)[])[]
---@param keys (integer|temper.Null)[]
---@return string
local function firstBig(rows, keys)
  local seen = ""
  do
    local i = 0
    while i < temper.list_length(rows) do
      local subject = temper.list_get(keys, i)
      ---@type integer
      local int32
      if temper.is_null(subject) then
        local row = temper.list_get(rows, i)
        local first = -1
        do
          local j = 0
          while j < temper.list_length(row) do
            ---@type integer
            local v
            local subject2 = temper.list_get(row, j)
            if temper.is_null(subject2) then
              i = temper.int32_add(i, 1)
              goto continue_outer
            else
              v = subject2 --[[@as integer]]
            end
            if v > 5 then
              goto break_outer
            end
            if v > 2 then
              first = v
              break
            end
            j = temper.int32_add(j, 1)
          end
        end
        int32 = first
      else
        int32 = subject --[[@as integer]]
      end
      local k = int32
      seen = seen .. k .. ","
      i = temper.int32_add(i, 1)
      ::continue_outer::
    end
    ::break_outer::
  end
  return seen
end

---@param words string[]
---@return string
local function viaOrelse(words)
  local seen = ""
  do
    local i = 0
    while i < temper.list_length(words) do
      ---@type integer
      local n
      ---@type integer|temper.Null
      local held = temper.null
      if not temper.catch(function()
        held = parse(temper.list_get(words, i))
      end, temper.ParseError) then
      end
      if temper.is_null(held) then
        seen = seen .. "!"
        if i > 2 then
          break
        end
        i = temper.int32_add(i, 1)
        goto continue
      else
        n = held --[[@as integer]]
      end
      seen = seen .. n
      i = temper.int32_add(i, 1)
      ::continue::
    end
  end
  return seen
end

---@param n integer
---@return string
local function viaIf(n)
  ---@type string
  local string_
  if n > 0 then
    string_ = "pos"
  else
    if n < -5 then
      return "low"
    end
    string_ = "neg"
  end
  local s = string_
  return "s=" .. s
end

steps = 0
local part = next_(1)
local part2 = next_(temper.null)
print((temper.is_null(part) and "null" or tostring(part --[[@as integer]])) .. " " .. (temper.is_null(part2) and "null" or tostring(part2 --[[@as integer]])))
print(sumPresent({ 1, temper.null, 3 }) .. " " .. sumUntilBad({ "1", "2", "x", "4" }))
print(parsedOr("12") .. " " .. parsedOr("x") .. " " .. stepped())
print(paired(3) .. " " .. paired(temper.null))
print(temper.list_length(listed(2, 4)) .. " " .. temper.list_length(listed(temper.null, 4)) .. " " .. temper.list_get(listed(2, temper.null), 0))
print(shown(5) .. " " .. shown(temper.null))
print(both(true, 1) .. " " .. both(true, temper.null) .. " " .. both(false, 1))
print(sign(4) .. " " .. sign(-4) .. " " .. sign(0))
firstWord("hello there")
firstWord(temper.null)
print(doJumps({ 1, temper.null, 2, temper.null, 3 }) .. " " .. doReturn(1, 0) .. " " .. doReturn(temper.null, 9) .. " " .. doReturn(temper.null, 1) .. " " .. doReturn(temper.null, 0))
print(tostring(ordered({ 1, temper.null })))
print(nested({ 1, temper.null, temper.null, 4 }, { 0, 7, temper.null, 0 }) .. " " .. viaOrelse({ "1", "x", "2", "y", "z", "3" }))
print(gridded({ { 1, 2 }, { 3, -1, 4 }, { 5, temper.null, 6 }, { 7 } }) .. " " .. firstBig({ { 1, 3 }, { temper.null, 1 }, { 4 }, { 9 }, { 1 } }, { 7, temper.null, temper.null, temper.null, temper.null }))
print(viaIf(1) .. " " .. viaIf(-1) .. " " .. viaIf(-9))
print(early() .. " " .. partly() .. " " .. steps)

return {}
