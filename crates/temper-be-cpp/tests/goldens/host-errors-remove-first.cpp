// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace host_errors_remove_first {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace host_errors_remove_first

// init.cpp
#include <host-errors-remove-first/init.hpp>

namespace host_errors_remove_first {

namespace core = temper::core;

static int32_t at(int32_t i) {
  if (i == 99) {
    core::bubble();
  }
  return i;
}

static int32_t removeFirst(std::shared_ptr<std::deque<int32_t>> const & d, int32_t i) {
  at(i);
  return core::Deque::removeFirst<int32_t>(d);
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    core::Console::log(console, TEMPER_STR("reading"));
    int32_t r;
    try {
      r = host_errors_remove_first::removeFirst(core::Deque::make<int32_t>(), 0);
    } catch (core::TemperBubble const &) {
      r = -1;
    }
    core::Console::log(console, "removed from empty " + std::to_string(r));
  });
}

}  // namespace host_errors_remove_first
