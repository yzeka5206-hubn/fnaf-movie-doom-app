#include "fnaf_static_cull.h"
extern "C" int fnaf_static_probe(const double* matrix, const float* vertices, int count) {
    if (!matrix || !vertices || count <= 0) return 0;
    FnafIqmCull::Box b;
    for (int i=0;i<count;++i)
        if (!b.Add(vertices[i*3],vertices[i*3+1],vertices[i*3+2])) return 0;
    return FnafStaticCull::Outside(matrix,b) ? 1 : 0;
}
