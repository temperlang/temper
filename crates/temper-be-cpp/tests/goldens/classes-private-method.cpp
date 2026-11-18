// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace private_method {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct C;
struct Money;
struct Sink;
struct TenSink;
struct Note;
struct Label;
struct Tally;

struct C : public std::enable_shared_from_this<C> {
  static std::shared_ptr<C> make();
  int32_t helper(int32_t x) const;
  int32_t method1(int32_t x) const;
  int32_t method2(int32_t x) const;
};

struct Money : public std::enable_shared_from_this<Money> {
  int32_t cents;
  int32_t spent;
  static std::shared_ptr<Money> make(int32_t cents);
  int32_t whole() const;
  int32_t get_rounded() const;
  std::shared_ptr<Money> plus(std::shared_ptr<Money> const & other) const;
  bool sameWhole(std::shared_ptr<Money> const & other) const;
  bool sameRounded(std::shared_ptr<Money> const & other) const;
  void spendFrom(std::shared_ptr<Money> const & other) const;
  int32_t get_timesSpent() const;
  temper::core::Str toString() const;
};

struct Sink : public virtual temper::core::AnyValueBase {
  virtual int32_t end() const = 0;
};

struct TenSink : public virtual Sink {
  static std::shared_ptr<TenSink> make();
  int32_t end() const override;
};

struct Note : public std::enable_shared_from_this<Note> {
  temper::core::Str body;
  static std::shared_ptr<Note> make(temper::core::Str const & body);
  std::shared_ptr<Note> text(temper::core::Str const & more) const;
  temper::core::Str get_body() const;
};

struct Label : public std::enable_shared_from_this<Label> {
  int32_t end;
  static std::shared_ptr<Label> make(int32_t end);
  temper::core::Str get_text() const;
  int32_t close(std::shared_ptr<Sink> const & sink) const;
  temper::core::Str noted(temper::core::Str const & more) const;
};

struct Tally : public std::enable_shared_from_this<Tally> {
  int32_t depth;
  int32_t size;
  static std::shared_ptr<Tally> make(int32_t size, std::shared_ptr<Tally> const & parent, std::shared_ptr<std::vector<std::shared_ptr<Tally>>> const & children);
  int32_t get_total() const;
  int32_t get_depth() const;
};

}  // namespace private_method

// init.cpp
#include <private-method/init.hpp>

