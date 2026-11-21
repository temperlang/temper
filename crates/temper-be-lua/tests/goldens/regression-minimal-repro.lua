local temper = require("temper-core")

local ls = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 }
temper.log("All  " .. temper.listed_join(ls, ", ", function(x) return tostring(x) end))
temper.log("Even " .. temper.listed_join(temper.listed_filter(ls, function(x) return temper.band(x, 1) == 0 end), ", ", function(x) return tostring(x) end))
temper.log("Odd  " .. temper.listed_join(temper.listed_filter(ls, function(x) return temper.band(x, 1) ~= 0 end), ", ", function(x) return tostring(x) end))
temper.log("Neg  " .. temper.listed_join(temper.listed_map(ls, function(x) return temper.int32_unm(x) end), ", ", function(x) return tostring(x) end))
local strs = temper.string_split("1 ; 2 ; 3", " ; ")
temper.log("Splt " .. temper.listed_join(strs, ", ", function(x) return x end))

return {}
