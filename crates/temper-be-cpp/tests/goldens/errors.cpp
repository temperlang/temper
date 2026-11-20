// init.hpp
#pragma once

#include <temper-core/base.hpp>

namespace errors {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct Box;

struct Box : public std::enable_shared_from_this<Box> {
  int32_t value;
  static std::shared_ptr<Box> make(int32_t value);
  int32_t get() const;
  temper::core::Str bad() const;
  int32_t get_value() const;
};

int32_t good(int32_t x);

}  // namespace errors

// init.cpp
#include <errors/init.hpp>

#include <temper-core/int.hpp>
#include <temper-core/console.hpp>

namespace errors {

namespace core = temper::core;

int32_t good(int32_t x) {
  global_init_init();
  return core::Int::add(x, 1);
}

static int32_t mismatched(int32_t) {
  core::bubble();
}

static int32_t undeclared() {
  core::bubble();
}

static int32_t misspelled(core::Str const &) {
  core::bubble();
}

static int32_t misspelledMethod(core::Str const &) {
  core::bubble();
}

static int32_t wrongArity() {
  core::bubble();
}

static int32_t unknownType(std::shared_ptr<core::AnyValueBase> const &) {
  return 1;
}

static int32_t afterUnclosed(int32_t x) {
  return core::Int::mul(x, 3);
}

static core::Str dropped(int32_t) {
  core::bubble();
}

static core::Str stored(int32_t) {
  core::bubble();
}

static int32_t returned() {
  core::bubble();
}

std::shared_ptr<Box> Box::make(int32_t value) {
  std::shared_ptr<Box> self = std::make_shared<Box>();
  self->value = value;
  return self;
}

int32_t Box::get() const {
  return this->value;
}

core::Str Box::bad() const {
  core::bubble();
}

int32_t Box::get_value() const {
  return this->value;
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    int32_t a;
    try {
      a = mismatched(1);
    } catch (core::TemperBubble const &) {
      a = -1;
    }
    int32_t b;
    try {
      b = undeclared();
    } catch (core::TemperBubble const &) {
      b = -1;
    }
    int32_t c;
    try {
      c = errors::misspelled(TEMPER_STR("a"));
    } catch (core::TemperBubble const &) {
      c = -1;
    }
    int32_t d;
    try {
      d = errors::misspelledMethod(TEMPER_STR("a"));
    } catch (core::TemperBubble const &) {
      d = -1;
    }
    int32_t e;
    try {
      e = wrongArity();
    } catch (core::TemperBubble const &) {
      e = -1;
    }
    std::shared_ptr<Box> box = Box::make(3);
    core::Str f;
    try {
      f = box->bad();
    } catch (core::TemperBubble const &) {
      f = TEMPER_STR("caught");
    }
    core::Console::log(console, std::to_string(good(1)) + " " + std::to_string(a) + " " + std::to_string(b) + " " + std::to_string(c) + " " + std::to_string(d) + " " + std::to_string(e));
    core::Console::log(console, std::to_string(errors::unknownType(core::any_box(1))) + " " + std::to_string(afterUnclosed(2)) + " " + std::to_string(box->get()) + " " + f);
    core::Str g;
    try {
      g = stored(1);
    } catch (core::TemperBubble const &) {
      g = TEMPER_STR("caught");
    }
    int32_t h;
    try {
      h = returned();
    } catch (core::TemperBubble const &) {
      h = -1;
    }
    int32_t i;
    try {
      core::bubble();
    } catch (core::TemperBubble const &) {
      i = -1;
    }
    core::Str j;
    try {
      j = dropped(1);
    } catch (core::TemperBubble const &) {
      j = TEMPER_STR("dropped");
    }
    core::Console::log(console, g + " " + std::to_string(h) + " " + std::to_string(i) + " " + j);
  });
}

}  // namespace errors
