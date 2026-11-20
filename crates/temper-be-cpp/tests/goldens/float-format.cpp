// init.hpp
#pragma once

#include <temper-core/base.hpp>

namespace float_format {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace float_format

// init.cpp
#include <float-format/init.hpp>

#include <temper-core/float64.hpp>
#include <temper-core/console.hpp>
#include <limits>

namespace float_format {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;
static core::GlobalSlot<double> one;

}  // namespace temper_globals

static void show(std::shared_ptr<std::vector<double>> const & xs) {
  core::Console::log(temper_globals::console.value, core::List::join(xs, TEMPER_STR(" "), [](double x) -> core::Str { return core::Float64::toString(x * temper_globals::one.value); }));
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::one.construct();
    temper_globals::console.value = core::Console::get_console();
    temper_globals::one.value = 1.0;
    float_format::show(core::List::make<double>(-1500.0, 1000000.0, 9999999.0, 10000000.0, 1000000000000000.0, 1e16, 1e20, 1e21));
    float_format::show(core::List::make<double>(0.001, 0.0009999999999999998, 0.0001, 1e-5, 1e-6, 1e-7));
    float_format::show(core::List::make<double>(0.1, 0.3333333333333333, 0.1 + 0.2, 1.2345678901234568e17, 9999999.999999998));
    float_format::show(core::List::make<double>(5e-324, 2.2250738585072014e-308, 7.120236347223045e-307));
    float_format::show(core::List::make<double>(1.7976931348623157e308, -0.0, std::numeric_limits<double>::quiet_NaN(), std::numeric_limits<double>::infinity(), -std::numeric_limits<double>::infinity()));
  });
}

}  // namespace float_format
