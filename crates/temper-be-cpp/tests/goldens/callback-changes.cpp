// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace callback_changes {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace callback_changes

// init.cpp
#include <callback-changes/init.hpp>

namespace callback_changes {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<std::vector<int32_t>>> f;
static core::GlobalSlot<int32_t> peeks;
static core::GlobalSlot<std::shared_ptr<std::vector<int32_t>>> s;

}  // namespace temper_globals

static core::Str show(std::shared_ptr<std::vector<int32_t>> const & ls) {
  return core::List::join(ls, TEMPER_STR(","), [](int32_t n) -> core::Str { return std::to_string(n); });
}

static std::shared_ptr<std::vector<core::Str>> words() {
  std::shared_ptr<std::vector<core::Str>> w = core::ListBuilder::make<core::Str>();
  {
    int32_t i = 0;
    while (i < 3) {
      core::ListBuilder::add<core::Str>(w, "w" + std::to_string(i));
      i = core::Int::add(i, 1);
    }
  }
  return w;
}

static int32_t reduceFromGrowing() {
  std::shared_ptr<std::vector<int32_t>> g = core::List::toListBuilder<int32_t>(core::List::make<int32_t>(1, 2));
  auto fn = [=](int32_t sum, int32_t n) -> int32_t {
    if (core::List::length<int32_t>(g) < 4) {
      core::ListBuilder::add<int32_t>(g, 9);
    }
    return core::Int::add(core::Int::mul(sum, 10), n);
  };
  return core::List::reduceFrom(g, 0, fn);
}

static int32_t mapShrinking() {
  std::shared_ptr<std::vector<core::Str>> w = words();
  auto fn = [=](core::Str s) -> core::Str {
    if (core::List::length<core::Str>(w) > 1) {
      core::ListBuilder::removeLast<core::Str>(w);
    }
    return s;
  };
  return core::List::length<core::Str>(core::List::map(w, fn));
}

static int32_t reduceShrinking() {
  std::shared_ptr<std::vector<core::Str>> w = words();
  auto fn = [=](core::Str all, core::Str) -> core::Str {
    if (core::List::length<core::Str>(w) > 1) {
      core::ListBuilder::removeLast<core::Str>(w);
    }
    return all;
  };
  core::Str all2 = core::List::reduce(w, fn);
  if (all2.empty()) {
    return 0;
  } else {
    return 1;
  }
}

static int32_t filterGrowing() {
  std::shared_ptr<std::vector<int32_t>> g = core::List::toListBuilder<int32_t>(core::List::make<int32_t>(1, 2));
  auto fn = [=](int32_t) -> bool {
    if (core::List::length<int32_t>(g) < 4) {
      core::ListBuilder::add<int32_t>(g, 9);
    }
    return true;
  };
  return core::List::length<int32_t>(core::List::filter(g, fn));
}

static int32_t joinGrowing() {
  std::shared_ptr<std::vector<int32_t>> g = core::List::toListBuilder<int32_t>(core::List::make<int32_t>(1, 2));
  auto fn = [=](int32_t n) -> core::Str {
    if (core::List::length<int32_t>(g) < 4) {
      core::ListBuilder::add<int32_t>(g, 9);
    }
    return std::to_string(n);
  };
  core::Str joined = core::List::join(g, TEMPER_STR(","), fn);
  if (joined.empty()) {
    return 0;
  } else {
    return 1;
  }
}

static std::shared_ptr<core::Mapped::Ordered<core::Str, int32_t>> letters() {
  std::shared_ptr<core::Mapped::Ordered<core::Str, int32_t>> m = core::Map::make<core::Str, int32_t>();
  core::MapBuilder::set<core::Str, int32_t>(m, "a" + std::to_string(1), 1);
  core::MapBuilder::set<core::Str, int32_t>(m, "b" + std::to_string(2), 2);
  core::MapBuilder::set<core::Str, int32_t>(m, "c" + std::to_string(3), 3);
  return m;
}

static int32_t forEachRemoving() {
  std::shared_ptr<core::Mapped::Ordered<core::Str, int32_t>> m = letters();
  std::shared_ptr<int32_t> sum = std::make_shared<int32_t>(0);
  auto fn = [=](core::Str, int32_t v) -> void {
    if (core::Mapped::has<core::Str, int32_t>(m, TEMPER_STR("c3"))) {
      try {
        core::MapBuilder::remove<core::Str, int32_t>(m, TEMPER_STR("c3"));
      } catch (core::TemperBubble const &) {
      }
    }
    *sum = core::Int::add((*sum), v);
  };
  core::Mapped::forEach(m, fn);
  return (*sum);
}

static int32_t forEachAdding() {
  std::shared_ptr<core::Mapped::Ordered<core::Str, int32_t>> m = letters();
  std::shared_ptr<int32_t> count = std::make_shared<int32_t>(0);
  auto fn = [=](core::Str, int32_t) -> void {
    *count = core::Int::add((*count), 1);
    if (core::Mapped::length<core::Str, int32_t>(m) < 30) {
      core::MapBuilder::set<core::Str, int32_t>(m, "k" + std::to_string(core::Mapped::length<core::Str, int32_t>(m)), 1);
    }
  };
  core::Mapped::forEach(m, fn);
  return (*count);
}

