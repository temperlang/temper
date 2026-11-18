local temper = require("temper-core")
local State = require("modules/state").State
local Engine = require("modules/state").Engine
local Parts = require("modules/state").Parts
local firstId = require("modules/state").firstId
local wheels = require("modules/parts/regex").wheels
local RegexNode = require("std/regex").RegexNode
local oneOrMore = require("std/regex").oneOrMore
local Dot = require("std/regex").Dot

---@return string
local function describe()
  local state = State(firstId)
  local engine = Engine(wheels())
  local least = oneOrMore(Dot).min
  local parts = Parts(temper.int32_add(engine.n, least))
  return "state " .. state.id .. " engine " .. engine.n .. " parts " .. parts.count
end

return { describe = describe }
