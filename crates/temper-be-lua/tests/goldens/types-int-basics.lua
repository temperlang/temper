local temper = require("temper-core")

temper.log("1.min(3)")
temper.log(tostring(math.min(1, 3)))
temper.log("3.min(1)")
temper.log(tostring(math.min(3, 1)))
temper.log("1.max(3)")
temper.log(tostring(math.max(1, 3)))
temper.log("3.max(1)")
temper.log(tostring(math.max(3, 1)))

return {}
