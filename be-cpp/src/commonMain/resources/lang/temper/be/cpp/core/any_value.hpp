#pragma once
#include <memory>
#include "temper_bubble.hpp"

namespace temper {
    namespace core {

        struct AnyValueBase {
            virtual ~AnyValueBase() = default;
        };

        template<class T>
        struct AnyValueBox : AnyValueBase {
            T value;

            AnyValueBox(T v) : value(std::move(v)) {}
        };

        template<class T>
        std::shared_ptr<AnyValueBase> any_box(T value) {
            return std::make_shared<AnyValueBox<T>>(std::move(value));
        }

    }
}
