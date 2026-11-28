local temper = require("temper-core")

---@class error-imports.a.AError: temper.Failure

---@class error-imports.a.AError.type: temper.Type
local AError = temper.type("error-imports.a.AError", temper.Failure)

---@class error-imports.a.NotFound: error-imports.a.AError
---@field key string
---@field message string

---@class error-imports.a.NotFound.type: temper.Type
---@operator call: error-imports.a.NotFound
local NotFound = temper.type("error-imports.a.NotFound", AError)

---@param this error-imports.a.NotFound
---@param key string
function NotFound.constructor(this, key)
  this.key = key
end

---@param this error-imports.a.NotFound
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
