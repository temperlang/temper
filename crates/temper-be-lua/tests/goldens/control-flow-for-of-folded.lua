local temper = require("temper-core")

---@param xs integer[]
---@return integer
local function total(xs)
  local t = 0
  do
    local length = temper.list_length(xs)
    local i = 0
    while i < length do
      local x = temper.list_get(xs, i)
      i = temper.int32_add(i, 1)
      t = temper.int32_add(t, x)
    end
  end
  return t
end

---@param xs integer[]
---@return integer
local function totalForEach(xs)
  local t = 0
  do
    local length = temper.list_length(xs)
    local i = 0
    while i < length do
      local x = temper.list_get(xs, i)
      i = temper.int32_add(i, 1)
      t = temper.int32_add(t, x)
    end
  end
  return t
end

---@param xs integer[]
---@return integer
local function totalFiltered(xs)
  local t = 0
  ---@param x integer
  ---@return boolean
  local function fn(x)
    t = temper.int32_add(t, x)
    return true
  end
  temper.listed_filter(xs, fn)
  return t
end

---@param xss integer[][]
---@return integer
local function totalNested(xss)
  local t = 0
  do
    local length = temper.list_length(xss)
    local i = 0
    while i < length do
      local xs = temper.list_get(xss, i)
      i = temper.int32_add(i, 1)
      do
        local length2 = temper.list_length(xs)
        local i2 = 0
        while i2 < length2 do
          local x = temper.list_get(xs, i2)
          i2 = temper.int32_add(i2, 1)
          t = temper.int32_add(t, x)
        end
      end
    end
  end
  return t
end

---@param xs integer[]
---@param n integer
---@return integer
local function firstOver(xs, n)
  do
    local length = temper.list_length(xs)
    local i = 0
    while i < length do
      local x = temper.list_get(xs, i)
      i = temper.int32_add(i, 1)
      if x > n then
        return x
      end
    end
  end
  return -1
end

temper.log("total " .. total({ 1, 2, 3 }))
temper.log("forEach " .. totalForEach({ 1, 2, 3 }))
temper.log("filter " .. totalFiltered({ 1, 2, 3 }))
temper.log("nested " .. totalNested({ { 1, 2 }, { 3 }, {} }))
temper.log("firstOver " .. firstOver({ 1, 2, 3 }, 1) .. " " .. firstOver({ 1, 2, 3 }, 5))

return {}
