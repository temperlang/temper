-- Host code using the generated `errors-interop` library's declared
-- errors the standard way, for `functional.rs`'s `runs`: `pcall`
-- catches them, `temper.instance_of` tells them apart by their own
-- types, the library's root, a sealed interface's, and the runtime's
-- `temper.Failure`, `tostring` and `message` give their messages, and
-- `cause` the error being handled where one was thrown; a panic, a
-- string, is none of them. It prints a line per check, which the test
-- compares with what it expects. LuaLS checks it with the generated code.

local temper = require("temper-core")
-- Its top-level code logs.
local lf = require("errors-interop")

---What calling `f` with `arg` raised.
---@param f fun(arg: any): any
---@param arg any
---@return any
local function raised(f, arg)
  local ok, err = pcall(f, arg)
  assert(not ok)
  return err
end

local missing = raised(lf.lookup, "z")
assert(temper.instance_of(missing, lf.NotFound))
---@cast missing errors-interop.NotFound
print("NotFound " .. missing.key .. ": " .. missing.message)

local failure = raised(lf.lookup, "y")
assert(temper.instance_of(failure, lf.ErrorsInteropError))
print(
  "base " .. tostring(temper.instance_of(failure, lf.NotFound))
    .. ", Failure " .. tostring(temper.instance_of(failure, temper.Failure))
    .. ", sign " .. tostring(temper.instance_of(failure, lf.SignError))
    .. ", " .. tostring(failure)
)

for _, text in ipairs({ "x", "" }) do
  local e = raised(lf.sign, text)
  assert(temper.instance_of(e, lf.SignError))
  if temper.instance_of(e, lf.BadSign) then
    ---@cast e errors-interop.BadSign
    print("sign BadSign: " .. tostring(e))
    assert(e.text == "x")
  else
    print("sign EmptyText: " .. tostring(e))
  end
end

local lookupFailed = raised(lf.setting, "port")
assert(temper.instance_of(lookupFailed, lf.LookupFailed))
---@cast lookupFailed errors-interop.LookupFailed
local cause = lookupFailed.cause
assert(temper.instance_of(cause, lf.NotFound))
---@cast cause errors-interop.NotFound
print(
  "LookupFailed " .. lookupFailed.name .. " " .. tostring(lookupFailed)
    .. ", caused by " .. cause.message
)
assert(cause.cause == nil)

for _, check in ipairs({ { "made before", lf.madeBefore, "early" }, { "thrown again", lf.thrownAgain, "first" } }) do
  local e = raised(check[2], check[3])
  ---@cast e temper.Failure
  local first = e.cause
  assert(temper.instance_of(first, lf.NotFound))
  ---@cast first temper.Failure
  print(check[1] .. " " .. e.message .. ", caused by " .. first.message)
end

-- Host code makes and raises errors too, as generated code does.
local made = raised(error, lf.NotFound("host"))
---@cast made temper.Failure
print("made by host " .. tostring(made) .. " " .. tostring(made.cause))

local panic = raised(lf.risky, 2)
print("panic passes, " .. tostring(temper.instance_of(panic, temper.Failure)) .. ", " .. type(panic))

-- A type of host code's own under a sealed one, which Temper's exhaustive
-- `when` over it doesn't know, panics there.
---@class host.Rogue: errors-interop.SignError
---@class host.Rogue.type: temper.Type
---@operator call: host.Rogue
local Rogue = temper.type("host.Rogue", lf.SignError)

---@param this host.Rogue
---@return string
function Rogue.get.message(this)
  return "rogue"
end

print("known subtype " .. lf.signName(lf.BadSign("?")))
local unknown = raised(lf.signName, Rogue())
-- Its message, after the place `error` puts first.
local said = tostring(unknown):gsub("^.*: ", "")
print("unknown subtype panics, " .. tostring(temper.instance_of(unknown, temper.Failure)) .. ", " .. said)
