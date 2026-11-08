// Offscreen Mesa GLES test of exact per-program light upload caching.
// Tests algorithm, shader switches and context/program recreation; not a phone benchmark.
#include "fnaf_light_cache.h"
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <array>
#include <cassert>
#include <cstdint>
#include <cstring>
#include <iostream>
#include <stdexcept>

using Pixel = std::array<unsigned char,4>;
using Data = std::array<float,8>;
struct Context {
 EGLDisplay display=EGL_NO_DISPLAY; EGLContext ctx=EGL_NO_CONTEXT; EGLSurface surface=EGL_NO_SURFACE;
 Context() {
  display=eglGetDisplay(EGL_DEFAULT_DISPLAY); EGLint major,minor;
  if(display==EGL_NO_DISPLAY || !eglInitialize(display,&major,&minor)) throw std::runtime_error("EGL init");
  eglBindAPI(EGL_OPENGL_ES_API);
  EGLint attrs[]={EGL_SURFACE_TYPE,EGL_PBUFFER_BIT,EGL_RENDERABLE_TYPE,EGL_OPENGL_ES2_BIT,
    EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,EGL_BLUE_SIZE,8,EGL_ALPHA_SIZE,8,EGL_NONE};
  EGLConfig config; EGLint count=0;
  if(!eglChooseConfig(display,attrs,&config,1,&count) || count!=1) throw std::runtime_error("EGL config");
  EGLint pb[]={EGL_WIDTH,4,EGL_HEIGHT,4,EGL_NONE};surface=eglCreatePbufferSurface(display,config,pb);
  EGLint ca[]={EGL_CONTEXT_CLIENT_VERSION,2,EGL_NONE};ctx=eglCreateContext(display,config,EGL_NO_CONTEXT,ca);
  if(surface==EGL_NO_SURFACE || ctx==EGL_NO_CONTEXT || !eglMakeCurrent(display,surface,surface,ctx)) throw std::runtime_error("EGL context");
  glViewport(0,0,4,4);glDisable(GL_BLEND);glDisable(GL_DITHER);glDisable(GL_DEPTH_TEST);
 }
 ~Context(){eglMakeCurrent(display,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);eglDestroySurface(display,surface);eglDestroyContext(display,ctx);eglTerminate(display);}
};
GLuint shader(GLenum type,const char *source) {
 GLuint sh=glCreateShader(type);glShaderSource(sh,1,&source,nullptr);glCompileShader(sh);GLint ok=0;glGetShaderiv(sh,GL_COMPILE_STATUS,&ok);
 if(!ok){char buf[4096];glGetShaderInfoLog(sh,sizeof(buf),nullptr,buf);throw std::runtime_error(buf);}return sh;
}
struct Program {
 GLuint id; GLint loc; FnafLightCache::UploadCache cache; int updates=0,reuses=0;
 Program() {
  auto v=shader(GL_VERTEX_SHADER,"attribute vec2 p; void main(){gl_Position=vec4(p,0.0,1.0);}");
  auto f=shader(GL_FRAGMENT_SHADER,"precision highp float; uniform vec4 lights[2]; void main(){gl_FragColor=vec4(lights[0].r,lights[1].g,lights[0].b+lights[1].b,1.0);}");
  id=glCreateProgram();glAttachShader(id,v);glAttachShader(id,f);glBindAttribLocation(id,0,"p");glLinkProgram(id);GLint ok=0;glGetProgramiv(id,GL_LINK_STATUS,&ok);
  if(!ok) throw std::runtime_error("link");
  loc=glGetUniformLocation(id,"lights");
  if(loc<0) throw std::runtime_error("light uniform");
  glDeleteShader(v); glDeleteShader(f);
 }
 ~Program(){glDeleteProgram(id);}
 Pixel draw(const Data& d,int vectors,bool reuse){
  glUseProgram(id);
  if (!reuse || !cache.Matches(d.data(),vectors*4)) {glUniform4fv(loc,vectors,d.data());if(reuse)cache.Remember(d.data(),vectors*4);else cache.Invalidate();++updates;}
  else ++reuses;
  const GLfloat xy[]={-1,-1,1,-1,-1,1,1,1};glVertexAttribPointer(0,2,GL_FLOAT,GL_FALSE,0,xy);glEnableVertexAttribArray(0);
  glDrawArrays(GL_TRIANGLE_STRIP,0,4);glFinish();Pixel out{};glReadPixels(1,1,1,1,GL_RGBA,GL_UNSIGNED_BYTE,out.data());
  if(glGetError()!=GL_NO_ERROR) throw std::runtime_error("GLES error");
  return out;
 }
};
int main(){
 for(int context=0;context<3;++context){
  Context egl; Program baseA,baseB,optA,optB;
  const Data a={.10f,.20f,.30f,1,.40f,.50f,.10f,1},b={.70f,.20f,.20f,1,.20f,.30f,.40f,1};
  const Data c={.11f,.20f,.30f,1,.40f,.50f,.10f,1};
  struct Step{int p;const Data* d;int count;};
  // Equal bytes on a switched program, changed bytes with reused slot, changed
  // vector counts, disable/re-enable, and complete new program/context.
  const Step steps[]={{0,&a,2},{1,&b,2},{0,&a,2},{0,&c,2},{0,&a,1},{0,&a,2},{1,&b,2},{1,&a,2},{1,&a,2},{0,&a,2}};
  for(auto step:steps){auto &reference=step.p?baseB:baseA;auto &optimized=step.p?optB:optA;
   auto expected=reference.draw(*step.d,step.count,false);auto actual=optimized.draw(*step.d,step.count,true);
   if(expected!=actual)throw std::runtime_error("pixel mismatch");
  }
  if(optA.reuses<1 || optB.reuses<1 || optA.updates<4) throw std::runtime_error("cache not exercised");
  // Context starts disabled; repeated offsets must be toggled when changed.
  if(glIsEnabled(GL_POLYGON_OFFSET_FILL)) throw std::runtime_error("unexpected initial offset");
  glEnable(GL_POLYGON_OFFSET_FILL);glPolygonOffset(1,1);if(!glIsEnabled(GL_POLYGON_OFFSET_FILL))throw std::runtime_error("offset enable");
  glDisable(GL_POLYGON_OFFSET_FILL);glPolygonOffset(0,0);if(glIsEnabled(GL_POLYGON_OFFSET_FILL))throw std::runtime_error("offset disable");
 }
 std::cout << "PASS offscreen GLES per-program exact light bytes, count changes, shader switches and 3 recreated contexts; polygon offset state\n";
}
