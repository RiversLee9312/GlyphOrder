// Copyright 2026 GlyphOrder contributors. SPDX-License-Identifier: Apache-2.0
#include <sys/types.h>
#include "zygisk.hpp"
#include <android/log.h>
#include <dirent.h>
#include <sys/stat.h>
#include <unistd.h>
#include <string>

namespace {
constexpr const char* TAG = "GlyphOrderPreload";

class FontPreloader final : public zygisk::ModuleBase {
    zygisk::Api* api_ = nullptr;
    JNIEnv* env_ = nullptr;

    int preloadDirectory(const char* directory, jclass typeface, jmethodID warmup) {
        DIR* dir = opendir(directory);
        if (!dir) return 0;
        int count = 0;
        while (const auto* entry = readdir(dir)) {
            const std::string name(entry->d_name);
            if (name.compare(0, 11, "GlyphOrder-") != 0) continue;
            const std::string path = std::string(directory) + "/" + name;
            struct stat info{};
            if (stat(path.c_str(), &info) != 0 || !S_ISREG(info.st_mode) || access(path.c_str(), R_OK) != 0) continue;
            jstring filename = env_->NewStringUTF(path.c_str());
            if (!filename) {
                env_->ExceptionClear();
                break;
            }
            // Populate HWUI's process-wide font-data cache, the same cache Minikin's
            // lazy font factory consults after specialization has removed mounts.
            env_->CallStaticVoidMethod(typeface, warmup, filename);
            env_->DeleteLocalRef(filename);
            if (env_->ExceptionCheck()) {
                env_->ExceptionClear();
                __android_log_print(ANDROID_LOG_WARN, TAG, "Cannot preload %s", path.c_str());
            } else {
                ++count;
            }
        }
        closedir(dir);
        return count;
    }

public:
    void onLoad(zygisk::Api* api, JNIEnv* env) override { api_ = api; env_ = env; }

    void preAppSpecialize(zygisk::AppSpecializeArgs*) override {
        // No callbacks or JNI hooks remain after this call. The framework owns
        // the cached font bytes, so this library can be unloaded immediately.
        api_->setOption(zygisk::Option::DLCLOSE_MODULE_LIBRARY);
        jclass typeface = env_->FindClass("android/graphics/Typeface");
        if (!typeface) { env_->ExceptionClear(); return; }
        jmethodID warmup = env_->GetStaticMethodID(typeface, "nativeWarmUpCache", "(Ljava/lang/String;)V");
        if (!warmup) {
            env_->ExceptionClear();
            env_->DeleteLocalRef(typeface);
            __android_log_print(ANDROID_LOG_ERROR, TAG, "ROM has no Typeface.nativeWarmUpCache");
            return;
        }
        const int count = preloadDirectory("/system/fonts", typeface, warmup)
                        + preloadDirectory("/product/fonts", typeface, warmup);
        env_->DeleteLocalRef(typeface);
        __android_log_print(ANDROID_LOG_DEBUG, TAG, "Preloaded %d font paths before specialization", count);
    }

    void preServerSpecialize(zygisk::ServerSpecializeArgs*) override {
        api_->setOption(zygisk::Option::DLCLOSE_MODULE_LIBRARY);
    }
};
}

REGISTER_ZYGISK_MODULE(FontPreloader)
