local temper = require("temper-core")

---@type integer
local Boolean

---@class core-shadows.mine.String
---@field text integer
---@field doubled integer

---@class core-shadows.mine.String.type: temper.Type
---@operator call: core-shadows.mine.String
local String = temper.type("core-shadows.mine.String")

---@param this core-shadows.mine.String
---@param text integer
function String.constructor(this, text)
  this.text = text
end

---@param this core-shadows.mine.String
---@return integer
function String.get.doubled(this)
  return temper.int32_mul(this.text, 2)
end

---@class core-shadows.mine.List
---@field n integer

---@class core-shadows.mine.List.type: temper.Type
---@operator call: core-shadows.mine.List
local List = temper.type("core-shadows.mine.List")

---@param this core-shadows.mine.List
---@param n integer
function List.constructor(this, n)
  this.n = n
end

---@param x integer
---@return integer
local function ignore(x)
  return temper.int32_add(x, 1)
end

---@return integer
local function summary()
  local s = String(3)
  local l = List(4)
  return temper.int32_add(temper.int32_add(temper.int32_add(temper.int32_add(s.text, s.doubled), l.n), ignore(10)), Boolean)
end

Boolean = 7

return { String = String, List = List, ignore = ignore, summary = summary }
