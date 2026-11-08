// Thread-local, exact-angle spotlight math cache. This does not choose lights.
#pragma once
#include <cstdint>
#include <cstring>
namespace FnafSpotCache {
struct Result { float inner, outer, x, y, z; };
class Cache {
    struct Entry { std::uint64_t key[4]{}; Result value{}; bool valid = false; };
    Entry entries[64]{};
public:
    // The key is the actual bit pattern of the four angles, never an actor or
    // frame index. NaN and +/-0 are distinct; direct-map collisions recompute.
    template<class Compute> Result Get(double inner, double outer, double yaw,
                                        double pitch, bool enabled, Compute compute,
                                        bool& reused) {
        const double values[4] = {inner, outer, yaw, pitch};
        std::uint64_t bits[4]; std::memcpy(bits, values, sizeof(bits));
        std::uint64_t hash = 1469598103934665603ULL;
        for (auto b : bits) { hash ^= b; hash *= 1099511628211ULL; }
        Entry& e = entries[(hash ^ (hash >> 32)) & 63];
        if (enabled && e.valid && std::memcmp(e.key, bits, sizeof(bits)) == 0) {
            reused = true; return e.value;
        }
        reused = false;
        Result result = compute();
        if (enabled) { std::memcpy(e.key, bits, sizeof(bits)); e.value = result; e.valid = true; }
        return result;
    }
};
}
