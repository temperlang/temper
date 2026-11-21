local temper = require("temper-core")
local Regex = require("std/regex").Regex
local RegexNode = require("std/regex").RegexNode
local Sequence = require("std/regex").Sequence
local Or = require("std/regex").Or
local Begin = require("std/regex").Begin
local CodePoints = require("std/regex").CodePoints
local Repeat = require("std/regex").Repeat
local Space = require("std/regex").Space
local Match = require("std/regex").Match

---@type std.regex.Regex
local commaItemStartRegex

---@param text string
---@return string
local function bulletizeList(text)
  local replaced = commaItemStartRegex:replace(text, function(match) return "\n- " end)
  return temper.string_slice(replaced, temper.string_next(replaced, 1), temper.string_end(replaced))
end

commaItemStartRegex = Sequence({ Or({ Begin, CodePoints(",") }), Repeat(Space, 0, temper.null, false) }):compiled()
temper.log(bulletizeList("apples, bananas, cherries"))

return { bulletizeList = bulletizeList }
