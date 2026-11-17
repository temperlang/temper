local temper = require("temper-core")

---@return integer
local function undefinedPlusArgInside()
  return temper.bubble()
end

---@param string_ string
---@param minSize integer
---@param pad string
---@return string
local function padLeft(string_, minSize, pad)
  local needed = temper.int32_sub(minSize, temper.string_countbetween(string_, 1, temper.string_end(string_)))
  if needed <= 0 then
    return string_
  end
  return temper.bubble()
end

temper.bubble()

return {}
