// init.hpp
#pragma once

#include <temper-core/core.hpp>
#include <std/regex.hpp>

namespace error_params {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct ErrorParamsError;
struct Missing;
template <class T, class R, class E>
struct Mapper;
struct Unknown;

struct ErrorParamsError : public virtual temper::core::Failure {};

struct Missing : public virtual ErrorParamsError {
  temper::core::Str key;
  static std::shared_ptr<Missing> make(temper::core::Str const & key);
  temper::core::Str get_message() const override;
  temper::core::Str get_key() const;
};

template <class T, class R, class E>
struct Mapper : public virtual temper::core::AnyValueBase {
  virtual temper::core::Expected<R, E> apply(T x) const = 0;
};

struct Unknown : public virtual ErrorParamsError {
  temper::core::Str word;
  static std::shared_ptr<Unknown> make(temper::core::Str const & word);
  temper::core::Str get_message() const override;
  temper::core::Str get_word() const;
};

}  // namespace error_params

// init.cpp
#include <error-params/init.hpp>

namespace error_params {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;

}  // namespace temper_globals

std::shared_ptr<Missing> Missing::make(core::Str const & key) {
  std::shared_ptr<Missing> self = std::make_shared<Missing>();
  self->key = key;
  return self;
}

core::Str Missing::get_message() const {
  return "missing (key: " + this->key + ")";
}

core::Str Missing::get_key() const {
  return this->key;
}

static core::Expected<int32_t, std::shared_ptr<Missing>> find(core::Str const & key) {
  if (key == TEMPER_STR("one")) {
    return 1;
  }
  if (key == TEMPER_STR("two")) {
    return 2;
  }
  return core::unexpected(Missing::make(key));
}

template <class T, class R, class E>
static core::Expected<std::shared_ptr<std::vector<R>>, E> mapAll(std::shared_ptr<std::vector<T>> const & xs, std::function<core::Expected<R, E>(T)> const & f) {
  std::shared_ptr<std::vector<R>> out = core::ListBuilder::make<R>();
  {
    int32_t length = core::List::length<T>(xs);
    int32_t i = 0;
    while (i < length) {
      T x = core::List::get<T>(xs, i);
      i = core::Int::add(i, 1);
      auto result = f(x);
      if (!result.has_value()) {
        return core::unexpected(result.error());
      }
      core::ListBuilder::add<R>(out, *result);
    }
  }
  return core::List::toList<R>(out);
}

static core::Expected<std::shared_ptr<std::vector<int32_t>>, std::shared_ptr<Missing>> found(std::shared_ptr<std::vector<core::Str>> const & keys) {
  return error_params::mapAll<core::Str, int32_t, std::shared_ptr<Missing>>(keys, [](core::Str k) -> core::Expected<int32_t, std::shared_ptr<Missing>> { return error_params::find(k); });
}

static core::Str shown(std::shared_ptr<std::vector<core::Str>> const & keys) {
  std::shared_ptr<std::vector<int32_t>> ns;
  std::shared_ptr<Missing> caught = nullptr;
  std::shared_ptr<std::vector<int32_t>> held = nullptr;
  auto foundResult = error_params::found(keys);
  if (foundResult.has_value()) {
    held = *foundResult;
  } else {
    std::shared_ptr<Missing> failure = foundResult.error();
    caught = failure;
  }
  if (held == nullptr) {
    std::shared_ptr<Missing> e = caught;
    return "missing " + e->key;
  } else {
    ns = held;
  }
  return core::List::join(ns, TEMPER_STR(","), [](int32_t n) -> core::Str { return std::to_string(n); });
}

static core::Expected<int32_t, std::shared_ptr<Missing>> applied(std::function<core::Expected<int32_t, std::shared_ptr<Missing>>(int32_t)> const & f, int32_t n) {
  return f(n);
}

