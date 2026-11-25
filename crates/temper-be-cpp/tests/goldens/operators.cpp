// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace operators {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace operators

// init.cpp
#include <operators/init.hpp>

namespace operators {

namespace core = temper::core;

static core::Str same(bool a, bool b) {
  return core::Boolean::toString(a == b) + " " + core::Boolean::toString(a != b) + " " + std::to_string(core::Compare::cmp(a, b));
}

static int32_t quot(int32_t a, int32_t b) {
  return core::Int::div_wrap(a, b);
}

static int32_t rem(int32_t a, int32_t b) {
  return core::Int::mod_wrap(a, b);
}

static core::Str words(core::Str const & a, core::Str const & b) {
  if (a < b) {
    return a + " < " + b;
  }
  if (a <= b) {
    return a + " == " + b;
  } else {
    return a + " > " + b;
  }
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    int32_t one = 1;
    int32_t big = core::Int::mul(2147483647, one);
    int32_t small = core::Int::mul(INT32_MIN, one);
    core::Console::log(console, std::to_string(core::Int::add(big, 1)) + " " + std::to_string(core::Int::sub(small, 1)) + " " + std::to_string(core::Int::mul(big, 4)) + " " + std::to_string(core::Int::neg(small)));
    core::Console::log(console, std::to_string(core::Int::mul(-7, one) >> 1) + " " + std::to_string(core::Int::ushr(core::Int::mul(-7, one), 28)) + " " + std::to_string(core::Int::shl(one, 33)) + " " + std::to_string(~one) + " " + std::to_string(5 & 3) + " " + std::to_string(5 | 3) + " " + std::to_string(5 ^ 3));
    core::Console::log(console, std::to_string(core::Compare::cmp(3, 5)) + " " + std::to_string(core::Compare::cmp(TEMPER_STR("b"), TEMPER_STR("a"))));
    core::Console::log(console, same(true, true) + " " + same(true, false) + " " + same(false, true));
    int32_t int32 = quot(-7, 2);
    int32_t value = rem(-7, 2);
    core::Console::log(console, std::to_string(int32) + " " + std::to_string(value) + " " + std::to_string(quot(small, -1)));
    core::Console::log(console, operators::words(TEMPER_STR("apple"), TEMPER_STR("banana")));
    core::Console::log(console, operators::words(TEMPER_STR("pear"), TEMPER_STR("pear")));
    core::Console::log(console, operators::words(TEMPER_STR("zoo"), TEMPER_STR("yak")));
    core::Console::log(console, core::Boolean::toString(operators::words(TEMPER_STR("a"), TEMPER_STR("b")) == TEMPER_STR("a < b")));
    int32_t n = 0;
    int32_t odd = 0;
    do {
      n = core::Int::add(n, 1);
      if (n % 2 != 0) {
        odd = core::Int::add(odd, n);
      }
    } while (n < 9);
    core::Console::log(console, "odd " + std::to_string(odd));
    int32_t found = -1;
    int32_t i = 0;
    while (i < 5 && found < 0) {
      int32_t j = 0;
      while (j < 5) {
        if (core::Int::mul(i, j) == 6) {
          found = core::Int::add(core::Int::mul(i, 10), j);
          break;
        }
        j = core::Int::add(j, 1);
      }
      i = core::Int::add(i, 1);
    }
    core::Console::log(console, "found " + std::to_string(found));
    core::Str empty = core::Str();
    core::Str x = TEMPER_STR("x");
    core::Console::log(console, core::Boolean::toString(empty.empty() && !x.empty()));
  });
}

}  // namespace operators
