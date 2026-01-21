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
  template<typename U>
  friend class Shared;

  T* ptr;
  size_t* count;

  void acquire() {
    if (count) {
      *count += 1;
    }
  }

  void release() {
    if (count) {
      *count -= 1;
      if (!*count) {
        delete ptr;
        delete count;
      }
      ptr = 0;
      count = 0;
    }
  }

public:
  explicit Shared(T* ptr = 0):
      ptr(ptr), count(ptr ? new size_t(1) : 0) {}

  Shared(const Shared& other): ptr(other.ptr), count(other.count) {
    acquire();
  }

  template<typename U>
  Shared(const Shared<U>& other): ptr(other.ptr), count(other.count) {
    acquire();
  }

  ~Shared() {
    release();
  }

  void reset() {
    release();
  }

  Shared& operator=(const Shared& other) {
    if (this != &other) {
      release();
      ptr = other.ptr;
      count = other.count;
      acquire();
    }
    return *this;
  }

  T* get() const { return ptr; }
  T& operator*() const { assert(ptr); return *ptr; }
  T* operator->() const { assert(ptr); return ptr; }
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
