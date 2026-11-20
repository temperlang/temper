// init.hpp
#pragma once

#include <temper-core/any_value.hpp>
#include <temper-core/str.hpp>
#include <cstdint>
#include <memory>

namespace call_override_from_subtype {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct Slappy;
struct Slippy;
struct A;
struct B;
struct C;
struct D;
struct ASplit;
struct BSplit;
struct CSplit;
struct DSplit;
struct ADeep;
struct B1Deep;
struct B2Deep;
struct CDeep;
struct DDeep;
struct EDeep;

struct Slappy : public virtual temper::core::AnyValueBase {
  virtual int32_t get_thingums() const = 0;
};

struct Slippy : public virtual Slappy {
  static std::shared_ptr<Slippy> make();
  bool checkOverridden() const;
  bool checkOwn() const;
  int32_t get_thingums() const override;
  int32_t get_thingumses() const;
};

struct A : public virtual temper::core::AnyValueBase {
  virtual temper::core::Str a() const;
  virtual temper::core::Str get_thing() const;
};

struct B : public virtual A {};

struct C : public virtual A {
  temper::core::Str a() const override;
  temper::core::Str get_thing() const override;
};

struct D : public virtual B, public virtual C {
  static std::shared_ptr<D> make();
};

struct ASplit : public virtual temper::core::AnyValueBase {
  virtual temper::core::Str a() const;
};

struct BSplit : public virtual ASplit {};

struct CSplit : public virtual temper::core::AnyValueBase {
  virtual temper::core::Str a() const;
};

struct DSplit : public virtual BSplit, public virtual CSplit {
  static std::shared_ptr<DSplit> make();
  temper::core::Str a() const override;
};

struct ADeep : public virtual temper::core::AnyValueBase {
  virtual temper::core::Str a() const;
};

struct B1Deep : public virtual ADeep {
  temper::core::Str a() const override;
};

struct B2Deep : public virtual B1Deep {};

struct CDeep : public virtual ADeep {};

struct DDeep : public virtual B2Deep, public virtual CDeep {
  static std::shared_ptr<DDeep> make();
};

struct EDeep : public virtual CDeep, public virtual B2Deep {
  static std::shared_ptr<EDeep> make();
  temper::core::Str a() const override;
};

}  // namespace call_override_from_subtype

// init.cpp
#include <call-override-from-subtype/init.hpp>

#include <temper-core/module_init.hpp>
#include <temper-core/casting.hpp>
#include <temper-core/boolean.hpp>
#include <temper-core/console.hpp>
#include <string>

namespace call_override_from_subtype {

namespace core = temper::core;

std::shared_ptr<Slippy> Slippy::make() {
  std::shared_ptr<Slippy> self = std::make_shared<Slippy>();
  return self;
}

bool Slippy::checkOverridden() const {
  std::shared_ptr<Slippy const> self = std::shared_ptr<Slippy const>(shared_from_this(), this);
  return self->get_thingums() == 1;
}

bool Slippy::checkOwn() const {
  std::shared_ptr<Slippy const> self = std::shared_ptr<Slippy const>(shared_from_this(), this);
  return self->get_thingumses() == 2;
}

int32_t Slippy::get_thingums() const {
  return 3;
}

int32_t Slippy::get_thingumses() const {
  return 4;
}

core::Str A::a() const {
  return TEMPER_STR("A wins!");
}

core::Str A::get_thing() const {
  return TEMPER_STR("A has thing!");
}

core::Str C::a() const {
  return TEMPER_STR("C wins!");
}

core::Str C::get_thing() const {
  return TEMPER_STR("C has thing!");
}

std::shared_ptr<D> D::make() {
  std::shared_ptr<D> self = std::make_shared<D>();
  return self;
}

core::Str ASplit::a() const {
  return TEMPER_STR("A wins!");
}

core::Str CSplit::a() const {
  return TEMPER_STR("C wins!");
}

std::shared_ptr<DSplit> DSplit::make() {
  std::shared_ptr<DSplit> self = std::make_shared<DSplit>();
  return self;
}

core::Str DSplit::a() const {
  return CSplit::a();
}

core::Str ADeep::a() const {
  return TEMPER_STR("A wins!");
}

core::Str B1Deep::a() const {
  return TEMPER_STR("B1 wins!");
}

std::shared_ptr<DDeep> DDeep::make() {
  std::shared_ptr<DDeep> self = std::make_shared<DDeep>();
  return self;
}

std::shared_ptr<EDeep> EDeep::make() {
  std::shared_ptr<EDeep> self = std::make_shared<EDeep>();
  return self;
}

core::Str EDeep::a() const {
  return ADeep::a();
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    std::shared_ptr<Slippy> slippy = Slippy::make();
    std::shared_ptr<Slappy> slappy = core::to_base<Slappy>(slippy);
    core::Console::log(console, std::to_string(slippy->get_thingums()));
    core::Console::log(console, std::to_string(slappy->get_thingums()));
    core::Console::log(console, std::to_string(slippy->get_thingumses()));
    core::Console::log(console, core::Boolean::toString(slippy->checkOverridden()));
    core::Console::log(console, core::Boolean::toString(slippy->checkOwn()));
    core::Console::log(console, D::make()->a());
    core::Console::log(console, D::make()->get_thing());
    core::Console::log(console, DSplit::make()->a());
    core::Console::log(console, DDeep::make()->a());
    core::Console::log(console, EDeep::make()->a());
  });
}

}  // namespace call_override_from_subtype
