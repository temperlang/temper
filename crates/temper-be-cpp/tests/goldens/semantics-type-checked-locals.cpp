// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace type_checked_locals {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct Problem;
struct Oops;

struct Problem : public virtual temper::core::Failure {};

struct Oops : public virtual Problem {
  static std::shared_ptr<Oops> make();
  temper::core::Str get_message() const override;
};

}  // namespace type_checked_locals

// init.cpp
#include <type-checked-locals/init.hpp>

namespace type_checked_locals {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;

}  // namespace temper_globals

static void yay() {
  core::Console::log(temper_globals::console.value, TEMPER_STR("yay"));
}

static void meh() {
  core::Console::log(temper_globals::console.value, TEMPER_STR("meh"));
}

static void boo() {
  core::Console::log(temper_globals::console.value, TEMPER_STR("boo"));
}

std::shared_ptr<Oops> Oops::make() {
  std::shared_ptr<Oops> self = std::make_shared<Oops>();
  return self;
}

core::Str Oops::get_message() const {
  return TEMPER_STR("oops");
}

static core::Expected<int32_t, std::shared_ptr<Oops>> f(int32_t x) {
  return x;
}

static core::Expected<int32_t, std::shared_ptr<Oops>> g(int32_t x) {
  return x;
}

static core::Expected<int32_t, std::shared_ptr<Oops>> i(int32_t x) {
  return x;
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::console.value = core::Console::get_console();
    core::Console::log(temper_globals::console.value, TEMPER_STR("Checkpoint 0"));
    int32_t a;
    static_cast<void>(a);
    core::panic();
  });
}

}  // namespace type_checked_locals
