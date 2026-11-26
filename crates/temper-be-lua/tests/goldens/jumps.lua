local temper = require("temper-core")

---@param n integer
---@return integer
local function search(n)
  local found = -1
  local i = 0
  while i < n do
    local jump
    local j = 0
    while true do
      if j >= n then
        break
      end
      if temper.int32_mul(i, j) == 6 then
        found = temper.int32_add(temper.int32_mul(i, 10), j)
        jump = "break"
        break
      end
      j = temper.int32_add(j, 1)
    end
    if jump then
      break
    end
    i = temper.int32_add(i, 1)
  end
  return found
end

---@param n integer
---@return integer
local function odds(n)
  local k = 0
  local sum = 0
  repeat
    repeat
      k = temper.int32_add(k, 1)
      if temper.band(k, 1) == 0 then
        break
      end
      sum = temper.int32_add(sum, k)
    until true
  until k >= n
  return sum
end

temper.log(tostring(search(5)))
temper.log(tostring(odds(9)))

return {}
