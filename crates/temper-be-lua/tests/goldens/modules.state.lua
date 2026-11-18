local temper = require("temper-core")

---@class modules.state.State
---@field id integer

---@class modules.state.State.type: temper.Type
---@operator call: modules.state.State
local State = temper.type("modules.state.State")

---@param this modules.state.State
---@param id integer
function State.constructor(this, id)
  this.id = id
end

---@class modules.state.Engine
---@field n integer

---@class modules.state.Engine.type: temper.Type
---@operator call: modules.state.Engine
local Engine = temper.type("modules.state.Engine")

---@param this modules.state.Engine
---@param n integer
function Engine.constructor(this, n)
  this.n = n
end

---@class modules.state.Parts
---@field count integer

---@class modules.state.Parts.type: temper.Type
---@operator call: modules.state.Parts
local Parts = temper.type("modules.state.Parts")

---@param this modules.state.Parts
---@param count integer
function Parts.constructor(this, count)
  this.count = count
end

local firstId = 1

return { State = State, Engine = Engine, Parts = Parts, firstId = firstId }
