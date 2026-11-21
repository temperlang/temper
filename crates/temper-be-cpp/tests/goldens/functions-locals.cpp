// init.hpp
#pragma once

#include <temper-core/any_value.hpp>
#include <temper-core/list.hpp>
#include <temper-core/int.hpp>
#include <temper-core/list_builder.hpp>
#include <cstdint>
#include <functional>
#include <memory>
#include <vector>

namespace locals {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

template <class T>
struct Handler;
template <class T>
struct Hub;

template <class T>
struct Handler : public virtual temper::core::AnyValueBase {
  virtual void apply(T item) const = 0;
};

template <class T>
struct Hub : public std::enable_shared_from_this<Hub<T>> {
  std::shared_ptr<std::vector<std::function<void(T)>>> handlers;
  static std::shared_ptr<Hub<T>> make();
  void onAction(std::function<void(T)> const & handler) const;
  void actionPerformed(T item) const;
};

void thresh(std::shared_ptr<std::vector<int32_t>> const & values, int32_t value);
void thresh2(std::shared_ptr<std::vector<int32_t>> const & values, int32_t value);

template <class T>
std::shared_ptr<Hub<T>> Hub<T>::make() {
  std::shared_ptr<Hub<T>> self = std::make_shared<Hub<T>>();
  self->handlers = temper::core::ListBuilder::make<std::function<void(T)>>();
  return self;
}

template <class T>
void Hub<T>::onAction(std::function<void(T)> const & handler) const {
  temper::core::ListBuilder::add<std::function<void(T)>>(this->handlers, handler);
}

template <class T>
void Hub<T>::actionPerformed(T item) const {
  {
    int32_t i = 0;
    while (i < temper::core::List::length<std::function<void(T)>>(this->handlers)) {
      temper::core::List::get<std::function<void(T)>>(this->handlers, i)(item);
      i = temper::core::Int::add(i, 1);
    }
  }
}

}  // namespace locals

// init.cpp
#include <locals/init.hpp>

#include <temper-core/str.hpp>
#include <temper-core/module_init.hpp>
#include <temper-core/console.hpp>
#include <string>

namespace locals {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;

}  // namespace temper_globals

static core::Str show(std::shared_ptr<std::vector<int32_t>> const & x) {
  std::shared_ptr<std::vector<core::Str>> s = core::ListBuilder::make<core::Str>();
  core::ListBuilder::add<core::Str>(s, TEMPER_STR("["));
  core::Str c = core::Str();
  {
    int32_t i = 0;
    while (i < core::List::length<int32_t>(x)) {
      core::ListBuilder::add<core::Str>(s, c);
      core::ListBuilder::add<core::Str>(s, std::to_string(core::List::get<int32_t>(x, i)));
      c = TEMPER_STR(", ");
      i = core::Int::add(i, 1);
    }
  }
  core::ListBuilder::add<core::Str>(s, TEMPER_STR("]"));
  return core::List::join(s, core::Str(), [](core::Str v) -> core::Str { return v; });
}

template <class T>
static T nestBlock(std::function<T()> const & b) {
  return b();
}

static void nestBlockVoid(std::function<void()> const & b) {
  b();
}

static void constantCapture(std::shared_ptr<std::vector<int32_t>> const & x, int32_t a) {
  int32_t b = core::Int::mul(a, 1);
  std::shared_ptr<std::vector<int32_t>> out = core::List::map(x, [=](int32_t n) -> int32_t { return core::Int::add(n, b); });
  core::Console::log(temper_globals::console.value, "constantCapture = " + locals::show(out));
}

static void constantCaptureNested(std::shared_ptr<std::vector<int32_t>> const & x, int32_t a) {
  int32_t b = core::Int::mul(a, 1);
  std::shared_ptr<std::vector<int32_t>> out = locals::nestBlock<std::shared_ptr<std::vector<int32_t>>>([=]() -> std::shared_ptr<std::vector<int32_t>> { return core::List::map(x, [=](int32_t n) -> int32_t { return core::Int::add(n, b); }); });
  core::Console::log(temper_globals::console.value, "constantCaptureNested = " + locals::show(out));
}

static void mutableCapture(std::shared_ptr<std::vector<int32_t>> const & x) {
  std::shared_ptr<int32_t> sum = std::make_shared<int32_t>(0);
  auto fn = [=](int32_t n) -> int32_t {
    *sum = core::Int::add((*sum), n);
    return (*sum);
  };
  std::shared_ptr<std::vector<int32_t>> out = core::List::map(x, fn);
  core::Str string = locals::show(out);
  core::Console::log(temper_globals::console.value, "mutableCapture = " + string + "; sum=" + std::to_string((*sum)));
}

static void mutableCaptureNested(std::shared_ptr<std::vector<int32_t>> const & x) {
  std::shared_ptr<int32_t> sum = std::make_shared<int32_t>(0);
  auto fn = [=]() -> std::shared_ptr<std::vector<int32_t>> {
    auto fn2 = [=](int32_t n) -> int32_t {
      *sum = core::Int::add((*sum), n);
      return (*sum);
    };
    return core::List::map(x, fn2);
  };
  std::shared_ptr<std::vector<int32_t>> out = locals::nestBlock<std::shared_ptr<std::vector<int32_t>>>(fn);
  core::Str string = locals::show(out);
  core::Console::log(temper_globals::console.value, "mutableCaptureNested = " + string + "; sum=" + std::to_string((*sum)));
}

void thresh(std::shared_ptr<std::vector<int32_t>> const & values, int32_t value) {
  global_init_init();
  std::shared_ptr<int32_t> value2 = std::make_shared<int32_t>(value);
  *value2 = core::Int::add((*value2), 1);
  std::shared_ptr<std::vector<int32_t>> result = core::List::filter(values, [=](int32_t it) -> bool { return it > (*value2); });
  core::Console::log(temper_globals::console.value, "thresh length = " + std::to_string(core::List::length<int32_t>(result)));
}

