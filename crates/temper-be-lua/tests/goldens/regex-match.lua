local temper = require("temper-core")
local Regex = require("std/regex").Regex
local Match = require("std/regex").Match
local Group = require("std/regex").Group
local RegexNode = require("std/regex").RegexNode
local Or = require("std/regex").Or
local Sequence = require("std/regex").Sequence
local Repeat = require("std/regex").Repeat
local oneOrMore = require("std/regex").oneOrMore
local CodeSet = require("std/regex").CodeSet
local CodePart = require("std/regex").CodePart
local CodePoints = require("std/regex").CodePoints
local entire = require("std/regex").entire
local Capture = require("std/regex").Capture
local CodeRange = require("std/regex").CodeRange
local Begin = require("std/regex").Begin
local WordBoundary = require("std/regex").WordBoundary
local Dot = require("std/regex").Dot

---@type string
local text
---@type string
local moreText

---@param group std.regex.Group
local function checkGroup(group)
  local name = group.name
  local value = group.value
  local begin = group.begin
  local end_ = group.end_
  local size = tostring(temper.string_countbetween(text, begin, end_))
  local begin2 = tostring(temper.string_countbetween(text, 1, begin))
  print("match group '" .. name .. "': '" .. value .. "' at " .. begin2 .. " size " .. size)
end

local regex = Sequence({ oneOrMore(CodeSet({ CodePoints("ab") })), CodePoints("c") }):compiled()
local full = entire(regex.data):compiled()
local strings = { "abc", "ac", "bbabac", "ab", "dc", "acc", "cbc" }
do
  local length = temper.list_length(strings)
  local i = 0
  while i < length do
    local string_ = temper.list_get(strings, i)
    i = temper.int32_add(i, 1)
    print("found " .. string_ .. ": " .. tostring(regex:found(string_)))
    print("matches " .. string_ .. ": " .. tostring(full:found(string_)))
  end
end
local catcher = Sequence({ Repeat(CodePoints("a"), 1, temper.null, false), Capture("intro", Repeat(CodePoints("b"), 1, temper.null, false)), CodePoints("c"), Repeat(Or({ Capture("option1", CodePoints("d")), Capture("alphaOption2", CodePoints("e")) }), 1, temper.null, false) }):compiled()
text = "🌍aaabbceef"
local subject = catcher:find(text)
local match = temper.is_null(subject) and error("panic") or (subject --[[@as std.regex.Match]])
checkGroup(match.full)
checkGroup(temper.mapped_get(match.groups, "intro"))
checkGroup(temper.mapped_get(match.groups, "alphaOption2"))
local thing = Repeat(CodeSet({ CodeRange(97, 122) }, false), 1, temper.null, false):compiled()
local multiText = "---abc---xyz---"
local subject2 = thing:find(multiText)
local first = temper.is_null(subject2) and error("panic") or (subject2 --[[@as std.regex.Match]])
local subject3 = thing:find(multiText, first.full.end_)
local second = temper.is_null(subject3) and error("panic") or (subject3 --[[@as std.regex.Match]])
print("Found both " .. first.full.value .. " and " .. second.full.value .. ".")
moreText = "🌍aaabbcee🌎abcd🌏"

---@param match std.regex.Match
---@return string
local function fn(match)
  local groups = match.groups
  ---@type string
  local option1
  if not temper.catch(function()
    option1 = temper.mapped_get(groups, "option1").value
  end, temper.MissingKey) then
    option1 = ""
  end
  ---@type string
  local alphaOption2
  if not temper.catch(function()
    alphaOption2 = temper.mapped_get(groups, "alphaOption2").value
  end, temper.MissingKey) then
    alphaOption2 = ""
  end
  local begin = temper.string_countbetween(moreText, 1, match.full.begin)
  return "-" .. option1 .. alphaOption2 .. begin .. "-"
end

