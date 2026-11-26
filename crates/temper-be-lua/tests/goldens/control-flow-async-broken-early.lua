local temper = require("temper-core")

---@type temper.PromiseBuilder
local b

b = temper.promisebuilder_constructor()

local function fn()
  ---@type integer
  local v
  if not temper.catch(function()
    v = temper.promisebuilder_promise(b):await()
  end, temper.BrokenPromise) then
    v = -1
  end
  print("got " .. v)
end

temper.async(temper.adapt_generator_fn(fn))
temper.promisebuilder_breakpromise(b)

return {}
