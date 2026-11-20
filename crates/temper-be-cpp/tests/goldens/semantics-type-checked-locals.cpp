// init.hpp
#pragma once

namespace type_checked_locals {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace type_checked_locals

// init.cpp
#include <type-checked-locals/init.hpp>

#include <temper-core/temper_bubble.hpp>
#include <temper-core/str.hpp>
#include <temper-core/base_types.hpp>
#include <temper-core/module_init.hpp>
#include <temper-core/console.hpp>
#include <cstdint>
#include <memory>

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

static int32_t f(int32_t x) {
  return x;
}

static int32_t g(int32_t x) {
  return x;
}

static int32_t i(int32_t x) {
  return x;
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::console.value = core::Console::get_console();
    core::Console::log(temper_globals::console.value, TEMPER_STR("Checkpoint 0"));
    try {
      int32_t a;
      static_cast<void>(a);
      core::bubble();
    } catch (core::TemperBubble const &) {
      yay();
    }
    core::Console::log(temper_globals::console.value, TEMPER_STR("Checkpoint 1"));
    int32_t a2 = 1;
    static_cast<void>(a2);
    yay();
    core::Console::log(temper_globals::console.value, TEMPER_STR("Checkpoint 2"));
    {
      int32_t a3 = 1;
      try {
        core::bubble();
      } catch (core::TemperBubble const &) {
        meh();
      }
      if (a3 == 1) {
        meh();
      } else {
        meh();
      }
    }
    core::Console::log(temper_globals::console.value, TEMPER_STR("Checkpoint 3"));
    int32_t a4 = 1;
    static_cast<void>(a4);
    a4 = 2;
    yay();
    core::Console::log(temper_globals::console.value, TEMPER_STR("Checkpoint 4"));
    try {
      f(0);
      yay();
    } catch (core::TemperBubble const &) {
      boo();
    }
    try {
      core::bubble();
    } catch (core::TemperBubble const &) {
      meh();
    }
    core::Console::log(temper_globals::console.value, TEMPER_STR("Checkpoint 5"));
    try {
      g(0);
      yay();
    } catch (core::TemperBubble const &) {
      boo();
    }
    try {
      core::bubble();
    } catch (core::TemperBubble const &) {
      meh();
    }
    core::Console::log(temper_globals::console.value, TEMPER_STR("Checkpoint 6"));
    try {
      i(0);
      yay();
    } catch (core::TemperBubble const &) {
      boo();
    }
    try {
      core::bubble();
    } catch (core::TemperBubble const &) {
      meh();
    }
  });
}

}  // namespace type_checked_locals
