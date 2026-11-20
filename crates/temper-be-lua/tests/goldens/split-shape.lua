local temper = require("temper-core")

local items = temper.list_of_parts((function() return { "item 0 é✓𝄞", "item 1 é✓𝄞", "item 2 é✓𝄞", "item 3 é✓𝄞" } end)(), (function() return { "item 4 é✓𝄞", "item 5 é✓𝄞", "item 6 é✓𝄞", "item 7 é✓𝄞" } end)(), (function() return { "item 8 é✓𝄞", "item 9 é✓𝄞" } end)())
local table_ = temper.map_constructor(temper.list_of_parts((function() return { temper.pair_constructor("k0", "item 0 é✓𝄞"), temper.pair_constructor("k1", "item 1 é✓𝄞"), temper.pair_constructor("k2", "item 2 é✓𝄞"), temper.pair_constructor("k3", "item 3 é✓𝄞") } end)(), (function() return { temper.pair_constructor("k4", "item 4 é✓𝄞"), temper.pair_constructor("k5", "item 5 é✓𝄞") } end)()))
local long = "abcdefghij é✓𝄞 \000 abcdefghij é✓𝄞 \000 "
local built = temper.listbuilder_constructor()
temper.listbuilder_add(built, "item 0 é✓𝄞")
temper.listbuilder_add(built, "item 1 é✓𝄞")
temper.listbuilder_add(built, "item 2 é✓𝄞")
temper.listbuilder_add(built, "item 3 é✓𝄞")
temper.listbuilder_add(built, "item 4 é✓𝄞")
temper.listbuilder_add(built, "item 5 é✓𝄞")
temper.listbuilder_add(built, "item 6 é✓𝄞")
temper.listbuilder_add(built, "item 7 é✓𝄞")
temper.listbuilder_add(built, "item 8 é✓𝄞")
temper.listbuilder_add(built, "item 9 é✓𝄞")
local all = temper.listbuilder_tolist(built)
print(temper.list_length(items) .. " " .. temper.list_get(items, 9))
local string_ = temper.mapped_length(table_) .. " "
---@type string
local t
if not temper.pcall(function()
  t = temper.mapped_get(table_, "k5")
end) then
  t = "none"
end
print(string_ .. t)
print(tostring(temper.list_length(temper.string_split(long, "✓"))))
print(temper.list_length(all) .. " " .. temper.list_get(all, 9))

return {}
