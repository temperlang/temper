// init.hpp
#pragma once

#include <temper-core/base.hpp>

namespace constness {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace constness

// init.cpp
#include <constness/init.hpp>

#include <temper-core/int.hpp>
#include <temper-core/console.hpp>

namespace constness {

namespace core = temper::core;

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    {
      int32_t nci = 1;
      core::Console::log(console, "nci = " + std::to_string(nci));
      nci = 2;
      core::Console::log(console, "nci = " + std::to_string(nci));
      int32_t nc;
      nc = 1;
      core::Console::log(console, "nc = " + std::to_string(nc));
      nc = 2;
      core::Console::log(console, "nc = " + std::to_string(nc));
      {
        int32_t i = 0;
        while (i < 3) {
          int32_t ncl;
          ncl = i;
          core::Console::log(console, "ncl = " + std::to_string(ncl));
          i = core::Int::add(i, 1);
        }
      }
      int32_t ci = 1;
      core::Console::log(console, "ci = " + std::to_string(ci));
      core::Console::log(console, TEMPER_STR("failed to set ci"));
      core::Console::log(console, "ci = " + std::to_string(ci));
      int32_t c;
      c = 1;
      core::Console::log(console, "c = " + std::to_string(c));
      core::Console::log(console, TEMPER_STR("failed to set c"));
      core::Console::log(console, "c = " + std::to_string(c));
      {
        int32_t i2 = 0;
        while (i2 < 3) {
          int32_t cl;
          cl = i2;
          core::Console::log(console, "cl = " + std::to_string(cl));
          i2 = core::Int::add(i2, 1);
        }
      }
    }
  });
}

}  // namespace constness
