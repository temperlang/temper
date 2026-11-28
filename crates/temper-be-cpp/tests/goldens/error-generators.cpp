// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace error_generators {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct ErrorGeneratorsError;
struct TooBig;

struct ErrorGeneratorsError : public virtual temper::core::Failure {};

struct TooBig : public virtual ErrorGeneratorsError {
  int32_t n;
  static std::shared_ptr<TooBig> make(int32_t n);
  temper::core::Str get_message() const override;
  int32_t get_n() const;
};

}  // namespace error_generators

// init.cpp
#include <error-generators/init.hpp>

namespace error_generators {

namespace core = temper::core;

std::shared_ptr<TooBig> TooBig::make(int32_t n) {
  std::shared_ptr<TooBig> self = std::make_shared<TooBig>();
  self->n = n;
  return self;
}

core::Str TooBig::get_message() const {
  return "too big (n: " + std::to_string(this->n) + ")";
}

int32_t TooBig::get_n() const {
  return this->n;
}

template <class T>
static std::shared_ptr<core::Generator<T>> generated(std::function<std::shared_ptr<core::Generator<T>>()> const & make_) {
  return make_();
}

template <class T>
static std::shared_ptr<core::SafeGenerator<T>> safely(std::function<std::shared_ptr<core::SafeGenerator<T>>()> const & make_) {
  return make_();
}

static core::Str why(std::shared_ptr<core::Failure> const & e) {
  core::Str mine = dynamic_cast<TooBig const *>(e.get()) != nullptr ? core::Str("too big " + std::to_string(core::checked_cast<TooBig>(e)->n)) : e->get_message();
  std::shared_ptr<core::Failure> cause = e->get_cause();
  if (cause == nullptr) {
    return mine;
  } else {
    return mine + ": " + error_generators::why(cause);
  }
}

static core::Str take(std::shared_ptr<core::Generator<int32_t>> const & g, int32_t n) {
  std::shared_ptr<std::vector<core::Str>> taken = core::ListBuilder::make<core::Str>();
  while (core::List::length<core::Str>(taken) < n) {
    std::shared_ptr<core::GeneratorResult<int32_t>> r;
    bool failed = false;
    std::shared_ptr<core::BrokenGenerator> caught = nullptr;
    std::shared_ptr<core::GeneratorResult<int32_t>> held = nullptr;
    auto Result = core::next(g);
    if (Result.has_value()) {
      held = *Result;
    } else {
      std::shared_ptr<core::BrokenGenerator> failure = Result.error();
      caught = failure;
      failed = true;
    }
    if (failed) {
      std::shared_ptr<core::BrokenGenerator> e = caught;
      core::Str join = core::List::join(taken, TEMPER_STR(" "), [](core::Str s) -> core::Str { return s; });
      return join + " " + error_generators::why(core::to_base<core::Failure>(e));
    } else {
      r = held;
    }
    if (!r->is_done()) {
      core::ListBuilder::add<core::Str>(taken, std::to_string(r->value()));
    } else {
      core::ListBuilder::add<core::Str>(taken, TEMPER_STR("done"));
      break;
    }
  }
  return core::List::join(taken, TEMPER_STR(" "), [](core::Str s2) -> core::Str { return s2; });
}

static std::shared_ptr<core::Generator<int32_t>> upTo(int32_t limit) {
  auto fn = [=]() -> std::shared_ptr<core::Generator<int32_t>> {
    std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
    std::shared_ptr<int32_t> i = std::make_shared<int32_t>(0);
    auto convertedCoroutine = [=](std::shared_ptr<core::Generator<int32_t>>) -> core::Expected<std::shared_ptr<core::GeneratorResult<int32_t>>, std::shared_ptr<core::Failure>> {
      while (true) {
        int32_t caseIndexLocal = (*caseIndex);
        *caseIndex = -1;
        switch (caseIndexLocal) {
          case 0:
            *i = 1;
            *caseIndex = 1;
            break;
          case 1:
            if ((*i) > limit) {
              return core::unexpected(TooBig::make((*i)));
            }
            *caseIndex = 2;
            return core::ValueResult<int32_t>::make((*i));
          case 2:
            *i = core::Int::add((*i), 1);
            *caseIndex = 1;
            break;
          default:
            return core::doneResult();
        }
      }
    };
    return core::adapt_generator_fn<int32_t>(convertedCoroutine);
  };
  return error_generators::generated<int32_t>(fn);
}

static core::Expected<int32_t, std::shared_ptr<TooBig>> checked(int32_t n, int32_t limit) {
  if (n > limit) {
    return core::unexpected(TooBig::make(n));
  }
  return n;
}

static std::shared_ptr<core::Generator<int32_t>> squares(int32_t limit) {
  auto fn = [=]() -> std::shared_ptr<core::Generator<int32_t>> {
    std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
    std::shared_ptr<int32_t> i = std::make_shared<int32_t>(0);
    auto convertedCoroutine = [=](std::shared_ptr<core::Generator<int32_t>>) -> core::Expected<std::shared_ptr<core::GeneratorResult<int32_t>>, std::shared_ptr<core::Failure>> {
      while (true) {
        int32_t caseIndexLocal = (*caseIndex);
        *caseIndex = -1;
        switch (caseIndexLocal) {
          case 0: {
            *caseIndex = 1;
            int32_t value;
            auto checkedResult = checked(50, limit);
            if (checkedResult.has_value()) {
              value = *checkedResult;
            } else {
              value = 0;
            }
            return core::ValueResult<int32_t>::make(value);
          }
          case 1:
            *i = 1;
            *caseIndex = 2;
            break;
          case 2: {
            *caseIndex = 3;
            auto checkedResult2 = checked(core::Int::mul((*i), (*i)), limit);
            if (!checkedResult2.has_value()) {
              return core::unexpected(checkedResult2.error());
            }
            return core::ValueResult<int32_t>::make(*checkedResult2);
          }
          case 3:
            *i = core::Int::add((*i), 1);
            *caseIndex = 2;
            break;
          default:
            return core::doneResult();
        }
      }
    };
    return core::adapt_generator_fn<int32_t>(convertedCoroutine);
  };
  return error_generators::generated<int32_t>(fn);
}

static std::shared_ptr<core::SafeGenerator<int32_t>> count(int32_t n) {
  auto fn = [=]() -> std::shared_ptr<core::SafeGenerator<int32_t>> {
    std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
    std::shared_ptr<int32_t> i = std::make_shared<int32_t>(0);
    auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<int32_t>>) -> std::shared_ptr<core::GeneratorResult<int32_t>> {
      while (true) {
        int32_t caseIndexLocal = (*caseIndex);
        *caseIndex = -1;
        switch (caseIndexLocal) {
          case 0:
            *i = 1;
            *caseIndex = 1;
            break;
          case 1:
            if ((*i) <= n) {
              *caseIndex = 2;
            } else {
              *caseIndex = -1;
            }
            break;
          case 2:
            *caseIndex = 3;
            return core::ValueResult<int32_t>::make((*i));
          case 3:
            *i = core::Int::add((*i), 1);
            *caseIndex = 1;
            break;
          default:
            return core::doneResult();
        }
      }
    };
    return core::safe_adapt_generator_fn<int32_t>(convertedCoroutine);
  };
  return error_generators::safely<int32_t>(fn);
}

