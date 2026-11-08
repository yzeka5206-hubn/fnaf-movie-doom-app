#version 450
layout(location = 0) out vec3 vcolor;
void main() {
    vec2 p[3] = vec2[3](vec2(-0.25, -0.75), vec2(0.25, -0.75), vec2(0.0, 0.75));
    float x[3] = float[3](-0.68, 0.0, 0.68);
    vec3 color[3] = vec3[3](vec3(1,0,0), vec3(0,1,0), vec3(0,0,1));
    int object = gl_VertexIndex / 3;
    gl_Position = vec4(p[gl_VertexIndex % 3] + vec2(x[object], 0), 0, 1);
    vcolor = color[object];
}
