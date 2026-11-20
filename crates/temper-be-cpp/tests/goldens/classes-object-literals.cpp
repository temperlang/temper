// extra-class.hpp
#pragma once

#include <temper-core/core.hpp>

namespace object_literals {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_extra_class();

struct Stringable;
struct Circle;
struct Rectangle;

struct Stringable : public virtual temper::core::AnyValueBase {
  virtual temper::core::Str toString() const = 0;
};

struct Circle : public virtual Stringable {
  int32_t radius;
  static std::shared_ptr<Circle> make(int32_t radius);
  temper::core::Str toString() const override;
  int32_t get_radius() const;
};

struct Rectangle : public std::enable_shared_from_this<Rectangle> {
  int32_t width;
  int32_t height;
  static std::shared_ptr<Rectangle> make(int32_t width, int32_t height);
  int32_t get_width() const;
  int32_t get_height() const;
};

}  // namespace object_literals

// extra-class.cpp
#include <object-literals/extra-class.hpp>

namespace object_literals {

namespace core = temper::core;

std::shared_ptr<Circle> Circle::make(int32_t radius) {
  std::shared_ptr<Circle> self = std::make_shared<Circle>();
  self->radius = radius;
  return self;
}

core::Str Circle::toString() const {
  return "Circle of radius " + std::to_string(this->radius);
}

int32_t Circle::get_radius() const {
  return this->radius;
}

std::shared_ptr<Rectangle> Rectangle::make(int32_t width, int32_t height) {
  std::shared_ptr<Rectangle> self = std::make_shared<Rectangle>();
  self->width = width;
  self->height = height;
  return self;
}

int32_t Rectangle::get_width() const {
  return this->width;
}

int32_t Rectangle::get_height() const {
  return this->height;
}

void global_init_extra_class() {}

}  // namespace object_literals

// init.hpp
#pragma once

#include <temper-core/core.hpp>
#include <object-literals/extra-class.hpp>

namespace object_literals {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct Person;
struct Whatever;
struct Moreover;

struct Person : public virtual Stringable {
  temper::core::Str name;
  int32_t age;
  static std::shared_ptr<Person> make(temper::core::Str const & name, temper::core::NullableParam<int32_t> age);
  static std::shared_ptr<Person> make(temper::core::Str const & name);
  temper::core::Str toString() const override;
  bool eq(std::shared_ptr<Person> const & other) const;
  temper::core::Str get_name() const;
  int32_t get_age() const;
};

struct Whatever : public std::enable_shared_from_this<Whatever> {
  temper::core::Str blah;
  static std::shared_ptr<Whatever> make(int32_t blech);
  temper::core::Str get_blah() const;
};

struct Moreover : public std::enable_shared_from_this<Moreover> {
  int32_t blech;
  int32_t bling;
  static std::shared_ptr<Moreover> make(temper::core::NullableParam<int32_t> blech, temper::core::NullableParam<int32_t> bling);
  static std::shared_ptr<Moreover> make(temper::core::NullableParam<int32_t> blech);
  static std::shared_ptr<Moreover> make();
  temper::core::Str toString() const;
  int32_t get_blech() const;
  int32_t get_bling() const;
};

}  // namespace object_literals

// init.cpp
#include <object-literals/init.hpp>

namespace object_literals {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;

}  // namespace temper_globals

std::shared_ptr<Person> Person::make(core::Str const & name, core::NullableParam<int32_t> age) {
  global_init_init();
  std::shared_ptr<Person> self = std::make_shared<Person>();
  int32_t age2 = age == nullptr ? -100 : age.value;
  self->name = name;
  self->age = age2;
  return self;
}

std::shared_ptr<Person> Person::make(core::Str const & name) {
  return make(name, nullptr);
}

core::Str Person::toString() const {
  std::shared_ptr<Person const> self = std::shared_ptr<Person const>(shared_from_this(), this);
  core::Str ageText = core::List::join(core::List::make<int32_t>(0), core::Str(), [=](int32_t) -> core::Str { return std::to_string(self->age); });
  return "Person " + self->name + " of age " + ageText;
}

bool Person::eq(std::shared_ptr<Person> const & other) const {
  if (this->name == other->name) {
    return this->age == other->age;
  } else {
    return false;
  }
}

core::Str Person::get_name() const {
  return this->name;
}

int32_t Person::get_age() const {
  return this->age;
}

std::shared_ptr<Whatever> Whatever::make(int32_t blech) {
  std::shared_ptr<Whatever> self = std::make_shared<Whatever>();
  self->blah = std::to_string(blech);
  return self;
}

core::Str Whatever::get_blah() const {
  return this->blah;
}

