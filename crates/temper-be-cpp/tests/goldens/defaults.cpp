// helpers.hpp
#pragma once

#include <temper-core/base.hpp>

namespace defaults {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_helpers();

struct Pad;

struct Pad : public std::enable_shared_from_this<Pad> {
  int32_t left;
  int32_t right;
  static std::shared_ptr<Pad> make(temper::core::NullableParam<int32_t> left, temper::core::NullableParam<int32_t> right);
  static std::shared_ptr<Pad> make(temper::core::NullableParam<int32_t> left);
  static std::shared_ptr<Pad> make();
  temper::core::Str apply(temper::core::Str const & text, temper::core::NullableParam<temper::core::Str> fill) const;
  temper::core::Str apply(temper::core::Str const & text) const;
  int32_t get_left() const;
  int32_t get_right() const;
};

temper::core::Str wrap(temper::core::Str const & text, temper::core::NullableParam<temper::core::Str> open, temper::core::NullableParam<temper::core::Str> close);
temper::core::Str wrap(temper::core::Str const & text, temper::core::NullableParam<temper::core::Str> open);
temper::core::Str wrap(temper::core::Str const & text);

}  // namespace defaults

// helpers.cpp
#include <defaults/helpers.hpp>

#include <temper-core/int.hpp>

namespace defaults {

namespace core = temper::core;

core::Str wrap(core::Str const & text, core::NullableParam<core::Str> open, core::NullableParam<core::Str> close) {
  core::Str open2 = open == nullptr ? TEMPER_STR("[") : open.value;
  core::Str close2 = close == nullptr ? open2 : close.value;
  return open2 + text + close2;
}

core::Str wrap(core::Str const & text, core::NullableParam<core::Str> open) {
  return wrap(text, open, nullptr);
}

core::Str wrap(core::Str const & text) {
  return wrap(text, nullptr, nullptr);
}

std::shared_ptr<Pad> Pad::make(core::NullableParam<int32_t> left, core::NullableParam<int32_t> right) {
  std::shared_ptr<Pad> self = std::make_shared<Pad>();
  int32_t left2 = left == nullptr ? 1 : left.value;
  int32_t right2 = right == nullptr ? 2 : right.value;
  self->left = left2;
  self->right = right2;
  return self;
}

std::shared_ptr<Pad> Pad::make(core::NullableParam<int32_t> left) {
  return make(left, nullptr);
}

std::shared_ptr<Pad> Pad::make() {
  return make(nullptr, nullptr);
}

core::Str Pad::apply(core::Str const & text, core::NullableParam<core::Str> fill) const {
  core::Str fill2 = fill == nullptr ? TEMPER_STR(".") : fill.value;
  core::Str out = text;
  {
    int32_t i = 0;
    while (i < this->left) {
      out = fill2 + out;
      i = core::Int::add(i, 1);
    }
  }
  {
    int32_t i = 0;
    while (i < this->right) {
      out = out + fill2;
      i = core::Int::add(i, 1);
    }
  }
  return out;
}

core::Str Pad::apply(core::Str const & text) const {
  return apply(text, nullptr);
}

int32_t Pad::get_left() const {
  return this->left;
}

int32_t Pad::get_right() const {
  return this->right;
}

void global_init_helpers() {}

}  // namespace defaults

// init.hpp
#pragma once

#include <temper-core/base.hpp>
#include <defaults/helpers.hpp>

