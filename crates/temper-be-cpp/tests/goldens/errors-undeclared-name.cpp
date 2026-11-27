// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace errors_undeclared_name {

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

}  // namespace errors_undeclared_name

// init.cpp
#include <errors-undeclared-name/init.hpp>

namespace errors_undeclared_name {

namespace core = temper::core;

int32_t good(int32_t x) {
  global_init_init();
  return core::Int::add(x, 1);
}

static int32_t undeclared() {
  core::panic();
}

static int32_t unknownType(std::shared_ptr<core::AnyValueBase> const &) {
  return 1;
}

static int32_t afterUnclosed(int32_t x) {
  return core::Int::mul(x, 3);
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
  core::panic();
}

int32_t Box::get_value() const {
  return this->value;
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    std::shared_ptr<Box> box = Box::make(3);
    core::Console::log(console, std::to_string(good(1)) + " " + std::to_string(errors_undeclared_name::unknownType(core::any_box(1))) + " " + std::to_string(afterUnclosed(2)) + " " + std::to_string(box->get()));
    undeclared();
    core::Console::log(console, TEMPER_STR("after"));
  });
}

}  // namespace errors_undeclared_name
