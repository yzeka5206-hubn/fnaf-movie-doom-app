#!/usr/bin/env python3
"""Offscreen GLES 3.1 tests of the exact GLSL-100 IQM skinning shader.

Uses Mesa/EGL (no emulator) and the user's original game models. This checks
GPU math and shader linking, not an Android phone or the full engine.
"""
import ctypes as C
import os
from pathlib import Path
import numpy as np

os.environ.setdefault('EGL_PLATFORM', 'surfaceless')
os.environ.setdefault('LIBGL_ALWAYS_SOFTWARE', '1')
ROOT = Path(__file__).resolve().parents[2]
U8=C.c_ubyte; I=C.c_int; U=C.c_uint; F=C.c_float; B=C.c_ubyte; V=C.c_void_p

def fn(lib, name, restype, args):
    f=getattr(lib,name); f.restype=restype; f.argtypes=args; return f

def ptr(array): return C.c_void_p(array.ctypes.data)

class GLES:
    def __init__(self):
        E=C.CDLL('libEGL.so.1'); self.G=C.CDLL('libGLESv2.so.2')
        self.eglGetDisplay=fn(E,'eglGetDisplay',V,[V]); self.eglInitialize=fn(E,'eglInitialize',I,[V,C.POINTER(I),C.POINTER(I)])
        self.eglBindAPI=fn(E,'eglBindAPI',I,[U]); self.eglChooseConfig=fn(E,'eglChooseConfig',I,[V,C.POINTER(I),C.POINTER(V),I,C.POINTER(I)])
        self.eglCreatePbufferSurface=fn(E,'eglCreatePbufferSurface',V,[V,V,C.POINTER(I)]); self.eglCreateContext=fn(E,'eglCreateContext',V,[V,V,V,C.POINTER(I)])
        self.eglMakeCurrent=fn(E,'eglMakeCurrent',I,[V,V,V,V]); self.eglGetError=fn(E,'eglGetError',U,[])
        display=self.eglGetDisplay(None); major=I();minor=I(); self.ok(self.eglInitialize(display,C.byref(major),C.byref(minor)),'eglInitialize')
        self.ok(self.eglBindAPI(0x30A0),'eglBindAPI')
        config=V(); count=I(); attrs=(I*13)(0x3033,1, 0x3040,0x40, 0x3024,8, 0x3023,8, 0x3022,8, 0x3021,8, 0x3038)
        self.ok(self.eglChooseConfig(display,attrs,C.byref(config),1,C.byref(count)) and count.value,'eglChooseConfig')
        surface=self.eglCreatePbufferSurface(display,config,(I*5)(0x3057,64,0x3056,64,0x3038)); self.ok(surface,'eglCreatePbufferSurface')
        context=self.eglCreateContext(display,config,None,(I*3)(0x3098,3,0x3038)); self.ok(context,'eglCreateContext')
        self.ok(self.eglMakeCurrent(display,surface,surface,context),'eglMakeCurrent')
        self.bind()
        print('EGL',major.value,minor.value,'GL',self.glGetString(0x1F02).decode(),'renderer',self.glGetString(0x1F01).decode())

    def ok(self, condition, what):
        if not condition: raise RuntimeError(f'{what} failed EGL=0x{self.eglGetError():04x}')

    def bind(self):
        G=self.G
        spec={
          'glGetString':(C.c_char_p,[U]), 'glGetError':(U,[]), 'glGetIntegerv':(None,[U,C.POINTER(I)]),
          'glCreateShader':(U,[U]),'glShaderSource':(None,[U,I,C.POINTER(C.c_char_p),C.POINTER(I)]), 'glCompileShader':(None,[U]),
          'glGetShaderiv':(None,[U,U,C.POINTER(I)]),'glGetShaderInfoLog':(None,[U,I,C.POINTER(I),C.c_char_p]),
          'glCreateProgram':(U,[]),'glAttachShader':(None,[U,U]),'glBindAttribLocation':(None,[U,U,C.c_char_p]),'glLinkProgram':(None,[U]),
          'glGetProgramiv':(None,[U,U,C.POINTER(I)]),'glGetProgramInfoLog':(None,[U,I,C.POINTER(I),C.c_char_p]),'glUseProgram':(None,[U]),
          'glGetUniformLocation':(I,[U,C.c_char_p]),'glUniform1i':(None,[I,I]),'glUniformMatrix4fv':(None,[I,I,B,V]),
          'glGenTextures':(None,[I,C.POINTER(U)]),'glBindTexture':(None,[U,U]),'glActiveTexture':(None,[U]),
          'glTexParameteri':(None,[U,U,I]),'glTexImage2D':(None,[U,I,I,I,I,I,U,U,V]),'glTexSubImage2D':(None,[U,I,I,I,I,I,U,U,V]),
          'glGenFramebuffers':(None,[I,C.POINTER(U)]),'glBindFramebuffer':(None,[U,U]),'glFramebufferTexture2D':(None,[U,U,U,U,I]),
          'glCheckFramebufferStatus':(U,[U]),'glGenBuffers':(None,[I,C.POINTER(U)]),'glBindBuffer':(None,[U,U]),
          'glBufferData':(None,[U,C.c_ssize_t,V,U]),'glDeleteBuffers':(None,[I,C.POINTER(U)]),'glEnableVertexAttribArray':(None,[U]),'glDisableVertexAttribArray':(None,[U]),
          'glVertexAttribPointer':(None,[U,I,U,B,I,V]),'glVertexAttrib3f':(None,[U,F,F,F]),
          'glViewport':(None,[I,I,I,I]),'glDrawArrays':(None,[U,I,I]),'glReadPixels':(None,[I,I,I,I,U,U,V]),
          'glClearColor':(None,[F,F,F,F]),'glClear':(None,[U]),'glDisable':(None,[U]),'glFinish':(None,[]),
        }
        for name,(restype,args) in spec.items():setattr(self,name,fn(G,name,restype,args))

    def check(self, what):
        error=self.glGetError()
        if error: raise RuntimeError(f'{what}: OpenGL error 0x{error:04x}')

    def compile(self, kind, text):
        shader=self.glCreateShader(kind); encoded=text.encode(); src=C.c_char_p(encoded); self.glShaderSource(shader,1,C.byref(src),None);self.glCompileShader(shader)
        status=I();self.glGetShaderiv(shader,0x8B81,C.byref(status))
        if not status.value:
            buf=C.create_string_buffer(32768);self.glGetShaderInfoLog(shader,len(buf),None,buf)
            raise RuntimeError(f'GLSL compile failed: {buf.value.decode()}')
        return shader

    def program(self, vertex, frag):
        p=self.glCreateProgram();self.glAttachShader(p,self.compile(0x8B31,vertex));self.glAttachShader(p,self.compile(0x8B30,frag))
        for k,v in {0:b'aPosition',1:b'aTexCoord',2:b'aColor',3:b'aVertex2',4:b'aNormal',5:b'aNormal2',7:b'aBoneWeight',8:b'aBoneSelector',9:b'aTestPixelCoord'}.items():
            self.glBindAttribLocation(p,k,v)
        self.glLinkProgram(p);status=I();self.glGetProgramiv(p,0x8B82,C.byref(status))
        if not status.value:
            buf=C.create_string_buffer(32768);self.glGetProgramInfoLog(p,len(buf),None,buf);raise RuntimeError(f'GLSL link failed: {buf.value.decode()}')
        return p

    def uniform_i(self,p,name,value):
        idx=self.glGetUniformLocation(p,name.encode())
        if idx >= 0:self.glUniform1i(idx,value)
        return idx

    def matrix_identity(self,p,name):
        idx=self.glGetUniformLocation(p,name.encode())
        if idx >= 0:
            mat=np.eye(4,dtype=np.float32);self.glUniformMatrix4fv(idx,1,0,ptr(mat))

    def texture(self,unit,w,h,data=None):
        t=U();self.glGenTextures(1,C.byref(t));self.glActiveTexture(0x84C0+unit);self.glBindTexture(0x0DE1,t.value)
        for opt,val in [(0x2801,0x2600),(0x2800,0x2600),(0x2802,0x812F),(0x2803,0x812F)]:self.glTexParameteri(0x0DE1,opt,val)
        self.glTexImage2D(0x0DE1,0,0x8814,w,h,0,0x1908,0x1406,None if data is None else ptr(data));self.check('RGBA32F texture')
        return t.value

    def buffer(self,loc,data,size,dtype,normalized=False):
        data=np.ascontiguousarray(data);b=U();self.glGenBuffers(1,C.byref(b));self.glBindBuffer(0x8892,b.value)
        self.glBufferData(0x8892,data.nbytes,ptr(data),0x88E4);self.glEnableVertexAttribArray(loc)
        self.glVertexAttribPointer(loc,size,dtype,int(normalized),0,None);return b.value


