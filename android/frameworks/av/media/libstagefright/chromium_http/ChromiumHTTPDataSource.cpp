/*
 * Copyright (C) 2011 The Android Open Source Project
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
#define LOG_TAG "ChromiumHTTPDataSource"
#include <media/stagefright/foundation/ADebug.h>

#include "include/ChromiumHTTPDataSource.h"

#include <media/stagefright/foundation/ALooper.h>
#include <media/stagefright/MediaErrors.h>

#include "support.h"

#include <cutils/properties.h> // for property_get

namespace android {

ChromiumHTTPDataSource::ChromiumHTTPDataSource(uint32_t flags)
    : mFlags(flags),
      mState(DISCONNECTED),
      mDelegate(new SfDelegate),
      mCurrentOffset(0),
      mIOResult(OK),
      mContentSize(-1),
      mDecryptHandle(NULL),
      mDrmManagerClient(NULL) {
    mDelegate->setOwner(this);
}

ChromiumHTTPDataSource::~ChromiumHTTPDataSource() {
    disconnect();

    delete mDelegate;
    mDelegate = NULL;

    clearDRMState_l();

    if (mDrmManagerClient != NULL) {
        delete mDrmManagerClient;
        mDrmManagerClient = NULL;
    }
}

status_t ChromiumHTTPDataSource::connect(
        const char *uri,
        const KeyedVector<String8, String8> *headers,
        off64_t offset) {
    Mutex::Autolock autoLock(mLock);

    uid_t uid;
    if (getUID(&uid)) {
        mDelegate->setUID(uid);
    }

#if defined(LOG_NDEBUG) && !LOG_NDEBUG
    LOG_PRI(ANDROID_LOG_VERBOSE, LOG_TAG, "connect on behalf of uid %d", uid);
#endif
    //ALOGD("\033[0;31;31m [%s - %d] uri:'%s'  \033[m\n", __FILE__, __LINE__, uri);

    return connect_l(uri, headers, offset);
}

status_t ChromiumHTTPDataSource::connect_l(
        const char *uri,
        const KeyedVector<String8, String8> *headers,
        off64_t offset) {
    if (mState != DISCONNECTED) {
        disconnect_l();
    }

#if defined(LOG_NDEBUG) && !LOG_NDEBUG
    LOG_PRI(ANDROID_LOG_VERBOSE, LOG_TAG,
                "connect to <URL suppressed> @%lld", offset);
#endif

    mURI = uri;
    mContentType = String8("application/octet-stream");

    if (headers != NULL) {
        mHeaders = *headers;
    } else {
        mHeaders.clear();
    }

    mState = CONNECTING;
    mContentSize = -1;
    mCurrentOffset = offset;

    mDelegate->initiateConnection(mURI.c_str(), &mHeaders, offset);

    while (mState == CONNECTING || mState == DISCONNECTING) {
        mCondition.wait(mLock);
    }
    if (mState == CONNECTED)
        mRedirecturl = mDelegate->getRedirectURL();

    return mState == CONNECTED ? OK : mIOResult;
}

void ChromiumHTTPDataSource::onRedirect(const char *url) {
    Mutex::Autolock autoLock(mLock);
    mURI = url;
}

void ChromiumHTTPDataSource::onConnectionEstablished(
        int64_t contentSize, const char *contentType) {
    Mutex::Autolock autoLock(mLock);

    if (mState != CONNECTING) {
        // We may have initiated disconnection.
        CHECK_EQ(mState, DISCONNECTING);
        return;
    }

    mState = CONNECTED;
    mContentSize = (contentSize < 0) ? -1 : contentSize + mCurrentOffset;
    mContentType = String8(contentType);
    mCondition.broadcast();
}

void ChromiumHTTPDataSource::onConnectionFailed(status_t err) {
    Mutex::Autolock autoLock(mLock);
    mState = DISCONNECTED;
    mCondition.broadcast();

    // mURI.clear();

    mIOResult = err;
}

void ChromiumHTTPDataSource::disconnect() {
    Mutex::Autolock autoLock(mLock);
    disconnect_l();
}

void ChromiumHTTPDataSource::disconnect_l() {
    if (mState == DISCONNECTED) {
        return;
    }

    mState = DISCONNECTING;
    mIOResult = -EINTR;

    mDelegate->initiateDisconnect();

    while (mState == DISCONNECTING) {
        mCondition.wait(mLock);
    }

    CHECK_EQ((int)mState, (int)DISCONNECTED);
}

status_t ChromiumHTTPDataSource::initCheck() const {
    Mutex::Autolock autoLock(mLock);

    return mState == CONNECTED ? OK : NO_INIT;
}

ssize_t ChromiumHTTPDataSource::readAt(off64_t offset, void *data, size_t size) {
    Mutex::Autolock autoLock(mLock);

    if (mState != CONNECTED) {
        return INVALID_OPERATION;
    }

#if 0
    char value[PROPERTY_VALUE_MAX];
    if (property_get("media.stagefright.disable-net", value, 0)
            && (!strcasecmp(value, "true") || !strcmp(value, "1"))) {
        LOG_PRI(ANDROID_LOG_INFO, LOG_TAG, "Simulating that the network is down.");
        disconnect_l();
        return ERROR_IO;
    }
#endif

    if (offset != mCurrentOffset) {
        AString tmp = mURI;
        KeyedVector<String8, String8> tmpHeaders = mHeaders;

        disconnect_l();

        status_t err = connect_l(tmp.c_str(), &tmpHeaders, offset);

        if (err != OK) {
            return err;
        }
    }

    mState = READING;

    int64_t startTimeUs = ALooper::GetNowUs();

    mDelegate->initiateRead(data, size);

    while (mState == READING) {
        mCondition.wait(mLock);
    }

    if (mIOResult < OK) {
        return mIOResult;
    }

    if (mState == CONNECTED) {
        int64_t delayUs = ALooper::GetNowUs() - startTimeUs;

        // The read operation was successful, mIOResult contains
        // the number of bytes read.
        addBandwidthMeasurement(mIOResult, delayUs);

        mCurrentOffset += mIOResult;
        return mIOResult;
    }

    return ERROR_IO;
}

void ChromiumHTTPDataSource::onReadCompleted(ssize_t size) {
    Mutex::Autolock autoLock(mLock);

    mIOResult = size;

    if (mState == READING) {
        mState = CONNECTED;
        mCondition.broadcast();
    }
}

status_t ChromiumHTTPDataSource::getSize(off64_t *size) {
    Mutex::Autolock autoLock(mLock);

    if (mContentSize < 0) {
        return ERROR_UNSUPPORTED;
    }

    *size = mContentSize;

    return OK;
}

uint32_t ChromiumHTTPDataSource::flags() {
    return kWantsPrefetching | kIsHTTPBasedSource;
}

// static
void ChromiumHTTPDataSource::InitiateRead(
        ChromiumHTTPDataSource *me, void *data, size_t size) {
    me->initiateRead(data, size);
}

void ChromiumHTTPDataSource::initiateRead(void *data, size_t size) {
    mDelegate->initiateRead(data, size);
}

void ChromiumHTTPDataSource::onDisconnectComplete() {
    Mutex::Autolock autoLock(mLock);
    CHECK_EQ((int)mState, (int)DISCONNECTING);

    mState = DISCONNECTED;
    // mURI.clear();
    mIOResult = -ENOTCONN;

    mCondition.broadcast();
}

sp<DecryptHandle> ChromiumHTTPDataSource::DrmInitialization(const char* mime) {
    Mutex::Autolock autoLock(mLock);

    if (mDrmManagerClient == NULL) {
        mDrmManagerClient = new DrmManagerClient();
    }

    if (mDrmManagerClient == NULL) {
        return NULL;
    }

    if (mDecryptHandle == NULL) {
        /* Note if redirect occurs, mUri is the redirect uri instead of the
         * original one
         */
        mDecryptHandle = mDrmManagerClient->openDecryptSession(
                String8(mURI.c_str()), mime);
    }

    if (mDecryptHandle == NULL) {
        delete mDrmManagerClient;
        mDrmManagerClient = NULL;
    }

    return mDecryptHandle;
}