template <class T, class R, class E>
static int32_t failures(std::shared_ptr<std::vector<T>> const & xs, std::function<core::Expected<R, E>(T)> const & f) {
  int32_t failed = 0;
  {
    int32_t length = core::List::length<T>(xs);
    int32_t i = 0;
    while (i < length) {
      T x = core::List::get<T>(xs, i);
      i = core::Int::add(i, 1);
      auto result = f(x);
      if (!result.has_value()) {
        failed = core::Int::add(failed, 1);
      }
    }
  }
  return failed;
}

template <class T, class E>
static core::Expected<int32_t, E> sumAll(std::shared_ptr<std::vector<T>> const & xs, std::function<core::Expected<int32_t, E>(T)> const & f) {
  int32_t total = 0;
  core::NullableParam<E> first = nullptr;
  {
    int32_t length = core::List::length<T>(xs);
    int32_t i = 0;
    while (i < length) {
      T x = core::List::get<T>(xs, i);
      i = core::Int::add(i, 1);
      auto result2 = [&]() -> core::Expected<void, E> {
        auto result = f(x);
        if (!result.has_value()) {
          return core::unexpected(result.error());
        }
        total = core::Int::add(total, *result);
        return {};
      }();
      if (!result2.has_value()) {
        E e = result2.error();
        if (first == nullptr) {
          first = e;
        }
      }
    }
  }
  core::NullableParam<E> failed = first;
  if (failed != nullptr) {
    return core::unexpected(failed.value);
  }
  return total;
}

template <class E>
static core::Expected<int32_t, E> both(std::function<core::Expected<int32_t, E>(core::Str)> const & a, std::function<core::Expected<int32_t, E>(core::Str)> const & b, core::Str const & key) {
  auto result = a(key);
  if (!result.has_value()) {
    return core::unexpected(result.error());
  }
  int32_t int32 = *result;
  auto result2 = b(key);
  if (!result2.has_value()) {
    return core::unexpected(result2.error());
  }
  return core::Int::add(int32, *result2);
}

std::shared_ptr<Unknown> Unknown::make(core::Str const & word) {
  std::shared_ptr<Unknown> self = std::make_shared<Unknown>();
  self->word = word;
  return self;
}

core::Str Unknown::get_message() const {
  return "unknown (word: " + this->word + ")";
}

core::Str Unknown::get_word() const {
  return this->word;
}

static core::Expected<int32_t, std::shared_ptr<Unknown>> word(core::Str const & key) {
  if (key == TEMPER_STR("ten")) {
    return 10;
  }
  return core::unexpected(Unknown::make(key));
}

static core::Str either(core::Str const & key) {
  int32_t n;
  std::shared_ptr<ErrorParamsError> caught = nullptr;
  core::NullableParam<int32_t> held = nullptr;
  auto fn = [](core::Str x) -> core::Expected<int32_t, std::shared_ptr<ErrorParamsError>> {
    auto findResult = error_params::find(x);
    if (!findResult.has_value()) {
      return core::unexpected(findResult.error());
    }
    return *findResult;
  };
  auto fn2 = [](core::Str x2) -> core::Expected<int32_t, std::shared_ptr<ErrorParamsError>> {
    auto wordResult = error_params::word(x2);
    if (!wordResult.has_value()) {
      return core::unexpected(wordResult.error());
    }
    return *wordResult;
  };
  auto bothResult = error_params::both<std::shared_ptr<ErrorParamsError>>(fn, fn2, key);
  if (bothResult.has_value()) {
    held = *bothResult;
  } else {
    std::shared_ptr<ErrorParamsError> failure = bothResult.error();
    caught = failure;
  }
  if (held == nullptr) {
    std::shared_ptr<ErrorParamsError> e = caught;
    return dynamic_cast<Missing const *>(e.get()) != nullptr ? core::Str("no " + core::checked_cast<Missing>(e)->key) : dynamic_cast<Unknown const *>(e.get()) != nullptr ? core::Str("unknown " + core::checked_cast<Unknown>(e)->word) : core::panic<core::Str>();
  } else {
    n = held.value;
  }
  return std::to_string(n);
}

