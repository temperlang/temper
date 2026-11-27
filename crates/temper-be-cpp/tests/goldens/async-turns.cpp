// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace async_turns {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct Tick;
struct Counter;
struct HalveError;
struct Odd;

struct Tick : public virtual temper::core::AnyValueBase {
  virtual void apply(int32_t i) const = 0;
};

struct Counter : public std::enable_shared_from_this<Counter> {
  int32_t n;
  static std::shared_ptr<Counter> make();
  void bump(temper::core::Str const & tag, int32_t times);
  int32_t get_n();
};

struct HalveError : public virtual temper::core::Failure {};

struct Odd : public virtual HalveError {
  int32_t n;
  static std::shared_ptr<Odd> make(int32_t n);
  temper::core::Str get_message() const override;
  int32_t get_n();
};

std::shared_ptr<temper::core::Promise<int32_t>> later(int32_t n);
std::shared_ptr<temper::core::SafeGenerator<std::shared_ptr<temper::core::Empty>>> ticks(int32_t n, std::function<void(int32_t)> const & each);
std::shared_ptr<temper::core::SafeGenerator<int32_t>> fibonacci();
std::shared_ptr<temper::core::SafeGenerator<int32_t>> collatz(int32_t start);
temper::core::Expected<int32_t, std::shared_ptr<Odd>> halve(int32_t n);

}  // namespace async_turns

// init.cpp
#include <async-turns/init.hpp>

namespace async_turns {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;
static core::GlobalSlot<std::shared_ptr<Counter>> c;
static core::GlobalSlot<std::shared_ptr<core::PromiseBuilder<int32_t>>> gateA;
static core::GlobalSlot<std::shared_ptr<core::PromiseBuilder<int32_t>>> gateB;

}  // namespace temper_globals

std::shared_ptr<core::Promise<int32_t>> later(int32_t n) {
  global_init_init();
  std::shared_ptr<core::PromiseBuilder<int32_t>> pb = core::PromiseBuilderNs::make<int32_t>();
  auto fn = [=]() -> std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> {
    std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
    auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>>) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
      int32_t caseIndexLocal = (*caseIndex);
      *caseIndex = -1;
      switch (caseIndexLocal) {
        case 0:
          core::complete(pb, core::Int::mul(n, 2));
          return core::doneResult();
        default:
          return core::doneResult();
      }
    };
    return core::safe_adapt_generator_fn<std::shared_ptr<core::Empty>>(convertedCoroutine);
  };
  core::async_run(fn);
  return core::getpromise(pb);
}

template <class T>
static std::shared_ptr<core::SafeGenerator<T>> generated(std::function<std::shared_ptr<core::SafeGenerator<T>>()> const & make_) {
  return make_();
}

std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> ticks(int32_t n, std::function<void(int32_t)> const & each) {
  global_init_init();
  auto fn = [=]() -> std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> {
    std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
    std::shared_ptr<int32_t> i = std::make_shared<int32_t>(0);
    auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>>) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
      while (true) {
        int32_t caseIndexLocal = (*caseIndex);
        *caseIndex = -1;
        switch (caseIndexLocal) {
          case 0:
            *i = 0;
            *caseIndex = 1;
            break;
          case 1:
            if ((*i) < n) {
              *caseIndex = 2;
            } else {
              *caseIndex = -1;
            }
            break;
          case 2:
            each((*i));
            *caseIndex = 3;
            return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
          case 3:
            *i = core::Int::add((*i), 1);
            *caseIndex = 1;
            break;
          default:
            return core::doneResult();
        }
      }
    };
    return core::safe_adapt_generator_fn<std::shared_ptr<core::Empty>>(convertedCoroutine);
  };
  return async_turns::generated<std::shared_ptr<core::Empty>>(fn);
}

template <class T>
static std::shared_ptr<core::SafeGenerator<T>> startedAt(int32_t start, std::function<std::shared_ptr<core::SafeGenerator<T>>(int32_t)> const & make_) {
  return make_(start);
}

