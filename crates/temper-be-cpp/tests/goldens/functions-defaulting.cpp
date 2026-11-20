// init.hpp
#pragma once

#include <temper-core/base.hpp>

namespace defaulting {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct Thing;
struct StringHolder;

struct Thing : public std::enable_shared_from_this<Thing> {
  static std::shared_ptr<Thing> make();
  void say(temper::core::NullableParam<temper::core::Str> a, temper::core::NullableParam<temper::core::Str> b) const;
  void say(temper::core::NullableParam<temper::core::Str> a) const;
  void say() const;
  static void sayStatic(temper::core::Str const & a, temper::core::NullableParam<temper::core::Str> b);
  static void sayStatic(temper::core::Str const & a);
};

struct StringHolder : public std::enable_shared_from_this<StringHolder> {
  temper::core::Str string;
  static std::shared_ptr<StringHolder> make(temper::core::Str const & string);
  temper::core::Str get_string() const;
};

}  // namespace defaulting

// init.cpp
#include <defaulting/init.hpp>

#include <temper-core/boolean.hpp>
#include <temper-core/int.hpp>
#include <temper-core/console.hpp>
#include <temper-core/string.hpp>
#include <mutex>

namespace defaulting {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;
static core::GlobalSlot<int32_t> fCalls;
static core::GlobalSlot<int32_t> inked;

}  // namespace temper_globals

static std::mutex moduleMutex;

// Reads a variable that functions write, under the module's lock.
template <class T>
static T load(T const &variable) {
  std::lock_guard<std::mutex> lock(moduleMutex);
  return variable;
}

static int32_t f() {
  std::lock_guard<std::mutex> lock(moduleMutex);
  temper_globals::fCalls.value = core::Int::add(temper_globals::fCalls.value, 1);
  return 42;
}

static int32_t g(core::NullableParam<int32_t> x) {
  int32_t x2 = x == nullptr ? f() : x.value;
  return x2;
}

static int32_t g() {
  return g(nullptr);
}

static core::NullableParam<int32_t> noInline() {
  core::Console::log(temper_globals::console.value, TEMPER_STR("noInline"));
  return nullptr;
}

std::shared_ptr<Thing> Thing::make() {
  std::shared_ptr<Thing> self = std::make_shared<Thing>();
  return self;
}

void Thing::say(core::NullableParam<core::Str> a, core::NullableParam<core::Str> b) const {
  core::Str a2 = a == nullptr ? core::Str() : a.value;
  core::Str b2 = b == nullptr ? core::Str() : b.value;
  core::Console::log(temper_globals::console.value, "saying " + a2 + b2);
}

void Thing::say(core::NullableParam<core::Str> a) const {
  return say(a, nullptr);
}

void Thing::say() const {
  return say(nullptr, nullptr);
}

void Thing::sayStatic(core::Str const & a, core::NullableParam<core::Str> b) {
  core::Str b2 = b == nullptr ? core::Str() : b.value;
  core::Console::log(temper_globals::console.value, "static " + a + b2);
}

void Thing::sayStatic(core::Str const & a) {
  return sayStatic(a, nullptr);
}

std::shared_ptr<StringHolder> StringHolder::make(core::Str const & string) {
  std::shared_ptr<StringHolder> self = std::make_shared<StringHolder>();
  self->string = string;
  return self;
}

core::Str StringHolder::get_string() const {
  return this->string;
}

static core::Str maybeEmpty(std::shared_ptr<StringHolder> const & a) {
  {
    std::lock_guard<std::mutex> lock(moduleMutex);
    temper_globals::inked.value = core::Int::add(temper_globals::inked.value, 1);
  }
  {
    core::NullableParam<core::Str> subject3 = a == nullptr ? nullptr : core::NullableParam<core::Str>(a->string);
    core::NullableParam<bool> subject2 = subject3 == nullptr ? nullptr : core::NullableParam<bool>(subject3.value.empty());
    core::NullableParam<core::Str> subject = subject2 == nullptr ? nullptr : core::NullableParam<core::Str>(core::Boolean::toString(subject2.value));
    if (subject == nullptr) {
      return TEMPER_STR("null");
    } else {
      return subject.value;
    }
  }
}

static core::NullableParam<int32_t> maybeLength(core::NullableParam<core::Str> a) {
  {
    std::lock_guard<std::mutex> lock(moduleMutex);
    temper_globals::inked.value = core::Int::add(temper_globals::inked.value, 1);
  }
  {
    if (a == nullptr) {
      return nullptr;
    } else {
      return core::String::countBetween(a.value, core::String::begin(), core::String::end(a.value));
    }
  }
}

static core::NullableParam<int32_t> maybeInc() {
  if (load(temper_globals::inked.value) == 10) {
    return nullptr;
  } else {
    {
      std::lock_guard<std::mutex> lock(moduleMutex);
      temper_globals::inked.value = core::Int::add(temper_globals::inked.value, 1);
    }
    return load(temper_globals::inked.value);
  }
}

static core::Str chainMore(core::NullableParam<core::Str> a) {
  core::NullableParam<int32_t> subject2 = defaulting::maybeLength(a);
  core::NullableParam<int32_t> subject = subject2 == nullptr ? nullptr : core::NullableParam<int32_t>(std::max(subject2.value, 0));
  return std::to_string(subject == nullptr ? -1 : subject.value);
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::fCalls.construct();
    temper_globals::inked.construct();
    temper_globals::console.value = core::Console::get_console();
    temper_globals::fCalls.value = 0;
    core::Console::log(temper_globals::console.value, "fCalls=" + std::to_string(temper_globals::fCalls.value));
    core::Console::log(temper_globals::console.value, "g(   )=" + std::to_string(defaulting::g()));
    core::Console::log(temper_globals::console.value, "fCalls=" + std::to_string(temper_globals::fCalls.value));
    core::Console::log(temper_globals::console.value, "g(123)=" + std::to_string(defaulting::g(123)));
    core::Console::log(temper_globals::console.value, "fCalls=" + std::to_string(temper_globals::fCalls.value));
    core::Console::log(temper_globals::console.value, "g(null)=" + std::to_string(defaulting::g(noInline())));
    core::Console::log(temper_globals::console.value, "fCalls=" + std::to_string(temper_globals::fCalls.value));
    Thing::make()->say(TEMPER_STR("hi"));
    Thing::make()->say(nullptr, TEMPER_STR("there"));
    Thing::make()->say(TEMPER_STR("by"), TEMPER_STR("e"));
    Thing::sayStatic(TEMPER_STR("yall"));
    temper_globals::inked.value = 0;
    core::NullableParam<int32_t> subject = maybeInc();
    core::Console::log(temper_globals::console.value, "maybeInc() -> " + std::to_string(subject == nullptr ? -1 : subject.value));
    core::Console::log(temper_globals::console.value, "inked: " + std::to_string(temper_globals::inked.value));
    core::Console::log(temper_globals::console.value, "lengthish \"ab\": " + defaulting::chainMore(TEMPER_STR("ab")));
    core::Console::log(temper_globals::console.value, "lengthish null: " + defaulting::chainMore(nullptr));
    core::Console::log(temper_globals::console.value, "inked: " + std::to_string(temper_globals::inked.value));
    core::Console::log(temper_globals::console.value, "maybeEmpty \"\": " + defaulting::maybeEmpty(StringHolder::make(core::Str())));
    core::Console::log(temper_globals::console.value, "maybeEmpty null: " + defaulting::maybeEmpty(nullptr));
    core::Console::log(temper_globals::console.value, "inked: " + std::to_string(temper_globals::inked.value));
  });
}

}  // namespace defaulting
