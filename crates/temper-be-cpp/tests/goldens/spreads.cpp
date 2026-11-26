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
struct Step;
struct Shape;
struct Circle;
struct Square;

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

struct Step : public std::enable_shared_from_this<Step> {
  int32_t n;
  temper::core::Str tag;
  static std::shared_ptr<Step> make(int32_t n, temper::core::Str const & tag);
  std::shared_ptr<Step> next();
  int32_t get_n() const;
  temper::core::Str get_tag() const;
};

struct Shape : public virtual temper::core::AnyValueBase {};

struct Circle : public virtual Shape {
  int32_t r;
  static std::shared_ptr<Circle> make(int32_t r);
  int32_t get_r() const;
};

struct Square : public virtual Shape {
  int32_t side;
  static std::shared_ptr<Square> make(int32_t side);
  int32_t get_side() const;
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
static core::GlobalSlot<std::shared_ptr<Box>> a;
static core::GlobalSlot<std::shared_ptr<core::PromiseBuilder<int32_t>>> gate;

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

static int32_t areaOf(std::shared_ptr<Sized> const & s) {
  return s->area();
}

static int32_t said(core::Str const & s, int32_t n) {
  core::Console::log(temper_globals::console.value, s);
  return n;
}

static core::Str both(int32_t x, std::shared_ptr<Box> const & b) {
  return std::to_string(x) + " " + b->toString();
}

static int32_t widths(std::shared_ptr<Box> const & b, int32_t n) {
  std::shared_ptr<Box> t = b;
  {
    int32_t i = 0;
    while (i < n) {
      std::shared_ptr<Box> spread10 = t;
      std::shared_ptr<Box> box = std::make_shared<Box>(*spread10);
      box->w = t->h;
      box->h = core::Int::add(t->w, 1);
      t = box;
      i = core::Int::add(i, 1);
    }
  }
  while (true) {
    std::shared_ptr<Box> spread11 = t;
    std::shared_ptr<Box> box2 = std::make_shared<Box>(*spread11);
    box2->h = 1;
    if (box2->area() >= 9) {
      break;
    }
    std::shared_ptr<Box> spread2 = t;
    std::shared_ptr<Box> box3 = std::make_shared<Box>(*spread2);
    box3->w = core::Int::add(t->w, 1);
    t = box3;
  }
  {
    int32_t i = 0;
    while (i < 2) {
      std::shared_ptr<Box> spread12 = t;
      int32_t arg = spreads::said(TEMPER_STR("h"), t->w);
      int32_t arg2 = spreads::said(TEMPER_STR("w"), t->h);
      core::NullableParam<core::Str> arg3 = spread12->label;
      t = Box::make(arg2, arg, arg3);
      i = core::Int::add(i, 1);
    }
  }
  if (t->w > 0) {
    std::shared_ptr<Box> spread13 = b;
    std::shared_ptr<Box> b2 = std::make_shared<Box>(*spread13);
    b2->label = TEMPER_STR("inner");
    core::Console::log(temper_globals::console.value, b2->toString());
  }
  return t->w;
}

std::shared_ptr<Step> Step::make(int32_t n, core::Str const & tag) {
  std::shared_ptr<Step> self = std::make_shared<Step>();
  self->n = n;
  self->tag = tag;
  return self;
}

std::shared_ptr<Step> Step::next() {
  std::shared_ptr<Step> self = shared_from_this();
  {
    std::shared_ptr<Step> spread = self;
    std::shared_ptr<Step> step = std::make_shared<Step>(*spread);
    step->n = core::Int::add(self->n, 1);
    return step;
  }
}

int32_t Step::get_n() const {
  return this->n;
}

core::Str Step::get_tag() const {
  return this->tag;
}

template <class T>
static std::shared_ptr<Two<T>> swapped(std::shared_ptr<Two<T>> const & t) {
  {
    std::shared_ptr<Two<T>> spread = t;
    return Two<T>::make(t->second, t->first);
  }
}

std::shared_ptr<Circle> Circle::make(int32_t r) {
  std::shared_ptr<Circle> self = std::make_shared<Circle>();
  self->r = r;
  return self;
}

int32_t Circle::get_r() const {
  return this->r;
}

std::shared_ptr<Square> Square::make(int32_t side) {
  std::shared_ptr<Square> self = std::make_shared<Square>();
  self->side = side;
  return self;
}

int32_t Square::get_side() const {
  return this->side;
}

static std::shared_ptr<Shape> doubled(std::shared_ptr<Shape> const & x) {
  std::shared_ptr<Shape> shape;
  if (dynamic_cast<Circle const *>(x.get()) != nullptr) {
    std::shared_ptr<Circle> spread10 = core::checked_cast<Circle>(x);
    shape = core::to_base<Shape>(Circle::make(core::Int::mul(core::checked_cast<Circle>(x)->r, 2)));
  } else {
    std::shared_ptr<Square> square;
    if (dynamic_cast<Square const *>(x.get()) != nullptr) {
      std::shared_ptr<Square> spread11 = core::checked_cast<Square>(x);
      square = Square::make(core::Int::mul(core::checked_cast<Square>(x)->side, 2));
    } else {
      square = core::panic<std::shared_ptr<Square>>();
    }
    shape = core::to_base<Shape>(square);
  }
  std::shared_ptr<Shape> made = shape;
  return made;
}

static int32_t size(std::shared_ptr<Shape> const & x) {
  int32_t n = dynamic_cast<Circle const *>(x.get()) != nullptr ? core::checked_cast<Circle>(x)->r : dynamic_cast<Square const *>(x.get()) != nullptr ? core::checked_cast<Square>(x)->side : core::panic<int32_t>();
  return n;
}

static std::shared_ptr<Box> picked(bool c) {
  if (c) {
    {
      std::shared_ptr<Box> spread = temper_globals::a.value;
      std::shared_ptr<Box> box = std::make_shared<Box>(*spread);
      box->w = 9;
      return box;
    }
  } else {
    return temper_globals::a.value;
  }
}

static std::shared_ptr<Box> applied(std::function<std::shared_ptr<Box>(int32_t)> const & f) {
  return f(4);
}

static int32_t positive(int32_t n) {
  if (n < 0) {
    core::bubble();
  }
  return n;
}

static std::shared_ptr<Box> sized(std::shared_ptr<Box> const & b, int32_t n) {
  {
    std::shared_ptr<Box> spread = b;
    std::shared_ptr<Box> box = std::make_shared<Box>(*spread);
    box->w = positive(n);
    return box;
  }
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::a.construct();
    temper_globals::gate.construct();
    temper_globals::console.value = core::Console::get_console();
    temper_globals::a.value = Box::make(2, 3);
    std::shared_ptr<Box> b = spreads::grown(temper_globals::a.value);
    core::Console::log(temper_globals::console.value, temper_globals::a.value->toString() + " " + b->toString() + " " + std::to_string(spreads::areaOf(core::to_base<Sized>(b))));
    std::shared_ptr<Box> spread = b;
    std::shared_ptr<Box> box = std::make_shared<Box>(*spread);
    box->label = TEMPER_STR("named");
    core::Console::log(temper_globals::console.value, box->toString());
    bool boolean = temper_globals::a.value->w > 1;
    if (boolean) {
      std::shared_ptr<Box> spread22 = temper_globals::a.value;
      std::shared_ptr<Box> box2 = std::make_shared<Box>(*spread22);
      box2->w = 10;
      boolean = box2->area() > 20;
    }
    bool wide = boolean;
    std::shared_ptr<Two<int32_t>> p = Two<int32_t>::make(1, 2);
    std::shared_ptr<Two<int32_t>> spread3 = p;
    std::shared_ptr<Two<int32_t>> q = std::make_shared<Two<int32_t>>(*spread3);
    q->second = 5;
    core::Console::log(temper_globals::console.value, core::Boolean::toString(wide) + " " + std::to_string(q->first) + " " + std::to_string(q->second));
    std::shared_ptr<Box> spread5 = temper_globals::a.value;
    std::shared_ptr<Box> spread4 = std::make_shared<Box>(*spread5);
    spread4->w = 7;
    std::shared_ptr<Box> nested = std::make_shared<Box>(*spread4);
    nested->h = 8;
    int32_t int32 = spreads::said(TEMPER_STR("one"), 1);
    int32_t value2 = int32;
    std::shared_ptr<Box> spread6 = temper_globals::a.value;
    std::shared_ptr<Box> box3 = std::make_shared<Box>(*spread6);
    box3->h = spreads::said(TEMPER_STR("two"), 2);
    core::Console::log(temper_globals::console.value, spreads::both(value2, box3));
    core::Str string = nested->toString() + " ";
    bool value = temper_globals::a.value->w > 5;
    if (!value) {
      std::shared_ptr<Box> spread7 = temper_globals::a.value;
      std::shared_ptr<Box> box4 = std::make_shared<Box>(*spread7);
      box4->w = spreads::said(TEMPER_STR("three"), 3);
      value = box4->w == 3;
    }
    core::Console::log(temper_globals::console.value, string + core::Boolean::toString(value));
    core::Console::log(temper_globals::console.value, std::to_string(spreads::widths(temper_globals::a.value, 3)));
    std::shared_ptr<Step> s = Step::make(1, TEMPER_STR("s"))->next()->next();
    std::shared_ptr<Two<int32_t>> spread8 = q;
    std::shared_ptr<Two<int32_t>> same = std::make_shared<Two<int32_t>>(*spread8);
    std::shared_ptr<Two<core::Str>> words = Two<core::Str>::make(TEMPER_STR("a"), TEMPER_STR("b"));
    std::shared_ptr<Two<core::Str>> flipped = spreads::swapped<core::Str>(words);
    core::Console::log(temper_globals::console.value, std::to_string(s->n) + " " + s->tag + " " + std::to_string(same->first) + std::to_string(same->second) + " " + flipped->first + flipped->second);
    int32_t value3 = spreads::size(spreads::doubled(core::to_base<Shape>(Circle::make(3))));
    core::Console::log(temper_globals::console.value, std::to_string(value3) + " " + std::to_string(spreads::size(spreads::doubled(core::to_base<Shape>(Square::make(5))))));
    std::shared_ptr<Box> made = spreads::applied([](int32_t n) -> std::shared_ptr<Box> { { std::shared_ptr<Box> spread2 = temper_globals::a.value; std::shared_ptr<Box> box5 = std::make_shared<Box>(*spread2); box5->h = n; return box5; } });
    core::Console::log(temper_globals::console.value, picked(true)->toString() + " " + picked(false)->toString() + " " + made->toString());
    std::shared_ptr<Box> spread9 = temper_globals::a.value;
    std::shared_ptr<Box> caught = std::make_shared<Box>(*spread9);
    int32_t value4;
    try {
      value4 = positive(-1);
    } catch (core::TemperBubble const &) {
      value4 = 0;
    }
    caught->w = value4;
    int32_t value5;
    try {
      value5 = positive(5);
    } catch (core::TemperBubble const &) {
      value5 = -1;
    }
    caught->h = value5;
    core::Str string2 = caught->toString() + " ";
    core::Str value6;
    try {
      value6 = spreads::sized(temper_globals::a.value, 4)->toString();
    } catch (core::TemperBubble const &) {
      value6 = TEMPER_STR("-");
    }
    core::Console::log(temper_globals::console.value, string2 + value6);
    core::Str value7;
    try {
      spreads::sized(temper_globals::a.value, -1);
      value7 = TEMPER_STR("sized");
    } catch (core::TemperBubble const &) {
      value7 = TEMPER_STR("bubbled");
    }
    core::Console::log(temper_globals::console.value, value7);
    temper_globals::gate.value = core::PromiseBuilderNs::make<int32_t>();
    auto fn2 = []() -> std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> {
      std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
      std::shared_ptr<std::shared_ptr<Box>> later = std::make_shared<std::shared_ptr<Box>>(nullptr);
      std::shared_ptr<std::shared_ptr<Box>> spread2 = std::make_shared<std::shared_ptr<Box>>(nullptr);
      std::shared_ptr<int32_t> arg = std::make_shared<int32_t>(0);
      std::shared_ptr<int32_t> arg2 = std::make_shared<int32_t>(0);
      std::shared_ptr<std::shared_ptr<core::Promise<int32_t>>> awaited = std::make_shared<std::shared_ptr<core::Promise<int32_t>>>(nullptr);
      auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> generator) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
        while (true) {
          int32_t caseIndexLocal = (*caseIndex);
          *caseIndex = -1;
          switch (caseIndexLocal) {
            case 0:
              *spread2 = temper_globals::a.value;
              *arg = (*spread2)->w;
              *caseIndex = 3;
              break;
            case 1:
              *arg2 = -1;
              *caseIndex = 2;
              break;
            case 2:
              *later = Box::make((*arg), (*arg2), TEMPER_STR("later"));
              core::Console::log(temper_globals::console.value, (*later)->toString());
              return core::doneResult();
            case 3:
              try {
                *awaited = core::getpromise(temper_globals::gate.value);
                *caseIndex = 4;
                core::awake_upon((*awaited), generator);
                return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
              } catch (core::TemperBubble const &) {
                *caseIndex = 1;
              }
              break;
            case 4:
              try {
                *arg2 = core::get_promise_result_sync((*awaited));
                *caseIndex = 2;
              } catch (core::TemperBubble const &) {
                *caseIndex = 1;
              }
              break;
            default:
              return core::doneResult();
          }
        }
      };
      return core::safe_adapt_generator_fn<std::shared_ptr<core::Empty>>(convertedCoroutine);
    };
    core::async_run(fn2);
    core::complete(temper_globals::gate.value, 6);
  });
}

}  // namespace spreads
