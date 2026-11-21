// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace panic_causes_uncaught_panic {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace panic_causes_uncaught_panic

// init.cpp
#include <panic-causes-uncaught-panic/init.hpp>

namespace panic_causes_uncaught_panic {

namespace core = temper::core;

static int32_t boom(int32_t n) {
  if (n > 0) {
    core::panic();
  }
  return n;
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    core::Console::log(console, TEMPER_STR("before"));
    boom(1);
    core::Console::log(console, TEMPER_STR("after"));
  });
}

}  // namespace panic_causes_uncaught_panic
