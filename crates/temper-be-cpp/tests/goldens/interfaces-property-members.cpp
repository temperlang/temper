// init.hpp
#pragma once

#include <temper-core/base.hpp>

namespace property_members {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct I;
struct J;
struct C;
template <class T>
struct D;
template <class T>
struct SI;
template <class T>
struct SSub;
template <class T>
struct Holder;
template <class L, class T>
struct Labeled;
struct LabeledSub;
struct High;
struct Low1;
struct Mid;
struct Low2;
struct Low3;

struct I : public virtual temper::core::AnyValueBase {
  virtual temper::core::Str get_x() = 0;
};

struct J : public virtual I {};

struct C : public virtual J {
  temper::core::Str x;
  static std::shared_ptr<C> make(temper::core::Str const & x);
  temper::core::Str get_x() override;
};

template <class T>
struct D : public std::enable_shared_from_this<D<T>> {
  T thing;
  static std::shared_ptr<D<T>> make(T thing);
  T get_thing() const;
};

template <class T>
struct SI : public virtual temper::core::AnyValueBase {
  virtual T get_thing() const = 0;
};

template <class T>
struct SSub : public virtual SI<T> {
  T thing;
  static std::shared_ptr<SSub<T>> make(T thing);
  T get_thing() const override;
};

template <class T>
struct Holder : public virtual temper::core::AnyValueBase {
  virtual T get_held() const = 0;
  virtual std::shared_ptr<std::vector<T>> heldAnd(T other) const;
};

template <class L, class T>
struct Labeled : public virtual Holder<T> {
  virtual temper::core::Str label(L l) const = 0;
};

struct LabeledSub : public virtual Labeled<temper::core::Str, std::shared_ptr<C>> {
  std::shared_ptr<C> held;
  static std::shared_ptr<LabeledSub> make(std::shared_ptr<C> const & held);
  temper::core::Str label(temper::core::Str l) const override;
  std::shared_ptr<C> get_held() const override;
};

struct High : public virtual temper::core::AnyValueBase {
  virtual temper::core::Str toString() const = 0;
  virtual bool eq(std::shared_ptr<High> const & other) const = 0;
};

struct Low1 : public virtual High {
  int32_t i;
  static std::shared_ptr<Low1> make(int32_t i);
  temper::core::Str toString() const override;
  bool eq(std::shared_ptr<High> const & other) const override;
  int32_t get_i() const;
};

struct Mid : public virtual High {};

struct Low2 : public virtual Mid {
  temper::core::Str s;
  static std::shared_ptr<Low2> make(temper::core::Str const & s);
  temper::core::Str toString() const override;
  bool eq(std::shared_ptr<High> const & other) const override;
  temper::core::Str get_s() const;
};

struct Low3 : public virtual Mid {
  static std::shared_ptr<Low3> make();
  temper::core::Str toString() const override;
  bool eq(std::shared_ptr<High> const & other) const override;
};

template <class T>
std::shared_ptr<D<T>> D<T>::make(T thing) {
  std::shared_ptr<D<T>> self = std::make_shared<D<T>>();
  self->thing = thing;
  return self;
}

template <class T>
T D<T>::get_thing() const {
  return this->thing;
}

template <class T>
std::shared_ptr<SSub<T>> SSub<T>::make(T thing) {
  std::shared_ptr<SSub<T>> self = std::make_shared<SSub<T>>();
  self->thing = thing;
  return self;
}

template <class T>
T SSub<T>::get_thing() const {
  return this->thing;
}

template <class T>
std::shared_ptr<std::vector<T>> Holder<T>::heldAnd(T other) const {
  std::shared_ptr<Holder<T> const> self = std::shared_ptr<Holder<T> const>(this->shared_from_this(), this);
  return temper::core::List::make<T>(self->get_held(), other);
}

}  // namespace property_members

// init.cpp
#include <property-members/init.hpp>

#include <temper-core/casting.hpp>
#include <temper-core/boolean.hpp>
#include <temper-core/int.hpp>
#include <temper-core/console.hpp>

