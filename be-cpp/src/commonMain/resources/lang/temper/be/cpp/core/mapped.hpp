#pragma once
#include <cstdint>
#include <map>
#include <memory>
#include <vector>
#include "base_types.hpp"
#include "pair.hpp"
#include "list.hpp"

namespace temper {
    namespace core {

        namespace Mapped {

            template<class Key, class Value>
            struct Ordered {
                std::vector<Key> order;
                std::map<Key, Value> data;
            };

            template<class Key, class Value>
            int32_t length(std::shared_ptr<Ordered<Key, Value>> m) {
                return static_cast<int32_t>(m->data.size());
            }

            template<class Key, class Value>
            Value get(std::shared_ptr<Ordered<Key, Value>> m, Key key) {
                typename std::map<Key, Value>::iterator it = m->data.find(key);
                if (it == m->data.end()) {
                    bubble<Value>("key not found");
                }
                return it->second;
            }

            template<class Key, class Value>
            Value getOr(std::shared_ptr<Ordered<Key, Value>> m, Key key, Value defaultValue) {
                typename std::map<Key, Value>::iterator it = m->data.find(key);
                if (it != m->data.end()) {
                    return it->second;
                }
                return defaultValue;
            }

            template<class Key, class Value>
            bool has(std::shared_ptr<Ordered<Key, Value>> m, Key key) {
                return m->data.find(key) != m->data.end();
            }

            template<class Key, class Value>
            std::shared_ptr<std::vector<Key>> keys(std::shared_ptr<Ordered<Key, Value>> m) {
                std::shared_ptr<std::vector<Key>> result = std::make_shared<std::vector<Key>>();
                for (const Key& k : m->order) { result->push_back(k); }
                return result;
            }

            template<class Key, class Value>
            std::shared_ptr<std::vector<Value>> values(std::shared_ptr<Ordered<Key, Value>> m) {
                std::shared_ptr<std::vector<Value>> result = std::make_shared<std::vector<Value>>();
                for (const Key& k : m->order) { result->push_back(m->data.at(k)); }
                return result;
            }

            template<class Key, class Value>
            std::shared_ptr<Ordered<Key, Value>> toMap(std::shared_ptr<Ordered<Key, Value>> m) {
                std::shared_ptr<Ordered<Key, Value>> result = std::make_shared<Ordered<Key, Value>>();
                result->data = m->data;
                result->order = m->order;
                return result;
            }

            template<class Key, class Value>
            std::shared_ptr<Ordered<Key, Value>> toMapBuilder(std::shared_ptr<Ordered<Key, Value>> m) {
                std::shared_ptr<Ordered<Key, Value>> result = std::make_shared<Ordered<Key, Value>>();
                result->data = m->data;
                result->order = m->order;
                return result;
            }

            template<class Key, class Value, class F>
            void forEach(std::shared_ptr<Ordered<Key, Value>> m, F fn) {
                for (const Key& k : m->order) { fn(k, m->data.at(k)); }
            }

            template<class Key, class Value>
            std::shared_ptr<std::vector<std::shared_ptr<Pair<Key, Value>>>> toList(std::shared_ptr<Ordered<Key, Value>> m) {
                std::shared_ptr<std::vector<std::shared_ptr<Pair<Key, Value>>>> result =
                std::make_shared<std::vector<std::shared_ptr<Pair<Key, Value>>>>();
                for (const Key& k : m->order) {
                    result->push_back(std::make_shared<Pair<Key, Value>>(k, m->data.at(k)));
                }
                return result;
            }

            template<class Key, class Value>
            std::shared_ptr<std::vector<std::shared_ptr<Pair<Key, Value>>>> toListBuilder(std::shared_ptr<Ordered<Key, Value>> m) {
                return toList(m);
            }

            template<class Key, class Value, class F>
            std::shared_ptr<std::vector<typename std::result_of<F(Key, Value)>::type>> toListWith(std::shared_ptr<Ordered<Key, Value>> m, F fn) {
                std::shared_ptr<std::vector<typename std::result_of<F(Key, Value)>::type>> result =
                std::make_shared<std::vector<typename std::result_of<F(Key, Value)>::type>>();
                for (const Key& k : m->order) { result->push_back(fn(k, m->data.at(k))); }
                return result;
            }

            template<class Key, class Value, class F>
            std::shared_ptr<std::vector<typename std::result_of<F(Key, Value)>::type>> toListBuilderWith(std::shared_ptr<Ordered<Key, Value>> m, F fn) {
                return toListWith(m, fn);
            }

        }

    }
}
