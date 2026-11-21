// init.hpp
#pragma once

#include <temper-core/any_value.hpp>
#include <temper-core/str.hpp>
#include <memory>

namespace inherited_getter {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct I;
struct C;

struct I : public virtual temper::core::AnyValueBase {
  virtual temper::core::Str get_place() const;
};

struct C : public virtual I {
  static std::shared_ptr<C> make();
};

}  // namespace inherited_getter

// init.cpp
#include <inherited-getter/init.hpp>

#include <temper-core/module_init.hpp>
#include <temper-core/console.hpp>

namespace inherited_getter {

namespace core = temper::core;

core::Str I::get_place() const {
  return TEMPER_STR("Byron Bay");
}

std::shared_ptr<C> C::make() {
  std::shared_ptr<C> self = std::make_shared<C>();
  return self;
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    std::shared_ptr<C> c = C::make();
    core::Console::log(console, "Goodbye, gotta run, " + c->get_place() + "!");
  });
}

}  // namespace inherited_getter
