// init.hpp
#pragma once

#include <memory>

namespace mutually_referencing_types {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct Bar;
struct Foo;

struct Bar : public std::enable_shared_from_this<Bar> {
  std::shared_ptr<Foo> x;
  static std::shared_ptr<Bar> make(std::shared_ptr<Foo> const & x);
  std::shared_ptr<Foo> get_x() const;
};

struct Foo : public std::enable_shared_from_this<Foo> {
  std::shared_ptr<Bar> x;
  static std::shared_ptr<Foo> make(std::shared_ptr<Bar> const & x);
  std::shared_ptr<Bar> get_x() const;
};

}  // namespace mutually_referencing_types

// init.cpp
#include <mutually-referencing-types/init.hpp>

#include <temper-core/module_init.hpp>
#include <temper-core/boolean.hpp>
#include <temper-core/console.hpp>

namespace mutually_referencing_types {

namespace core = temper::core;

std::shared_ptr<Bar> Bar::make(std::shared_ptr<Foo> const & x) {
  std::shared_ptr<Bar> self = std::make_shared<Bar>();
  self->x = x;
  return self;
}

std::shared_ptr<Foo> Bar::get_x() const {
  return this->x;
}

std::shared_ptr<Foo> Foo::make(std::shared_ptr<Bar> const & x) {
  std::shared_ptr<Foo> self = std::make_shared<Foo>();
  self->x = x;
  return self;
}

std::shared_ptr<Bar> Foo::get_x() const {
  return this->x;
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    std::shared_ptr<Foo> emptyFoo = Foo::make(nullptr);
    std::shared_ptr<Bar> emptyBar = Bar::make(nullptr);
    std::shared_ptr<Foo> nonEmptyFoo = Foo::make(emptyBar);
    std::shared_ptr<Bar> nonEmptyBar = Bar::make(emptyFoo);
    std::shared_ptr<Bar> stuffedBar = Bar::make(nonEmptyFoo);
    core::Console::log(console, "emptyFoo.x has Foo -> " + core::Boolean::toString(emptyFoo->x != nullptr));
    core::Console::log(console, "nonEmptyFoo.x has Foo -> " + core::Boolean::toString(nonEmptyFoo->x != nullptr));
  });
}

}  // namespace mutually_referencing_types
