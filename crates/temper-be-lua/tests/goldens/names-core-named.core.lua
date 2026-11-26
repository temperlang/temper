local temper = require("temper-core")

---@param who string
---@return string
local function greet(who)
  return "hello from " .. who
end

---@param n integer
---@param d integer
---@return integer
local function share(n, d)
  ---@type integer
  local t
  if not temper.pcall(function()
    t = temper.int32_div(n, d)
  end) then
    t = 0
  end
  return t
end

return { greet = greet, share = share }
