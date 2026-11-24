local temper = require("temper-core")
local Queue = require("std/collections").Queue

local queue = temper.queue_constructor()
temper.queue_add(queue, "a")
print(temper.queue_removefirst(queue))
print("before")
print(temper.queue_removefirst(queue))
print("not reached")

return {}
