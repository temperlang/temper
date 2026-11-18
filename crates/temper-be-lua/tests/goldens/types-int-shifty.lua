local temper = require("temper-core")
local int64_0 = temper.int64_constructor(0)
local int64_3 = temper.int64_constructor(3)
local int64_1 = temper.int64_constructor(1)
local int64_2 = temper.int64_constructor(2)
local int64_neg2 = temper.int64_constructor(-2)
local int64_7 = temper.int64_constructor(7)
local int64_297421889964898413 = temper.int64_constructor(69248930, 331905133)
local int64_neg3505189831424144594 = temper.int64_constructor(-816115605, 1806109486)
local int64_neg1 = temper.int64_constructor(-1)
local int64_5 = temper.int64_constructor(5)
local int64_128 = temper.int64_constructor(128)
local int64_neg128 = temper.int64_constructor(-128)

---@param hexString string
---@return string
local function insertUnderscores(hexString)
  local sb = temper.stringbuilder_constructor()
  local idx = 1
  local end_ = temper.string_end(hexString)
  if idx - end_ < 0 then
    local possibleSign = temper.string_get(hexString, idx)
    if possibleSign == 43 or possibleSign == 45 then
      idx = temper.string_next(hexString, idx)
      temper.stringbuilder_appendbetween(sb, hexString, 1, idx)
    end
  end
  local nFirstGroup = temper.int32_mod(temper.string_countbetween(hexString, idx, end_), 4)
  local needsUnderscore = false
  if nFirstGroup ~= 0 then
    local groupStart = idx
    do
      local i = 0
      while i < nFirstGroup do
        idx = temper.string_next(hexString, idx)
        i = temper.int32_add(i, 1)
      end
    end
    temper.stringbuilder_appendbetween(sb, hexString, groupStart, idx)
    needsUnderscore = true
  end
  while idx - end_ < 0 do
    local groupStart = idx
    idx = temper.string_step(hexString, idx, 4)
    if needsUnderscore then
      temper.stringbuilder_append(sb, "_")
    end
    temper.stringbuilder_appendbetween(sb, hexString, groupStart, idx)
    needsUnderscore = true
  end
  return temper.stringbuilder_tostring(sb)
end

---@param n integer
---@return string
local function fmt32(n)
  return insertUnderscores(temper.int32_tostring(n, 16))
end

---@param n integer
---@return string
local function fmt64(n)
  return insertUnderscores(temper.int64_tostring(n, 16))
end

---@param a integer
---@param b integer
local function and32(a, b)
  local c = temper.band(a, b)
  print("0x:" .. fmt32(a) .. " & " .. fmt32(b) .. " == " .. fmt32(c))
end

---@param a integer
---@param b integer
local function and64(a, b)
  local c = temper.band(a, b)
  print("0x:" .. fmt64(a) .. " & " .. fmt64(b) .. " == " .. fmt64(c))
end

---@param a integer
---@param b integer
local function or32(a, b)
  local c = temper.bor(a, b)
  print("0x:" .. fmt32(a) .. " | " .. fmt32(b) .. " == " .. fmt32(c))
end

---@param a integer
---@param b integer
local function or64(a, b)
  local c = temper.bor(a, b)
  print("0x:" .. fmt64(a) .. " | " .. fmt64(b) .. " == " .. fmt64(c))
end

---@param n integer
local function bComp32(n)
  print("0x:~" .. fmt32(n) .. " == " .. fmt32(temper.bnot(n)))
end

---@param n integer
local function bComp64(n)
  print("0x:~" .. fmt64(n) .. " == " .. fmt64(temper.bnot(n)))
end

---@param a integer
---@param b integer
local function shl32(a, b)
  local c = temper.shl32(a, b)
  print("0x:" .. fmt32(a) .. " << " .. b .. " == " .. fmt32(c))
end

---@param a integer
---@param b integer
local function shl64(a, b)
  local c = temper.shl64(a, b)
  print("0x:" .. fmt64(a) .. " << " .. b .. " == " .. fmt64(c))
end

---@param a integer
---@param b integer
local function shr32(a, b)
  local c = temper.shr32(a, b)
  print("0x:" .. fmt32(a) .. " >> " .. b .. " == " .. fmt32(c))
end

---@param a integer
---@param b integer
local function shr64(a, b)
  local c = temper.shr64(a, b)
  print("0x:" .. fmt64(a) .. " >> " .. b .. " == " .. fmt64(c))
end

---@param a integer
---@param b integer
local function uShr32(a, b)
  local c = temper.ushr32(a, b)
  print("0x:" .. fmt32(a) .. " >>> " .. b .. " == " .. fmt32(c))
end

---@param a integer
---@param b integer
local function uShr64(a, b)
  local c = temper.ushr64(a, b)
  print("0x:" .. fmt64(a) .. " >>> " .. b .. " == " .. fmt64(c))
end

