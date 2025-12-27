
#include <functional>
#include <sstream>
#include <string>
#include <iostream>
#include <memory>
#include <vector>
#include <map>

namespace temper {
    namespace core {
        namespace helpers {
            namespace {
                void cat(std::stringstream &dst) {
                    (void) dst;
                }

                template<class First, class... Rest>
                void cat(std::stringstream &dst, First fst, Rest... rest) {
                    dst << fst.get();
                    cat(dst, rest...);
                }

                template<class Base>
                struct Object {
                    using Type = std::shared_ptr<Base>;
                };

                template<>
                struct Object<void> {
                    using Type = void;
                };
            }
        }

        namespace {
            using Void = void;
            using Boolean = bool;
            using Int = ptrdiff_t;
            using String = std::string;

            template<class Ret, class... Params>
            using Function = std::function<Ret(Params...)>;

            // lists
            template<class Elem>
            using Listed = std::vector<Elem>;

            template<class Elem>
            using List = std::vector<Elem>;

            template<class Elem>
            using ListBuilder = std::vector<Elem>;

            // maps
            template<class Key, class Value>
            using Mapped = std::map<Key, Value>;

            template<class Key, class Value>
            using Map = std::map<Key, Value>;

            template<class Key, class Value>
            using MapBuilder = std::map<Key, Value>;

            // wrapper
            template<class Base>
            using Object = typename helpers::Object<Base>::Type;

            template<class Base>
            using Nullable = Base;

            template<class Base>
            using Bubble = Base;

            template<class Base, class... Fields>
            Object<Base> object(Fields... fields) {
                return std::make_shared<Base>(fields...);
            }

            template<class... Args>
            Object<String> cat(Args... args) {
                std::stringstream ss;
                helpers::cat(ss, args...);
                return object<String>(ss.str());
            }

            struct Console {
                std::function<Void(String)> logger;
            };

            void log(Object<Console> console, Object<String> msg) {
                std::stringstream ss;
                ss << msg.get();
                ss << '\n';
                console->logger(ss.str());
                return;
            }

            Object<Console> get_console() {
                return object<Console>([](String s) {
                    std::cout << s;
                });
            }

            template<class Key, class Value>
            Bubble<void> set(MapBuilder<Key, Value> map, Key key, Value value) {
                map[key] = value;
            }
        }
    }
}
