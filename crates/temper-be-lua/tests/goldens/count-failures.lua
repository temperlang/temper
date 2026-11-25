local temper = require("temper-core")

---@class count-failures.CountError: temper.Failure

---@class count-failures.CountError.type: temper.Type
local CountError = temper.type("count-failures.CountError", temper.Failure)

---@class count-failures.BadCount: count-failures.CountError
---@field text string
---@field message string

---@class count-failures.BadCount.type: temper.Type
---@operator call: count-failures.BadCount
local BadCount = temper.type("count-failures.BadCount", CountError)

---@param this count-failures.BadCount
---@param text string
function BadCount.constructor(this, text)
  this.text = text
end

---@param this count-failures.BadCount
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
