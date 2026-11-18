#include <jni.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <pthread.h>

#if defined(__BYTE_ORDER__) && defined(__ORDER_LITTLE_ENDIAN__) \
        && __BYTE_ORDER__ != __ORDER_LITTLE_ENDIAN__
#error "Packed M3G vertex colors currently require a little-endian Android ABI"
#endif

#ifndef M3G_GPU_DEPTH_DIAGNOSTIC
#define M3G_GPU_DEPTH_DIAGNOSTIC 0
#endif

#define M3G_MAX_TEXTURES 512

typedef struct {
    jint key;
    GLuint texture;
    jint width;
    jint height;
} texture_entry;

typedef struct {
    EGLDisplay display;
    EGLContext context;
    EGLSurface surface;
    EGLConfig config;
    GLuint framebuffer;
    GLuint color_texture;
    GLuint depth_buffer;
    GLuint vertex_buffer;
    GLuint index_buffer;
    GLuint program;
    GLint position;
    GLint texcoord;
    GLint color;
    GLint distance;
    GLint sampler;
    GLint has_texture;
    GLint texture_blend;
    GLint blend_color;
    GLint alpha_threshold;
    GLint fog_mode;
    GLint fog_color;
    GLint fog_near;
    GLint fog_far;
    GLint fog_density;
    GLint projection_mode;
    GLint camera_near;
    GLint camera_far;
    GLint alpha_pass;
    GLint transform_enabled;
    GLint model_view;
    GLint mvp;
    jint width;
    jint height;
    int ready;
    int draw_state_ready;
    pthread_t current_thread;
    int current_thread_valid;
    int texture_count;
    jboolean profile_enabled;
    texture_entry textures[M3G_MAX_TEXTURES];
} gpu_context;

static gpu_context gpu;

enum {
    PROFILE_JNI_ACQUIRE,
    PROFILE_TEXTURE,
    PROFILE_VERTEX_UPLOAD,
    PROFILE_INDEX_VALIDATE,
    PROFILE_INDEX_UPLOAD,
    PROFILE_STATE_UNIFORM,
    PROFILE_DRAW_CPU,
    PROFILE_JNI_RELEASE,
    PROFILE_NATIVE_CALLS,
    PROFILE_GL_DRAWS,
    PROFILE_VERTEX_BYTES,
    PROFILE_INDEX_BYTES,
    PROFILE_MAKE_CURRENT,
    PROFILE_VALIDATE,
    PROFILE_GL_ERROR,
    PROFILE_NATIVE_TOTAL,
    PROFILE_CONTEXT_BINDS,
    PROFILE_CONTEXT_SKIPS,
    PROFILE_READ_CONTEXT,
    PROFILE_READ_ALLOC,
    PROFILE_READ_PIXELS,
    PROFILE_READ_CONVERT,
    PROFILE_READ_COPY,
    PROFILE_READ_TOTAL,
    PROFILE_READ_FRAMES,
    PROFILE_END_CONTEXT,
    PROFILE_END_FINISH,
    PROFILE_STAT_COUNT
};

static uint64_t gpu_profile_stats[PROFILE_STAT_COUNT];

static uint64_t profile_now_ns(void) {
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    return (uint64_t)now.tv_sec * 1000000000ULL + (uint64_t)now.tv_nsec;
}

static jboolean profile_return(jboolean result, jboolean profile_enabled,
        uint64_t native_started) {
    if (profile_enabled)
        gpu_profile_stats[PROFILE_NATIVE_TOTAL] += profile_now_ns() - native_started;
    return result;
}

static void profiled_draw_elements(jint index_count, jboolean profile_enabled) {
    uint64_t started = profile_enabled ? profile_now_ns() : 0;
    glDrawElements(GL_TRIANGLES, index_count, GL_UNSIGNED_SHORT, (const void *)0);
    if (profile_enabled) {
        gpu_profile_stats[PROFILE_DRAW_CPU] += profile_now_ns() - started;
        gpu_profile_stats[PROFILE_GL_DRAWS]++;
    }
}

static int has_extension(const char *extensions, const char *wanted) {
    if (!extensions || !wanted || !*wanted || strchr(wanted, ' ')) return 0;
    size_t length = strlen(wanted);
    const char *match = extensions;
    while ((match = strstr(match, wanted)) != NULL) {
        int starts_token = match == extensions || match[-1] == ' ';
        int ends_token = match[length] == '\0' || match[length] == ' ';
        if (starts_token && ends_token) return 1;
        match += length;
    }
    return 0;
}

static const char *vertex_shader =
    "attribute vec4 aPosition;\n"
    "attribute vec2 aTexCoord;\n"
    "attribute vec4 aColor;\n"
    "attribute float aDistance;\n"
    "varying vec2 vTexCoord;\n"
    "varying vec4 vColor;\n"
    "varying float vDistance;\n"
    "uniform int uTransformEnabled;\n"
    "uniform mat4 uModelView;\n"
    "uniform mat4 uMvp;\n"
    "void main(){gl_Position=vec4(aPosition.xy,-aPosition.z,aPosition.w);"
    "if(uTransformEnabled==1){vec4 eye=uModelView*aPosition;"
    "vec4 clip=uMvp*aPosition;gl_Position=vec4(clip.xy,-clip.z,clip.w);"
    "vDistance=-eye.z;}"
    "vTexCoord=aTexCoord;vColor=aColor;"
    "if(uTransformEnabled==0)vDistance=aDistance;}\n";

