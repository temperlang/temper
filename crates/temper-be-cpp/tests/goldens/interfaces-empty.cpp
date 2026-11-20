// init.hpp
#pragma once

#include <temper-core/base.hpp>

namespace empty {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct Greeter;
struct HiGreeter;

struct Greeter : public virtual temper::core::AnyValueBase {};

struct HiGreeter : public virtual Greeter {
  static std::shared_ptr<HiGreeter> make();
  void greet() const;
};

}  // namespace empty

// init.cpp
#include <empty/init.hpp>

#include <temper-core/casting.hpp>
#include <temper-core/console.hpp>

namespace empty {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;

}  // namespace temper_globals

std::shared_ptr<HiGreeter> HiGreeter::make() {
  std::shared_ptr<HiGreeter> self = std::make_shared<HiGreeter>();
  return self;
}

void HiGreeter::greet() const {
  core::Console::log(temper_globals::console.value, TEMPER_STR("Hi!"));
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::console.value = core::Console::get_console();
    std::shared_ptr<Greeter> greeter = core::to_base<Greeter>(HiGreeter::make());
    core::checked_cast<HiGreeter>(greeter)->greet();
  });
}

}  // namespace empty
