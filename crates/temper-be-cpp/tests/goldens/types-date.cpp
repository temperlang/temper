// init.hpp
#pragma once

#include <temper-core/core.hpp>
#include <std/temporal.hpp>

namespace date {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace date

// init.cpp
#include <date/init.hpp>

namespace date {

namespace core = temper::core;

static core::Str checkDate(int32_t year, int32_t month, int32_t day) {
  auto dateResult = temper_std::Date::make(year, month, day);
  if (dateResult.has_value()) {
    return core::Date::toString(*dateResult);
  } else {
    std::shared_ptr<temper_std::TemporalError> e = dateResult.error();
    return e->get_message();
  }
}

void global_init_init() {
  core::init_once([] {
    temper_std::global_init_temporal();
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    auto dateResult = temper_std::Date::make(2023, 6, 29);
    if (!dateResult.has_value()) {
      core::uncaught_failure(dateResult.error());
    }
    std::shared_ptr<temper_std::Date> d = *dateResult;
    core::Console::log(console, "year    = " + std::to_string(d->year) + "\nmonth   = " + std::to_string(d->month) + "\nday     = " + std::to_string(d->day) + "\nweekday = " + std::to_string(((core::Date::getDayOfWeek(d) + 6) % 7) + 1));
    core::Console::log(console, "ISO 8601: " + core::Date::toString(d));
    auto dateResult2 = temper_std::Date::make(12, 11, 10);
    if (!dateResult2.has_value()) {
      core::uncaught_failure(dateResult2.error());
    }
    std::shared_ptr<temper_std::Date> yonksAgo = *dateResult2;
    core::Console::log(console, "ISO 8601 yonks: " + core::Date::toString(yonksAgo));
    core::Console::log(console, "yonksAgo.year = " + std::to_string(yonksAgo->year));
    {
      std::shared_ptr<std::vector<int32_t>> list = core::List::make<int32_t>(1700, 1800, 1900, 2000, 2100, 2400);
      int32_t length = core::List::length<int32_t>(list);
      int32_t i = 0;
      while (i < length) {
        int32_t y = core::List::get<int32_t>(list, i);
        i = core::Int::add(i, 1);
        auto dateResult3 = temper_std::Date::make(y, 1, 1);
        if (!dateResult3.has_value()) {
          core::uncaught_failure(dateResult3.error());
        }
        int32_t int32 = ((core::Date::getDayOfWeek(*dateResult3) + 6) % 7) + 1;
        auto dateResult4 = temper_std::Date::make(y, 3, 1);
        if (!dateResult4.has_value()) {
          core::uncaught_failure(dateResult4.error());
        }
        core::Console::log(console, std::to_string(y) + ": Jan 1 weekday " + std::to_string(int32) + ", Mar 1 weekday " + std::to_string(((core::Date::getDayOfWeek(*dateResult4) + 6) % 7) + 1));
      }
    }
    core::Str string = checkDate(2024, 2, 29);
    core::Console::log(console, string + " " + checkDate(2023, 2, 29));
    auto fn = [](core::Str s) -> core::Str {
      auto fromIsoStringResult = temper_std::Date::fromIsoString(s);
      if (fromIsoStringResult.has_value()) {
        return core::Date::toString(*fromIsoStringResult);
      } else {
        return TEMPER_STR("none");
      }
    };
    std::shared_ptr<std::vector<core::Str>> parsed = core::List::map(core::List::make<core::Str>(TEMPER_STR("2024-02-29"), TEMPER_STR("2023-02-29"), TEMPER_STR("2023-2-1")), fn);
    core::Console::log(console, core::List::join(parsed, TEMPER_STR(" "), [](core::Str it) -> core::Str { return it; }));
    core::Date::today<temper_std::Date>();
    auto dateResult5 = temper_std::Date::make(2010, 3, 21);
    if (!dateResult5.has_value()) {
      core::uncaught_failure(dateResult5.error());
    }
    std::shared_ptr<temper_std::Date> start = *dateResult5;
    auto dateResult6 = temper_std::Date::make(2012, 3, 20);
    if (!dateResult6.has_value()) {
      core::uncaught_failure(dateResult6.error());
    }
    int32_t firstYears = core::Date::yearsBetween(start, *dateResult6);
    core::Console::log(console, "First years between: " + std::to_string(firstYears));
    auto dateResult7 = temper_std::Date::make(2012, 3, 21);
    if (!dateResult7.has_value()) {
      core::uncaught_failure(dateResult7.error());
    }
    int32_t secondYears = core::Date::yearsBetween(start, *dateResult7);
    core::Console::log(console, "Second years between: " + std::to_string(secondYears));
    std::shared_ptr<temper_std::Date> rdc;
    auto fromIsoStringResult2 = temper_std::Date::fromIsoString(TEMPER_STR("1596-03-31"));
    if (fromIsoStringResult2.has_value()) {
      rdc = *fromIsoStringResult2;
    } else {
      core::panic();
    }
    core::Console::log(console, "Happy birthday, René: day=" + std::to_string(rdc->day) + ", month=" + std::to_string(rdc->month) + ", year=" + std::to_string(rdc->year));
  });
}

}  // namespace date
