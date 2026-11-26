local temper = require("temper-core")
local BitVector = require("std/collections").BitVector

local v = temper.bitvector_constructor(2)
temper.bitvector_set(v, 5, false)
print("v[5]=" .. tostring(temper.bitvector_get(v, 5)))
print("before")
temper.bitvector_set(v, -1, false)
print("not reached")

return {}
