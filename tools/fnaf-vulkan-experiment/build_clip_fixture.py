#!/usr/bin/env python3
"""Extract the actual shipped renderer's clip branch into a Vulkan GPU fixture."""
from pathlib import Path
from zipfile import ZipFile
import re, sys
apk_resource, output = map(Path, sys.argv[1:])
with ZipFile(apk_resource) as z:
    vertex = z.read('shaders/glsl/main.vp').decode()
    fragment = z.read('shaders/glsl/main.fp').decode()
matches = re.findall(r'#ifdef NO_CLIPDISTANCE_SUPPORT\s*(ClipDistanceA = vec4\(ClipDistance0,.*?#endif)', vertex, re.S)
assert len(matches) == 1, 'shipped vertex shader clip-distance branch changed'
clip_branch = matches[0].rsplit('#endif', 1)[0]
assert 'gl_ClipDistance[4] = ClipDistance4;' in clip_branch
match = re.search(r'if \(ClipDistanceA\.x < 0\.0.*?\) discard;', fragment)
assert match, 'shipped fragment shader clip-distance branch changed'
clip_discard = match.group(0)
vert = '''#version 450
layout(location = 0) out vec3 vcolor;
#ifdef NO_CLIPDISTANCE_SUPPORT
layout(location = 7) out vec4 ClipDistanceA;
layout(location = 8) out vec4 ClipDistanceB;
#else
out gl_PerVertex { vec4 gl_Position; float gl_ClipDistance[5]; };
#endif
void main() {
    vec2 p[3] = vec2[3](vec2(-0.25, -0.75), vec2(0.25, -0.75), vec2(0.0, 0.75));
    float x[3] = float[3](-0.68, 0.0, 0.68);
    vec3 color[3] = vec3[3](vec3(1,0,0), vec3(0,1,0), vec3(0,0,1));
    int object = gl_VertexIndex / 3;
    vec2 local = p[gl_VertexIndex % 3];
    gl_Position = vec4(local + vec2(x[object], 0), 0, 1);
    vcolor = color[object];
    float ClipDistance0 = local.x + 0.19;
    float ClipDistance1 = 0.19 - local.x;
    float ClipDistance2 = local.y + 0.62;
    float ClipDistance3 = 0.66 - local.y;
    float ClipDistance4 = 0.34 + local.x + local.y;
#ifdef NO_CLIPDISTANCE_SUPPORT
''' + clip_branch + '''
#endif
}
'''
frag = '''#version 450
layout(location = 0) in vec3 vcolor;
layout(location = 0) out vec4 outColor;
#ifdef NO_CLIPDISTANCE_SUPPORT
layout(location = 7) in vec4 ClipDistanceA;
layout(location = 8) in vec4 ClipDistanceB;
#endif
void main() {
#ifdef NO_CLIPDISTANCE_SUPPORT
''' + clip_discard + '''
#endif
outColor = vec4(vcolor, 1);
}
'''
(output / 'clip.vert').write_text(vert)
(output / 'clip.frag').write_text(frag)
print('Extracted clip branch from shipped Vulkan shaders')
