// init.hpp
#pragma once

#include <temper-core/base.hpp>

namespace indices {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace indices

// init.cpp
#include <indices/init.hpp>

#include <temper-core/compare.hpp>
#include <temper-core/int.hpp>
#include <temper-core/console.hpp>
#include <temper-core/string.hpp>
#include <temper-core/string_builder.hpp>

namespace indices {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;

}  // namespace temper_globals

static int32_t indexOfChar(core::Str const & s, int32_t c) {
  int32_t i = core::String::begin();
  while (core::String::hasIndex(s, i)) {
    if (core::String::get(s, i) == c) {
      return i;
    }
    i = core::String::next(s, i);
  }
  return core::String::none();
}

static void comparePositions(core::Str const & s, int32_t a, int32_t b) {
  int32_t delta = core::Compare::cmp(indices::indexOfChar(s, a), indices::indexOfChar(s, b));
  core::Str relPos = delta < 0 ? TEMPER_STR("before") : delta == 0 ? TEMPER_STR("at") : TEMPER_STR("after");
  core::Str value;
  try {
    value = core::String::fromCodepoint(a);
  } catch (core::TemperBubble const &) {
    value = TEMPER_STR("?");
  }
  core::Str string = value;
  core::Str value2;
  try {
    value2 = core::String::fromCodepoint(b);
  } catch (core::TemperBubble const &) {
    value2 = TEMPER_STR("?");
  }
  core::Console::log(temper_globals::console.value, "'" + string + "' is " + relPos + " '" + value2 + "' in \"" + s + "\".");
}

static bool has0(core::Str const & s, int32_t c) {
  return core::String::isStringIndex(indices::indexOfChar(s, c));
}

static bool has1(core::Str const & s, int32_t c) {
  int32_t i = indices::indexOfChar(s, c);
  return core::String::isStringIndex(i);
}

static bool has2(core::Str const & s, int32_t c) {
  int32_t i = indices::indexOfChar(s, c);
  return !core::String::isNoStringIndex(i);
}

static bool has3(core::Str const & s, int32_t c) {
  int32_t i = indices::indexOfChar(s, c);
  try {
    core::String::requireStringIndex(i);
    return true;
  } catch (core::TemperBubble const &) {
    return false;
  }
}

static bool has4(core::Str const & s, int32_t c) {
  int32_t i = indices::indexOfChar(s, c);
  try {
    core::String::requireNoStringIndex(i);
    return false;
  } catch (core::TemperBubble const &) {
    return true;
  }
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::console.value = core::Console::get_console();
    indices::comparePositions(TEMPER_STR("cookie"), 99, 105);
    indices::comparePositions(TEMPER_STR("cookie"), 107, 111);
    indices::comparePositions(TEMPER_STR("cookie"), 107, 107);
    indices::comparePositions(TEMPER_STR("cookie"), 120, 99);
    indices::comparePositions(TEMPER_STR("cookie"), 101, 120);
    indices::comparePositions(TEMPER_STR("cookie"), 120, 121);
    core::Str s = TEMPER_STR("cookie");
    {
      std::shared_ptr<std::vector<int32_t>> list = core::List::make<int32_t>(111, 105, 122);
      int32_t length = core::List::length<int32_t>(list);
      int32_t i = 0;
      while (i < length) {
        int32_t c = core::List::get<int32_t>(list, i);
        i = core::Int::add(i, 1);
        core::Str value;
        try {
          value = core::String::fromCodepoint(c);
        } catch (core::TemperBubble const &) {
          value = TEMPER_STR("?");
        }
        core::Str string = value;
        core::Console::log(temper_globals::console.value, "'" + string + "' in \"" + s + "\": " + (indices::has0(s, c) ? TEMPER_STR("y") : TEMPER_STR("n")) + ", " + (indices::has1(s, c) ? TEMPER_STR("y") : TEMPER_STR("n")) + ", " + (indices::has2(s, c) ? TEMPER_STR("y") : TEMPER_STR("n")) + ", " + (indices::has3(s, c) ? TEMPER_STR("y") : TEMPER_STR("n")) + ", " + (indices::has4(s, c) ? TEMPER_STR("y") : TEMPER_STR("n")));
      }
    }
    {
      std::shared_ptr<std::ostringstream> stringBuilder = core::StringBuilder::make();
      core::StringBuilder::append(stringBuilder, TEMPER_STR("Hello"));
      core::StringBuilder::append(stringBuilder, TEMPER_STR(","));
      int32_t afterComma = core::StringBuilder::end(stringBuilder);
      core::StringBuilder::append(stringBuilder, TEMPER_STR(" World!"));
      core::Str str = core::StringBuilder::toString(stringBuilder);
      core::Console::log(temper_globals::console.value, core::String::slice(str, core::String::begin(), afterComma));
      core::Console::log(temper_globals::console.value, core::String::slice(str, afterComma, core::String::end(str)));
    }
  });
}

}  // namespace indices
