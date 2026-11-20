// init.hpp
#pragma once

#include <temper-core/base.hpp>

namespace ops {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace ops

// init.cpp
#include <ops/init.hpp>

#include <temper-core/boolean.hpp>
#include <temper-core/float64.hpp>
#include <temper-core/console.hpp>
#include <cmath>
#include <limits>

namespace ops {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;
static core::GlobalSlot<double> two;

}  // namespace temper_globals

static void near(core::Str const & label, double actual, double expected) {
  bool subject = core::Float64::near(actual, expected);
  core::Str result = subject == true ? TEMPER_STR("✅") : core::Str("❌ wanted " + core::Float64::toString(expected) + ", was " + core::Float64::toString(actual));
  core::Console::log(temper_globals::console.value, label + ": " + result);
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::two.construct();
    temper_globals::console.value = core::Console::get_console();
    double one = 1.0;
    temper_globals::two.value = one + one;
    double half = one / temper_globals::two.value;
    ops::near(TEMPER_STR("half.abs()"), core::Float64::abs(half), half);
    ops::near(TEMPER_STR("(-half).abs()"), core::Float64::abs(-half), half);
    double three = one + temper_globals::two.value;
    ops::near(TEMPER_STR("half.acos()"), core::Float64::acos(half), core::Float64::pi() / three);
    double pi = core::Float64::pi();
    double six = temper_globals::two.value * three;
    ops::near(TEMPER_STR("half.asin()"), core::Float64::asin(half), pi / six);
    double four = temper_globals::two.value * temper_globals::two.value;
    ops::near(TEMPER_STR("(-one).atan()"), core::Float64::atan(-one), -pi / four);
    ops::near(TEMPER_STR("one.atan2(-one)"), core::Float64::atan2(one, -one), (three * pi) / four);
    ops::near(TEMPER_STR("(four / three).ceil()"), core::Float64::ceil(four / three), temper_globals::two.value);
    ops::near(TEMPER_STR("(-four / three).ceil()"), core::Float64::ceil(-four / three), -one);
    ops::near(TEMPER_STR("(pi / three).cos()"), core::Float64::cos(pi / three), half);
    double e = core::Float64::e();
    ops::near(TEMPER_STR("one.cosh()"), core::Float64::cosh(one), (e + one / e) / temper_globals::two.value);
    ops::near(TEMPER_STR("half.exp() == e.sqrt()"), core::Float64::exp(half), core::Float64::sqrt(e));
    ops::near(TEMPER_STR("half.expm1()"), core::Float64::expm1(half), core::Float64::sqrt(e) - one);
    ops::near(TEMPER_STR("(four / three).floor()"), core::Float64::floor(four / three), one);
    ops::near(TEMPER_STR("(-four / three).floor()"), core::Float64::floor(-four / three), -temper_globals::two.value);
    ops::near(TEMPER_STR("e.log()"), core::Float64::log(core::Float64::e()), one);
    double five = one + four;
    double ten = temper_globals::two.value * five;
    ops::near(TEMPER_STR("ten.log10()"), core::Float64::log10(ten), one);
    ops::near(TEMPER_STR("(e - one).log1p()"), core::Float64::log1p(e - one), one);
    auto fn = [](double x) -> core::Str {
      auto fn2 = [=](double y) -> core::Str {
        core::Str pair = core::List::join(core::List::make<double>(core::Float64::max(x, y), core::Float64::min(x, y)), TEMPER_STR(", "), [](double z) -> core::Str { return core::Float64::toString(z); });
        return "(" + pair + ")";
      };
      return core::List::join(core::List::make<double>(std::numeric_limits<double>::quiet_NaN(), temper_globals::two.value), TEMPER_STR(", "), fn2);
    };
    core::Str maxMinCombos = core::List::join(core::List::make<double>(std::numeric_limits<double>::quiet_NaN(), one), TEMPER_STR(", "), fn);
    core::Console::log(temper_globals::console.value, "maxMinCombos: " + maxMinCombos);
    double quarter = half / temper_globals::two.value;
    ops::near(TEMPER_STR("2.25 % 2.0"), std::fmod(temper_globals::two.value + quarter, temper_globals::two.value), 0.25);
    ops::near(TEMPER_STR("2.25 % -2.0"), std::fmod(temper_globals::two.value + quarter, -temper_globals::two.value), 0.25);
    ops::near(TEMPER_STR("-2.25 % 2.0"), std::fmod(-(temper_globals::two.value + quarter), temper_globals::two.value), -0.25);
    ops::near(TEMPER_STR("-2.25 % -2.0"), std::fmod(-(temper_globals::two.value + quarter), -temper_globals::two.value), -0.25);
    ops::near(TEMPER_STR("pi.round()"), core::Float64::round(pi), three);
    ops::near(TEMPER_STR("(-pi).round()"), core::Float64::round(-pi), -three);
    ops::near(TEMPER_STR("pi.sign()"), core::Float64::sign(pi), one);
    ops::near(TEMPER_STR("(-pi).sign()"), core::Float64::sign(-pi), -one);
    double zero = one - one;
    core::Console::log(temper_globals::console.value, "zero.sign(): " + core::Float64::toString(core::Float64::sign(zero)));
    core::Console::log(temper_globals::console.value, "(-zero).sign(): " + core::Float64::toString(core::Float64::sign(-zero)));
    ops::near(TEMPER_STR("(pi / six).sin()"), core::Float64::sin(pi / six), half);
    ops::near(TEMPER_STR("one.sinh()"), core::Float64::sinh(one), (e - one / e) / temper_globals::two.value);
    ops::near(TEMPER_STR("(-pi / four).tan()"), core::Float64::tan(-pi / four), -one);
    ops::near(TEMPER_STR("(three * pi / four).tan()"), core::Float64::tan((three * pi) / four), -one);
    ops::near(TEMPER_STR("one.tanh()"), core::Float64::tanh(one), (core::Float64::pow(e, 2.0) - 1.0) / (core::Float64::pow(e, 2.0) + 1.0));
    core::Console::log(temper_globals::console.value, "one.near(one + 0.1): " + core::Boolean::toString(core::Float64::near(one, one + 0.1)));
    core::Console::log(temper_globals::console.value, "one.near(one + 0.25, absTol = 0.25): " + core::Boolean::toString(core::Float64::near(one, one + 0.25, nullptr, 0.25)));
    core::Console::log(temper_globals::console.value, "one.near(one + 0.1 + 1e-9, absTol = 0.1): " + core::Boolean::toString(core::Float64::near(one, one + 0.1 + 1e-9, nullptr, 0.1)));
    core::Console::log(temper_globals::console.value, "one.near(one - 0.1, absTol = 0.11): " + core::Boolean::toString(core::Float64::near(one, one - 0.1, nullptr, 0.11)));
    core::Console::log(temper_globals::console.value, "ten.near(ten + 0.1, absTol = 0.011): " + core::Boolean::toString(core::Float64::near(ten, ten + 0.1, nullptr, 0.011)));
    core::Console::log(temper_globals::console.value, "ten.near(ten + 0.1, relTol = 0.011): " + core::Boolean::toString(core::Float64::near(ten, ten + 0.1, 0.011)));
  });
}

}  // namespace ops
