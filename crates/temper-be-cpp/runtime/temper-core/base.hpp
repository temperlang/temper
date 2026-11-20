#pragma once
// What every generated header includes: the runtime's types that library
// signatures show (`Str`, `NullableParam`, `Pair`, `GlobalSlot`,
// `AnyValueBase`, lists), and the standard headers generated code takes
// as given. A header or `.cpp` that uses more of the runtime (`Int`,
// `String`, `Mapped`, `Regex`, `Promise`, and the rest) includes those
// headers itself, so a file that includes a library's header parses only
// what it needs. `core.hpp` includes everything.

// The runtime's version, which `temper build` writes in.
#define TEMPER_CORE_VERSION "0.0.0"
#include <algorithm>
#include <atomic>
#include <cstdint>
#include <functional>
#include <memory>
#include <mutex>
#include <sstream>
#include <string>
#include <type_traits>
#include <utility>
#include <vector>
#include "temper_bubble.hpp"
#include "any_value.hpp"
#include "nullable_param.hpp"
#include "pair.hpp"
#include "str.hpp"
#include "base_types.hpp"
#include "list.hpp"
#include "module_init.hpp"
