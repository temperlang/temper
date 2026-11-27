local temper = require("temper-core")

---@class failure-generators.FailureGeneratorsError: temper.Failure

---@class failure-generators.FailureGeneratorsError.type: temper.Type
local FailureGeneratorsError = temper.type("failure-generators.FailureGeneratorsError", temper.Failure)

---@class failure-generators.TooBig: failure-generators.FailureGeneratorsError
---@field n integer
---@field message string

---@class failure-generators.TooBig.type: temper.Type
---@operator call: failure-generators.TooBig
local TooBig = temper.type("failure-generators.TooBig", FailureGeneratorsError)

---@param this failure-generators.TooBig
---@param n integer
function TooBig.constructor(this, n)
  this.n = n
end

---@param this failure-generators.TooBig
---@return string
function TooBig.get.message(this)
  return "too big (n: " .. this.n .. ")"
end

---@generic T
---@param make fun(): temper.Generator
---@return temper.Generator
local function generated(make)
  return make()
end

---@generic T
---@param make fun(): temper.Generator
---@return temper.Generator
local function safely(make)
  return make()
end

---@param e temper.Failure
---@return string
local function why(e)
  local mine = temper.instance_of(e, TooBig) and "too big " .. (e --[[@as failure-generators.TooBig]]).n or e.message
  local cause = temper.failure_cause(e)
  if temper.is_null(cause) then
    return mine
  else
    return mine .. ": " .. why(cause --[[@as temper.Failure]])
  end
end

---@param g temper.Generator
---@param n integer
---@return string
local function take(g, n)
  local taken = temper.listbuilder_constructor()
  while temper.listbuilder_length(taken) < n do
    ---@type table
    local r
    local failed = false
    ---@type temper.BrokenGenerator|temper.Null
    local caught = temper.null
    ---@type table|temper.Null
    local held = temper.null
    local t, failure = temper.catch(function()
      held = { temper.generator_next(g) }
    end, temper.BrokenGenerator)
    if not t then
      caught = failure
      failed = true
    end
    if failed then
      local e = caught --[[@as temper.BrokenGenerator]]
      return temper.listed_join(taken, " ", function(s) return s end) .. " " .. why(e)
    else
      r = held --[[@as table]]
    end
    if r[1] ~= nil then
      temper.listbuilder_add(taken, tostring((r --[[@as table]])[2]))
    else
      temper.listbuilder_add(taken, "done")
      break
    end
  end
  return temper.listed_join(taken, " ", function(s2) return s2 end)
end

---@param limit integer
---@return temper.Generator
local function upTo(limit)
  local function fn()
    local i = 1
    while true do
      if i > limit then
        error(TooBig(i))
      end
      temper.yield(i)
      i = temper.int32_add(i, 1)
    end
  end
  return generated(temper.adapt_generator_fn(fn))
end

---@param n integer
---@param limit integer
---@return integer
local function checked(n, limit)
  if n > limit then
    error(TooBig(n))
  end
  return n
end

---@param limit integer
---@return temper.Generator
local function squares(limit)
  local function fn()
    ---@type integer
    local t
    if not temper.catch(function()
      t = checked(50, limit)
    end, TooBig) then
      t = 0
    end
    temper.yield(t)
    local i = 1
    while true do
      temper.yield(checked(temper.int32_mul(i, i), limit))
      i = temper.int32_add(i, 1)
    end
  end
  return generated(temper.adapt_generator_fn(fn))
end

---@param n integer
---@return temper.Generator
local function count(n)
  local function fn()
    do
      local i = 1
      while i <= n do
        temper.yield(i)
        i = temper.int32_add(i, 1)
      end
    end
  end
  return safely(temper.adapt_generator_fn(fn))
end

---@param r table
---@return string
local function shown(r)
  if r[1] ~= nil then
    return tostring((r --[[@as table]])[2])
  else
    return "done"
  end
end

---@param g temper.Generator
---@return string
local function firstTwo(g)
  local a = shown({ temper.generator_next(g) })
  ---@type string
  local b
  if not temper.catch(function()
    b = shown({ temper.generator_next(g) })
  end, temper.BrokenGenerator) then
    b = "failed"
  end
  return a .. " " .. b
end

---@param inner temper.Generator
---@return temper.Generator
local function doubled(inner)
  local function fn()
    while true do
      local r = { temper.generator_next(inner) }
      if r[1] ~= nil then
        temper.yield(temper.int32_mul((r --[[@as table]])[2], 2))
      else
        break
      end
    end
  end
  return generated(temper.adapt_generator_fn(fn))
end

print(take(upTo(3), 9))
local one = upTo(1)
print(take(one, 9))
print(take(one, 9))
print(take(squares(10), 9))
print(take(count(2), 9))
print(firstTwo(count(1)))
print(take(doubled(upTo(2)), 9))
print(take(doubled(count(2)), 9))

return {}
