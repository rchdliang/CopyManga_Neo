#include "engine.h"
#include <jni.h>
#include <map>
#include <mutex>
#include <stdexcept>
#include <vector>
namespace {
struct Registry {
    std::mutex gate;
    std::map<jlong,std::shared_ptr<restore::Engine>> engines;
    jlong nextId=1;
};
// 避免在 Windows DLL 卸载锁中析构 Vulkan 设备；正常资源由 destroy/OnUnload 释放。
Registry& registry() { static auto* value=new Registry; return *value; }
std::shared_ptr<restore::Engine> engine(jlong id) {
    auto& state=registry();
    std::lock_guard<std::mutex> lock(state.gate);
    auto it=state.engines.find(id);if(it==state.engines.end())throw std::runtime_error("Restore engine is closed");
    return it->second;
}
void fail(JNIEnv* env,const std::exception& error) {
    auto type=env->FindClass("java/lang/IllegalStateException");
    if(type)env->ThrowNew(type,error.what());
}
}
#define METHOD(name) Java_com_rchdliang_copymanga_1neo_multiplatform_restore_VulkanRestoreBridge_##name
extern "C" {
JNIEXPORT jlong JNICALL METHOD(create)(JNIEnv* env,jclass,jboolean software) {
    try {auto e=std::make_shared<restore::Engine>(software==JNI_TRUE);auto& state=registry();std::lock_guard<std::mutex> lock(state.gate);auto id=state.nextId++;state.engines.emplace(id,std::move(e));return id;}
    catch(const std::exception& error){fail(env,error);return 0;}
}
JNIEXPORT void JNICALL METHOD(loadModel)(JNIEnv* env,jclass,jlong id,jint key,jbyteArray data) {
    try {
        if(!data)throw std::runtime_error("Missing model");
        auto length=env->GetArrayLength(data);if(length<=0||length>16*1024*1024)throw std::runtime_error("Invalid model length");
        std::vector<uint8_t> bytes(size_t(length),uint8_t(0));
        env->GetByteArrayRegion(data,0,length,reinterpret_cast<jbyte*>(bytes.data()));
        if(env->ExceptionCheck())return;
        engine(id)->load(key,bytes.data(),bytes.size());
    } catch(const std::exception& error){fail(env,error);}
}
JNIEXPORT void JNICALL METHOD(renderTile)(JNIEnv* env,jclass,jlong id,jint key,jobject source,jint w,jint h,jobject destination,jint x,jint y) {
    try {
        auto pixels=int64_t(w)*h;
        if(w<=0||h<=0||pixels<=0||pixels>4194304)throw std::runtime_error("Invalid image size");
        auto length=pixels*4;
        if(!source||!destination||
            env->GetDirectBufferCapacity(source)<length||env->GetDirectBufferCapacity(destination)<length)
            throw std::runtime_error("Invalid direct pixel buffers");
        auto input=static_cast<const uint8_t*>(env->GetDirectBufferAddress(source));
        auto output=static_cast<uint8_t*>(env->GetDirectBufferAddress(destination));
        engine(id)->tile(key,input,w,h,output,x,y);
    } catch(const std::exception& error){fail(env,error);}
}
JNIEXPORT jstring JNICALL METHOD(deviceName)(JNIEnv* env,jclass,jlong id) {
    try{return env->NewStringUTF(engine(id)->deviceName().c_str());}
    catch(const std::exception& error){fail(env,error);return nullptr;}
}
JNIEXPORT void JNICALL METHOD(destroy)(JNIEnv*,jclass,jlong id) {
    std::shared_ptr<restore::Engine> old;
    auto& state=registry();
    {std::lock_guard<std::mutex> lock(state.gate);auto it=state.engines.find(id);if(it!=state.engines.end()){old=std::move(it->second);state.engines.erase(it);}}
}
JNIEXPORT void JNICALL JNI_OnUnload(JavaVM*,void*) {
    std::map<jlong,std::shared_ptr<restore::Engine>> old;
    auto& state=registry();
    {std::lock_guard<std::mutex> lock(state.gate);old.swap(state.engines);}
}
}
