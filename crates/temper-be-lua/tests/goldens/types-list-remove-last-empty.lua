local temper = require("temper-core")

local builder = temper.listbuilder_constructor()
temper.listbuilder_add(builder, "a")
print(temper.listbuilder_removelast(builder))
print("before")
print(temper.listbuilder_removelast(builder))
print("not reached")

return {}
