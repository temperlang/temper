// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace panic_causes_panic_in_orelse_whose_body_returns {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace panic_causes_panic_in_orelse_whose_body_returns

// init.cpp
#include <panic-causes-panic-in-orelse-whose-body-returns/init.hpp>

namespace panic_causes_panic_in_orelse_whose_body_returns {

namespace core = temper::core;

static int32_t positive(int32_t n) {
  if (n <= 0) {
    core::bubble();
  }
  return n;
}

static int32_t checked(int32_t n) {
  try {
    if (n == 0) {
      return 0;
    }
    positive(n);
  } catch (core::TemperBubble const &) {
    core::panic();
  }
  return n;
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    core::Console::log(console, TEMPER_STR("before"));
    core::Console::log(console, "after " + std::to_string(checked(-1)));
  });
}

}  // namespace panic_causes_panic_in_orelse_whose_body_returns
