#pragma once
#include <memory>
#include "temper_bubble.hpp"
#include "base_types.hpp"
#include "any_value.hpp"

namespace temper {
    namespace core {

        template<class Target, class Source>
        std::shared_ptr<Target> checked_cast(const std::shared_ptr<Source>& src) {
            std::shared_ptr<Target> result = std::dynamic_pointer_cast<Target>(src);
            if (result == nullptr) {
                throw TemperBubble("bad cast");
            }
            return result;
        }

        // True iff [src] is an AnyValue box currently holding a [T]. Used to implement
        // `x is SomeValueType` when x is a boxed AnyValue: a plain non-null check would
        // wrongly accept a box holding a different type. Mirrors Rust's temper_core::is.
        template<class T>
        bool is_box(const std::shared_ptr<AnyValueBase>& src) {
            return std::dynamic_pointer_cast<AnyValueBox<T>>(src) != nullptr;
        }

        template<class T>
        std::shared_ptr<AnyValueBox<T>> checked_cast_box(const std::shared_ptr<AnyValueBase>& src) {
            std::shared_ptr<AnyValueBox<T>> result = std::dynamic_pointer_cast<AnyValueBox<T>>(src);
            if (result == nullptr) {
                throw TemperBubble("bad cast: AnyValue does not hold expected type");
            }
            return result;
        }

        // Like [checked_cast], but (a) propagates null unchanged instead of throwing, and
        // (b) uses the aliasing shared_ptr constructor over a raw `dynamic_cast<Target*>`.
        // The aliasing form is what makes multiple/virtual inheritance correct: it stores the
        // base-subobject pointer `raw` (which dynamic_cast adjusts for non-leftmost bases) while
        // sharing the original control block, whereas dynamic_pointer_cast would only work for
        // hierarchies where no pointer adjustment is needed. Used by the `coerce` conversion path.
        template<class Target, class Source>
        std::shared_ptr<Target> temper_upcast(const std::shared_ptr<Source>& src) {
            if (src == nullptr) {
                return std::shared_ptr<Target>();
            }
            Target* raw = dynamic_cast<Target*>(src.get());
            if (raw == nullptr) {
                throw TemperBubble("bad upcast");
            }
            return std::shared_ptr<Target>(src, raw);
        }

        template<class Source>
        struct Coercible {
            std::shared_ptr<Source> ptr;

            Coercible(const std::shared_ptr<Source>& p) : ptr(p) {}

            template<class Target>
            operator std::shared_ptr<Target>() const {
                return temper_upcast<Target>(ptr);
            }
        };

        template<class Source>
        Coercible<Source> coerce(const std::shared_ptr<Source>& src) {
            return Coercible<Source>(src);
        }

    }
}
