// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace core_numbers {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace core_numbers

// init.cpp
#include <core-numbers/init.hpp>

namespace core_numbers {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<std::vector<int32_t>>> digits;

}  // namespace temper_globals

static int32_t from(int32_t index) {
  return core::List::reduceFromIndex(temper_globals::digits.value, 100, index, [](int32_t s, int32_t n) -> int32_t { return core::Int::add(core::Int::mul(s, 10), n); });
}

void global_init_init() {
  core::init_once([] {
    temper_globals::digits.construct();
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    int32_t one = 1;
    int32_t big = core::Int::mul(2147483647, one);
    int32_t small = core::Int::mul(INT32_MIN, one);
    core::Console::log(console, std::to_string(core::Int::add(big, 1)) + " " + std::to_string(core::Int::sub(small, 1)) + " " + std::to_string(core::Int::add(one, 1)) + " " + std::to_string(core::Int::sub(one, 1)) + " " + std::to_string(core::Int::add(small, 1)) + " " + std::to_string(core::Int::sub(big, 1)));
    int64_t one64 = core::Int::toInt64(one);
    int64_t big64 = core::Int64::mul(int64_t{9223372036854775807}, one64);
    int64_t small64 = core::Int64::sub(core::Int64::neg(big64), one64);
    core::Console::log(console, std::to_string(core::Int64::add(big64, int64_t{1})) + " " + std::to_string(core::Int64::sub(small64, int64_t{1})) + " " + std::to_string(core::Int64::sub(one64, int64_t{1})));
    int32_t zero = core::Int::sub(one, one);
    int32_t signum = core::Int::signum(big);
    int32_t int32 = core::Int::signum(small);
    int32_t value = core::Int::signum(zero);
    int32_t value2 = core::Int::signum(one);
    int32_t value3 = core::Int::signum(core::Int::sub(zero, one));
    core::Console::log(console, std::to_string(signum) + " " + std::to_string(int32) + " " + std::to_string(value) + " " + std::to_string(value2) + " " + std::to_string(value3) + " " + std::to_string(core::Int::signum(core::Int::add(small, 1))));
    temper_globals::digits.value = core::List::make<int32_t>(1, 2, 3);
    int32_t value4 = from(0);
    int32_t value5 = from(1);
    int32_t value6 = from(3);
    core::Console::log(console, std::to_string(value4) + " " + std::to_string(value5) + " " + std::to_string(value6) + " " + std::to_string(from(7)));
    double inf = 1e308 * 10.0;
    double nan = inf - inf;
    double ninf = 0.0 - inf;
    core::Console::log(console, core::Boolean::toString(core::Float64::near(nan, nan)) + " " + core::Boolean::toString(core::Float64::near(inf, inf)) + " " + core::Boolean::toString(core::Float64::near(ninf, ninf)) + " " + core::Boolean::toString(core::Float64::near(nan, 1.0)) + " " + core::Boolean::toString(core::Float64::near(1.0, nan)));
    core::Console::log(console, core::Boolean::toString(core::Float64::near(inf, 1.0)) + " " + core::Boolean::toString(core::Float64::near(inf, ninf)) + " " + core::Boolean::toString(core::Float64::near(inf, 1.0, 0.0, 0.0)) + " " + core::Boolean::toString(core::Float64::near(inf, inf, 0.0, 0.0)) + " " + core::Boolean::toString(core::Float64::near(inf, 1.0, 1.0, inf)) + " " + core::Boolean::toString(core::Float64::near(1.0, ninf, 1.0, inf)));
    core::Console::log(console, core::Boolean::toString(core::Float64::near(1.0, 1.5, nan, 1.0)) + " " + core::Boolean::toString(core::Float64::near(1.0, 1.5, 1.0, nan)) + " " + core::Boolean::toString(core::Float64::near(1.0, 1.0, nan, nan)) + " " + core::Boolean::toString(core::Float64::near(-0.0, 0.0, 0.0, 0.0)));
    core::Console::log(console, core::Boolean::toString(core::Float64::near(1.0, 1.0 + 1e-10)) + " " + core::Boolean::toString(core::Float64::near(1.0, 1.1)) + " " + core::Boolean::toString(core::Float64::near(1.0, 1.1, 0.2)) + " " + core::Boolean::toString(core::Float64::near(0.0, 1e-300, 1e-9, 1e-12)));
    core::Console::log(console, std::to_string(core::List::reduceFromIndex(temper_globals::digits.value, 0, -1, [](int32_t s, int32_t) -> int32_t { return core::Int::add(s, 1); })));
  });
}

}  // namespace core_numbers