std::shared_ptr<Moreover> Moreover::make(core::NullableParam<int32_t> blech, core::NullableParam<int32_t> bling) {
  std::shared_ptr<Moreover> self = std::make_shared<Moreover>();
  int32_t blech2 = blech == nullptr ? 0 : blech.value;
  int32_t bling2 = bling == nullptr ? 0 : bling.value;
  self->blech = blech2;
  self->bling = bling2;
  return self;
}

std::shared_ptr<Moreover> Moreover::make(core::NullableParam<int32_t> blech) {
  return make(blech, nullptr);
}

std::shared_ptr<Moreover> Moreover::make() {
  return make(nullptr, nullptr);
}

core::Str Moreover::toString() const {
  return std::to_string(this->blech) + " " + std::to_string(this->bling);
}

int32_t Moreover::get_blech() const {
  return this->blech;
}

int32_t Moreover::get_bling() const {
  return this->bling;
}

static void bother() {
  std::shared_ptr<Moreover> whatIAm = Moreover::make(1);
  std::shared_ptr<Moreover> moreover = Moreover::make(2, 3);
  core::Console::log(temper_globals::console.value, "Moreover: " + moreover->toString());
  std::shared_ptr<Moreover> moreover2 = Moreover::make(4);
  core::Console::log(temper_globals::console.value, "Moreover again: " + moreover2->toString());
  std::shared_ptr<Moreover> moreover3 = Moreover::make(nullptr, 7);
  core::Console::log(temper_globals::console.value, "Moreover yet again: " + moreover3->toString());
  std::shared_ptr<Whatever> whatever = Whatever::make(5);
  core::Console::log(temper_globals::console.value, "Whatever: " + whatever->blah);
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    global_init_extra_class();
    temper_globals::console.value = core::Console::get_console();
    std::shared_ptr<Person> person1 = Person::make(TEMPER_STR("Alice"), 50);
    std::shared_ptr<Person> person2 = Person::make(TEMPER_STR("Bob"), 45);
    std::shared_ptr<Circle> circle1 = Circle::make(2);
    std::shared_ptr<Circle> circle2 = Circle::make(1);
    std::shared_ptr<std::vector<std::shared_ptr<Stringable>>> things = core::List::make<std::shared_ptr<Stringable>>(core::to_base<Stringable>(person1), core::to_base<Stringable>(person2), core::to_base<Stringable>(circle1), core::to_base<Stringable>(circle2));
    auto fn = [](std::shared_ptr<Stringable> thing) -> int32_t {
      core::Console::log(temper_globals::console.value, thing->toString());
      return 0;
    };
    core::List::map(things, fn);
    Whatever::make(456);
    Whatever::make(321);
    Rectangle::make(7, 8);
    bother();
    std::shared_ptr<Whatever> whatever = Whatever::make(6);
    core::Console::log(temper_globals::console.value, "Whatever again: " + whatever->blah);
    std::shared_ptr<Person> personAgain = Person::make(TEMPER_STR("Carrie"), 40);
    core::Str name = personAgain->name;
    int32_t someAge = personAgain->age;
    core::Console::log(temper_globals::console.value, "name is " + name + " and age is " + std::to_string(someAge));
    std::shared_ptr<Person> personMore = Person::make(name, 35);
    core::Console::log(temper_globals::console.value, personMore->toString());
    std::shared_ptr<Person> twin = Person::make(person1->name, person1->age);
    core::Console::log(temper_globals::console.value, "Some are twins: " + core::Boolean::toString(person1->eq(twin)));
    core::Console::log(temper_globals::console.value, "All are twins: " + core::Boolean::toString(person2->eq(twin)));
  });
}

}  // namespace object_literals

// main.cpp
#include "object-literals/extra-class.hpp"
#include "object-literals/init.hpp"
#include "temper-core/stack_trace.hpp"

int main() {
  temper::core::install_stack_traces();
  object_literals::global_init_extra_class();
  object_literals::global_init_init();
}

// CMakeLists.txt
# Generated by Temper.
cmake_minimum_required(VERSION 3.10)
project(object-literals LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
find_package(Threads REQUIRED)

add_library(object-literals extra-class.cpp init.cpp)
target_include_directories(object-literals PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/..)
target_link_libraries(object-literals PUBLIC Threads::Threads)

# Stack traces for the programs, not the library (temper-core/stack_trace.hpp).
include(${CMAKE_CURRENT_SOURCE_DIR}/../temper-core/stack_trace.cmake)
add_executable(object-literals-main main.cpp)
target_link_libraries(object-literals-main PRIVATE object-literals)
temper_stack_traces(object-literals-main)

