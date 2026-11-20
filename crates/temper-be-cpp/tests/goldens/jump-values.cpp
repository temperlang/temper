// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace jump_values {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace jump_values

// init.cpp
#include <jump-values/init.hpp>

#include <mutex>

namespace jump_values {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;
static core::GlobalSlot<int32_t> steps;

}  // namespace temper_globals

static std::mutex moduleMutex;

static int32_t step(int32_t n) {
  {
    std::lock_guard<std::mutex> lock(moduleMutex);
    temper_globals::steps.value = core::Int::add(temper_globals::steps.value, 1);
  }
  core::Console::log(temper_globals::console.value, "step " + std::to_string(n));
  return n;
}

static int32_t pair(int32_t a, int32_t b) {
  return core::Int::add(core::Int::mul(a, 10), b);
}

static int32_t parse(core::Str const & s) {
  return core::String::toInt32(s);
}

static core::NullableParam<int32_t> next(core::NullableParam<int32_t> y) {
  int32_t x;
  if (y == nullptr) {
    return nullptr;
  } else {
    x = y.value;
  }
  return core::Int::add(x, 1);
}

static int32_t sumPresent(std::shared_ptr<std::vector<core::NullableParam<int32_t>>> const & ys) {
  int32_t sum = 0;
  {
    int32_t i = 0;
    while (i < core::List::length<core::NullableParam<int32_t>>(ys)) {
      int32_t t;
      core::NullableParam<int32_t> subject = core::List::get<core::NullableParam<int32_t>>(ys, i);
      if (subject == nullptr) {
        {
          i = core::Int::add(i, 1);
          continue;
        }
      } else {
        t = subject.value;
      }
      sum = core::Int::add(sum, t);
      i = core::Int::add(i, 1);
    }
  }
  return sum;
}

static int32_t sumUntilBad(std::shared_ptr<std::vector<core::Str>> const & words) {
  int32_t sum = 0;
  int32_t i = 0;
  while (i < core::List::length<core::Str>(words)) {
    int32_t t;
    core::NullableParam<int32_t> held = nullptr;
    try {
      held = jump_values::parse(core::List::get<core::Str>(words, i));
    } catch (core::TemperBubble const &) {
    }
    if (held == nullptr) {
      break;
    } else {
      t = held.value;
    }
    sum = core::Int::add(sum, t);
    i = core::Int::add(i, 1);
  }
  return sum;
}

static int32_t parsedOr(core::Str const & s) {
  int32_t t;
  core::NullableParam<int32_t> held = nullptr;
  try {
    held = jump_values::parse(s);
  } catch (core::TemperBubble const &) {
  }
  if (held == nullptr) {
    return -1;
  } else {
    t = held.value;
  }
  return t;
}

static core::NullableParam<int32_t> after(int32_t i) {
  if (i < 3) {
    return core::Int::add(i, 1);
  } else {
    return nullptr;
  }
}

static core::Str stepped() {
  core::Str seen = core::Str();
  {
    int32_t i = 0;
    while (i < 6) {
      if (i == 1) {
        {
          core::NullableParam<int32_t> subject = after(i);
          i = subject == nullptr ? 9 : subject.value;
          continue;
        }
      }
      seen = seen + std::to_string(i);
      core::NullableParam<int32_t> subject2 = after(i);
      i = subject2 == nullptr ? 9 : subject2.value;
    }
  }
  return seen;
}

static int32_t paired(core::NullableParam<int32_t> y) {
  int32_t int32 = step(1);
  int32_t value = int32;
  int32_t t;
  if (y == nullptr) {
    return -2;
  } else {
    t = y.value;
  }
  return pair(value, t);
}

static std::shared_ptr<std::vector<int32_t>> listed(core::NullableParam<int32_t> y, core::NullableParam<int32_t> z) {
  int32_t t;
  if (y == nullptr) {
    return core::List::make<int32_t>();
  } else {
    t = y.value;
  }
  int32_t t2;
  if (z == nullptr) {
    return core::List::make<int32_t>(9);
  } else {
    t2 = z.value;
  }
  return core::List::make<int32_t>(1, t, 3, t2);
}

static core::Str shown(core::NullableParam<int32_t> y) {
  int32_t t;
  if (y == nullptr) {
    return TEMPER_STR("none");
  } else {
    t = y.value;
  }
  return "y=" + std::to_string(t);
}

