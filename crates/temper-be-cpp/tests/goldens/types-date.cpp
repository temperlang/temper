// init.hpp
#pragma once

#include <temper-core/base.hpp>
#include <std/temporal.hpp>

namespace date {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace date

// init.cpp
#include <date/init.hpp>

#include <temper-core/int.hpp>
#include <temper-core/console.hpp>
#include <temper-core/date.hpp>

namespace date {

namespace core = temper::core;

void global_init_init() {
  core::init_once([] {
    temper_std::global_init_temporal();
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    std::shared_ptr<temper_std::Date> d = core::Date::makeDate<temper_std::Date>(2023, 6, 29);
    core::Console::log(console, "year    = " + std::to_string(d->year) + "\nmonth   = " + std::to_string(d->month) + "\nday     = " + std::to_string(d->day) + "\nweekday = " + std::to_string(((core::Date::getDayOfWeek(d) + 6) % 7) + 1));
    core::Console::log(console, "ISO 8601: " + core::Date::toString(d));
    std::shared_ptr<temper_std::Date> yonksAgo = core::Date::makeDate<temper_std::Date>(12, 11, 10);
    core::Console::log(console, "ISO 8601 yonks: " + core::Date::toString(yonksAgo));
    core::Console::log(console, "yonksAgo.year = " + std::to_string(yonksAgo->year));
    {
      std::shared_ptr<std::vector<int32_t>> list = core::List::make<int32_t>(1700, 1800, 1900, 2000, 2100, 2400);
      int32_t length = core::List::length<int32_t>(list);
      int32_t i = 0;
      while (i < length) {
        int32_t y = core::List::get<int32_t>(list, i);
        i = core::Int::add(i, 1);
        int32_t int32 = ((core::Date::getDayOfWeek(core::Date::makeDate<temper_std::Date>(y, 1, 1)) + 6) % 7) + 1;
        core::Console::log(console, std::to_string(y) + ": Jan 1 weekday " + std::to_string(int32) + ", Mar 1 weekday " + std::to_string(((core::Date::getDayOfWeek(core::Date::makeDate<temper_std::Date>(y, 3, 1)) + 6) % 7) + 1));
      }
    }
    core::Date::today<temper_std::Date>();
    std::shared_ptr<temper_std::Date> start = core::Date::makeDate<temper_std::Date>(2010, 3, 21);
    int32_t firstYears = core::Date::yearsBetween(start, core::Date::makeDate<temper_std::Date>(2012, 3, 20));
    core::Console::log(console, "First years between: " + std::to_string(firstYears));
    int32_t secondYears = core::Date::yearsBetween(start, core::Date::makeDate<temper_std::Date>(2012, 3, 21));
    core::Console::log(console, "Second years between: " + std::to_string(secondYears));
    std::shared_ptr<temper_std::Date> rdc = core::Date::fromIso<temper_std::Date>(TEMPER_STR("1596-03-31"));
    core::Console::log(console, "Happy birthday, René: day=" + std::to_string(rdc->day) + ", month=" + std::to_string(rdc->month) + ", year=" + std::to_string(rdc->year));
  });
}

}  // namespace date
