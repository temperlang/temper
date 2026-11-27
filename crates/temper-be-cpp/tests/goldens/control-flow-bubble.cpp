// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace bubble {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct Problem;
struct Missing;

struct Problem : public virtual temper::core::Failure {};

struct Missing : public virtual Problem {
  static std::shared_ptr<Missing> make();
  temper::core::Str get_message() const override;
};

}  // namespace bubble

// init.cpp
#include <bubble/init.hpp>

namespace bubble {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;

}  // namespace temper_globals

std::shared_ptr<Missing> Missing::make() {
  std::shared_ptr<Missing> self = std::make_shared<Missing>();
  return self;
}

core::Str Missing::get_message() const {
  return TEMPER_STR("missing");
}

static core::Expected<int32_t, std::shared_ptr<Missing>> more(core::NullableParam<int32_t>) {
  return core::unexpected(Missing::make());
}

static core::Expected<core::Str, std::shared_ptr<Missing>> something(core::NullableParam<core::Str> x) {
  {
    if (x != nullptr) {
      return x.value;
    } else {
      return core::unexpected(Missing::make());
    }
  }
}

static void report(core::NullableParam<core::Str> x) {
  core::Str message;
  auto somethingResult = bubble::something(x);
  if (somethingResult.has_value()) {
    message = *somethingResult;
  } else {
    message = TEMPER_STR("null");
  }
  core::Str suffix;
  auto moreResult = bubble::more(x != nullptr ? 1 : 0);
  if (moreResult.has_value()) {
    suffix = std::to_string(*moreResult);
  } else {
    suffix = core::Str();
  }
  core::Console::log(temper_globals::console.value, message + suffix);
}

static core::Expected<int32_t, std::shared_ptr<Missing>> calcRows(int32_t cols, int32_t total) {
  core::Console::log(temper_globals::console.value, "calcRows(" + std::to_string(cols) + ", " + std::to_string(total) + ")");
  if (core::Int::mod_wrap(total, cols) != 0) {
    return core::unexpected(Missing::make());
  }
  return core::Int::div_wrap(total, cols);
}

static core::Expected<int32_t, std::shared_ptr<core::ParseError>> parsed(core::Str const & s) {
  return core::String::toInt32(s);
}

static core::Expected<int32_t, std::shared_ptr<core::ParseError>> countBelow(core::Str const & s) {
  int32_t i = 0;
  while (true) {
    auto parsedResult = bubble::parsed(s);
    if (!parsedResult.has_value()) {
      return core::unexpected(parsedResult.error());
    }
    if (i >= *parsedResult) {
      break;
    }
    i = core::Int::add(i, 1);
  }
  return i;
}

static core::Expected<int32_t, std::shared_ptr<Missing>> firstOver(int32_t n) {
  int32_t i = 0;
  while (i < 9) {
    i = core::Int::add(i, 1);
    if (i > n) {
      break;
    }
  }
  return i;
}

static core::Expected<int32_t, std::shared_ptr<Missing>> sumSkipping(int32_t n) {
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

static core::Expected<int32_t, std::shared_ptr<Missing>> tenTimes(int32_t n) {
  {
    int32_t i = 0;
    while (i < 9) {
      if (i == n) {
        return core::Int::mul(i, 10);
      }
      i = core::Int::add(i, 1);
    }
  }
  return core::unexpected(Missing::make());
}

static bool isNone(core::NullableParam<int32_t> r) {
  return r == nullptr;
}

static core::Str viaOrElse(int32_t n) {
  int32_t value;
  auto firstOverResult = firstOver(n);
  if (firstOverResult.has_value()) {
    value = *firstOverResult;
  } else {
    value = -1;
  }
  int32_t value2;
  auto sumSkippingResult = sumSkipping(n);
  if (sumSkippingResult.has_value()) {
    value2 = *sumSkippingResult;
  } else {
    value2 = -1;
  }
  int32_t value3;
  auto tenTimesResult = tenTimes(n);
  if (tenTimesResult.has_value()) {
    value3 = *tenTimesResult;
  } else {
    value3 = -1;
  }
  return std::to_string(value) + " " + std::to_string(value2) + " " + std::to_string(value3);
}

static int32_t unreachableInt() {
  core::panic();
}

template <class T>
static core::Expected<T, std::shared_ptr<Missing>> failAs(T) {
  return core::unexpected(Missing::make());
}

static core::Expected<void, std::shared_ptr<Missing>> bail() {
  return core::unexpected(Missing::make());
}

static core::NullableParam<core::Str> noName() {
  return nullptr;
}

static void stop() {
  core::panic();
}

static core::Expected<int32_t, std::shared_ptr<Missing>> half(int32_t n) {
  if (n % 2 != 0) {
    auto bailResult = bail();
    if (!bailResult.has_value()) {
      return core::unexpected(bailResult.error());
    }
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
    auto calcRowsResult = calcRows(3, 6);
    if (!calcRowsResult.has_value()) {
      core::uncaught_failure(calcRowsResult.error());
    }
    core::Console::log(temper_globals::console.value, std::to_string(*calcRowsResult));
    int32_t value;
    auto countBelowResult = bubble::countBelow(TEMPER_STR("abc"));
    if (countBelowResult.has_value()) {
      value = *countBelowResult;
    } else {
      value = -1;
    }
    core::Console::log(temper_globals::console.value, std::to_string(value));
    int32_t value2;
    auto firstOverResult = firstOver(1);
    if (firstOverResult.has_value()) {
      value2 = *firstOverResult;
    } else {
      value2 = -1;
    }
    core::NullableParam<int32_t> value3;
    auto firstOverResult2 = firstOver(1);
    if (firstOverResult2.has_value()) {
      value3 = *firstOverResult2;
    } else {
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
    auto halfResult = half(4);
    if (halfResult.has_value()) {
      value4 = *halfResult;
    } else {
      value4 = -1;
    }
    int32_t int32 = value4;
    int32_t value5;
    auto halfResult2 = half(3);
    if (halfResult2.has_value()) {
      value5 = *halfResult2;
    } else {
      value5 = -1;
    }
    int32_t value6 = value5;
    int32_t value7;
    auto halfResult3 = half(-2);
    if (halfResult3.has_value()) {
      value7 = *halfResult3;
    } else {
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
