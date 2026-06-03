#pragma once
#include <memory>
#include <vector>
#include "mapped.hpp"
#include "pair.hpp"

namespace temper {
    namespace core {
        namespace Map {

            template<class Key, class Value>
            std::shared_ptr<Mapped::Ordered<Key, Value>> make() {
                return std::make_shared<Mapped::Ordered<Key, Value>>();
            }

            template<class Key, class Value>
            std::shared_ptr<Mapped::Ordered<Key, Value>> make(std::shared_ptr<std::vector<std::shared_ptr<Pair<Key, Value>>>> pairs) {
                std::shared_ptr<Mapped::Ordered<Key, Value>> result = std::make_shared<Mapped::Ordered<Key, Value>>();
                for (const std::shared_ptr<Pair<Key, Value>>& p : *pairs) {
                    Key k = p->get_key();
                    Value v = p->get_value();
                    bool isNew = (result->data.find(k) == result->data.end());
                    result->data[k] = v;
                    if (isNew) {
                        result->order.push_back(k);
                    }
                }
                return result;
            }

        }
    }
}
