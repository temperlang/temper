local temper = require("temper-core")

---@class write-back-through-field.Ping
---@field peer write-back-through-field.Pong
---@field n integer
---@field most integer
---@field ping fun(self: write-back-through-field.Ping): integer
---@field pass fun(self: write-back-through-field.Ping): integer
---@field record fun(self: write-back-through-field.Ping): boolean
---@field around fun(self: write-back-through-field.Ping): integer

---@class write-back-through-field.Ping.type: temper.Type
---@operator call: write-back-through-field.Ping
local Ping = temper.type("write-back-through-field.Ping")

---@param this write-back-through-field.Ping
---@param peer write-back-through-field.Pong
function Ping.constructor(this, peer)
  this.peer = peer
  this.n = 0
  this.most = 0
end

---@param this write-back-through-field.Ping
---@return integer
function Ping.methods.ping(this)
  this.peer:pong(this)
  return this.n
end

---@param this write-back-through-field.Ping
---@return integer
function Ping.methods.pass(this)
  return this.peer:bump(this, this.n)
end

---@param this write-back-through-field.Ping
---@return boolean
function Ping.methods.record(this)
  this.n = temper.int32_add(this.n, 1)
  if this.n > this.most then
    this.most = this.n
  end
  return this.n > temper.int32_sub(this.most, 1)
end

---@param this write-back-through-field.Ping
---@return integer
function Ping.methods.around(this)
  return temper.int32_add(temper.int32_add(this.n, this.peer:bump(this, 10)), this.n)
end

---@class write-back-through-field.Pong
---@field pong fun(self: write-back-through-field.Pong, a: write-back-through-field.Ping)
---@field bump fun(self: write-back-through-field.Pong, a: write-back-through-field.Ping, by: integer): integer

---@class write-back-through-field.Pong.type: temper.Type
---@operator call: write-back-through-field.Pong
local Pong = temper.type("write-back-through-field.Pong")

---@param this write-back-through-field.Pong
function Pong.constructor(this)
end

---@param this write-back-through-field.Pong
---@param a write-back-through-field.Ping
function Pong.methods.pong(this, a)
  a.n = temper.int32_add(a.n, 1)
end

---@param this write-back-through-field.Pong
---@param a write-back-through-field.Ping
---@param by integer
---@return integer
function Pong.methods.bump(this, a, by)
  a.n = temper.int32_add(a.n, by)
  return a.n
end

local p = Ping(Pong())
print("ping=" .. p:ping())
print("pass=" .. p:pass())
print("record=" .. tostring(p:record()) .. " most=" .. p.most)
print("around=" .. p:around() .. " n=" .. p.n)

return {}
