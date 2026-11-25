// init.hpp
#pragma once

#include <temper-core/core.hpp>
#include <std/regex.hpp>
#include <std/json.hpp>
#include <std/temporal.hpp>

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
  if (dynamic_cast<Square const *>(s.get()) != nullptr) {
    return core::checked_cast<Square>(s);
  } else {
    core::bubble();
  }
}

static int32_t calling(std::function<int32_t()> const & f) {
  try {
    return f();
  } catch (core::TemperBubble const &) {
    return -9;
  }
}

static core::Str appending(int32_t codePoint) {
  std::shared_ptr<std::ostringstream> sb = core::StringBuilder::make();
  core::StringBuilder::appendCodepoint(sb, codePoint);
  return core::StringBuilder::toString(sb);
}

static core::Str parsing(core::Str const & text) {
  temper_std::parseJson(text);
  return TEMPER_STR("parsed");
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
    temper_std::global_init_json();
    temper_std::global_init_temporal();
    temper_globals::console.value = core::Console::get_console();
    int32_t zero;
    try {
      zero = failing(0);
    } catch (core::TemperBubble const &) {
      zero = -1;
    }
    static_cast<void>(zero);
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
    core::Console::log(temper_globals::console.value, std::to_string(a) + " " + std::to_string(b) + " " + std::to_string(e) + " " + core::Boolean::toString(core::Float64::eq(f, -6.0)) + " " + g + " " + h + " " + std::to_string(i));
    int64_t k;
    try {
      k = core::String::toInt64(TEMPER_STR("zz"));
    } catch (core::TemperBubble const &) {
      k = int64_t{-7};
    }
    int32_t l;
    try {
      l = core::String::toInt32(TEMPER_STR("99999999999"));
    } catch (core::TemperBubble const &) {
      l = -8;
    }
    int32_t m;
    try {
      m = core::Float64::toInt32(1e20);
    } catch (core::TemperBubble const &) {
      m = -9;
    }
    int32_t n;
    try {
      n = core::Int64::toInt32(int64_t{5000000000});
    } catch (core::TemperBubble const &) {
      n = -10;
    }
    std::shared_ptr<std::vector<int32_t>> points = core::List::make<int32_t>(65, 1114112);
    core::Str o;
    try {
      o = core::String::fromCodepoints(points);
    } catch (core::TemperBubble const &) {
      o = TEMPER_STR("?");
    }
    core::Str q;
    try {
      q = appending(-1);
    } catch (core::TemperBubble const &) {
      q = TEMPER_STR("?");
    }
    std::shared_ptr<core::Mapped::Ordered<core::Str, int32_t>> builder = core::Map::make<core::Str, int32_t>();
    int32_t r;
    try {
      r = core::MapBuilder::remove<core::Str, int32_t>(builder, TEMPER_STR("x"));
    } catch (core::TemperBubble const &) {
      r = -11;
    }
    core::Str s;
    try {
      s = core::Date::toString(core::Date::makeDate<temper_std::Date>(2023, 2, 30));
    } catch (core::TemperBubble const &) {
      s = TEMPER_STR("no date");
    }
    core::Str t;
    try {
      t = core::Date::toString(core::Date::fromIso<temper_std::Date>(TEMPER_STR("2023-13-01")));
    } catch (core::TemperBubble const &) {
      t = TEMPER_STR("bad date");
    }
    core::Str u;
    try {
      u = panics::parsing(TEMPER_STR("[1,"));
    } catch (core::TemperBubble const &) {
      u = TEMPER_STR("bad json");
    }
    core::Console::log(temper_globals::console.value, std::to_string(k) + " " + std::to_string(l) + " " + std::to_string(m) + " " + std::to_string(n) + " " + o + " " + q + " " + std::to_string(r) + " " + s + " " + t + " " + u);
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
