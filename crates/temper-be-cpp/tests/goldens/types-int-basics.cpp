// init.hpp
#pragma once

#include <temper-core/base.hpp>

namespace basics {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace basics

// init.cpp
#include <basics/init.hpp>

#include <temper-core/console.hpp>

namespace basics {

namespace core = temper::core;

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    core::Console::log(console, TEMPER_STR("1.min(3)"));
    core::Console::log(console, std::to_string(std::min(1, 3)));
    core::Console::log(console, TEMPER_STR("3.min(1)"));
    core::Console::log(console, std::to_string(std::min(3, 1)));
    core::Console::log(console, TEMPER_STR("1.max(3)"));
    core::Console::log(console, std::to_string(std::max(1, 3)));
    core::Console::log(console, TEMPER_STR("3.max(1)"));
    core::Console::log(console, std::to_string(std::max(3, 1)));
  });
}

}  // namespace basics
