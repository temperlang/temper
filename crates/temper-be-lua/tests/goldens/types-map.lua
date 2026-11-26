local temper = require("temper-core")

---@type table<integer, string>
local messages
---@type table<string, boolean[]>
local eightColors

---@generic K
---@generic V
---@param mapped table<K, V>
---@return K[]
local function listKeys(mapped)
  return temper.listed_map(temper.mapped_tolist(mapped), function(entry) return entry.key end)
end

---@param code integer
---@return string
local function messageDefined(code)
  return tostring(temper.mapped_has(messages, code))
end

---@param color boolean[]
---@return string
local function colorToHex(color)
  ---@param b boolean
  ---@return string
  local function fn(b)
    if b then
      return "FF"
    else
      return "00"
    end
  end
  local c = temper.listed_join(color, "", fn)
  return "#" .. c
end

---@param name string
---@param map table<string, string>
local function printMap(name, map)
  temper.log("--- " .. name .. " ---")
  temper.mapped_foreach(map, function(key, value) temper.log(key .. " = " .. value) end)
end

---@param name string
---@param map table<string, string>
local function printMapBuilder(name, map)
  temper.log("--- " .. name .. " ---")
  temper.mapped_foreach(map, function(key, value) temper.log(key .. " = " .. value) end)
end

messages = temper.map_constructor({ temper.pair_constructor(200, "OK"), temper.pair_constructor(404, "Not Found"), temper.pair_constructor(500, "Internal Server Error") })
local message = temper.mapped_get(messages, 200)
temper.log("Message: " .. message)
temper.log("Has 200: " .. messageDefined(200) .. ", 302: " .. messageDefined(302))
---@type string
local t
if not temper.pcall(function()
  t = temper.mapped_get(messages, 302)
end) then
  t = "Found"
end
temper.log("Fallback: " .. t)
temper.log("Codes: " .. temper.listed_join(listKeys(messages), " ", function(it) return tostring(it) end))
temper.log("Again: " .. temper.listed_join(temper.mapped_keys(messages), " ", function(it) return tostring(it) end))
temper.log("Messages: " .. temper.listed_join(temper.mapped_values(messages), ", ", function(it) return it end))
local groups = temper.mapped_tolistwith(messages, function(key, value) return temper.int32_div(key, 100) end)
temper.log("Groups: " .. temper.listed_join(groups, " ", function(it) return tostring(it) end))
local builder2 = temper.mapbuilder_constructor()
temper.mapbuilder_set(builder2, "Honduras", { "El Salvador", "Guatemala", "Nicaragua" })
temper.log("From builder: " .. temper.listed_join(temper.mapped_get(builder2, "Honduras"), ", ", function(it) return it end))
temper.log("Key so far: " .. temper.list_get(listKeys(builder2), 0))
temper.mapbuilder_set(builder2, "El Salvador", { "Guatemala", "Honduras" })
temper.mapbuilder_set(builder2, "Nicaragua", { "Costa Rica", "Honduras" })
temper.mapbuilder_set(builder2, "Panama", { "Colombia", "Costa Rica" })
if not temper.pcall(function()
  temper.mapbuilder_remove(builder2, "Honduras")
end) then
end
local was = temper.mapbuilder_remove(builder2, "El Salvador")
temper.log("Was there: " .. temper.listed_join(was, ", ", function(it) return it end))
---@type string
local t_1
if not temper.pcall(function()
  t_1 = temper.list_get(temper.mapbuilder_remove(builder2, "El Salvador"), 0)
end) then
  t_1 = "Gone"
end
temper.log(t_1)
local neighbors = temper.mapped_tomap(builder2)
temper.log("From built: " .. temper.listed_join(temper.mapped_get(neighbors, "Nicaragua"), ", ", function(it) return it end))
local mapOfNone = temper.map_constructor({})
local mapOfOne = temper.map_constructor({ temper.pair_constructor("jack", "all trades") })
local mapOfTwo = temper.map_constructor({ temper.pair_constructor(1, "one"), temper.pair_constructor(2, "electric boogaloo") })
temper.log("mapOfNone: " .. temper.mapped_length(mapOfNone))
temper.log("mapOfOne: " .. temper.mapped_length(mapOfOne))
temper.log("mapOfTwo: " .. temper.mapped_length(mapOfTwo))
eightColors = temper.mapbuilder_constructor()
temper.mapbuilder_set(eightColors, "black", { false, false, false })
temper.mapbuilder_set(eightColors, "red", { true, false, false })
temper.mapbuilder_set(eightColors, "green", { false, true, false })
temper.mapbuilder_set(eightColors, "blue", { false, false, true })
temper.mapbuilder_set(eightColors, "yellow", { true, true, false })
temper.mapbuilder_set(eightColors, "cyan", { false, true, true })
temper.mapbuilder_set(eightColors, "purple", { true, false, true })
temper.mapbuilder_set(eightColors, "white", { true, true, true })
temper.log("with 3 bits you can draw " .. temper.mapped_length(eightColors) .. " colors.")

---@param color string
---@param bits boolean[]
local function fn9(color, bits)
  local foundByGetOr = temper.mapped_getor(eightColors, color, {})
  if temper.list_length(foundByGetOr) ~= 3 then
    temper.log('getOr("' .. color .. '") failed to find key')
  end
  local want = colorToHex(bits)
  local got = colorToHex(foundByGetOr)
  if want ~= got then
    temper.log('getOr("' .. color .. '") found ' .. got .. " but wanted " .. want)
  end
end

temper.mapped_foreach(eightColors, fn9)
local map = temper.map_constructor({ temper.pair_constructor("key", "value"), temper.pair_constructor("other key", "other value") })
printMap("map", map)
local builder = temper.mapped_tomapbuilder(map)
temper.mapbuilder_set(builder, "added key", "added value")
temper.mapbuilder_set(builder, "key", "replaced value")
printMapBuilder("builder", builder)
printMap("map (same as above)", map)
local newMap = temper.mapped_tomap(builder)
printMap("builder to map", newMap)
local builderAsMapped = builder
local newMapFromMapped = temper.mapped_tomap(builderAsMapped)
temper.mapbuilder_remove(builder, "other key")
temper.mapbuilder_set(builder, "key", "double replaced value")
printMap("builder to map (same as above)", newMap)
printMap("builder to map via mapped (same as above)", newMapFromMapped)
temper.mapbuilder_clear(builder)
temper.mapbuilder_set(builder, "something", "alone")
printMapBuilder("recently cleared", builder)
local mayOrMayNotCopyMap = temper.mapped_tomap(map)
local mapped = map
local mayOrMayNotCopyMapped = temper.mapped_tomap(mapped)
local madeBy = temper.mapbuilder_constructor()
temper.mapbuilder_set(madeBy, "temper", "temper systems")
printMapBuilder("made by", madeBy)
local madeByAndJokes = temper.mapped_tomapbuilder(madeBy)
temper.mapbuilder_set(madeByAndJokes, "milk", "milking a cow")
printMapBuilder("made by (and a joke about milk)", madeByAndJokes)
printMapBuilder("made by (not changed from a change to a copy)", madeBy)

return {}