void thresh2(std::shared_ptr<std::vector<int32_t>> const & values, int32_t value) {
  global_init_init();
  std::shared_ptr<int32_t> value2 = std::make_shared<int32_t>(value);
  *value2 = core::Int::add((*value2), 1);
  std::shared_ptr<std::vector<int32_t>> result = core::List::filter(values, [=](int32_t it) -> bool { return it > (*value2); });
  core::Console::log(temper_globals::console.value, "thresh2 length = " + std::to_string(core::List::length<int32_t>(result)));
}

static void selfRecursion(std::shared_ptr<std::vector<int32_t>> const & x) {
  struct MapperGroup {
    std::shared_ptr<std::vector<int32_t>> x;
    void mapper(int32_t i, std::shared_ptr<std::vector<int32_t>> out) const {
      if (i < core::List::length<int32_t>(x)) {
        core::ListBuilder::add<int32_t>(out, core::Int::mul(core::List::get<int32_t>(x, i), 2));
        mapper(core::Int::add(i, 1), out);
      }
    }
  };
  MapperGroup const mapper_group = MapperGroup{x};
  std::function<void(int32_t, std::shared_ptr<std::vector<int32_t>>)> mapper = [=](int32_t a0, std::shared_ptr<std::vector<int32_t>> a1) -> void { mapper_group.mapper(a0, a1); };
  std::shared_ptr<std::vector<int32_t>> out2 = core::ListBuilder::make<int32_t>();
  mapper(0, out2);
  core::Console::log(temper_globals::console.value, "selfRecursion = " + locals::show(out2));
}

static void selfRecursionNested(std::shared_ptr<std::vector<int32_t>> const & x) {
  struct MapperGroup {
    std::shared_ptr<std::vector<int32_t>> x;
    void mapper(int32_t i, std::shared_ptr<std::vector<int32_t>> out) const {
      locals::nestBlockVoid([=, x = x, mapper_group = *this]() -> void { if (i < core::List::length<int32_t>(x)) { core::ListBuilder::add<int32_t>(out, core::Int::mul(core::List::get<int32_t>(x, i), 2)); mapper_group.mapper(core::Int::add(i, 1), out); } });
    }
  };
  MapperGroup const mapper_group = MapperGroup{x};
  std::function<void(int32_t, std::shared_ptr<std::vector<int32_t>>)> mapper = [=](int32_t a0, std::shared_ptr<std::vector<int32_t>> a1) -> void { mapper_group.mapper(a0, a1); };
  std::shared_ptr<std::vector<int32_t>> out2 = core::ListBuilder::make<int32_t>();
  mapper(0, out2);
  core::Console::log(temper_globals::console.value, "selfRecursionNested = " + locals::show(out2));
}

static void mutualRecursion(std::shared_ptr<std::vector<int32_t>> const & x) {
  struct WalkerGroup {
    std::shared_ptr<std::vector<int32_t>> x;
    void walker(int32_t i, std::shared_ptr<std::vector<int32_t>> out) const {
      if (i < core::List::length<int32_t>(x)) {
        putter(i, out);
      }
    }
    void putter(int32_t i2, std::shared_ptr<std::vector<int32_t>> out2) const {
      core::ListBuilder::add<int32_t>(out2, core::Int::mul(core::List::get<int32_t>(x, i2), 2));
      walker(core::Int::add(i2, 1), out2);
    }
  };
  WalkerGroup const walker_group = WalkerGroup{x};
  std::function<void(int32_t, std::shared_ptr<std::vector<int32_t>>)> walker = [=](int32_t a0, std::shared_ptr<std::vector<int32_t>> a1) -> void { walker_group.walker(a0, a1); };
  std::shared_ptr<std::vector<int32_t>> out3 = core::ListBuilder::make<int32_t>();
  walker(0, out3);
  core::Console::log(temper_globals::console.value, "mutualRecursion = " + locals::show(out3));
}

static std::shared_ptr<std::vector<int32_t>> gatherHandlers(std::shared_ptr<Hub<int32_t>> const & hub) {
  std::shared_ptr<std::vector<int32_t>> items = core::ListBuilder::make<int32_t>();
  {
    int32_t i = 0;
    while (i < 3) {
      int32_t i2 = i;
      hub->onAction([=](int32_t item) -> void { core::ListBuilder::add<int32_t>(items, core::Int::add(item, i2)); });
      i = core::Int::add(i, 1);
    }
  }
  return items;
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::console.value = core::Console::get_console();
    locals::constantCapture(core::List::make<int32_t>(1, 2, 3), 1);
    locals::constantCaptureNested(core::List::make<int32_t>(1, 2, 3), 1);
    locals::mutableCapture(core::List::make<int32_t>(1, 2, 3));
    locals::mutableCaptureNested(core::List::make<int32_t>(1, 2, 3));
    locals::thresh(core::List::make<int32_t>(4, 5, 6), 4);
    locals::thresh2(core::List::make<int32_t>(4, 5, 6), 4);
    locals::selfRecursion(core::List::make<int32_t>(1, 2, 3));
    locals::selfRecursionNested(core::List::make<int32_t>(1, 2, 3));
    locals::mutualRecursion(core::List::make<int32_t>(1, 2, 3));
    std::shared_ptr<Hub<int32_t>> hub = Hub<int32_t>::make();
    std::shared_ptr<std::vector<int32_t>> items = locals::gatherHandlers(hub);
    hub->actionPerformed(1);
  });
}

}  // namespace locals
