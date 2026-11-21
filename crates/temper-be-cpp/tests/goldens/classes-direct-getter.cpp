// init.hpp
#pragma once

#include <temper-core/str.hpp>
#include <memory>

namespace direct_getter {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct C;

struct C : public std::enable_shared_from_this<C> {
  static std::shared_ptr<C> make();
  temper::core::Str get_place() const;
};

}  // namespace direct_getter

// init.cpp
#include <direct-getter/init.hpp>

#include <temper-core/module_init.hpp>
#include <temper-core/console.hpp>

namespace direct_getter {

namespace core = temper::core;

std::shared_ptr<C> C::make() {
  std::shared_ptr<C> self = std::make_shared<C>();
  return self;
}

core::Str C::get_place() const {
  return TEMPER_STR("Hilo, HI");
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    std::shared_ptr<C> c = C::make();
    core::Console::log(console, "Hello, " + c->get_place() + "!");
  });
}

}  // namespace direct_getter
