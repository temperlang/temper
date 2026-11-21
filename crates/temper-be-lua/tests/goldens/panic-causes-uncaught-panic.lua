---@param n integer
---@return integer
local function boom(n)
  if n > 0 then
    error("panic")
  end
  return n
end

print("before")
boom(1)
print("after")

return {}
