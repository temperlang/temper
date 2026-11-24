// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace failures {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct FailuresError;
struct NotFound;
struct SignError;
struct EmptyText;
struct BadSign;
struct ConfigError;

struct FailuresError : public virtual temper::core::Failure {};

struct NotFound : public virtual FailuresError {
  temper::core::Str key;
  static std::shared_ptr<NotFound> make(temper::core::Str const & key);
  temper::core::Str get_message() const override;
  temper::core::Str get_key() const;
};

struct SignError : public virtual FailuresError {};

struct EmptyText : public virtual SignError {
  static std::shared_ptr<EmptyText> make();
  temper::core::Str get_message() const override;
};

struct BadSign : public virtual SignError {
  temper::core::Str text;
  static std::shared_ptr<BadSign> make(temper::core::Str const & text);
  temper::core::Str get_message() const override;
  temper::core::Str get_text() const;
};

struct ConfigError : public virtual FailuresError {
  temper::core::Str setting;
  static std::shared_ptr<ConfigError> make(temper::core::Str const & setting);
  temper::core::Str get_message() const override;
  temper::core::Str get_setting() const;
};

}  // namespace failures

// init.cpp
#include <failures/init.hpp>

namespace failures {

namespace core = temper::core;

std::shared_ptr<NotFound> NotFound::make(core::Str const & key) {
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

static core::Expected<int32_t, std::shared_ptr<NotFound>> lookup(core::Str const & key) {
  if (key == TEMPER_STR("a")) {
    return 1;
  }
  if (key == TEMPER_STR("b")) {
    return 2;
  }
  return core::unexpected(NotFound::make(key));
}

static core::Expected<int32_t, std::shared_ptr<NotFound>> sum(core::Str const & a, core::Str const & b) {
  auto lookupResult = failures::lookup(a);
  if (!lookupResult.has_value()) {
    return core::unexpected(lookupResult.error());
  }
  int32_t x = *lookupResult;
  auto lookupResult2 = failures::lookup(b);
  if (!lookupResult2.has_value()) {
    return core::unexpected(lookupResult2.error());
  }
  int32_t y = *lookupResult2;
  return core::Int::add(x, y);
}

static core::Str shown(core::Str const & a, core::Str const & b) {
  int32_t total;
  std::shared_ptr<NotFound> caught = nullptr;
  core::NullableParam<int32_t> held = nullptr;
  auto sumResult = failures::sum(a, b);
  if (sumResult.has_value()) {
    held = *sumResult;
  } else {
    std::shared_ptr<NotFound> failure = sumResult.error();
    caught = failure;
  }
  if (held == nullptr) {
    std::shared_ptr<NotFound> e = caught;
    return "no " + e->key;
  } else {
    total = held.value;
  }
  return std::to_string(total);
}

std::shared_ptr<EmptyText> EmptyText::make() {
  std::shared_ptr<EmptyText> self = std::make_shared<EmptyText>();
  return self;
}

core::Str EmptyText::get_message() const {
  return TEMPER_STR("empty text");
}

std::shared_ptr<BadSign> BadSign::make(core::Str const & text) {
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

static core::Expected<int32_t, std::shared_ptr<BadSign>> sign(core::Str const & text) {
  {
    if (text == TEMPER_STR("+")) {
      return 1;
    } else if (text == TEMPER_STR("-")) {
      return -1;
    } else {
      return core::unexpected(BadSign::make(text));
    }
  }
}

static core::Expected<int32_t, std::shared_ptr<SignError>> parseSign(core::Str const & text) {
  if (text == core::Str()) {
    return core::unexpected(EmptyText::make());
  }
  auto signResult = failures::sign(text);
  if (!signResult.has_value()) {
    return core::unexpected(signResult.error());
  }
  return *signResult;
}

static core::Str describe(std::shared_ptr<SignError> const & e) {
  {
    if (dynamic_cast<EmptyText const *>(e.get()) != nullptr) {
      return TEMPER_STR("empty");
    } else if (dynamic_cast<BadSign const *>(e.get()) != nullptr) {
      return "bad sign " + core::checked_cast<BadSign>(e)->text;
    } else {
      core::panic();
    }
  }
}

static core::Expected<int32_t, std::shared_ptr<NotFound>> rethrown(std::shared_ptr<NotFound> const & p) {
  return core::unexpected(p);
}

std::shared_ptr<ConfigError> ConfigError::make(core::Str const & setting) {
  std::shared_ptr<ConfigError> self = std::make_shared<ConfigError>();
  self->setting = setting;
  return self;
}

core::Str ConfigError::get_message() const {
  return "config error (setting: " + this->setting + ")";
}

core::Str ConfigError::get_setting() const {
  return this->setting;
}

static core::Expected<int32_t, std::shared_ptr<ConfigError>> setting(core::Str const & name) {
  auto lookupResult = failures::lookup(name);
  if (lookupResult.has_value()) {
    return *lookupResult;
  } else {
    std::shared_ptr<NotFound> e = lookupResult.error();
    return core::unexpected(core::with_cause(ConfigError::make(name + ": missing " + e->key), e));
  }
}

static core::Str pair(core::Str const & a, core::Str const & b) {
  auto lookupResult = failures::lookup(a);
  if (lookupResult.has_value()) {
    int32_t x = *lookupResult;
    int32_t y;
    auto lookupResult2 = failures::lookup(b);
    if (lookupResult2.has_value()) {
      y = *lookupResult2;
    } else {
      y = 0;
    }
    return std::to_string(x) + "+" + std::to_string(y);
  } else {
    std::shared_ptr<NotFound> e = lookupResult.error();
    return "outer " + e->key;
  }
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    int32_t found;
    auto lookupResult = failures::lookup(TEMPER_STR("a"));
    if (lookupResult.has_value()) {
      found = *lookupResult;
    } else {
      found = -1;
    }
    int32_t missing;
    auto lookupResult2 = failures::lookup(TEMPER_STR("z"));
    if (lookupResult2.has_value()) {
      missing = *lookupResult2;
    } else {
      missing = -1;
    }
    core::Console::log(console, std::to_string(found) + " " + std::to_string(missing));
    int32_t n;
    auto lookupResult3 = failures::lookup(TEMPER_STR("q"));
    if (lookupResult3.has_value()) {
      n = *lookupResult3;
    } else {
      std::shared_ptr<NotFound> e = lookupResult3.error();
      core::Console::log(console, "not found: " + e->key);
      n = 0;
    }
    int32_t m;
    auto lookupResult4 = failures::lookup(TEMPER_STR("b"));
    if (lookupResult4.has_value()) {
      m = *lookupResult4;
    } else {
      m = -1;
    }
    core::Console::log(console, std::to_string(n) + " " + std::to_string(m));
    core::Console::log(console, failures::shown(TEMPER_STR("a"), TEMPER_STR("b")) + " " + failures::shown(TEMPER_STR("a"), TEMPER_STR("x")) + " " + failures::shown(TEMPER_STR("y"), TEMPER_STR("z")));
    {
      std::shared_ptr<std::vector<core::Str>> list = core::List::make<core::Str>(TEMPER_STR("+"), TEMPER_STR("-"), core::Str(), TEMPER_STR("x"));
      int32_t length = core::List::length<core::Str>(list);
      int32_t i = 0;
      while (i < length) {
        core::Str t = core::List::get<core::Str>(list, i);
        i = core::Int::add(i, 1);
        int32_t s;
        std::shared_ptr<SignError> caught = nullptr;
        core::NullableParam<int32_t> held = nullptr;
        auto parseSignResult = failures::parseSign(t);
        if (parseSignResult.has_value()) {
          held = *parseSignResult;
        } else {
          std::shared_ptr<SignError> failure = parseSignResult.error();
          caught = failure;
        }
        if (held == nullptr) {
          std::shared_ptr<SignError> e2 = caught;
          core::Console::log(console, failures::describe(e2));
          continue;
        } else {
          s = held.value;
        }
        core::Console::log(console, std::to_string(s));
      }
    }
    int32_t code;
    auto parseSignResult2 = failures::parseSign(TEMPER_STR("?"));
    if (parseSignResult2.has_value()) {
      code = *parseSignResult2;
    } else {
      std::shared_ptr<SignError> e3 = parseSignResult2.error();
      code = dynamic_cast<EmptyText const *>(e3.get()) != nullptr ? 0 : dynamic_cast<BadSign const *>(e3.get()) != nullptr ? 9 : core::panic<int32_t>();
    }
    core::Console::log(console, std::to_string(code));
    std::shared_ptr<NotFound> kept = NotFound::make(TEMPER_STR("none"));
    int32_t v;
    auto lookupResult5 = failures::lookup(TEMPER_STR("k"));
    if (lookupResult5.has_value()) {
      v = *lookupResult5;
    } else {
      std::shared_ptr<NotFound> e4 = lookupResult5.error();
      kept = e4;
      v = -1;
    }
    std::shared_ptr<std::vector<std::shared_ptr<NotFound>>> problems = core::List::make<std::shared_ptr<NotFound>>(NotFound::make(TEMPER_STR("x")), kept);
    core::Console::log(console, std::to_string(v) + " " + core::List::join(problems, TEMPER_STR(","), [](std::shared_ptr<NotFound> p) -> core::Str { return p->key; }));
    int32_t w;
    auto rethrownResult = failures::rethrown(kept);
    if (rethrownResult.has_value()) {
      w = *rethrownResult;
    } else {
      std::shared_ptr<NotFound> e5 = rethrownResult.error();
      core::Console::log(console, "again " + e5->key);
      w = 0;
    }
    static_cast<void>(w);
    int32_t port;
    auto settingResult = failures::setting(TEMPER_STR("port"));
    if (settingResult.has_value()) {
      port = *settingResult;
    } else {
      std::shared_ptr<ConfigError> e6 = settingResult.error();
      core::Console::log(console, e6->setting);
      port = -1;
    }
    int32_t first;
    auto settingResult2 = failures::setting(TEMPER_STR("a"));
    if (settingResult2.has_value()) {
      first = *settingResult2;
    } else {
      first = -1;
    }
    core::Console::log(console, std::to_string(port) + " " + std::to_string(first));
    core::Console::log(console, failures::pair(TEMPER_STR("a"), TEMPER_STR("z")) + " " + failures::pair(TEMPER_STR("z"), TEMPER_STR("a")));
    int32_t total = 0;
    {
      std::shared_ptr<std::vector<core::Str>> list2 = core::List::make<core::Str>(TEMPER_STR("a"), TEMPER_STR("b"), TEMPER_STR("c"), TEMPER_STR("a"));
      int32_t length2 = core::List::length<core::Str>(list2);
      int32_t i2 = 0;
      while (i2 < length2) {
        core::Str k = core::List::get<core::Str>(list2, i2);
        i2 = core::Int::add(i2, 1);
        int32_t int32 = total;
        int32_t t2;
        core::NullableParam<int32_t> held2 = nullptr;
        auto lookupResult6 = failures::lookup(k);
        if (lookupResult6.has_value()) {
          held2 = *lookupResult6;
        }
        if (held2 == nullptr) {
          break;
        } else {
          t2 = held2.value;
        }
        total = core::Int::add(int32, t2);
      }
    }
    core::Console::log(console, std::to_string(total));
  });
}

}  // namespace failures
