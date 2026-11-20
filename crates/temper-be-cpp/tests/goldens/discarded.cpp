// init.hpp
#pragma once

#include <temper-core/base.hpp>

namespace discarded {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct Lexer;

struct Lexer : public std::enable_shared_from_this<Lexer> {
  int32_t seen;
  static std::shared_ptr<Lexer> make();
  bool get_inComment() const;
  temper::core::NullableParam<temper::core::Str> scan(temper::core::Str const & text);
  int32_t get_seen();
};

}  // namespace discarded

// init.cpp
#include <discarded/init.hpp>

#include <temper-core/int.hpp>
#include <temper-core/console.hpp>

namespace discarded {

namespace core = temper::core;

std::shared_ptr<Lexer> Lexer::make() {
  std::shared_ptr<Lexer> self = std::make_shared<Lexer>();
  self->seen = 0;
  return self;
}

bool Lexer::get_inComment() const {
  return this->seen > 0;
}

core::NullableParam<core::Str> Lexer::scan(core::Str const &) {
  this->seen = core::Int::add(this->seen, 1);
  if (this->seen > 100) {
    return TEMPER_STR("too long");
  } else {
    return nullptr;
  }
}

int32_t Lexer::get_seen() {
  return this->seen;
}

static core::Str finish(std::shared_ptr<std::vector<core::Str>> const & texts) {
  std::shared_ptr<Lexer> lexer = Lexer::make();
  core::NullableParam<core::Str> problem = nullptr;
  {
    int32_t length = core::List::length<core::Str>(texts);
    int32_t i = 0;
    while (i < length) {
      core::Str text = core::List::get<core::Str>(texts, i);
      i = core::Int::add(i, 1);
      core::NullableParam<core::Str> found = nullptr;
      found = lexer->scan(text);
      if (problem == nullptr) {
        problem = found;
      }
    }
  }
  if (lexer->get_inComment()) {
    lexer->scan(TEMPER_STR("\n"));
  }
  if (problem == nullptr) {
    return "ok " + std::to_string(lexer->seen);
  } else {
    return TEMPER_STR("problem");
  }
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    core::Console::log(console, discarded::finish(core::List::make<core::Str>(TEMPER_STR("a"), TEMPER_STR("b"))));
  });
}

}  // namespace discarded
