local temper = require("temper-core")

---@class property-order.C
---@field backed integer
---@field computed integer

---@class property-order.C.type: temper.Type
---@operator call: property-order.C
local C = temper.type("property-order.C")

---@param this property-order.C
---@param backed? integer|temper.Null
function C.constructor(this, backed)
  if temper.is_null(backed) then
    temper.log("initializing backed")
    backed = 1
  end
  ---@cast backed integer
  this.backed = backed
end

---@param this property-order.C
---@return integer
function C.get.computed(this)
  temper.log("computing computed")
  return 1
end

temper.log("about to create instance")
local c = C()
temper.log("about to get backed")
temper.log("c.backed=" .. c.backed)
temper.log("about to get computed")
temper.log("c.computed=" .. c.computed)
temper.log("done")

return {}
