// helpers/deep.hpp
#pragma once

#include <temper-core/base.hpp>

namespace modules {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_deep();

temper::core::Str greet(temper::core::Str const & who);

}  // namespace modules

// helpers/deep.cpp
#include <modules/helpers/deep.hpp>

namespace modules {

namespace core = temper::core;

core::Str greet(core::Str const & who) {
  return "hello, " + who + " from deep";
}

void global_init_deep() {}

}  // namespace modules

// helpers.hpp
#pragma once

#include <temper-core/base.hpp>
#include <modules/helpers/deep.hpp>

namespace modules {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_helpers();

// Storage, which the library reads and its init sets; read values
// through the accessors below.
namespace temper_globals {
extern temper::core::GlobalSlot<int32_t> base;
extern temper::core::GlobalSlot<int32_t> calls;
}  // namespace temper_globals

int32_t const & base();
// The module's functions write it, so this reads it under their
// lock and gives a copy.
int32_t calls();

int32_t twice(int32_t x);
int32_t intDoubled(int32_t x);
int32_t bump();

}  // namespace modules

// helpers.cpp
#include <modules/helpers.hpp>

#include <temper-core/int.hpp>
#include <temper-core/console.hpp>
#include <mutex>

namespace modules {

namespace core = temper::core;

namespace temper_globals {

core::GlobalSlot<int32_t> base;
core::GlobalSlot<int32_t> calls;

}  // namespace temper_globals

static std::mutex moduleMutex;

// Reads a variable that functions write, under the module's lock.
template <class T>
static T load(T const &variable) {
  std::lock_guard<std::mutex> lock(moduleMutex);
  return variable;
}

int32_t const & base() {
  global_init_helpers();
  return temper_globals::base.value;
}

int32_t calls() {
  global_init_helpers();
  return load(temper_globals::calls.value);
}

int32_t twice(int32_t x) {
  global_init_helpers();
  return core::Int::mul(x, 2);
}

int32_t intDoubled(int32_t x) {
  global_init_helpers();
  return twice(x);
}

int32_t bump() {
  global_init_helpers();
  std::lock_guard<std::mutex> lock(moduleMutex);
  temper_globals::calls.value = core::Int::add(temper_globals::calls.value, 1);
  return temper_globals::calls.value;
}

void global_init_helpers() {
  core::init_once([] {
    temper_globals::base.construct();
    temper_globals::calls.construct();
    global_init_deep();
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    temper_globals::base.value = 10;
    temper_globals::calls.value = 0;
    core::Console::log(console, modules::greet(TEMPER_STR("helpers")));
  });
}

}  // namespace modules

// core.hpp
#pragma once

#include <temper-core/base.hpp>

namespace modules {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_core();

struct Ratio;

struct Ratio : public std::enable_shared_from_this<Ratio> {
  int32_t n;
  int32_t d;
  static std::shared_ptr<Ratio> make(int32_t n, int32_t d);
  bool eq(std::shared_ptr<Ratio> const & other) const;
  int32_t get_n() const;
  int32_t get_d() const;
};

int32_t share(int32_t n, int32_t d);
std::shared_ptr<Ratio> ratioOf(int32_t n, int32_t d);

}  // namespace modules

// core.cpp
#include <modules/core.hpp>

#include <temper-core/int.hpp>

namespace modules {

namespace core = temper::core;

int32_t share(int32_t n, int32_t d) {
  try {
    return core::Int::div_wrap(n, d);
  } catch (core::TemperBubble const &) {
    return 0;
  }
}

std::shared_ptr<Ratio> Ratio::make(int32_t n, int32_t d) {
  std::shared_ptr<Ratio> self = std::make_shared<Ratio>();
  self->n = n;
  self->d = d;
  return self;
}

bool Ratio::eq(std::shared_ptr<Ratio> const & other) const {
  return core::Int::mul(this->n, other->d) == core::Int::mul(other->n, this->d);
}

int32_t Ratio::get_n() const {
  return this->n;
}

int32_t Ratio::get_d() const {
  return this->d;
}

std::shared_ptr<Ratio> ratioOf(int32_t n, int32_t d) {
  if (d == 0) {
    return nullptr;
  } else {
    return Ratio::make(n, d);
  }
}

void global_init_core() {}

}  // namespace modules

