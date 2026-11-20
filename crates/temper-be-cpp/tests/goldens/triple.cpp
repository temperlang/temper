// init.hpp
#pragma once

namespace triple {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace triple

// init.cpp
#include <triple/init.hpp>

#include <temper-core/module_init.hpp>
#include <temper-core/int.hpp>
#include <temper-core/console.hpp>
#include <cstdint>
#include <memory>
#include <string>

namespace triple {

namespace core = temper::core;

static int32_t triple(int32_t n) {
  return core::Int::mul(n, 3);
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    core::Console::log(console, "triple(14) = " + std::to_string(triple(14)));
  });
}

}  // namespace triple
