// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace types_float_basics {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace types_float_basics

// init.cpp
#include <types-float-basics/init.hpp>

namespace types_float_basics {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;

}  // namespace temper_globals

static bool gt(double a, double b) {
  return core::Float64::gt(a, b);
}

static bool lt(double a, double b) {
  return core::Float64::lt(a, b);
}

static bool eq(double a, double b) {
  return core::Float64::eq(a, b);
}

static bool neq(double a, double b) {
  return !core::Float64::eq(a, b);
}

static double add(double a, double b) {
  return a + b;
}

static void checkParse(core::Str const & string) {
  core::Str converted;
  auto Result = core::String::toFloat64(string);
  if (Result.has_value()) {
    converted = core::Float64::toString(*Result);
  } else {
    converted = TEMPER_STR("failed");
  }
  core::Console::log(temper_globals::console.value, "Parse " + string + " -> " + converted);
}

static void checkTiny(core::Str const & string) {
  core::Str positive;
  auto Result = core::String::toFloat64(string);
  if (Result.has_value()) {
    positive = core::Boolean::toString(core::Float64::gt(*Result, 0.0));
  } else {
    positive = TEMPER_STR("failed");
  }
  core::Console::log(temper_globals::console.value, "Parse " + string + " > 0 -> " + positive);
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::console.value = core::Console::get_console();
    double negativeZero = -0.0;
    double zero = 0.0;
    double one = 1.0;
    double two = 2.0;
    double three = 3.0;
    if (gt(one, zero)) {
      core::Console::log(temper_globals::console.value, TEMPER_STR("greater than"));
    }
    if (lt(zero, one)) {
      core::Console::log(temper_globals::console.value, TEMPER_STR("less than"));
    }
    if (eq(zero, one)) {
      core::Console::log(temper_globals::console.value, TEMPER_STR("equality is wrong"));
    }
    if (neq(zero, one)) {
      core::Console::log(temper_globals::console.value, TEMPER_STR("inequality"));
    }
    core::Console::log(temper_globals::console.value, core::Float64::toString(zero));
    if (neq(negativeZero, zero)) {
      core::Console::log(temper_globals::console.value, TEMPER_STR("zeroes not equal"));
    }
    if (lt(negativeZero, zero)) {
      core::Console::log(temper_globals::console.value, TEMPER_STR("negZero < zero"));
    }
    if (core::Float64::eq(zero - zero, zero)) {
      core::Console::log(temper_globals::console.value, TEMPER_STR("zero subtraction"));
    }
    if (core::Float64::eq(zero + zero, zero)) {
      core::Console::log(temper_globals::console.value, TEMPER_STR("zero addition"));
    }
    if (core::Float64::eq(negativeZero - zero, negativeZero)) {
      core::Console::log(temper_globals::console.value, TEMPER_STR("negative zero minus zero is negative"));
    }
    if (core::Float64::eq(negativeZero + zero, zero)) {
      core::Console::log(temper_globals::console.value, TEMPER_STR("negative zero plus zero is positive"));
    }
    if (core::Float64::eq(zero - negativeZero, zero)) {
      core::Console::log(temper_globals::console.value, TEMPER_STR("zero minus negative zero is zero"));
    }
    if (core::Float64::eq(zero + negativeZero, zero)) {
      core::Console::log(temper_globals::console.value, TEMPER_STR("zero plus negative zero is zero"));
    }
    if (core::Float64::eq(negativeZero - negativeZero, zero)) {
      core::Console::log(temper_globals::console.value, TEMPER_STR("negative zero minus negative zero is zero"));
    }
    if (core::Float64::eq(negativeZero + negativeZero, negativeZero)) {
      core::Console::log(temper_globals::console.value, TEMPER_STR("negative zero plus negative zero is negative zero"));
    }
    core::Console::log(temper_globals::console.value, core::Float64::toString(negativeZero));
    double third = one / three;
    double half = one / two;
    double twoThirds = two / three;
    if (gt(third, half)) {
      core::Console::log(temper_globals::console.value, TEMPER_STR("oops third > half"));
    }
    if (gt(half, twoThirds)) {
      core::Console::log(temper_globals::console.value, TEMPER_STR("oops half > twoThirds"));
    }
    if (neq(third * two, twoThirds)) {
      core::Console::log(temper_globals::console.value, TEMPER_STR("misaligned multiplication"));
    }
    if (neq(add(third, twoThirds), one)) {
      core::Console::log(temper_globals::console.value, TEMPER_STR("doesn't add back up"));
    }
    core::Console::log(temper_globals::console.value, core::Float64::toString(half));
    core::Console::log(temper_globals::console.value, core::Float64::toString(core::Float64::pow(4.0, -half)));
    core::Console::log(temper_globals::console.value, core::Float64::toString(third));
    core::Console::log(temper_globals::console.value, core::Float64::toString(twoThirds));
    core::Console::log(temper_globals::console.value, core::Float64::toString(one));
    double almostBig = 1e25 * one;
    core::Console::log(temper_globals::console.value, core::Float64::toString(almostBig));
    double big = 99999999999999.0 * 999999999999.0;
    core::Console::log(temper_globals::console.value, core::Float64::toString(big));
    double bigger = big * big * big * big * big * big * big * big;
    core::Console::log(temper_globals::console.value, core::Float64::toString(bigger));
    double middling = -1500.0 * one;
    core::Console::log(temper_globals::console.value, core::Float64::toString(middling) + " " + core::Float64::toString(1000000.0 * one) + " " + core::Float64::toString(9999999.0 * one) + " " + core::Float64::toString(0.001 * one));
    core::Console::log(temper_globals::console.value, core::Float64::toString(1e21 * one) + " " + core::Float64::toString(1.5e-10 * one) + " " + core::Float64::toString(2e23 * one));
    double biggest = bigger * bigger * bigger * bigger * bigger * bigger;
    core::Console::log(temper_globals::console.value, "biggest " + core::Float64::toString(biggest));
    double tiniest = one / biggest;
    core::Console::log(temper_globals::console.value, "tiniest " + core::Float64::toString(tiniest));
    double negativeInfinity = biggest * -1.0;
    core::Console::log(temper_globals::console.value, "negative infinity " + core::Float64::toString(negativeInfinity));
    double infinity = std::numeric_limits<double>::infinity();
    core::Console::log(temper_globals::console.value, core::Float64::toString(infinity));
    core::Console::log(temper_globals::console.value, TEMPER_STR("processed an infinity"));
    double nan = std::numeric_limits<double>::quiet_NaN();
    core::Console::log(temper_globals::console.value, core::Float64::toString(nan));
    if (core::Float64::gt(nan, infinity)) {
      core::Console::log(temper_globals::console.value, TEMPER_STR("NaN is biggest"));
    } else {
      core::Console::log(temper_globals::console.value, TEMPER_STR("Infinity is biggest"));
    }
    core::Console::log(temper_globals::console.value, "NaN == NaN? " + core::Boolean::toString(core::Float64::eq(nan, std::numeric_limits<double>::quiet_NaN())));
    core::Console::log(temper_globals::console.value, "Int to float: " + core::Float64::toString(core::Int::toFloat64(core::Float64::toInt32Unsafe(two))));
    core::Console::log(temper_globals::console.value, "Positive to int: " + std::to_string(core::Float64::toInt32Unsafe(4.9 * one)));
    core::Console::log(temper_globals::console.value, "Negative to int: " + std::to_string(core::Float64::toInt32Unsafe(-4.9 * one)));
    core::Str value;
    auto Result = core::Float64::toInt32(infinity);
    if (Result.has_value()) {
      value = std::to_string(*Result);
    } else {
      value = TEMPER_STR("no");
    }
    core::Console::log(temper_globals::console.value, "Infinity to int: " + value);
    core::Str value2;
    auto Result2 = core::Float64::toInt32(std::numeric_limits<double>::quiet_NaN());
    if (Result2.has_value()) {
      value2 = std::to_string(*Result2);
    } else {
      value2 = TEMPER_STR("no");
    }
    core::Console::log(temper_globals::console.value, "NaN to int: " + value2);
    int64_t oneInt64 = core::Float64::toInt64Unsafe(one);
    int64_t notSoBig = core::Int64::mul(int64_t{2147483647}, oneInt64);
    int64_t notSoSmall = core::Int64::mul(int64_t{-2147483648}, oneInt64);
    int64_t slightlyBigger = core::Float64::toInt64Unsafe(core::Int64::toFloat64Unsafe(notSoBig) + 1.5);
    int64_t slightlySmaller = core::Float64::toInt64Unsafe(core::Int64::toFloat64Unsafe(notSoSmall) - 1.5);
    int64_t tooBigForFloat64 = core::Int64::mul(int64_t{9007199254740992}, oneInt64);
    double okToConvert64 = core::Int64::toFloat64Unsafe(core::Int64::sub(tooBigForFloat64, int64_t{1}));
    double sneakyBig = okToConvert64 + 1.0;
    core::Console::log(temper_globals::console.value, "Bigger to Int64: " + std::to_string(slightlyBigger));
    core::Console::log(temper_globals::console.value, "Smaller to Int64: " + std::to_string(slightlySmaller));
    core::Console::log(temper_globals::console.value, "Barely ok to Int64: " + std::to_string(core::Float64::toInt64Unsafe(okToConvert64)));
    core::Str value3;
    auto Result3 = core::Float64::toInt64(sneakyBig);
    if (Result3.has_value()) {
      value3 = std::to_string(*Result3);
    } else {
      value3 = TEMPER_STR("no");
    }
    core::Console::log(temper_globals::console.value, "Sneaky big to Int64: " + value3);
    core::Str value4;
    auto Result4 = core::Int64::toFloat64(tooBigForFloat64);
    if (Result4.has_value()) {
      value4 = core::Float64::toString(*Result4);
    } else {
      value4 = TEMPER_STR("no");
    }
    core::Console::log(temper_globals::console.value, "Too big Int64 to Float64: " + value4);
    core::Str value5;
    auto Result5 = core::Float64::toInt64(infinity);
    if (Result5.has_value()) {
      value5 = std::to_string(*Result5);
    } else {
      value5 = TEMPER_STR("no");
    }
    core::Console::log(temper_globals::console.value, "Infinity to Int64: " + value5);
    core::Str value6;
    auto Result6 = core::Float64::toInt64(std::numeric_limits<double>::quiet_NaN());
    if (Result6.has_value()) {
      value6 = std::to_string(*Result6);
    } else {
      value6 = TEMPER_STR("no");
    }
    core::Console::log(temper_globals::console.value, "NaN to Int64: " + value6);
    types_float_basics::checkParse(TEMPER_STR("-1.5"));
    types_float_basics::checkParse(TEMPER_STR("-0"));
    types_float_basics::checkParse(TEMPER_STR("2.0"));
    types_float_basics::checkParse(TEMPER_STR("2"));
    types_float_basics::checkParse(TEMPER_STR(" 2 "));
    types_float_basics::checkParse(TEMPER_STR("5e-1"));
    types_float_basics::checkParse(TEMPER_STR("NaN"));
    types_float_basics::checkParse(TEMPER_STR("-Infinity"));
    types_float_basics::checkTiny(TEMPER_STR("2.225e-308"));
    types_float_basics::checkTiny(TEMPER_STR("5e-324"));
    types_float_basics::checkParse(TEMPER_STR("1e-400"));
    types_float_basics::checkParse(TEMPER_STR("-1e-400"));
    types_float_basics::checkParse(TEMPER_STR("2."));
    types_float_basics::checkParse(TEMPER_STR(".2"));
    types_float_basics::checkParse(TEMPER_STR("2.0.0"));
    types_float_basics::checkParse(TEMPER_STR("-inf"));
    types_float_basics::checkParse(TEMPER_STR("totally"));
  });
}

}  // namespace types_float_basics
