// init.hpp
#pragma once

#include <temper-core/base.hpp>

namespace loops {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace loops

// init.cpp
#include <loops/init.hpp>

#include <temper-core/int.hpp>
#include <temper-core/console.hpp>

namespace loops {

namespace core = temper::core;

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    {
      int32_t i = 0;
      while (i < 5) {
        if (i == 1 || i == 3) {
          {
            i = core::Int::add(i, 1);
            continue;
          }
        }
        core::Console::log(console, std::to_string(i));
        i = core::Int::add(i, 1);
      }
    }
    {
      int32_t i2 = 0;
      while (i2 < 5) {
        if (i2 == 3) {
          break;
        }
        core::Console::log(console, std::to_string(i2));
        i2 = core::Int::add(i2, 1);
      }
    }
    int32_t n = 0;
    {
      {
        int32_t i3 = 0;
        while (i3 < 4) {
          {
            core::Str str = "row " + std::to_string(i3) + " =";
            int32_t j = 0;
            while (true) {
              int32_t t = n;
              n = core::Int::add(t, 1);
              str = str + " " + std::to_string(t);
              if (i3 <= j) {
                core::Console::log(console, str);
                {
                  i3 = core::Int::add(i3, 1);
                  goto outerNext;
                }
              }
              j = core::Int::add(j, 1);
            }
          }
          outerNext:;
        }
      }
    }
    int32_t prime = 1;
    {
      {
        int32_t i4 = 0;
        while (i4 < 25) {
          {
            {
              {
                int32_t j2 = 0;
                while (j2 < 25) {
                  {
                    {
                      {
                        int32_t k = 0;
                        while (k < 25) {
                          prime = core::Int::add(prime, 1);
                          if (prime != 2 && prime % 2 == 0) {
                            {
                              i4 = core::Int::add(i4, 1);
                              goto outerNext2;
                            }
                          }
                          if (prime != 3 && prime % 3 == 0) {
                            {
                              j2 = core::Int::add(j2, 1);
                              goto outerNext3;
                            }
                          }
                          if (prime != 5 && prime % 5 == 0) {
                            {
                              k = core::Int::add(k, 1);
                              continue;
                            }
                          }
                          if (prime != 7 && prime % 7 == 0) {
                            {
                              k = core::Int::add(k, 1);
                              continue;
                            }
                          }
                          core::Console::log(console, std::to_string(prime));
                          k = core::Int::add(k, 1);
                        }
                      }
                    }
                    j2 = core::Int::add(j2, 1);
                  }
                  outerNext3:;
                }
              }
            }
            i4 = core::Int::add(i4, 1);
          }
          outerNext2:;
        }
      }
    }
    int32_t val = 0;
    {
      {
        int32_t i5 = 0;
        while (i5 < 10) {
          {
            core::Str str2 = "row " + std::to_string(i5) + " =";
            int32_t j3 = 0;
            while (true) {
              int32_t t2 = val;
              val = core::Int::add(t2, 1);
              str2 = str2 + " " + std::to_string(t2);
              if (i5 == 4) {
                core::Console::log(console, TEMPER_STR("break"));
                goto outerEnd;
              }
              if (i5 <= j3) {
                core::Console::log(console, str2);
                {
                  i5 = core::Int::add(i5, 1);
                  goto outerNext4;
                }
              }
              j3 = core::Int::add(j3, 1);
            }
          }
          outerNext4:;
        }
        outerEnd:;
      }
    }
  });
}

}  // namespace loops
