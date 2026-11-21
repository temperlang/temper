local temper = require("temper-core")

local n = 200000
local source = temper.listbuilder_constructor()
do
  local i = 0
  while i < n do
    temper.listbuilder_add(source, i)
    i = temper.int32_add(i, 1)
  end
end
local values = temper.listbuilder_tolist(source)
local b = temper.listbuilder_constructor()
temper.listbuilder_addall(b, values)
temper.listbuilder_addall(b, values, 0)
temper.listbuilder_add(b, -1, n)
temper.listbuilder_addall(b, values, n)
temper.log(temper.listbuilder_length(b) .. " " .. temper.listed_get(b, 0) .. " " .. temper.listed_get(b, temper.int32_sub(n, 1)) .. " " .. temper.listed_get(b, n) .. " " .. temper.listed_get(b, temper.int32_mul(2, n)) .. " " .. temper.listed_get(b, temper.int32_add(temper.int32_mul(2, n), 1)) .. " " .. temper.listed_get(b, temper.int32_mul(3, n)))
local twice = temper.listbuilder_constructor()
temper.listbuilder_addall(twice, values)
temper.listbuilder_addall(twice, twice, 1)
temper.log(temper.listbuilder_length(twice) .. " " .. temper.listed_get(twice, 0) .. " " .. temper.listed_get(twice, 1) .. " " .. temper.listed_get(twice, n) .. " " .. temper.listed_get(twice, temper.int32_add(n, 1)))
local s = temper.listbuilder_constructor()
temper.listbuilder_add(s, 7)
temper.listbuilder_add(s, 8)
local removed = temper.listbuilder_splice(s, 1, 0, values)
temper.log(temper.listbuilder_length(s) .. " " .. temper.list_length(removed) .. " " .. temper.listed_get(s, 0) .. " " .. temper.listed_get(s, 1) .. " " .. temper.listed_get(s, n) .. " " .. temper.listed_get(s, temper.int32_add(n, 1)))
local c = temper.listbuilder_constructor()
temper.listbuilder_add(c, 7)
temper.listbuilder_add(c, 8)
temper.listbuilder_splice(c, -1, 0, values)
temper.listbuilder_splice(c, temper.int32_mul(10, n), 0, values)
temper.log(temper.listbuilder_length(c) .. " " .. temper.listed_get(c, 0) .. " " .. temper.listed_get(c, temper.int32_sub(n, 1)) .. " " .. temper.listed_get(c, n) .. " " .. temper.listed_get(c, temper.int32_add(n, 1)) .. " " .. temper.listed_get(c, temper.int32_add(n, 2)) .. " " .. temper.listed_get(c, temper.int32_add(temper.int32_mul(2, n), 1)))
local own = temper.listbuilder_constructor()
temper.listbuilder_addall(own, values)
local gone = temper.listbuilder_splice(own, 0, 1, own)
temper.log(temper.listbuilder_length(own) .. " " .. temper.list_length(gone) .. " " .. temper.listed_get(own, 0) .. " " .. temper.listed_get(own, temper.int32_sub(n, 1)) .. " " .. temper.listed_get(own, n) .. " " .. temper.listed_get(own, temper.int32_sub(temper.int32_mul(2, n), 2)))
local kept = temper.listed_filter(values, function(x) return x ~= 5 end)
temper.log(temper.list_length(kept) .. " " .. temper.list_get(kept, 4) .. " " .. temper.list_get(kept, 5) .. " " .. temper.list_get(kept, temper.int32_sub(n, 2)))
local codes = temper.listbuilder_constructor()
do
  local i2 = 0
  while i2 < n do
    temper.listbuilder_add(codes, temper.int32_add(97, temper.int32_mod(i2, 3)))
    i2 = temper.int32_add(i2, 1)
  end
end
---@type string
local text
if not temper.pcall(function()
  text = temper.string_fromcodepoints(temper.listbuilder_tolist(codes))
end) then
  text = ""
end
local four = temper.string_next(text, temper.string_next(text, temper.string_next(text, temper.string_next(text, 1))))
temper.log(temper.string_countbetween(text, 1, temper.string_end(text)) .. " " .. temper.string_slice(text, 1, four))

return {}
