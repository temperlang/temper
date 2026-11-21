local temper = require("temper-core")

local function fn()
  if not temper.pcall(function()
    local b = temper.promisebuilder_constructor()
    temper.promisebuilder_complete(b, "Completed synchronously")
    temper.log(temper.promisebuilder_promise(b):await())
  end) then
    error("panic")
  end
  do
    local b2 = temper.promisebuilder_constructor()
    temper.promisebuilder_breakpromise(b2)
    ---@type string
    local t
    if not temper.pcall(function()
      t = temper.promisebuilder_promise(b2):await()
    end) then
      t = "Broken synchronously"
    end
    temper.log(t)
  end
  if not temper.pcall(function()
    local b3 = temper.promisebuilder_constructor()
    local function fn2()
      temper.promisebuilder_complete(b3, "Completed asynchronously")
    end
    temper.async(temper.adapt_generator_fn(fn2))
    temper.log(temper.promisebuilder_promise(b3):await())
  end) then
    error("panic")
  end
  if not temper.pcall(function()
    local b4 = temper.promisebuilder_constructor()
    temper.promisebuilder_complete(b4, "Worth doing twice")
    temper.log(temper.promisebuilder_promise(b4):await())
    temper.log(temper.promisebuilder_promise(b4):await())
  end) then
    error("panic")
  end
  if not temper.pcall(function()
    local a = temper.promisebuilder_constructor()
    local b5 = temper.promisebuilder_constructor()
    local function fn3()
      if not temper.pcall(function()
        temper.promisebuilder_promise(b5):await()
      end) then
        error("panic")
      end
      temper.log("After")
      temper.promisebuilder_complete(a, temper.empty())
    end
    temper.async(temper.adapt_generator_fn(fn3))
    local function fn4()
      temper.log("Before")
      temper.promisebuilder_complete(b5, temper.empty())
    end
    temper.async(temper.adapt_generator_fn(fn4))
    temper.promisebuilder_promise(a):await()
  end) then
    error("panic")
  end
  if not temper.pcall(function()
    temper.log("What is your favourite colour?")
    local b6 = temper.promisebuilder_constructor()
    temper.promisebuilder_complete(b6, "Blue")
    temper.promisebuilder_complete(b6, "No, yell...")
    temper.log(temper.promisebuilder_promise(b6):await())
  end) then
  end
end

temper.async(temper.adapt_generator_fn(fn))

return {}
