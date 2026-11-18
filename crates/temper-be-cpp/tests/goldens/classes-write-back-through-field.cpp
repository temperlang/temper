// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace write_back_through_field {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct Ping;
struct Pong;

struct Ping : public std::enable_shared_from_this<Ping> {
  std::shared_ptr<Pong> peer;
  int32_t n;
  int32_t most;
  static std::shared_ptr<Ping> make(std::shared_ptr<Pong> const & peer);
  int32_t ping();
  int32_t pass();
  bool record();
  int32_t around();
  std::shared_ptr<Pong> get_peer() const;
  int32_t get_n();
  int32_t get_most();
};

struct Pong : public std::enable_shared_from_this<Pong> {
  static std::shared_ptr<Pong> make();
  void pong(std::shared_ptr<Ping> const & a) const;
  int32_t bump(std::shared_ptr<Ping> const & a, int32_t by) const;
};

}  // namespace write_back_through_field

// init.cpp
#include <write-back-through-field/init.hpp>

namespace write_back_through_field {

namespace core = temper::core;

std::shared_ptr<Ping> Ping::make(std::shared_ptr<Pong> const & peer) {
  std::shared_ptr<Ping> self = std::make_shared<Ping>();
  self->peer = peer;
  self->n = 0;
  self->most = 0;
  return self;
}

int32_t Ping::ping() {
  std::shared_ptr<Ping> self = shared_from_this();
  self->peer->pong(self);
  return self->n;
}

int32_t Ping::pass() {
  std::shared_ptr<Ping> self = shared_from_this();
  return self->peer->bump(self, self->n);
}

bool Ping::record() {
  this->n = core::Int::add(this->n, 1);
  if (this->n > this->most) {
    this->most = this->n;
  }
  return this->n > core::Int::sub(this->most, 1);
}

int32_t Ping::around() {
  std::shared_ptr<Ping> self = shared_from_this();
  int32_t int32 = self->n;
  int32_t value = core::Int::add(int32, self->peer->bump(self, 10));
  return core::Int::add(value, self->n);
}

std::shared_ptr<Pong> Ping::get_peer() const {
  return this->peer;
}

int32_t Ping::get_n() {
  return this->n;
}

int32_t Ping::get_most() {
  return this->most;
}

std::shared_ptr<Pong> Pong::make() {
  std::shared_ptr<Pong> self = std::make_shared<Pong>();
  return self;
}

void Pong::pong(std::shared_ptr<Ping> const & a) const {
  {
    a->n = core::Int::add(a->n, 1);
  }
}

int32_t Pong::bump(std::shared_ptr<Ping> const & a, int32_t by) const {
  {
    a->n = core::Int::add(a->n, by);
  }
  return a->n;
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    std::shared_ptr<Ping> p = Ping::make(Pong::make());
    core::Console::log(console, "ping=" + std::to_string(p->ping()));
    core::Console::log(console, "pass=" + std::to_string(p->pass()));
    bool boolean = p->record();
    core::Console::log(console, "record=" + core::Boolean::toString(boolean) + " most=" + std::to_string(p->most));
    int32_t int32 = p->around();
    core::Console::log(console, "around=" + std::to_string(int32) + " n=" + std::to_string(p->n));
  });
}

}  // namespace write_back_through_field
