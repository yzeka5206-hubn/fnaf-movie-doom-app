// Test-only C interface. Production builds the same boxes once per IQM model.
#include "fnaf_iqm_cull.h"
#include <vector>
extern "C" int fnaf_cull_probe(const double* pvm, const float* xyz,
                               const unsigned char* selectors, const unsigned char* weights,
                               int vertices, int joints, const float* palette) {
    if (!xyz || !selectors || !weights || vertices <= 0 || joints <= 0 || joints > 256) return 0;
    std::vector<FnafIqmCull::Box> boxes(joints);
    FnafIqmCull::Box unweighted;
    for (int v=0; v<vertices; ++v) {
        bool weighted=false;
        for (int i=0; i<4; ++i) if (weights[v*4+i]) {
            int joint=selectors[v*4+i];
            if (joint>=joints || !boxes[joint].Add(xyz[v*3],xyz[v*3+1],xyz[v*3+2])) return 0;
            weighted=true;
        }
        if (!weighted && !unweighted.Add(xyz[v*3],xyz[v*3+1],xyz[v*3+2])) return 0;
    }
    return FnafIqmCull::Outside(pvm,boxes.data(),joints,unweighted,palette,joints) ? 1 : 0;
}
