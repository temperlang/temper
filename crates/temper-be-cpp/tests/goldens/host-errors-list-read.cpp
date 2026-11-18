// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace host_errors_list_read {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace host_errors_list_read

// init.cpp
#include <host-errors-list-read/init.hpp>

namespace host_errors_list_read {

namespace core = temper::core;

static int32_t at(int32_t i) {
  if (i == 99) {
    core::bubble();
  }
  return i;
}

static int32_t read(std::shared_ptr<std::vector<int32_t>> const & ls, int32_t i) {
  return core::List::get<int32_t>(ls, at(i));
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    core::Console::log(console, TEMPER_STR("reading"));
    int32_t r;
    try {
      r = host_errors_list_read::read(core::List::make<int32_t>(1, 2), 5);
    } catch (core::TemperBubble const &) {
      r = -1;
    }
    core::Console::log(console, "read past the end " + std::to_string(r));
  });
}

}  // namespace host_errors_list_read
