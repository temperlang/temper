local temper = require("temper-core")

local emptyString = ""
local foo = "foo"
temper.log(emptyString == "" and "emptyString is empty" or "emptyString is not empty")
temper.log(foo == "" and "foo is empty" or "foo is not empty")

return {}
