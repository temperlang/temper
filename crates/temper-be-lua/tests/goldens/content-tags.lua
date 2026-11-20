local temper = require("temper-core")

---Gives a class's instances a `__tostring` that calls their `toString`.
---@param class temper.Type
local function with_tostring(class)
  local construct = class.constructor
  ---@type table?
  local meta = nil
  class.constructor = function(this, ...)
    if meta == nil then
      meta = {}
      for k, v in _G.pairs(_G.getmetatable(this)) do
        meta[k] = v
      end
      meta.__tostring = function(self)
        return self:toString()
      end
    end
    _G.setmetatable(this, meta)
    construct(this, ...)
  end
end

---@class content-tags.Mark
---@field label string

---@class content-tags.Mark.type: temper.Type
---@operator call: content-tags.Mark
local Mark = temper.type("content-tags.Mark")

---@param this content-tags.Mark
---@param label string
function Mark.constructor(this, label)
  this.label = label
end

---@class content-tags.Buffer
---@field sb temper.StringBuilder
---@field append fun(self: content-tags.Buffer, x: string)
---@field mark fun(self: content-tags.Buffer, m: content-tags.Mark)
---@field toString fun(self: content-tags.Buffer): string

---@class content-tags.Buffer.type: temper.Type
---@operator call: content-tags.Buffer
local Buffer = temper.type("content-tags.Buffer")

---@param this content-tags.Buffer
function Buffer.constructor(this)
  this.sb = temper.stringbuilder_constructor()
end

---@param this content-tags.Buffer
---@param x string
function Buffer.methods.append(this, x)
  temper.stringbuilder_append(this.sb, x)
end

---@param this content-tags.Buffer
---@param m content-tags.Mark
function Buffer.methods.mark(this, m)
  temper.stringbuilder_append(this.sb, "{" .. m.label .. "}")
end

---@param this content-tags.Buffer
---@return string
function Buffer.methods.toString(this)
  return temper.stringbuilder_tostring(this.sb)
end

with_tostring(Buffer)

---@class content-tags.Effect
---@field enactOn fun(self: content-tags.Effect, c: content-tags.Buffer)

---@class content-tags.Effect.type: temper.Type
local Effect = temper.type("content-tags.Effect")

---@class content-tags.AppendParseEffect: content-tags.Effect
---@field text string
---@field enactOn fun(self: content-tags.AppendParseEffect, c: content-tags.Buffer)

---@class content-tags.AppendParseEffect.type: temper.Type
---@operator call: content-tags.AppendParseEffect
local AppendParseEffect = temper.type("content-tags.AppendParseEffect", Effect)

---@param this content-tags.AppendParseEffect
---@param text string
function AppendParseEffect.constructor(this, text)
  this.text = text
end

---@param this content-tags.AppendParseEffect
---@param c content-tags.Buffer
function AppendParseEffect.methods.enactOn(this, c)
  c:append(this.text)
end

---@class content-tags.EventParseEffect: content-tags.Effect
---@field event content-tags.Mark
---@field enactOn fun(self: content-tags.EventParseEffect, c: content-tags.Buffer)

---@class content-tags.EventParseEffect.type: temper.Type
---@operator call: content-tags.EventParseEffect
local EventParseEffect = temper.type("content-tags.EventParseEffect", Effect)

---@param this content-tags.EventParseEffect
---@param event content-tags.Mark
function EventParseEffect.constructor(this, event)
  this.event = event
end

---@param this content-tags.EventParseEffect
---@param c content-tags.Buffer
function EventParseEffect.methods.enactOn(this, c)
  c:mark(this.event)
end

---@class content-tags.After
---@field effects content-tags.Effect[]
---@field stateAfter boolean

---@class content-tags.After.type: temper.Type
---@operator call: content-tags.After
local After = temper.type("content-tags.After")

---@param this content-tags.After
---@param effects content-tags.Effect[]
---@param stateAfter boolean
function After.constructor(this, effects, stateAfter)
  this.effects = effects
  this.stateAfter = stateAfter
end

---@class content-tags.Propagator
---@field quote string

---@class content-tags.Propagator.type: temper.Type
---@operator call: content-tags.Propagator
local Propagator = temper.type("content-tags.Propagator")

---@param this content-tags.Propagator
---@param quote string
function Propagator.constructor(this, quote)
  this.quote = quote
end

---@param problem string
---@param isError boolean
local function ignore(problem, isError)
  if isError then
    print(problem)
  end
end

---@param p content-tags.Propagator
---@param before boolean
---@param known string|temper.Null
---@param same fun(arg0: boolean, arg1: boolean): boolean
---@param callout fun(arg0: string, arg1: boolean)
---@return content-tags.After
local function propagateOver(p, before, known, same, callout)
  if temper.is_null(known) then
    return After({}, before)
  end
  if (known --[[@as string]]) == "!" then
    return After({ EventParseEffect(Mark("bang")) }, before)
  end
  local angles = temper.list_length(temper.string_split(known --[[@as string]], "<"))
  if angles > 1 then
    callout("angle bracket in fixed text", false)
  end
  if not same(before, before) then
    callout("states differ from themselves", true)
  end
  local quotes = temper.int32_sub(temper.list_length(temper.string_split(known --[[@as string]], p.quote)), 1)
  return After({ AppendParseEffect(known --[[@as string]]) }, temper.int32_mod(quotes, 2) == 1 and not before or temper.int32_mod(quotes, 2) ~= 1 and before)
