local temper = require("temper-core")

---@param b boolean
---@return string
local function explicit(b)
  if b then
    return "true"
  end
  return "false"
end

---@param b boolean
---@return string
local function halfAndHalf(b)
  if b then
    return "true"
  end
  return "false"
end

---@param b boolean
---@return string
local function implicit(b)
  if b then
    return "true"
  else
    return "false"
  end
end

---@param a boolean
---@param b boolean
---@return string
local function nested(a, b)
  local result = "neither"
  if a then
    if b then
      result = "both"
    end
  else
    result = "not a"
  end
  return result
end

local t = true
local f = not t
temper.log("explicit    true  -> " .. explicit(t))
temper.log("explicit    false -> " .. explicit(f))
temper.log("halfAndHalf true  -> " .. halfAndHalf(t))
temper.log("halfAndHalf false -> " .. halfAndHalf(f))
temper.log("implicit    true  -> " .. implicit(t))
temper.log("implicit    false -> " .. implicit(f))
temper.log("nested      true  false -> " .. nested(t, f))
temper.log("nested      false true  -> " .. nested(f, t))

return {}
