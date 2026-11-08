// Conservative screen-frustum rejection for OBJ and all MD3 animation frames.
// Only render work is skipped; a failed or ambiguous test must always draw.
#pragma once
#include "fnaf_iqm_cull.h"
#include <cmath>

namespace FnafStaticCull
{
// Matrices are column-major, identical to the GLES vertex shader. All-frame
// vertex AABBs also enclose the linear interpolation of any two MD3 frames.
// Ignore near/far clipping: depth clamp and special passes can change those.
inline bool Outside(const double* projectionViewModel, const FnafIqmCull::Box& box)
{
    if (!projectionViewModel || !box.used) return false;
    for (int i = 0; i < 16; ++i)
        if (!std::isfinite(projectionViewModel[i])) return false;
    double planes[4][4];
    for (int p = 0; p < 4; ++p)
    {
        const int row = p / 2;
        const double sign = (p & 1) ? -1.0 : 1.0;
        for (int c = 0; c < 4; ++c)
            planes[p][c] = projectionViewModel[c * 4 + 3] +
                           sign * projectionViewModel[c * 4 + row];
    }
    // Reuse the same generous floating-point margin as the IQM path.
    return FnafIqmCull::UpdateMask(15, planes, box, nullptr) != 0;
}
}
