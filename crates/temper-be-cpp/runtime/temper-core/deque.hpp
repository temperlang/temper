#pragma once
#include <deque>
#include <memory>
#include "temper_bubble.hpp"
#include "base_types.hpp"

namespace temper {
    namespace core {

        namespace Queue {

            template<class Elem>
            std::shared_ptr<std::deque<Elem>> make() {
                return std::make_shared<std::deque<Elem>>();
            }

            template<class Elem>
            void add(const std::shared_ptr<std::deque<Elem>>& dq, typename NonDeduced<Elem>::type elem) {
                dq->push_back(elem);
            }

            template<class Elem>
            bool isEmpty(const std::shared_ptr<std::deque<Elem>>& dq) {
                return dq->empty();
            }

            template<class Elem>
            Elem removeFirst(const std::shared_ptr<std::deque<Elem>>& dq) {
                if (dq->empty()) {
                    // Panics, as reading a list out of bounds does.
                    panic<Elem>("index 0 out of bounds for length 0");
                }
                Elem front = dq->front();
                dq->pop_front();
                return front;
            }

        }

    }
}
