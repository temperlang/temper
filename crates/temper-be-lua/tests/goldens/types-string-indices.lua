local temper = require("temper-core")

---@param s string
---@param c integer
---@return integer
local function indexOfChar(s, c)
  local i = 1
  while temper.string_hasindex(s, i) do
    if temper.string_get(s, i) == c then
      return i
    end
    i = temper.string_next(s, i)
  end
  return -1
end

---@param s string
---@param a integer
---@param b integer
local function comparePositions(s, a, b)
  local delta = indexOfChar(s, a) - indexOfChar(s, b)
  local relPos = delta < 0 and "before" or (delta == 0 and "at" or "after")
  ---@type string
  local t
  if not temper.pcall(function()
    t = temper.string_fromcodepoint(a)
  end) then
    t = "?"
  end
  ---@type string
  local t_1
  if not temper.pcall(function()
    t_1 = temper.string_fromcodepoint(b)
  end) then
    t_1 = "?"
  end
  temper.log("'" .. t .. "' is " .. relPos .. " '" .. t_1 .. "' in \"" .. s .. '".')
end

---@param s string
---@param c integer
---@return boolean
local function has0(s, c)
  return indexOfChar(s, c) >= 1
end

---@param s string
---@param c integer
---@return boolean
local function has1(s, c)
  local i = indexOfChar(s, c)
  return i >= 1
end

---@param s string
---@param c integer
---@return boolean
local function has2(s, c)
  local i = indexOfChar(s, c)
  return i >= 1
end

---@param s string
---@param c integer
---@return boolean
local function has3(s, c)
  local i = indexOfChar(s, c)
  ---@type boolean
  local t
  if not temper.pcall(function()
    temper.require_string_index(i)
    t = true
  end) then
    t = false
  end
  return t
end

---@param s string
---@param c integer
---@return boolean
local function has4(s, c)
  local i = indexOfChar(s, c)
  ---@type boolean
  local t
  if not temper.pcall(function()
    temper.require_no_string_index(i)
    t = false
  end) then
    t = true
  end
  return t
end

comparePositions("cookie", 99, 105)
comparePositions("cookie", 107, 111)
comparePositions("cookie", 107, 107)
comparePositions("cookie", 120, 99)
comparePositions("cookie", 101, 120)
comparePositions("cookie", 120, 121)
local s = "cookie"
do
  local list = { 111, 105, 122 }
  local length = temper.list_length(list)
  local i = 0
  while i < length do
    local c = temper.list_get(list, i)
    i = temper.int32_add(i, 1)
    ---@type string
    local t
    if not temper.pcall(function()
      t = temper.string_fromcodepoint(c)
    end) then
      t = "?"
    end
    temper.log("'" .. t .. "' in \"" .. s .. '": ' .. (has0(s, c) and "y" or "n") .. ", " .. (has1(s, c) and "y" or "n") .. ", " .. (has2(s, c) and "y" or "n") .. ", " .. (has3(s, c) and "y" or "n") .. ", " .. (has4(s, c) and "y" or "n"))
  end
end
do
  local stringBuilder = temper.stringbuilder_constructor()
  temper.stringbuilder_append(stringBuilder, "Hello")
  temper.stringbuilder_append(stringBuilder, ",")
  local afterComma = temper.stringbuilder_end(stringBuilder)
  temper.stringbuilder_append(stringBuilder, " World!")
  local str = temper.stringbuilder_tostring(stringBuilder)
  temper.log(temper.string_slice(str, 1, afterComma))
  temper.log(temper.string_slice(str, afterComma, temper.string_end(str)))
end

return {}
