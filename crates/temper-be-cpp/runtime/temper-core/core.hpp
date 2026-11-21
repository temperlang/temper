#pragma once
// The whole runtime, for host code and users that want it all.
// Generated headers and `.cpp`s include only the runtime's headers they
// use.

// The runtime's version, which `temper build` writes in.
#define TEMPER_CORE_VERSION "0.0.0"
#include "temper_bubble.hpp"
#include "any_value.hpp"
#include "nullable_param.hpp"
#include "pair.hpp"
#include "str.hpp"
#include "base_types.hpp"
#include "list.hpp"
#include "module_init.hpp"
#include "casting.hpp"
#include "compare.hpp"
#include "boolean.hpp"
#include "int.hpp"
#include "int64.hpp"
#include "float64.hpp"
#include "console.hpp"
#include "string.hpp"
#include "string_builder.hpp"
#include "list_builder.hpp"
#include "mapped.hpp"
#include "map_builder.hpp"
#include "map.hpp"
#include "deque.hpp"
#include "dense_bit_vector.hpp"
#include "date.hpp"
#include "regex.hpp"
#include "generator.hpp"
#include "promise.hpp"

namespace temper {
    namespace core {

        template<class Elem, class... Args>
        std::shared_ptr<std::vector<Elem>> make(Args... args) {
            return List::make<Elem>(args...);
        }

    }
}