end

---@class content-tags.Escaper
---@field applyString fun(self: content-tags.Escaper, x: string): string

---@class content-tags.Escaper.type: temper.Type
local Escaper = temper.type("content-tags.Escaper")

---@class content-tags.TextEscaper: content-tags.Escaper
---@field applyString fun(self: content-tags.TextEscaper, x: string): string

---@class content-tags.TextEscaper.type: temper.Type
---@field instance content-tags.TextEscaper
---@operator call: content-tags.TextEscaper
local TextEscaper = temper.type("content-tags.TextEscaper", Escaper)

---@param this content-tags.TextEscaper
function TextEscaper.constructor(this)
end

---@param this content-tags.TextEscaper
---@param x string
---@return string
function TextEscaper.methods.applyString(this, x)
  return "[" .. x .. "]"
end

---@class content-tags.QuoteEscaper: content-tags.Escaper
---@field applyString fun(self: content-tags.QuoteEscaper, x: string): string

---@class content-tags.QuoteEscaper.type: temper.Type
---@field instance content-tags.QuoteEscaper
---@operator call: content-tags.QuoteEscaper
local QuoteEscaper = temper.type("content-tags.QuoteEscaper", Escaper)

---@param this content-tags.QuoteEscaper
function QuoteEscaper.constructor(this)
end

---@param this content-tags.QuoteEscaper
---@param x string
---@return string
function QuoteEscaper.methods.applyString(this, x)
  return "(" .. x .. ")"
end

---@class content-tags.Both: content-tags.Escaper
---@field first content-tags.Escaper
---@field second content-tags.Escaper
---@field applyString fun(self: content-tags.Both, x: string): string

---@class content-tags.Both.type: temper.Type
---@operator call: content-tags.Both
local Both = temper.type("content-tags.Both", Escaper)

---@param this content-tags.Both
---@param first content-tags.Escaper
---@param second content-tags.Escaper
function Both.constructor(this, first, second)
  this.first = first
  this.second = second
end

---@param this content-tags.Both
---@param x string
---@return string
function Both.methods.applyString(this, x)
  return this.second:applyString(this.first:applyString(x))
end

---@class content-tags.Picker
---@field strict boolean
---@field escaperFor fun(self: content-tags.Picker, inQuote: boolean, callout: fun(arg0: string, arg1: boolean)): content-tags.Escaper

---@class content-tags.Picker.type: temper.Type
---@operator call: content-tags.Picker
local Picker = temper.type("content-tags.Picker")

---@param this content-tags.Picker
---@param strict boolean
function Picker.constructor(this, strict)
  this.strict = strict
end

---@param this content-tags.Picker
---@param inQuote boolean
---@param callout fun(arg0: string, arg1: boolean)
---@return content-tags.Escaper
function Picker.methods.escaperFor(this, inQuote, callout)
  if this.strict and inQuote then
    callout("a value in quotes", false)
  end
  if inQuote then
    return Both(TextEscaper.instance, QuoteEscaper.instance)
  else
    return TextEscaper.instance
  end
end

---@class content-tags.ContextualAutoescapingAccumulator
---@field accumulated string

---@class content-tags.ContextualAutoescapingAccumulator.type: temper.Type
local ContextualAutoescapingAccumulator = temper.type("content-tags.ContextualAutoescapingAccumulator")

---@class content-tags.Quoting: content-tags.ContextualAutoescapingAccumulator
---@field inQuote boolean
---@field collector content-tags.Buffer
---@field appendSafe fun(self: content-tags.Quoting, x: string)
---@field append fun(self: content-tags.Quoting, x: string)
---@field accumulated string

---@class content-tags.Quoting.type: temper.Type
---@operator call: content-tags.Quoting
local Quoting = temper.type("content-tags.Quoting", ContextualAutoescapingAccumulator)

---@return content-tags.Buffer
function Quoting.newCollector()
  return Buffer()
end

---@return boolean
function Quoting.initialState()
  return false
end

---@return content-tags.Propagator
function Quoting.propagator()
  return Propagator("'")
end

---@return content-tags.Picker
function Quoting.picker()
  return Picker(false)
end

---@return fun(arg0: boolean, arg1: boolean): boolean
function Quoting.sameStatePredicate()
  return function(a, b) return a == b end
end

---@param a boolean
---@param b boolean
---@param callout fun(arg0: string, arg1: boolean)
---@return boolean
function Quoting.mergeStates(a, b, callout)
  if a ~= b then
    callout("branches end in different contexts", true)
  end
  return a
end

---@param inQuote boolean
---@param callout fun(arg0: string, arg1: boolean)
function Quoting.checkEndState(inQuote, callout)
  if inQuote then
    callout("ends inside quotes", true)
  end
