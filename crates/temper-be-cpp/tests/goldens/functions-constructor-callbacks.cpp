// init.hpp
#pragma once

#include <temper-core/base.hpp>

namespace constructor_callbacks {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

template <class T>
struct Hi;

template <class T>
struct Hi : public std::enable_shared_from_this<Hi<T>> {
  std::function<T(T)> ha;
  static std::shared_ptr<Hi<T>> make(std::function<T(T)> const & ha);
  std::function<T(T)> get_ha() const;
};

std::shared_ptr<Hi<int32_t>> newIntHi(int32_t j);
std::shared_ptr<Hi<int32_t>> makeIntHi(int32_t j);

template <class T>
std::shared_ptr<Hi<T>> Hi<T>::make(std::function<T(T)> const & ha) {
  std::shared_ptr<Hi<T>> self = std::make_shared<Hi<T>>();
  self->ha = ha;
  return self;
}

template <class T>
std::function<T(T)> Hi<T>::get_ha() const {
  return this->ha;
}

template <class T>
std::shared_ptr<Hi<T>> makeHi(std::function<T(T)> const & ha) {
  return Hi<T>::make(ha);
}

}  // namespace constructor_callbacks

// init.cpp
#include <constructor-callbacks/init.hpp>

#include <temper-core/int.hpp>

namespace constructor_callbacks {

namespace core = temper::core;

std::shared_ptr<Hi<int32_t>> newIntHi(int32_t j) {
  return Hi<int32_t>::make([=](int32_t i) -> int32_t { return core::Int::add(i, j); });
}

std::shared_ptr<Hi<int32_t>> makeIntHi(int32_t j) {
  return constructor_callbacks::makeHi<int32_t>([=](int32_t i) -> int32_t { return core::Int::add(i, j); });
}

void global_init_init() {}

}  // namespace constructor_callbacks
