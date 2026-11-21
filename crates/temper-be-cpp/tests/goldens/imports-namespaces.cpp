// shapes.hpp
#pragma once

#include <temper-core/core.hpp>

namespace namespaces {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_shapes();

template <class T>
struct Box;
struct Shape;
struct Square;

template <class T>
struct Box : public std::enable_shared_from_this<Box<T>> {
  T value;
  static std::shared_ptr<Box<T>> make(T value);
  T get_value() const;
};

struct Shape : public virtual temper::core::AnyValueBase {
  virtual int32_t area() const = 0;
};

struct Square : public virtual Shape {
  int32_t side;
  static std::shared_ptr<Square> make(int32_t side);
  int32_t area() const override;
  static std::shared_ptr<Square> sized(int32_t side);
  int32_t get_side() const;
};

// Storage, which the library reads and its init sets; read values
// through the accessors below.
namespace temper_globals {
extern temper::core::GlobalSlot<int32_t> unit;
}  // namespace temper_globals

int32_t const & unit();

int32_t twice(int32_t x);

template <class T>
std::shared_ptr<Box<T>> Box<T>::make(T value) {
  global_init_shapes();
  std::shared_ptr<Box<T>> self = std::make_shared<Box<T>>();
  self->value = value;
  return self;
}

template <class T>
T Box<T>::get_value() const {
  return this->value;
}

}  // namespace namespaces

// shapes.cpp
#include <namespaces/shapes.hpp>

namespace namespaces {

namespace core = temper::core;

namespace temper_globals {

core::GlobalSlot<int32_t> unit;

}  // namespace temper_globals

int32_t const & unit() {
  global_init_shapes();
  return temper_globals::unit.value;
}

int32_t twice(int32_t x) {
  global_init_shapes();
  return core::Int::mul(x, 2);
}

std::shared_ptr<Square> Square::make(int32_t side) {
  global_init_shapes();
  std::shared_ptr<Square> self = std::make_shared<Square>();
  self->side = side;
  return self;
}

int32_t Square::area() const {
  return core::Int::mul(this->side, this->side);
}

std::shared_ptr<Square> Square::sized(int32_t side) {
  global_init_shapes();
  return Square::make(side);
}

int32_t Square::get_side() const {
  return this->side;
}

void global_init_shapes() {
  core::init_once([] {
    temper_globals::unit.construct();
    temper_globals::unit.value = 1;
  });
}

}  // namespace namespaces

// init.hpp
#pragma once

#include <temper-core/core.hpp>
#include <namespaces/shapes.hpp>

namespace namespaces {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct Circle;

struct Circle : public virtual Shape {
  int32_t r;
  static std::shared_ptr<Circle> make(int32_t r);
  int32_t area() const override;
  int32_t get_r() const;
};

}  // namespace namespaces

// init.cpp
#include <namespaces/init.hpp>

namespace namespaces {

namespace core = temper::core;

std::shared_ptr<Circle> Circle::make(int32_t r) {
  std::shared_ptr<Circle> self = std::make_shared<Circle>();
  self->r = r;
  return self;
}

int32_t Circle::area() const {
  return core::Int::mul(core::Int::mul(3, this->r), this->r);
}

int32_t Circle::get_r() const {
  return this->r;
}

static int32_t bump(int32_t shapes) {
  return core::Int::add(shapes, 1);
}

void global_init_init() {
  core::init_once([] {
    global_init_shapes();
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    core::Console::log(console, std::to_string(temper_globals::unit.value));
    core::Console::log(console, std::to_string(twice(21)));
    std::shared_ptr<Box<int32_t>> box = Box<int32_t>::make(3);
    std::shared_ptr<std::vector<std::shared_ptr<Shape>>> squares = core::List::make<std::shared_ptr<Shape>>(core::to_base<Shape>(Square::make(4)), core::to_base<Shape>(Square::make(temper_globals::unit.value)), core::to_base<Shape>(Square::sized(5)));
    {
      int32_t length = core::List::length<std::shared_ptr<Shape>>(squares);
      int32_t i = 0;
      while (i < length) {
        std::shared_ptr<Shape> s = core::List::get<std::shared_ptr<Shape>>(squares, i);
        i = core::Int::add(i, 1);
        core::Console::log(console, "area " + std::to_string(s->area()));
      }
    }
    std::shared_ptr<std::vector<std::shared_ptr<Shape>>> mixed = core::List::make<std::shared_ptr<Shape>>(core::to_base<Shape>(Circle::make(2)), core::to_base<Shape>(Square::make(2)));
    {
      int32_t length2 = core::List::length<std::shared_ptr<Shape>>(mixed);
      int32_t i2 = 0;
      while (i2 < length2) {
        std::shared_ptr<Shape> s2 = core::List::get<std::shared_ptr<Shape>>(mixed, i2);
        i2 = core::Int::add(i2, 1);
        {
          if (dynamic_cast<Square const *>(s2.get()) != nullptr) {
            core::Console::log(console, "a square of " + std::to_string(core::checked_cast<Square>(s2)->area()));
          } else {
            core::Console::log(console, "something else of " + std::to_string(s2->area()));
          }
        }
      }
    }
    core::Console::log(console, std::to_string(bump(box->value)));
  });
}

}  // namespace namespaces

// main.cpp
#include "namespaces/shapes.hpp"
#include "namespaces/init.hpp"

int main() {
  namespaces::global_init_shapes();
  namespaces::global_init_init();
}

// CMakeLists.txt
# Generated by Temper.
cmake_minimum_required(VERSION 3.10)
project(namespaces LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
find_package(Threads REQUIRED)

add_library(namespaces shapes.cpp init.cpp)
target_include_directories(namespaces PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/..)
target_link_libraries(namespaces PUBLIC Threads::Threads)

add_executable(namespaces-main main.cpp)
target_link_libraries(namespaces-main PRIVATE namespaces)

