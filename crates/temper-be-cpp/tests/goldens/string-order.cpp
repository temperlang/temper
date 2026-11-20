// init.hpp
#pragma once

#include <temper-core/base.hpp>

namespace string_order {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace string_order

// init.cpp
#include <string-order/init.hpp>

#include <temper-core/compare.hpp>
#include <temper-core/boolean.hpp>
#include <temper-core/float64.hpp>
#include <temper-core/console.hpp>

namespace string_order {

namespace core = temper::core;

static core::Str show(core::Str const & a, core::Str const & b) {
  return std::to_string(core::Compare::cmp(a, b)) + " " + core::Boolean::toString(a < b) + " " + core::Boolean::toString(a <= b) + " " + core::Boolean::toString(a > b) + " " + core::Boolean::toString(a >= b);
}

static int32_t cmp(double a, double b) {
  return core::Float64::cmp(a, b);
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    core::Console::log(console, string_order::show(TEMPER_STR("｡"), TEMPER_STR("😀")));
    core::Console::log(console, string_order::show(TEMPER_STR("😀"), TEMPER_STR("｡")));
    core::Console::log(console, string_order::show(TEMPER_STR("\uE000"), TEMPER_STR("𐀀")));
    core::Console::log(console, string_order::show(TEMPER_STR("\uFFFF"), TEMPER_STR("\U0010FFFF")));
    core::Console::log(console, string_order::show(TEMPER_STR("a｡b"), TEMPER_STR("a😀")));
    core::Console::log(console, string_order::show(TEMPER_STR("😀"), TEMPER_STR("😁")));
    core::Console::log(console, string_order::show(TEMPER_STR("😀"), TEMPER_STR("😀")));
    core::Console::log(console, string_order::show(TEMPER_STR("😀"), TEMPER_STR("😀a")));
    core::Console::log(console, string_order::show(core::Str(), TEMPER_STR("😀")));
    core::Console::log(console, string_order::show(core::Str(), core::Str()));
    core::Console::log(console, string_order::show(TEMPER_STR("z"), TEMPER_STR("a")));
    core::Console::log(console, string_order::show(TEMPER_STR("abc"), TEMPER_STR("abd")));
    core::Console::log(console, std::to_string(cmp(2.5, 1.0)) + " " + std::to_string(cmp(1.0, 2.5)) + " " + std::to_string(cmp(1.5, 1.5)));
  });
}

}  // namespace string_order
