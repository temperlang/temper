// init.hpp
#pragma once

#include <temper-core/any_value.hpp>
#include <temper-core/str.hpp>
#include <cstdint>
#include <memory>

namespace extra_class {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

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

}  // namespace extra_class

// init.cpp
#include <extra-class/init.hpp>

#include <string>

namespace extra_class {

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

void global_init_init() {}

}  // namespace extra_class
