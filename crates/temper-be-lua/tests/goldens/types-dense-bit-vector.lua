local temper = require("temper-core")

local v = temper.densebitvector_constructor(16)
temper.log("v[2]=" .. tostring(temper.densebitvector_get(v, 2)))
temper.log("v[3]=" .. tostring(temper.densebitvector_get(v, 3)))
temper.log("v[3] <- true")
temper.densebitvector_set(v, 3, true)
temper.log("v[2]=" .. tostring(temper.densebitvector_get(v, 2)))
temper.log("v[3]=" .. tostring(temper.densebitvector_get(v, 3)))
temper.log("v[3] <- false")
temper.densebitvector_set(v, 3, false)
temper.log("v[2]=" .. tostring(temper.densebitvector_get(v, 2)))
temper.log("v[3]=" .. tostring(temper.densebitvector_get(v, 3)))

return {}
