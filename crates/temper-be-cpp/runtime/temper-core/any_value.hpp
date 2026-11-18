#pragma once
#include <memory>
#include "temper_bubble.hpp"

namespace temper {
    namespace core {

        // Every polymorphic Temper object shares a single (virtually inherited) `AnyValueBase`
        // root. Deriving it from enable_shared_from_this lets `borrow_this` recover a properly
        // owning shared_ptr for `this` (objects are always created via make_shared), instead of
        // a non-owning alias that would dangle if a callee stored it.
        struct AnyValueBase : std::enable_shared_from_this<AnyValueBase> {
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
