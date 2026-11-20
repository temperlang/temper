// init.hpp
#pragma once

#include <temper-core/str.hpp>
#include <cstdint>
#include <memory>

namespace sorting {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct Entry;

struct Entry : public std::enable_shared_from_this<Entry> {
  int32_t id;
  int32_t group;
  static std::shared_ptr<Entry> make(int32_t id, int32_t group);
  temper::core::Str toString() const;
  int32_t get_id() const;
  int32_t get_group() const;
};

}  // namespace sorting

// init.cpp
#include <sorting/init.hpp>

#include <temper-core/list.hpp>
#include <temper-core/module_init.hpp>
#include <temper-core/int.hpp>
#include <temper-core/float64.hpp>
#include <temper-core/console.hpp>
#include <temper-core/list_builder.hpp>
#include <functional>
#include <string>
#include <vector>

namespace sorting {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;

}  // namespace temper_globals

std::shared_ptr<Entry> Entry::make(int32_t id, int32_t group) {
  std::shared_ptr<Entry> self = std::make_shared<Entry>();
  self->id = id;
  self->group = group;
  return self;
}

core::Str Entry::toString() const {
  return "{ id: " + std::to_string(this->id) + ", group: " + std::to_string(this->group) + " }";
}

int32_t Entry::get_id() const {
  return this->id;
}

int32_t Entry::get_group() const {
  return this->group;
}

static void mungeInts(std::shared_ptr<std::vector<int32_t>> const & ints) {
  std::shared_ptr<std::vector<int32_t>> builder = core::List::toListBuilder<int32_t>(ints);
  core::ListBuilder::sort(builder, [](int32_t a, int32_t b) -> int32_t { return core::Int::sub(a, b); });
  core::Console::log(temper_globals::console.value, core::List::join(core::List::sorted(ints, [](int32_t a2, int32_t b2) -> int32_t { return core::Int::sub(a2, b2); }), TEMPER_STR(", "), [](int32_t a3) -> core::Str { return std::to_string(a3); }));
  core::Console::log(temper_globals::console.value, core::List::join(builder, TEMPER_STR(", "), [](int32_t a4) -> core::Str { return std::to_string(a4); }));
}

static void mungeFloats(std::shared_ptr<std::vector<double>> const & floats) {
  std::shared_ptr<std::vector<double>> builder = core::List::toListBuilder<double>(floats);
  std::function<int32_t(double, double)> compareFloats = [](double a, double b) -> int32_t { return core::Float64::toInt32Unsafe(core::Float64::sign(a - b)); };
  core::ListBuilder::sort(builder, compareFloats);
  core::Console::log(temper_globals::console.value, core::List::join(core::List::sorted(floats, compareFloats), TEMPER_STR(", "), [](double a2) -> core::Str { return core::Float64::toString(a2); }));
  core::Console::log(temper_globals::console.value, core::List::join(builder, TEMPER_STR(", "), [](double a3) -> core::Str { return core::Float64::toString(a3); }));
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::console.value = core::Console::get_console();
    std::shared_ptr<std::vector<std::shared_ptr<Entry>>> entries = core::List::toListBuilder<std::shared_ptr<Entry>>(core::List::make<std::shared_ptr<Entry>>(Entry::make(0, 0), Entry::make(1, 1), Entry::make(2, 2), Entry::make(3, 0), Entry::make(4, 1), Entry::make(5, 2), Entry::make(6, 0), Entry::make(7, 1), Entry::make(8, 2)));
    std::shared_ptr<std::vector<std::shared_ptr<Entry>>> sortedEntries = core::List::sorted(entries, [](std::shared_ptr<Entry> a, std::shared_ptr<Entry> b) -> int32_t { return core::Int::sub(a->group, b->group); });
    core::Console::log(temper_globals::console.value, core::List::join(sortedEntries, TEMPER_STR("\n"), [](std::shared_ptr<Entry> a) -> core::Str { return a->toString(); }));
    core::Console::log(temper_globals::console.value, core::List::get<std::shared_ptr<Entry>>(entries, 1)->toString());
    core::ListBuilder::sort(entries, [](std::shared_ptr<Entry> a, std::shared_ptr<Entry> b) -> int32_t { return core::Int::sub(a->group, b->group); });
    core::Console::log(temper_globals::console.value, core::List::join(entries, TEMPER_STR("\n"), [](std::shared_ptr<Entry> a) -> core::Str { return a->toString(); }));
    sorting::mungeInts(core::List::make<int32_t>(2, 1));
    sorting::mungeFloats(core::List::make<double>(3.4, 1.2));
  });
}

}  // namespace sorting
