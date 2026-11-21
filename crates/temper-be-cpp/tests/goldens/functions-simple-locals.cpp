// init.hpp
#pragma once

namespace simple_locals {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace simple_locals

// init.cpp
#include <simple-locals/init.hpp>

#include <temper-core/module_init.hpp>
#include <temper-core/int.hpp>
#include <temper-core/console.hpp>
#include <cstdint>
#include <functional>
#include <memory>
#include <string>

namespace simple_locals {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;

}  // namespace temper_globals

static int32_t callIt(int32_t i, std::function<int32_t(int32_t)> const & f) {
  return core::Int::mul(3, f(i));
}

static void enclose(int32_t i) {
  int32_t result = simple_locals::callIt(2, [=](int32_t j) -> int32_t { return core::Int::add(i, j); });
  core::Console::log(temper_globals::console.value, std::to_string(result));
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::console.value = core::Console::get_console();
    enclose(1);
  });
}

}  // namespace simple_locals
