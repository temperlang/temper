// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace host_errors_negative_index {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace host_errors_negative_index

// init.cpp
#include <host-errors-negative-index/init.hpp>

namespace host_errors_negative_index {

namespace core = temper::core;

static int32_t at(int32_t i) {
  if (i == 99) {
    core::bubble();
  }
  return i;
}

static std::shared_ptr<std::vector<int32_t>> built() {
  std::shared_ptr<std::vector<int32_t>> lb = core::ListBuilder::make<int32_t>();
  core::ListBuilder::add<int32_t>(lb, 1);
  return lb;
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
      r = host_errors_negative_index::read(built(), -1);
    } catch (core::TemperBubble const &) {
      r = -1;
    }
    core::Console::log(console, "read before the start " + std::to_string(r));
  });
}

}  // namespace host_errors_negative_index
