local temper = require("temper-core")

---Gives a class's instances a `__tostring` that calls their `toString`.
---@param class temper.Type
local function with_tostring(class)
  local construct = class.constructor
  ---@type table?
  local meta = nil
  class.constructor = function(this, ...)
    if meta == nil then
      meta = {}
      for k, v in _G.pairs(_G.getmetatable(this)) do
        meta[k] = v
      end
      meta.__tostring = function(self)
        return self:toString()
      end
    end
    _G.setmetatable(this, meta)
    construct(this, ...)
  end
end

---@class sorting.Entry
---@field id integer
---@field group integer
---@field toString fun(self: sorting.Entry): string

---@class sorting.Entry.type: temper.Type
---@operator call: sorting.Entry
local Entry = temper.type("sorting.Entry")

---@param this sorting.Entry
---@param id integer
---@param group integer
function Entry.constructor(this, id, group)
  this.id = id
  this.group = group
end

---@param this sorting.Entry
---@return string
function Entry.methods.toString(this)
  return "{ id: " .. this.id .. ", group: " .. this.group .. " }"
end

with_tostring(Entry)

---@param ints integer[]
local function mungeInts(ints)
  local builder = temper.list_tolistbuilder(ints)
  temper.listbuilder_sort(builder, function(a, b) return temper.int32_sub(a, b) end)
  temper.log(temper.listed_join(temper.listed_sorted(ints, function(a2, b2) return temper.int32_sub(a2, b2) end), ", ", function(a3) return tostring(a3) end))
  temper.log(temper.listed_join(builder, ", ", function(a4) return tostring(a4) end))
end

---@param floats number[]
local function mungeFloats(floats)
  local builder = temper.list_tolistbuilder(floats)
  ---@param a number
  ---@param b number
  ---@return integer
  local function compareFloats(a, b)
    return temper.float64_toint32unsafe(temper.float64_sign(a - b))
  end
  temper.listbuilder_sort(builder, compareFloats)
  temper.log(temper.listed_join(temper.listed_sorted(floats, compareFloats), ", ", function(a2) return temper.float64_tostring(a2) end))
  temper.log(temper.listed_join(builder, ", ", function(a3) return temper.float64_tostring(a3) end))
end

local entries = temper.list_tolistbuilder({ Entry(0, 0), Entry(1, 1), Entry(2, 2), Entry(3, 0), Entry(4, 1), Entry(5, 2), Entry(6, 0), Entry(7, 1), Entry(8, 2) })
local sortedEntries = temper.listed_sorted(entries, function(a, b) return temper.int32_sub(a.group, b.group) end)
temper.log(temper.listed_join(sortedEntries, "\n", function(a) return a:toString() end))
temper.log(temper.listed_get(entries, 1):toString())
temper.listbuilder_sort(entries, function(a, b) return temper.int32_sub(a.group, b.group) end)
temper.log(temper.listed_join(entries, "\n", function(a) return a:toString() end))
mungeInts({ 2, 1 })
mungeFloats({ 3.4, 1.2 })

return {}
