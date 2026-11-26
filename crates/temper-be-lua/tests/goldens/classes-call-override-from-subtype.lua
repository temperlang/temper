local temper = require("temper-core")

---@class call-override-from-subtype.Slappy
---@field thingums integer

---@class call-override-from-subtype.Slappy.type: temper.Type
local Slappy = temper.type("call-override-from-subtype.Slappy")

---@class call-override-from-subtype.Slippy: call-override-from-subtype.Slappy
---@field checkOverridden fun(self: call-override-from-subtype.Slippy): boolean
---@field checkOwn fun(self: call-override-from-subtype.Slippy): boolean
---@field thingums integer
---@field thingumses integer

---@class call-override-from-subtype.Slippy.type: temper.Type
---@operator call: call-override-from-subtype.Slippy
local Slippy = temper.type("call-override-from-subtype.Slippy", Slappy)

---@param this call-override-from-subtype.Slippy
function Slippy.constructor(this)
end

---@param this call-override-from-subtype.Slippy
---@return boolean
function Slippy.methods.checkOverridden(this)
  return this.thingums == 1
end

---@param this call-override-from-subtype.Slippy
---@return boolean
function Slippy.methods.checkOwn(this)
  return this.thingumses == 2
end

---@param this call-override-from-subtype.Slippy
---@return integer
function Slippy.get.thingums(this)
  return 3
end

---@param this call-override-from-subtype.Slippy
---@return integer
function Slippy.get.thingumses(this)
  return 4
end

---@class call-override-from-subtype.A
---@field a fun(self: call-override-from-subtype.A): string
---@field thing string

---@class call-override-from-subtype.A.type: temper.Type
local A = temper.type("call-override-from-subtype.A")

---@param this call-override-from-subtype.A
---@return string
function A.methods.a(this)
  return "A wins!"
end

---@param this call-override-from-subtype.A
---@return string
function A.get.thing(this)
  return "A has thing!"
end

---@class call-override-from-subtype.B: call-override-from-subtype.A

---@class call-override-from-subtype.B.type: temper.Type
local B = temper.type("call-override-from-subtype.B", A)

---@class call-override-from-subtype.C: call-override-from-subtype.A
---@field a fun(self: call-override-from-subtype.C): string
---@field thing string

---@class call-override-from-subtype.C.type: temper.Type
local C = temper.type("call-override-from-subtype.C", A)

---@param this call-override-from-subtype.C
---@return string
function C.methods.a(this)
  return "C wins!"
end

---@param this call-override-from-subtype.C
---@return string
function C.get.thing(this)
  return "C has thing!"
end

---@class call-override-from-subtype.D: call-override-from-subtype.B, call-override-from-subtype.C

---@class call-override-from-subtype.D.type: temper.Type
---@operator call: call-override-from-subtype.D
local D = temper.type("call-override-from-subtype.D", B, C)

---@param this call-override-from-subtype.D
function D.constructor(this)
end

D.methods.a = C.methods.a
D.get.thing = C.get.thing

---@class call-override-from-subtype.ASplit
---@field a fun(self: call-override-from-subtype.ASplit): string

---@class call-override-from-subtype.ASplit.type: temper.Type
local ASplit = temper.type("call-override-from-subtype.ASplit")

---@param this call-override-from-subtype.ASplit
---@return string
function ASplit.methods.a(this)
  return "A wins!"
end

---@class call-override-from-subtype.BSplit: call-override-from-subtype.ASplit

---@class call-override-from-subtype.BSplit.type: temper.Type
local BSplit = temper.type("call-override-from-subtype.BSplit", ASplit)

---@class call-override-from-subtype.CSplit
---@field a fun(self: call-override-from-subtype.CSplit): string

---@class call-override-from-subtype.CSplit.type: temper.Type
local CSplit = temper.type("call-override-from-subtype.CSplit")

---@param this call-override-from-subtype.CSplit
---@return string
function CSplit.methods.a(this)
  return "C wins!"
end

---@class call-override-from-subtype.DSplit: call-override-from-subtype.BSplit, call-override-from-subtype.CSplit

---@class call-override-from-subtype.DSplit.type: temper.Type
---@operator call: call-override-from-subtype.DSplit
local DSplit = temper.type("call-override-from-subtype.DSplit", BSplit, CSplit)

---@param this call-override-from-subtype.DSplit
function DSplit.constructor(this)
end

DSplit.methods.a = CSplit.methods.a

---@class call-override-from-subtype.ADeep
---@field a fun(self: call-override-from-subtype.ADeep): string

---@class call-override-from-subtype.ADeep.type: temper.Type
local ADeep = temper.type("call-override-from-subtype.ADeep")

---@param this call-override-from-subtype.ADeep
---@return string
function ADeep.methods.a(this)
  return "A wins!"
end

---@class call-override-from-subtype.B1Deep: call-override-from-subtype.ADeep
---@field a fun(self: call-override-from-subtype.B1Deep): string

---@class call-override-from-subtype.B1Deep.type: temper.Type
local B1Deep = temper.type("call-override-from-subtype.B1Deep", ADeep)

---@param this call-override-from-subtype.B1Deep
---@return string
function B1Deep.methods.a(this)
  return "B1 wins!"
end

---@class call-override-from-subtype.B2Deep: call-override-from-subtype.B1Deep

---@class call-override-from-subtype.B2Deep.type: temper.Type
local B2Deep = temper.type("call-override-from-subtype.B2Deep", B1Deep)

---@class call-override-from-subtype.CDeep: call-override-from-subtype.ADeep

---@class call-override-from-subtype.CDeep.type: temper.Type
local CDeep = temper.type("call-override-from-subtype.CDeep", ADeep)

---@class call-override-from-subtype.DDeep: call-override-from-subtype.B2Deep, call-override-from-subtype.CDeep

---@class call-override-from-subtype.DDeep.type: temper.Type
---@operator call: call-override-from-subtype.DDeep
local DDeep = temper.type("call-override-from-subtype.DDeep", B2Deep, CDeep)

---@param this call-override-from-subtype.DDeep
function DDeep.constructor(this)
end

---@class call-override-from-subtype.EDeep: call-override-from-subtype.CDeep, call-override-from-subtype.B2Deep

---@class call-override-from-subtype.EDeep.type: temper.Type
---@operator call: call-override-from-subtype.EDeep
local EDeep = temper.type("call-override-from-subtype.EDeep", CDeep, B2Deep)

---@param this call-override-from-subtype.EDeep
function EDeep.constructor(this)
end

local slippy = Slippy()
local slappy = slippy
temper.log(tostring(slippy.thingums))
temper.log(tostring(slappy.thingums))
temper.log(tostring(slippy.thingumses))
temper.log(tostring(slippy:checkOverridden()))
temper.log(tostring(slippy:checkOwn()))
temper.log(D():a())
temper.log(D().thing)
temper.log(DSplit():a())
temper.log(DDeep():a())
temper.log(EDeep():a())

return {}
