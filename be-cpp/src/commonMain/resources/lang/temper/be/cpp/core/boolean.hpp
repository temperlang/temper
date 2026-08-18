#pragma once
#include <string>

namespace temper {
    namespace core {

        namespace Boolean {

            inline std::string toString(bool b) {
                return b ? "true" : "false";
            }

        }

    }
}
