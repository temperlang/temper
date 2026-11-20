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

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    int32_t one = 1;
    int32_t big = core::Int::mul(2147483647, one);
    int32_t small = core::Int::mul(INT32_MIN, one);
    core::Console::log(console, std::to_string(core::Int::add(big, 1)) + " " + std::to_string(core::Int::sub(small, 1)) + " " + std::to_string(core::Int::add(one, 1)) + " " + std::to_string(core::Int::sub(one, 1)) + " " + std::to_string(core::Int::add(small, 1)) + " " + std::to_string(core::Int::sub(big, 1)));
    int64_t one64 = core::Int::toInt64(one);
    int64_t big64 = core::Int64::mul(int64_t{9223372036854775807}, one64);
    int64_t small64 = core::Int64::sub(core::Int64::neg(big64), one64);
    core::Console::log(console, std::to_string(core::Int64::add(big64, int64_t{1})) + " " + std::to_string(core::Int64::sub(small64, int64_t{1})) + " " + std::to_string(core::Int64::sub(one64, int64_t{1})));
    double inf = 1e308 * 10.0;
    double nan = inf - inf;
    double ninf = 0.0 - inf;
    core::Console::log(console, core::Boolean::toString(core::Float64::near(nan, nan)) + " " + core::Boolean::toString(core::Float64::near(inf, inf)) + " " + core::Boolean::toString(core::Float64::near(ninf, ninf)) + " " + core::Boolean::toString(core::Float64::near(nan, 1.0)) + " " + core::Boolean::toString(core::Float64::near(1.0, nan)));
    core::Console::log(console, core::Boolean::toString(core::Float64::near(inf, 1.0)) + " " + core::Boolean::toString(core::Float64::near(inf, ninf)) + " " + core::Boolean::toString(core::Float64::near(inf, 1.0, 0.0, 0.0)) + " " + core::Boolean::toString(core::Float64::near(inf, inf, 0.0, 0.0)) + " " + core::Boolean::toString(core::Float64::near(inf, 1.0, 1.0, inf)) + " " + core::Boolean::toString(core::Float64::near(1.0, ninf, 1.0, inf)));
    core::Console::log(console, core::Boolean::toString(core::Float64::near(1.0, 1.5, nan, 1.0)) + " " + core::Boolean::toString(core::Float64::near(1.0, 1.5, 1.0, nan)) + " " + core::Boolean::toString(core::Float64::near(1.0, 1.0, nan, nan)) + " " + core::Boolean::toString(core::Float64::near(-0.0, 0.0, 0.0, 0.0)));
    core::Console::log(console, core::Boolean::toString(core::Float64::near(1.0, 1.0 + 1e-10)) + " " + core::Boolean::toString(core::Float64::near(1.0, 1.1)) + " " + core::Boolean::toString(core::Float64::near(1.0, 1.1, 0.2)) + " " + core::Boolean::toString(core::Float64::near(0.0, 1e-300, 1e-9, 1e-12)));
  });
}

}  // namespace core_numbers