static int32_t toListWithRemoving() {
  std::shared_ptr<core::Mapped::Ordered<core::Str, int32_t>> m = letters();
  auto fn = [=](core::Str, int32_t v) -> int32_t {
    if (core::Mapped::has<core::Str, int32_t>(m, TEMPER_STR("c3"))) {
      try {
        core::MapBuilder::remove<core::Str, int32_t>(m, TEMPER_STR("c3"));
      } catch (core::TemperBubble const &) {
      }
    }
    return v;
  };
  return core::List::length<int32_t>(core::Mapped::toListWith(m, fn));
}

void global_init_init() {
  core::init_once([] {
    temper_globals::f.construct();
    temper_globals::peeks.construct();
    temper_globals::s.construct();
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    std::shared_ptr<std::vector<int32_t>> b = core::List::toListBuilder<int32_t>(core::List::make<int32_t>(1, 2));
    core::ListBuilder::addAll<int32_t>(b, b);
    std::shared_ptr<std::vector<int32_t>> c = core::List::toListBuilder<int32_t>(core::List::make<int32_t>(1, 2));
    core::ListBuilder::addAll<int32_t>(c, c, 1);
    std::shared_ptr<std::vector<int32_t>> d = core::List::toListBuilder<int32_t>(core::List::make<int32_t>(1, 2, 3));
    std::shared_ptr<std::vector<int32_t>> removed = core::ListBuilder::splice<int32_t>(d, 1, 1, d);
    core::Str string = callback_changes::show(b);
    core::Str value = callback_changes::show(c);
    core::Str value2 = callback_changes::show(d);
    core::Console::log(console, "self " + string + " " + value + " " + value2 + " " + callback_changes::show(removed));
    std::shared_ptr<std::vector<int32_t>> e = core::List::toListBuilder<int32_t>(core::List::make<int32_t>(1, 2));
    std::shared_ptr<std::vector<int32_t>> kept = core::List::filter(e, [](int32_t n) -> bool { return n > 0; });
    core::ListBuilder::add<int32_t>(e, 3);
    core::Str value3 = callback_changes::show(kept);
    core::Console::log(console, "filter " + value3 + " " + callback_changes::show(e));
    temper_globals::f.value = core::List::toListBuilder<int32_t>(core::List::make<int32_t>(3, 1, 2));
    temper_globals::peeks.value = 0;
    auto fn2 = [](int32_t a, int32_t z) -> int32_t {
      temper_globals::peeks.value = core::Int::add(temper_globals::peeks.value, core::List::length<int32_t>(temper_globals::f.value));
      return core::Int::sub(a, z);
    };
    core::ListBuilder::sort(temper_globals::f.value, fn2);
    core::Console::log(console, "peeking " + callback_changes::show(temper_globals::f.value));
    int32_t value4;
    try {
      value4 = reduceFromGrowing();
    } catch (core::TemperBubble const &) {
      value4 = -1;
    }
    int32_t int32 = value4;
    int32_t value5;
    try {
      value5 = mapShrinking();
    } catch (core::TemperBubble const &) {
      value5 = -1;
    }
    int32_t value6 = value5;
    int32_t value7;
    try {
      value7 = reduceShrinking();
    } catch (core::TemperBubble const &) {
      value7 = -1;
    }
    int32_t value8 = value7;
    int32_t value9;
    try {
      value9 = filterGrowing();
    } catch (core::TemperBubble const &) {
      value9 = -1;
    }
    int32_t value10 = value9;
    int32_t value11;
    try {
      value11 = joinGrowing();
    } catch (core::TemperBubble const &) {
      value11 = -1;
    }
    int32_t value12 = value11;
    int32_t value13;
    try {
      value13 = forEachRemoving();
    } catch (core::TemperBubble const &) {
      value13 = -1;
    }
    int32_t value14 = value13;
    int32_t value15;
    try {
      value15 = forEachAdding();
    } catch (core::TemperBubble const &) {
      value15 = -1;
    }
    int32_t value16 = value15;
    int32_t value17;
    try {
      value17 = toListWithRemoving();
    } catch (core::TemperBubble const &) {
      value17 = -1;
    }
    std::shared_ptr<std::vector<int32_t>> walked = core::List::make<int32_t>(int32, value6, value8, value10, value12, value14, value16, value17);
    core::Console::log(console, "walks " + std::to_string(core::List::length<int32_t>(walked)));
    temper_globals::s.value = core::List::toListBuilder<int32_t>(core::List::make<int32_t>(3, 1, 2));
    auto fn3 = [](int32_t a, int32_t z) -> int32_t {
      if (core::List::length<int32_t>(temper_globals::s.value) < 40) {
        core::ListBuilder::add<int32_t>(temper_globals::s.value, 0);
      }
      return core::Int::sub(a, z);
    };
    core::ListBuilder::sort(temper_globals::s.value, fn3);
    core::Console::log(console, "sorted " + std::to_string(core::List::length<int32_t>(temper_globals::s.value)));
  });
}

}  // namespace callback_changes
