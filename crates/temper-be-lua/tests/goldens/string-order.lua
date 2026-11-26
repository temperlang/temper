local temper = require("temper-core")

---@param a string
---@param b string
---@return string
local function show(a, b)
  return temper.str_cmp(a, b) .. " " .. tostring(a < b) .. " " .. tostring(a <= b) .. " " .. tostring(a > b) .. " " .. tostring(a >= b)
end

---@param a number
---@param b number
---@return integer
local function cmp(a, b)
  return temper.float_cmp(a, b)
end

temper.log(show("｡", "😀"))
temper.log(show("😀", "｡"))
temper.log(show("", "𐀀"))
temper.log(show("￿", "􏿿"))
temper.log(show("a｡b", "a😀"))
temper.log(show("😀", "😁"))
temper.log(show("😀", "😀"))
temper.log(show("😀", "😀a"))
temper.log(show("", "😀"))
temper.log(show("", ""))
temper.log(show("z", "a"))
temper.log(show("abc", "abd"))
temper.log(cmp(2.5, 1.0) .. " " .. cmp(1.0, 2.5) .. " " .. cmp(1.5, 1.5))

return {}