// state.hpp
#pragma once

#include <temper-core/base.hpp>

namespace modules {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_state();

struct State;
struct Engine;
struct Parts;

struct State : public std::enable_shared_from_this<State> {
  int32_t id;
  static std::shared_ptr<State> make(int32_t id);
  int32_t get_id() const;
};

struct Engine : public std::enable_shared_from_this<Engine> {
  int32_t n;
  static std::shared_ptr<Engine> make(int32_t n);
  int32_t get_n() const;
};

struct Parts : public std::enable_shared_from_this<Parts> {
  int32_t count;
  static std::shared_ptr<Parts> make(int32_t count);
  int32_t get_count() const;
};

// Storage, which the library reads and its init sets; read values
// through the accessors below.
namespace temper_globals {
extern temper::core::GlobalSlot<int32_t> firstId;
}  // namespace temper_globals

int32_t const & firstId();

}  // namespace modules

// state.cpp
#include <modules/state.hpp>

namespace modules {

namespace core = temper::core;

namespace temper_globals {

core::GlobalSlot<int32_t> firstId;

}  // namespace temper_globals

int32_t const & firstId() {
  global_init_state();
  return temper_globals::firstId.value;
}

std::shared_ptr<State> State::make(int32_t id) {
  global_init_state();
  std::shared_ptr<State> self = std::make_shared<State>();
  self->id = id;
  return self;
}

int32_t State::get_id() const {
  return this->id;
}

std::shared_ptr<Engine> Engine::make(int32_t n) {
  global_init_state();
  std::shared_ptr<Engine> self = std::make_shared<Engine>();
  self->n = n;
  return self;
}

int32_t Engine::get_n() const {
  return this->n;
}

std::shared_ptr<Parts> Parts::make(int32_t count) {
  global_init_state();
  std::shared_ptr<Parts> self = std::make_shared<Parts>();
  self->count = count;
  return self;
}

int32_t Parts::get_count() const {
  return this->count;
}

void global_init_state() {
  core::init_once([] {
    temper_globals::firstId.construct();
    temper_globals::firstId.value = 1;
  });
}

}  // namespace modules

// parts/regex.hpp
#pragma once

#include <temper-core/base.hpp>

namespace modules {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_regex();

int32_t wheels();

}  // namespace modules

// parts/regex.cpp
#include <modules/parts/regex.hpp>

namespace modules {

namespace core = temper::core;

int32_t wheels() {
  return 4;
}

void global_init_regex() {}

}  // namespace modules

// machine.hpp
#pragma once

#include <temper-core/base.hpp>
#include <modules/state.hpp>
#include <modules/parts/regex.hpp>
#include <std/regex.hpp>

namespace modules {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_machine();

temper::core::Str describe();

}  // namespace modules

// machine.cpp
#include <modules/machine.hpp>

#include <temper-core/casting.hpp>
#include <temper-core/int.hpp>

namespace modules {

namespace core = temper::core;

core::Str describe() {
  global_init_machine();
  std::shared_ptr<State> state = State::make(temper_globals::firstId.value);
  std::shared_ptr<Engine> engine = Engine::make(wheels());
  int32_t least = temper_std::oneOrMore(core::to_base<temper_std::RegexNode>(temper_std::temper_globals::Dot.value))->min;
  std::shared_ptr<Parts> parts = Parts::make(core::Int::add(engine->n, least));
  return "state " + std::to_string(state->id) + " engine " + std::to_string(engine->n) + " parts " + std::to_string(parts->count);
}

void global_init_machine() {
  core::init_once([] {
    global_init_state();
    global_init_regex();
    temper_std::global_init_regex();
  });
}

}  // namespace modules

// init.hpp
#pragma once

#include <temper-core/base.hpp>
#include <modules/helpers/deep.hpp>
#include <modules/helpers.hpp>
#include <modules/core.hpp>
#include <modules/machine.hpp>

