// init.hpp
#pragma once

#include <temper-core/base.hpp>

namespace hello_world {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace hello_world

// init.cpp
#include <hello-world/init.hpp>

#include <temper-core/console.hpp>

namespace hello_world {

namespace core = temper::core;

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    core::Console::log(console, TEMPER_STR("Hello, World!"));
  });
}

}  // namespace hello_world