static core::Str both(bool t, core::NullableParam<int32_t> y) {
  bool ok;
  if (t && y != nullptr) {
    ok = true;
  } else {
    return TEMPER_STR("no");
  }
  return "ok " + core::Boolean::toString(ok);
}

static core::Str sign(int32_t n) {
  int32_t t;
  if (n > 0) {
    t = 1;
  } else if (n < 0) {
    t = -1;
  } else {
    return TEMPER_STR("zero");
  }
  return std::to_string(pair(0, t));
}

static void firstWord(core::NullableParam<core::Str> text) {
  core::Str t;
  if (text == nullptr) {
    return;
  } else {
    t = text.value;
  }
  core::Console::log(temper_globals::console.value, "first " + core::List::get<core::Str>(core::String::split(t, TEMPER_STR(" ")), 0));
}

static int32_t early() {
  return 7;
}

static int32_t partly() {
  step(2);
  return step(3);
}

static core::Str doJumps(std::shared_ptr<std::vector<core::NullableParam<int32_t>>> const & ys) {
  core::Str seen = core::Str();
  {
    int32_t i = 0;
    while (i < core::List::length<core::NullableParam<int32_t>>(ys)) {
      int32_t v;
      core::NullableParam<int32_t> subject = core::List::get<core::NullableParam<int32_t>>(ys, i);
      if (subject == nullptr) {
        seen = seen + "a";
        if (i == 1) {
          {
            i = core::Int::add(i, 1);
            continue;
          }
        }
        seen = seen + "b";
        break;
      } else {
        v = subject.value;
      }
      seen = seen + std::to_string(v);
      i = core::Int::add(i, 1);
    }
  }
  return seen;
}

static core::Str doReturn(core::NullableParam<int32_t> y, int32_t z) {
  int32_t int32;
  if (y == nullptr) {
    if (z > 0) {
      if (z > 5) {
        return TEMPER_STR("big");
      }
      return TEMPER_STR("pos");
    }
    int32 = 0;
  } else {
    int32 = y.value;
  }
  int32_t v = int32;
  return "v" + std::to_string(v);
}

static int32_t trio(int32_t a, int32_t b, int32_t c) {
  return core::Int::add(core::Int::add(a, b), c);
}

static int32_t ordered(std::shared_ptr<std::vector<core::NullableParam<int32_t>>> const & ys) {
  int32_t sum = 0;
  {
    int32_t i = 0;
    while (i < core::List::length<core::NullableParam<int32_t>>(ys)) {
      int32_t int32 = step(core::Int::add(10, i));
      int32_t value = int32;
      int32_t t;
      core::NullableParam<int32_t> subject = core::List::get<core::NullableParam<int32_t>>(ys, i);
      if (subject == nullptr) {
        step(core::Int::add(20, i));
        {
          i = core::Int::add(i, 1);
          continue;
        }
      } else {
        t = subject.value;
      }
      int32_t value2 = t;
      sum = core::Int::add(sum, trio(value, value2, step(core::Int::add(30, i))));
      i = core::Int::add(i, 1);
    }
  }
  return sum;
}

static core::Str nested(std::shared_ptr<std::vector<core::NullableParam<int32_t>>> const & xs, std::shared_ptr<std::vector<core::NullableParam<int32_t>>> const & ys) {
  core::Str seen = core::Str();
  {
    {
      int32_t i = 0;
      while (i < core::List::length<core::NullableParam<int32_t>>(xs)) {
        int32_t v;
        core::NullableParam<int32_t> subject = core::List::get<core::NullableParam<int32_t>>(xs, i);
        if (subject == nullptr) {
          core::NullableParam<int32_t> subject2 = core::List::get<core::NullableParam<int32_t>>(ys, i);
          if (subject2 == nullptr) {
            break;
          } else {
            v = subject2.value;
          }
        } else {
          v = subject.value;
        }
        seen = seen + std::to_string(v);
        i = core::Int::add(i, 1);
      }
    }
  }
  return seen;
}

