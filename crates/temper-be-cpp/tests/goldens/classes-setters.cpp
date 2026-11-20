// init.hpp
#pragma once

#include <temper-core/base.hpp>

namespace setters {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct Foo;
struct Bar;
struct Whatever;
struct ConcreteWhatever;

struct Foo : public std::enable_shared_from_this<Foo> {
  temper::core::Str backing;
  static std::shared_ptr<Foo> make(temper::core::Str const & backing);
  temper::core::Str get_bar();
  void set_bar(temper::core::Str const & value);
};

struct Bar : public std::enable_shared_from_this<Bar> {
  temper::core::Str backing;
  static std::shared_ptr<Bar> make(temper::core::Str const & backing);
  temper::core::Str get_bar();
  void set_bar(temper::core::Str const & value);
  void usingSet(temper::core::Str const & value);
};

struct Whatever : public virtual temper::core::AnyValueBase {
  virtual int32_t get_something() = 0;
  virtual void set_something(int32_t blah) = 0;
};

struct ConcreteWhatever : public virtual Whatever {
  int32_t something;
  static std::shared_ptr<ConcreteWhatever> make(int32_t something);
  int32_t get_something() override;
  void set_something(int32_t value) override;
};

}  // namespace setters

// init.cpp
#include <setters/init.hpp>

#include <temper-core/casting.hpp>
#include <temper-core/console.hpp>

namespace setters {

namespace core = temper::core;

std::shared_ptr<Foo> Foo::make(core::Str const & backing) {
  std::shared_ptr<Foo> self = std::make_shared<Foo>();
  self->backing = backing;
  return self;
}

core::Str Foo::get_bar() {
  return this->backing;
}

void Foo::set_bar(core::Str const & value) {
  this->backing = value;
}

std::shared_ptr<Bar> Bar::make(core::Str const & backing) {
  std::shared_ptr<Bar> self = std::make_shared<Bar>();
  self->backing = backing;
  return self;
}

core::Str Bar::get_bar() {
  return this->backing;
}

void Bar::set_bar(core::Str const & value) {
  this->backing = value;
}

void Bar::usingSet(core::Str const & value) {
  std::shared_ptr<Bar> self = shared_from_this();
  self->set_bar(value);
}

std::shared_ptr<ConcreteWhatever> ConcreteWhatever::make(int32_t something) {
  global_init_init();
  std::shared_ptr<ConcreteWhatever> self = std::make_shared<ConcreteWhatever>();
  self->something = something;
  return self;
}

int32_t ConcreteWhatever::get_something() {
  return this->something;
}

void ConcreteWhatever::set_something(int32_t value) {
  this->something = value;
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    core::Console::log(console, Foo::make(TEMPER_STR("initial"))->get_bar());
    std::shared_ptr<Foo> foo = Foo::make(TEMPER_STR("initial"));
    foo->set_bar(TEMPER_STR("next"));
    core::Console::log(console, foo->get_bar());
    std::shared_ptr<Bar> bar = Bar::make(TEMPER_STR("initial"));
    bar->usingSet(TEMPER_STR("next"));
    core::Console::log(console, bar->get_bar());
    std::shared_ptr<Whatever> what = core::to_base<Whatever>(ConcreteWhatever::make(4));
    what->set_something(5);
    core::Console::log(console, "something set to " + std::to_string(what->get_something()));
  });
}

}  // namespace setters
