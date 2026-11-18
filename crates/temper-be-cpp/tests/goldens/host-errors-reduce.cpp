// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace host_errors_reduce {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace host_errors_reduce

// init.cpp
#include <host-errors-reduce/init.hpp>

namespace host_errors_reduce {

namespace core = temper::core;

static int32_t at(int32_t i) {
  if (i == 99) {
    core::bubble();
  }
  return i;
}

static int32_t sum(std::shared_ptr<std::vector<int32_t>> const & ls, int32_t i) {
  at(i);
  return core::List::reduce(ls, [](int32_t a, int32_t b) -> int32_t { return core::Int::add(a, b); });
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    core::Console::log(console, TEMPER_STR("reading"));
    int32_t r;
    try {
      r = host_errors_reduce::sum(core::List::make<int32_t>(), 0);
    } catch (core::TemperBubble const &) {
      r = -1;
    }
    core::Console::log(console, "reduced empty " + std::to_string(r));
  });
}

}  // namespace host_errors_reduce
