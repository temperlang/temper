// init.hpp
#pragma once

#include <temper-core/base.hpp>

namespace minimal_repro {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace minimal_repro

// init.cpp
#include <minimal-repro/init.hpp>

#include <temper-core/int.hpp>
#include <temper-core/console.hpp>
#include <temper-core/string.hpp>

namespace minimal_repro {

namespace core = temper::core;

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    std::shared_ptr<std::vector<int32_t>> ls = core::List::make<int32_t>(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
    core::Console::log(console, "All  " + core::List::join(ls, TEMPER_STR(", "), [](int32_t x) -> core::Str { return std::to_string(x); }));
    core::Console::log(console, "Even " + core::List::join(core::List::filter(ls, [](int32_t x) -> bool { return (x & 1) == 0; }), TEMPER_STR(", "), [](int32_t x) -> core::Str { return std::to_string(x); }));
    core::Console::log(console, "Odd  " + core::List::join(core::List::filter(ls, [](int32_t x) -> bool { return (x & 1) != 0; }), TEMPER_STR(", "), [](int32_t x) -> core::Str { return std::to_string(x); }));
    core::Console::log(console, "Neg  " + core::List::join(core::List::map(ls, [](int32_t x) -> int32_t { return core::Int::neg(x); }), TEMPER_STR(", "), [](int32_t x) -> core::Str { return std::to_string(x); }));
    std::shared_ptr<std::vector<core::Str>> strs = core::String::split(TEMPER_STR("1 ; 2 ; 3"), TEMPER_STR(" ; "));
    core::Console::log(console, "Splt " + core::List::join(strs, TEMPER_STR(", "), [](core::Str x) -> core::Str { return x; }));
  });
}

}  // namespace minimal_repro
