// Standalone conservative-culling and texture-resize regression tests.
#include "fnaf_iqm_cull.h"
#include "fnaf_texture_resize.h"
#include <array>
#include <cassert>
#include <cmath>
#include <cstdint>
#include <iostream>
#include <limits>
#include <random>
#include <vector>
using FnafIqmCull::Box;

static void identity(double* a) { for (int i=0;i<16;++i) a[i]=(i%5==0)?1:0; }
static void identity(float* a) { for (int i=0;i<16;++i) a[i]=(i%5==0)?1:0; }

int main() {
    double pvm[16]; identity(pvm);
    float palette[2*16]; identity(palette); identity(palette+16);
    std::array<Box,2> boxes; Box unweighted;
    assert(boxes[0].Add(-.5f, -.5f, 0)); assert(boxes[0].Add(.5f, .5f, 0));
    assert(!FnafIqmCull::Outside(pvm, boxes.data(), 2, unweighted, palette, 2));
    palette[12]=3;
    assert(FnafIqmCull::Outside(pvm, boxes.data(), 2, unweighted, palette, 2));
    assert(unweighted.Add(0,0,0));
    assert(!FnafIqmCull::Outside(pvm, boxes.data(), 2, unweighted, palette, 2));
    unweighted = {};
    assert(!FnafIqmCull::Outside(pvm, boxes.data(), 2, unweighted, palette, 1));
    palette[12]=1.49f; // touches near the side boundary; conservative margin
    assert(!FnafIqmCull::Outside(pvm, boxes.data(), 2, unweighted, palette, 2));
    palette[12]=3; palette[0]=std::numeric_limits<float>::quiet_NaN();
    assert(!FnafIqmCull::Outside(pvm, boxes.data(), 2, unweighted, palette, 2));
    assert(!FnafIqmCull::Outside(nullptr, boxes.data(), 2, unweighted, palette, 2));

    // Property test: a model rejected by the implementation must have every
    // genuinely skinned vertex outside the SAME clip plane. Randomly exercise
    // mixed weights, independent bones, negative scale and offscreen views.
    std::mt19937 rng(1234567);
    std::uniform_real_distribution<float> rd(-2.f, 2.f), shift(-6.f, 6.f);
    std::uniform_int_distribution<int> bi(0,4), wi(0,255);
    int rejected=0, visible=0;
    for (int trial=0;trial<30000;++trial) {
        constexpr int B=5, N=32;
        std::array<Box,B> b{}; Box un{}; float mat[B*16];
        for (int k=0;k<B;++k) {
            identity(mat+k*16);
            for (int col=0;col<3;++col) for (int row=0;row<3;++row)
                mat[k*16+col*4+row] = rd(rng);
            mat[k*16+12]=rd(rng); mat[k*16+13]=rd(rng); mat[k*16+14]=rd(rng);
            // The IQM shader overwrites the skinned homogeneous w to 1.
            mat[k*16+3]=rd(rng); mat[k*16+7]=rd(rng); mat[k*16+11]=rd(rng); mat[k*16+15]=rd(rng);
        }
        double m[16]; identity(m); m[12]=shift(rng); m[13]=shift(rng);
        // Also vary the projection, including a nonconstant homogeneous w.
        m[0]=.1+std::abs(rd(rng)); m[5]=.1+std::abs(rd(rng));
        m[3]=rd(rng)*.05; m[7]=rd(rng)*.05; m[11]=rd(rng)*.05;
        unsigned all=15;
        for (int v=0;v<N;++v) {
            float xyz[3]={rd(rng),rd(rng),rd(rng)};
            // Production keeps the whole bind pose as well as animated boxes.
            assert(un.Add(xyz[0],xyz[1],xyz[2]));
            double raw[4]={xyz[0],xyz[1],xyz[2],1}, rawclip[4]={};
            for(int row=0;row<4;++row) for(int c=0;c<4;++c) rawclip[row]+=m[c*4+row]*raw[c];
            for(int plane=0;plane<4;++plane)
                if(rawclip[3]+((plane&1)?-1:1)*rawclip[plane/2]>=-1e-8) all &= ~(1u<<plane);
            int inds[4], w[4], sum=0;
            for(int j=0;j<4;++j) { inds[j]=bi(rng); w[j]=wi(rng); sum+=w[j]; }
            if (v==0 && (trial%7)==0) { for(int& x:w)x=0; sum=0; }
            double sk[4]={0,0,0,1};
            if (!sum) { for(int c=0;c<3;++c) sk[c]=xyz[c]; }
            else for(int j=0;j<4;++j) if(w[j]) {
                int k=inds[j]; assert(b[k].Add(xyz[0],xyz[1],xyz[2]));
                for(int row=0;row<3;++row) {
                    double val=mat[k*16+12+row];
                    for(int c=0;c<3;++c) val += mat[k*16+c*4+row]*xyz[c];
                    sk[row] += val*double(w[j])/sum;
                }
            }
            double clip[4]={};
            for(int row=0;row<4;++row) for(int c=0;c<4;++c) clip[row]+=m[c*4+row]*sk[c];
            for(int plane=0;plane<4;++plane) {
                double q=clip[3]+((plane&1)?-1:1)*clip[plane/2];
                if(q>=-1e-8) all &= ~(1u<<plane);
            }
        }
        bool culled=FnafIqmCull::Outside(m,b.data(),B,un,mat,B);
        if(culled) { ++rejected; assert(all != 0); } else ++visible;
    }
    assert(rejected > 1000 && visible > 1000);

    using namespace FnafTextureResize;
    assert(IsVisualAsset("Characters/Animatronics/Freddy/body.jpg"));
    assert(IsVisualAsset("props/a.png") && IsVisualAsset("TEXTURES/wall.jpg"));
    assert(!IsVisualAsset("hires/M_DOOM.png") && !IsVisualAsset("shaders_gles/main.vp"));
    assert(!IsVisualAsset(nullptr));
    int w,h; Dimensions(2048,1024,1024,w,h); assert(w==1024 && h==512);
    Dimensions(1512,1512,1024,w,h); assert(w==1024 && h==1024);
    Dimensions(200,100,1024,w,h); assert(w==200 && h==100);
    Dimensions(2000,1000,0,w,h); assert(w==2000 && h==1000);
    // Opaque red and fully transparent green: red stays red, alpha is averaged.
    uint8_t image[16]={255,0,0,255, 0,255,0,0, 255,0,0,255, 0,255,0,0}, out[4];
    assert(FnafTextureResize::Box(image,2,2,out,1,1));
    assert(out[0]==255 && out[1]==0 && out[2]==0 && out[3]==128);
    uint8_t empty[16]={}; assert(FnafTextureResize::Box(empty,2,2,out,1,1));
    assert(out[0]==0 && out[1]==0 && out[2]==0 && out[3]==0);
    assert(!FnafTextureResize::Box(empty,2,2,out,3,1));
    std::cout << "PASS: conservative IQM culling: " << rejected << " offscreen / " << visible
              << " retained across 30000 randomized weighted poses; image alpha/dimensions/path tests\n";
}