static core::Expected<core::Str, std::shared_ptr<Missing>> parts(std::shared_ptr<std::vector<core::Str>> const & keys) {
  auto fn = [](core::Str k) -> core::Expected<bool, std::shared_ptr<Missing>> {
    auto findResult = error_params::find(k);
    if (!findResult.has_value()) {
      return core::unexpected(findResult.error());
    }
    return *findResult > 1;
  };
  auto Result = core::List::tryFilter(keys, fn);
  if (!Result.has_value()) {
    return core::unexpected(Result.error());
  }
  std::shared_ptr<std::vector<core::Str>> found = *Result;
  auto fn2 = [](int32_t n, core::Str k2) -> core::Expected<int32_t, std::shared_ptr<Missing>> {
    auto findResult2 = error_params::find(k2);
    if (!findResult2.has_value()) {
      return core::unexpected(findResult2.error());
    }
    return core::Int::add(n, *findResult2);
  };
  auto Result2 = core::List::tryReduceFrom(keys, 0, fn2);
  if (!Result2.has_value()) {
    return core::unexpected(Result2.error());
  }
  int32_t total = *Result2;
  core::Str join = core::List::join(found, TEMPER_STR(","), [](core::Str k3) -> core::Str { return k3; });
  auto fn4 = [](core::Str k4) -> core::Expected<core::Str, std::shared_ptr<Missing>> {
    auto findResult3 = error_params::find(k4);
    if (!findResult3.has_value()) {
      return core::unexpected(findResult3.error());
    }
    return std::to_string(*findResult3);
  };
  auto Result3 = core::List::tryJoin(keys, TEMPER_STR("+"), fn4);
  if (!Result3.has_value()) {
    return core::unexpected(Result3.error());
  }
  return join + " " + std::to_string(total) + " " + *Result3;
}

template <class T, class E>
static core::Expected<std::shared_ptr<std::vector<T>>, E> mapTwice(std::shared_ptr<std::vector<T>> const & xs, std::function<core::Expected<T, E>(T)> const & f) {
  auto mapAllResult = error_params::mapAll<T, T, E>(xs, f);
  if (!mapAllResult.has_value()) {
    return core::unexpected(mapAllResult.error());
  }
  std::shared_ptr<std::vector<T>> once = *mapAllResult;
  return core::List::tryMap(once, f);
}

static core::Expected<int32_t, std::shared_ptr<Missing>> upToTwo(int32_t n) {
  if (n > 2) {
    return core::unexpected(Missing::make(std::to_string(n)));
  }
  return core::Int::add(n, 1);
}

static core::Expected<core::Str, std::shared_ptr<Missing>> keyed(core::Str const & text) {
  std::shared_ptr<temper_std::Regex> regex = temper_std::Repeat::make(core::to_base<temper_std::RegexNode>(temper_std::CodeSet::make(core::List::make<std::shared_ptr<temper_std::CodePart>>(core::to_base<temper_std::CodePart>(temper_std::CodeRange::make(97, 122))), false)), 1, nullptr, false)->compiled();
  auto fn = [](std::shared_ptr<temper_std::Match> m) -> core::Expected<core::Str, std::shared_ptr<Missing>> {
    core::Console::log(temper_globals::console.value, "seen " + m->full->value);
    auto findResult = error_params::find(m->full->value);
    if (!findResult.has_value()) {
      return core::unexpected(findResult.error());
    }
    return std::to_string(*findResult);
  };
  return regex->replace<std::shared_ptr<Missing>>(text, fn);
}

