// init.hpp
#pragma once

#include <temper-core/core.hpp>
#include <std/regex.hpp>

namespace panics {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct Shape;
struct Square;
struct Circle;

struct Shape : public virtual temper::core::AnyValueBase {};

struct Square : public virtual Shape {
  int32_t side;
  static std::shared_ptr<Square> make(int32_t side);
  int32_t get_side() const;
};

struct Circle : public virtual Shape {
  static std::shared_ptr<Circle> make();
};

}  // namespace panics

// init.cpp
#include <panics/init.hpp>

namespace panics {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;

}  // namespace temper_globals

std::shared_ptr<Square> Square::make(int32_t side) {
  std::shared_ptr<Square> self = std::make_shared<Square>();
  self->side = side;
  return self;
}

int32_t Square::get_side() const {
  return this->side;
}

std::shared_ptr<Circle> Circle::make() {
  std::shared_ptr<Circle> self = std::make_shared<Circle>();
  return self;
}

static int32_t failing(int32_t n) {
  if (n > 0) {
    core::bubble();
  }
  return n;
}

static std::shared_ptr<Square> downcast(std::shared_ptr<Shape> const & s) {
  return core::checked_cast<Square>(s);
}

static int32_t calling(std::function<int32_t()> const & f) {
  try {
    return f();
  } catch (core::TemperBubble const &) {
    return -9;
  }
}

static int32_t explode(int32_t n) {
  if (n > 0) {
    core::panic();
  }
  core::bubble();
}

static int32_t noted(core::Str const & name) {
  core::Console::log(temper_globals::console.value, name + " fallback");
  return -2;
}

static int32_t inner() {
  try {
    return explode(1);
  } catch (core::TemperBubble const &) {
    return panics::noted(TEMPER_STR("inner"));
  }
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_std::global_init_regex();
    temper_globals::console.value = core::Console::get_console();
    int32_t zero;
    try {
      zero = failing(0);
    } catch (core::TemperBubble const &) {
      zero = -1;
    }
    int32_t a;
    try {
      a = failing(1);
    } catch (core::TemperBubble const &) {
      a = -1;
    }
    int32_t b;
    try {
      b = panics::downcast(core::to_base<Shape>(Circle::make()))->side;
    } catch (core::TemperBubble const &) {
      b = -2;
    }
    int32_t c;
    try {
      c = core::Int::div_wrap(1, zero);
    } catch (core::TemperBubble const &) {
      c = -3;
    }
    int32_t d;
    try {
      d = core::Int64::toInt32(core::Int64::div_wrap(int64_t{1}, core::Int::toInt64(zero)));
    } catch (core::TemperBubble const &) {
      d = -4;
    }
    int32_t e;
    try {
      e = core::String::toInt32(TEMPER_STR("nope"));
    } catch (core::TemperBubble const &) {
      e = -5;
    }
    double f;
    try {
      f = core::String::toFloat64(TEMPER_STR("x"));
    } catch (core::TemperBubble const &) {
      f = -6.0;
    }
    core::Str g;
    try {
      g = core::String::fromCodepoint(1114112);
    } catch (core::TemperBubble const &) {
      g = TEMPER_STR("?");
    }
    std::shared_ptr<temper_std::Regex> digits = temper_std::Repeat::make(core::to_base<temper_std::RegexNode>(temper_std::CodeSet::make(core::List::make<std::shared_ptr<temper_std::CodePart>>(core::to_base<temper_std::CodePart>(temper_std::CodeRange::make(48, 57))), false)), 1, nullptr, false)->compiled();
    core::Str h;
    try {
      h = digits->find(TEMPER_STR("none"))->full->value;
    } catch (core::TemperBubble const &) {
      h = TEMPER_STR("no digits");
    }
    int32_t i = panics::calling([]() -> int32_t { return failing(2); });
    core::Console::log(temper_globals::console.value, std::to_string(a) + " " + std::to_string(b) + " " + std::to_string(c) + " " + std::to_string(d) + " " + std::to_string(e) + " " + core::Boolean::toString(core::Float64::eq(f, -6.0)) + " " + g + " " + h + " " + std::to_string(i));
    core::Console::log(temper_globals::console.value, TEMPER_STR("panicking"));
    int32_t p;
    try {
      p = inner();
    } catch (core::TemperBubble const &) {
      p = -1;
    }
    core::Console::log(temper_globals::console.value, "after the panic " + std::to_string(p));
  });
}

}  // namespace panics
