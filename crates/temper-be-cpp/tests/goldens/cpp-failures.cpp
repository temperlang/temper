// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace cpp_failures {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct CppFailuresError;
struct NotFound;
struct SignError;
struct BadSign;
struct EmptyText;
struct LookupFailed;

struct CppFailuresError : public virtual temper::core::Failure {};

struct NotFound : public virtual CppFailuresError {
  temper::core::Str key;
  static std::shared_ptr<NotFound> make(temper::core::Str const & key);
  temper::core::Str get_message() const override;
  temper::core::Str get_key() const;
};

struct SignError : public virtual CppFailuresError {};

struct BadSign : public virtual SignError {
  temper::core::Str text;
  static std::shared_ptr<BadSign> make(temper::core::Str const & text);
  temper::core::Str get_message() const override;
  temper::core::Str get_text() const;
};

struct EmptyText : public virtual SignError {
  static std::shared_ptr<EmptyText> make();
  temper::core::Str get_message() const override;
};

struct LookupFailed : public virtual CppFailuresError {
  temper::core::Str name;
  static std::shared_ptr<LookupFailed> make(temper::core::Str const & name);
  temper::core::Str get_message() const override;
  temper::core::Str get_name() const;
};

temper::core::Expected<int32_t, std::shared_ptr<NotFound>> lookup(temper::core::Str const & key);
temper::core::Expected<int32_t, std::shared_ptr<SignError>> sign(temper::core::Str const & text);
temper::core::Expected<int32_t, std::shared_ptr<LookupFailed>> setting(temper::core::Str const & name);
temper::core::Expected<int32_t, std::shared_ptr<LookupFailed>> madeBefore(temper::core::Str const & name);
temper::core::Expected<int32_t, std::shared_ptr<LookupFailed>> thrownAgain(temper::core::Str const & name);
temper::core::Expected<int32_t, std::shared_ptr<NotFound>> risky(int32_t n);
temper::core::Str explained(temper::core::Str const & name);
temper::core::Str signMessage(temper::core::Str const & text);
int32_t mixed(temper::core::Str const & key, int32_t n);
temper::core::Str firstSign(std::shared_ptr<std::vector<temper::core::Str>> const & texts);

}  // namespace cpp_failures

// init.cpp
#include <cpp-failures/init.hpp>