namespace modules {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct Engine2;

struct Engine2 : public std::enable_shared_from_this<Engine2> {
  temper::core::Str name;
  static std::shared_ptr<Engine2> make(temper::core::Str const & name);
  temper::core::Str get_name() const;
};

int32_t sum(int32_t n);

}  // namespace modules

// init.cpp
#include <modules/init.hpp>

#include <temper-core/boolean.hpp>
#include <temper-core/int.hpp>
#include <temper-core/console.hpp>

namespace modules {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<int32_t> base2;

}  // namespace temper_globals

std::shared_ptr<Engine2> Engine2::make(core::Str const & name) {
  std::shared_ptr<Engine2> self = std::make_shared<Engine2>();
  self->name = name;
  return self;
}

core::Str Engine2::get_name() const {
  return this->name;
}

int32_t sum(int32_t n) {
  global_init_init();
  return core::Int::add(core::Int::add(twice(n), temper_globals::base.value), temper_globals::base2.value);
}

void global_init_init() {
  core::init_once([] {
    temper_globals::base2.construct();
    global_init_deep();
    global_init_helpers();
    global_init_core();
    global_init_machine();
    std::shared_ptr<core::Console::Type> console2 = core::Console::get_console();
    int32_t calls2 = temper_globals::calls.value;
    temper_globals::base2.value = 1;
    core::Console::log(console2, modules::greet(TEMPER_STR("modules")));
    core::Console::log(console2, "sum " + std::to_string(sum(3)));
    core::Console::log(console2, "share " + std::to_string(share(7, 2)) + " " + std::to_string(intDoubled(21)));
    core::Console::log(console2, Engine2::make(TEMPER_STR("own"))->name + ", " + describe());
    bump();
    bump();
    core::Console::log(console2, "calls " + std::to_string(calls2));
    core::Console::log(console2, "calls now " + std::to_string(bump()));
    std::shared_ptr<Ratio> half = Ratio::make(1, 2);
    core::Str string = core::Boolean::toString(half->eq(Ratio::make(2, 4))) + " ";
    std::shared_ptr<Ratio> left = ratioOf(3, 6);
    std::shared_ptr<Ratio> left2 = half;
    std::shared_ptr<Ratio> right = ratioOf(1, 0);
    core::Console::log(console2, string + core::Boolean::toString(left == nullptr ? false : left->eq(half)) + " " + core::Boolean::toString(!(right == nullptr ? false : left2->eq(right))));
  });
}

}  // namespace modules

// client.hpp
#pragma once

#include <temper-core/base.hpp>
#include <modules/init.hpp>

namespace modules {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_client();

int32_t sumTwice(int32_t n);

}  // namespace modules

// client.cpp
#include <modules/client.hpp>

#include <temper-core/int.hpp>

namespace modules {

namespace core = temper::core;

int32_t sumTwice(int32_t n) {
  global_init_client();
  return core::Int::mul(sum(n), 2);
}

void global_init_client() {
  core::init_once([] {
    global_init_init();
  });
}

}  // namespace modules

// main.cpp
#include "modules/helpers/deep.hpp"
#include "modules/helpers.hpp"
#include "modules/core.hpp"
#include "modules/state.hpp"
#include "modules/parts/regex.hpp"
#include "modules/machine.hpp"
#include "modules/init.hpp"
#include "modules/client.hpp"

int main() {
  modules::global_init_deep();
  modules::global_init_helpers();
  modules::global_init_core();
  modules::global_init_state();
  modules::global_init_regex();
  modules::global_init_machine();
  modules::global_init_init();
  modules::global_init_client();
}

// CMakeLists.txt
# Generated by Temper.
cmake_minimum_required(VERSION 3.10)
project(modules LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
find_package(Threads REQUIRED)
if(NOT TARGET std)
  add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../std ${CMAKE_CURRENT_BINARY_DIR}/std)
endif()

add_library(modules helpers/deep.cpp helpers.cpp core.cpp state.cpp parts/regex.cpp machine.cpp init.cpp client.cpp)
target_include_directories(modules PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/..)
target_link_libraries(modules PUBLIC Threads::Threads std)

add_executable(modules-main main.cpp)
target_link_libraries(modules-main PRIVATE modules)

