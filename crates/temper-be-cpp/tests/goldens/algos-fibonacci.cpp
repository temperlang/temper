// init.hpp
#pragma once

namespace fibonacci {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace fibonacci

// init.cpp
#include <fibonacci/init.hpp>

#include <temper-core/module_init.hpp>
#include <temper-core/int.hpp>
#include <temper-core/console.hpp>
#include <cstdint>
#include <memory>
#include <string>

namespace fibonacci {

namespace core = temper::core;

static int32_t fib(int32_t i) {
  int32_t a = 0;
  int32_t b = 1;
  while (i > 0) {
    int32_t c = core::Int::add(a, b);
    a = b;
    b = c;
    i = core::Int::sub(i, 1);
  }
  return a;
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    int32_t zero = 0;
    core::Console::log(console, "fib(1)=" + std::to_string(fib(core::Int::add(zero, 1))));
    core::Console::log(console, "fib(10)=" + std::to_string(fib(core::Int::add(zero, 10))));
    core::Console::log(console, "fib(20)=" + std::to_string(fib(core::Int::add(zero, 20))));
  });
}

}  // namespace fibonacci
