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
struct Problem;
struct Positive;
struct NotSquare;

struct Shape : public virtual temper::core::AnyValueBase {};

struct Square : public virtual Shape {
  int32_t side;
  static std::shared_ptr<Square> make(int32_t side);
  int32_t get_side() const;
};

struct Circle : public virtual Shape {
  static std::shared_ptr<Circle> make();
};

struct Problem : public virtual temper::core::Failure {};

struct Positive : public virtual Problem {
  int32_t n;
  static std::shared_ptr<Positive> make(int32_t n);
  temper::core::Str get_message() const override;
  int32_t get_n() const;
};

struct NotSquare : public virtual Problem {
  static std::shared_ptr<NotSquare> make();
  temper::core::Str get_message() const override;
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

std::shared_ptr<Positive> Positive::make(int32_t n) {
  std::shared_ptr<Positive> self = std::make_shared<Positive>();
  self->n = n;
  return self;
}

core::Str Positive::get_message() const {
  return "positive (n: " + std::to_string(this->n) + ")";
}

int32_t Positive::get_n() const {
  return this->n;
}

std::shared_ptr<NotSquare> NotSquare::make() {
  std::shared_ptr<NotSquare> self = std::make_shared<NotSquare>();
  return self;
}

core::Str NotSquare::get_message() const {
  return TEMPER_STR("not square");
}

static core::Expected<int32_t, std::shared_ptr<Positive>> failing(int32_t n) {
  if (n > 0) {
    return core::unexpected(Positive::make(n));
  }
  return n;
}

static core::Expected<std::shared_ptr<Square>, std::shared_ptr<NotSquare>> downcast(std::shared_ptr<Shape> const & s) {
  if (dynamic_cast<Square const *>(s.get()) != nullptr) {
    return core::checked_cast<Square>(s);
  } else {
    return core::unexpected(NotSquare::make());
  }
}

static int32_t calling(std::function<core::Expected<int32_t, std::shared_ptr<Problem>>()> const & f) {
  auto result = f();
  if (result.has_value()) {
    return *result;
  } else {
    return -9;
  }
}

static core::Expected<core::Str, std::shared_ptr<core::InvalidCodePoint>> appending(int32_t codePoint) {
  std::shared_ptr<std::ostringstream> sb = core::StringBuilder::make();
  auto Result = core::StringBuilder::appendCodepoint(sb, codePoint);
  if (!Result.has_value()) {
    return core::unexpected(Result.error());
  }
  return core::StringBuilder::toString(sb);
}

static core::Expected<core::Str, std::shared_ptr<temper_std::JsonError>> parsing(core::Str const & text) {
  auto parseJsonResult = temper_std::parseJson(text);
  if (!parseJsonResult.has_value()) {
    return core::unexpected(parseJsonResult.error());
  }
  return TEMPER_STR("parsed");
}

static core::Expected<int32_t, std::shared_ptr<Positive>> explode(int32_t n) {
  if (n > 0) {
    core::panic();
  }
  return core::unexpected(Positive::make(n));
}

static int32_t noted(core::Str const & name) {
  core::Console::log(temper_globals::console.value, name + " fallback");
  return -2;
}

static core::Expected<int32_t, std::shared_ptr<Positive>> inner() {
  auto explodeResult = explode(1);
  if (explodeResult.has_value()) {
    return *explodeResult;
  } else {
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
    auto failingResult = failing(0);
    if (failingResult.has_value()) {
      zero = *failingResult;
    } else {
      zero = -1;
    }
    static_cast<void>(zero);
    int32_t a;
    auto failingResult2 = failing(1);
    if (failingResult2.has_value()) {
      a = *failingResult2;
    } else {
      a = -1;
    }
    int32_t b;
    auto result = [&]() -> core::Expected<int32_t, std::shared_ptr<NotSquare>> {
      auto downcastResult = panics::downcast(core::to_base<Shape>(Circle::make()));
      if (!downcastResult.has_value()) {
        return core::unexpected(downcastResult.error());
      }
      return (*downcastResult)->side;
    }();
    if (result.has_value()) {
      b = *result;
    } else {
      b = -2;
    }
    int32_t e;
    auto Result = core::String::toInt32(TEMPER_STR("nope"));
    if (Result.has_value()) {
      e = *Result;
    } else {
      e = -5;
    }
    double f;
    auto Result2 = core::String::toFloat64(TEMPER_STR("x"));
    if (Result2.has_value()) {
      f = *Result2;
    } else {
      f = -6.0;
    }
    core::Str g;
    auto Result3 = core::String::fromCodepoint(1114112);
    if (Result3.has_value()) {
      g = *Result3;
    } else {
      g = TEMPER_STR("?");
    }
    std::shared_ptr<temper_std::Regex> digits = temper_std::Repeat::make(core::to_base<temper_std::RegexNode>(temper_std::CodeSet::make(core::List::make<std::shared_ptr<temper_std::CodePart>>(core::to_base<temper_std::CodePart>(temper_std::CodeRange::make(48, 57))), false)), 1, nullptr, false)->compiled();
    std::shared_ptr<temper_std::Match> subject3 = digits->find(TEMPER_STR("none"));
    std::shared_ptr<temper_std::Group> subject2 = subject3 == nullptr ? nullptr : subject3->full;
    core::NullableParam<core::Str> subject = subject2 == nullptr ? nullptr : core::NullableParam<core::Str>(subject2->value);
    core::Str h = subject == nullptr ? TEMPER_STR("no digits") : subject.value;
    auto fn = []() -> core::Expected<int32_t, std::shared_ptr<Problem>> {
      auto failingResult3 = failing(2);
      if (!failingResult3.has_value()) {
        return core::unexpected(failingResult3.error());
      }
      return *failingResult3;
    };
    int32_t i = panics::calling(fn);
    core::Console::log(temper_globals::console.value, std::to_string(a) + " " + std::to_string(b) + " " + std::to_string(e) + " " + core::Boolean::toString(core::Float64::eq(f, -6.0)) + " " + g + " " + h + " " + std::to_string(i));
    int64_t k;
    auto Result4 = core::String::toInt64(TEMPER_STR("zz"));
    if (Result4.has_value()) {
      k = *Result4;
    } else {
      k = int64_t{-7};
    }
    int32_t l;
    auto Result5 = core::String::toInt32(TEMPER_STR("99999999999"));
    if (Result5.has_value()) {
      l = *Result5;
    } else {
      l = -8;
    }
    int32_t m;
    auto Result6 = core::Float64::toInt32(1e20);
    if (Result6.has_value()) {
      m = *Result6;
    } else {
      m = -9;
    }
    int32_t n;
    auto Result7 = core::Int64::toInt32(int64_t{5000000000});
    if (Result7.has_value()) {
      n = *Result7;
    } else {
      n = -10;
    }
    std::shared_ptr<std::vector<int32_t>> points = core::List::make<int32_t>(65, 1114112);
    core::Str o;
    auto Result8 = core::String::fromCodepoints(points);
    if (Result8.has_value()) {
      o = *Result8;
    } else {
      std::shared_ptr<core::InvalidCodePoint> e2 = Result8.error();
      o = core::Int::toString(e2->codePoint, 16);
    }
    core::Str q;
    auto appendingResult = appending(-1);
    if (appendingResult.has_value()) {
      q = *appendingResult;
    } else {
      std::shared_ptr<core::InvalidCodePoint> e3 = appendingResult.error();
      q = std::to_string(e3->codePoint);
    }
    std::shared_ptr<core::Mapped::Ordered<core::Str, int32_t>> builder = core::Map::make<core::Str, int32_t>();
    int32_t r;
    auto Result9 = core::Mapped::get<core::Str, int32_t>(builder, TEMPER_STR("x"));
    if (Result9.has_value()) {
      r = *Result9;
    } else {
      r = -11;
    }
    core::Str r2;
    auto Result10 = core::MapBuilder::remove<core::Str, int32_t>(builder, TEMPER_STR("y"));
    if (Result10.has_value()) {
      r2 = std::to_string(*Result10);
    } else {
      std::shared_ptr<core::MissingKey> e4 = Result10.error();
      r2 = e4->get_message();
    }
    core::Str s;
    auto dateResult = temper_std::Date::make(2023, 2, 30);
    if (dateResult.has_value()) {
      s = core::Date::toString(*dateResult);
    } else {
      s = TEMPER_STR("no date");
    }
    core::Str t;
    auto fromIsoStringResult = temper_std::Date::fromIsoString(TEMPER_STR("2023-13-01"));
    if (fromIsoStringResult.has_value()) {
      t = core::Date::toString(*fromIsoStringResult);
    } else {
      t = TEMPER_STR("bad date");
    }
    core::Str u;
    auto parsingResult = panics::parsing(TEMPER_STR("[1,"));
    if (parsingResult.has_value()) {
      u = *parsingResult;
    } else {
      u = TEMPER_STR("bad json");
    }
    std::shared_ptr<temper_std::JsonTextProducer> partial = temper_std::JsonTextProducer::make();
    partial->startObject();
    core::Str v;
    auto toJsonStringResult = partial->toJsonString();
    if (toJsonStringResult.has_value()) {
      v = *toJsonStringResult;
    } else {
      std::shared_ptr<temper_std::JsonSyntaxError> e5 = toJsonStringResult.error();
      v = e5->problem;
    }
    core::Console::log(temper_globals::console.value, std::to_string(k) + " " + std::to_string(l) + " " + std::to_string(m) + " " + std::to_string(n) + " " + o + " " + q + " " + std::to_string(r) + " " + r2 + " " + s + " " + t + " " + u);
    core::Console::log(temper_globals::console.value, v);
    core::Console::log(temper_globals::console.value, TEMPER_STR("panicking"));
    int32_t p;
    auto innerResult = inner();
    if (innerResult.has_value()) {
      p = *innerResult;
    } else {
      p = -1;
    }
    core::Console::log(temper_globals::console.value, "after the panic " + std::to_string(p));
  });
}

}  // namespace panics
