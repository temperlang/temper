#pragma once
// The whole runtime. Generated headers include only `base.hpp`, and
// what else they use; this is for host code that wants it all.
#include "base.hpp"
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
