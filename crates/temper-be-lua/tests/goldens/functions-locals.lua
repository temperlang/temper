local temper = require("temper-core")

---@param x integer[]
---@return string
local function show(x)
  local s = temper.listbuilder_constructor()
  temper.listbuilder_add(s, "[")
  local c = ""
  do
    local i = 0
    while i < temper.listed_length(x) do
      temper.listbuilder_add(s, c)
      temper.listbuilder_add(s, tostring(temper.listed_get(x, i)))
      c = ", "
      i = temper.int32_add(i, 1)
    end
  end
  temper.listbuilder_add(s, "]")
  return temper.listed_join(s, "", function(v) return v end)
end

---@generic T
---@param b fun(): T
---@return T
local function nestBlock(b)
  return b()
end

---@param b fun()
local function nestBlockVoid(b)
  b()
end

---@param x integer[]
---@param a integer
local function constantCapture(x, a)
  local b = temper.int32_mul(a, 1)
  local out = temper.listed_map(x, function(n) return temper.int32_add(n, b) end)
  temper.log("constantCapture = " .. show(out))
end

---@param x integer[]
---@param a integer
local function constantCaptureNested(x, a)
  local b = temper.int32_mul(a, 1)
  local out = nestBlock(function() return temper.listed_map(x, function(n) return temper.int32_add(n, b) end) end)
  temper.log("constantCaptureNested = " .. show(out))
end

---@param x integer[]
local function mutableCapture(x)
  local sum = 0
  ---@param n integer
  ---@return integer
  local function fn(n)
    sum = temper.int32_add(sum, n)
    return sum
  end
  local out = temper.listed_map(x, fn)
  temper.log("mutableCapture = " .. show(out) .. "; sum=" .. sum)
end

---@param x integer[]
local function mutableCaptureNested(x)
  local sum = 0
  ---@return integer[]
  local function fn()
    ---@param n integer
    ---@return integer
    local function fn2(n)
      sum = temper.int32_add(sum, n)
      return sum
    end
    return temper.listed_map(x, fn2)
  end
  local out = nestBlock(fn)
  temper.log("mutableCaptureNested = " .. show(out) .. "; sum=" .. sum)
end

---@param values integer[]
---@param value integer
local function thresh(values, value)
  value = temper.int32_add(value, 1)
  local result = temper.listed_filter(values, function(it) return it > value end)
  temper.log("thresh length = " .. temper.list_length(result))
end

---@param values integer[]
---@param value integer
local function thresh2(values, value)
  local value2 = value
  value2 = temper.int32_add(value2, 1)
  local result = temper.listed_filter(values, function(it) return it > value2 end)
  temper.log("thresh2 length = " .. temper.list_length(result))
end

---@param x integer[]
local function selfRecursion(x)
  ---@param i integer
  ---@param out integer[]
  local function mapper(i, out)
    if i < temper.list_length(x) then
      temper.listbuilder_add(out, temper.int32_mul(temper.list_get(x, i), 2))
      mapper(temper.int32_add(i, 1), out)
    end
  end
  local out2 = temper.listbuilder_constructor()
  mapper(0, out2)
  temper.log("selfRecursion = " .. show(out2))
end

---@param x integer[]
local function selfRecursionNested(x)
  ---@param i integer
  ---@param out integer[]
  local function mapper(i, out)
    local function fn2()
      if i < temper.list_length(x) then
        temper.listbuilder_add(out, temper.int32_mul(temper.list_get(x, i), 2))
        mapper(temper.int32_add(i, 1), out)
      end
    end
    nestBlockVoid(fn2)
  end
  local out2 = temper.listbuilder_constructor()
  mapper(0, out2)
  temper.log("selfRecursionNested = " .. show(out2))
end

---@param x integer[]
local function mutualRecursion(x)
  ---@type fun(arg0: integer, arg1: integer[])
  local putter
  ---@param i integer
  ---@param out integer[]
  local function walker(i, out)
    if i < temper.list_length(x) then
      putter(i, out)
    end
  end
  ---@param i2 integer
  ---@param out2 integer[]
  function putter(i2, out2)
    temper.listbuilder_add(out2, temper.int32_mul(temper.list_get(x, i2), 2))
    walker(temper.int32_add(i2, 1), out2)
  end
  local out3 = temper.listbuilder_constructor()
  walker(0, out3)
  temper.log("mutualRecursion = " .. show(out3))
end

---@class locals.Handler
---@field apply fun(self: locals.Handler, item: any)

---@class locals.Handler.type: temper.Type
local Handler = temper.type("locals.Handler")

---@class locals.Hub
---@field handlers fun(arg0: any)[]
---@field onAction fun(self: locals.Hub, handler: fun(arg0: any))
---@field actionPerformed fun(self: locals.Hub, item: any)

---@class locals.Hub.type: temper.Type
---@operator call: locals.Hub
local Hub = temper.type("locals.Hub")

---@param this locals.Hub
function Hub.constructor(this)
  this.handlers = temper.listbuilder_constructor()
end

---@param this locals.Hub
---@param handler fun(arg0: any)
function Hub.methods.onAction(this, handler)
  temper.listbuilder_add(this.handlers, handler)
end

---@param this locals.Hub
---@param item any
function Hub.methods.actionPerformed(this, item)
  do
    local i = 0
    while i < temper.listbuilder_length(this.handlers) do
      temper.listed_get(this.handlers, i)(item)
      i = temper.int32_add(i, 1)
    end
  end
end

---@param hub locals.Hub
---@return integer[]
local function gatherHandlers(hub)
  local items = temper.listbuilder_constructor()
  do
    local i = 0
    while i < 3 do
      local i2 = i
      hub:onAction(function(item) temper.listbuilder_add(items, temper.int32_add(item, i2)) end)
      i = temper.int32_add(i, 1)
    end
  end
  return items
end

constantCapture({ 1, 2, 3 }, 1)
constantCaptureNested({ 1, 2, 3 }, 1)
mutableCapture({ 1, 2, 3 })
mutableCaptureNested({ 1, 2, 3 })
thresh({ 4, 5, 6 }, 4)
thresh2({ 4, 5, 6 }, 4)
selfRecursion({ 1, 2, 3 })
selfRecursionNested({ 1, 2, 3 })
mutualRecursion({ 1, 2, 3 })
local hub = Hub()
local items = gatherHandlers(hub)
hub:actionPerformed(1)

return { thresh = thresh, thresh2 = thresh2 }
