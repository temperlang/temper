// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace spreads {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct Sized;
struct Box;
template <class T>
struct Two;

struct Sized : public virtual temper::core::AnyValueBase {
  virtual int32_t area() const = 0;
};

struct Box : public virtual Sized {
  int32_t w;
  int32_t h;
  temper::core::NullableParam<temper::core::Str> label;
  static std::shared_ptr<Box> make(int32_t w, int32_t h, temper::core::NullableParam<temper::core::Str> label);
  static std::shared_ptr<Box> make(int32_t w, int32_t h);
  int32_t area() const override;
  temper::core::Str toString() const;
  int32_t get_w() const;
  int32_t get_h() const;
  temper::core::NullableParam<temper::core::Str> get_label() const;
};

template <class T>
struct Two : public std::enable_shared_from_this<Two<T>> {
  T first;
  T second;
  static std::shared_ptr<Two<T>> make(T first, T second);
  T get_first() const;
  T get_second() const;
};

template <class T>
std::shared_ptr<Two<T>> Two<T>::make(T first, T second) {
  std::shared_ptr<Two<T>> self = std::make_shared<Two<T>>();
  self->first = first;
  self->second = second;
  return self;
}

template <class T>
T Two<T>::get_first() const {
  return this->first;
}

template <class T>
T Two<T>::get_second() const {
  return this->second;
}

}  // namespace spreads

// init.cpp
#include <spreads/init.hpp>

namespace spreads {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;

}  // namespace temper_globals

std::shared_ptr<Box> Box::make(int32_t w, int32_t h, core::NullableParam<core::Str> label) {
  std::shared_ptr<Box> self = std::make_shared<Box>();
  self->w = w;
  self->h = h;
  self->label = label;
  return self;
}

std::shared_ptr<Box> Box::make(int32_t w, int32_t h) {
  return make(w, h, nullptr);
}

int32_t Box::area() const {
  return core::Int::mul(this->w, this->h);
}

core::Str Box::toString() const {
  core::NullableParam<core::Str> subject = this->label;
  return (subject == nullptr ? TEMPER_STR("box") : subject.value) + " " + std::to_string(this->w) + "x" + std::to_string(this->h);
}

int32_t Box::get_w() const {
  return this->w;
}

int32_t Box::get_h() const {
  return this->h;
}

core::NullableParam<core::Str> Box::get_label() const {
  return this->label;
}

static std::shared_ptr<Box> grown(std::shared_ptr<Box> const & b) {
  {
    std::shared_ptr<Box> spread = b;
    std::shared_ptr<Box> box = std::make_shared<Box>(*spread);
    box->w = core::Int::add(b->w, 1);
    return box;
  }
}

static int32_t widths(std::shared_ptr<Box> const & b, int32_t n) {
  std::shared_ptr<Box> t = b;
  {
    int32_t i = 0;
    while (i < n) {
      std::shared_ptr<Box> spread8 = t;
      std::shared_ptr<Box> box = std::make_shared<Box>(*spread8);
      box->w = t->h;
      box->h = core::Int::add(t->w, 1);
      t = box;
      i = core::Int::add(i, 1);
    }
  }
  while (true) {
    std::shared_ptr<Box> spread9 = t;
    std::shared_ptr<Box> box2 = std::make_shared<Box>(*spread9);
    box2->h = 1;
    if (box2->area() >= 9) {
      break;
    }
    std::shared_ptr<Box> spread2 = t;
    std::shared_ptr<Box> box3 = std::make_shared<Box>(*spread2);
    box3->w = core::Int::add(t->w, 1);
    t = box3;
  }
  if (t->w > 0) {
    std::shared_ptr<Box> spread10 = b;
    std::shared_ptr<Box> b2 = std::make_shared<Box>(*spread10);
    b2->label = TEMPER_STR("inner");
    core::Console::log(temper_globals::console.value, b2->toString());
  }
  return t->w;
}

static int32_t said(core::Str const & s, int32_t n) {
  core::Console::log(temper_globals::console.value, s);
  return n;
}

static core::Str both(int32_t x, std::shared_ptr<Box> const & b) {
  return std::to_string(x) + " " + b->toString();
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::console.value = core::Console::get_console();
    std::shared_ptr<Box> a = Box::make(2, 3);
    std::shared_ptr<Box> b = spreads::grown(a);
    core::Console::log(temper_globals::console.value, a->toString() + " " + b->toString() + " " + std::to_string(b->area()));
    std::shared_ptr<Box> spread = b;
    std::shared_ptr<Box> box = std::make_shared<Box>(*spread);
    box->label = TEMPER_STR("named");
    core::Console::log(temper_globals::console.value, box->toString());
    bool boolean = a->w > 1;
    if (boolean) {
      std::shared_ptr<Box> spread2 = a;
      std::shared_ptr<Box> box2 = std::make_shared<Box>(*spread2);
      box2->w = 10;
      boolean = box2->area() > 20;
    }
    bool wide = boolean;
    std::shared_ptr<Two<int32_t>> p = Two<int32_t>::make(1, 2);
    std::shared_ptr<Two<int32_t>> spread3 = p;
    std::shared_ptr<Two<int32_t>> q = std::make_shared<Two<int32_t>>(*spread3);
    q->second = 5;
    core::Console::log(temper_globals::console.value, core::Boolean::toString(wide) + " " + std::to_string(q->first) + " " + std::to_string(q->second));
    core::Console::log(temper_globals::console.value, std::to_string(spreads::widths(a, 3)));
    std::shared_ptr<Box> spread5 = a;
    std::shared_ptr<Box> spread4 = std::make_shared<Box>(*spread5);
    spread4->w = 7;
    std::shared_ptr<Box> nested = std::make_shared<Box>(*spread4);
    nested->h = 8;
    int32_t int32 = spreads::said(TEMPER_STR("one"), 1);
    int32_t value2 = int32;
    std::shared_ptr<Box> spread6 = a;
    std::shared_ptr<Box> box3 = std::make_shared<Box>(*spread6);
    box3->h = spreads::said(TEMPER_STR("two"), 2);
    core::Console::log(temper_globals::console.value, spreads::both(value2, box3));
    core::Str string = nested->toString() + " ";
    bool value = a->w > 5;
    if (!value) {
      std::shared_ptr<Box> spread7 = a;
      std::shared_ptr<Box> box4 = std::make_shared<Box>(*spread7);
      box4->w = spreads::said(TEMPER_STR("three"), 3);
      value = box4->w == 3;
    }
    core::Console::log(temper_globals::console.value, string + core::Boolean::toString(value));
  });
}

}  // namespace spreads
