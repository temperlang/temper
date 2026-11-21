// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace panic_causes_panic_passing_orelse_panic {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace panic_causes_panic_passing_orelse_panic

// init.cpp
#include <panic-causes-panic-passing-orelse-panic/init.hpp>

namespace panic_causes_panic_passing_orelse_panic {

namespace core = temper::core;

static int32_t positive(int32_t n) {
  if (n <= 0) {
    core::bubble();
  }
  return n;
}

static int32_t read(std::shared_ptr<std::vector<int32_t>> const & ls, int32_t i) {
  positive(core::Int::add(i, 1));
  return core::List::get<int32_t>(ls, i);
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    core::Console::log(console, TEMPER_STR("before"));
    int32_t r;
    try {
      r = panic_causes_panic_passing_orelse_panic::read(core::List::make<int32_t>(1, 2), 5);
    } catch (core::TemperBubble const &) {
      core::panic();
    }
    core::Console::log(console, "after " + std::to_string(r));
  });
}

}  // namespace panic_causes_panic_passing_orelse_panic
