#pragma once
#include <cstddef>
#include <cstdint>
#include <memory>
#include <string>
#include <vector>
#include "temper_bubble.hpp"
#include "base_types.hpp"

namespace temper {
    namespace core {
        namespace BitVector {

            // Grows a vector to `size` bits, opaque to g++'s optimizer: inlined
            // where it knows the vector's size, g++ 12 to 14 at -O3 report a false
            // -Warray-bounds in libstdc++'s vector<bool> insert (as list.hpp's
            // reserve_items notes), failing -Wall -Werror release builds. Growing
            // is rare, so the call costs little.
#if defined(__GNUC__) && !defined(__clang__)
            __attribute__((noipa))
#endif
            inline void grow(std::vector<bool>& bv, std::size_t size) {
                bv.resize(size, false);
            }

            // A negative size is none.
            inline std::shared_ptr<std::vector<bool>> make(int32_t size) {
                return std::make_shared<std::vector<bool>>(static_cast<std::size_t>(size < 0 ? 0 : size), false);
            }

            inline bool get(std::shared_ptr<std::vector<bool>> bv, int32_t index) {
                // A bit never set is false, as std's Temper body has it.
                int32_t sz = static_cast<int32_t>(bv->size());
                return 0 <= index && index < sz && (*bv)[index];
            }

            inline void set(std::shared_ptr<std::vector<bool>> bv, int32_t index, bool value) {
                // As std's Temper body: past the end, a false bit is
                // already false, and a true one grows the vector.
                int32_t sz = static_cast<int32_t>(bv->size());
                if (index < 0) {
                    // Panics, even to set false, as a list's set out of
                    // bounds does, for length 0: a bit vector has none.
                    index_out_of_bounds(index, 0);
                }
                if (index >= sz) {
                    if (!value) {
                        return;
                    }
                    grow(*bv, static_cast<std::size_t>(index) + 1);
                }
                (*bv)[index] = value;
            }

        }
    }
}
