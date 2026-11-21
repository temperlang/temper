local temper = require("temper-core")

---@class inherited-getter.I
---@field place string

---@class inherited-getter.I.type: temper.Type
local I = temper.type("inherited-getter.I")

---@param this inherited-getter.I
---@return string
function I.get.place(this)
  return "Byron Bay"
end

---@class inherited-getter.C: inherited-getter.I

---@class inherited-getter.C.type: temper.Type
---@operator call: inherited-getter.C
local C = temper.type("inherited-getter.C", I)

---@param this inherited-getter.C
function C.constructor(this)
end

local c = C()
temper.log("Goodbye, gotta run, " .. c.place .. "!")

return {}
