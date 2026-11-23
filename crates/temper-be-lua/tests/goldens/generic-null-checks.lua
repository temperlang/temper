local temper = require("temper-core")

---@class generic-null-checks.Named
---@field name string

---@class generic-null-checks.Named.type: temper.Type
---@operator call: generic-null-checks.Named
local Named = temper.type("generic-null-checks.Named")

---@param this generic-null-checks.Named
---@param name string
function Named.constructor(this, name)
  this.name = name
end

---@generic T
---@param x T
---@return boolean
local function isNull(x)
  return temper.is_null(x)
end

---@generic T
---@param x T
---@param y T
---@return T
local function orElse(x, y)
  if temper.is_null(x) then
    return y
  else
    return x
  end
end

temper.log(tostring(isNull(temper.null)) .. " " .. tostring(isNull("a")) .. " " .. tostring(isNull("a")))
temper.log(tostring(isNull(temper.null)) .. " " .. tostring(isNull(1)) .. " " .. tostring(isNull(1)))
temper.log(tostring(isNull(temper.null)) .. " " .. tostring(isNull(Named("n"))))
local subject = orElse(temper.null, "y")
temper.log(temper.is_null(subject) and "null" or (subject --[[@as string]]))
local string_ = orElse("x", "y") .. " "
local subject2 = orElse(temper.null, 2)
temper.log(string_ .. (temper.is_null(subject2) and -1 or (subject2 --[[@as integer]])))

return { Named = Named, isNull = isNull, orElse = orElse }
