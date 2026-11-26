local temper = require("temper-core")

---@class mutually-referencing-types.Bar
---@field x mutually-referencing-types.Foo|temper.Null

---@class mutually-referencing-types.Bar.type: temper.Type
---@operator call: mutually-referencing-types.Bar
local Bar = temper.type("mutually-referencing-types.Bar")

---@param this mutually-referencing-types.Bar
---@param x mutually-referencing-types.Foo|temper.Null
function Bar.constructor(this, x)
  this.x = x
end

---@class mutually-referencing-types.Foo
---@field x mutually-referencing-types.Bar|temper.Null

---@class mutually-referencing-types.Foo.type: temper.Type
---@operator call: mutually-referencing-types.Foo
local Foo = temper.type("mutually-referencing-types.Foo")

---@param this mutually-referencing-types.Foo
---@param x mutually-referencing-types.Bar|temper.Null
function Foo.constructor(this, x)
  this.x = x
end

local emptyFoo = Foo(temper.null)
local emptyBar = Bar(temper.null)
local nonEmptyFoo = Foo(emptyBar)
local nonEmptyBar = Bar(emptyFoo)
local stuffedBar = Bar(nonEmptyFoo)
temper.log("emptyFoo.x has Foo -> " .. tostring(not temper.is_null(emptyFoo.x)))
temper.log("nonEmptyFoo.x has Foo -> " .. tostring(not temper.is_null(nonEmptyFoo.x)))

return {}
