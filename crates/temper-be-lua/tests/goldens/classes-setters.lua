local temper = require("temper-core")

---@class setters.Foo
---@field backing string
---@field bar string

---@class setters.Foo.type: temper.Type
---@operator call: setters.Foo
local Foo = temper.type("setters.Foo")

---@param this setters.Foo
---@param backing string
function Foo.constructor(this, backing)
  this.backing = backing
end

---@param this setters.Foo
---@return string
function Foo.get.bar(this)
  return this.backing
end

---@param this setters.Foo
---@param value string
function Foo.set.bar(this, value)
  this.backing = value
end

---@class setters.Bar
---@field backing string
---@field bar string
---@field usingSet fun(self: setters.Bar, value: string)

---@class setters.Bar.type: temper.Type
---@operator call: setters.Bar
local Bar = temper.type("setters.Bar")

---@param this setters.Bar
---@param backing string
function Bar.constructor(this, backing)
  this.backing = backing
end

---@param this setters.Bar
---@return string
function Bar.get.bar(this)
  return this.backing
end

---@param this setters.Bar
---@param value string
function Bar.set.bar(this, value)
  this.backing = value
end

---@param this setters.Bar
---@param value string
function Bar.methods.usingSet(this, value)
  this.bar = value
end

---@class setters.Whatever
---@field something integer

---@class setters.Whatever.type: temper.Type
local Whatever = temper.type("setters.Whatever")

---@class setters.ConcreteWhatever: setters.Whatever
---@field something integer

---@class setters.ConcreteWhatever.type: temper.Type
---@operator call: setters.ConcreteWhatever
local ConcreteWhatever = temper.type("setters.ConcreteWhatever", Whatever)

---@param this setters.ConcreteWhatever
---@param something integer
function ConcreteWhatever.constructor(this, something)
  this.something = something
end

temper.log(Foo("initial").bar)
local foo = Foo("initial")
foo.bar = "next"
temper.log(foo.bar)
local bar = Bar("initial")
bar:usingSet("next")
temper.log(bar.bar)
local what = ConcreteWhatever(4)
what.something = 5
temper.log("something set to " .. what.something)

return { Whatever = Whatever, ConcreteWhatever = ConcreteWhatever }
