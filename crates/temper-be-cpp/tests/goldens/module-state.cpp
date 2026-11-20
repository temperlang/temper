// init.hpp
#pragma once

#include <temper-core/str.hpp>
#include <temper-core/module_init.hpp>
#include <cstdint>

namespace module_state {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

// Storage, which the library reads and its init sets; read values
// through the accessors below.
namespace temper_globals {
extern temper::core::GlobalSlot<temper::core::Str> greeting;
extern temper::core::GlobalSlot<int32_t> total;
extern temper::core::GlobalSlot<int32_t> ticks;
}  // namespace temper_globals

temper::core::Str const & greeting();
// The module's functions write it, so this reads it under their
// lock and gives a copy.
int32_t total();
// The module's functions write it, so this reads it under their
// lock and gives a copy.
int32_t ticks();

void tick();
temper::core::Str report();

}  // namespace module_state

// init.cpp
#include <module-state/init.hpp>

#include <temper-core/int.hpp>
#include <temper-core/console.hpp>
#include <memory>
#include <mutex>
#include <string>

namespace module_state {

namespace core = temper::core;

namespace temper_globals {

core::GlobalSlot<core::Str> greeting;
static core::GlobalSlot<int32_t> count;
static core::GlobalSlot<int32_t> limit;
core::GlobalSlot<int32_t> total;
core::GlobalSlot<int32_t> ticks;

}  // namespace temper_globals

static std::mutex moduleMutex;

// Reads a variable that functions write, under the module's lock.
template <class T>
static T load(T const &variable) {
  std::lock_guard<std::mutex> lock(moduleMutex);
  return variable;
}

core::Str const & greeting() {
  global_init_init();
  return temper_globals::greeting.value;
}

int32_t total() {
  global_init_init();
  return load(temper_globals::total.value);
}

int32_t ticks() {
  global_init_init();
  return load(temper_globals::ticks.value);
}

static int32_t bump() {
  std::lock_guard<std::mutex> lock(moduleMutex);
  temper_globals::count.value = core::Int::add(temper_globals::count.value, 1);
  temper_globals::total.value = core::Int::add(temper_globals::total.value, temper_globals::count.value);
  return temper_globals::count.value;
}

void tick() {
  global_init_init();
  std::lock_guard<std::mutex> lock(moduleMutex);
  temper_globals::ticks.value = core::Int::add(temper_globals::ticks.value, 1);
}

core::Str report() {
  global_init_init();
  int32_t int32 = bump();
  return temper_globals::greeting.value + " " + std::to_string(int32) + " of " + std::to_string(temper_globals::limit.value) + " " + std::to_string(load(temper_globals::total.value));
}

void global_init_init() {
  core::init_once([] {
    temper_globals::greeting.construct();
    temper_globals::count.construct();
    temper_globals::limit.construct();
    temper_globals::total.construct();
    temper_globals::ticks.construct();
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    temper_globals::greeting.value = TEMPER_STR("hi");
    temper_globals::count.value = 0;
    temper_globals::limit.value = 3;
    temper_globals::total.value = 10;
    temper_globals::ticks.value = 0;
    int32_t local = 1;
    local = core::Int::add(local, temper_globals::limit.value);
    core::Console::log(console, report());
    core::Console::log(console, report());
    core::Console::log(console, "local " + std::to_string(local));
  });
}

}  // namespace module_state
