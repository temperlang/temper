// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace dead_handlers {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace dead_handlers

// init.cpp
#include <dead-handlers/init.hpp>

namespace dead_handlers {

namespace core = temper::core;

static int32_t caught(int32_t n) {
  return core::Int::add(n, 1);
}

static int32_t orElse(int32_t n) {
  return core::Int::add(n, 2);
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    core::Console::log(console, std::to_string(caught(1)));
    core::Console::log(console, std::to_string(orElse(1)));
    core::Console::log(console, TEMPER_STR("caught"));
    core::Console::log(console, TEMPER_STR("or else"));
  });
}

}  // namespace dead_handlers
