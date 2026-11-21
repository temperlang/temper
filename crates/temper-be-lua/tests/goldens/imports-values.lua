local temper = require("temper-core")
local magicNumber = require("values/exporter").magicNumber

---@param n integer
---@return integer
local function timesMagic(n)
  return temper.int32_mul(n, magicNumber)
end

local a = 1
local c = timesMagic(a)
temper.log("number = " .. c)

return { timesMagic = timesMagic }