std::shared_ptr<core::SafeGenerator<int32_t>> fibonacci() {
  global_init_init();
  auto fn = []() -> std::shared_ptr<core::SafeGenerator<int32_t>> {
    std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
    std::shared_ptr<int32_t> a = std::make_shared<int32_t>(0);
    std::shared_ptr<int32_t> b = std::make_shared<int32_t>(0);
    std::shared_ptr<int32_t> next = std::make_shared<int32_t>(0);
    auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<int32_t>>) -> std::shared_ptr<core::GeneratorResult<int32_t>> {
      while (true) {
        int32_t caseIndexLocal = (*caseIndex);
        *caseIndex = -1;
        switch (caseIndexLocal) {
          case 0:
            *a = 0;
            *b = 1;
            *caseIndex = 1;
            break;
          case 1:
            *caseIndex = 2;
            return core::ValueResult<int32_t>::make((*a));
          case 2:
            *next = core::Int::add((*a), (*b));
            *a = (*b);
            *b = (*next);
            *caseIndex = 1;
            break;
          default:
            return core::doneResult();
        }
      }
    };
    return core::safe_adapt_generator_fn<int32_t>(convertedCoroutine);
  };
  return async_turns::generated<int32_t>(fn);
}

std::shared_ptr<core::SafeGenerator<int32_t>> collatz(int32_t start) {
  global_init_init();
  auto fn = [](int32_t first) -> std::shared_ptr<core::SafeGenerator<int32_t>> {
    std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
    std::shared_ptr<int32_t> n = std::make_shared<int32_t>(0);
    auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<int32_t>>) -> std::shared_ptr<core::GeneratorResult<int32_t>> {
      while (true) {
        int32_t caseIndexLocal = (*caseIndex);
        *caseIndex = -1;
        switch (caseIndexLocal) {
          case 0:
            *n = first;
            *caseIndex = 1;
            break;
          case 1:
            *caseIndex = 2;
            return core::ValueResult<int32_t>::make((*n));
          case 2:
            if ((*n) == 1) {
              {
                *caseIndex = -1;
                continue;
              }
            }
            if ((*n) % 2 == 0) {
              *n = (*n) / 2;
            } else {
              *n = core::Int::add(core::Int::mul(3, (*n)), 1);
            }
            *caseIndex = 1;
            break;
          default:
            return core::doneResult();
        }
      }
    };
    return core::safe_adapt_generator_fn<int32_t>(convertedCoroutine);
  };
  return async_turns::startedAt<int32_t>(start, fn);
}

static std::shared_ptr<std::vector<int32_t>> take(std::shared_ptr<core::SafeGenerator<int32_t>> const & g, int32_t n) {
  std::shared_ptr<std::vector<int32_t>> taken = core::ListBuilder::make<int32_t>();
  while (core::List::length<int32_t>(taken) < n) {
    std::shared_ptr<core::GeneratorResult<int32_t>> r = core::next(g);
    if (!r->is_done()) {
      core::ListBuilder::add<int32_t>(taken, r->value());
    } else {
      break;
    }
  }
  return core::List::toList<int32_t>(taken);
}

static core::Str shown(std::shared_ptr<std::vector<int32_t>> const & ints) {
  return core::List::join(ints, TEMPER_STR(" "), [](int32_t i) -> core::Str { return std::to_string(i); });
}

std::shared_ptr<Counter> Counter::make() {
  std::shared_ptr<Counter> self = std::make_shared<Counter>();
  self->n = 0;
  return self;
}

void Counter::bump(core::Str const & tag, int32_t times) {
  core::Console::log(temper_globals::console.value, tag + " start");
  {
    int32_t i = 0;
    while (i < times) {
      int32_t before = this->n;
      this->n = core::Int::add(before, 1);
      i = core::Int::add(i, 1);
    }
  }
  core::Console::log(temper_globals::console.value, tag + " done n=" + std::to_string(this->n));
}

int32_t Counter::get_n() {
  return this->n;
}

std::shared_ptr<Odd> Odd::make(int32_t n) {
  global_init_init();
  std::shared_ptr<Odd> self = std::make_shared<Odd>();
  self->n = n;
  return self;
}

core::Str Odd::get_message() const {
  return "odd (n: " + std::to_string(this->n) + ")";
}

int32_t Odd::get_n() {
  return this->n;
}