end

---@param c content-tags.Buffer
---@return string
function Quoting.fromCollector(c)
  return c:toString()
end

---@param m content-tags.Mark
---@param c content-tags.Buffer
function Quoting.enact(m, c)
  c:mark(m)
end

---@param this content-tags.Quoting
function Quoting.constructor(this)
  this.inQuote = false
  this.collector = Buffer()
end

---@param this content-tags.Quoting
---@param x string
function Quoting.methods.appendSafe(this, x)
  local after = propagateOver(Quoting.propagator(), this.inQuote, x, Quoting.sameStatePredicate(), ignore)
  this.inQuote = after.stateAfter
  do
    local list = after.effects
    local length = temper.list_length(list)
    local i = 0
    while i < length do
      local e = temper.list_get(list, i)
      i = temper.int32_add(i, 1)
      e:enactOn(this.collector)
    end
  end
end

---@param this content-tags.Quoting
---@param x string
function Quoting.methods.append(this, x)
  local escaped = Quoting.picker():escaperFor(this.inQuote, ignore):applyString(x)
  this.collector:append(escaped)
end

---@param this content-tags.Quoting
---@return string
function Quoting.get.accumulated(this)
  return this.collector:toString()
end

---@param name string
---@param who string
---@return string
local function greet(name, who)
  do
    local collector = Quoting.newCollector()
    collector:append("say 'hi ")
    collector:append(QuoteEscaper.instance:applyString(TextEscaper.instance:applyString(name)))
    collector:append("' to ")
    collector:append(TextEscaper.instance:applyString(who))
    Quoting.enact(Mark("bang"), collector)
    return Quoting.fromCollector(collector)
  end
end

---@param c boolean
---@param x string
---@return string
local function branch(c, x)
  do
    local collector = Quoting.newCollector()
    collector:append(TextEscaper.instance:applyString(x))
    if c then
      collector:append("''")
    end
    collector:append(" '")
    collector:append(QuoteEscaper.instance:applyString(TextEscaper.instance:applyString(x)))
    collector:append("'")
    return Quoting.fromCollector(collector)
  end
end

---@param xs string[]
---@return string
local function each(xs)
  do
    local collector = Quoting.newCollector()
    do
      local length = temper.list_length(xs)
      local i = 0
      while i < length do
        local x = temper.list_get(xs, i)
        i = temper.int32_add(i, 1)
        collector:append("'")
        collector:append(QuoteEscaper.instance:applyString(TextEscaper.instance:applyString(x)))
        collector:append("' ")
        collector:append(TextEscaper.instance:applyString(x))
        collector:append(";")
      end
    end
    return Quoting.fromCollector(collector)
  end
end

---@param n integer
---@param x string
---@return string
local function counted(n, x)
  do
    local collector = Quoting.newCollector()
    do
      local i = 0
      while i < n do
        local jump
        repeat
          i = temper.int32_add(i, 1)
          if i == 2 then
            break
          end
          collector:append("'")
          collector:append(QuoteEscaper.instance:applyString(TextEscaper.instance:applyString(x)))
          collector:append("'")
          if i == 4 then
            jump = "break"
            break
          end
          collector:append(TextEscaper.instance:applyString(x))
          collector:append(",")
        until true
        if jump == "break" then
          break
        end
      end
    end
    return Quoting.fromCollector(collector)
  end
end

---@param c boolean
---@param x string
---@return string
local function attribute(c, x)
  do
    local collector = Quoting.newCollector()
    collector:append("a='")
    if c then
      return "none"
    end
    collector:append(QuoteEscaper.instance:applyString(TextEscaper.instance:applyString(x)))
    collector:append("'")
    return Quoting.fromCollector(collector)
  end
end

---@param xs string[]
---@return string
local function opened(xs)
  do
    local accumulator = Quoting()
    do
      local length = temper.list_length(xs)
      local i = 0
      while i < length do
        local x = temper.list_get(xs, i)
        i = temper.int32_add(i, 1)
        accumulator:appendSafe("'")
        accumulator:append(x)
      end
    end
    return accumulator.accumulated
  end
end

TextEscaper.instance = TextEscaper()
QuoteEscaper.instance = QuoteEscaper()
print(greet("Ann", "Bob"))
print(branch(true, "a"))
print(branch(false, "b"))
print(each({ "c", "d" }))
print(counted(5, "e"))
print(attribute(false, "f") .. " " .. attribute(true, "f"))
print(opened({ "g", "h", "i" }))

return {
  Mark = Mark,
  Buffer = Buffer,
  Effect = Effect,
  AppendParseEffect = AppendParseEffect,
  EventParseEffect = EventParseEffect,
  After = After,
  Propagator = Propagator,
  propagateOver = propagateOver,
  Escaper = Escaper,
  TextEscaper = TextEscaper,
  QuoteEscaper = QuoteEscaper,
  Both = Both,
  Picker = Picker,
  ContextualAutoescapingAccumulator = ContextualAutoescapingAccumulator,
  Quoting = Quoting,
}
