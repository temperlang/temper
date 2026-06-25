#pragma once
#include <algorithm>
#include <memory>
#include "temper_bubble.hpp"
#include "base_types.hpp"
#include "mapped.hpp"

namespace temper {
    namespace core {

        namespace MapBuilder {

            template<class Key, class Value>
            void set(const std::shared_ptr<Mapped::Ordered<Key, Value>>& m, Key key, Value value) {
                bool isNew = (m->data.find(key) == m->data.end());
                m->data[key] = value;
                if (isNew) {
                    m->order.push_back(key);
                }
            }

            template<class Key, class Value>
            Value remove(const std::shared_ptr<Mapped::Ordered<Key, Value>>& m, Key key) {
                typename std::map<Key, Value>::iterator it = m->data.find(key);
                if (it == m->data.end()) {
                    bubble<Value>("key not found");
                }
                Value result = it->second;
                m->data.erase(it);
                // Remove from `order` using the map's own ordering-equivalence
                // (!(a<b) && !(b<a)) rather than operator==, so the two structures stay in sync
                // even for key types where == and the map's comparator would disagree.
                m->order.erase(
                    std::remove_if(
                        m->order.begin(),
                        m->order.end(),
                        [&key](const Key& k) {
                            return !(k < key) && !(key < k);
                        }
                    ),
                    m->order.end()
                );
                return result;
            }

            template<class Key, class Value>
            void clear(const std::shared_ptr<Mapped::Ordered<Key, Value>>& m) {
                m->data.clear();
                m->order.clear();
            }

        }

    }
}
