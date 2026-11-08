// Exact, per-program light-uniform cache. This never selects or removes lights.
#pragma once
#include <cstddef>
#include <cstring>
#include <vector>

namespace FnafLightCache {
class UploadCache {
    std::vector<float> data;
public:
    bool Matches(const float* values, std::size_t count) const {
        return values && data.size() == count &&
               (count == 0 || std::memcmp(data.data(), values, count * sizeof(float)) == 0);
    }
    void Remember(const float* values, std::size_t count) {
        if (!values || count == 0) { data.clear(); return; }
        data.assign(values, values + count);
    }
    void Invalidate() { data.clear(); }
};
}
