#ifndef TEMPER_CORE_SHARED_HPP
#define TEMPER_CORE_SHARED_HPP

#include <cassert>
#include <memory>
#include <string>

namespace temper {
namespace core {

// TODO Custom override def to avoid modern if wanted?

#if __cplusplus >= 201103L

template<typename T>
using Shared = std::shared_ptr<T>;

template<class T, class... Args>
Shared<T> shared(Args&&... args) {
  return std::make_shared<T>(std::forward<Args>(args)...);
}

#else // not __cplusplus >= 201103L

template<typename T> // TODO `, typename SyncPolicy = NoSync`
class Shared {
  struct Control {
    T* base;
    unsigned count;
    Control(T* base_): base(base_), count(1) {}
  };

  Control* control;

  void acquire() {
    if (control) {
      control->count += 1;
    }
  }

  void release() {
    if (control) {
      control->count -= 1;
      if (!control->count) {
        delete control->base;
        delete control;
      }
    }
  }

public:
  explicit Shared(T* base = 0): control(base ? new Control(base) : 0) {}

  Shared(const Shared& other): control(other.control) {
    acquire();
  }

  ~Shared() {
    release();
  }

  Shared& operator=(const Shared& other) {
    if (this != &other) {
      release();
      control = other.control;
      acquire();
    }
    return *this;
  }

  T* get() const { return control ? control->base : 0; }
  T& operator*() const { assert(control); return *control->base; }
  T* operator->() const { assert(control); return control->base; }
};

template<class T>
Shared<T> shared() {
  return Shared<T>(new T());
}

template<class T, class A1>
Shared<T> shared(A1 a1) {
  return Shared<T>(new T(a1));
}

template<class T, class A1, class A2>
Shared<T> shared(A1 a1, A2 a2) {
  return Shared<T>(new T(a1, a2));
}

#endif // __cplusplus >= 201103L else

typedef Shared<std::string> String;

} // namespace core
} // namespace temper

#endif // TEMPER_CORE_SHARED_HPP
