// init.hpp
#pragma once

#include <temper-core/str.hpp>
#include <memory>

namespace hello_world_object {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct C;

struct C : public std::enable_shared_from_this<C> {
  temper::core::Str x;
  temper::core::Str y;
  static std::shared_ptr<C> make(temper::core::Str const & x, temper::core::Str const & y);
  void echo(temper::core::Str const & input) const;
  temper::core::Str get_x() const;
  temper::core::Str get_y() const;
};

}  // namespace hello_world_object

// init.cpp
#include <hello-world-object/init.hpp>

#include <temper-core/module_init.hpp>
#include <temper-core/console.hpp>

namespace hello_world_object {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;

}  // namespace temper_globals

std::shared_ptr<C> C::make(core::Str const & x, core::Str const & y) {
  std::shared_ptr<C> self = std::make_shared<C>();
  self->x = x;
  self->y = y;
  return self;
}

void C::echo(core::Str const & input) const {
  core::Console::log(temper_globals::console.value, input);
}

core::Str C::get_x() const {
  return this->x;
}

core::Str C::get_y() const {
  return this->y;
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::console.value = core::Console::get_console();
    std::shared_ptr<C> c = C::make(TEMPER_STR("Hello"), TEMPER_STR("World"));
    core::Console::log(temper_globals::console.value, c->x + ", " + c->y + "!");
    c->echo(TEMPER_STR("Hello World, again!"));
  });
}

}  // namespace hello_world_object
