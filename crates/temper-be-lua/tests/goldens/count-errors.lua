local temper = require("temper-core")

---@class count-errors.CountError: temper.Failure

---@class count-errors.CountError.type: temper.Type
local CountError = temper.type("count-errors.CountError", temper.Failure)

---@class count-errors.BadCount: count-errors.CountError
---@field text string
---@field message string

---@class count-errors.BadCount.type: temper.Type
---@operator call: count-errors.BadCount
local BadCount = temper.type("count-errors.BadCount", CountError)

---@param this count-errors.BadCount
---@param text string
function BadCount.constructor(this, text)
  this.text = text
end

---@param this count-errors.BadCount
---@return string
function BadCount.get.message(this)
  return "bad count (text: " .. this.text .. ")"
end

---@param text string
---@return integer
local function parseCount(text)
  if text == "zero" then
    return 0
  elseif text == "two" then
    return 2
  else
    error(BadCount(text))
  end
end

return { CountError = CountError, BadCount = BadCount, parseCount = parseCount }