static const char *fragment_shader =
    // Repeated terrain UVs can become large at distance; mediump loses their
    // fractional precision and can turn texture samples into visible bands.
    // If fragment highp is unsupported, shader compilation fails and the
    // renderer safely falls back to the software path.
    "precision highp float;\n"
    "uniform sampler2D uTexture;\n"
    "uniform float uHasTexture;\n"
    "uniform int uTextureBlend;\n"
    "uniform vec3 uBlendColor;\n"
    "uniform float uAlphaThreshold;\n"
    "uniform int uFogMode;\n"
    "uniform vec3 uFogColor;\n"
    "uniform float uFogNear;\n"
    "uniform float uFogFar;\n"
    "uniform float uFogDensity;\n"
    "uniform int uProjectionMode;\n"
    "uniform float uCameraNear;\n"
    "uniform float uCameraFar;\n"
    "uniform int uAlphaPass;\n"
    "varying vec2 vTexCoord;\n"
    "varying vec4 vColor;\n"
    "varying float vDistance;\n"
    "void main(){\n"
    "vec4 c=vColor;\n"
    "if(uHasTexture>0.5){vec4 t=texture2D(uTexture,vTexCoord);"
    "if(uTextureBlend==228)c=vec4(t.rgb,t.a);"
    "else if(uTextureBlend==226)c=vec4(mix(c.rgb,t.rgb,t.a),c.a);"
    "else if(uTextureBlend==225)c=vec4(mix(uBlendColor,t.rgb,c.rgb),c.a*t.a);"
    "else c*=t;}\n"
    "if(c.a<=0.0||c.a<uAlphaThreshold)discard;"
    "if(uAlphaPass==1&&c.a<0.9981)discard;"
    "if(uAlphaPass==2&&c.a>=0.9981)discard;\n"
    "float d=vDistance;if(uProjectionMode==50){d=(uCameraNear*uCameraFar)/"
    "(uCameraNear+gl_FragCoord.z*(uCameraFar-uCameraNear));}"
    "else if(uProjectionMode==49){d=uCameraFar-gl_FragCoord.z*"
    "(uCameraFar-uCameraNear);}\n"
    "float f=0.0;if(uFogMode==81){f=uFogFar<=uFogNear?"
    "(d>=uFogFar?1.0:0.0):clamp((d-uFogNear)/(uFogFar-uFogNear),0.0,1.0);}"
    "else if(uFogMode==80){f=1.0-exp(-uFogDensity*d);}\n"
    "c.rgb=mix(c.rgb,uFogColor,clamp(f,0.0,1.0));\n"
#if M3G_GPU_DEPTH_DIAGNOSTIC
    "float contourPhase=fract(log2(1.0+max(vDistance,0.0))*0.75);"
    "float contourLine=1.0-smoothstep(0.015,0.055,abs(contourPhase-0.5));"
    "c.rgb=mix(c.rgb,vec3(1.0,0.05,0.85),contourLine*0.70);\n"
#endif
    "gl_FragColor=c;}\n";

