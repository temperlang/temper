local temper = require("temper-core")

---@param body fun(): temper.Generator
local function f(body)
  local generator = body()
  temper.log("f0")
  temper.generator_next(generator)
  temper.log("f1")
  temper.generator_next(generator)
  temper.log("f2")
  temper.generator_next(generator)
  temper.log("f3")
end

local function fn()
  local i = 0
  while true do
    temper.log("generated " .. i)
    i = temper.int32_add(i, 1)
    temper.yield()
  end
end

f(temper.adapt_generator_fn(fn))

return {}
