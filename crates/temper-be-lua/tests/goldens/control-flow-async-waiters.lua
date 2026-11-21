local temper = require("temper-core")

local function fn()
  if not temper.pcall(function()
    local b = temper.promisebuilder_constructor()
    local firstDone = temper.promisebuilder_constructor()
    local secondDone = temper.promisebuilder_constructor()
    local function fn2()
      ---@type integer
      local v
      if not temper.pcall(function()
        v = temper.promisebuilder_promise(b):await()
      end) then
        v = -1
      end
      temper.promisebuilder_complete(firstDone, v)
    end
    temper.async(temper.adapt_generator_fn(fn2))
    local function fn3()
      ---@type integer
      local v
      if not temper.pcall(function()
        v = temper.promisebuilder_promise(b):await()
      end) then
        v = -1
      end
      temper.promisebuilder_complete(secondDone, v)
    end
    temper.async(temper.adapt_generator_fn(fn3))
    local function fn4()
      temper.promisebuilder_complete(b, 7)
    end
    temper.async(temper.adapt_generator_fn(fn4))
    local first = temper.promisebuilder_promise(firstDone):await()
    local second = temper.promisebuilder_promise(secondDone):await()
    temper.log("after complete: first got " .. first .. ", second got " .. second)
  end) then
    error("panic")
  end
  if not temper.pcall(function()
    local b2 = temper.promisebuilder_constructor()
    local firstDone2 = temper.promisebuilder_constructor()
    local secondDone2 = temper.promisebuilder_constructor()
    local function fn5()
      ---@type integer
      local v
      if not temper.pcall(function()
        v = temper.promisebuilder_promise(b2):await()
      end) then
        v = -1
      end
      temper.promisebuilder_complete(firstDone2, v)
    end
    temper.async(temper.adapt_generator_fn(fn5))
    local function fn6()
      ---@type integer
      local v
      if not temper.pcall(function()
        v = temper.promisebuilder_promise(b2):await()
      end) then
        v = -1
      end
      temper.promisebuilder_complete(secondDone2, v)
    end
    temper.async(temper.adapt_generator_fn(fn6))
    local function fn7()
      temper.promisebuilder_breakpromise(b2)
    end
    temper.async(temper.adapt_generator_fn(fn7))
    local first2 = temper.promisebuilder_promise(firstDone2):await()
    local second2 = temper.promisebuilder_promise(secondDone2):await()
    temper.log("after breakPromise: first got " .. first2 .. ", second got " .. second2)
  end) then
    error("panic")
  end
end

temper.async(temper.adapt_generator_fn(fn))

return {}