static GLuint compile_shader(GLenum type, const char *source) {
    GLuint shader = glCreateShader(type);
    if (!shader) return 0;
    glShaderSource(shader, 1, &source, NULL);
    glCompileShader(shader);
    GLint compiled = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (!compiled) {
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

static int make_current(void) {
    if (gpu.display == EGL_NO_DISPLAY || gpu.context == EGL_NO_CONTEXT
            || gpu.surface == EGL_NO_SURFACE
            || !eglMakeCurrent(gpu.display, gpu.surface, gpu.surface, gpu.context))
        return 0;
    gpu.current_thread = pthread_self();
    gpu.current_thread_valid = 1;
    return 1;
}

static int make_current_for_draw(jboolean profile_enabled) {
    if (gpu.current_thread_valid
            && pthread_equal(gpu.current_thread, pthread_self())) {
        if (profile_enabled) gpu_profile_stats[PROFILE_CONTEXT_SKIPS]++;
        return 1;
    }
    if (profile_enabled) gpu_profile_stats[PROFILE_CONTEXT_BINDS]++;
    return make_current();
}

static int initialize_context(void) {
    if (gpu.display != EGL_NO_DISPLAY && gpu.context != EGL_NO_CONTEXT)
        return gpu.ready && make_current();
    gpu.display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (gpu.display == EGL_NO_DISPLAY) return 0;
    EGLint major = 0, minor = 0;
    if (!eglInitialize(gpu.display, &major, &minor)) return 0;
    if (!eglBindAPI(EGL_OPENGL_ES_API)) return 0;
    const EGLint config_attributes[] = {
        EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, 24, EGL_NONE
    };
    EGLint count = 0;
    if (!eglChooseConfig(gpu.display, config_attributes, &gpu.config, 1, &count)
            || count == 0) return 0;
    const EGLint context_attributes[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };
    gpu.context = eglCreateContext(gpu.display, gpu.config, EGL_NO_CONTEXT, context_attributes);
    if (gpu.context == EGL_NO_CONTEXT) return 0;
    const EGLint surface_attributes[] = { EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE };
    gpu.surface = eglCreatePbufferSurface(gpu.display, gpu.config, surface_attributes);
    if (gpu.surface == EGL_NO_SURFACE || !make_current()) return 0;
    // The software renderer uses float depth. Keep GPU depth precision high
    // enough for distant voxel terrain; otherwise fall back to software rather
    // than drawing z-fighting bands with a 16-bit depth buffer.
    const char *extensions = (const char *)glGetString(GL_EXTENSIONS);
    if (!has_extension(extensions, "GL_OES_depth24")) return 0;

    GLuint vs = compile_shader(GL_VERTEX_SHADER, vertex_shader);
    GLuint fs = compile_shader(GL_FRAGMENT_SHADER, fragment_shader);
    if (!vs || !fs) return 0;
    gpu.program = glCreateProgram();
    glAttachShader(gpu.program, vs);
    glAttachShader(gpu.program, fs);
    glBindAttribLocation(gpu.program, 0, "aPosition");
    glBindAttribLocation(gpu.program, 1, "aTexCoord");
    glBindAttribLocation(gpu.program, 2, "aColor");
    glBindAttribLocation(gpu.program, 3, "aDistance");
    glLinkProgram(gpu.program);
    glDeleteShader(vs);
    glDeleteShader(fs);
    GLint linked = 0;
    glGetProgramiv(gpu.program, GL_LINK_STATUS, &linked);
    if (!linked) return 0;
    gpu.position = glGetAttribLocation(gpu.program, "aPosition");
    gpu.texcoord = glGetAttribLocation(gpu.program, "aTexCoord");
    gpu.color = glGetAttribLocation(gpu.program, "aColor");
    gpu.distance = glGetAttribLocation(gpu.program, "aDistance");
    gpu.sampler = glGetUniformLocation(gpu.program, "uTexture");
    gpu.has_texture = glGetUniformLocation(gpu.program, "uHasTexture");
    gpu.texture_blend = glGetUniformLocation(gpu.program, "uTextureBlend");
    gpu.blend_color = glGetUniformLocation(gpu.program, "uBlendColor");
    gpu.alpha_threshold = glGetUniformLocation(gpu.program, "uAlphaThreshold");
    gpu.fog_mode = glGetUniformLocation(gpu.program, "uFogMode");
    gpu.fog_color = glGetUniformLocation(gpu.program, "uFogColor");
    gpu.fog_near = glGetUniformLocation(gpu.program, "uFogNear");
    gpu.fog_far = glGetUniformLocation(gpu.program, "uFogFar");
    gpu.fog_density = glGetUniformLocation(gpu.program, "uFogDensity");
    gpu.projection_mode = glGetUniformLocation(gpu.program, "uProjectionMode");
    gpu.camera_near = glGetUniformLocation(gpu.program, "uCameraNear");
    gpu.camera_far = glGetUniformLocation(gpu.program, "uCameraFar");
    gpu.alpha_pass = glGetUniformLocation(gpu.program, "uAlphaPass");
    gpu.transform_enabled = glGetUniformLocation(gpu.program, "uTransformEnabled");
    gpu.model_view = glGetUniformLocation(gpu.program, "uModelView");
    gpu.mvp = glGetUniformLocation(gpu.program, "uMvp");
    glGenBuffers(1, &gpu.vertex_buffer);
    glGenBuffers(1, &gpu.index_buffer);
    if (!gpu.vertex_buffer || !gpu.index_buffer || glGetError() != GL_NO_ERROR)
        return 0;
    gpu.ready = 1;
    return 1;
}

static int resize_target(jint width, jint height) {
    if (width < 1 || height < 1 || width > 4096 || height > 4096) return 0;
    if (gpu.width == width && gpu.height == height && gpu.framebuffer) return 1;
    if (gpu.framebuffer) glDeleteFramebuffers(1, &gpu.framebuffer);
    if (gpu.color_texture) glDeleteTextures(1, &gpu.color_texture);
    if (gpu.depth_buffer) glDeleteRenderbuffers(1, &gpu.depth_buffer);
    gpu.framebuffer = gpu.color_texture = gpu.depth_buffer = 0;
    glGenTextures(1, &gpu.color_texture);
    glBindTexture(GL_TEXTURE_2D, gpu.color_texture);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0,
            GL_RGBA, GL_UNSIGNED_BYTE, NULL);
    glGenRenderbuffers(1, &gpu.depth_buffer);
    glBindRenderbuffer(GL_RENDERBUFFER, gpu.depth_buffer);
    glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24_OES, width, height);
    glGenFramebuffers(1, &gpu.framebuffer);
    glBindFramebuffer(GL_FRAMEBUFFER, gpu.framebuffer);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
            GL_TEXTURE_2D, gpu.color_texture, 0);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT,
            GL_RENDERBUFFER, gpu.depth_buffer);
    GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    gpu.width = width;
    gpu.height = height;
    return status == GL_FRAMEBUFFER_COMPLETE && glGetError() == GL_NO_ERROR;
}