static core::Str shown(std::shared_ptr<core::GeneratorResult<int32_t>> const & r) {
  if (!r->is_done()) {
    return std::to_string(r->value());
  } else {
    return TEMPER_STR("done");
  }
}

static core::Str firstTwo(std::shared_ptr<core::SafeGenerator<int32_t>> const & g) {
  core::Str a = error_generators::shown(core::next(g));
  core::Str b;
  auto Result = core::next(core::generator_of(g));
  if (Result.has_value()) {
    b = error_generators::shown(*Result);
  } else {
    b = TEMPER_STR("failed");
  }
  return a + " " + b;
}

static std::shared_ptr<core::Generator<int32_t>> doubled(std::shared_ptr<core::Generator<int32_t>> const & inner) {
  auto fn = [=]() -> std::shared_ptr<core::Generator<int32_t>> {
    std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
    std::shared_ptr<std::shared_ptr<core::GeneratorResult<int32_t>>> r = std::make_shared<std::shared_ptr<core::GeneratorResult<int32_t>>>(nullptr);
    auto convertedCoroutine = [=](std::shared_ptr<core::Generator<int32_t>>) -> core::Expected<std::shared_ptr<core::GeneratorResult<int32_t>>, std::shared_ptr<core::Failure>> {
      while (true) {
        int32_t caseIndexLocal = (*caseIndex);
        *caseIndex = -1;
        switch (caseIndexLocal) {
          case 0:
            *caseIndex = 1;
            break;
          case 1: {
            auto Result = core::next(inner);
            if (!Result.has_value()) {
              return core::unexpected(Result.error());
            }
            *r = *Result;
            if (!(*r)->is_done()) {
              *caseIndex = 2;
            } else {
              *caseIndex = 3;
            }
            break;
          }
          case 2:
            *caseIndex = 1;
            return core::ValueResult<int32_t>::make(core::Int::mul((*r)->value(), 2));
          case 3:
            {
              {
                *caseIndex = -1;
                continue;
              }
            }
          default:
            return core::doneResult();
        }
      }
    };
    return core::adapt_generator_fn<int32_t>(convertedCoroutine);
  };
  return error_generators::generated<int32_t>(fn);
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    core::Console::log(console, error_generators::take(upTo(3), 9));
    std::shared_ptr<core::Generator<int32_t>> one = upTo(1);
    core::Console::log(console, error_generators::take(one, 9));
    core::Console::log(console, error_generators::take(one, 9));
    core::Console::log(console, error_generators::take(squares(10), 9));
    core::Console::log(console, error_generators::take(core::generator_of(count(2)), 9));
    core::Console::log(console, error_generators::firstTwo(count(1)));
    core::Console::log(console, error_generators::take(error_generators::doubled(upTo(2)), 9));
    core::Console::log(console, error_generators::take(error_generators::doubled(core::generator_of(count(2))), 9));
  });
}

}  // namespace error_generators
