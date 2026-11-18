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

---@class private-method.C
---@field helper fun(self: private-method.C, x: integer): integer
---@field method1 fun(self: private-method.C, x: integer): integer
---@field method2 fun(self: private-method.C, x: integer): integer

---@class private-method.C.type: temper.Type
---@operator call: private-method.C
local C = temper.type("private-method.C")

---@param this private-method.C
function C.constructor(this)
end

---@param this private-method.C
---@param x integer
---@return integer
function C.methods.helper(this, x)
  return temper.int32_add(x, 1)
end

---@param this private-method.C
---@param x integer
---@return integer
function C.methods.method1(this, x)
  return this:helper(temper.int32_mul(x, 2))
end

---@param this private-method.C
---@param x integer
---@return integer
function C.methods.method2(this, x)
  return this:helper(temper.int32_mul(x, 3))
end

---@class private-method.Money
---@field cents integer
---@field spent integer
---@field whole fun(self: private-method.Money): integer
---@field rounded integer
---@field plus fun(self: private-method.Money, other: private-method.Money): private-method.Money
---@field sameWhole fun(self: private-method.Money, other: private-method.Money): boolean
---@field sameRounded fun(self: private-method.Money, other: private-method.Money): boolean
---@field spendFrom fun(self: private-method.Money, other: private-method.Money)
---@field timesSpent integer
---@field toString fun(self: private-method.Money): string

---@class private-method.Money.type: temper.Type
---@operator call: private-method.Money
local Money = temper.type("private-method.Money")

---@param this private-method.Money
---@param cents integer
function Money.constructor(this, cents)
  this.cents = cents
  this.spent = 0
end

---@param this private-method.Money
---@return integer
function Money.methods.whole(this)
  return temper.int32_div(this.cents, 100)
end

---@param this private-method.Money
---@return integer
function Money.get.rounded(this)
  return temper.int32_div(temper.int32_add(this.cents, 50), 100)
end

---@param this private-method.Money
---@param other private-method.Money
---@return private-method.Money
function Money.methods.plus(this, other)
  return Money(temper.int32_add(this.cents, other.cents))
end

---@param this private-method.Money
---@param other private-method.Money
---@return boolean
function Money.methods.sameWhole(this, other)
  return this:whole() == other:whole()
end

---@param this private-method.Money
---@param other private-method.Money
---@return boolean
function Money.methods.sameRounded(this, other)
  return this.rounded == other.rounded
end

---@param this private-method.Money
---@param other private-method.Money
function Money.methods.spendFrom(this, other)
  other.spent = temper.int32_add(other.spent, 1)
end

---@param this private-method.Money
---@return integer
function Money.get.timesSpent(this)
  return this.spent
end

---@param this private-method.Money
---@return string
function Money.methods.toString(this)
  return this.cents .. " cents"
end

with_tostring(Money)

---@class private-method.Sink
---@field end_ fun(self: private-method.Sink): integer

---@class private-method.Sink.type: temper.Type
local Sink = temper.type("private-method.Sink")

---@class private-method.TenSink: private-method.Sink
---@field end_ fun(self: private-method.TenSink): integer

---@class private-method.TenSink.type: temper.Type
---@operator call: private-method.TenSink
local TenSink = temper.type("private-method.TenSink", Sink)

---@param this private-method.TenSink
function TenSink.constructor(this)
end

---@param this private-method.TenSink
---@return integer
function TenSink.methods.end_(this)
  return 10
end

---@class private-method.Note
---@field body string
---@field text fun(self: private-method.Note, more: string): private-method.Note

---@class private-method.Note.type: temper.Type
---@operator call: private-method.Note
local Note = temper.type("private-method.Note")

---@param this private-method.Note
---@param body string
function Note.constructor(this, body)
  this.body = body
end

---@param this private-method.Note
---@param more string
---@return private-method.Note
function Note.methods.text(this, more)
  return Note(this.body .. more)
end

---@class private-method.Label
---@field end_ integer
---@field text string
---@field close fun(self: private-method.Label, sink: private-method.Sink): integer
---@field noted fun(self: private-method.Label, more: string): string

---@class private-method.Label.type: temper.Type
---@operator call: private-method.Label
local Label = temper.type("private-method.Label")

---@param this private-method.Label
---@param end_ integer
function Label.constructor(this, end_)
  this.end_ = end_
end

---@param this private-method.Label
---@return string
function Label.get.text(this)
  return "label"
end

---@param this private-method.Label
---@param sink private-method.Sink
---@return integer
function Label.methods.close(this, sink)
  return temper.int32_add(sink:end_(), this.end_)
end

---@param this private-method.Label
---@param more string
---@return string
function Label.methods.noted(this, more)
  return Note(this.text):text(more).body
end

---@class private-method.Tally
---@field depth integer
---@field size integer
---@field total integer

---@class private-method.Tally.type: temper.Type
---@operator call: private-method.Tally
local Tally = temper.type("private-method.Tally")

---@param this private-method.Tally
---@param size integer
---@param parent private-method.Tally|temper.Null
---@param children private-method.Tally[]
function Tally.constructor(this, size, parent, children)
  this.depth = temper.is_null(parent) and 0 or temper.int32_add((parent --[[@as private-method.Tally]]).depth, 1)
  local sum = size
  do
    local i = 0
    while i < temper.list_length(children) do
      sum = temper.int32_add(sum, temper.list_get(children, i).size)
      i = temper.int32_add(i, 1)
    end
  end
  this.size = temper.int32_add(sum, this.depth)
end

---@param this private-method.Tally
---@return integer
function Tally.get.total(this)
  return this.size
end

local c = C()
print("method1(10)=" .. c:method1(10))
print("method2(10)=" .. c:method2(10))
local a = Money(150)
local b = Money(175)
print("plus: " .. a:plus(b):toString())
print("same whole: " .. tostring(a:sameWhole(b)))
print("same rounded: " .. tostring(a:sameRounded(b)))
a:spendFrom(b)
a:spendFrom(b)
print("b spent: " .. b.timesSpent)
local label = Label(1)
print("close: " .. label:close(TenSink()))
print("noted: " .. label:noted("!"))
local leaf = Tally(2, temper.null, {})
local mid = Tally(3, leaf, { leaf, leaf })
local top = Tally(1, mid, { mid, leaf })
print("depths: " .. leaf.depth .. " " .. mid.depth .. " " .. top.depth)
print("totals: " .. leaf.total .. " " .. mid.total .. " " .. top.total)

return {}