def shader_source(output_normals=False):
    i_data=(ROOT/'tools/fnaf-gles-skinning/shader_interface.glsl').read_text()
    shader=(ROOT/'tools/fnaf-gles-skinning/main.vp').read_text()
    shader=shader.replace('BonesResult ApplyBones();','attribute vec2 aTestPixelCoord;\nBonesResult ApplyBones();')
    assert shader.count('gl_Position = ProjectionMatrix * eyeCoordPos;')==1
    if output_normals:
        assert shader.count('pixelpos.xyz = worldcoord.xyz;')==1
        shader=shader.replace('pixelpos.xyz = worldcoord.xyz;', 'pixelpos.xyz = bones.Normal;')
    shader=shader.replace('gl_Position = ProjectionMatrix * eyeCoordPos;','gl_Position = vec4(aTestPixelCoord, 0.0, 1.0);\n\tgl_PointSize = 1.0;')
    return '#version 100\n#define FNAF_BONE_TEXTURE 1\n#define MAXIMUM_LIGHT_VECTORS 128\n#define NO_CLIPDISTANCE_SUPPORT\nprecision highp int;\nprecision highp float;\n'+i_data+'\n'+shader


def main():
    g=GLES(); vert=shader_source()
    p=g.program(vert,'#version 100\nprecision highp float;\nvarying vec4 pixelpos;\nvoid main(){gl_FragColor=vec4(pixelpos.xyz,1.0);}')
    g.glUseProgram(p)
    for name in ['ModelMatrix','NormalModelMatrix','ViewMatrix','NormalViewMatrix','ProjectionMatrix','TextureMatrix']:g.matrix_identity(p,name)
    print('linked, a palette uniform location=',g.uniform_i(p,'uFnafBoneTexture',17), 'bone base=',g.uniform_i(p,'uBoneIndexBase',0))
    palette=np.zeros((256,4,4),dtype=np.float32)
    palette[0]=np.eye(4,dtype=np.float32); palette[0,3,0:3]=[3,4,5] # matrix as 4 column RGBA pixels
    bone_texture=g.texture(17,4,256,palette)
    color=g.texture(0,64,64);fb=U();g.glGenFramebuffers(1,C.byref(fb));g.glBindFramebuffer(0x8D40,fb.value)
    g.glFramebufferTexture2D(0x8D40,0x8CE0,0x0DE1,color,0)
    status=g.glCheckFramebufferStatus(0x8D40)
    if status!=0x8CD5:raise RuntimeError(f'float FBO incomplete: 0x{status:04x}')
    g.glViewport(0,0,64,64);g.glDisable(0x0B71);g.glDisable(0x0BE2)
    g.buffer(0,np.array([[1,2,3]],np.float32),3,0x1406)
    g.buffer(7,np.array([[255,0,0,0]],np.uint8),4,0x1401,True)
    g.buffer(8,np.array([[0,0,0,0]],np.uint8),4,0x1401)
    g.buffer(9,np.array([[-1+1/64,-1+1/64]],np.float32),2,0x1406)
    g.glDisableVertexAttribArray(4);g.glVertexAttrib3f(4,0,0,1);g.glDisableVertexAttribArray(5);g.glVertexAttrib3f(5,0,0,1)
    g.glClearColor(0,0,0,0);g.glClear(0x4000);g.glDrawArrays(0,0,1);g.glFinish();g.check('draw')
    result=np.empty((64,64,4),np.float32);g.glReadPixels(0,0,64,64,0x1908,0x1406,ptr(result));g.check('read')
    print('vertex transformed:',result[0,0]); np.testing.assert_allclose(result[0,0,:3],[4,6,8],rtol=0,atol=0.0001)
    # The engine's shared frame buffer contains consecutive actor palettes.
    # It must slice from each actor's base, then the shader indexes from zero.
    actor_buffer=np.zeros((3,4,4),np.float32)
    for i,translation in enumerate(((10,0,0),(0,20,0),(0,0,30))):
        actor_buffer[i]=np.eye(4,dtype=np.float32);actor_buffer[i,3,:3]=translation
    def render(expected):
        g.glClear(0x4000);g.glDrawArrays(0,0,1);g.glFinish();g.check('multi-actor draw')
        g.glReadPixels(0,0,64,64,0x1908,0x1406,ptr(result));g.check('multi-actor read')
        np.testing.assert_allclose(result[0,0,:3],expected,rtol=0,atol=1e-4)
    for actor,expected in ((2,(1,2,33)),(0,(11,2,3)),(1,(1,22,3)),(2,(1,2,33))):
        actor_slice=np.ascontiguousarray(actor_buffer[actor:])
        g.glActiveTexture(0x84C0+17);g.glBindTexture(0x0DE1,bone_texture)
        g.glTexSubImage2D(0x0DE1,0,0,0,4,len(actor_slice),0x1908,0x1406,ptr(actor_slice))
        render(expected)
    g.uniform_i(p,'uBoneIndexBase',-1);render((1,2,3))
    g.uniform_i(p,'uBoneIndexBase',0)
    g.buffer(7,np.array([[0,0,0,0]],np.uint8),4,0x1401,True);render((1,2,3))
    print('PASS: actor buffer bases, out-of-order draws, no-bone and zero-weight fallbacks')
    print('PASS: real GLSL 100, ES3 floating-point skeletal palette')

if __name__=='__main__':main()
