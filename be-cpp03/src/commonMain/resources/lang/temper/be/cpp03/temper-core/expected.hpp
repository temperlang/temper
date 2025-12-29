#ifndef TEMPER_CORE_EXPECTED_HPP
#define TEMPER_CORE_EXPECTED_HPP

#ifdef __has_include
# if __has_include(<expected>)
#   include <expected>
# endif
#endif // __has_include

namespace temper {
namespace core {

// TODO Support string or refcounted strings instead?
typedef const char* Error;

#if __cpp_lib_expected

using Unexpected = std::unexpected<Error>;

template<typename T>
using Expected = std::expected<T, Error>;

#else // not __cplusplus >= 202302L

class Unexpected {
public:
  Unexpected(Error error): err(error) {}

  Error error() const {
    return err;
  }

private:
  Error err;
};

template<typename T>
class Expected {
public:
  explicit Expected(const T& v): has_val(true) {
    new(&storage.value) T(v);
  }

  Expected(const Unexpected& u): has_val(false) {
    storage.error = u.error();
  }

  ~Expected() {
    if (has_val) {
      storage.value.~T();
    } else {
      // TODO Destruct if we support allocated errors.
      // storage.error.~Error();
    }
  }

  bool has_value() const {
    return has_val;
  }

  const Error& error() const {
    if (has_val) throw std::logic_error("has");
    return storage.error;
  }

  Error& error() {
    if (has_val) throw std::logic_error("has");
    return storage.error;
  }

  const T& value() const {
    if (!has_val) throw std::logic_error("!has");
    return storage.value;
  }

  T& value() {
    if (!has_val) throw std::logic_error("!has");
    return storage.value;
  }

  const T& operator*() const {
    return storage.value;
  }

  T& operator*() {
    return storage.value;
  }

private:
  union {
    T value;
    Error error;
  } storage;

  bool has_val;
};

#endif // __cplusplus >= 202302L else

} // namespace core
} // namespace temper

#endif // TEMPER_CORE_EXPECTED_HPP
