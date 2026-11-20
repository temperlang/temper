// init.hpp
#pragma once

#include <temper-core/base.hpp>

namespace jumps {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace jumps

// init.cpp
#include <jumps/init.hpp>

#include <temper-core/int.hpp>
#include <temper-core/console.hpp>

namespace jumps {

namespace core = temper::core;

static int32_t search(int32_t n) {
  int32_t found = -1;
  int32_t i = 0;
  while (i < n) {
    int32_t j = 0;
    while (true) {
      if (j >= n) {
        break;
      }
      if (core::Int::mul(i, j) == 6) {
        found = core::Int::add(core::Int::mul(i, 10), j);
        goto outerEnd;
      }
      j = core::Int::add(j, 1);
    }
    i = core::Int::add(i, 1);
  }
  outerEnd:;
  return found;
}

static int32_t odds(int32_t n) {
  int32_t k = 0;
  int32_t sum = 0;
  do {
    k = core::Int::add(k, 1);
    if ((k & 1) == 0) {
      continue;
    }
    sum = core::Int::add(sum, k);
  } while (k < n);
  return sum;
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    core::Console::log(console, std::to_string(search(5)));
    core::Console::log(console, std::to_string(odds(9)));
  });
}

}  // namespace jumps
