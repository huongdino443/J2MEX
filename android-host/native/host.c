/* Minimal JNI invocation launcher. Embedded OpenJDK executes in a separate
 * Android process; no desktop/window bridge or Amethyst code is linked. */
#include <jni.h>
#include <dlfcn.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <stdint.h>

typedef jint (*create_vm_fn)(JavaVM **, void **, void *);
typedef struct { char *home, *work, *cache, *classpath, *log; char **args; int argc; int result; } launch_t;

static void *load_library(launch_t *job, const char *relative) {
    char path[4096];
    snprintf(path, sizeof(path), "%s/lib/aarch64/%s", job->home, relative);
    void *handle = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
    if (!handle) fprintf(stderr, "dlopen %s: %s\n", path, dlerror());
    return handle;
}

static void *run_vm(void *pointer) {
    launch_t *job = pointer;
    int fd = open(job->log, O_CREAT | O_WRONLY | O_APPEND, 0600);
    if (fd >= 0) { dup2(fd, STDOUT_FILENO); dup2(fd, STDERR_FILENO); close(fd); }
    setvbuf(stdout, NULL, _IONBF, 0);
    setvbuf(stderr, NULL, _IONBF, 0);
    fprintf(stdout, "FreeJ2ME Lite native host v6\nJAVA_HOME=%s\n", job->home);
    // HotSpot 8 AArch64 emits 48-bit pointer constants. Android heap pointer
    // tags are incompatible with that code generation, even during VM startup.
    // The application manifest opts out before the runtime process is created.
    void *probe = malloc(32);
    if (!probe) { fprintf(stderr, "Native heap probe allocation failed\n"); job->result = -21; return NULL; }
    unsigned tag = (unsigned)((uintptr_t)probe >> 56);
    fprintf(stdout, "Native heap pointer tag byte=0x%02x; page size=%ld\n", tag, sysconf(_SC_PAGESIZE));
    free(probe);
    if (tag != 0) {
        fprintf(stderr, "Tagged native heap is still enabled; refusing to start legacy HotSpot 8.\n");
        job->result = -21;
        return NULL;
    }
    setenv("JAVA_HOME", job->home, 1);
    setenv("HOME", job->work, 1);
    setenv("TMPDIR", job->cache, 1);
    char ldpath[8192];
    snprintf(ldpath, sizeof(ldpath), "%s/lib/aarch64:%s/lib/aarch64/server",
             job->home, job->home);
    setenv("LD_LIBRARY_PATH", ldpath, 1);
    chdir(job->work);
    void *jvm = load_library(job, "server/libjvm.so");
    if (!jvm) { job->result = -10; return NULL; }
    const char *libs[] = {"libverify.so", "libjava.so", "libnet.so", "libnio.so",
        "libawt.so", "libawt_headless.so", "libfreetype.so", "libfontmanager.so"};
    for (unsigned i = 0; i < sizeof(libs)/sizeof(libs[0]); i++)
        if (!load_library(job, libs[i])) { job->result = -11; return NULL; }
    create_vm_fn create_vm = (create_vm_fn)dlsym(jvm, "JNI_CreateJavaVM");
    if (!create_vm) { fprintf(stderr, "JNI_CreateJavaVM not found\n"); job->result = -12; return NULL; }
    char home[4096], cp[8192], work[4096], cache[4096], fonts[4096], fontconfig[4096], libpath[8192], errors[4096];
    snprintf(home, sizeof(home), "-Djava.home=%s", job->home);
    snprintf(cp, sizeof(cp), "-Djava.class.path=%s", job->classpath);
    snprintf(work, sizeof(work), "-Duser.home=%s", job->work);
    snprintf(cache, sizeof(cache), "-Djava.io.tmpdir=%s", job->cache);
    snprintf(fonts, sizeof(fonts), "-Dsun.java2d.fontpath=%s/lib/fonts", job->home);
    snprintf(fontconfig, sizeof(fontconfig), "-Dsun.awt.fontconfig=%s/lib/fontconfig.properties", job->home);
    snprintf(libpath, sizeof(libpath), "-Djava.library.path=%s", ldpath);
    if (strcmp(job->log, "/dev/null") == 0)
        snprintf(errors, sizeof(errors), "-XX:ErrorFile=/dev/null");
    else
        snprintf(errors, sizeof(errors), "-XX:ErrorFile=%s/hs_err_pid%%p.log", job->work);
    char *values[] = {home, cp, work, cache, fonts, fontconfig, libpath, errors,
        "-Djava.awt.headless=true", "-Dfile.encoding=ISO-8859-1", "-Xms16m", "-Xmx192m", "-XX:+UseSerialGC"};
    JavaVMOption options[sizeof(values)/sizeof(values[0])];
    memset(options, 0, sizeof(options));
    for (unsigned i = 0; i < sizeof(values)/sizeof(values[0]); i++) options[i].optionString = values[i];
    // Android's NDK declares JNI 1.6; OpenJDK 8 accepts that interface version.
    JavaVMInitArgs init = {JNI_VERSION_1_6, sizeof(options)/sizeof(options[0]), options, JNI_FALSE};
    JavaVM *vm = NULL;
    JNIEnv *env = NULL;
    jint result = create_vm(&vm, (void **)&env, &init);
    fprintf(stdout, "JNI_CreateJavaVM result=%d\n", result);
    if (result != JNI_OK) { job->result = result; return NULL; }
    jclass entry = (*env)->FindClass(env, "org/recompile/android/HeadlessMain");
    jclass string_class = (*env)->FindClass(env, "java/lang/String");
    if (!entry || !string_class) { (*env)->ExceptionDescribe(env); job->result = -13; return NULL; }
    jmethodID main_method = (*env)->GetStaticMethodID(env, entry, "main", "([Ljava/lang/String;)V");
    if (!main_method) { (*env)->ExceptionDescribe(env); job->result = -14; return NULL; }
    jobjectArray args = (*env)->NewObjectArray(env, job->argc, string_class, NULL);
    for (int i = 0; i < job->argc; i++) {
        jstring value = (*env)->NewStringUTF(env, job->args[i]);
        (*env)->SetObjectArrayElement(env, args, i, value);
        (*env)->DeleteLocalRef(env, value);
    }
    (*env)->CallStaticVoidMethod(env, entry, main_method, args);
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionDescribe(env); job->result = -15; }
    else job->result = 0;
    return NULL;
}

