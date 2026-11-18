// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace functions_program {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct Counter;
struct IntOp;
struct Scaler;
struct Button;
struct Gate;

struct Counter : public virtual temper::core::AnyValueBase {
  virtual int32_t apply() const = 0;
};

struct IntOp : public virtual temper::core::AnyValueBase {
  virtual int32_t apply(int32_t x) const = 0;
};

struct Scaler : public std::enable_shared_from_this<Scaler> {
  static std::shared_ptr<Scaler> make();
  static int32_t triple(int32_t x);
};

struct Button : public std::enable_shared_from_this<Button> {
  temper::core::Str label;
  std::function<int32_t()> onPress;
  static std::shared_ptr<Button> make(temper::core::Str const & label, std::function<int32_t()> const & onPress);
  temper::core::Str press() const;
  temper::core::Str get_label() const;
  std::function<int32_t()> get_onPress() const;
};

struct Gate : public std::enable_shared_from_this<Gate> {
  temper::core::NullableParam<std::function<int32_t(int32_t)>> check;
  static std::shared_ptr<Gate> make();
  bool allows(int32_t n) const;
  int32_t passed(int32_t n) const;
  temper::core::NullableParam<std::function<int32_t(int32_t)>> get_check();
};

}  // namespace functions_program

// init.cpp
#include <functions-program/init.hpp>

namespace functions_program {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;

}  // namespace temper_globals

static std::function<int32_t()> makeCounter(int32_t start) {
  std::shared_ptr<int32_t> count = std::make_shared<int32_t>(start);
  auto fn = [=]() -> int32_t {
    *count = core::Int::add((*count), 1);
    return (*count);
  };
  return fn;
}

static std::function<int32_t(int32_t)> twice(std::function<int32_t(int32_t)> const & f) {
  return [=](int32_t x) -> int32_t { return f(f(x)); };
}

static int32_t applyTo(std::function<int32_t(int32_t)> const & f, int32_t x) {
  return f(x);
}

static int32_t addOne(int32_t x) {
  return core::Int::add(x, 1);
}

std::shared_ptr<Scaler> Scaler::make() {
  std::shared_ptr<Scaler> self = std::make_shared<Scaler>();
  return self;
}

int32_t Scaler::triple(int32_t x) {
  return core::Int::mul(x, 3);
}

std::shared_ptr<Button> Button::make(core::Str const & label, std::function<int32_t()> const & onPress) {
  std::shared_ptr<Button> self = std::make_shared<Button>();
  self->label = label;
  self->onPress = onPress;
  return self;
}

core::Str Button::press() const {
  return this->label + " " + std::to_string(this->onPress());
}

core::Str Button::get_label() const {
  return this->label;
}

std::function<int32_t()> Button::get_onPress() const {
  return this->onPress;
}

static void drive(int32_t steps, std::function<std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>>()> const & body) {
  std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> g = body();
  {
    int32_t s = 0;
    while (s < steps) {
      core::next(g);
      s = core::Int::add(s, 1);
    }
  }
}

static int32_t sumFrom(std::shared_ptr<std::vector<int32_t>> const & xs, int32_t least) {
  int32_t sum = 0;
  {
    int32_t length = core::List::length<int32_t>(xs);
    int32_t i = 0;
    while (i < length) {
      int32_t x = core::List::get<int32_t>(xs, i);
      i = core::Int::add(i, 1);
      if (x < least) {
        continue;
      }
      sum = core::Int::add(sum, x);
    }
  }
  return sum;
}

static int32_t doubles(core::Str const & s) {
  int32_t n = 0;
  int32_t end = core::String::end(s);
  {
    int32_t i = core::String::begin();
    int32_t prev = i;
    while (core::Compare::cmp(i, end) < 0) {
      if (core::Compare::cmp(prev, i) < 0 && core::String::get(s, prev) == core::String::get(s, i)) {
        n = core::Int::add(n, 1);
      }
      prev = i;
      i = core::String::next(s, i);
    }
  }
  return n;
}

static int32_t climb(int32_t x) {
  int32_t d;
  if (x == 0) {
    struct DownGroup {
      int32_t x;
      int32_t down(int32_t n) const {
        if (n <= x) {
          return 0;
        } else {
          return core::Int::add(down(core::Int::sub(n, 1)), 1);
        }
      }
    };
    DownGroup const down_group = DownGroup{x};
    std::function<int32_t(int32_t)> down = [=](int32_t a0) -> int32_t { return down_group.down(a0); };
    d = down(3);
  } else {
    return -1;
  }
  d = core::Int::add(d, 1);
  {
    if (d == 4) {
      int32_t top = core::Int::add(d, 1);
      struct UpGroup {
        int32_t top;
        int32_t up(int32_t n) const {
          if (n >= top) {
            return n;
          } else {
            return up(core::Int::add(n, 1));
          }
        }
      };
      UpGroup const up_group = UpGroup{top};
      std::function<int32_t(int32_t)> up = [=](int32_t a0) -> int32_t { return up_group.up(a0); };
      return up(d);
    } else {
      return d;
    }
  }
}

