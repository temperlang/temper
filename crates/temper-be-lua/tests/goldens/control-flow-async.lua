local temper = require("temper-core")

local function fn()
  local t, t_1 = temper.catch(function()
    local b = temper.promisebuilder_constructor()
    temper.promisebuilder_complete(b, "Completed synchronously")
    print(temper.promisebuilder_promise(b):await())
  end, temper.BrokenPromise)
  if not t then
    error("panic\ncaused by: " .. tostring(t_1))
  end
  do
    local b2 = temper.promisebuilder_constructor()
    temper.promisebuilder_breakpromise(b2)
    ---@type string
    local t_2
    if not temper.catch(function()
      t_2 = temper.promisebuilder_promise(b2):await()
    end, temper.BrokenPromise) then
      t_2 = "Broken synchronously"
    end
    print(t_2)
  end
  do
    local b2 = temper.promisebuilder_constructor()
    temper.promisebuilder_breakpromise(b2)
    ---@type string
    local t_3
    local t_4, e = temper.catch(function()
      t_3 = temper.promisebuilder_promise(b2):await()
    end, temper.BrokenPromise)
    if not t_4 then
      t_3 = "Caught " .. e.message
    end
    print(t_3)
  end
  do
    local b2 = temper.promisebuilder_constructor()
    local function fn2()
      temper.promisebuilder_breakpromise(b2)
    end
    temper.async(temper.adapt_generator_fn(fn2))
    ---@type string
    local t
    local t_1, e = temper.catch(function()
      t = temper.promisebuilder_promise(b2):await()
    end, temper.BrokenPromise)
    if not t_1 then
      t = "Caught " .. e.message .. " later"
    end
    print(t)
  end
  local t, t_1 = temper.catch(function()
    local b3 = temper.promisebuilder_constructor()
    local function fn3()
      temper.promisebuilder_complete(b3, "Completed asynchronously")
    end
    temper.async(temper.adapt_generator_fn(fn3))
    print(temper.promisebuilder_promise(b3):await())
  end, temper.BrokenPromise)
  if not t then
    error("panic\ncaused by: " .. tostring(t_1))
  end
  local t_2, t_3 = temper.catch(function()
    local b4 = temper.promisebuilder_constructor()
    temper.promisebuilder_complete(b4, "Worth doing twice")
    print(temper.promisebuilder_promise(b4):await())
    print(temper.promisebuilder_promise(b4):await())
  end, temper.BrokenPromise)
  if not t_2 then
    error("panic\ncaused by: " .. tostring(t_3))
  end
  local t, t_1 = temper.catch(function()
    local a = temper.promisebuilder_constructor()
    local b5 = temper.promisebuilder_constructor()
    local function fn4()
      local t, t_1 = temper.catch(function()
        temper.promisebuilder_promise(b5):await()
      end, temper.BrokenPromise)
      if not t then
        error("panic\ncaused by: " .. tostring(t_1))
      end
      print("After")
      temper.promisebuilder_complete(a, temper.empty())
    end
    temper.async(temper.adapt_generator_fn(fn4))
    local function fn5()
      print("Before")
      temper.promisebuilder_complete(b5, temper.empty())
    end
    temper.async(temper.adapt_generator_fn(fn5))
    temper.promisebuilder_promise(a):await()
  end, temper.BrokenPromise)
  if not t then
    error("panic\ncaused by: " .. tostring(t_1))
  end
  if not temper.catch(function()
    print("What is your favourite colour?")
    local b6 = temper.promisebuilder_constructor()
    temper.promisebuilder_complete(b6, "Blue")
    temper.promisebuilder_complete(b6, "No, yell...")
    print(temper.promisebuilder_promise(b6):await())
  end, temper.BrokenPromise) then
  end
end

temper.async(temper.adapt_generator_fn(fn))

return {}
