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
  if not temper.pcall(function()
    t = positive(n)
  end) then
    error("panic")
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
  temper.log(tostring(temper.is_null(maybe)))
  maybe = 3
  temper.log(tostring(not temper.is_null(maybe)))
end

temper.log(describe(temper.null))
temper.log(describe(41))
local subject = firstWord("hi")
temper.log(temper.is_null(subject) and "nothing" or (subject --[[@as string]]))
local subject2 = firstWord("")
temper.log(temper.is_null(subject2) and "nothing" or (subject2 --[[@as string]]))
temper.log(tostring(same(temper.null, temper.null)) .. " " .. tostring(same("a", temper.null)) .. " " .. tostring(same(temper.null, "a")) .. " " .. tostring(same("a", "a")))
temper.log(tostring(sameInt(1, 2)) .. " " .. tostring(sameInt(3, 3)) .. " " .. tostring(sameInt(temper.null, 3)))
temper.log(parse("12") .. " " .. parse("x"))
temper.log(halved("12", 2) .. " " .. halved("x", 2) .. " " .. halved("12", 0))
temper.log(tostring(checked(7)))
temper.log(orZero(temper.null) .. " " .. orZero(5))
varying()
local list = Node(1, Node(2, Node(3, temper.null)))
temper.log(tostring(list:last()))
local tail = list.next
if not temper.is_null(tail) then
  temper.log(tostring((tail --[[@as nullables.Node]]).value))
end
if not temper.pcall(function()
  positive(-1)
end) then
  temper.log("negative")
end
---@type integer
local fallback
if not temper.pcall(function()
  fallback = positive(-2)
end) then
  fallback = 0
end
temper.log(tostring(fallback))
temper.log(reason(1, temper.null) .. " " .. reason(-1, temper.null) .. " " .. reason(-1, "bad"))

return {
  Node = Node,
  describe = describe,
  firstWord = firstWord,
  stops = stops,
}