static void put_pixel(uint8_t *out, int x, int y, uint32_t argb) {
    size_t index = ((size_t)y * (size_t)gpu.width + (size_t)x) * 4;
    out[index] = (uint8_t)((argb >> 16) & 255);
    out[index + 1] = (uint8_t)((argb >> 8) & 255);
    out[index + 2] = (uint8_t)(argb & 255);
    out[index + 3] = (uint8_t)((argb >> 24) & 255);
}

static texture_entry *find_texture_entry(jint key) {
    for (int i = 0; i < gpu.texture_count; ++i)
        if (gpu.textures[i].key == key) return &gpu.textures[i];
    return NULL;
}

static GLuint upload_texture(JNIEnv *env, jint key, jintArray pixels,
        jint width, jint height, const jint *state) {
    if (key == 0 || !pixels || width < 1 || height < 1
            || (jlong)width * height > 16777216) return 0;
    texture_entry *entry = find_texture_entry(key);
    GLuint id;
    if (entry) {
        if (entry->width != width || entry->height != height) return 0;
        glBindTexture(GL_TEXTURE_2D, entry->texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER,
                state[0] ? GL_LINEAR : GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER,
                state[0] ? GL_LINEAR : GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S,
                state[1] ? GL_REPEAT : GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T,
                state[2] ? GL_REPEAT : GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);
        return entry->texture;
    } else {
        if (gpu.texture_count >= M3G_MAX_TEXTURES) return 0;
        glGenTextures(1, &id);
        entry = &gpu.textures[gpu.texture_count++];
        entry->key = key;
        entry->texture = id;
        entry->width = width;
        entry->height = height;
    }
    id = entry->texture;
    jsize count = (*env)->GetArrayLength(env, pixels);
    if (count < width * height) return 0;
    jint *source = (*env)->GetIntArrayElements(env, pixels, NULL);
    if (!source) return 0;
    size_t byte_count = (size_t)width * (size_t)height * 4;
    uint8_t *rgba = malloc(byte_count);
    if (!rgba) {
        (*env)->ReleaseIntArrayElements(env, pixels, source, JNI_ABORT);
        return 0;
    }
    for (jint i = 0; i < width * height; ++i) {
        uint32_t argb = (uint32_t)source[i];
        size_t at = (size_t)i * 4;
        rgba[at] = (uint8_t)((argb >> 16) & 255);
        rgba[at + 1] = (uint8_t)((argb >> 8) & 255);
        rgba[at + 2] = (uint8_t)(argb & 255);
        rgba[at + 3] = (uint8_t)((argb >> 24) & 255);
    }
    (*env)->ReleaseIntArrayElements(env, pixels, source, JNI_ABORT);
    glBindTexture(GL_TEXTURE_2D, id);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0,
            GL_RGBA, GL_UNSIGNED_BYTE, rgba);
    free(rgba);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER,
            state[0] ? GL_LINEAR : GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER,
            state[0] ? GL_LINEAR : GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S,
            state[1] ? GL_REPEAT : GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T,
            state[2] ? GL_REPEAT : GL_CLAMP_TO_EDGE);
    glBindTexture(GL_TEXTURE_2D, 0);
    return glGetError() == GL_NO_ERROR ? id : 0;
}

static jboolean gpu_begin(JNIEnv *env, jclass type, jint width, jint height,
        jintArray initial_pixels) {
    gpu.profile_enabled = JNI_FALSE;
    if (!initialize_context() || !resize_target(width, height)) return JNI_FALSE;
    gpu.draw_state_ready = 0;
    glBindFramebuffer(GL_FRAMEBUFFER, gpu.framebuffer);
    glViewport(0, 0, width, height);
    if (initial_pixels) {
        jsize count = (*env)->GetArrayLength(env, initial_pixels);
        if ((jlong)count < (jlong)width * height) return JNI_FALSE;
        jint *source = (*env)->GetIntArrayElements(env, initial_pixels, NULL);
        if (!source) return JNI_FALSE;
        size_t bytes = (size_t)width * (size_t)height * 4;
        uint8_t *rgba = malloc(bytes);
        if (!rgba) {
            (*env)->ReleaseIntArrayElements(env, initial_pixels, source, JNI_ABORT);
            return JNI_FALSE;
        }
        for (int y = 0; y < height; ++y)
            for (int x = 0; x < width; ++x)
                put_pixel(rgba, x, height - 1 - y,
                        (uint32_t)source[(size_t)y * width + x]);
        glBindTexture(GL_TEXTURE_2D, gpu.color_texture);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height,
                GL_RGBA, GL_UNSIGNED_BYTE, rgba);
        free(rgba);
        (*env)->ReleaseIntArrayElements(env, initial_pixels, source, JNI_ABORT);
    }
    glClearDepthf(0.0f);
    glDepthMask(GL_TRUE);
    glClear(GL_DEPTH_BUFFER_BIT);
    glEnable(GL_DEPTH_TEST);
    glDepthFunc(GL_GREATER);
    glDisable(GL_DITHER);
    glBindFramebuffer(GL_FRAMEBUFFER, gpu.framebuffer);
    return glGetError() == GL_NO_ERROR ? JNI_TRUE : JNI_FALSE;
}

