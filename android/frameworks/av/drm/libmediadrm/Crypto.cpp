/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//#define LOG_NDEBUG 0
#define LOG_TAG "Crypto"
#include <utils/Log.h>
#include <dirent.h>
#include <dlfcn.h>

#include <binder/IMemory.h>
#include <media/Crypto.h>
#include <media/hardware/CryptoAPI.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AString.h>
#include <media/stagefright/foundation/hexdump.h>
#include <media/stagefright/MediaErrors.h>
// For Realtek DRM Plugin
#define PLUGIN_NUMBER_NUMBER    0x2379
#define PLUGIN_INIT_FAILED      0x2380
#define PLUGIN_NOT_EXIST        0x2381
#define PLUGIN_WRONG_NUMBER     0x2382

namespace android {

KeyedVector<Vector<uint8_t>, String8> Crypto::mUUIDToLibraryPathMap;
KeyedVector<String8, wp<SharedLibrary> > Crypto::mLibraryPathToOpenLibraryMap;
Mutex Crypto::mMapLock;

static bool operator<(const Vector<uint8_t> &lhs, const Vector<uint8_t> &rhs) {
    if (lhs.size() < rhs.size()) {
        return true;
    } else if (lhs.size() > rhs.size()) {
        return false;
    }

    return memcmp((void *)lhs.array(), (void *)rhs.array(), rhs.size()) < 0;
}

Crypto::Crypto()
    : mInitCheck(NO_INIT),
      mFactory(NULL),
      mPlugin(NULL) {
}

Crypto::~Crypto() {
    delete mPlugin;
    mPlugin = NULL;
    closeFactory();
}

void Crypto::closeFactory() {
    delete mFactory;
    mFactory = NULL;
    mLibrary.clear();
}

status_t Crypto::initCheck() const {
    return mInitCheck;
}

/*
 * Search the plugins directory for a plugin that supports the scheme
 * specified by uuid
 *
 * If found:
 *    mLibrary holds a strong pointer to the dlopen'd library
 *    mFactory is set to the library's factory method
 *    mInitCheck is set to OK
 *
 * If not found:
 *    mLibrary is cleared and mFactory are set to NULL
 *    mInitCheck is set to an error (!OK)
 */
void Crypto::findFactoryForScheme(const uint8_t uuid[16]) {

    closeFactory();

    // lock static maps
    Mutex::Autolock autoLock(mMapLock);

    // first check cache
    Vector<uint8_t> uuidVector;
    uuidVector.appendArray(uuid, sizeof(uuid[0]) * 16);
    ssize_t index = mUUIDToLibraryPathMap.indexOfKey(uuidVector);
    if (index >= 0) {
        if (loadLibraryForScheme(mUUIDToLibraryPathMap[index], uuid)) {
            mInitCheck = OK;
            return;
        } else {
            ALOGE("Failed to load from cached library path!");
            mInitCheck = ERROR_UNSUPPORTED;
            return;
        }
    }

    // no luck, have to search
    String8 dirPath("/vendor/lib/mediadrm");
    String8 pluginPath;

    DIR* pDir = opendir(dirPath.string());
    if (pDir) {
        struct dirent* pEntry;
        while ((pEntry = readdir(pDir))) {

            pluginPath = dirPath + "/" + pEntry->d_name;

            if (pluginPath.getPathExtension() == ".so") {
                ALOGD("Crypto pluginPath: %s", pluginPath.string());
                if (loadLibraryForScheme(pluginPath, uuid)) {
                    mUUIDToLibraryPathMap.add(uuidVector, pluginPath);
                    mInitCheck = OK;
                    closedir(pDir);
                    return;
                }
            }
        }

        closedir(pDir);
    }


    //to load plugin at /data/lib
    dirPath = "/data/lib";

    pDir = opendir(dirPath.string());
    if (pDir) {
        struct dirent* pEntry;
        while ((pEntry = readdir(pDir))) {

            pluginPath = dirPath + "/" + pEntry->d_name;

            if (pluginPath.getPathExtension() == ".so") {
                ALOGD("Crypto pluginPath: %s", pluginPath.string());
                if((strcmp(pEntry->d_name,"libwvdrmengine.so") == 0) || (strcmp(pEntry->d_name,"libPlayReadyDrmCryptoPlugin.so") == 0)) {
                    if (loadLibraryForScheme(pluginPath, uuid)) {
                        mUUIDToLibraryPathMap.add(uuidVector, pluginPath);
                        mInitCheck = OK;
                        closedir(pDir);
                        return;
                    }
                }
            }
        }

        closedir(pDir);
    }


    // try the legacy libdrmdecrypt.so
    pluginPath = "libdrmdecrypt.so";
    if (loadLibraryForScheme(pluginPath, uuid)) {
        mUUIDToLibraryPathMap.add(uuidVector, pluginPath);
        mInitCheck = OK;
        return;
    }

    mInitCheck = ERROR_UNSUPPORTED;
}

bool Crypto::loadLibraryForScheme(const String8 &path, const uint8_t uuid[16]) {

    // get strong pointer to open shared library
    ssize_t index = mLibraryPathToOpenLibraryMap.indexOfKey(path);
    if (index >= 0) {
        mLibrary = mLibraryPathToOpenLibraryMap[index].promote();
    } else {
        index = mLibraryPathToOpenLibraryMap.add(path, NULL);
    }

    if (!mLibrary.get()) {
        mLibrary = new SharedLibrary(path);
        if (!*mLibrary) {
            ALOGE("loadLibraryForScheme failed:%s", mLibrary->lastError());
            return false;
        }

        mLibraryPathToOpenLibraryMap.replaceValueAt(index, mLibrary);
    }

    typedef CryptoFactory *(*CreateCryptoFactoryFunc)();

    CreateCryptoFactoryFunc createCryptoFactory =
        (CreateCryptoFactoryFunc)mLibrary->lookup("createCryptoFactory");

    if (createCryptoFactory == NULL ||
        (mFactory = createCryptoFactory()) == NULL ||
        !mFactory->isCryptoSchemeSupported(uuid)) {   
        closeFactory();
        return false;
    }
    return true;
}

bool Crypto::isCryptoSchemeSupported(const uint8_t uuid[16]) {
    Mutex::Autolock autoLock(mLock);

    if (mFactory && mFactory->isCryptoSchemeSupported(uuid)) {
        return true;
    }

    findFactoryForScheme(uuid);
    return (mInitCheck == OK);
}

status_t Crypto::createPlugin(
        const uint8_t uuid[16], const void *data, size_t size) {
    Mutex::Autolock autoLock(mLock);

    if (mPlugin != NULL) {
        return -EINVAL;
    }

    if (!mFactory || !mFactory->isCryptoSchemeSupported(uuid)) {
        findFactoryForScheme(uuid);
    }

    if (mInitCheck != OK) {
        return mInitCheck;
    }

    return mFactory->createPlugin(uuid, data, size, &mPlugin);
}

status_t Crypto::destroyPlugin() {
    Mutex::Autolock autoLock(mLock);

    if (mInitCheck != OK) {
        return mInitCheck;
    }

    if (mPlugin == NULL) {
        return -EINVAL;
    }

    delete mPlugin;
    mPlugin = NULL;

    return OK;
}

bool Crypto::requiresSecureDecoderComponent(const char *mime) const {
    Mutex::Autolock autoLock(mLock);

    if (mInitCheck != OK) {
        return mInitCheck;
    }

    if (mPlugin == NULL) {
        return -EINVAL;
    }

    return mPlugin->requiresSecureDecoderComponent(mime);
}

ssize_t Crypto::decrypt(
        DestinationType dstType,
        const uint8_t key[16],
        const uint8_t iv[16],
        CryptoPlugin::Mode mode,
        const CryptoPlugin::Pattern &pattern,
        const sp<IMemory> &sharedBuffer, size_t offset,
        const CryptoPlugin::SubSample *subSamples, size_t numSubSamples,
        void *dstPtr,
        AString *errorDetailMsg) {
    Mutex::Autolock autoLock(mLock);

    if (mInitCheck != OK) {
        return mInitCheck;
    }

    if (mPlugin == NULL) {
        return -EINVAL;
    }

    const void *srcPtr = static_cast<uint8_t *>(sharedBuffer->pointer()) + offset;

    return mPlugin->decrypt(
            dstType != kDestinationTypeVmPointer,
            key, iv, mode, pattern, srcPtr, subSamples, numSubSamples, dstPtr,
            errorDetailMsg);
}

void Crypto::notifyResolution(uint32_t width, uint32_t height) {
    Mutex::Autolock autoLock(mLock);

    if (mInitCheck == OK && mPlugin != NULL) {
        mPlugin->notifyResolution(width, height);
    }
}

status_t Crypto::setMediaDrmSession(const Vector<uint8_t> &sessionId) {
    Mutex::Autolock autoLock(mLock);

    status_t result = NO_INIT;
    if (mInitCheck == OK && mPlugin != NULL) {
        result = mPlugin->setMediaDrmSession(sessionId);
    }
    return result;
}

}  // namespace android
