#pragma once
#include <memory>

namespace temper {
    namespace core {

        template<class A, class B>
        struct Pair {
            A key;
            B value;

            Pair() = default;

            Pair(A k, B v) : key(std::move(k)), value(std::move(v)) {}

            A get_key() const {
                return key;
            }

            B get_value() const {
                return value;
            }

            A first() const {
                return key;
            }

            B second() const {
                return value;
            }
        };

        namespace PairFactory {

            template<class A, class B>
            std::shared_ptr<Pair<A, B>> make(A a, B b) {
                return std::make_shared<Pair<A, B>>(std::move(a), std::move(b));
            }

        }

    }
}
