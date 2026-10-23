#include "engine.h"
#include <jni.h>
#include <exception>
namespace {
struct Utf {
    JNIEnv* env; jstring value; const char* text;
    Utf(JNIEnv* e, jstring v): env(e), value(v), text(v ? e->GetStringUTFChars(v, nullptr) : nullptr) {}
    ~Utf() { if (text) env->ReleaseStringUTFChars(value, text); }
};
}
extern "C" JNIEXPORT jint JNICALL
Java_com_rchdliang_copymanga_1neo_multiplatform_sr_NativeSr_probe(JNIEnv* e, jobject, jstring models, jboolean gpu) {
    Utf m(e, models); if (!m.text) return -2;
    try { return sr_probe(m.text, gpu); } catch (const std::exception&) { return -2; }
}
extern "C" JNIEXPORT jint JNICALL
Java_com_rchdliang_copymanga_1neo_multiplatform_sr_NativeSr_run(JNIEnv* e, jobject, jstring models, jint gpu, jstring input, jstring output) {
    Utf m(e, models), i(e, input), o(e, output); if (!m.text || !i.text || !o.text) return 12;
    try { return sr_run(m.text, gpu, i.text, o.text); } catch (const std::exception&) { return 12; }
}
