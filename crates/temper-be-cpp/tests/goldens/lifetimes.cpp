// init.hpp
#pragma once

#include <temper-core/nullable_param.hpp>
#include <temper-core/str.hpp>
#include <memory>
#include <vector>

namespace lifetimes {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct Box;
struct Holder;
struct Item;
struct Slot;
struct Names;

struct Box : public std::enable_shared_from_this<Box> {
  std::shared_ptr<Item> item;
  static std::shared_ptr<Box> make(std::shared_ptr<Item> const & item);
  std::shared_ptr<Item> get_item();
};

struct Holder : public std::enable_shared_from_this<Holder> {
  std::shared_ptr<Box> box;
  static std::shared_ptr<Holder> make(std::shared_ptr<Box> const & box);
  temper::core::Str replace(std::shared_ptr<Box> const & old);
  temper::core::Str replaceItem(std::shared_ptr<Item> const & old);
  temper::core::Str run();
  temper::core::Str runItem();
  temper::core::Str drop();
  std::shared_ptr<Box> get_box();
};

struct Item : public std::enable_shared_from_this<Item> {
  temper::core::Str name;
  static std::shared_ptr<Item> make(temper::core::Str const & name);
  temper::core::Str leave(std::shared_ptr<Holder> const & h) const;
  temper::core::Str get_name() const;
};

struct Slot : public std::enable_shared_from_this<Slot> {
  std::shared_ptr<Item> item;
  temper::core::NullableParam<temper::core::Str> tag;
  static std::shared_ptr<Slot> make(std::shared_ptr<Item> const & item, temper::core::NullableParam<temper::core::Str> tag);
  temper::core::Str swap(std::shared_ptr<Item> const & old);
  temper::core::Str run();
  temper::core::Str relabel();
  std::shared_ptr<Item> get_item();
  temper::core::NullableParam<temper::core::Str> get_tag();
};

struct Names : public std::enable_shared_from_this<Names> {
  std::shared_ptr<std::vector<temper::core::Str>> list;
  static std::shared_ptr<Names> make(std::shared_ptr<std::vector<temper::core::Str>> const & list);
  temper::core::Str swap(std::shared_ptr<std::vector<temper::core::Str>> const & old);
  temper::core::Str run();
  temper::core::Str firstThen();
  std::shared_ptr<std::vector<temper::core::Str>> get_list();
};

}  // namespace lifetimes

// init.cpp
#include <lifetimes/init.hpp>

#include <temper-core/list.hpp>
#include <temper-core/module_init.hpp>
#include <temper-core/console.hpp>
#include <string>