void ChromiumHTTPDataSource::getDrmInfo(
        sp<DecryptHandle> &handle, DrmManagerClient **client) {
    Mutex::Autolock autoLock(mLock);

    handle = mDecryptHandle;
    *client = mDrmManagerClient;
}

String8 ChromiumHTTPDataSource::getUri() {
    Mutex::Autolock autoLock(mLock);

    return String8(mURI.c_str());
}

String8 ChromiumHTTPDataSource::getMIMEType() const {
    Mutex::Autolock autoLock(mLock);

    return mContentType;
}

AString ChromiumHTTPDataSource::getRedirectUri() {
    return mRedirecturl;
}

void ChromiumHTTPDataSource::clearDRMState_l() {
    if (mDecryptHandle != NULL) {
        // To release mDecryptHandle
        CHECK(mDrmManagerClient);
        mDrmManagerClient->closeDecryptSession(mDecryptHandle);
        mDecryptHandle = NULL;
    }
}

status_t ChromiumHTTPDataSource::reconnectAtOffset(off64_t offset) {
    Mutex::Autolock autoLock(mLock);

    if (mURI.empty()) {
        return INVALID_OPERATION;
    }

    LOG_PRI(ANDROID_LOG_INFO, LOG_TAG, "Reconnecting...");
    status_t err = connect_l(mURI.c_str(), &mHeaders, offset);
    if (err != OK) {
        LOG_PRI(ANDROID_LOG_INFO, LOG_TAG, "Reconnect failed w/ err 0x%08x", err);
    }

    return err;
}

// static
status_t ChromiumHTTPDataSource::UpdateProxyConfig(
        const char *host, int32_t port, const char *exclusionList) {
    return SfDelegate::UpdateProxyConfig(host, port, exclusionList);
}

}  // namespace android

