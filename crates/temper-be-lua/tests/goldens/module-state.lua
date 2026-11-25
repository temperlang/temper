local temper = require("temper-core")
local exports = {}

---@type string
local greeting
---@type integer
local count
---@type integer
local limit
---@type string[]
local prefixes

---@class module-state.Palette
---@field name string

---@class module-state.Palette.type: temper.Type
---@field warm module-state.Palette
---@operator call: module-state.Palette
local Palette = temper.type("module-state.Palette")

---@param this module-state.Palette
---@param name string
function Palette.constructor(this, name)
  this.name = name
end

---@param n string
---@return module-state.Palette
function Palette.named(n)
  return Palette(temper.list_get(prefixes, 0) .. n)
end

---@return integer
local function bump()
  count = temper.int32_add(count, 1)
  exports.total = temper.int32_add(exports.total, count)
  return count
end

local function tick()
  exports.ticks = temper.int32_add(exports.ticks, 1)
end

---@return string
local function report()
  return greeting .. " " .. bump() .. " of " .. limit .. " " .. exports.total
end

greeting = "hi"
count = 0
limit = 3
exports.total = 10
exports.ticks = 0
local local_ = 1
prefixes = { "p-" }
Palette.warm = Palette.named("warm")
local favorite = Palette.warm
local_ = temper.int32_add(local_, limit)
temper.log(report())
temper.log(report())
temper.log("local " .. local_)
temper.log(Palette.warm.name .. " " .. favorite.name)

exports.greeting = greeting
exports.Palette = Palette
exports.favorite = favorite
exports.tick = tick
exports.report = report
return exports