core::Expected<int32_t, std::shared_ptr<Odd>> halve(int32_t n) {
  global_init_init();
  if (n % 2 != 0) {
    return core::unexpected(Odd::make(n));
  }
  return n / 2;
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::c.construct();
    temper_globals::gateA.construct();
    temper_globals::gateB.construct();
    temper_globals::console.value = core::Console::get_console();
    std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> t = async_turns::ticks(2, [](int32_t i) -> void { core::Console::log(temper_globals::console.value, "tick " + std::to_string(i)); });
    core::next(t);
    core::next(t);
    core::next(t);
    core::Console::log(temper_globals::console.value, "fibonacci " + async_turns::shown(async_turns::take(fibonacci(), 10)));
    core::Console::log(temper_globals::console.value, "collatz " + async_turns::shown(async_turns::take(collatz(6), 100)));
    std::shared_ptr<core::SafeGenerator<int32_t>> fib = fibonacci();
    async_turns::take(fib, 5);
    core::Console::log(temper_globals::console.value, "then " + async_turns::shown(async_turns::take(fib, 3)));
    temper_globals::c.value = Counter::make();
    temper_globals::gateA.value = core::PromiseBuilderNs::make<int32_t>();
    temper_globals::gateB.value = core::PromiseBuilderNs::make<int32_t>();
    auto fn2 = []() -> std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> {
      std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
      std::shared_ptr<int32_t> k = std::make_shared<int32_t>(0);
      std::shared_ptr<std::shared_ptr<core::Promise<int32_t>>> awaited = std::make_shared<std::shared_ptr<core::Promise<int32_t>>>(nullptr);
      auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> generator) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
        while (true) {
          int32_t caseIndexLocal = (*caseIndex);
          *caseIndex = -1;
          switch (caseIndexLocal) {
            case 0:
              temper_globals::c.value->bump(TEMPER_STR("A"), 10000);
              *caseIndex = 3;
              break;
            case 1:
              *k = -1;
              *caseIndex = 2;
              break;
            case 2:
              temper_globals::c.value->bump("A" + std::to_string((*k)), 10000);
              return core::doneResult();
            case 3:
              *awaited = core::getpromise(temper_globals::gateA.value);
              *caseIndex = 4;
              core::awake_upon((*awaited), generator);
              return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
            case 4: {
              auto Result = core::get_promise_result_sync((*awaited));
              if (Result.has_value()) {
                *k = *Result;
                *caseIndex = 2;
              } else {
                *caseIndex = 1;
              }
              break;
            }
            default:
              return core::doneResult();
          }
        }
      };
      return core::safe_adapt_generator_fn<std::shared_ptr<core::Empty>>(convertedCoroutine);
    };
    core::async_run(fn2);
    auto fn3 = []() -> std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> {
      std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
      std::shared_ptr<int32_t> k = std::make_shared<int32_t>(0);
      std::shared_ptr<std::shared_ptr<core::Promise<int32_t>>> awaited = std::make_shared<std::shared_ptr<core::Promise<int32_t>>>(nullptr);
      auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> generator) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
        while (true) {
          int32_t caseIndexLocal = (*caseIndex);
          *caseIndex = -1;
          switch (caseIndexLocal) {
            case 0:
              temper_globals::c.value->bump(TEMPER_STR("B"), 10000);
              *caseIndex = 3;
              break;
            case 1:
              *k = -1;
              *caseIndex = 2;
              break;
            case 2:
              temper_globals::c.value->bump("B" + std::to_string((*k)), 10000);
              return core::doneResult();
            case 3:
              *awaited = core::getpromise(temper_globals::gateB.value);
              *caseIndex = 4;
              core::awake_upon((*awaited), generator);
              return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
            case 4: {
              auto Result2 = core::get_promise_result_sync((*awaited));
              if (Result2.has_value()) {
                *k = *Result2;
                *caseIndex = 2;
              } else {
                *caseIndex = 1;
              }
              break;
            }
            default:
              return core::doneResult();
          }
        }
      };
      return core::safe_adapt_generator_fn<std::shared_ptr<core::Empty>>(convertedCoroutine);
    };
    core::async_run(fn3);
    auto fn4 = []() -> std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> {
      std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
      auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>>) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
        int32_t caseIndexLocal = (*caseIndex);
        *caseIndex = -1;
        switch (caseIndexLocal) {
          case 0:
            core::Console::log(temper_globals::console.value, TEMPER_STR("opener: calling complete"));
            core::complete(temper_globals::gateA.value, 1);
            core::complete(temper_globals::gateB.value, 2);
            core::Console::log(temper_globals::console.value, TEMPER_STR("opener: complete returned"));
            return core::doneResult();
          default:
            return core::doneResult();
        }
      };
      return core::safe_adapt_generator_fn<std::shared_ptr<core::Empty>>(convertedCoroutine);
    };
    core::async_run(fn4);
  });
}

}  // namespace async_turns
