// init.hpp
#pragma once

#include <temper-core/base.hpp>

namespace property_order {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct C;

struct C : public std::enable_shared_from_this<C> {
  int32_t backed;
  static std::shared_ptr<C> make(temper::core::NullableParam<int32_t> backed);
  static std::shared_ptr<C> make();
  int32_t get_computed() const;
  int32_t get_backed() const;
};

}  // namespace property_order

// init.cpp
#include <property-order/init.hpp>

#include <temper-core/console.hpp>

namespace property_order {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;

}  // namespace temper_globals

std::shared_ptr<C> C::make(core::NullableParam<int32_t> backed) {
  std::shared_ptr<C> self = std::make_shared<C>();
  int32_t backed2;
  if (backed == nullptr) {
    core::Console::log(temper_globals::console.value, TEMPER_STR("initializing backed"));
    backed2 = 1;
  } else {
    backed2 = backed.value;
  }
  self->backed = backed2;
  return self;
}

std::shared_ptr<C> C::make() {
  return make(nullptr);
}

int32_t C::get_computed() const {
  core::Console::log(temper_globals::console.value, TEMPER_STR("computing computed"));
  return 1;
}

int32_t C::get_backed() const {
  return this->backed;
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::console.value = core::Console::get_console();
    core::Console::log(temper_globals::console.value, TEMPER_STR("about to create instance"));
    std::shared_ptr<C> c = C::make();
    core::Console::log(temper_globals::console.value, TEMPER_STR("about to get backed"));
    core::Console::log(temper_globals::console.value, "c.backed=" + std::to_string(c->backed));
    core::Console::log(temper_globals::console.value, TEMPER_STR("about to get computed"));
    core::Console::log(temper_globals::console.value, "c.computed=" + std::to_string(c->get_computed()));
    core::Console::log(temper_globals::console.value, TEMPER_STR("done"));
  });
}

}  // namespace property_order
