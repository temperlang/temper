local temper = require("temper-core")

---@class failure-imports.a.FailureImportsError: temper.Failure

---@class failure-imports.a.FailureImportsError.type: temper.Type
local FailureImportsError = temper.type("failure-imports.a.FailureImportsError", temper.Failure)

---@class failure-imports.a.NotFound: failure-imports.a.FailureImportsError
---@field key string
---@field message string

---@class failure-imports.a.NotFound.type: temper.Type
---@operator call: failure-imports.a.NotFound
local NotFound = temper.type("failure-imports.a.NotFound", FailureImportsError)

---@param this failure-imports.a.NotFound
---@param key string
function NotFound.constructor(this, key)
  this.key = key
end

---@param this failure-imports.a.NotFound
---@return string
function NotFound.get.message(this)
  return "not found (key: " .. this.key .. ")"
end

---@param key string
---@return integer
local function lookup(key)
  error(NotFound(key))
end

return {
  FailureImportsError = FailureImportsError,
  NotFound = NotFound,
  lookup = lookup,
}