namespace defaults {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct Greeter;
struct Counter;

struct Greeter : public std::enable_shared_from_this<Greeter> {
  temper::core::Str greeting;
  temper::core::Str punct;
  static std::shared_ptr<Greeter> make(temper::core::NullableParam<temper::core::Str> greeting, temper::core::NullableParam<temper::core::Str> punct);
  static std::shared_ptr<Greeter> make(temper::core::NullableParam<temper::core::Str> greeting);
  static std::shared_ptr<Greeter> make();
  temper::core::Str greet(temper::core::NullableParam<temper::core::Str> name, temper::core::NullableParam<int32_t> times) const;
  temper::core::Str greet(temper::core::NullableParam<temper::core::Str> name) const;
  temper::core::Str greet() const;
  static temper::core::Str shout(temper::core::Str const & text, temper::core::NullableParam<temper::core::Str> mark);
  static temper::core::Str shout(temper::core::Str const & text);
  temper::core::Str get_greeting() const;
  temper::core::Str get_punct() const;
};

struct Counter : public std::enable_shared_from_this<Counter> {
  int32_t count;
  static std::shared_ptr<Counter> make(temper::core::NullableParam<int32_t> start);
  static std::shared_ptr<Counter> make();
  int32_t add(temper::core::NullableParam<int32_t> by);
  int32_t add();
  int32_t get_count();
};

}  // namespace defaults

// init.cpp
#include <defaults/init.hpp>

#include <temper-core/int.hpp>
#include <temper-core/console.hpp>
#include <mutex>

namespace defaults {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<int32_t> calls;

}  // namespace temper_globals

static std::mutex moduleMutex;

static int32_t next() {
  std::lock_guard<std::mutex> lock(moduleMutex);
  temper_globals::calls.value = core::Int::add(temper_globals::calls.value, 1);
  return temper_globals::calls.value;
}

static core::Str range(int32_t start, core::NullableParam<int32_t> end, core::NullableParam<int32_t> step) {
  int32_t end2 = end == nullptr ? core::Int::add(start, 10) : end.value;
  int32_t step2 = step == nullptr ? next() : step.value;
  return std::to_string(start) + ".." + std::to_string(end2) + " by " + std::to_string(step2);
}

static core::Str range(int32_t start, core::NullableParam<int32_t> end) {
  return range(start, end, nullptr);
}

static core::Str range(int32_t start) {
  return range(start, nullptr, nullptr);
}

static core::Str tag(core::Str const & text, core::NullableParam<core::Str> mark) {
  if (mark == nullptr) {
    return text;
  } else {
    return mark.value + text;
  }
}

static core::Str tag(core::Str const & text) {
  return tag(text, nullptr);
}

static core::Str named(core::NullableParam<core::Str> name) {
  core::NullableParam<core::Str> name2 = name == nullptr ? core::NullableParam<core::Str>(TEMPER_STR("nobody")) : name;
  {
    if (name2 == nullptr) {
      return TEMPER_STR("null");
    } else {
      return name2.value;
    }
  }
}

static core::Str named() {
  return named(nullptr);
}

std::shared_ptr<Greeter> Greeter::make(core::NullableParam<core::Str> greeting, core::NullableParam<core::Str> punct) {
  std::shared_ptr<Greeter> self = std::make_shared<Greeter>();
  core::Str greeting2 = greeting == nullptr ? TEMPER_STR("Hello") : greeting.value;
  core::Str punct2 = punct == nullptr ? TEMPER_STR("!") : punct.value;
  self->greeting = greeting2;
  self->punct = punct2;
  return self;
}

std::shared_ptr<Greeter> Greeter::make(core::NullableParam<core::Str> greeting) {
  return make(greeting, nullptr);
}

std::shared_ptr<Greeter> Greeter::make() {
  return make(nullptr, nullptr);
}

core::Str Greeter::greet(core::NullableParam<core::Str> name, core::NullableParam<int32_t> times) const {
  core::Str name2 = name == nullptr ? TEMPER_STR("world") : name.value;
  int32_t times2 = times == nullptr ? 1 : times.value;
  core::Str out = core::Str();
  {
    int32_t i = 0;
    while (i < times2) {
      out = out + this->greeting + ", " + name2 + this->punct;
      i = core::Int::add(i, 1);
    }
  }
  return out;
}

core::Str Greeter::greet(core::NullableParam<core::Str> name) const {
  return greet(name, nullptr);
}