static jboolean gpu_clear(JNIEnv *env, jclass type, jint argb,
        jboolean clear_color, jboolean clear_depth) {
    if (!gpu.ready || !make_current()) return JNI_FALSE;
    glBindFramebuffer(GL_FRAMEBUFFER, gpu.framebuffer);
    GLbitfield mask = 0;
    if (clear_color) {
        glClearColor(((argb >> 16) & 255) / 255.0f,
                ((argb >> 8) & 255) / 255.0f,
                (argb & 255) / 255.0f, 1.0f);
        mask |= GL_COLOR_BUFFER_BIT;
    }
    if (clear_depth) {
        glClearDepthf(0.0f);
        mask |= GL_DEPTH_BUFFER_BIT;
    }
    glClear(mask);
    return glGetError() == GL_NO_ERROR ? JNI_TRUE : JNI_FALSE;
}

static jboolean gpu_draw(JNIEnv *env, jclass type, jintArray vertex_array,
        jint vertex_word_count, jshortArray index_array, jint index_count,
        jint texture_key, jintArray texture_pixels,
        jintArray state_array, jfloatArray parameter_array, jboolean profile_enabled) {
    gpu.profile_enabled = profile_enabled;
    uint64_t native_started = profile_enabled ? profile_now_ns() : 0;
    uint64_t stage_started = profile_enabled ? profile_now_ns() : 0;
    if (!gpu.ready || !vertex_array || !index_array
            || !state_array || !parameter_array) {
        if (profile_enabled)
            gpu_profile_stats[PROFILE_VALIDATE] += profile_now_ns() - stage_started;
        return profile_return(JNI_FALSE, profile_enabled, native_started);
    }
    stage_started = profile_enabled ? profile_now_ns() : 0;
    jboolean current = make_current_for_draw(profile_enabled) ? JNI_TRUE : JNI_FALSE;
    if (profile_enabled)
        gpu_profile_stats[PROFILE_MAKE_CURRENT] += profile_now_ns() - stage_started;
    if (!current) return profile_return(JNI_FALSE, profile_enabled, native_started);

    stage_started = profile_enabled ? profile_now_ns() : 0;
    jsize vertex_array_length = (*env)->GetArrayLength(env, vertex_array);
    jsize index_array_length = (*env)->GetArrayLength(env, index_array);
    if (vertex_word_count < 27 || vertex_word_count % 9 != 0
            || vertex_word_count > vertex_array_length || index_count < 3
            || index_count > index_array_length || index_count > 300000
            || vertex_word_count / 9 > 65535) {
        if (profile_enabled)
            gpu_profile_stats[PROFILE_VALIDATE] += profile_now_ns() - stage_started;
        return profile_return(JNI_FALSE, profile_enabled, native_started);
    }
    if ((*env)->GetArrayLength(env, state_array) < 17
            || (*env)->GetArrayLength(env, parameter_array) < 46) {
        if (profile_enabled)
            gpu_profile_stats[PROFILE_VALIDATE] += profile_now_ns() - stage_started;
        return profile_return(JNI_FALSE, profile_enabled, native_started);
    }
    if (profile_enabled)
        gpu_profile_stats[PROFILE_VALIDATE] += profile_now_ns() - stage_started;
    stage_started = profile_enabled ? profile_now_ns() : 0;
    jint *state = (*env)->GetIntArrayElements(env, state_array, NULL);
    jfloat *params = (*env)->GetFloatArrayElements(env, parameter_array, NULL);
    jint *vertices = (*env)->GetIntArrayElements(env, vertex_array, NULL);
    jshort *indices = (*env)->GetShortArrayElements(env, index_array, NULL);
    if (profile_enabled)
        gpu_profile_stats[PROFILE_JNI_ACQUIRE] += profile_now_ns() - stage_started;
    if (!state || !params || !vertices || !indices) goto fail;
    if (profile_enabled) gpu_profile_stats[PROFILE_NATIVE_CALLS]++;
    stage_started = profile_enabled ? profile_now_ns() : 0;
    GLuint texture = texture_key == 0 ? 0
            : upload_texture(env, texture_key, texture_pixels,
                    state[12], state[13], state);
    if (profile_enabled)
        gpu_profile_stats[PROFILE_TEXTURE] += profile_now_ns() - stage_started;
    if (texture_key != 0 && texture == 0) goto fail;

    stage_started = profile_enabled ? profile_now_ns() : 0;
    if (!gpu.draw_state_ready) {
        glUseProgram(gpu.program);
        glBindBuffer(GL_ARRAY_BUFFER, gpu.vertex_buffer);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, gpu.index_buffer);
        glEnableVertexAttribArray((GLuint)gpu.position);
        glEnableVertexAttribArray((GLuint)gpu.texcoord);
        glEnableVertexAttribArray((GLuint)gpu.color);
        glEnableVertexAttribArray((GLuint)gpu.distance);
        glVertexAttribPointer((GLuint)gpu.position, 4, GL_FLOAT, GL_FALSE,
                9 * sizeof(jint), (const void *)(uintptr_t)0);
        glVertexAttribPointer((GLuint)gpu.texcoord, 2, GL_FLOAT, GL_FALSE,
                9 * sizeof(jint), (const void *)(uintptr_t)(4 * sizeof(jint)));
        glVertexAttribPointer((GLuint)gpu.color, 4, GL_UNSIGNED_SHORT, GL_TRUE,
                9 * sizeof(jint), (const void *)(uintptr_t)(6 * sizeof(jint)));
        glVertexAttribPointer((GLuint)gpu.distance, 1, GL_FLOAT, GL_FALSE,
                9 * sizeof(jint), (const void *)(uintptr_t)(8 * sizeof(jint)));
        gpu.draw_state_ready = 1;
    }
    glBufferData(GL_ARRAY_BUFFER, (GLsizeiptr)vertex_word_count * sizeof(jint),
            vertices, GL_STREAM_DRAW);
    if (profile_enabled) {
        gpu_profile_stats[PROFILE_VERTEX_UPLOAD] += profile_now_ns() - stage_started;
        gpu_profile_stats[PROFILE_VERTEX_BYTES] +=
                (uint64_t)vertex_word_count * sizeof(jint);
    }
    stage_started = profile_enabled ? profile_now_ns() : 0;
    for (jsize i = 0; i < index_count; ++i) {
        if ((uint16_t)indices[i] >= vertex_word_count / 9) {
            if (profile_enabled)
                gpu_profile_stats[PROFILE_INDEX_VALIDATE] += profile_now_ns() - stage_started;
            goto fail;
        }
    }
    if (profile_enabled)
        gpu_profile_stats[PROFILE_INDEX_VALIDATE] += profile_now_ns() - stage_started;
    stage_started = profile_enabled ? profile_now_ns() : 0;
    glBufferData(GL_ELEMENT_ARRAY_BUFFER, (GLsizeiptr)index_count * sizeof(uint16_t),
            indices, GL_STREAM_DRAW);
    if (profile_enabled) {
        gpu_profile_stats[PROFILE_INDEX_UPLOAD] += profile_now_ns() - stage_started;
        gpu_profile_stats[PROFILE_INDEX_BYTES] += (uint64_t)index_count * sizeof(uint16_t);
    }

    stage_started = profile_enabled ? profile_now_ns() : 0;
    if (state[6]) glEnable(GL_DEPTH_TEST); else glDisable(GL_DEPTH_TEST);
    glDepthFunc(GL_GREATER);
    glDepthMask(state[7] && !state[15] ? GL_TRUE : GL_FALSE);
    glColorMask(state[8] ? GL_TRUE : GL_FALSE,
            state[8] ? GL_TRUE : GL_FALSE,
            state[8] ? GL_TRUE : GL_FALSE,
            state[10] ? GL_TRUE : GL_FALSE);
    if (state[4] == 160 || state[4] == 161) {
        glEnable(GL_CULL_FACE);
        glCullFace(state[4] == 160 ? GL_BACK : GL_FRONT);
    } else glDisable(GL_CULL_FACE);
    glFrontFace(state[5] == 169 ? GL_CW : GL_CCW);
    if (state[9] == 68) {
        glDisable(GL_BLEND);
    } else {
        glEnable(GL_BLEND);
        if (state[9] == 65)
            glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE, GL_ONE, GL_ONE);
        else if (state[9] == 66)
            glBlendFuncSeparate(GL_DST_COLOR, GL_ZERO, GL_DST_ALPHA, GL_ZERO);
        else if (state[9] == 67)
            glBlendFuncSeparate(GL_DST_COLOR, GL_SRC_COLOR, GL_DST_ALPHA, GL_ZERO);
        else glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA,
                GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
    }
    glPolygonOffset(-params[10], -params[11]);
    if (params[10] != 0.0f || params[11] != 0.0f) glEnable(GL_POLYGON_OFFSET_FILL);
    else glDisable(GL_POLYGON_OFFSET_FILL);

    glActiveTexture(GL_TEXTURE0);
    if (texture) {
        glBindTexture(GL_TEXTURE_2D, texture);
        glUniform1f(gpu.has_texture, 1.0f);
        glUniform1i(gpu.texture_blend, state[3]);
        glUniform3f(gpu.blend_color, params[1], params[2], params[3]);
    } else {
        glUniform1f(gpu.has_texture, 0.0f);
        glUniform1i(gpu.texture_blend, 0);
    }
    glUniform1i(gpu.sampler, 0);
    glUniform1f(gpu.alpha_threshold, params[0]);
    glUniform1i(gpu.fog_mode, state[11]);
    glUniform3f(gpu.fog_color, params[4], params[5], params[6]);
    glUniform1f(gpu.fog_near, params[7]);
    glUniform1f(gpu.fog_far, params[8]);
    glUniform1f(gpu.fog_density, params[9]);
    glUniform1i(gpu.projection_mode, state[14]);
    glUniform1f(gpu.camera_near, params[12]);
    glUniform1f(gpu.camera_far, params[13]);
    glUniform1i(gpu.transform_enabled, state[16]);
    if (state[16]) {
        glUniformMatrix4fv(gpu.model_view, 1, GL_FALSE, &params[14]);
        glUniformMatrix4fv(gpu.mvp, 1, GL_FALSE, &params[30]);
    }
    if (profile_enabled)
        gpu_profile_stats[PROFILE_STATE_UNIFORM] += profile_now_ns() - stage_started;
    if (state[7] && state[15] && state[9] != 65) {
        // The software path lets only fully opaque fragments update depth
        // during its deferred translucent pass.
        stage_started = profile_enabled ? profile_now_ns() : 0;
        glUniform1i(gpu.alpha_pass, 1);
        glDepthMask(GL_TRUE);
        if (state[9] == 68) glDisable(GL_BLEND);
        else {
            glEnable(GL_BLEND);
            if (state[9] == 66)
                glBlendFuncSeparate(GL_DST_COLOR, GL_ZERO, GL_DST_ALPHA, GL_ZERO);
            else if (state[9] == 67)
                glBlendFuncSeparate(GL_DST_COLOR, GL_SRC_COLOR, GL_DST_ALPHA, GL_ZERO);
            else glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA,
                    GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        }
        if (profile_enabled)
            gpu_profile_stats[PROFILE_STATE_UNIFORM] += profile_now_ns() - stage_started;
        profiled_draw_elements(index_count, profile_enabled);
        stage_started = profile_enabled ? profile_now_ns() : 0;
        glUniform1i(gpu.alpha_pass, 2);
        glDepthMask(GL_FALSE);
        if (state[9] == 68) glDisable(GL_BLEND);
        else {
            glEnable(GL_BLEND);
            if (state[9] == 66)
                glBlendFuncSeparate(GL_DST_COLOR, GL_ZERO, GL_DST_ALPHA, GL_ZERO);
            else if (state[9] == 67)
                glBlendFuncSeparate(GL_DST_COLOR, GL_SRC_COLOR, GL_DST_ALPHA, GL_ZERO);
            else glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA,
                    GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        }
        if (profile_enabled)
            gpu_profile_stats[PROFILE_STATE_UNIFORM] += profile_now_ns() - stage_started;
        profiled_draw_elements(index_count, profile_enabled);
        stage_started = profile_enabled ? profile_now_ns() : 0;
        glUniform1i(gpu.alpha_pass, 0);
        if (profile_enabled)
            gpu_profile_stats[PROFILE_STATE_UNIFORM] += profile_now_ns() - stage_started;
    } else {
        stage_started = profile_enabled ? profile_now_ns() : 0;
        glUniform1i(gpu.alpha_pass, 0);
        glDepthMask(state[7] ? GL_TRUE : GL_FALSE);
        if (profile_enabled)
            gpu_profile_stats[PROFILE_STATE_UNIFORM] += profile_now_ns() - stage_started;
        profiled_draw_elements(index_count, profile_enabled);
    }
    stage_started = profile_enabled ? profile_now_ns() : 0;
    (*env)->ReleaseIntArrayElements(env, state_array, state, JNI_ABORT);
    (*env)->ReleaseFloatArrayElements(env, parameter_array, params, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, vertex_array, vertices, JNI_ABORT);
    (*env)->ReleaseShortArrayElements(env, index_array, indices, JNI_ABORT);
    if (profile_enabled)
        gpu_profile_stats[PROFILE_JNI_RELEASE] += profile_now_ns() - stage_started;
    stage_started = profile_enabled ? profile_now_ns() : 0;
    jboolean draw_succeeded = glGetError() == GL_NO_ERROR ? JNI_TRUE : JNI_FALSE;
    if (profile_enabled)
        gpu_profile_stats[PROFILE_GL_ERROR] += profile_now_ns() - stage_started;
    return profile_return(draw_succeeded, profile_enabled, native_started);

