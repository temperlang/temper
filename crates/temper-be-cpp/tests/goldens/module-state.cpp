// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace module_state {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct Palette;

struct Palette : public std::enable_shared_from_this<Palette> {
 private:
  struct temper_statics {
    static temper::core::GlobalSlot<std::shared_ptr<Palette>> warm;
  };
  friend void global_init_init();

 public:
  temper::core::Str name;
  static std::shared_ptr<Palette> const & warm();
  static std::shared_ptr<Palette> make(temper::core::Str const & name);
  static std::shared_ptr<Palette> named(temper::core::Str const & n);
  temper::core::Str get_name() const;
};

// Storage, which the library reads and its init sets; read values
// through the accessors below.
namespace temper_globals {
extern temper::core::GlobalSlot<temper::core::Str> greeting;
extern temper::core::GlobalSlot<int32_t> total;
extern temper::core::GlobalSlot<int32_t> ticks;
extern temper::core::GlobalSlot<std::shared_ptr<Palette>> favorite;
}  // namespace temper_globals

temper::core::Str const & greeting();
// The module's functions write it, so this reads it under their
// lock and gives a copy.
int32_t total();
// The module's functions write it, so this reads it under their
// lock and gives a copy.
int32_t ticks();
std::shared_ptr<Palette> const & favorite();

void tick();
temper::core::Str report();

}  // namespace module_state

// init.cpp
#include <module-state/init.hpp>

#include <mutex>

namespace module_state {

namespace core = temper::core;

namespace temper_globals {

core::GlobalSlot<core::Str> greeting;
static core::GlobalSlot<int32_t> count;
static core::GlobalSlot<int32_t> limit;
core::GlobalSlot<int32_t> total;
core::GlobalSlot<int32_t> ticks;
static core::GlobalSlot<std::shared_ptr<std::vector<core::Str>>> prefixes;
core::GlobalSlot<std::shared_ptr<Palette>> favorite;

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

std::shared_ptr<Palette> const & favorite() {
  global_init_init();
  return temper_globals::favorite.value;
}

core::GlobalSlot<std::shared_ptr<Palette>> Palette::temper_statics::warm;

std::shared_ptr<Palette> const & Palette::warm() {
  global_init_init();
  return Palette::temper_statics::warm.value;
}

std::shared_ptr<Palette> Palette::make(core::Str const & name) {
  global_init_init();
  std::shared_ptr<Palette> self = std::make_shared<Palette>();
  self->name = name;
  return self;
}

std::shared_ptr<Palette> Palette::named(core::Str const & n) {
  global_init_init();
  return Palette::make(core::List::get<core::Str>(temper_globals::prefixes.value, 0) + n);
}

core::Str Palette::get_name() const {
  return this->name;
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
    temper_globals::prefixes.construct();
    temper_globals::favorite.construct();
    Palette::temper_statics::warm.construct();
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    temper_globals::greeting.value = TEMPER_STR("hi");
    temper_globals::count.value = 0;
    temper_globals::limit.value = 3;
    temper_globals::total.value = 10;
    temper_globals::ticks.value = 0;
    int32_t local = 1;
    temper_globals::prefixes.value = core::List::make<core::Str>(TEMPER_STR("p-"));
    Palette::temper_statics::warm.value = Palette::named(TEMPER_STR("warm"));
    temper_globals::favorite.value = Palette::temper_statics::warm.value;
    local = core::Int::add(local, temper_globals::limit.value);
    core::Console::log(console, report());
    core::Console::log(console, report());
    core::Console::log(console, "local " + std::to_string(local));
    core::Console::log(console, Palette::temper_statics::warm.value->name + " " + temper_globals::favorite.value->name);
  });
}

}  // namespace module_state