std::shared_ptr<Gate> Gate::make() {
  std::shared_ptr<Gate> self = std::make_shared<Gate>();
  self->check = nullptr;
  return self;
}

bool Gate::allows(int32_t n) const {
  core::NullableParam<std::function<int32_t(int32_t)>> c = this->check;
  if (c == nullptr) {
    return true;
  } else {
    return c.value(n) > 0;
  }
}

int32_t Gate::passed(int32_t n) const {
  core::NullableParam<std::function<int32_t(int32_t)>> c = this->check;
  if (c != nullptr) {
    return c.value(n);
  } else {
    return n;
  }
}

core::NullableParam<std::function<int32_t(int32_t)>> Gate::get_check() {
  return this->check;
}

static bool stringIsPalindrome(core::Str const & s) {
  int32_t i = core::String::begin();
  int32_t j = core::String::end(s);
  while (core::Compare::cmp(i, j) < 0) {
    j = core::String::prev(s, j);
    if (core::String::get(s, i) != core::String::get(s, j)) {
      return false;
    }
    i = core::String::next(s, i);
  }
  return true;
}

static int32_t intOrZero(core::NullableParam<int32_t> n) {
  {
    if (n == nullptr) {
      return 0;
    } else {
      return n.value;
    }
  }
}

template <class T>
static T listFirstOr(std::shared_ptr<std::vector<T>> const & ls, T d) {
  if (core::List::isEmpty<T>(ls)) {
    return d;
  } else {
    return core::List::get<T>(ls, 0);
  }
}

static core::Str stringPadded(core::Str const & s, int32_t n, core::NullableParam<core::Str> fill) {
  core::Str fill2 = fill == nullptr ? TEMPER_STR(".") : fill.value;
  core::Str out = s;
  {
    int32_t i = 0;
    while (i < n) {
      out = out + fill2;
      i = core::Int::add(i, 1);
    }
  }
  return out;
}

static core::Str stringPadded(core::Str const & s, int32_t n) {
  return stringPadded(s, n, nullptr);
}