static char *copy_string(JNIEnv *env, jstring string) {
    const char *value = (*env)->GetStringUTFChars(env, string, NULL);
    char *copy = strdup(value);
    (*env)->ReleaseStringUTFChars(env, string, value);
    return copy;
}

JNIEXPORT jint JNICALL Java_org_recompile_freej2me_lite_NativeHost_launch
  (JNIEnv *env, jclass clazz, jstring home, jstring work, jstring cache, jstring cp, jstring log, jobjectArray args) {
    launch_t job = {0};
    job.home = copy_string(env, home); job.work = copy_string(env, work);
    job.cache = copy_string(env, cache); job.classpath = copy_string(env, cp); job.log = copy_string(env, log);
    job.argc = (*env)->GetArrayLength(env, args);
    job.args = calloc(job.argc, sizeof(char *));
    for (int i = 0; i < job.argc; i++) {
        jstring value = (*env)->GetObjectArrayElement(env, args, i);
        job.args[i] = copy_string(env, value); (*env)->DeleteLocalRef(env, value);
    }
    pthread_t thread;
    int error = pthread_create(&thread, NULL, run_vm, &job);
    if (error) job.result = -20; else pthread_join(thread, NULL);
    for (int i = 0; i < job.argc; i++) free(job.args[i]);
    free(job.args); free(job.home); free(job.work); free(job.cache); free(job.classpath); free(job.log);
    return job.result;
}