static core::Str gridded(std::shared_ptr<std::vector<std::shared_ptr<std::vector<core::NullableParam<int32_t>>>>> const & rows) {
  core::Str seen = core::Str();
  {
    {
      int32_t i = 0;
      while (i < core::List::length<std::shared_ptr<std::vector<core::NullableParam<int32_t>>>>(rows)) {
        {
          std::shared_ptr<std::vector<core::NullableParam<int32_t>>> row = core::List::get<std::shared_ptr<std::vector<core::NullableParam<int32_t>>>>(rows, i);
          {
            int32_t j = 0;
            while (j < core::List::length<core::NullableParam<int32_t>>(row)) {
              int32_t v;
              core::NullableParam<int32_t> subject = core::List::get<core::NullableParam<int32_t>>(row, j);
              if (subject == nullptr) {
                goto outerEnd;
              } else {
                v = subject.value;
              }
              int32_t w;
              core::NullableParam<int32_t> subject2 = v < 0 ? nullptr : core::NullableParam<int32_t>(v);
              if (subject2 == nullptr) {
                seen = seen + "-";
                {
                  i = core::Int::add(i, 1);
                  goto outerNext;
                }
              } else {
                w = subject2.value;
              }
              seen = seen + std::to_string(w);
              j = core::Int::add(j, 1);
            }
          }
          seen = seen + "|";
          i = core::Int::add(i, 1);
        }
        outerNext:;
      }
      outerEnd:;
    }
  }
  return seen;
}

static core::Str firstBig(std::shared_ptr<std::vector<std::shared_ptr<std::vector<core::NullableParam<int32_t>>>>> const & rows, std::shared_ptr<std::vector<core::NullableParam<int32_t>>> const & keys) {
  core::Str seen = core::Str();
  {
    {
      int32_t i = 0;
      while (i < core::List::length<std::shared_ptr<std::vector<core::NullableParam<int32_t>>>>(rows)) {
        {
          core::NullableParam<int32_t> subject = core::List::get<core::NullableParam<int32_t>>(keys, i);
          int32_t int32;
          if (subject == nullptr) {
            std::shared_ptr<std::vector<core::NullableParam<int32_t>>> row = core::List::get<std::shared_ptr<std::vector<core::NullableParam<int32_t>>>>(rows, i);
            int32_t first = -1;
            {
              int32_t j = 0;
              while (j < core::List::length<core::NullableParam<int32_t>>(row)) {
                int32_t v;
                core::NullableParam<int32_t> subject2 = core::List::get<core::NullableParam<int32_t>>(row, j);
                if (subject2 == nullptr) {
                  {
                    i = core::Int::add(i, 1);
                    goto outerNext;
                  }
                } else {
                  v = subject2.value;
                }
                if (v > 5) {
                  goto outerEnd;
                }
                if (v > 2) {
                  first = v;
                  break;
                }
                j = core::Int::add(j, 1);
              }
            }
            int32 = first;
          } else {
            int32 = subject.value;
          }
          int32_t k = int32;
          seen = seen + std::to_string(k) + ",";
          i = core::Int::add(i, 1);
        }
        outerNext:;
      }
      outerEnd:;
    }
  }
  return seen;
}

static core::Str viaOrelse(std::shared_ptr<std::vector<core::Str>> const & words) {
  core::Str seen = core::Str();
  {
    int32_t i = 0;
    while (i < core::List::length<core::Str>(words)) {
      int32_t n;
      core::NullableParam<int32_t> held = nullptr;
      try {
        held = jump_values::parse(core::List::get<core::Str>(words, i));
      } catch (core::TemperBubble const &) {
      }
      if (held == nullptr) {
        seen = seen + "!";
        if (i > 2) {
          break;
        }
        {
          i = core::Int::add(i, 1);
          continue;
        }
      } else {
        n = held.value;
      }
      seen = seen + std::to_string(n);
      i = core::Int::add(i, 1);
    }
  }
  return seen;
}