local replaced = catcher:replace(moreText, fn)
print("replaced: " .. replaced)
local notReplaced = catcher:replace("nope", function(match) return "hi" end)
print("not replaced: " .. notReplaced)
local subject6 = catcher:find("not here")
local subject5 = temper.is_null(subject6) and temper.null or (subject6 --[[@as std.regex.Match]]).full
local subject4 = temper.is_null(subject5) and temper.null or (subject5 --[[@as std.regex.Group]]).value
local noMatch = temper.is_null(subject4) and "sorry" or (subject4 --[[@as string]])
print("No match says " .. noMatch)
local or_ = Or({ CodePoints("ab"), CodePoints("bc") }):compiled().data
local option1 = "ab"
local option2 = "bc"
local optionNone = "cb"
print("or option 1: " .. tostring(or_:found(option1)))
print("or option 2: " .. tostring(or_:found(option2)))
print("or option none: " .. tostring(or_:found(optionNone)))
local begin = Sequence({ Begin, CodePoints("a") }):compiled()
local beginsWithATrue = "ab"
local beginsWithAFalse = "bab"
print("begin true: " .. tostring(begin:found(beginsWithATrue)))
print("begin false: " .. tostring(begin:found(beginsWithAFalse)))
local negatedBasic = entire(Sequence({ CodePoints("a"), CodeSet({ CodeRange(97, 99) }, true) }):compiled().data):compiled()
local nbTrue = "a🌍"
local nbFalse = "ac"
print("negated basic true: " .. tostring(negatedBasic:found(nbTrue)))
print("negated basic false: " .. tostring(negatedBasic:found(nbFalse)))
local simplerLines = "It was the best of times.\nIt was the worst of times."
local subject9 = Repeat(CodeSet({}, true), 1, temper.null, false):compiled():find(simplerLines)
local subject8 = temper.is_null(subject9) and temper.null or (subject9 --[[@as std.regex.Match]]).full
local subject7 = temper.is_null(subject8) and temper.null or (subject8 --[[@as std.regex.Group]]).value
print(temper.is_null(subject7) and "-" or (subject7 --[[@as string]]))
local subject12 = Repeat(CodeSet({}, false), 1, temper.null, false):compiled():find(simplerLines)
local subject11 = temper.is_null(subject12) and temper.null or (subject12 --[[@as std.regex.Match]]).full
local subject10 = temper.is_null(subject11) and temper.null or (subject11 --[[@as std.regex.Group]]).value
print(temper.is_null(subject10) and "-" or (subject10 --[[@as string]]))
local notLow = oneOrMore(CodeSet({ CodeRange(0, 47) }, true)):compiled()
local subject15 = notLow:find("-/a🌍-")
local subject14 = temper.is_null(subject15) and temper.null or (subject15 --[[@as std.regex.Match]]).full
local subject13 = temper.is_null(subject14) and temper.null or (subject14 --[[@as std.regex.Group]]).value
print("not low: " .. (temper.is_null(subject13) and "-" or (subject13 --[[@as string]])))
local subject18 = Repeat(CodeSet({ CodePoints("\t"), CodePoints("\n"), CodePoints(" "), CodePoints(">") }, true), 1, temper.null, false):compiled():find("ab>c")
local subject17 = temper.is_null(subject18) and temper.null or (subject18 --[[@as std.regex.Match]]).full
local subject16 = temper.is_null(subject17) and temper.null or (subject17 --[[@as std.regex.Group]]).value
print("not space or >: " .. (temper.is_null(subject16) and "-" or (subject16 --[[@as string]])))
local idStart = CodeSet({ CodeRange(97, 122), CodeRange(65, 90), CodePoints("_") }, false):compiled().data
local idContinue = Or({ idStart, CodeSet({ CodeRange(48, 57) }, false):compiled().data })
local word = Sequence({ WordBoundary, idStart, Repeat(idContinue, 0, temper.null), WordBoundary }):compiled()
local expected = "def_456"
local subject21 = word:find("123_abc " .. expected .. " ghi_789")
local subject20 = temper.is_null(subject21) and temper.null or (subject21 --[[@as std.regex.Match]]).full
local subject19 = temper.is_null(subject20) and temper.null or (subject20 --[[@as std.regex.Group]]).value
local found = temper.is_null(subject19) and "-" or (subject19 --[[@as string]])
print("Found id: " .. found)
local stringToEscape = "$(*^"
local regexWithInterpolatedString = Sequence({ Dot, Repeat(CodePoints(stringToEscape), 1, temper.null, false), Dot }):compiled()
local stringToEscapeAndMore = "a" .. stringToEscape .. stringToEscape .. "b"
local subject22 = regexWithInterpolatedString:find(stringToEscapeAndMore)
local escapedMatch = temper.is_null(subject22) and error("panic") or (subject22 --[[@as std.regex.Match]])
print(escapedMatch.full.value)
local subject23 = Sequence({ Dot, Capture("full", Dot) }):compiled():find("ab")
local ownFullMatch = temper.is_null(subject23) and error("panic") or (subject23 --[[@as std.regex.Match]])
local ownFull = temper.mapped_get(ownFullMatch.groups, "full").value
print("Own full: " .. ownFull .. " vs " .. ownFullMatch.full.value)
local comboLines = "It was the best\nof times.\r\n\rIt was the worst of times."
do
  local list = Or({ CodePoints("\n"), Sequence({ CodePoints("\r"), Repeat(CodePoints("\n"), 0, 1, false) }) }):compiled():split(comboLines)
  local length2 = temper.list_length(list)
  local i2 = 0
  while i2 < length2 do
    local line = temper.list_get(list, i2)
    i2 = temper.int32_add(i2, 1)
    print(line .. ";")
  end
end

return {}
