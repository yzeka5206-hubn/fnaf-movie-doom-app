#include "fnaf_static_cull.h"
#include "fnaf_texture_resize.h"
#include <cassert>
#include <array>
#include <cmath>
#include <cstdint>
#include <iostream>
#include <limits>
#include <random>
#include <vector>

static unsigned pointMask(const double* m, const double* p) {
    double clip[4] = {};
    for (int row=0; row<4; ++row)
        for (int c=0; c<4; ++c) clip[row] += m[c*4+row]*p[c];
    unsigned mask = 0;
    for (int plane=0; plane<4; ++plane)
        if (clip[3] + (plane&1 ? -1 : 1)*clip[plane/2] < -1e-8) mask |= 1u<<plane;
    return mask;
}
int main() {
    using FnafIqmCull::Box;
    using FnafStaticCull::Outside;
    double identity[16]={1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1};
    Box empty; assert(!Outside(identity,empty)); assert(!Outside(nullptr,empty));
    Box tangent; assert(tangent.Add(1,0,0)); assert(!Outside(identity,tangent));
    Box left; assert(left.Add(-5,-.1f,0)); assert(left.Add(-4,.1f,1)); assert(Outside(identity,left));
    Box visible; assert(visible.Add(0,0,0)); assert(visible.Add(2,1,1)); assert(!Outside(identity,visible));
    double invalid[16]; for(int i=0;i<16;++i) invalid[i]=identity[i];
    invalid[0]=std::numeric_limits<double>::quiet_NaN(); assert(!Outside(invalid,left));
    Box nonfinite; nonfinite.used=true; nonfinite.lo[0]=std::numeric_limits<float>::infinity();
    assert(!Outside(identity,nonfinite));
    assert(!empty.Add(std::numeric_limits<float>::quiet_NaN(), 0, 0));
    std::mt19937 rng(9817231);
    std::uniform_real_distribution<float> v(-4,4), shift(-12,12), angle(-3.14f,3.14f), unit(0,1);
    int rejected=0, kept=0;
    for (int trial=0;trial<30000;++trial) {
        Box bounds;
        std::vector<std::array<float,3>> vertices;
        for (int i=0;i<24;++i) {
            std::array<float,3> q={v(rng),v(rng),v(rng)};
            vertices.push_back(q); assert(bounds.Add(q[0],q[1],q[2]));
        }
        double a=angle(rng), s=.1+unit(rng)*1.5, t=.1+unit(rng)*1.5;
        double m[16] = {s*std::cos(a),s*std::sin(a),0,.01*v(rng),
                       -t*std::sin(a),t*std::cos(a),0,.01*v(rng),
                        .1*v(rng),.1*v(rng),1,.02*v(rng),shift(rng),shift(rng),0,1};
        if (Outside(m,bounds)) {
            ++rejected;
            unsigned all=15;
            for (const auto& q: vertices) {
                double p[4]={q[0],q[1],q[2],1}; all &= pointMask(m,p);
            }
            // MD3 frame interpolation is a convex combination of two vertices.
            for (int j=0;j<24;++j) {
                auto q=vertices[j],r=vertices[(j*7+3)%vertices.size()]; double mix=unit(rng);
                double p[4]={q[0]*(1-mix)+r[0]*mix,q[1]*(1-mix)+r[1]*mix,q[2]*(1-mix)+r[2]*mix,1};
                all &= pointMask(m,p);
            }
            assert(all!=0); // cannot remove a frame whose geometry enters view
        } else ++kept;
    }
    assert(rejected>1000 && kept>1000);
    int w,h;
    FnafTextureResize::Dimensions(1512,1512,768,w,h); assert(w==768 && h==768);
    FnafTextureResize::Dimensions(2048,1024,768,w,h); assert(w==768 && h==384);
    FnafTextureResize::Dimensions(800,400,1024,w,h); assert(w==800 && h==400);
    FnafTextureResize::Dimensions(0,100,768,w,h); assert(w==0 && h==100);
    std::cout << "PASS 30000 conservative static/animated-box views: " << rejected << " rejected / " << kept
              << " retained; tangent, nonfinite, interpolated poses, 768px resize\n";
}