namespace private_method {

namespace core = temper::core;

std::shared_ptr<C> C::make() {
  std::shared_ptr<C> self = std::make_shared<C>();
  return self;
}

int32_t C::helper(int32_t x) const {
  return core::Int::add(x, 1);
}

int32_t C::method1(int32_t x) const {
  std::shared_ptr<C const> self = shared_from_this();
  return self->helper(core::Int::mul(x, 2));
}

int32_t C::method2(int32_t x) const {
  std::shared_ptr<C const> self = shared_from_this();
  return self->helper(core::Int::mul(x, 3));
}

std::shared_ptr<Money> Money::make(int32_t cents) {
  std::shared_ptr<Money> self = std::make_shared<Money>();
  self->cents = cents;
  self->spent = 0;
  return self;
}

int32_t Money::whole() const {
  return this->cents / 100;
}

int32_t Money::get_rounded() const {
  return core::Int::add(this->cents, 50) / 100;
}

std::shared_ptr<Money> Money::plus(std::shared_ptr<Money> const & other) const {
  std::shared_ptr<Money const> self = shared_from_this();
  return Money::make(core::Int::add(self->cents, other->cents));
}

bool Money::sameWhole(std::shared_ptr<Money> const & other) const {
  std::shared_ptr<Money const> self = shared_from_this();
  return self->whole() == other->whole();
}

bool Money::sameRounded(std::shared_ptr<Money> const & other) const {
  std::shared_ptr<Money const> self = shared_from_this();
  return self->get_rounded() == other->get_rounded();
}

void Money::spendFrom(std::shared_ptr<Money> const & other) const {
  other->spent = core::Int::add(other->spent, 1);
}

int32_t Money::get_timesSpent() const {
  return this->spent;
}

core::Str Money::toString() const {
  return std::to_string(this->cents) + " cents";
}

std::shared_ptr<TenSink> TenSink::make() {
  std::shared_ptr<TenSink> self = std::make_shared<TenSink>();
  return self;
}

int32_t TenSink::end() const {
  return 10;
}

std::shared_ptr<Note> Note::make(core::Str const & body) {
  std::shared_ptr<Note> self = std::make_shared<Note>();
  self->body = body;
  return self;
}

std::shared_ptr<Note> Note::text(core::Str const & more) const {
  std::shared_ptr<Note const> self = shared_from_this();
  return Note::make(self->body + more);
}

core::Str Note::get_body() const {
  return this->body;
}

std::shared_ptr<Label> Label::make(int32_t end) {
  std::shared_ptr<Label> self = std::make_shared<Label>();
  self->end = end;
  return self;
}

core::Str Label::get_text() const {
  return TEMPER_STR("label");
}

int32_t Label::close(std::shared_ptr<Sink> const & sink) const {
  std::shared_ptr<Label const> self = shared_from_this();
  return core::Int::add(sink->end(), self->end);
}

core::Str Label::noted(core::Str const & more) const {
  std::shared_ptr<Label const> self = shared_from_this();
  return Note::make(self->get_text())->text(more)->body;
}

std::shared_ptr<Tally> Tally::make(int32_t size, std::shared_ptr<Tally> const & parent, std::shared_ptr<std::vector<std::shared_ptr<Tally>>> const & children) {
  std::shared_ptr<Tally> self = std::make_shared<Tally>();
  self->depth = parent == nullptr ? 0 : core::Int::add(parent->depth, 1);
  int32_t sum = size;
  {
    int32_t i = 0;
    while (i < core::List::length<std::shared_ptr<Tally>>(children)) {
      sum = core::Int::add(sum, core::List::get<std::shared_ptr<Tally>>(children, i)->size);
      i = core::Int::add(i, 1);
    }
  }
  self->size = core::Int::add(sum, self->depth);
  return self;
}

int32_t Tally::get_total() const {
  return this->size;
}

int32_t Tally::get_depth() const {
  return this->depth;
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    std::shared_ptr<C> c = C::make();
    core::Console::log(console, "method1(10)=" + std::to_string(c->method1(10)));
    core::Console::log(console, "method2(10)=" + std::to_string(c->method2(10)));
    std::shared_ptr<Money> a = Money::make(150);
    std::shared_ptr<Money> b = Money::make(175);
    core::Console::log(console, "plus: " + a->plus(b)->toString());
    core::Console::log(console, "same whole: " + core::Boolean::toString(a->sameWhole(b)));
    core::Console::log(console, "same rounded: " + core::Boolean::toString(a->sameRounded(b)));
    a->spendFrom(b);
    a->spendFrom(b);
    core::Console::log(console, "b spent: " + std::to_string(b->get_timesSpent()));
    std::shared_ptr<Label> label = Label::make(1);
    core::Console::log(console, "close: " + std::to_string(label->close(core::to_base<Sink>(TenSink::make()))));
    core::Console::log(console, "noted: " + label->noted(TEMPER_STR("!")));
    std::shared_ptr<Tally> leaf = Tally::make(2, nullptr, core::List::make<std::shared_ptr<Tally>>());
    std::shared_ptr<Tally> mid = Tally::make(3, leaf, core::List::make<std::shared_ptr<Tally>>(leaf, leaf));
    std::shared_ptr<Tally> top = Tally::make(1, mid, core::List::make<std::shared_ptr<Tally>>(mid, leaf));
    core::Console::log(console, "depths: " + std::to_string(leaf->depth) + " " + std::to_string(mid->depth) + " " + std::to_string(top->depth));
    core::Console::log(console, "totals: " + std::to_string(leaf->get_total()) + " " + std::to_string(mid->get_total()) + " " + std::to_string(top->get_total()));
  });
}

}  // namespace private_method
