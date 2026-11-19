-- Host code using the generated `async-turns` library the standard way,
-- for `functional.rs`'s `runs`: it prints a line per check, which the
-- test compares with what it expects. LuaLS checks it with the generated
-- code. Lua has no standard promise, so host code awaits in a block of
-- its own, as generated code does, and can't block, so there's no
-- blocking check.

local temper = require("temper-core")
-- Its top-level code logs, and runs its `async` blocks.
local turns = require("async-turns")

-- A block of the host's own awaits a generated promise.
temper.async(temper.adapt_generator_fn(function()
  print("await " .. turns.later(1):await())
end))

-- Awaiting outside any block fails clearly.
local ok, err = pcall(function()
  return turns.later(2):await()
end)
print("settled outside " .. tostring(ok))
local pending = temper.promisebuilder_promise(temper.promisebuilder_constructor())
ok, err = pcall(function()
  return pending:await()
end)
print("outside a block: " .. tostring(not ok and tostring(err):find("await outside an async block") ~= nil))

-- `for … in` steps a generator.
local seen = {}
for _ in turns.ticks(3, function(i)
  seen[#seen + 1] = i
end) do
end
print("for in " .. table.concat(seen, ","))

-- A generator stepped again after a failed step is done.
local g = turns.ticks(3, function(i)
  if i == 1 then
    error("boom")
  end
end)
g()
ok = pcall(g)
print("failed step " .. tostring(not ok))
print("then done " .. tostring(g() == nil))

-- `for … in` takes a lazy generator's values, and stops early.
local fib = {}
for _, n in turns.fibonacci() do
  if #fib == 8 then
    break
  end
  fib[#fib + 1] = n
end
print("fibonacci " .. table.concat(fib, ","))
local steps = {}
for _, n in turns.collatz(7) do
  steps[#steps + 1] = n
end
print("collatz " .. table.concat(steps, ","))

-- A Temper failure is an `error`, which `pcall` catches, as
-- notes/api-compat.md promises.
ok = pcall(turns.halve, 3)
print("halve " .. tostring(turns.halve(8)) .. (ok and " returned" or " caught"))
