local temper = require("temper-core")
local exports = {}

---@type string
local greeting
---@type integer
local count
---@type integer
local limit

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
local_ = temper.int32_add(local_, limit)
temper.log(report())
temper.log(report())
temper.log("local " .. local_)

exports.greeting = greeting
exports.tick = tick
exports.report = report
return exports
