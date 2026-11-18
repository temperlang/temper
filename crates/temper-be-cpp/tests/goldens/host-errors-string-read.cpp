// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace host_errors_string_read {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace host_errors_string_read

// init.cpp
#include <host-errors-string-read/init.hpp>

namespace host_errors_string_read {

namespace core = temper::core;

static int32_t at(int32_t i) {
  if (i == 99) {
    core::bubble();
  }
  return i;
}

static int32_t codeAt(core::Str const & s, int32_t i) {
  at(i);
  return core::String::get(s, core::String::end(s));
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    core::Console::log(console, TEMPER_STR("reading"));
    int32_t r;
    try {
      r = host_errors_string_read::codeAt(TEMPER_STR("ab"), 0);
    } catch (core::TemperBubble const &) {
      r = -1;
    }
    core::Console::log(console, "read the end " + std::to_string(r));
  });
}

}  // namespace host_errors_string_read
