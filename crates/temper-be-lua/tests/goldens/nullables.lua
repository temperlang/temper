local temper = require("temper-core")

---@class nullables.Node
---@field value integer
---@field next nullables.Node|temper.Null
---@field last fun(self: nullables.Node): integer

---@class nullables.Node.type: temper.Type
---@operator call: nullables.Node
local Node = temper.type("nullables.Node")

---@param this nullables.Node
---@param value integer
---@param next_ nullables.Node|temper.Null
function Node.constructor(this, value, next_)
  this.value = value
  this.next = next_
end

---@param this nullables.Node
---@return integer
function Node.methods.last(this)
  local n = this.next
  if temper.is_null(n) then
    return this.value
  else
    return (n --[[@as nullables.Node]]):last()
  end
end

---@param x integer|temper.Null
---@return string
local function describe(x)
  if temper.is_null(x) then
    return "none"
  end
  return "some " .. temper.int32_add(x --[[@as integer]], 1)
end

---@param s string|temper.Null
---@return string|temper.Null
local function firstWord(s)
  if not temper.is_null(s) and (s --[[@as string]]) ~= "" then
    return s
  else
    return temper.null
  end
end

---@param a string|temper.Null
---@param b string|temper.Null
---@return boolean
local function same(a, b)
  return a == b
end

---@param a integer|temper.Null
---@param b integer|temper.Null
---@return boolean
local function sameInt(a, b)
  return a == b
end

---@param s string
---@return integer
local function parse(s)
  ---@type integer
  local t
  if not temper.pcall(function()
    t = temper.string_toint32(s)
  end) then
    t = -1
  end
  return t
end

---@param s string
---@param d integer
---@return integer
local function halved(s, d)
  ---@type integer
  local t
  if not temper.pcall(function()
    t = temper.int32_div(temper.string_toint32(s), d)
  end) then
    t = -1
  end
  return t
end

---@param n integer
---@return integer
local function positive(n)
  if n < 0 then
    return temper.bubble()
  end
  return n
end

---@param n integer
---@return integer
local function checked(n)
  ---@type integer
  local t
  local t_1, t_2 = temper.pcall(function()
    t = positive(n)
  end)
  if not t_1 then
    error("panic\ncaused by: " .. tostring(t_2))
  end
  return t
end

---@return integer
local function stops()
  error("panic")
  return 1
end

---@param x integer|temper.Null
---@return integer
local function orZero(x)
  if temper.is_null(x) then
    return 0
  else
    return x --[[@as integer]]
  end
end

---@param n integer
---@param why string|temper.Null
---@return string
local function reason(n, why)
  ---@type string
  local t
  if not temper.pcall(function()
    positive(n)
    t = "ok"
  end) then
    t = temper.is_null(why) and "fails" or (why --[[@as string]])
  end
  return t
end

local function varying()
  ---@type integer|temper.Null
  local maybe = temper.null
  print(tostring(temper.is_null(maybe)))
  maybe = 3
  print(tostring(not temper.is_null(maybe)))
end

print(describe(temper.null))
print(describe(41))
local subject = firstWord("hi")
print(temper.is_null(subject) and "nothing" or (subject --[[@as string]]))
local subject2 = firstWord("")
print(temper.is_null(subject2) and "nothing" or (subject2 --[[@as string]]))
print(tostring(same(temper.null, temper.null)) .. " " .. tostring(same("a", temper.null)) .. " " .. tostring(same(temper.null, "a")) .. " " .. tostring(same("a", "a")))
print(tostring(sameInt(1, 2)) .. " " .. tostring(sameInt(3, 3)) .. " " .. tostring(sameInt(temper.null, 3)))
print(parse("12") .. " " .. parse("x"))
print(halved("12", 2) .. " " .. halved("x", 2) .. " " .. halved("12", 0))
print(tostring(checked(7)))
print(orZero(temper.null) .. " " .. orZero(5))
varying()
local list = Node(1, Node(2, Node(3, temper.null)))
print(tostring(list:last()))
local tail = list.next
if not temper.is_null(tail) then
  print(tostring((tail --[[@as nullables.Node]]).value))
end
if not temper.pcall(function()
  positive(-1)
end) then
  print("negative")
end
---@type integer
local fallback
if not temper.pcall(function()
  fallback = positive(-2)
end) then
  fallback = 0
end
print(tostring(fallback))
print(reason(1, temper.null) .. " " .. reason(-1, temper.null) .. " " .. reason(-1, "bad"))

return {
  Node = Node,
  describe = describe,
  firstWord = firstWord,
  stops = stops,
}
