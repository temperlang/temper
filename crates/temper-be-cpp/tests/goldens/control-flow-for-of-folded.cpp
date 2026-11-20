// init.hpp
#pragma once

#include <temper-core/base.hpp>

namespace for_of_folded {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace for_of_folded

// init.cpp
#include <for-of-folded/init.hpp>

#include <temper-core/int.hpp>
#include <temper-core/console.hpp>

namespace for_of_folded {

namespace core = temper::core;

static int32_t total(std::shared_ptr<std::vector<int32_t>> const & xs) {
  int32_t t = 0;
  {
    int32_t length = core::List::length<int32_t>(xs);
    int32_t i = 0;
    while (i < length) {
      int32_t x = core::List::get<int32_t>(xs, i);
      i = core::Int::add(i, 1);
      t = core::Int::add(t, x);
    }
  }
  return t;
}

static int32_t totalForEach(std::shared_ptr<std::vector<int32_t>> const & xs) {
  int32_t t = 0;
  {
    int32_t length = core::List::length<int32_t>(xs);
    int32_t i = 0;
    while (i < length) {
      int32_t x = core::List::get<int32_t>(xs, i);
      i = core::Int::add(i, 1);
      t = core::Int::add(t, x);
    }
  }
  return t;
}

static int32_t totalFiltered(std::shared_ptr<std::vector<int32_t>> const & xs) {
  std::shared_ptr<int32_t> t = std::make_shared<int32_t>(0);
  auto fn = [=](int32_t x) -> bool {
    *t = core::Int::add((*t), x);
    return true;
  };
  core::List::filter(xs, fn);
  return (*t);
}

static int32_t totalNested(std::shared_ptr<std::vector<std::shared_ptr<std::vector<int32_t>>>> const & xss) {
  int32_t t = 0;
  {
    int32_t length = core::List::length<std::shared_ptr<std::vector<int32_t>>>(xss);
    int32_t i = 0;
    while (i < length) {
      std::shared_ptr<std::vector<int32_t>> xs = core::List::get<std::shared_ptr<std::vector<int32_t>>>(xss, i);
      i = core::Int::add(i, 1);
      {
        int32_t length2 = core::List::length<int32_t>(xs);
        int32_t i2 = 0;
        while (i2 < length2) {
          int32_t x = core::List::get<int32_t>(xs, i2);
          i2 = core::Int::add(i2, 1);
          t = core::Int::add(t, x);
        }
      }
    }
  }
  return t;
}

static int32_t firstOver(std::shared_ptr<std::vector<int32_t>> const & xs, int32_t n) {
  {
    int32_t length = core::List::length<int32_t>(xs);
    int32_t i = 0;
    while (i < length) {
      int32_t x = core::List::get<int32_t>(xs, i);
      i = core::Int::add(i, 1);
      if (x > n) {
        return x;
      }
    }
  }
  return -1;
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    core::Console::log(console, "total " + std::to_string(for_of_folded::total(core::List::make<int32_t>(1, 2, 3))));
    core::Console::log(console, "forEach " + std::to_string(for_of_folded::totalForEach(core::List::make<int32_t>(1, 2, 3))));
    core::Console::log(console, "filter " + std::to_string(for_of_folded::totalFiltered(core::List::make<int32_t>(1, 2, 3))));
    core::Console::log(console, "nested " + std::to_string(for_of_folded::totalNested(core::List::make<std::shared_ptr<std::vector<int32_t>>>(core::List::make<int32_t>(1, 2), core::List::make<int32_t>(3), core::List::make<int32_t>()))));
    core::Console::log(console, "firstOver " + std::to_string(for_of_folded::firstOver(core::List::make<int32_t>(1, 2, 3), 1)) + " " + std::to_string(for_of_folded::firstOver(core::List::make<int32_t>(1, 2, 3), 5)));
  });
}

}  // namespace for_of_folded
