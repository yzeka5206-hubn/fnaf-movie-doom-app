// Conservative, render-only IQM frustum rejection for the FNAF mobile build.
// A weighted vertex is a convex combination of its influencing bones' affine
// transforms. Therefore each bone's transformed vertex AABB encloses its
// contribution, including arbitrary runtime bone overrides and interpolation.
// If *all* boxes are outside one clip plane, no triangle can reach the screen.
// Invalid/unknown data always fails open (draw the model).
#pragma once

#include <algorithm>
#include <cmath>
#include <limits>

namespace FnafIqmCull
{
struct Box
{
    float lo[3] = {0, 0, 0};
    float hi[3] = {0, 0, 0};
    bool used = false;

    bool Add(float x, float y, float z)
    {
        const float p[3] = {x, y, z};
        for (float v : p) if (!std::isfinite(v)) return false;
        if (!used)
        {
            for (int k = 0; k < 3; ++k) lo[k] = hi[k] = p[k];
            used = true;
        }
        else
        {
            for (int k = 0; k < 3; ++k)
            {
                lo[k] = std::min(lo[k], p[k]);
                hi[k] = std::max(hi[k], p[k]);
            }
        }
        return true;
    }
};

// Transform each clip-plane equation back into the box's local space, then
// evaluate its maximum over the AABB. Matrices are column-major, like GLSL.
// Weighted IQM vertices force position.w=1 after skinning in the shader, so
// the bone matrix's fourth row must NOT be used in this calculation.
inline unsigned UpdateMask(unsigned mask, const double plane[4][4], const Box& box,
                           const float* bone)
{
    if (!box.used) return mask;
    for (int p = 0; p < 4; ++p)
    {
        const unsigned bit = 1u << p;
        if (!(mask & bit)) continue;
        double q[4];
        if (bone)
        {
            for (int c = 0; c < 3; ++c)
                q[c] = plane[p][0] * bone[c * 4 + 0] +
                       plane[p][1] * bone[c * 4 + 1] +
                       plane[p][2] * bone[c * 4 + 2];
            q[3] = plane[p][3] + plane[p][0] * bone[12] +
                   plane[p][1] * bone[13] + plane[p][2] * bone[14];
        }
        else for (int c = 0; c < 4; ++c) q[c] = plane[p][c];

        double high = q[3], magnitude = 1.0 + std::abs(q[3]);
        for (int c = 0; c < 3; ++c)
        {
            if (!std::isfinite(q[c]) || !std::isfinite(box.lo[c]) ||
                !std::isfinite(box.hi[c])) return 0;
            high += q[c] * (q[c] >= 0 ? box.hi[c] : box.lo[c]);
            magnitude += std::abs(q[c]) * std::max(std::abs(box.lo[c]), std::abs(box.hi[c]));
        }
        if (!std::isfinite(high) || !std::isfinite(magnitude)) return 0;
        // A generous numerical margin avoids clipping at the edge of the view.
        if (high >= -0.005 * magnitude) mask &= ~bit;
    }
    return mask;
}

// projectionViewModel and bone matrices use column-major float GLSL layout.
// Ignore near/far planes: depth-clamp and special passes can alter those.
inline bool Outside(const double* projectionViewModel, const Box* bones, int boneCount,
                    const Box& unweighted, const float* palette, int paletteCount)
{
    if (!projectionViewModel || !bones || !palette || boneCount <= 0 ||
        boneCount > 256 || paletteCount < boneCount) return false;
    for (int i = 0; i < 16; ++i)
        if (!std::isfinite(projectionViewModel[i])) return false;
    double planes[4][4];
    for (int p = 0; p < 4; ++p)
    {
        const int row = p / 2;              // x or y
        const double sign = (p & 1) ? -1.0 : 1.0;
        for (int c = 0; c < 4; ++c)
            planes[p][c] = projectionViewModel[c * 4 + 3] +
                           sign * projectionViewModel[c * 4 + row];
    }
    unsigned outside = 15;
    bool any = unweighted.used;
    outside = UpdateMask(outside, planes, unweighted, nullptr);
    if (!outside) return false;
    for (int i = 0; i < boneCount; ++i)
    {
        if (!bones[i].used) continue;
        any = true;
        outside = UpdateMask(outside, planes, bones[i], palette + i * 16);
        if (!outside) return false;
    }
    return any && outside != 0;
}
}
