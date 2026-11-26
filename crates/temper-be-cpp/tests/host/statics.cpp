// Host code using the generated `classes` library's static field, for
// `functional.rs`'s `statics_private`: it reads `Rect::unit()`, and the
// storage behind it, `temper_statics`, is out of its reach, so it can't
// reassign the value. Compiled only (`-fsyntax-only`).
#include "classes/init.hpp"
#include <memory>
#include <type_traits>

namespace {

template <class...>
struct make_void {
    typedef void type;
};

// Whether `T::temper_statics` is a type code here may name: access is
// checked in substitution, so a private one isn't.
template <class T, class = void>
struct statics_reachable : std::false_type {};

template <class T>
struct statics_reachable<T, typename make_void<typename T::temper_statics>::type>
    : std::true_type {};

static_assert(!statics_reachable<classes::Rect>::value,
              "host code reaches Rect::temper_statics");
static_assert(
    std::is_same<decltype(classes::Rect::unit()), std::shared_ptr<classes::Rect> const &>::value,
    "Rect::unit() reads the value, read-only");

}  // namespace
