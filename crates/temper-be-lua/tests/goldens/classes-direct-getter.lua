local temper = require("temper-core")

---@class direct-getter.C
---@field place string

---@class direct-getter.C.type: temper.Type
---@operator call: direct-getter.C
local C = temper.type("direct-getter.C")

---@param this direct-getter.C
function C.constructor(this)
end

---@param this direct-getter.C
---@return string
function C.get.place(this)
  return "Hilo, HI"
end

local c = C()
temper.log("Hello, " .. c.place .. "!")

return {}
