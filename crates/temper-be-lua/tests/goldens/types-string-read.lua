local temper = require("temper-core")
local int64_neg1 = temper.int64_constructor(-1)

---@type string
local hay

---@param s string
local function reportSliceInfo(s)
  local start = 1
  local end_ = temper.string_end(s)
  local count = temper.string_countbetween(s, start, end_)
  print("Count of code-points for `" .. s .. "`: " .. count)
  print("Has at least 0: " .. tostring(temper.string_hasatleast(s, start, end_, 0)))
  print("Has at least 1: " .. tostring(temper.string_hasatleast(s, start, end_, 1)))
  print("Has at least 2: " .. tostring(temper.string_hasatleast(s, start, end_, 2)))
  print("Has at least 3: " .. tostring(temper.string_hasatleast(s, start, end_, 3)))
  print("Has at least 4: " .. tostring(temper.string_hasatleast(s, start, end_, 4)))
  print("Has at least 5: " .. tostring(temper.string_hasatleast(s, start, end_, 5)))
  print("Has at least 6: " .. tostring(temper.string_hasatleast(s, start, end_, 6)))
  print("Has at least 7: " .. tostring(temper.string_hasatleast(s, start, end_, 7)))
  print("Has at least 8: " .. tostring(temper.string_hasatleast(s, start, end_, 8)))
  print("code-points")
  do
    local i = 1
    while temper.string_hasindex(s, i) do
      local cp = temper.string_get(s, i)
      i = temper.string_next(s, i)
      print("  " .. temper.int32_tostring(cp, 16))
    end
  end
end

---@param message string
local function compareEnds(message)
  local start = 1
  local end_ = temper.string_end(message)
  print("start < end? " .. tostring(start - end_ < 0))
  print("start > end? " .. tostring(start - end_ > 0))
  print("start == start? " .. tostring(start == start))
  print("end < end? " .. tostring(end_ - end_ < 0))
  print("end <= end? " .. tostring(end_ - end_ <= 0))
end

---@param code integer
---@return string
local function fromCodeNoInline(code)
  print("From 0x" .. temper.int32_tostring(code, 16))
  return temper.string_fromcodepoint(code)
end

---@param codes integer[]
---@return string
local function fromCodesNoInline(codes)
  print("From codes")
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
  if not temper.catch(function()
    converted = tostring(not temper.is_null(radix) and temper.string_toint32(string_, radix --[[@as integer]]) or temper.string_toint32(string_))
  end, temper.ParseError) then
    converted = "failed"
  end
  print("Parse " .. string_ .. " -> " .. converted)
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
    print(label .. ": " .. before .. "...")
    return temper.string_next(haystack, index --[[@as integer]])
  else
    print(label .. ": (nope)")
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
print(temper.string_fromcodepoint(127757))
print(fromCodeNoInline(127757))
---@type string
local t
if not temper.catch(function()
  t = fromCodeNoInline(1114112)
end, temper.InvalidCodePoint) then
  t = "nope"
end
print(t)
---@type string
local t_1
if not temper.catch(function()
  t_1 = fromCodeNoInline(55296)
end, temper.InvalidCodePoint) then
  t_1 = "no to surrogate"
end
print(t_1)
print(fromCodesNoInline({ 128059, 8205, 10052, 65039 }))
---@type string
local t_2
if not temper.catch(function()
  t_2 = fromCodesNoInline({ 55296, 56320 })
end, temper.InvalidCodePoint) then
  t_2 = "no to surrogates"
end
print(t_2)
---@type string
local t_3
local t_4, e = temper.catch(function()
  t_3 = fromCodesNoInline({ 65, -1, 56320 })
end, temper.InvalidCodePoint)
if not t_4 then
  t_3 = temper.int32_tostring(e.codePoint, 16)
end
print(t_3)
---@type string
local t_5
local t_6, e2 = temper.catch(function()
  t_5 = temper.string_fromcodepoint(57343)
end, temper.InvalidCodePoint)
if not t_6 then
  t_5 = e2.message
end
print(t_5)
---@type string
local t_7
local t_8, e3 = temper.catch(function()
  t_7 = tostring(temper.string_toint32("x"))
end, temper.ParseError)
if not t_8 then
  t_7 = e3.message
end
print(t_7)
---@type string
local t_9
local t_10, e4 = temper.catch(function()
  t_9 = tostring(temper.float64_toint32(1e300))
end, temper.ParseError)
if not t_10 then
  t_9 = e4.reason
end
print(t_9)
---@type integer
local t_11
if not temper.catch(function()
  t_11 = temper.string_toint64("12")
end, temper.ParseError) then
  t_11 = int64_neg1
end
print(temper.int64_tostring(t_11))
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
checkParse("10", 0)
checkParse("0", 1)
---@type string
local t_12
local t_13, e5 = temper.catch(function()
  t_12 = temper.int64_tostring(temper.string_toint64("10", 0))
end, temper.ParseError)
if not t_13 then
  t_12 = e5.reason
end
print(t_12)
---@type integer
local t_14
if not temper.catch(function()
  t_14 = temper.string_toint64("10", -2)
end, temper.ParseError) then
  t_14 = int64_neg1
end
print(temper.int64_tostring(t_14))
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
  print("s[3:]  -> " .. temper.string_slice(s, after3, temper.string_end(s)))
  print("s[:3]  -> " .. temper.string_slice(s, 1, after3))
  print("s[1:4] -> " .. temper.string_slice(s, after1_2, after4))
  print("s[:4]  -> " .. temper.string_slice(s, 1, after4))
  print("s[1:4] -> " .. temper.string_slice(s, before3b, after4b))
  print("s[-many:many] -> " .. temper.string_slice(s, wayBefore, wayAfter))
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
print("nul: " .. foundAt(nulHay, "\000"))
local afterNul = temper.string_next(nulHay, temper.string_next(nulHay, temper.string_next(nulHay, 1)))
print("nul again: " .. foundAt(nulHay, "\000", afterNul))
print("soft hyphen: " .. foundAt("ab\194\173#", "\194\173"))
print("past soft hyphen: " .. foundAt("ab\194\173#", "b#"))
print("zwj: " .. foundAt("a\226\128\141b", "ab"))
print("composed: " .. foundAt("é", "é"))

return {}