static core::Str bracketed(std::shared_ptr<temper_std::Match> const & m) {
  return "<" + m->full->value + ">";
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_std::global_init_regex();
    temper_globals::console.value = core::Console::get_console();
    std::shared_ptr<std::vector<int32_t>> doubled = *error_params::mapAll<int32_t, int32_t, core::Never>(core::List::make<int32_t>(1, 2, 3), [](int32_t n) -> core::Expected<int32_t, core::Never> { return core::Int::mul(n, 2); });
    std::function<core::Expected<int32_t, core::Never>(int32_t)> safe = [](int32_t n) -> core::Expected<int32_t, core::Never> { return core::Int::add(n, 1); };
    core::Str join = core::List::join(doubled, TEMPER_STR(","), [](int32_t n) -> core::Str { return std::to_string(n); });
    core::Console::log(temper_globals::console.value, join + " " + std::to_string(*safe(1)));
    std::shared_ptr<std::vector<int32_t>> named;
    auto mapAllResult = error_params::mapAll<core::Str, int32_t, std::shared_ptr<Missing>>(core::List::make<core::Str>(TEMPER_STR("two"), TEMPER_STR("one")), find);
    if (mapAllResult.has_value()) {
      named = *mapAllResult;
    } else {
      named = core::List::make<int32_t>();
    }
    core::Str string = error_params::shown(core::List::make<core::Str>(TEMPER_STR("one"), TEMPER_STR("two")));
    core::Str value2 = error_params::shown(core::List::make<core::Str>(TEMPER_STR("one"), TEMPER_STR("six"), TEMPER_STR("ten")));
    core::Console::log(temper_globals::console.value, string + " " + value2 + " " + std::to_string(core::List::length<int32_t>(named)));
    int32_t six;
    auto appliedResult = error_params::applied([](int32_t n) -> core::Expected<int32_t, std::shared_ptr<Missing>> { return core::Int::mul(n, 3); }, 2);
    if (appliedResult.has_value()) {
      six = *appliedResult;
    } else {
      six = -1;
    }
    core::Console::log(temper_globals::console.value, std::to_string(six));
    int32_t some = error_params::failures<core::Str, int32_t, std::shared_ptr<Missing>>(core::List::make<core::Str>(TEMPER_STR("one"), TEMPER_STR("six"), TEMPER_STR("two"), TEMPER_STR("ten")), find);
    int32_t none = error_params::failures<int32_t, int32_t, core::Never>(core::List::make<int32_t>(1, 2), [](int32_t n) -> core::Expected<int32_t, core::Never> { return n; });
    core::Console::log(temper_globals::console.value, std::to_string(some) + " " + std::to_string(none));
    int32_t summed;
    auto sumAllResult = error_params::sumAll<core::Str, std::shared_ptr<Missing>>(core::List::make<core::Str>(TEMPER_STR("one"), TEMPER_STR("two")), find);
    if (sumAllResult.has_value()) {
      summed = *sumAllResult;
    } else {
      summed = -1;
    }
    int32_t unsummed;
    auto sumAllResult2 = error_params::sumAll<core::Str, std::shared_ptr<Missing>>(core::List::make<core::Str>(TEMPER_STR("six"), TEMPER_STR("one"), TEMPER_STR("ten")), find);
    if (sumAllResult2.has_value()) {
      unsummed = *sumAllResult2;
    } else {
      std::shared_ptr<Missing> e = sumAllResult2.error();
      core::Console::log(temper_globals::console.value, "first missing " + e->key);
      unsummed = -1;
    }
    core::Console::log(temper_globals::console.value, std::to_string(summed) + " " + std::to_string(unsummed) + " " + std::to_string(*error_params::sumAll<int32_t, core::Never>(core::List::make<int32_t>(1, 2), safe)));
    int32_t twelve;
    auto bothResult = error_params::both<std::shared_ptr<Missing>>(find, [](core::Str) -> core::Expected<int32_t, std::shared_ptr<Missing>> { return 10; }, TEMPER_STR("two"));
    if (bothResult.has_value()) {
      twelve = *bothResult;
    } else {
      twelve = -1;
    }
    int32_t lost;
    auto bothResult2 = error_params::both<std::shared_ptr<Missing>>(find, find, TEMPER_STR("six"));
    if (bothResult2.has_value()) {
      lost = *bothResult2;
    } else {
      std::shared_ptr<Missing> e2 = bothResult2.error();
      core::Console::log(temper_globals::console.value, "lost " + e2->key);
      lost = -1;
    }
    core::Console::log(temper_globals::console.value, std::to_string(twelve) + " " + std::to_string(lost));
    core::Str value3 = error_params::either(TEMPER_STR("one"));
    core::Console::log(temper_globals::console.value, value3 + " " + error_params::either(TEMPER_STR("ten")));
    core::Str value4;
    auto partsResult = error_params::parts(core::List::make<core::Str>(TEMPER_STR("one"), TEMPER_STR("two")));
    if (partsResult.has_value()) {
      value4 = *partsResult;
    } else {
      value4 = TEMPER_STR("?");
    }
    core::Console::log(temper_globals::console.value, value4);
    core::Str value5;
    auto partsResult2 = error_params::parts(core::List::make<core::Str>(TEMPER_STR("one"), TEMPER_STR("six")));
    if (partsResult2.has_value()) {
      value5 = *partsResult2;
    } else {
      std::shared_ptr<Missing> e3 = partsResult2.error();
      value5 = "missing " + e3->key;
    }
    core::Console::log(temper_globals::console.value, value5);
    core::Console::log(temper_globals::console.value, core::List::join(core::List::sorted(core::List::make<int32_t>(3, 1, 2), [](int32_t a, int32_t b) -> int32_t { return core::Int::sub(a, b); }), TEMPER_STR(","), [](int32_t n) -> core::Str { return std::to_string(n); }));
    std::shared_ptr<std::vector<int32_t>> bumped = *core::List::tryMap(core::List::make<int32_t>(1, 2), safe);
    std::shared_ptr<std::vector<int32_t>> twice = *error_params::mapTwice<int32_t, core::Never>(core::List::make<int32_t>(1, 2), safe);
    std::shared_ptr<std::vector<int32_t>> failed;
    auto mapTwiceResult = error_params::mapTwice<int32_t, std::shared_ptr<Missing>>(core::List::make<int32_t>(1, 2), upToTwo);
    if (mapTwiceResult.has_value()) {
      failed = *mapTwiceResult;
    } else {
      std::shared_ptr<Missing> e4 = mapTwiceResult.error();
      core::Console::log(temper_globals::console.value, "missing " + e4->key);
      failed = core::List::make<int32_t>();
    }
    core::Str value6 = core::List::join(bumped, TEMPER_STR(","), [](int32_t n) -> core::Str { return std::to_string(n); });
    core::Str value7 = core::List::join(twice, TEMPER_STR(","), [](int32_t n) -> core::Str { return std::to_string(n); });
    core::Console::log(temper_globals::console.value, value6 + " " + value7 + " " + std::to_string(core::List::length<int32_t>(failed)));
    core::Str allKeyed;
    auto keyedResult = error_params::keyed(TEMPER_STR("one+two"));
    if (keyedResult.has_value()) {
      allKeyed = *keyedResult;
    } else {
      allKeyed = TEMPER_STR("?");
    }
    core::Str noneKeyed;
    auto keyedResult2 = error_params::keyed(TEMPER_STR("one+six+ten"));
    if (keyedResult2.has_value()) {
      noneKeyed = *keyedResult2;
    } else {
      std::shared_ptr<Missing> e5 = keyedResult2.error();
      noneKeyed = "missing " + e5->key;
    }
    core::Str plain = *temper_std::CodePoints::make(TEMPER_STR("o"))->compiled()->replace<core::Never>(TEMPER_STR("two"), [](std::shared_ptr<temper_std::Match>) -> core::Str { return TEMPER_STR("0"); });
    core::Console::log(temper_globals::console.value, allKeyed + " " + noneKeyed + " " + plain + " " + *temper_std::CodePoints::make(TEMPER_STR("w"))->compiled()->replace<core::Never>(TEMPER_STR("two"), bracketed));
  });
}

}  // namespace error_params
