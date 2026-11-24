local temper = require("temper-core")

---@generic T
---@param ls T[]
---@return T|temper.Null
local function firstOrNull(ls)
  if temper.listed_isempty(ls) then
    return temper.null
  else
    return temper.list_get(ls, 0)
  end
end

---@class nullable-generics.Slot
---@field value any
---@field kept fun(self: nullable-generics.Slot, keep: boolean): any|temper.Null

---@class nullable-generics.Slot.type: temper.Type
---@operator call: nullable-generics.Slot
local Slot = temper.type("nullable-generics.Slot")

---@param this nullable-generics.Slot
---@param value any
function Slot.constructor(this, value)
  this.value = value
end

---@param this nullable-generics.Slot
---@param keep boolean
---@return any|temper.Null
function Slot.methods.kept(this, keep)
  if keep then
    return this.value
  else
    return temper.null
  end
end

---@class nullable-generics.Named
---@field name string

---@class nullable-generics.Named.type: temper.Type
---@operator call: nullable-generics.Named
local Named = temper.type("nullable-generics.Named")

---@param this nullable-generics.Named
---@param name string
function Named.constructor(this, name)
  this.name = name
end

---@generic T
---@param x T
---@param y? T|temper.Null
---@return T
local function withDefault(x, y)
  if temper.is_null(y) then
    y = x
  end
  ---@cast y -temper.Null
  return y
end

local strings = { temper.null, "b" }
local noStrings = {}
local ints = { temper.null, 2 }
local a = firstOrNull(strings)
local b = firstOrNull(noStrings)
local c = firstOrNull(ints)
temper.log(tostring(temper.is_null(a)) .. " " .. tostring(temper.is_null(b)) .. " " .. tostring(temper.is_null(c)))
local subject = firstOrNull({ "x" })
temper.log(temper.is_null(subject) and "none" or (subject --[[@as string]]))
local subject2 = firstOrNull(temper.listed_slice(ints, 1, 2))
local subject3 = firstOrNull({ 3 })
temper.log((temper.is_null(subject2) and -1 or (subject2 --[[@as integer]])) .. " " .. (temper.is_null(subject3) and -1 or (subject3 --[[@as integer]])))
local slot = Slot(temper.null)
temper.log(tostring(temper.is_null(slot:kept(true))) .. " " .. tostring(temper.is_null(slot:kept(false))))
local subject4 = Slot("s"):kept(true)
temper.log(temper.is_null(subject4) and "none" or (subject4 --[[@as string]]))
local nameds = { temper.null }
local noNameds = {}
local n = firstOrNull({ Named("n") })
local string_ = tostring(temper.is_null(firstOrNull(nameds))) .. " " .. tostring(temper.is_null(firstOrNull(noNameds))) .. " "
local subject5 = temper.is_null(n) and temper.null or (n --[[@as nullable-generics.Named]]).name
temper.log(string_ .. (temper.is_null(subject5) and "none" or (subject5 --[[@as string]])))
local subject7 = firstOrNull(noNameds)
local subject6 = temper.is_null(subject7) and temper.null or (subject7 --[[@as nullable-generics.Named]]).name
temper.log(temper.is_null(subject6) and "none" or (subject6 --[[@as string]]))
local none = temper.null
local subject8 = withDefault("a", none)
temper.log(temper.is_null(subject8) and "null" or (subject8 --[[@as string]]))
local subject9 = withDefault("a", "b")
temper.log(temper.is_null(subject9) and "null" or (subject9 --[[@as string]]))
local subject10 = withDefault("a")
temper.log(temper.is_null(subject10) and "null" or (subject10 --[[@as string]]))
local subject11 = withDefault(1, temper.null)
temper.log(tostring(temper.is_null(subject11) and -1 or (subject11 --[[@as integer]])))
temper.log(withDefault("c") .. " " .. withDefault(4, 5))

return {
  firstOrNull = firstOrNull,
  Slot = Slot,
  Named = Named,
  withDefault = withDefault,
}
