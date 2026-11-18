local temper = require("temper-core")

---@type integer
local fCalls
---@type integer
local inked

---@return integer
local function f()
  fCalls = temper.int32_add(fCalls, 1)
  return 42
end

---@param x? integer|temper.Null
---@return integer
local function g(x)
  if temper.is_null(x) then
    x = f()
  end
  ---@cast x integer
  return x
end

---@return integer|temper.Null
local function noInline()
  print("noInline")
  return temper.null
end

---@class defaulting.Thing
---@field say fun(self: defaulting.Thing, a?: string|temper.Null, b?: string|temper.Null)

---@class defaulting.Thing.type: temper.Type
---@operator call: defaulting.Thing
local Thing = temper.type("defaulting.Thing")

---@param this defaulting.Thing
function Thing.constructor(this)
end

---@param this defaulting.Thing
---@param a? string|temper.Null
---@param b? string|temper.Null
function Thing.methods.say(this, a, b)
  if temper.is_null(a) then
    a = ""
  end
  ---@cast a string
  if temper.is_null(b) then
    b = ""
  end
  ---@cast b string
  print("saying " .. a .. b)
end

---@param a string
---@param b? string|temper.Null
function Thing.sayStatic(a, b)
  if temper.is_null(b) then
    b = ""
  end
  ---@cast b string
  print("static " .. a .. b)
end

---@class defaulting.StringHolder
---@field string string

---@class defaulting.StringHolder.type: temper.Type
---@operator call: defaulting.StringHolder
local StringHolder = temper.type("defaulting.StringHolder")

---@param this defaulting.StringHolder
---@param string_ string
function StringHolder.constructor(this, string_)
  this.string = string_
end

---@param a defaulting.StringHolder|temper.Null
---@return string
local function maybeEmpty(a)
  inked = temper.int32_add(inked, 1)
  do
    local subject3 = temper.is_null(a) and temper.null or (a --[[@as defaulting.StringHolder]]).string
    local subject2 = temper.is_null(subject3) and temper.null or (subject3 --[[@as string]]) == ""
    local subject = temper.is_null(subject2) and temper.null or tostring(subject2 --[[@as boolean]])
    if temper.is_null(subject) then
      return "null"
    else
      return subject --[[@as string]]
    end
  end
end

---@param a string|temper.Null
---@return integer|temper.Null
local function maybeLength(a)
  inked = temper.int32_add(inked, 1)
  if temper.is_null(a) then
    return temper.null
  else
    return temper.string_countbetween(a --[[@as string]], 1, temper.string_end(a --[[@as string]]))
  end
end

---@return integer|temper.Null
local function maybeInc()
  if inked == 10 then
    return temper.null
  else
    inked = temper.int32_add(inked, 1)
    return inked
  end
end

---@param a string|temper.Null
---@return string
local function chainMore(a)
  local subject2 = maybeLength(a)
  local subject = temper.is_null(subject2) and temper.null or math.max(subject2 --[[@as integer]], 0)
  return tostring(temper.is_null(subject) and -1 or (subject --[[@as integer]]))
end

fCalls = 0
print("fCalls=" .. fCalls)
print("g(   )=" .. g())
print("fCalls=" .. fCalls)
print("g(123)=" .. g(123))
print("fCalls=" .. fCalls)
print("g(null)=" .. g(noInline()))
print("fCalls=" .. fCalls)
Thing():say("hi")
Thing():say(temper.null, "there")
Thing():say("by", "e")
Thing.sayStatic("yall")
inked = 0
local subject = maybeInc()
print("maybeInc() -> " .. (temper.is_null(subject) and -1 or (subject --[[@as integer]])))
print("inked: " .. inked)
print('lengthish "ab": ' .. chainMore("ab"))
print("lengthish null: " .. chainMore(temper.null))
print("inked: " .. inked)
print('maybeEmpty "": ' .. maybeEmpty(StringHolder("")))
print("maybeEmpty null: " .. maybeEmpty(temper.null))
print("inked: " .. inked)

return {}
