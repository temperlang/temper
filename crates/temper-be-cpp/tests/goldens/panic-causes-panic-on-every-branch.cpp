// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace panic_causes_panic_on_every_branch {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace panic_causes_panic_on_every_branch

// init.cpp
#include <panic-causes-panic-on-every-branch/init.hpp>

namespace panic_causes_panic_on_every_branch {

namespace core = temper::core;

static int32_t positive(int32_t n) {
  if (n <= 0) {
    core::bubble();
  }
  return n;
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    core::Console::log(console, TEMPER_STR("before"));
    int32_t k;
    try {
      k = positive(1);
    } catch (core::TemperBubble const &) {
      k = 0;
    }
    if (k > 0) {
      core::panic();
    } else {
      try {
        positive(-1);
      } catch (core::TemperBubble const &) {
        core::panic();
      }
      core::panic();
    }
  });
}

}  // namespace panic_causes_panic_on_every_branch