fail:
    stage_started = profile_enabled ? profile_now_ns() : 0;
    if (state) (*env)->ReleaseIntArrayElements(env, state_array, state, JNI_ABORT);
    if (params) (*env)->ReleaseFloatArrayElements(env, parameter_array, params, JNI_ABORT);
    if (vertices) (*env)->ReleaseIntArrayElements(env, vertex_array, vertices, JNI_ABORT);
    if (indices) (*env)->ReleaseShortArrayElements(env, index_array, indices, JNI_ABORT);
    if (profile_enabled)
        gpu_profile_stats[PROFILE_JNI_RELEASE] += profile_now_ns() - stage_started;
    return profile_return(JNI_FALSE, profile_enabled, native_started);
}

static jboolean gpu_take_profile_stats(JNIEnv *env, jclass type, jlongArray output) {
    const jsize legacy_stat_count = 18;
    if (!output || (*env)->GetArrayLength(env, output) < legacy_stat_count)
        return JNI_FALSE;
    jsize output_length = (*env)->GetArrayLength(env, output);
    jsize copy_count = output_length < PROFILE_STAT_COUNT
            ? output_length : PROFILE_STAT_COUNT;
    jlong values[PROFILE_STAT_COUNT];
    for (int i = 0; i < PROFILE_STAT_COUNT; ++i) {
        values[i] = (jlong)gpu_profile_stats[i];
        gpu_profile_stats[i] = 0;
    }
    (*env)->SetLongArrayRegion(env, output, 0, copy_count, values);
    return (*env)->ExceptionCheck(env) ? JNI_FALSE : JNI_TRUE;
}

