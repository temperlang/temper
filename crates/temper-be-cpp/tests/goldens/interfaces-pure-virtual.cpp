// init.hpp
#pragma once

#include <temper-core/any_value.hpp>
#include <temper-core/str.hpp>
#include <cstdint>
#include <memory>

namespace pure_virtual {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct Greeter;
struct HiGreeter;
template <class T>
struct Wrapper;
struct Box;
struct BoxWrapper;
struct IntWrapper;

struct Greeter : public virtual temper::core::AnyValueBase {
  virtual void greet() const = 0;
};

struct HiGreeter : public virtual Greeter {
  static std::shared_ptr<HiGreeter> make();
  void greet() const override;
};

template <class T>
struct Wrapper : public virtual temper::core::AnyValueBase {
  virtual T wrap(T item) const = 0;
};

struct Box : public std::enable_shared_from_this<Box> {
  temper::core::Str label;
  static std::shared_ptr<Box> make(temper::core::Str const & label);
  temper::core::Str get_label() const;
};

struct BoxWrapper : public virtual Wrapper<std::shared_ptr<Box>> {
  static std::shared_ptr<BoxWrapper> make();
  std::shared_ptr<Box> wrap(std::shared_ptr<Box> item) const override;
};

struct IntWrapper : public virtual Wrapper<int32_t> {
  static std::shared_ptr<IntWrapper> make();
  int32_t wrap(int32_t item) const override;
};

}  // namespace pure_virtual

// init.cpp
#include <pure-virtual/init.hpp>

#include <temper-core/module_init.hpp>
#include <temper-core/casting.hpp>
#include <temper-core/int.hpp>
#include <temper-core/console.hpp>
#include <string>

namespace pure_virtual {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;

}  // namespace temper_globals

std::shared_ptr<HiGreeter> HiGreeter::make() {
  std::shared_ptr<HiGreeter> self = std::make_shared<HiGreeter>();
  return self;
}

void HiGreeter::greet() const {
  core::Console::log(temper_globals::console.value, TEMPER_STR("Hi, virtually!"));
}

std::shared_ptr<Box> Box::make(core::Str const & label) {
  std::shared_ptr<Box> self = std::make_shared<Box>();
  self->label = label;
  return self;
}

core::Str Box::get_label() const {
  return this->label;
}

std::shared_ptr<BoxWrapper> BoxWrapper::make() {
  std::shared_ptr<BoxWrapper> self = std::make_shared<BoxWrapper>();
  return self;
}

std::shared_ptr<Box> BoxWrapper::wrap(std::shared_ptr<Box> item) const {
  item = Box::make("[" + item->label + "]");
  return item;
}

std::shared_ptr<IntWrapper> IntWrapper::make() {
  std::shared_ptr<IntWrapper> self = std::make_shared<IntWrapper>();
  return self;
}

int32_t IntWrapper::wrap(int32_t item) const {
  return core::Int::add(item, 1);
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::console.value = core::Console::get_console();
    std::shared_ptr<Greeter> greeter = core::to_base<Greeter>(HiGreeter::make());
    greeter->greet();
    std::shared_ptr<Wrapper<std::shared_ptr<Box>>> boxWrapper = core::to_base<Wrapper<std::shared_ptr<Box>>>(BoxWrapper::make());
    std::shared_ptr<Wrapper<int32_t>> intWrapper = core::to_base<Wrapper<int32_t>>(IntWrapper::make());
    core::Console::log(temper_globals::console.value, boxWrapper->wrap(Box::make(TEMPER_STR("box")))->label);
    core::Console::log(temper_globals::console.value, std::to_string(intWrapper->wrap(41)));
  });
}

}  // namespace pure_virtual