static core::Str viaIf(int32_t n) {
  core::Str string;
  if (n > 0) {
    string = TEMPER_STR("pos");
  } else {
    if (n < -5) {
      return TEMPER_STR("low");
    }
    string = TEMPER_STR("neg");
  }
  core::Str s = string;
  return "s=" + s;
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::steps.construct();
    temper_globals::console.value = core::Console::get_console();
    temper_globals::steps.value = 0;
    core::NullableParam<int32_t> part = jump_values::next(1);
    core::NullableParam<int32_t> part2 = jump_values::next(nullptr);
    core::Console::log(temper_globals::console.value, (part == nullptr ? TEMPER_STR("null") : core::Str(std::to_string(part.value))) + " " + (part2 == nullptr ? TEMPER_STR("null") : core::Str(std::to_string(part2.value))));
    int32_t value = jump_values::sumPresent(core::List::make<core::NullableParam<int32_t>>(1, nullptr, 3));
    core::Console::log(temper_globals::console.value, std::to_string(value) + " " + std::to_string(jump_values::sumUntilBad(core::List::make<core::Str>(TEMPER_STR("1"), TEMPER_STR("2"), TEMPER_STR("x"), TEMPER_STR("4")))));
    int32_t value2 = jump_values::parsedOr(TEMPER_STR("12"));
    core::Console::log(temper_globals::console.value, std::to_string(value2) + " " + std::to_string(jump_values::parsedOr(TEMPER_STR("x"))) + " " + stepped());
    int32_t value3 = jump_values::paired(3);
    core::Console::log(temper_globals::console.value, std::to_string(value3) + " " + std::to_string(jump_values::paired(nullptr)));
    core::Console::log(temper_globals::console.value, std::to_string(core::List::length<int32_t>(jump_values::listed(2, 4))) + " " + std::to_string(core::List::length<int32_t>(jump_values::listed(nullptr, 4))) + " " + std::to_string(core::List::get<int32_t>(jump_values::listed(2, nullptr), 0)));
    core::Console::log(temper_globals::console.value, jump_values::shown(5) + " " + jump_values::shown(nullptr));
    core::Console::log(temper_globals::console.value, jump_values::both(true, 1) + " " + jump_values::both(true, nullptr) + " " + jump_values::both(false, 1));
    core::Console::log(temper_globals::console.value, sign(4) + " " + sign(-4) + " " + sign(0));
    jump_values::firstWord(TEMPER_STR("hello there"));
    jump_values::firstWord(nullptr);
    core::Console::log(temper_globals::console.value, jump_values::doJumps(core::List::make<core::NullableParam<int32_t>>(1, nullptr, 2, nullptr, 3)) + " " + jump_values::doReturn(1, 0) + " " + jump_values::doReturn(nullptr, 9) + " " + jump_values::doReturn(nullptr, 1) + " " + jump_values::doReturn(nullptr, 0));
    core::Console::log(temper_globals::console.value, std::to_string(jump_values::ordered(core::List::make<core::NullableParam<int32_t>>(1, nullptr))));
    core::Str value4 = jump_values::nested(core::List::make<core::NullableParam<int32_t>>(1, nullptr, nullptr, 4), core::List::make<core::NullableParam<int32_t>>(0, 7, nullptr, 0));
    core::Console::log(temper_globals::console.value, value4 + " " + jump_values::viaOrelse(core::List::make<core::Str>(TEMPER_STR("1"), TEMPER_STR("x"), TEMPER_STR("2"), TEMPER_STR("y"), TEMPER_STR("z"), TEMPER_STR("3"))));
    core::Console::log(temper_globals::console.value, jump_values::gridded(core::List::make<std::shared_ptr<std::vector<core::NullableParam<int32_t>>>>(core::List::make<core::NullableParam<int32_t>>(1, 2), core::List::make<core::NullableParam<int32_t>>(3, -1, 4), core::List::make<core::NullableParam<int32_t>>(5, nullptr, 6), core::List::make<core::NullableParam<int32_t>>(7))) + " " + jump_values::firstBig(core::List::make<std::shared_ptr<std::vector<core::NullableParam<int32_t>>>>(core::List::make<core::NullableParam<int32_t>>(1, 3), core::List::make<core::NullableParam<int32_t>>(nullptr, 1), core::List::make<core::NullableParam<int32_t>>(4), core::List::make<core::NullableParam<int32_t>>(9), core::List::make<core::NullableParam<int32_t>>(1)), core::List::make<core::NullableParam<int32_t>>(7, nullptr, nullptr, nullptr, nullptr)));
    core::Console::log(temper_globals::console.value, viaIf(1) + " " + viaIf(-1) + " " + viaIf(-9));
    int32_t value5 = partly();
    core::Console::log(temper_globals::console.value, std::to_string(early()) + " " + std::to_string(value5) + " " + std::to_string(temper_globals::steps.value));
  });
}

}  // namespace jump_values
