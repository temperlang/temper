// init.hpp
#pragma once

#include <temper-core/base.hpp>

namespace specific {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct AlphaInterface;
struct BravoClass;
struct CharlieClass;

struct AlphaInterface : public virtual temper::core::AnyValueBase {};

struct BravoClass : public virtual AlphaInterface {
  static std::shared_ptr<BravoClass> make();
};

struct CharlieClass : public virtual AlphaInterface {
  static std::shared_ptr<CharlieClass> make();
};

}  // namespace specific

// init.cpp
#include <specific/init.hpp>

#include <temper-core/casting.hpp>
#include <temper-core/console.hpp>

namespace specific {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;

}  // namespace temper_globals

std::shared_ptr<BravoClass> BravoClass::make() {
  std::shared_ptr<BravoClass> self = std::make_shared<BravoClass>();
  return self;
}

std::shared_ptr<CharlieClass> CharlieClass::make() {
  std::shared_ptr<CharlieClass> self = std::make_shared<CharlieClass>();
  return self;
}

static void checkB(std::shared_ptr<AlphaInterface> const & instance) {
  core::Str value;
  try {
    core::checked_cast<BravoClass>(instance);
    value = TEMPER_STR("yep");
  } catch (core::TemperBubble const &) {
    value = TEMPER_STR("nope");
  }
  core::Console::log(temper_globals::console.value, value);
}

static void checkIfB(std::shared_ptr<AlphaInterface> const & instance) {
  core::Console::log(temper_globals::console.value, dynamic_cast<BravoClass const *>(instance.get()) != nullptr ? TEMPER_STR("yep") : TEMPER_STR("nope"));
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::console.value = core::Console::get_console();
    specific::checkB(core::to_base<AlphaInterface>(BravoClass::make()));
    specific::checkB(core::to_base<AlphaInterface>(CharlieClass::make()));
    std::shared_ptr<std::vector<std::shared_ptr<AlphaInterface>>> items = core::List::make<std::shared_ptr<AlphaInterface>>(core::to_base<AlphaInterface>(BravoClass::make()), core::to_base<AlphaInterface>(CharlieClass::make()));
    specific::checkB(core::List::get<std::shared_ptr<AlphaInterface>>(items, 0));
    specific::checkB(core::List::get<std::shared_ptr<AlphaInterface>>(items, 1));
    specific::checkIfB(core::List::get<std::shared_ptr<AlphaInterface>>(items, 0));
    specific::checkIfB(core::List::get<std::shared_ptr<AlphaInterface>>(items, 1));
  });
}

}  // namespace specific
