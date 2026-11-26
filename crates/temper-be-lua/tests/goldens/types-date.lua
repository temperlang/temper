local temper = require("temper-core")
local Date = require("std/temporal").Date
local TemporalError = require("std/temporal").TemporalError

---@param year integer
---@param month integer
---@param day integer
---@return string
local function checkDate(year, month, day)
  ---@type string
  local t
  local t_1, e = temper.catch(function()
    t = temper.date_tostring(temper.date_constructor(year, month, day))
  end, TemporalError)
  if not t_1 then
    t = e.message
  end
  return t
end

local d = temper.date_constructor(2023, 6, 29)
print("year    = " .. temper.date_year(d) .. "\nmonth   = " .. temper.date_month(d) .. "\nday     = " .. temper.date_day(d) .. "\nweekday = " .. temper.date_dayofweek(d))
print("ISO 8601: " .. temper.date_tostring(d))
local yonksAgo = temper.date_constructor(12, 11, 10)
print("ISO 8601 yonks: " .. temper.date_tostring(yonksAgo))
print("yonksAgo.year = " .. temper.date_year(yonksAgo))
do
  local list = { 1700, 1800, 1900, 2000, 2100, 2400 }
  local length = temper.list_length(list)
  local i = 0
  while i < length do
    local y = temper.list_get(list, i)
    i = temper.int32_add(i, 1)
    print(y .. ": Jan 1 weekday " .. temper.date_dayofweek(temper.date_constructor(y, 1, 1)) .. ", Mar 1 weekday " .. temper.date_dayofweek(temper.date_constructor(y, 3, 1)))
  end
end
print(checkDate(2024, 2, 29) .. " " .. checkDate(2023, 2, 29))

---@param s string
---@return string
local function fn(s)
  ---@type string
  local t
  if not temper.catch(function()
    t = temper.date_tostring(temper.date_fromisostring(s))
  end, TemporalError) then
    t = "none"
  end
  return t
end

local parsed = temper.listed_map({ "2024-02-29", "2023-02-29", "2023-2-1" }, fn)
print(temper.listed_join(parsed, " ", function(it) return it end))
temper.date_today()
local start = temper.date_constructor(2010, 3, 21)
local firstYears = temper.date_yearsbetween(start, temper.date_constructor(2012, 3, 20))
print("First years between: " .. firstYears)
local secondYears = temper.date_yearsbetween(start, temper.date_constructor(2012, 3, 21))
print("Second years between: " .. secondYears)
---@type std.temporal.Date
local rdc
local t_1, t_2 = temper.catch(function()
  rdc = temper.date_fromisostring("1596-03-31")
end, TemporalError)
if not t_1 then
  error("panic\ncaused by: " .. tostring(t_2))
end
print("Happy birthday, René: day=" .. temper.date_day(rdc) .. ", month=" .. temper.date_month(rdc) .. ", year=" .. temper.date_year(rdc))

return {}
