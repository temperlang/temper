// init.hpp
#pragma once

#include <temper-core/core.hpp>
#include <std/collections.hpp>

namespace bit_growth {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace bit_growth

// init.cpp
#include <bit-growth/init.hpp>

namespace bit_growth {

namespace core = temper::core;

void global_init_init() {
  core::init_once([] {
    temper_std::global_init_collections();
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    std::shared_ptr<std::vector<bool>> v = core::BitVector::make(0);
    core::BitVector::set(v, 5, false);
    bool before = core::BitVector::get(v, 5);
    core::BitVector::set(v, 9, true);
    core::Console::log(console, core::Boolean::toString(before) + " " + core::Boolean::toString(core::BitVector::get(v, 9)) + " " + core::Boolean::toString(core::BitVector::get(v, 8)) + " " + core::Boolean::toString(core::BitVector::get(v, 100)));
  });
}

}  // namespace bit_growth