print("and32")
and32(0, 0)
and32(3, 1)
and32(2, 3)
and32(-2, 7)
and32(-809434545, 901070595)
print("and64")
and64(int64_0, int64_0)
and64(int64_3, int64_1)
and64(int64_2, int64_3)
and64(int64_neg2, int64_7)
and64(int64_297421889964898413, int64_neg3505189831424144594)
print("or32")
or32(0, 0)
or32(0, 1)
or32(2, 1)
or32(2, 2)
or32(3, 1)
or32(2, 3)
or32(-2, 7)
or32(-809434545, 901070595)
print("or64")
or64(int64_0, int64_0)
or64(int64_0, int64_1)
or64(int64_2, int64_1)
or64(int64_2, int64_2)
or64(int64_3, int64_1)
or64(int64_2, int64_3)
or64(int64_neg2, int64_7)
or64(int64_297421889964898413, int64_neg3505189831424144594)
print("bComp32")
bComp32(0)
bComp32(1)
bComp32(-1)
bComp32(2)
bComp32(-2)
bComp32(-809434545)
bComp32(901070595)
print("bComp64")
bComp64(int64_0)
bComp64(int64_1)
bComp64(int64_neg1)
bComp64(int64_2)
bComp64(int64_neg2)
bComp64(int64_297421889964898413)
bComp64(int64_neg3505189831424144594)
print("shl32")
shl32(5, 0)
shl32(5, 1)
shl32(5, 2)
shl32(5, 3)
shl32(5, 4)
shl32(5, 5)
shl32(5, 20)
shl32(5, 28)
shl32(5, 29)
shl32(5, 30)
shl32(5, 31)
shl32(5, 32)
shl32(5, 33)
shl32(5, -1)
shl32(5, -30)
shl32(-1, 0)
shl32(-1, 1)
shl32(-1, 2)
print("shl64")
shl64(int64_5, 0)
shl64(int64_5, 1)
shl64(int64_5, 2)
shl64(int64_5, 3)
shl64(int64_5, 4)
shl64(int64_5, 5)
shl64(int64_5, 33)
shl64(int64_5, 62)
shl64(int64_5, 63)
shl64(int64_5, 64)
shl64(int64_5, -1)
shl64(int64_5, -60)
shl64(int64_neg1, 0)
shl64(int64_neg1, 1)
shl64(int64_neg1, 2)
print("shr32")
shr32(128, 0)
shr32(128, 1)
shr32(128, 2)
shr32(128, 3)
shr32(128, 4)
shr32(128, 5)
shr32(128, 6)
shr32(128, 7)
shr32(128, 8)
shr32(128, 9)
shr32(128, 31)
shr32(128, 32)
shr32(128, 33)
shr32(128, -1)
shr32(128, -30)
shr32(-128, 0)
shr32(-128, 1)
shr32(-128, 2)
shr32(-128, 3)
shr32(-128, 4)
print("shr64")
shr64(int64_128, 0)
shr64(int64_128, 1)
shr64(int64_128, 2)
shr64(int64_128, 3)
shr64(int64_128, 4)
shr64(int64_128, 5)
shr64(int64_128, 6)
shr64(int64_128, 7)
shr64(int64_128, 8)
shr64(int64_128, 9)
shr64(int64_128, 32)
shr64(int64_128, 63)
shr64(int64_128, 64)
shr64(int64_128, 65)
shr64(int64_128, -1)
shr64(int64_128, -62)
shr64(int64_neg128, 0)
shr64(int64_neg128, 1)
shr64(int64_neg128, 2)
shr64(int64_neg128, 3)
shr64(int64_neg128, 4)
print("uShr32")
uShr32(128, 0)
uShr32(128, 1)
uShr32(128, 2)
uShr32(128, 3)
uShr32(128, 4)
uShr32(128, 5)
uShr32(128, 6)
uShr32(128, 7)
uShr32(128, 8)
uShr32(128, 9)
uShr32(128, 31)
uShr32(128, 32)
uShr32(128, 33)
uShr32(128, -1)
uShr32(128, -30)
uShr32(-128, 0)
uShr32(-128, 1)
uShr32(-128, 2)
uShr32(-128, 3)
uShr32(-128, 4)
print("uShr64")
uShr64(int64_128, 0)
uShr64(int64_128, 1)
uShr64(int64_128, 2)
uShr64(int64_128, 3)
uShr64(int64_128, 4)
uShr64(int64_128, 5)
uShr64(int64_128, 6)
uShr64(int64_128, 7)
uShr64(int64_128, 8)
uShr64(int64_128, 9)
uShr64(int64_128, 32)
uShr64(int64_128, 63)
uShr64(int64_128, 64)
uShr64(int64_128, 65)
uShr64(int64_128, -1)
uShr64(int64_128, -62)
uShr64(int64_neg128, 0)
uShr64(int64_neg128, 1)
uShr64(int64_neg128, 2)
uShr64(int64_neg128, 3)
uShr64(int64_neg128, 4)

return {}
