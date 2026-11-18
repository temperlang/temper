// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace bit_vector {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace bit_vector

// init.cpp
#include <bit-vector/init.hpp>

namespace bit_vector {

namespace core = temper::core;

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    std::shared_ptr<std::vector<bool>> v = core::DenseBitVector::make(0);
    core::DenseBitVector::set(v, 5, false);
    bool before = core::DenseBitVector::get(v, 5);
    core::DenseBitVector::set(v, 9, true);
    core::Console::log(console, core::Boolean::toString(before) + " " + core::Boolean::toString(core::DenseBitVector::get(v, 9)) + " " + core::Boolean::toString(core::DenseBitVector::get(v, 8)) + " " + core::Boolean::toString(core::DenseBitVector::get(v, 100)));
  });
}

}  // namespace bit_vector