core::Str Greeter::greet() const {
  return greet(nullptr, nullptr);
}

core::Str Greeter::shout(core::Str const & text, core::NullableParam<core::Str> mark) {
  core::Str mark2 = mark == nullptr ? TEMPER_STR("!") : mark.value;
  return text + mark2 + mark2;
}

core::Str Greeter::shout(core::Str const & text) {
  return shout(text, nullptr);
}

core::Str Greeter::get_greeting() const {
  return this->greeting;
}

core::Str Greeter::get_punct() const {
  return this->punct;
}

std::shared_ptr<Counter> Counter::make(core::NullableParam<int32_t> start) {
  std::shared_ptr<Counter> self = std::make_shared<Counter>();
  int32_t start2 = start == nullptr ? 10 : start.value;
  self->count = start2;
  return self;
}

std::shared_ptr<Counter> Counter::make() {
  return make(nullptr);
}

int32_t Counter::add(core::NullableParam<int32_t> by) {
  int32_t by2 = by == nullptr ? 1 : by.value;
  this->count = core::Int::add(this->count, by2);
  return this->count;
}

int32_t Counter::add() {
  return add(nullptr);
}

int32_t Counter::get_count() {
  return this->count;
}

void global_init_init() {
  core::init_once([] {
    temper_globals::calls.construct();
    global_init_helpers();
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    temper_globals::calls.value = 0;
    core::Console::log(console, defaults::range(1));
    core::Console::log(console, defaults::range(1, 5));
    core::Console::log(console, defaults::range(1, 5, 3));
    core::Console::log(console, defaults::range(2, nullptr, 7));
    core::Console::log(console, "calls " + std::to_string(temper_globals::calls.value));
    core::Console::log(console, defaults::tag(TEMPER_STR("plain")));
    core::Console::log(console, defaults::tag(TEMPER_STR("marked"), TEMPER_STR("#")));
    core::Console::log(console, defaults::named());
    core::Console::log(console, defaults::named(nullptr));
    core::Console::log(console, defaults::named(TEMPER_STR("Ada")));
    core::Console::log(console, Greeter::make()->greet());
    core::Console::log(console, Greeter::make(TEMPER_STR("Hi"))->greet(TEMPER_STR("you"), 2));
    core::Console::log(console, Greeter::make(nullptr, TEMPER_STR("?"))->greet(nullptr, 2));
    core::Console::log(console, Greeter::shout(TEMPER_STR("hey")));
    std::shared_ptr<Counter> counter = Counter::make();
    counter->add();
    int32_t int32 = counter->add(5);
    core::Console::log(console, "count " + std::to_string(int32) + " " + std::to_string(Counter::make(1)->add()));
    core::Console::log(console, defaults::wrap(TEMPER_STR("a")));
    core::Console::log(console, defaults::wrap(TEMPER_STR("b"), TEMPER_STR("<")));
    core::Console::log(console, defaults::wrap(TEMPER_STR("c"), TEMPER_STR("<"), TEMPER_STR(">")));
    std::shared_ptr<Pad> pad = Pad::make();
    core::Console::log(console, pad->apply(TEMPER_STR("x")));
    core::Console::log(console, Pad::make(0, 3)->apply(TEMPER_STR("y"), TEMPER_STR("-")));
  });
}

}  // namespace defaults

// main.cpp
#include "defaults/helpers.hpp"
#include "defaults/init.hpp"

int main() {
  defaults::global_init_helpers();
  defaults::global_init_init();
}

// CMakeLists.txt
# Generated by Temper.
cmake_minimum_required(VERSION 3.10)
project(defaults LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
find_package(Threads REQUIRED)

add_library(defaults helpers.cpp init.cpp)
target_include_directories(defaults PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/..)
target_link_libraries(defaults PUBLIC Threads::Threads)

add_executable(defaults-main main.cpp)
target_link_libraries(defaults-main PRIVATE defaults)

