// init.hpp
#pragma once

#include <temper-core/core.hpp>
#include <std/collections.hpp>

namespace bit_vector_negative_false {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace bit_vector_negative_false

// init.cpp
#include <bit-vector-negative-false/init.hpp>

namespace bit_vector_negative_false {

namespace core = temper::core;

void global_init_init() {
  core::init_once([] {
    temper_std::global_init_collections();
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    std::shared_ptr<std::vector<bool>> v = core::BitVector::make(2);
    core::BitVector::set(v, 5, false);
    core::Console::log(console, "v[5]=" + core::Boolean::toString(core::BitVector::get(v, 5)));
    core::Console::log(console, TEMPER_STR("before"));
    core::BitVector::set(v, -1, false);
    core::Console::log(console, TEMPER_STR("not reached"));
  });
}

}  // namespace bit_vector_negative_false
