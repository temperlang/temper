// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace host_errors {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace host_errors

// init.cpp
#include <host-errors/init.hpp>

namespace host_errors {

namespace core = temper::core;

static int32_t read(std::shared_ptr<std::vector<int32_t>> const & ls, int32_t i) {
  if (i < 0) {
    core::bubble();
  }
  return core::List::get<int32_t>(ls, i);
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    core::Console::log(console, TEMPER_STR("reading"));
    int32_t r;
    try {
      r = host_errors::read(core::List::make<int32_t>(1, 2), 5);
    } catch (core::TemperBubble const &) {
      r = -1;
    }
    core::Console::log(console, "read past the end " + std::to_string(r));
    core::panic();
  });
}

}  // namespace host_errors
