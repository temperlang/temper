local temper = require("temper-core")
local ExportedClass = require("types/exporter").ExportedClass

local x = ExportedClass(42)
temper.log("foo = " .. x.foo)

return {}
