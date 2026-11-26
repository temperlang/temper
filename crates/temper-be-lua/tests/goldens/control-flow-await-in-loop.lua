local temper = require("temper-core")

---@type temper.PromiseBuilder
local once
---@type temper.PromiseBuilder
local later

once = temper.promisebuilder_constructor()
temper.promisebuilder_complete(once, "same")
later = temper.promisebuilder_constructor()

local function fn()
  do
    local i = 0
    while i < 2 do
      ---@type string
      local s
      if not temper.pcall(function()
        s = temper.promisebuilder_promise(once):await()
      end) then
        s = "?"
      end
      temper.log(s .. " " .. i)
      i = temper.int32_add(i, 1)
    end
  end
  local j = 0
  while j < 2 do
    ---@type string
    local s
    if not temper.pcall(function()
      s = temper.promisebuilder_promise(later):await()
    end) then
      s = "?"
    end
    temper.log(s .. " " .. j)
    j = temper.int32_add(j, 1)
  end
  ---@type string
  local t
  if not temper.pcall(function()
    t = temper.promisebuilder_promise(once):await()
  end) then
    t = "?"
  end
  temper.log(t .. " after the loops")
end

temper.async(temper.adapt_generator_fn(fn))
temper.promisebuilder_complete(later, "later")

return {}
