// init.hpp
#pragma once

namespace broken {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace broken

// init.cpp
#include <broken/init.hpp>

#include <temper-core/base_types.hpp>
#include <temper-core/module_init.hpp>

namespace broken {

namespace core = temper::core;

void global_init_init() {
  core::init_once([] {
    core::bubble();
  });
}

}  // namespace broken
