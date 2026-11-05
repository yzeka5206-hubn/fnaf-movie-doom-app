/* JNI entry for the converted, native FNAF launcher.
 * The shipped OpenTouch SDL nativeRunMain only initializes JNI thread state;
 * it deliberately does not dispatch SDL_main. The Java SDL thread calls that
 * initializer first, then calls this bridge. The v7 game path is unchanged.
 */
#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>

extern int SDL_main(int argc, char **argv);
#define TAG "FnafMenuRunner"

JNIEXPORT jint JNICALL
Java_com_msa_freedoom_OriginalMenuActivity_nativeRunConvertedLauncher(
        JNIEnv *env, jobject instance, jobjectArray arguments) {
    (void)instance;
    if (!arguments) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Missing launcher arguments");
        return -1;
    }
    jsize count = (*env)->GetArrayLength(env, arguments);
    if (count < 0 || count > 32) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Invalid argument count");
        return -1;
    }
    char **argv = calloc((size_t)count + 2, sizeof(*argv));
    if (!argv) return -1;
    argv[0] = strdup("app_process");
    if (!argv[0]) { free(argv); return -1; }
    int initialized = 1;
    for (jsize i = 0; i < count; ++i) {
        jstring value = (jstring)(*env)->GetObjectArrayElement(env, arguments, i);
        if ((*env)->ExceptionCheck(env)) goto error;
        if (value) {
            const char *utf = (*env)->GetStringUTFChars(env, value, 0);
            if (!utf) { (*env)->DeleteLocalRef(env, value); goto error; }
            argv[i + 1] = strdup(utf);
            (*env)->ReleaseStringUTFChars(env, value, utf);
            (*env)->DeleteLocalRef(env, value);
        } else {
            argv[i + 1] = strdup("");
        }
        if (!argv[i + 1]) goto error;
        initialized++;
    }
    __android_log_print(ANDROID_LOG_INFO, TAG, "Starting converted SDL launcher");
    jint status = (jint)SDL_main((int)count + 1, argv);
    __android_log_print(ANDROID_LOG_INFO, TAG, "Converted SDL launcher returned %d", status);
    for (int i = 0; i < initialized; ++i) free(argv[i]);
    free(argv);
    return status;
error:
    for (int i = 0; i < initialized; ++i) free(argv[i]);
    free(argv);
    __android_log_print(ANDROID_LOG_ERROR, TAG, "Could not prepare launcher arguments");
    return -1;
}
