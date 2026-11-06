// Bounded, alpha-aware downsampling for ordinary four-channel game images.
// This never touches render targets, indexed textures, fonts, or menus.
#pragma once
#include <cstddef>
#include <cstdint>

namespace FnafTextureResize
{
inline char LowerAscii(char c) { return c >= 'A' && c <= 'Z' ? char(c + ('a' - 'A')) : c; }
inline bool StartsWith(const char* value, const char* prefix)
{
    if (!value) return false;
    while (*prefix)
    {
        if (!*value || LowerAscii(*value++) != *prefix++) return false;
    }
    return true;
}
inline bool IsVisualAsset(const char* path)
{
    return StartsWith(path, "characters/") || StartsWith(path, "props/") ||
           StartsWith(path, "textures/");
}
inline void Dimensions(int width, int height, int cap, int& outWidth, int& outHeight)
{
    outWidth = width; outHeight = height;
    if (width <= 0 || height <= 0 || cap < 256) return;
    int longest = width > height ? width : height;
    if (longest <= cap) return;
    outWidth = int((int64_t(width) * cap) / longest);
    outHeight = int((int64_t(height) * cap) / longest);
    if (outWidth < 1) outWidth = 1;
    if (outHeight < 1) outHeight = 1;
}

// Input and output are packed BGRA/RGBA with straight alpha. Weighted colors
// prevent transparent black texels from leaving a fringe. Empty boxes stay
// transparent black and never divide by zero.
inline bool Box(const uint8_t* src, int width, int height,
                uint8_t* dst, int outWidth, int outHeight)
{
    if (!src || !dst || width <= 0 || height <= 0 || outWidth <= 0 || outHeight <= 0 ||
        outWidth > width || outHeight > height) return false;
    for (int y = 0; y < outHeight; ++y)
    {
        int y0 = int((int64_t(y) * height) / outHeight);
        int y1 = int((int64_t(y + 1) * height) / outHeight);
        for (int x = 0; x < outWidth; ++x)
        {
            int x0 = int((int64_t(x) * width) / outWidth);
            int x1 = int((int64_t(x + 1) * width) / outWidth);
            uint64_t sum[4] = {0, 0, 0, 0}, count = 0;
            for (int sy = y0; sy < y1; ++sy)
                for (int sx = x0; sx < x1; ++sx)
                {
                    const uint8_t* p = src + (size_t(sy) * width + sx) * 4;
                    const unsigned a = p[3];
                    sum[0] += uint64_t(p[0]) * a;
                    sum[1] += uint64_t(p[1]) * a;
                    sum[2] += uint64_t(p[2]) * a;
                    sum[3] += a;
                    ++count;
                }
            uint8_t* out = dst + (size_t(y) * outWidth + x) * 4;
            for (int c = 0; c < 3; ++c)
                out[c] = sum[3] ? uint8_t((sum[c] + sum[3] / 2) / sum[3]) : 0;
            out[3] = count ? uint8_t((sum[3] + count / 2) / count) : 0;
        }
    }
    return true;
}
}