namespace lifetimes {

namespace core = temper::core;

std::shared_ptr<Box> Box::make(std::shared_ptr<Item> const & item) {
  std::shared_ptr<Box> self = std::make_shared<Box>();
  self->item = item;
  return self;
}

std::shared_ptr<Item> Box::get_item() {
  return this->item;
}

std::shared_ptr<Holder> Holder::make(std::shared_ptr<Box> const & box) {
  std::shared_ptr<Holder> self = std::make_shared<Holder>();
  self->box = box;
  return self;
}

core::Str Holder::replace(std::shared_ptr<Box> const & old) {
  std::shared_ptr<Holder> self = shared_from_this();
  self->box = Box::make(Item::make(TEMPER_STR("new")));
  return old->item->name;
}

core::Str Holder::replaceItem(std::shared_ptr<Item> const & old) {
  std::shared_ptr<Holder> self = shared_from_this();
  self->box = Box::make(Item::make(TEMPER_STR("newer")));
  return old->name;
}

core::Str Holder::run() {
  std::shared_ptr<Holder> self = shared_from_this();
  std::shared_ptr<Box> value = self->box;
  return self->replace(value);
}

core::Str Holder::runItem() {
  std::shared_ptr<Holder> self = shared_from_this();
  std::shared_ptr<Item> value = self->box->item;
  return self->replaceItem(value);
}

core::Str Holder::drop() {
  std::shared_ptr<Holder> self = shared_from_this();
  return self->box->item->leave(self);
}

std::shared_ptr<Box> Holder::get_box() {
  return this->box;
}

std::shared_ptr<Item> Item::make(core::Str const & name) {
  std::shared_ptr<Item> self = std::make_shared<Item>();
  self->name = name;
  return self;
}

core::Str Item::leave(std::shared_ptr<Holder> const & h) const {
  std::shared_ptr<Item const> self = shared_from_this();
  h->box = Box::make(Item::make(TEMPER_STR("other")));
  return self->name;
}

core::Str Item::get_name() const {
  return this->name;
}

std::shared_ptr<Slot> Slot::make(std::shared_ptr<Item> const & item, core::NullableParam<core::Str> tag) {
  std::shared_ptr<Slot> self = std::make_shared<Slot>();
  self->item = item;
  self->tag = tag;
  return self;
}

core::Str Slot::swap(std::shared_ptr<Item> const & old) {
  this->item = nullptr;
  this->tag = nullptr;
  if (old == nullptr) {
    return TEMPER_STR("none");
  } else {
    return old->name;
  }
}

core::Str Slot::run() {
  std::shared_ptr<Slot> self = shared_from_this();
  std::shared_ptr<Item> value = self->item;
  return self->swap(value);
}

core::Str Slot::relabel() {
  core::NullableParam<core::Str> was = this->tag;
  this->tag = TEMPER_STR("after");
  {
    if (was == nullptr) {
      return TEMPER_STR("unlabeled");
    } else {
      return was.value;
    }
  }
}

std::shared_ptr<Item> Slot::get_item() {
  return this->item;
}

core::NullableParam<core::Str> Slot::get_tag() {
  return this->tag;
}

static std::shared_ptr<Item> maybeItem(bool make_) {
  if (make_) {
    return Item::make(TEMPER_STR("temp"));
  } else {
    return nullptr;
  }
}

std::shared_ptr<Names> Names::make(std::shared_ptr<std::vector<core::Str>> const & list) {
  std::shared_ptr<Names> self = std::make_shared<Names>();
  self->list = list;
  return self;
}

core::Str Names::swap(std::shared_ptr<std::vector<core::Str>> const & old) {
  std::shared_ptr<Names> self = shared_from_this();
  self->list = core::List::make<core::Str>(TEMPER_STR("fresh"));
  return core::List::get<core::Str>(old, 0);
}

core::Str Names::run() {
  std::shared_ptr<Names> self = shared_from_this();
  std::shared_ptr<std::vector<core::Str>> value = self->list;
  return self->swap(value);
}

core::Str Names::firstThen() {
  std::shared_ptr<Names> self = shared_from_this();
  core::Str first = core::List::get<core::Str>(self->list, 0);
  self->list = core::List::make<core::Str>(TEMPER_STR("replaced"));
  return first;
}

std::shared_ptr<std::vector<core::Str>> Names::get_list() {
  return this->list;
}

static std::shared_ptr<std::vector<core::Str>> made() {
  return core::List::make<core::Str>(TEMPER_STR("made"), TEMPER_STR("list"));
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    std::shared_ptr<Holder> h = Holder::make(Box::make(Item::make(TEMPER_STR("old"))));
    core::Console::log(console, h->run());
    core::Console::log(console, h->runItem());
    core::Console::log(console, h->box->item->name);
    core::Console::log(console, h->drop());
    core::Console::log(console, h->box->item->name);
    core::Console::log(console, Holder::make(Box::make(Item::make(TEMPER_STR("chained"))))->box->item->name);
    std::shared_ptr<Slot> slot = Slot::make(Item::make(TEMPER_STR("slotted")), TEMPER_STR("before"));
    core::Console::log(console, slot->run());
    core::Console::log(console, slot->run());
    slot->tag = TEMPER_STR("first");
    core::Console::log(console, slot->relabel());
    core::Console::log(console, slot->relabel());
    std::shared_ptr<Item> subject = maybeItem(true);
    core::Console::log(console, (subject == nullptr ? Item::make(TEMPER_STR("fallback")) : subject)->name);
    std::shared_ptr<Item> subject2 = maybeItem(false);
    core::Console::log(console, (subject2 == nullptr ? Item::make(TEMPER_STR("fallback")) : subject2)->name);
    std::shared_ptr<Item> got = maybeItem(true);
    if (got != nullptr) {
      core::Console::log(console, got->name);
    }
    std::shared_ptr<Names> names = Names::make(core::List::make<core::Str>(TEMPER_STR("kept")));
    core::Console::log(console, names->run());
    core::Console::log(console, names->firstThen());
    core::Console::log(console, core::List::get<core::Str>(names->list, 0));
    core::Str second = core::List::get<core::Str>(made(), 1);
    core::Console::log(console, second);
    core::Console::log(console, std::to_string(core::List::length<core::Str>(made())));
  });
}

}  // namespace lifetimes
