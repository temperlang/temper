// init.hpp
#pragma once

#include <temper-core/base.hpp>

namespace as_values {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct StringSink;
struct DelayedFunction;

struct StringSink : public virtual temper::core::AnyValueBase {
  virtual void apply(temper::core::Str const & s) const = 0;
};

struct DelayedFunction : public std::enable_shared_from_this<DelayedFunction> {
  std::function<void(temper::core::Str)> f;
  static std::shared_ptr<DelayedFunction> make(std::function<void(temper::core::Str)> const & f);
  void callIt() const;
};

}  // namespace as_values

// init.cpp
#include <as-values/init.hpp>

#include <temper-core/boolean.hpp>
#include <temper-core/int.hpp>
#include <temper-core/console.hpp>

namespace as_values {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;

}  // namespace temper_globals

static bool isEven(int32_t i);

static void hello(core::Str const & how) {
  core::Console::log(temper_globals::console.value, "Called " + how + "!");
}

static void callIt(std::function<void(core::Str)> const & f) {
  f(TEMPER_STR("by a function"));
}

std::shared_ptr<DelayedFunction> DelayedFunction::make(std::function<void(core::Str)> const & f) {
  std::shared_ptr<DelayedFunction> self = std::make_shared<DelayedFunction>();
  self->f = f;
  return self;
}

void DelayedFunction::callIt() const {
  this->f(TEMPER_STR("from a method"));
}

static bool isOdd(int32_t i) {
  if (i == 0) {
    return false;
  } else {
    return isEven(core::Int::sub(i, 1));
  }
}

static bool isEven(int32_t i) {
  if (i == 0) {
    return true;
  } else {
    return isOdd(core::Int::sub(i, 1));
  }
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::console.value = core::Console::get_console();
    as_values::hello(TEMPER_STR("directly"));
    as_values::callIt(hello);
    DelayedFunction::make(hello)->callIt();
    core::Console::log(temper_globals::console.value, "Is 5 even? " + core::Boolean::toString(isEven(5)));
    core::Console::log(temper_globals::console.value, "Is 6 even? " + core::Boolean::toString(isEven(6)));
  });
}

}  // namespace as_values