static core::Str gatePassed(std::shared_ptr<Gate> const & g, core::Str const & s) {
  return s + " " + core::Boolean::toString(g->allows(1));
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::console.value = core::Console::get_console();
    std::function<int32_t()> counter = makeCounter(10);
    counter();
    core::Console::log(temper_globals::console.value, "count " + std::to_string(counter()));
    std::function<int32_t()> other = makeCounter(0);
    int32_t int32 = other();
    core::Console::log(temper_globals::console.value, "other " + std::to_string(int32) + " count " + std::to_string(counter()));
    std::function<int32_t(int32_t)> addTwo = functions_program::twice(addOne);
    int32_t value = addTwo(5);
    core::Console::log(temper_globals::console.value, "twice " + std::to_string(value) + " " + std::to_string(functions_program::applyTo(addOne, 1)));
    int32_t value2 = functions_program::applyTo(Scaler::triple, 4);
    core::Console::log(temper_globals::console.value, "triple " + std::to_string(value2) + " " + std::to_string(functions_program::twice(Scaler::triple)(2)));
    std::shared_ptr<std::vector<int32_t>> ns = core::List::make<int32_t>(5, 2, 8, 1, 4);
    std::shared_ptr<std::vector<int32_t>> doubled = core::List::map(ns, [](int32_t n) -> int32_t { return core::Int::mul(n, 2); });
    std::shared_ptr<std::vector<int32_t>> big = core::List::filter(doubled, [](int32_t n) -> bool { return n > 4; });
    core::Console::log(temper_globals::console.value, core::List::join(big, TEMPER_STR(", "), [](int32_t n) -> core::Str { return std::to_string(n); }));
    std::shared_ptr<std::vector<int32_t>> order = core::List::sorted(ns, [](int32_t a, int32_t b) -> int32_t { return core::Int::sub(a, b); });
    core::Console::log(temper_globals::console.value, core::List::join(order, TEMPER_STR(" "), [](int32_t n) -> core::Str { return std::to_string(n); }));
    int32_t total = 0;
    {
      int32_t length = core::List::length<int32_t>(ns);
      int32_t i2 = 0;
      while (i2 < length) {
        int32_t n = core::List::get<int32_t>(ns, i2);
        i2 = core::Int::add(i2, 1);
        total = core::Int::add(total, n);
      }
    }
    core::Console::log(temper_globals::console.value, "total " + std::to_string(total));
    std::shared_ptr<Button> button = Button::make(TEMPER_STR("hits"), makeCounter(0));
    button->press();
    core::Console::log(temper_globals::console.value, button->press());
    std::shared_ptr<std::vector<std::function<int32_t()>>> makers = core::ListBuilder::make<std::function<int32_t()>>();
    {
      int32_t i = 0;
      while (i < 3) {
        int32_t k = core::Int::mul(i, 10);
        core::ListBuilder::add<std::function<int32_t()>>(makers, [=]() -> int32_t { return k; });
        i = core::Int::add(i, 1);
      }
    }
    core::Console::log(temper_globals::console.value, core::List::join(makers, TEMPER_STR(" "), [](std::function<int32_t()> c) -> core::Str { return std::to_string(c()); }));
    auto fn8 = []() -> std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> {
      std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
      std::shared_ptr<std::shared_ptr<std::vector<std::function<int32_t()>>>> fs = std::make_shared<std::shared_ptr<std::vector<std::function<int32_t()>>>>(nullptr);
      std::shared_ptr<int32_t> i = std::make_shared<int32_t>(0);
      std::shared_ptr<int32_t> j = std::make_shared<int32_t>(0);
      auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>>) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
        while (true) {
          int32_t caseIndexLocal = (*caseIndex);
          *caseIndex = -1;
          switch (caseIndexLocal) {
            case 0:
              *fs = core::ListBuilder::make<std::function<int32_t()>>();
              *i = 0;
              *caseIndex = 1;
              break;
            case 1:
              if ((*i) < 3) {
                *caseIndex = 2;
              } else {
                *caseIndex = 3;
              }
              break;
            case 2: {
              *j = core::Int::mul((*i), 10);
              std::shared_ptr<std::vector<std::function<int32_t()>>> listBuilder = (*fs);
              int32_t j22 = (*j);
              core::ListBuilder::add<std::function<int32_t()>>(listBuilder, [=]() -> int32_t { return j22; });
              *caseIndex = 4;
              return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
            }
            case 3:
              core::Console::log(temper_globals::console.value, core::List::join((*fs), TEMPER_STR(" "), [](std::function<int32_t()> c) -> core::Str { return std::to_string(c()); }));
              *caseIndex = -1;
              return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
            case 4: {
              std::shared_ptr<std::vector<std::function<int32_t()>>> value3 = (*fs);
              int32_t j2 = (*j);
              core::ListBuilder::add<std::function<int32_t()>>(value3, [=]() -> int32_t { return core::Int::add(j2, 1); });
              *i = core::Int::add((*i), 1);
              *caseIndex = 1;
              break;
            }
            default:
              return core::doneResult();
          }
        }
      };
      return core::safe_adapt_generator_fn<std::shared_ptr<core::Empty>>(convertedCoroutine);
    };
    functions_program::drive(5, fn8);
    core::Console::log(temper_globals::console.value, "sum " + std::to_string(functions_program::sumFrom(ns, 4)) + " doubles " + std::to_string(functions_program::doubles(TEMPER_STR("bookkeeper"))));
    int32_t value4 = climb(0);
    core::Console::log(temper_globals::console.value, "climb " + std::to_string(value4) + " " + std::to_string(climb(1)));
    std::shared_ptr<Gate> gate = Gate::make();
    bool boolean = gate->allows(0);
    core::Console::log(temper_globals::console.value, "gate " + core::Boolean::toString(boolean) + " " + std::to_string(gate->passed(4)));
    gate->check = [](int32_t x) -> int32_t { return core::Int::sub(x, 1); };
    bool value5 = gate->allows(1);
    bool value6 = gate->allows(2);
    core::Console::log(temper_globals::console.value, "gate " + core::Boolean::toString(value5) + " " + core::Boolean::toString(value6) + " " + std::to_string(gate->passed(4)));
    core::NullableParam<int32_t> none = gate->allows(0) ? core::NullableParam<int32_t>(3) : nullptr;
    core::Console::log(temper_globals::console.value, core::Boolean::toString(functions_program::stringIsPalindrome(TEMPER_STR("step on no pets"))) + " " + core::Boolean::toString(functions_program::stringIsPalindrome(TEMPER_STR("steps"))));
    core::Console::log(temper_globals::console.value, std::to_string(functions_program::intOrZero(none)) + " " + std::to_string(functions_program::listFirstOr<int32_t>(core::List::make<int32_t>(4, 5), 0)) + " " + functions_program::listFirstOr<core::Str>(core::List::make<core::Str>(TEMPER_STR("x")), TEMPER_STR("-")));
    core::Console::log(temper_globals::console.value, functions_program::stringPadded(TEMPER_STR("ab"), 2) + " " + functions_program::stringPadded(TEMPER_STR("ab"), 1, TEMPER_STR("*")));
    int32_t value7 = gate->passed(4);
    core::Console::log(temper_globals::console.value, std::to_string(value7) + " " + functions_program::gatePassed(gate, TEMPER_STR("gate")));
  });
}

}  // namespace functions_program
