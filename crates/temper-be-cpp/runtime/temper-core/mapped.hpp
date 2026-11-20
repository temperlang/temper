#pragma once
#include <cstdint>
#include <map>
#include <memory>
#include <utility>
#include <vector>
#include "temper_bubble.hpp"
#include "base_types.hpp"
#include "pair.hpp"

namespace temper {
    namespace core {

        namespace Mapped {

            template<class Key, class Value>
            struct Ordered {
                std::vector<Key> order;
                std::map<Key, Value> data;
            };

            template<class Key, class Value>
            int32_t length(const std::shared_ptr<Ordered<Key, Value>>& m) {
                return static_cast<int32_t>(m->data.size());
            }

            template<class Key, class Value>
            Value get(const std::shared_ptr<Ordered<Key, Value>>& m, Key key) {
                typename std::map<Key, Value>::iterator it = m->data.find(key);
                if (it == m->data.end()) {
                    bubble<Value>("key not found");
                }
                return it->second;
            }

            template<class Key, class Value>
            Value getOr(const std::shared_ptr<Ordered<Key, Value>>& m, Key key, Value defaultValue) {
                typename std::map<Key, Value>::iterator it = m->data.find(key);
                if (it != m->data.end()) {
                    return it->second;
                }
                return defaultValue;
            }

            template<class Key, class Value>
            bool has(const std::shared_ptr<Ordered<Key, Value>>& m, Key key) {
                return m->data.find(key) != m->data.end();
            }

            template<class Key, class Value>
            std::shared_ptr<std::vector<Key>> keys(const std::shared_ptr<Ordered<Key, Value>>& m) {
                std::shared_ptr<std::vector<Key>> result = std::make_shared<std::vector<Key>>();
                for (const Key& k : m->order) {
                    result->push_back(k);
                }
                return result;
            }

            template<class Key, class Value>
            std::shared_ptr<std::vector<Value>> values(const std::shared_ptr<Ordered<Key, Value>>& m) {
                std::shared_ptr<std::vector<Value>> result = std::make_shared<std::vector<Value>>();
                for (const Key& k : m->order) {
                    result->push_back(m->data.at(k));
                }
                return result;
            }

            template<class Key, class Value>
            std::shared_ptr<Ordered<Key, Value>> toMap(const std::shared_ptr<Ordered<Key, Value>>& m) {
                std::shared_ptr<Ordered<Key, Value>> result = std::make_shared<Ordered<Key, Value>>();
                result->data = m->data;
                result->order = m->order;
                return result;
            }

            template<class Key, class Value>
            std::shared_ptr<Ordered<Key, Value>> toMapBuilder(const std::shared_ptr<Ordered<Key, Value>>& m) {
                std::shared_ptr<Ordered<Key, Value>> result = std::make_shared<Ordered<Key, Value>>();
                result->data = m->data;
                result->order = m->order;
                return result;
            }

            // The entries in order, copied, for those that call back
            // (`forEach` and `toListWith`), which walk them as the
            // interpreter walks a snapshot: the callback may change a map
            // builder it walks, which would free or shrink the storage a
            // walk of `order` itself reads, or remove a key it looks up.
            template<class Key, class Value>
            std::vector<std::pair<Key, Value>> entries(const std::shared_ptr<Ordered<Key, Value>>& m) {
                std::vector<std::pair<Key, Value>> result;
                result.reserve(m->order.size());
                for (const Key& k : m->order) {
                    result.emplace_back(k, m->data.at(k));
                }
                return result;
            }

            template<class Key, class Value, class F>
            void forEach(const std::shared_ptr<Ordered<Key, Value>>& m, F fn) {
                for (const std::pair<Key, Value>& entry : entries(m)) {
                    fn(entry.first, entry.second);
                }
            }

            template<class Key, class Value>
            std::shared_ptr<std::vector<std::shared_ptr<Pair<Key, Value>>>> toList(const std::shared_ptr<Ordered<Key, Value>>& m) {
                std::shared_ptr<std::vector<std::shared_ptr<Pair<Key, Value>>>> result =
                std::make_shared<std::vector<std::shared_ptr<Pair<Key, Value>>>>();
                for (const Key& k : m->order) {
                    result->push_back(std::make_shared<Pair<Key, Value>>(k, m->data.at(k)));
                }
                return result;
            }

            template<class Key, class Value>
            std::shared_ptr<std::vector<std::shared_ptr<Pair<Key, Value>>>> toListBuilder(const std::shared_ptr<Ordered<Key, Value>>& m) {
                return toList(m);
            }

            template<class Key, class Value, class F>
            auto toListWith(const std::shared_ptr<Ordered<Key, Value>>& m, F fn)
                -> std::shared_ptr<std::vector<decltype(fn(std::declval<const Key&>(), std::declval<const Value&>()))>> {
                using R = decltype(fn(std::declval<const Key&>(), std::declval<const Value&>()));
                std::shared_ptr<std::vector<R>> result = std::make_shared<std::vector<R>>();
                for (const std::pair<Key, Value>& entry : entries(m)) {
                    result->push_back(fn(entry.first, entry.second));
                }
                return result;
            }

            template<class Key, class Value, class F>
            auto toListBuilderWith(const std::shared_ptr<Ordered<Key, Value>>& m, F fn)
                -> std::shared_ptr<std::vector<decltype(fn(std::declval<const Key&>(), std::declval<const Value&>()))>> {
                return toListWith(m, fn);
            }

        }

    }
}
