// init.hpp
#pragma once

#include <temper-core/base.hpp>

namespace if_return {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace if_return

// init.cpp
#include <if-return/init.hpp>

#include <temper-core/console.hpp>

namespace if_return {

namespace core = temper::core;

static core::Str explicit_(bool b) {
  if (b) {
    return TEMPER_STR("true");
  }
  return TEMPER_STR("false");
}

static core::Str halfAndHalf(bool b) {
  if (b) {
    return TEMPER_STR("true");
  }
  return TEMPER_STR("false");
}

static core::Str implicit(bool b) {
  if (b) {
    return TEMPER_STR("true");
  } else {
    return TEMPER_STR("false");
  }
}

static core::Str nested(bool a, bool b) {
  core::Str result = TEMPER_STR("neither");
  if (a) {
    if (b) {
      result = TEMPER_STR("both");
    }
  } else {
    result = TEMPER_STR("not a");
  }
  return result;
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    bool t = true;
    bool f = !t;
    core::Console::log(console, "explicit    true  -> " + explicit_(t));
    core::Console::log(console, "explicit    false -> " + explicit_(f));
    core::Console::log(console, "halfAndHalf true  -> " + halfAndHalf(t));
    core::Console::log(console, "halfAndHalf false -> " + halfAndHalf(f));
    core::Console::log(console, "implicit    true  -> " + implicit(t));
    core::Console::log(console, "implicit    false -> " + implicit(f));
    core::Console::log(console, "nested      true  false -> " + nested(t, f));
    core::Console::log(console, "nested      false true  -> " + nested(f, t));
  });
}

}  // namespace if_return
