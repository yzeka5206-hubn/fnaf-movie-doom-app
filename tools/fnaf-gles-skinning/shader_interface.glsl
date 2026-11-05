

		// light buffers
		uniform vec4 lights[MAXIMUM_LIGHT_VECTORS];

		// bone matrix buffers
		uniform mat4 bones[MAXIMUM_LIGHT_VECTORS];

		uniform	mat4 ProjectionMatrix;
		uniform	mat4 ViewMatrix;
		uniform	mat4 NormalViewMatrix;

		uniform	vec4 uCameraPos;
		uniform	vec4 uClipLine;

		uniform	float uGlobVis;			// uGlobVis = R_GetGlobVis(r_visibility) / 32.0
		uniform	int uPalLightLevels;	
		uniform	int uViewHeight;		// Software fuzz scaling
		uniform	float uClipHeight;
		uniform	float uClipHeightDirection;
		uniform	int uShadowmapFilter;

		uniform float uThickFogDistance;
		uniform float uThickFogMultiplier;

		uniform int uTextureMode;
		uniform vec2 uClipSplit;
		uniform float uAlphaThreshold;

		// colors
		uniform vec4 uObjectColor;
		uniform vec4 uObjectColor2;
		uniform vec4 uDynLightColor;
		uniform vec4 uAddColor;
		uniform vec4 uTextureBlendColor;
		uniform vec4 uTextureModulateColor;
		uniform vec4 uTextureAddColor;
		uniform vec4 uFogColor;
		uniform float uDesaturationFactor;
		uniform float uInterpolationFactor;

		// Glowing walls stuff
		uniform vec4 uGlowTopPlane;
		uniform vec4 uGlowTopColor;
		uniform vec4 uGlowBottomPlane;
		uniform vec4 uGlowBottomColor;

		uniform vec4 uGradientTopPlane;
		uniform vec4 uGradientBottomPlane;

		uniform vec4 uSplitTopPlane;
		uniform vec4 uSplitBottomPlane;

		uniform vec4 uDetailParms;
		// Lighting + Fog
		uniform vec4 uLightAttr;
		#define uLightLevel uLightAttr.a
		#define uFogDensity uLightAttr.b
		#define uLightFactor uLightAttr.g
		#define uLightDist uLightAttr.r
		//uniform int uFogEnabled;

		// dynamic lights
		uniform ivec4 uLightRange;

		// bone animation
		uniform int uBoneIndexBase;

		// Blinn glossiness and specular level
		uniform vec2 uSpecularMaterial;

		// matrices
		uniform mat4 ModelMatrix;
		uniform mat4 NormalModelMatrix;
		uniform mat4 TextureMatrix;

		uniform vec4 uFixedColormapStart;
		uniform vec4 uFixedColormapRange;

		// textures
		uniform sampler2D tex;
		uniform sampler2D ShadowMap;
		uniform sampler2D texture2;
		uniform sampler2D texture3;
		uniform sampler2D texture4;
		uniform sampler2D texture5;
		uniform sampler2D texture6;
		uniform sampler2D texture7;
		uniform sampler2D texture8;
		uniform sampler2D texture9;
		uniform sampler2D texture10;
		uniform sampler2D texture11;

		// timer data
		uniform float timer;

		// material types
		#if defined(SPECULAR)
		#define normaltexture texture2
		#define speculartexture texture3
		#define brighttexture texture4
		#define detailtexture texture5
		#define glowtexture texture6
		#elif defined(PBR)
		#define normaltexture texture2
		#define metallictexture texture3
		#define roughnesstexture texture4
		#define aotexture texture5
		#define brighttexture texture6
		#define detailtexture texture7
		#define glowtexture texture8
		#else
		#define brighttexture texture2
		#define detailtexture texture3
		#define glowtexture texture4
		#endif
	
