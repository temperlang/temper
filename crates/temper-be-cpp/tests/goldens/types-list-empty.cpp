// init.hpp
#pragma once

namespace types_list_empty {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace types_list_empty

// init.cpp
#include <types-list-empty/init.hpp>

#include <temper-core/str.hpp>
#include <temper-core/list.hpp>
#include <temper-core/module_init.hpp>
#include <temper-core/console.hpp>
#include <cstdint>
#include <memory>
#include <vector>

namespace types_list_empty {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;

}  // namespace temper_globals

static void f(std::shared_ptr<std::vector<int32_t>> const & b) {
  if (core::List::isEmpty<int32_t>(b)) {
    core::Console::log(temper_globals::console.value, TEMPER_STR("empty"));
  } else {
    core::Console::log(temper_globals::console.value, TEMPER_STR("not empty"));
  }
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::console.value = core::Console::get_console();
    types_list_empty::f(core::List::make<int32_t>(2, 3));
    types_list_empty::f(core::List::make<int32_t>());
  });
}

}  // namespace types_list_empty
