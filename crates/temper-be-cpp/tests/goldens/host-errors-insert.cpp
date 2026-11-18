// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace host_errors_insert {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace host_errors_insert

// init.cpp
#include <host-errors-insert/init.hpp>

namespace host_errors_insert {

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

static int32_t insert(std::shared_ptr<std::vector<int32_t>> const & lb, int32_t i) {
  core::ListBuilder::add<int32_t>(lb, 2, at(i));
  return core::List::length<int32_t>(lb);
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    core::Console::log(console, TEMPER_STR("reading"));
    int32_t r;
    try {
      r = host_errors_insert::insert(built(), 5);
    } catch (core::TemperBubble const &) {
      r = -1;
    }
    core::Console::log(console, "added past the end " + std::to_string(r));
  });
}

}  // namespace host_errors_insert
