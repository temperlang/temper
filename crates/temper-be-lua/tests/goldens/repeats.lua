local temper = require("temper-core")

---@param n integer
---@return integer
local function odds(n)
  local i = 0
  local sum = 0
  repeat
    repeat
      i = temper.int32_add(i, 1)
      local bit_ = temper.band(i, 1)
      if bit_ == 0 then
        break
      end
      sum = temper.int32_add(sum, i)
    until true
  until i >= n
  return sum
end

---@param n integer
---@return integer
local function pairs_(n)
  local count = 0
  local i = 0
  while i < n do
    local jump_rows
    repeat
      i = temper.int32_add(i, 1)
      local j = 0
      while true do
        j = temper.int32_add(j, 1)
        if j > i then
          jump_rows = "continue"
          break
        end
        count = temper.int32_add(count, 1)
      end
      if jump_rows then
        break
      end
    until true
  end
  return count
end

---@param n integer
---@return integer
local function positive(n)
  if n < 0 then
    return temper.bubble()
  end
  return n
end

print(tostring(odds(9)))
print(tostring(pairs_(4)))
print(tostring(positive(3)))

return {}
