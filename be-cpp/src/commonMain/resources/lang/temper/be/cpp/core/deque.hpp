#pragma once
#include <deque>
#include <memory>
#include "base_types.hpp"

namespace temper {
    namespace core {

        namespace Deque {

            template<class Elem>
            std::shared_ptr<std::deque<Elem>> make() {
                return std::make_shared<std::deque<Elem>>();
            }

            template<class Elem>
            void add(std::shared_ptr<std::deque<Elem>> dq, typename NonDeduced<Elem>::type elem) {
                dq->push_back(elem);
            }

            template<class Elem>
            bool isEmpty(std::shared_ptr<std::deque<Elem>> dq) {
                return dq->empty();
            }

            template<class Elem>
            Elem removeFirst(std::shared_ptr<std::deque<Elem>> dq) {
                if (dq->empty()) {
                    bubble<Elem>("removeFirst on empty deque");
                }
                Elem front = dq->front();
                dq->pop_front();
                return front;
            }

        }

    }
}
