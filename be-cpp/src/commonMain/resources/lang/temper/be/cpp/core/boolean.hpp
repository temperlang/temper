#pragma once
#include <string>
#include "str.hpp"

namespace temper {
    namespace core {

        namespace Boolean {

            inline Str toString(bool b) {
                return b ? TEMPER_STR("true") : TEMPER_STR("false");
            }

        }

    }
}
