local temper = require("temper-core")

---@type string
local hay

---@param s string
local function reportSliceInfo(s)
  local start = 1
  local end_ = temper.string_end(s)
  local count = temper.string_countbetween(s, start, end_)
  temper.log("Count of code-points for `" .. s .. "`: " .. count)
  temper.log("Has at least 0: " .. tostring(temper.string_hasatleast(s, start, end_, 0)))
  temper.log("Has at least 1: " .. tostring(temper.string_hasatleast(s, start, end_, 1)))
  temper.log("Has at least 2: " .. tostring(temper.string_hasatleast(s, start, end_, 2)))
  temper.log("Has at least 3: " .. tostring(temper.string_hasatleast(s, start, end_, 3)))
  temper.log("Has at least 4: " .. tostring(temper.string_hasatleast(s, start, end_, 4)))
  temper.log("Has at least 5: " .. tostring(temper.string_hasatleast(s, start, end_, 5)))
  temper.log("Has at least 6: " .. tostring(temper.string_hasatleast(s, start, end_, 6)))
  temper.log("Has at least 7: " .. tostring(temper.string_hasatleast(s, start, end_, 7)))
  temper.log("Has at least 8: " .. tostring(temper.string_hasatleast(s, start, end_, 8)))
  temper.log("code-points")
  do
    local i = 1
    while temper.string_hasindex(s, i) do
      local cp = temper.string_get(s, i)
      i = temper.string_next(s, i)
      temper.log("  " .. temper.int32_tostring(cp, 16))
    end
  end
end

---@param message string
local function compareEnds(message)
  local start = 1
  local end_ = temper.string_end(message)
  temper.log("start < end? " .. tostring(start - end_ < 0))
  temper.log("start > end? " .. tostring(start - end_ > 0))
  temper.log("start == start? " .. tostring(start == start))
  temper.log("end < end? " .. tostring(end_ - end_ < 0))
  temper.log("end <= end? " .. tostring(end_ - end_ <= 0))
end

---@param code integer
---@return string
local function fromCodeNoInline(code)
  temper.log("From 0x" .. temper.int32_tostring(code, 16))
  return temper.string_fromcodepoint(code)
end

---@param codes integer[]
---@return string
local function fromCodesNoInline(codes)
  temper.log("From codes")
  return temper.string_fromcodepoints(codes)
end

---@param string_ string
---@param radix? integer|temper.Null
local function checkParse(string_, radix)
  if radix == nil then
    radix = temper.null
  end
  ---@type string
  local converted
  if not temper.pcall(function()
    converted = tostring(not temper.is_null(radix) and temper.string_toint32(string_, radix --[[@as integer]]) or temper.string_toint32(string_))
  end) then
    converted = "failed"
  end
  temper.log("Parse " .. string_ .. " -> " .. converted)
end

---@param label string
---@param haystack string
---@param needle string
---@param start? integer|temper.Null
---@return integer
local function reportFound(label, haystack, needle, start)
  if temper.is_null(start) then
    start = 1
  end
  ---@cast start integer
  local index = temper.string_indexof(haystack, needle, start)
  if index >= 1 then
    local before = temper.string_slice(haystack, temper.string_prev(hay, start), index --[[@as integer]])
    temper.log(label .. ": " .. before .. "...")
    return temper.string_next(haystack, index --[[@as integer]])
  else
    temper.log(label .. ": (nope)")
    return 1
  end
end

---@param haystack string
---@param needle string
---@param start? integer|temper.Null
---@return integer
local function foundAt(haystack, needle, start)
  if temper.is_null(start) then
    start = 1
  end
  ---@cast start integer
  local index = temper.string_indexof(haystack, needle, start)
  if index >= 1 then
    return temper.string_countbetween(haystack, 1, index --[[@as integer]])
  else
    return -1
  end
end

reportSliceInfo("κόσμε𐆊")
reportSliceInfo("Hi")
compareEnds("Hi")
temper.log(temper.string_fromcodepoint(127757))
temper.log(fromCodeNoInline(127757))
---@type string
local t
if not temper.pcall(function()
  t = fromCodeNoInline(1114112)
end) then
  t = "nope"
end
temper.log(t)
---@type string
local t_1
if not temper.pcall(function()
  t_1 = fromCodeNoInline(55296)
end) then
  t_1 = "no to surrogate"
end
temper.log(t_1)
temper.log(fromCodesNoInline({ 128059, 8205, 10052, 65039 }))
---@type string
local t_2
if not temper.pcall(function()
  t_2 = fromCodesNoInline({ 55296, 56320 })
end) then
  t_2 = "no to surrogates"
end
temper.log(t_2)
checkParse("-1", temper.null)
checkParse("2", temper.null)
checkParse(" 2 ", temper.null)
checkParse("2.0", temper.null)
checkParse("2a", temper.null)
checkParse("0x10", temper.null)
checkParse("totally", temper.null)
checkParse("2a", 16)
checkParse("20", 7)
checkParse("20", 17)
checkParse("20", 37)
do
  local s = "foo-bar"
  local after1_2 = temper.string_next(s, 1)
  local after2_2 = temper.string_next(s, after1_2)
  local after3 = temper.string_next(s, after2_2)
  local after4 = temper.string_next(s, after3)
  local after4b = temper.string_step(s, 1, 4)
  local before1 = temper.string_prev(s, after4b)
  local before3 = temper.string_step(s, before1, -2)
  local before3b = temper.string_step(s, before3, 0)
  local wayBefore = temper.string_step(s, before3, -100)
  local wayAfter = temper.string_step(s, before3, 100)
  temper.log("s[3:]  -> " .. temper.string_slice(s, after3, temper.string_end(s)))
  temper.log("s[:3]  -> " .. temper.string_slice(s, 1, after3))
  temper.log("s[1:4] -> " .. temper.string_slice(s, after1_2, after4))
  temper.log("s[:4]  -> " .. temper.string_slice(s, 1, after4))
  temper.log("s[1:4] -> " .. temper.string_slice(s, before3b, after4b))
  temper.log("s[-many:many] -> " .. temper.string_slice(s, wayBefore, wayAfter))
end
hay = "It was the best of times, it was the worst of times, ..."
local needle = "time"
local after1 = reportFound("found 1", hay, needle)
local after2 = reportFound("found 2", hay, needle, after1)
reportFound("found 3", hay, needle, after2)
reportFound("hay 2", "time comes for us all", needle)
reportFound("hay 3", "in the nick of time", needle)
reportFound("hay 4", "my name is tim", needle)
reportFound("hay 5", "tim", needle)
local nulHay = "ab\000#\000"
temper.log("nul: " .. foundAt(nulHay, "\000"))
local afterNul = temper.string_next(nulHay, temper.string_next(nulHay, temper.string_next(nulHay, 1)))
temper.log("nul again: " .. foundAt(nulHay, "\000", afterNul))
temper.log("soft hyphen: " .. foundAt("ab\194\173#", "\194\173"))
temper.log("past soft hyphen: " .. foundAt("ab\194\173#", "b#"))
temper.log("zwj: " .. foundAt("a\226\128\141b", "ab"))
temper.log("composed: " .. foundAt("é", "é"))

return {}
