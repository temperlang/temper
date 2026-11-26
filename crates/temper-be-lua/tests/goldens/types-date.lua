local temper = require("temper-core")
local Date = require("std/temporal").Date

local d = temper.date_constructor(2023, 6, 29)
temper.log("year    = " .. temper.date_year(d) .. "\nmonth   = " .. temper.date_month(d) .. "\nday     = " .. temper.date_day(d) .. "\nweekday = " .. temper.date_dayofweek(d))
temper.log("ISO 8601: " .. temper.date_tostring(d))
local yonksAgo = temper.date_constructor(12, 11, 10)
temper.log("ISO 8601 yonks: " .. temper.date_tostring(yonksAgo))
temper.log("yonksAgo.year = " .. temper.date_year(yonksAgo))
do
  local list = { 1700, 1800, 1900, 2000, 2100, 2400 }
  local length = temper.list_length(list)
  local i = 0
  while i < length do
    local y = temper.list_get(list, i)
    i = temper.int32_add(i, 1)
    temper.log(y .. ": Jan 1 weekday " .. temper.date_dayofweek(temper.date_constructor(y, 1, 1)) .. ", Mar 1 weekday " .. temper.date_dayofweek(temper.date_constructor(y, 3, 1)))
  end
end
temper.date_today()
local start = temper.date_constructor(2010, 3, 21)
local firstYears = temper.date_yearsbetween(start, temper.date_constructor(2012, 3, 20))
temper.log("First years between: " .. firstYears)
local secondYears = temper.date_yearsbetween(start, temper.date_constructor(2012, 3, 21))
temper.log("Second years between: " .. secondYears)
local rdc = temper.date_fromisostring("1596-03-31")
temper.log("Happy birthday, René: day=" .. temper.date_day(rdc) .. ", month=" .. temper.date_month(rdc) .. ", year=" .. temper.date_year(rdc))

return {}
