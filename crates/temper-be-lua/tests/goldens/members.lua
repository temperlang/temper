local temper = require("temper-core")
local exports = {}

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

---@class members.Sized
---@field size integer
---@field twice fun(self: members.Sized): integer

---@class members.Sized.type: temper.Type
local Sized = temper.type("members.Sized")

---@param this members.Sized
---@return integer
function Sized.methods.twice(this)
  return temper.int32_mul(this.size, 2)
end

---@class members.Box: members.Sized
---@field size integer
---@field bump fun(self: members.Box): integer
---@field positive fun(self: members.Box): boolean
---@field toString fun(self: members.Box): string

---@class members.Box.type: temper.Type
---@field methods_ integer
---@operator call: members.Box
local Box = temper.type("members.Box", Sized)

---@param this members.Box
---@param size integer
function Box.constructor(this, size)
  this.size = size
end

---@param this members.Box
---@return integer
function Box.methods.bump(this)
  exports.made = temper.int32_add(exports.made, 1)
  this.size = temper.int32_add(this.size, 1)
  return this.size
end

---@param this members.Box
---@return boolean
function Box.methods.positive(this)
  exports.made = temper.int32_add(exports.made, 1)
  return this.size > 0
end

---@param this members.Box
---@return string
function Box.methods.toString(this)
  return "Box(" .. this.size .. ")"
end

with_tostring(Box)

---@param n integer
---@return members.Box
local function make(n)
  return Box(n)
end

---@class members.Named
---@field name string

---@class members.Named.type: temper.Type
local Named = temper.type("members.Named")

---@param this members.Named
---@return string
function Named.get.name(this)
  return "anon"
end

---@class members.Pet: members.Named
---@field name string
---@field name_ string

---@class members.Pet.type: temper.Type
---@operator call: members.Pet
local Pet = temper.type("members.Pet", Named)

---@param this members.Pet
---@param name string
function Pet.constructor(this, name)
  this.name_ = name
end

---@param this members.Pet
---@return string
function Pet.get.name(this)
  return this.name_
end

---@param b members.Box
---@return boolean
local function order(b)
  return temper.bool_cmp(true, b:positive()) < 0
end

exports.made = 0
Box.methods_ = 7
local b = make(1)
temper.log(b:bump() .. " " .. b:twice() .. " " .. exports.made)
temper.log(tostring(order(b)) .. " " .. exports.made .. " " .. Box.methods_)
local n = Pet("rex")
temper.log(n.name)

exports.Sized = Sized
exports.Box = Box
exports.Named = Named
exports.Pet = Pet
return exports
