local temper = require("temper-core")

---@generic T
---@param items T[]
---@param check fun(arg0: T): boolean
---@return boolean
local function all(items, check)
  ---@generic T
  ---@param result boolean
  ---@param it T
  ---@return boolean
  local function fn(result, it)
    if result then
      return check(it)
    else
      return false
    end
  end
  return temper.listed_reducefrom(items, true, fn)
end

local vals = { 2, 3, 4 }
local sum = temper.listed_reduce(vals, function(sum, n) return temper.int32_add(sum, n) end)
local poorReverseJoin = temper.listed_reducefrom(vals, "", function(text, n) return n .. text end)
temper.log("Reductions: " .. sum .. " " .. poorReverseJoin)
temper.log("All even: " .. tostring(all(vals, function(n) return temper.int32_mod(n, 2) == 0 end)))
temper.log("All positive: " .. tostring(all(vals, function(n) return n > 0 end)))

return {}
