// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace panic_causes_panic_passing_orelse_panic {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct Problem;
struct NotPositive;

struct Problem : public virtual temper::core::Failure {};

struct NotPositive : public virtual Problem {
  int32_t n;
  static std::shared_ptr<NotPositive> make(int32_t n);
  temper::core::Str get_message() const override;
  int32_t get_n() const;
};

}  // namespace panic_causes_panic_passing_orelse_panic

// init.cpp
#include <panic-causes-panic-passing-orelse-panic/init.hpp>

namespace panic_causes_panic_passing_orelse_panic {

namespace core = temper::core;

std::shared_ptr<NotPositive> NotPositive::make(int32_t n) {
  std::shared_ptr<NotPositive> self = std::make_shared<NotPositive>();
  self->n = n;
  return self;
}

core::Str NotPositive::get_message() const {
  return "not positive (n: " + std::to_string(this->n) + ")";
}

int32_t NotPositive::get_n() const {
  return this->n;
}

static core::Expected<int32_t, std::shared_ptr<NotPositive>> positive(int32_t n) {
  if (n <= 0) {
    return core::unexpected(NotPositive::make(n));
  }
  return n;
}

static core::Expected<int32_t, std::shared_ptr<NotPositive>> read(std::shared_ptr<std::vector<int32_t>> const & ls, int32_t i) {
  auto positiveResult = positive(core::Int::add(i, 1));
  if (!positiveResult.has_value()) {
    return core::unexpected(positiveResult.error());
  }
  return core::List::get<int32_t>(ls, i);
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    core::Console::log(console, TEMPER_STR("before"));
    int32_t r;
    auto readResult = panic_causes_panic_passing_orelse_panic::read(core::List::make<int32_t>(1, 2), 5);
    if (readResult.has_value()) {
      r = *readResult;
    } else {
      core::panic_caused_by(readResult.error());
    }
    core::Console::log(console, "after " + std::to_string(r));
  });
}

}  // namespace panic_causes_panic_passing_orelse_panic
