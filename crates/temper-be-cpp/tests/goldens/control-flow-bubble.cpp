// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace bubble {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace bubble

// init.cpp
#include <bubble/init.hpp>

namespace bubble {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;

}  // namespace temper_globals

static int32_t more(core::NullableParam<int32_t>) {
  core::bubble();
}

static core::Str something(core::NullableParam<core::Str> x) {
  {
    if (x != nullptr) {
      return x.value;
    } else {
      core::bubble();
    }
  }
}

static void report(core::NullableParam<core::Str> x) {
  core::Str message;
  try {
    message = bubble::something(x);
  } catch (core::TemperBubble const &) {
    message = TEMPER_STR("null");
  }
  core::Str suffix;
  try {
    int32_t value;
    try {
      core::not_null(x);
      value = 1;
    } catch (core::TemperBubble const &) {
      value = 0;
    }
    suffix = std::to_string(bubble::more(value));
  } catch (core::TemperBubble const &) {
    suffix = core::Str();
  }
  core::Console::log(temper_globals::console.value, message + suffix);
}

static int32_t calcRows(int32_t cols, int32_t total) {
  core::Console::log(temper_globals::console.value, "calcRows(" + std::to_string(cols) + ", " + std::to_string(total) + ")");
  if (core::Int::mod_wrap(total, cols) != 0) {
    core::bubble();
  }
  return core::Int::div_wrap(total, cols);
}

static int32_t countBelow(core::Str const & s) {
  int32_t i = 0;
  while (i < core::String::toInt32(s)) {
    i = core::Int::add(i, 1);
  }
  return i;
}

static int32_t firstOver(int32_t n) {
  int32_t i = 0;
  while (i < 9) {
    i = core::Int::add(i, 1);
    if (i > n) {
      break;
    }
  }
  return i;
}

static int32_t sumSkipping(int32_t n) {
  int32_t sum = 0;
  {
    int32_t i = 0;
    while (i < 5) {
      if (i == n) {
        {
          i = core::Int::add(i, 1);
          continue;
        }
      }
      sum = core::Int::add(sum, i);
      i = core::Int::add(i, 1);
    }
  }
  return sum;
}

static int32_t tenTimes(int32_t n) {
  {
    int32_t i = 0;
    while (i < 9) {
      if (i == n) {
        return core::Int::mul(i, 10);
      }
      i = core::Int::add(i, 1);
    }
  }
  core::bubble();
}

static bool isNone(core::NullableParam<int32_t> r) {
  return r == nullptr;
}

static core::Str viaOrElse(int32_t n) {
  int32_t value;
  try {
    value = firstOver(n);
  } catch (core::TemperBubble const &) {
    value = -1;
  }
  int32_t value2;
  try {
    value2 = sumSkipping(n);
  } catch (core::TemperBubble const &) {
    value2 = -1;
  }
  int32_t value3;
  try {
    value3 = tenTimes(n);
  } catch (core::TemperBubble const &) {
    value3 = -1;
  }
  return std::to_string(value) + " " + std::to_string(value2) + " " + std::to_string(value3);
}

static int32_t unreachableInt() {
  core::panic();
}

template <class T>
static T failAs(T) {
  core::bubble();
}

static void bail() {
  core::bubble();
}

static core::NullableParam<core::Str> noName() {
  return nullptr;
}

static void stop() {
  core::panic();
}

static int32_t half(int32_t n) {
  if (n % 2 != 0) {
    bail();
    core::panic();
  }
  if (n < 0) {
    return failAs<int32_t>(n);
  } else {
    return n / 2;
  }
}

static int32_t pick(bool b) {
  if (b) {
    return 1;
  } else {
    return unreachableInt();
  }
}

static core::NullableParam<core::Str> named(bool b) {
  if (b) {
    return TEMPER_STR("named");
  } else {
    return noName();
  }
}

static int32_t chosen(bool b) {
  int32_t x;
  if (b) {
    x = 3;
    core::Console::log(temper_globals::console.value, TEMPER_STR("chosen"));
  } else {
    unreachableInt();
    core::panic();
  }
  return x;
}

static core::Str shout(core::NullableParam<core::Str> s) {
  if (s == nullptr) {
    stop();
    core::panic();
  }
  core::Str t = s.value;
  return t + "!";
}

static void branch(int32_t a, int32_t b) {
  core::Str c = a == 0 ? (b == 0 ? TEMPER_STR("00") : b == 1 ? TEMPER_STR("01") : b == 2 ? TEMPER_STR("02") : TEMPER_STR("0x")) : a == 1 ? TEMPER_STR("1x") : a == 2 ? TEMPER_STR("2x") : a == 3 ? TEMPER_STR("3x") : TEMPER_STR("xx");
  core::Console::log(temper_globals::console.value, "branch(" + std::to_string(a) + ", " + std::to_string(b) + ") -> " + c);
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::console.value = core::Console::get_console();
    bubble::report(TEMPER_STR("hi"));
    bubble::report(nullptr);
    core::Console::log(temper_globals::console.value, std::to_string(calcRows(3, 6)));
    int32_t value;
    try {
      value = bubble::countBelow(TEMPER_STR("abc"));
    } catch (core::TemperBubble const &) {
      value = -1;
    }
    core::Console::log(temper_globals::console.value, std::to_string(value));
    int32_t value2;
    try {
      value2 = firstOver(1);
    } catch (core::TemperBubble const &) {
      value2 = -1;
    }
    core::NullableParam<int32_t> value3;
    try {
      value3 = firstOver(1);
    } catch (core::TemperBubble const &) {
      value3 = nullptr;
    }
    core::Console::log(temper_globals::console.value, std::to_string(value2) + " " + core::Boolean::toString(bubble::isNone(value3)));
    core::Console::log(temper_globals::console.value, viaOrElse(2));
    core::Console::log(temper_globals::console.value, viaOrElse(12));
    core::Str string = std::to_string(pick(true)) + " ";
    core::NullableParam<core::Str> subject = named(true);
    core::NullableParam<core::Str> subject2 = named(false);
    core::Console::log(temper_globals::console.value, string + (subject == nullptr ? TEMPER_STR("-") : subject.value) + " " + (subject2 == nullptr ? TEMPER_STR("null") : subject2.value));
    int32_t value4;
    try {
      value4 = half(4);
    } catch (core::TemperBubble const &) {
      value4 = -1;
    }
    int32_t int32 = value4;
    int32_t value5;
    try {
      value5 = half(3);
    } catch (core::TemperBubble const &) {
      value5 = -1;
    }
    int32_t value6 = value5;
    int32_t value7;
    try {
      value7 = half(-2);
    } catch (core::TemperBubble const &) {
      value7 = -1;
    }
    core::Console::log(temper_globals::console.value, std::to_string(int32) + " " + std::to_string(value6) + " " + std::to_string(value7));
    int32_t value8 = chosen(true);
    core::Console::log(temper_globals::console.value, std::to_string(value8) + " " + bubble::shout(TEMPER_STR("hey")));
    branch(0, 2);
    branch(2, 0);
  });
}

}  // namespace bubble
