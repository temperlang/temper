local temper = require("temper-core")

local function fn()
  local t, t_1 = temper.pcall(function()
    local b = temper.promisebuilder_constructor()
    temper.promisebuilder_complete(b, "Completed synchronously")
    print(temper.promisebuilder_promise(b):await())
  end)
  if not t then
    error("panic\ncaused by: " .. tostring(t_1))
  end
  do
    local b2 = temper.promisebuilder_constructor()
    temper.promisebuilder_breakpromise(b2)
    ---@type string
    local t_2
    if not temper.pcall(function()
      t_2 = temper.promisebuilder_promise(b2):await()
    end) then
      t_2 = "Broken synchronously"
    end
    print(t_2)
  end
  local t, t_1 = temper.pcall(function()
    local b3 = temper.promisebuilder_constructor()
    local function fn2()
      temper.promisebuilder_complete(b3, "Completed asynchronously")
    end
    temper.async(temper.adapt_generator_fn(fn2))
    print(temper.promisebuilder_promise(b3):await())
  end)
  if not t then
    error("panic\ncaused by: " .. tostring(t_1))
  end
  local t_2, t_3 = temper.pcall(function()
    local b4 = temper.promisebuilder_constructor()
    temper.promisebuilder_complete(b4, "Worth doing twice")
    print(temper.promisebuilder_promise(b4):await())
    print(temper.promisebuilder_promise(b4):await())
  end)
  if not t_2 then
    error("panic\ncaused by: " .. tostring(t_3))
  end
  local t, t_1 = temper.pcall(function()
    local a = temper.promisebuilder_constructor()
    local b5 = temper.promisebuilder_constructor()
    local function fn3()
      local t, t_1 = temper.pcall(function()
        temper.promisebuilder_promise(b5):await()
      end)
      if not t then
        error("panic\ncaused by: " .. tostring(t_1))
      end
      print("After")
      temper.promisebuilder_complete(a, temper.empty())
    end
    temper.async(temper.adapt_generator_fn(fn3))
    local function fn4()
      print("Before")
      temper.promisebuilder_complete(b5, temper.empty())
    end
    temper.async(temper.adapt_generator_fn(fn4))
    temper.promisebuilder_promise(a):await()
  end)
  if not t then
    error("panic\ncaused by: " .. tostring(t_1))
  end
  if not temper.pcall(function()
    print("What is your favourite colour?")
    local b6 = temper.promisebuilder_constructor()
    temper.promisebuilder_complete(b6, "Blue")
    temper.promisebuilder_complete(b6, "No, yell...")
    print(temper.promisebuilder_promise(b6):await())
  end) then
  end
end

temper.async(temper.adapt_generator_fn(fn))

return {}
