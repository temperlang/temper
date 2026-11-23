// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace sealed_whens {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct Shape;
struct Dot;
struct Line;
struct Namer;

struct Shape : public virtual temper::core::AnyValueBase {
  virtual int32_t get_size() const = 0;
};

struct Dot : public virtual Shape {
  int32_t size;
  static std::shared_ptr<Dot> make(int32_t size);
  int32_t get_size() const override;
};

struct Line : public virtual Shape {
  int32_t size;
  static std::shared_ptr<Line> make(int32_t size);
  int32_t get_size() const override;
};

struct Namer : public std::enable_shared_from_this<Namer> {
  static std::shared_ptr<Namer> make();
  temper::core::Str name(std::shared_ptr<Shape> const & s) const;
};

}  // namespace sealed_whens

// init.cpp
#include <sealed-whens/init.hpp>

namespace sealed_whens {

namespace core = temper::core;

std::shared_ptr<Dot> Dot::make(int32_t size) {
  std::shared_ptr<Dot> self = std::make_shared<Dot>();
  self->size = size;
  return self;
}

int32_t Dot::get_size() const {
  return this->size;
}

std::shared_ptr<Line> Line::make(int32_t size) {
  std::shared_ptr<Line> self = std::make_shared<Line>();
  self->size = size;
  return self;
}

int32_t Line::get_size() const {
  return this->size;
}

static std::shared_ptr<Shape> shapeOf(int32_t n) {
  if (n > 1) {
    return core::to_base<Shape>(Line::make(n));
  } else {
    return core::to_base<Shape>(Dot::make(n));
  }
}

static core::Str named(std::shared_ptr<Shape> const & s) {
  core::Str name = dynamic_cast<Dot const *>(s.get()) != nullptr ? TEMPER_STR("dot") : dynamic_cast<Line const *>(s.get()) != nullptr ? TEMPER_STR("line") : core::panic<core::Str>();
  return name;
}

static core::Str pair(std::shared_ptr<Shape> const & s, std::shared_ptr<Shape> const & t) {
  {
    if (dynamic_cast<Dot const *>(s.get()) != nullptr) {
      if (dynamic_cast<Dot const *>(t.get()) != nullptr) {
        return TEMPER_STR("dd");
      } else if (dynamic_cast<Line const *>(t.get()) != nullptr) {
        return TEMPER_STR("dl");
      } else {
        core::panic();
      }
    } else if (dynamic_cast<Line const *>(s.get()) != nullptr) {
      return TEMPER_STR("l");
    } else {
      core::panic();
    }
  }
}

static int32_t doubled(int32_t n) {
  return core::Int::mul(n, 2);
}

static int32_t code(std::shared_ptr<Shape> const & s, bool early) {
  if (early) {
    return dynamic_cast<Dot const *>(s.get()) != nullptr ? 1 : dynamic_cast<Line const *>(s.get()) != nullptr ? 2 : core::panic<int32_t>();
  }
  if (s->get_size() > 1) {
    {
      if (dynamic_cast<Dot const *>(s.get()) != nullptr) {
        return 10;
      } else if (dynamic_cast<Line const *>(s.get()) != nullptr) {
        return 20;
      } else {
        core::panic();
      }
    }
  } else {
    return doubled(dynamic_cast<Dot const *>(s.get()) != nullptr ? 3 : dynamic_cast<Line const *>(s.get()) != nullptr ? 4 : core::panic<int32_t>());
  }
}

std::shared_ptr<Namer> Namer::make() {
  std::shared_ptr<Namer> self = std::make_shared<Namer>();
  return self;
}

core::Str Namer::name(std::shared_ptr<Shape> const & s) const {
  {
    if (dynamic_cast<Dot const *>(s.get()) != nullptr) {
      return TEMPER_STR("a dot");
    } else if (dynamic_cast<Line const *>(s.get()) != nullptr) {
      return TEMPER_STR("a line");
    } else {
      core::panic();
    }
  }
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    std::shared_ptr<Shape> dot = shapeOf(1);
    std::shared_ptr<Shape> line = shapeOf(2);
    core::Str string = sealed_whens::named(dot);
    core::Str value = sealed_whens::named(line);
    core::Str value2 = sealed_whens::pair(dot, line);
    core::Console::log(console, string + " " + value + " " + value2 + " " + sealed_whens::pair(line, dot));
    int32_t int32 = sealed_whens::code(line, true);
    int32_t value3 = sealed_whens::code(dot, false);
    int32_t value4 = sealed_whens::code(line, false);
    core::Console::log(console, std::to_string(int32) + " " + std::to_string(value3) + " " + std::to_string(value4) + " " + Namer::make()->name(line));
  });
}

}  // namespace sealed_whens
