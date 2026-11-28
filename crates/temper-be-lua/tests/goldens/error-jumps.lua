local temper = require("temper-core")

---@class error-jumps.ErrorJumpsError: temper.Failure

---@class error-jumps.ErrorJumpsError.type: temper.Type
local ErrorJumpsError = temper.type("error-jumps.ErrorJumpsError", temper.Failure)

---@class error-jumps.NotFound: error-jumps.ErrorJumpsError
---@field key string
---@field message string

---@class error-jumps.NotFound.type: temper.Type
---@operator call: error-jumps.NotFound
local NotFound = temper.type("error-jumps.NotFound", ErrorJumpsError)

---@param this error-jumps.NotFound
---@param key string
function NotFound.constructor(this, key)
  this.key = key
end

---@param this error-jumps.NotFound
---@return string
function NotFound.get.message(this)
  return "not found (key: " .. this.key .. ")"
end

---@param key string
---@return integer
local function lookup(key)
  if key == "a" then
    return 1
  end
  error(NotFound(key))
end

---@param keys string[]
---@return string
local function scan(keys)
  local out = ""
  do
    local length = temper.list_length(keys)
    local i = 0
    while i < length do
      local k = temper.list_get(keys, i)
      i = temper.int32_add(i, 1)
      local t, t_1, t_2 = temper.catch(function()
        if k == "stop" then
          return "break", "flow"
        end
        out = out .. lookup(k)
      end, NotFound)
      if not t then
        local e = t_1
        out = out .. "!" .. e.key
      elseif t_1 == "break" then
        break
      end
    end
  end
  return out
end

print(scan({ "a", "x", "stop", "a" }))

return {
  ErrorJumpsError = ErrorJumpsError,
  NotFound = NotFound,
  lookup = lookup,
  scan = scan,
}
