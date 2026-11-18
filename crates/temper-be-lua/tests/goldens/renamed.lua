local temper = require("temper-core")

---@type integer
local native

---@param n integer
---@return integer
local function lambda(n)
  return temper.int32_add(n, 1)
end

---@class renamed.Checked
---@field end_ integer
---@field delete fun(self: renamed.Checked): integer
---@field local_ integer

---@class renamed.Checked.type: temper.Type
---@operator call: renamed.Checked
local Checked = temper.type("renamed.Checked")

---@param this renamed.Checked
---@param end_ integer
function Checked.constructor(this, end_)
  this.end_ = end_
end

---@param this renamed.Checked
---@return integer
function Checked.methods.delete(this)
  return temper.int32_mul(this.end_, 2)
end

---@param this renamed.Checked
---@return integer
function Checked.get.local_(this)
  return temper.int32_add(this.end_, native)
end

---@class renamed.Builtins
---@field hash integer
---@field get fun(self: renamed.Builtins, i: integer): integer
---@field set fun(self: renamed.Builtins, i: integer): integer
---@field map fun(self: renamed.Builtins, i: integer): integer
---@field format fun(self: renamed.Builtins): string
---@field int integer
---@field later fun(self: renamed.Builtins): integer
---@field init fun(self: renamed.Builtins): integer
---@field print fun(self: renamed.Builtins): string

---@class renamed.Builtins.type: temper.Type
---@operator call: renamed.Builtins
local Builtins = temper.type("renamed.Builtins")

---@param this renamed.Builtins
---@param hash integer
function Builtins.constructor(this, hash)
  this.hash = hash
end

---@param this renamed.Builtins
---@param i integer
---@return integer
function Builtins.methods.get(this, i)
  return temper.int32_add(this.hash, i)
end

---@param this renamed.Builtins
---@param i integer
---@return integer
function Builtins.methods.set(this, i)
  return temper.int32_mul(i, 2)
end

---@param this renamed.Builtins
---@param i integer
---@return integer
function Builtins.methods.map(this, i)
  return temper.int32_add(i, 1)
end

---@param this renamed.Builtins
---@return string
function Builtins.methods.format(this)
  return "#" .. this.hash
end

---@param this renamed.Builtins
---@return integer
function Builtins.get.int(this)
  return temper.int32_mul(this.hash, 10)
end

---@param this renamed.Builtins
---@return integer
function Builtins.methods.later(this)
  return temper.int32_add(this.int, 1)
end

---@param this renamed.Builtins
---@return integer
function Builtins.methods.init(this)
  return temper.int32_add(this.hash, 3)
end

---@param this renamed.Builtins
---@return string
function Builtins.methods.print(this)
  return "print " .. this:init()
end

native = 2

return {
  lambda = lambda,
  native = native,
  Checked = Checked,
  Builtins = Builtins,
}