namespace cpp_failures {

namespace core = temper::core;

std::shared_ptr<NotFound> NotFound::make(core::Str const & key) {
  global_init_init();
  std::shared_ptr<NotFound> self = std::make_shared<NotFound>();
  self->key = key;
  return self;
}

core::Str NotFound::get_message() const {
  return "not found (key: " + this->key + ")";
}

core::Str NotFound::get_key() const {
  return this->key;
}

std::shared_ptr<BadSign> BadSign::make(core::Str const & text) {
  global_init_init();
  std::shared_ptr<BadSign> self = std::make_shared<BadSign>();
  self->text = text;
  return self;
}

core::Str BadSign::get_message() const {
  return "bad sign (text: " + this->text + ")";
}

core::Str BadSign::get_text() const {
  return this->text;
}

std::shared_ptr<EmptyText> EmptyText::make() {
  global_init_init();
  std::shared_ptr<EmptyText> self = std::make_shared<EmptyText>();
  return self;
}

core::Str EmptyText::get_message() const {
  return TEMPER_STR("no text");
}

std::shared_ptr<LookupFailed> LookupFailed::make(core::Str const & name) {
  global_init_init();
  std::shared_ptr<LookupFailed> self = std::make_shared<LookupFailed>();
  self->name = name;
  return self;
}

core::Str LookupFailed::get_message() const {
  return "lookup failed (name: " + this->name + ")";
}

core::Str LookupFailed::get_name() const {
  return this->name;
}

core::Expected<int32_t, std::shared_ptr<NotFound>> lookup(core::Str const & key) {
  global_init_init();
  if (key == TEMPER_STR("a")) {
    return 1;
  }
  return core::unexpected(NotFound::make(key));
}

core::Expected<int32_t, std::shared_ptr<SignError>> sign(core::Str const & text) {
  global_init_init();
  {
    if (text == TEMPER_STR("+")) {
      return 1;
    } else if (text == TEMPER_STR("-")) {
      return -1;
    } else if (text == core::Str()) {
      return core::unexpected(EmptyText::make());
    } else {
      return core::unexpected(BadSign::make(text));
    }
  }
}

core::Expected<int32_t, std::shared_ptr<LookupFailed>> setting(core::Str const & name) {
  global_init_init();
  auto lookupResult = cpp_failures::lookup(name);
  if (lookupResult.has_value()) {
    return *lookupResult;
  } else {
    std::shared_ptr<NotFound> failure = lookupResult.error();
    return core::unexpected(core::with_cause(LookupFailed::make(name), failure));
  }
}

core::Expected<int32_t, std::shared_ptr<LookupFailed>> madeBefore(core::Str const & name) {
  global_init_init();
  std::shared_ptr<LookupFailed> failure = LookupFailed::make(name);
  auto lookupResult = cpp_failures::lookup(name);
  if (lookupResult.has_value()) {
    return *lookupResult;
  } else {
    std::shared_ptr<NotFound> failure2 = lookupResult.error();
    return core::unexpected(core::with_cause(failure, failure2));
  }
}

core::Expected<int32_t, std::shared_ptr<LookupFailed>> thrownAgain(core::Str const & name) {
  global_init_init();
  auto settingResult = cpp_failures::setting(name);
  if (settingResult.has_value()) {
    return *settingResult;
  } else {
    std::shared_ptr<LookupFailed> e = settingResult.error();
    auto lookupResult = cpp_failures::lookup(TEMPER_STR("q"));
    if (lookupResult.has_value()) {
      return *lookupResult;
    } else {
      std::shared_ptr<NotFound> failure = lookupResult.error();
      return core::unexpected(core::with_cause(e, failure));
    }
  }
}

core::Expected<int32_t, std::shared_ptr<NotFound>> risky(int32_t n) {
  global_init_init();
  if (n > 1) {
    core::panic();
  }
  return cpp_failures::lookup(TEMPER_STR("a"));
}

core::Str explained(core::Str const & name) {
  global_init_init();
  auto settingResult = cpp_failures::setting(name);
  if (settingResult.has_value()) {
    return std::to_string(*settingResult);
  } else {
    std::shared_ptr<LookupFailed> e = settingResult.error();
    std::shared_ptr<core::Failure> cause = e->get_cause();
    core::Str why = cause != nullptr ? cause->get_message() : TEMPER_STR("nothing");
    return e->get_message() + ", from " + why;
  }
}

core::Str signMessage(core::Str const & text) {
  global_init_init();
  auto signResult = cpp_failures::sign(text);
  if (signResult.has_value()) {
    return std::to_string(*signResult);
  } else {
    std::shared_ptr<SignError> e = signResult.error();
    return e->get_message();
  }
}

int32_t mixed(core::Str const & key, int32_t n) {
  global_init_init();
  try {
    auto lookupResult = cpp_failures::lookup(key);
    if (lookupResult.has_value()) {
      return core::Int::add(*lookupResult, core::Int::div_wrap(10, n));
    } else {
      core::bubble();
    }
  } catch (core::TemperBubble const &) {
    return -1;
  }
}

core::Str firstSign(std::shared_ptr<std::vector<core::Str>> const & texts) {
  global_init_init();
  {
    int32_t length = core::List::length<core::Str>(texts);
    int32_t i = 0;
    while (i < length) {
      core::Str t = core::List::get<core::Str>(texts, i);
      i = core::Int::add(i, 1);
      int32_t s;
      std::shared_ptr<SignError> caught = nullptr;
      core::NullableParam<int32_t> held = nullptr;
      auto signResult = cpp_failures::sign(t);
      if (signResult.has_value()) {
        held = *signResult;
      } else {
        std::shared_ptr<SignError> failure = signResult.error();
        caught = failure;
      }
      if (held == nullptr) {
        std::shared_ptr<SignError> e = caught;
        if (dynamic_cast<BadSign const *>(e.get()) != nullptr) {
          return "bad " + core::checked_cast<BadSign>(e)->text;
        }
        continue;
      } else {
        s = held.value;
      }
      return std::to_string(s);
    }
  }
  return TEMPER_STR("none");
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    auto lookupResult = cpp_failures::lookup(TEMPER_STR("a"));
    if (!lookupResult.has_value()) {
      core::uncaught_failure(lookupResult.error());
    }
    auto signResult = cpp_failures::sign(TEMPER_STR("-"));
    if (!signResult.has_value()) {
      core::uncaught_failure(signResult.error());
    }
    auto settingResult = cpp_failures::setting(TEMPER_STR("a"));
    if (!settingResult.has_value()) {
      core::uncaught_failure(settingResult.error());
    }
    core::Console::log(console, std::to_string(*lookupResult) + " " + std::to_string(*signResult) + " " + std::to_string(*settingResult));
    core::Console::log(console, std::to_string(cpp_failures::mixed(TEMPER_STR("a"), 0)) + " " + std::to_string(cpp_failures::mixed(TEMPER_STR("z"), 1)) + " " + std::to_string(cpp_failures::mixed(TEMPER_STR("a"), 5)));
    core::Str string = cpp_failures::explained(TEMPER_STR("port"));
    core::Str value = cpp_failures::explained(TEMPER_STR("a"));
    core::Console::log(console, string + "; " + value + "; " + cpp_failures::signMessage(TEMPER_STR("x")));
    core::Console::log(console, cpp_failures::firstSign(core::List::make<core::Str>(core::Str(), TEMPER_STR("+"))) + " " + cpp_failures::firstSign(core::List::make<core::Str>(core::Str(), TEMPER_STR("?"))) + " " + cpp_failures::firstSign(core::List::make<core::Str>()));
  });
}

}  // namespace cpp_failures
