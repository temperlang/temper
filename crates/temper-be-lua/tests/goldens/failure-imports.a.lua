local temper = require("temper-core")

---@class failure-imports.a.AError: temper.Failure

---@class failure-imports.a.AError.type: temper.Type
local AError = temper.type("failure-imports.a.AError", temper.Failure)

---@class failure-imports.a.NotFound: failure-imports.a.AError
---@field key string
---@field message string

---@class failure-imports.a.NotFound.type: temper.Type
---@operator call: failure-imports.a.NotFound
local NotFound = temper.type("failure-imports.a.NotFound", AError)

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

return { AError = AError, NotFound = NotFound, lookup = lookup }
