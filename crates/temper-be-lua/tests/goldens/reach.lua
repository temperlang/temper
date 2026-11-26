local temper = require("temper-core")

---@type integer
local shownCount
---@type integer
local base

---@param n integer
---@return integer
local function shown(n)
  shownCount = temper.int32_add(shownCount, 1)
  return temper.int32_add(n, base)
end

shownCount = 0
base = 1
local checked = shown(2)
temper.log("shown " .. shown(1))

---@return integer
local function shownCount__get()
  return shownCount
end

---@param value integer
local function shownCount__set(value)
  shownCount = value
end

return {
  shown = shown,
  ["$test"] = { base = base, checked = checked, shown = shown, shownCount__get = shownCount__get, shownCount__set = shownCount__set },
}