namespace property_members {

namespace core = temper::core;

template <class IT>
static IT leastX(IT a, IT b) {
  if (a->get_x() < b->get_x()) {
    return a;
  } else {
    return b;
  }
}

std::shared_ptr<C> C::make(core::Str const & x) {
  std::shared_ptr<C> self = std::make_shared<C>();
  self->x = x;
  return self;
}

core::Str C::get_x() {
  return this->x;
}

std::shared_ptr<LabeledSub> LabeledSub::make(std::shared_ptr<C> const & held) {
  std::shared_ptr<LabeledSub> self = std::make_shared<LabeledSub>();
  self->held = held;
  return self;
}

core::Str LabeledSub::label(core::Str l) const {
  return l + ": " + this->held->x;
}

std::shared_ptr<C> LabeledSub::get_held() const {
  return this->held;
}

std::shared_ptr<Low1> Low1::make(int32_t i) {
  std::shared_ptr<Low1> self = std::make_shared<Low1>();
  self->i = i;
  return self;
}

core::Str Low1::toString() const {
  return "Low1(" + std::to_string(this->i) + ")";
}

bool Low1::eq(std::shared_ptr<High> const & other) const {
  if (dynamic_cast<Low1 const *>(other.get()) != nullptr) {
    return this->i == core::checked_cast<Low1>(other)->i;
  } else {
    return false;
  }
}

int32_t Low1::get_i() const {
  return this->i;
}

std::shared_ptr<Low2> Low2::make(core::Str const & s) {
  std::shared_ptr<Low2> self = std::make_shared<Low2>();
  self->s = s;
  return self;
}

core::Str Low2::toString() const {
  return "Low2(" + this->s + ")";
}

bool Low2::eq(std::shared_ptr<High> const & other) const {
  if (dynamic_cast<Low2 const *>(other.get()) != nullptr) {
    return this->s == core::checked_cast<Low2>(other)->s;
  } else {
    return false;
  }
}

core::Str Low2::get_s() const {
  return this->s;
}

std::shared_ptr<Low3> Low3::make() {
  std::shared_ptr<Low3> self = std::make_shared<Low3>();
  return self;
}

core::Str Low3::toString() const {
  return TEMPER_STR("Low3");
}

bool Low3::eq(std::shared_ptr<High> const & other) const {
  return dynamic_cast<Low3 const *>(other.get()) != nullptr;
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    std::shared_ptr<C> a = C::make(TEMPER_STR("foo"));
    std::shared_ptr<C> b = C::make(TEMPER_STR("bar"));
    core::Console::log(console, "leastX of " + a->x + " and " + b->x + " is " + property_members::leastX<std::shared_ptr<C>>(a, b)->x + ".");
    std::shared_ptr<I> i = core::to_base<I>(a);
    core::Console::log(console, "As an I, a.x is " + i->get_x() + ".");
    std::shared_ptr<D<std::shared_ptr<I>>> di = D<std::shared_ptr<I>>::make(core::to_base<I>(a));
    std::shared_ptr<D<std::shared_ptr<J>>> dj = D<std::shared_ptr<J>>::make(core::to_base<J>(a));
    core::Console::log(console, "Generically, x is " + di->thing->get_x() + ".");
    core::Console::log(console, "Genericallier, x is still " + core::to_base<I>(dj->thing)->get_x() + ".");
    std::shared_ptr<SI<std::shared_ptr<C>>> si = core::to_base<SI<std::shared_ptr<C>>>(SSub<std::shared_ptr<C>>::make(a));
    core::Console::log(console, "Sealed thing is " + si->get_thing()->x + ".");
    std::shared_ptr<Labeled<core::Str, std::shared_ptr<C>>> labeled = core::to_base<Labeled<core::Str, std::shared_ptr<C>>>(LabeledSub::make(a));
    std::shared_ptr<std::vector<std::shared_ptr<C>>> heldAnd = labeled->heldAnd(b);
    core::Console::log(console, "Labeled thing is " + labeled->label(TEMPER_STR("first")) + ".");
    core::Console::log(console, "Held are " + labeled->get_held()->x + " and " + core::List::get<std::shared_ptr<C>>(heldAnd, 1)->x + ".");
    std::shared_ptr<High> high1 = core::to_base<High>(Low1::make(11));
    std::shared_ptr<High> high1b = core::to_base<High>(Low1::make(12));
    std::shared_ptr<High> high1c = core::to_base<High>(Low1::make(12));
    std::shared_ptr<High> high2 = core::to_base<High>(Low2::make(TEMPER_STR("two")));
    std::shared_ptr<High> high2b = core::to_base<High>(Low2::make(TEMPER_STR("to be")));
    std::shared_ptr<High> high2c = core::to_base<High>(Low2::make(TEMPER_STR("to be")));
    std::shared_ptr<High> high3 = core::to_base<High>(Low3::make());
    std::shared_ptr<High> high3b = core::to_base<High>(Low3::make());
    std::shared_ptr<std::vector<std::shared_ptr<core::Pair<std::shared_ptr<High>, std::shared_ptr<High>>>>> pairs = core::List::make<std::shared_ptr<core::Pair<std::shared_ptr<High>, std::shared_ptr<High>>>>(core::PairFactory::make<std::shared_ptr<High>, std::shared_ptr<High>>(high1, high1b), core::PairFactory::make<std::shared_ptr<High>, std::shared_ptr<High>>(high1b, high1c), core::PairFactory::make<std::shared_ptr<High>, std::shared_ptr<High>>(high1, high2), core::PairFactory::make<std::shared_ptr<High>, std::shared_ptr<High>>(high2, high2b), core::PairFactory::make<std::shared_ptr<High>, std::shared_ptr<High>>(high2b, high2c), core::PairFactory::make<std::shared_ptr<High>, std::shared_ptr<High>>(high1, high3), core::PairFactory::make<std::shared_ptr<High>, std::shared_ptr<High>>(high3, high3b));
    {
      int32_t length = core::List::length<std::shared_ptr<core::Pair<std::shared_ptr<High>, std::shared_ptr<High>>>>(pairs);
      int32_t i2 = 0;
      while (i2 < length) {
        std::shared_ptr<core::Pair<std::shared_ptr<High>, std::shared_ptr<High>>> pair = core::List::get<std::shared_ptr<core::Pair<std::shared_ptr<High>, std::shared_ptr<High>>>>(pairs, i2);
        i2 = core::Int::add(i2, 1);
        core::Console::log(console, pair->key->toString() + " == " + pair->value->toString() + ": " + core::Boolean::toString(pair->key->eq(pair->value)));
      }
    }
  });
}

}  // namespace property_members