static jboolean profile_read_return(jboolean result, jboolean profile_enabled,
        uint64_t started) {
    if (profile_enabled)
        gpu_profile_stats[PROFILE_READ_TOTAL] += profile_now_ns() - started;
    if (result && profile_enabled) gpu_profile_stats[PROFILE_READ_FRAMES]++;
    return result;
}

static jboolean gpu_read(JNIEnv *env, jclass type, jintArray output) {
    jboolean profile_enabled = gpu.profile_enabled;
    uint64_t total_started = profile_enabled ? profile_now_ns() : 0;
    if (!gpu.ready || !output)
        return profile_read_return(JNI_FALSE, profile_enabled, total_started);
    uint64_t stage_started = profile_enabled ? profile_now_ns() : 0;
    int current = make_current();
    if (profile_enabled)
        gpu_profile_stats[PROFILE_READ_CONTEXT] += profile_now_ns() - stage_started;
    if (!current || (*env)->GetArrayLength(env, output) < gpu.width * gpu.height)
        return profile_read_return(JNI_FALSE, profile_enabled, total_started);
    size_t bytes = (size_t)gpu.width * (size_t)gpu.height * 4;
    stage_started = profile_enabled ? profile_now_ns() : 0;
    uint8_t *rgba = malloc(bytes);
    jint *argb = malloc((size_t)gpu.width * (size_t)gpu.height * sizeof(jint));
    if (profile_enabled)
        gpu_profile_stats[PROFILE_READ_ALLOC] += profile_now_ns() - stage_started;
    if (!rgba || !argb) {
        free(rgba);
        free(argb);
        return profile_read_return(JNI_FALSE, profile_enabled, total_started);
    }
    glBindFramebuffer(GL_FRAMEBUFFER, gpu.framebuffer);
    stage_started = profile_enabled ? profile_now_ns() : 0;
    glReadPixels(0, 0, gpu.width, gpu.height, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
    GLenum read_error = glGetError();
    if (profile_enabled)
        gpu_profile_stats[PROFILE_READ_PIXELS] += profile_now_ns() - stage_started;
    if (read_error != GL_NO_ERROR) {
        free(rgba);
        free(argb);
        return profile_read_return(JNI_FALSE, profile_enabled, total_started);
    }
    stage_started = profile_enabled ? profile_now_ns() : 0;
    for (int y = 0; y < gpu.height; ++y) {
        int source_y = gpu.height - 1 - y;
        for (int x = 0; x < gpu.width; ++x) {
            size_t source = ((size_t)source_y * gpu.width + x) * 4;
            argb[(size_t)y * gpu.width + x] = (jint)(
                    ((uint32_t)rgba[source + 3] << 24)
                    | ((uint32_t)rgba[source] << 16)
                    | ((uint32_t)rgba[source + 1] << 8)
                    | (uint32_t)rgba[source + 2]);
        }
    }
    if (profile_enabled)
        gpu_profile_stats[PROFILE_READ_CONVERT] += profile_now_ns() - stage_started;
    stage_started = profile_enabled ? profile_now_ns() : 0;
    (*env)->SetIntArrayRegion(env, output, 0, gpu.width * gpu.height, argb);
    if (profile_enabled)
        gpu_profile_stats[PROFILE_READ_COPY] += profile_now_ns() - stage_started;
    free(rgba);
    free(argb);
    return profile_read_return(JNI_TRUE, profile_enabled, total_started);
}

static void gpu_end(JNIEnv *env, jclass type) {
    jboolean profile_enabled = gpu.profile_enabled;
    uint64_t stage_started = profile_enabled ? profile_now_ns() : 0;
    int current = gpu.ready && make_current();
    if (profile_enabled)
        gpu_profile_stats[PROFILE_END_CONTEXT] += profile_now_ns() - stage_started;
    if (current) {
        stage_started = profile_enabled ? profile_now_ns() : 0;
        glFinish();
        if (profile_enabled)
            gpu_profile_stats[PROFILE_END_FINISH] += profile_now_ns() - stage_started;
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }
    gpu.profile_enabled = JNI_FALSE;
}

static jboolean gpu_available(JNIEnv *env, jclass type) {
    return JNI_TRUE;
}

jboolean freej2me_register_m3g_gpu(JNIEnv *env) {
    jclass bridge = (*env)->FindClass(env, "javax/microedition/m3g/M3GNativeGpu");
    if (!bridge) { (*env)->ExceptionClear(env); return JNI_FALSE; }
    JNINativeMethod methods[] = {
        {"nativeBeginFrame", "(II[I)Z", (void *)gpu_begin},
        {"nativeClear", "(IZZ)Z", (void *)gpu_clear},
        {"nativeDraw", "([II[SII[I[I[FZ)Z", (void *)gpu_draw},
        {"nativeTakeProfileStats", "([J)Z", (void *)gpu_take_profile_stats},
        {"nativeReadFrame", "([I)Z", (void *)gpu_read},
        {"nativeEndFrame", "()V", (void *)gpu_end},
        {"nativeAvailable", "()Z", (void *)gpu_available}
    };
    jint result = (*env)->RegisterNatives(env, bridge, methods,
            (jint)(sizeof(methods) / sizeof(methods[0])));
    (*env)->DeleteLocalRef(env, bridge);
    if (result != JNI_OK) { (*env)->ExceptionClear(env); return JNI_FALSE; }
    return JNI_TRUE;
}
