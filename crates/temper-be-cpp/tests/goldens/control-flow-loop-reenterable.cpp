// init.hpp
#pragma once

#include <temper-core/base.hpp>

namespace loop_reenterable {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace loop_reenterable

// init.cpp
#include <loop-reenterable/init.hpp>

#include <temper-core/int.hpp>
#include <temper-core/console.hpp>

namespace loop_reenterable {

namespace core = temper::core;

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    int32_t i = 0;
    while (i < 2) {
      int32_t x = i;
      core::Console::log(console, std::to_string(x));
      i = core::Int::add(i, 1);
    }
  });
}

}  // namespace loop_reenterable
