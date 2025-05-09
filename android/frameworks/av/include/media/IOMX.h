/*
 * Copyright (C) 2009 The Android Open Source Project
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

#ifndef ANDROID_IOMX_H_

#define ANDROID_IOMX_H_

#include <binder/IInterface.h>
#include <gui/IGraphicBufferProducer.h>
#include <gui/IGraphicBufferConsumer.h>
#include <ui/GraphicBuffer.h>
#include <utils/List.h>
#include <utils/String8.h>

#include <list>

#include <media/hardware/MetadataBufferType.h>

#include <OMX_Core.h>
#include <OMX_Video.h>

namespace android {

class IMemory;
class IOMXObserver;
class IOMXRenderer;
class NativeHandle;
class Surface;

class IOMX : public IInterface {
public:
    DECLARE_META_INTERFACE(OMX);

    typedef uint32_t buffer_id;
    typedef uint32_t node_id;

    // Given a node_id and the calling process' pid, returns true iff
    // the implementation of the OMX interface lives in the same
    // process.
    virtual bool livesLocally(node_id node, pid_t pid) = 0;

    struct ComponentInfo {
        String8 mName;
        List<String8> mRoles;
    };
    virtual status_t listNodes(List<ComponentInfo> *list) = 0;

    virtual status_t allocateNode(
            const char *name, const sp<IOMXObserver> &observer,
            sp<IBinder> *nodeBinder,
            node_id *node) = 0;

    virtual status_t freeNode(node_id node) = 0;

    virtual status_t sendCommand(
            node_id node, OMX_COMMANDTYPE cmd, OMX_S32 param) = 0;

    virtual status_t getParameter(
            node_id node, OMX_INDEXTYPE index,
            void *params, size_t size) = 0;

    virtual status_t setParameter(
            node_id node, OMX_INDEXTYPE index,
            const void *params, size_t size) = 0;

    virtual status_t getConfig(
            node_id node, OMX_INDEXTYPE index,
            void *params, size_t size) = 0;

    virtual status_t setConfig(
            node_id node, OMX_INDEXTYPE index,
            const void *params, size_t size) = 0;

    virtual status_t getState(
            node_id node, OMX_STATETYPE* state) = 0;

    // This will set *type to previous metadata buffer type on OMX error (not on binder error), and
    // new metadata buffer type on success.
    virtual status_t storeMetaDataInBuffers(
            node_id node, OMX_U32 port_index, OMX_BOOL enable, MetadataBufferType *type = NULL) = 0;

    virtual status_t prepareForAdaptivePlayback(
            node_id node, OMX_U32 portIndex, OMX_BOOL enable,
            OMX_U32 maxFrameWidth, OMX_U32 maxFrameHeight) = 0;

    virtual status_t configureVideoTunnelMode(
            node_id node, OMX_U32 portIndex, OMX_BOOL tunneled,
            OMX_U32 audioHwSync, native_handle_t **sidebandHandle) = 0;

    virtual status_t enableNativeBuffers(
            node_id node, OMX_U32 port_index, OMX_BOOL graphic, OMX_BOOL enable) = 0;

    virtual status_t getGraphicBufferUsage(
            node_id node, OMX_U32 port_index, OMX_U32* usage) = 0;

    // Use |params| as an OMX buffer, but limit the size of the OMX buffer to |allottedSize|.
    virtual status_t useBuffer(
            node_id node, OMX_U32 port_index, const sp<IMemory> &params,
            buffer_id *buffer, OMX_U32 allottedSize) = 0;

    virtual status_t useGraphicBuffer(
            node_id node, OMX_U32 port_index,
            const sp<GraphicBuffer> &graphicBuffer, buffer_id *buffer) = 0;

    virtual status_t updateGraphicBufferInMeta(
            node_id node, OMX_U32 port_index,
            const sp<GraphicBuffer> &graphicBuffer, buffer_id buffer) = 0;

    virtual status_t updateNativeHandleInMeta(
            node_id node, OMX_U32 port_index,
            const sp<NativeHandle> &nativeHandle, buffer_id buffer) = 0;

    // This will set *type to resulting metadata buffer type on OMX error (not on binder error) as
    // well as on success.
    virtual status_t createInputSurface(
            node_id node, OMX_U32 port_index, android_dataspace dataSpace,
            sp<IGraphicBufferProducer> *bufferProducer,
            MetadataBufferType *type = NULL) = 0;

    virtual status_t createPersistentInputSurface(
            sp<IGraphicBufferProducer> *bufferProducer,
            sp<IGraphicBufferConsumer> *bufferConsumer) = 0;

    // This will set *type to resulting metadata buffer type on OMX error (not on binder error) as
    // well as on success.
    virtual status_t setInputSurface(
            node_id node, OMX_U32 port_index,
            const sp<IGraphicBufferConsumer> &bufferConsumer,
            MetadataBufferType *type) = 0;

    virtual status_t signalEndOfInputStream(node_id node) = 0;

    // Allocate an opaque buffer as a native handle. If component supports returning native
    // handles, those are returned in *native_handle. Otherwise, the allocated buffer is
    // returned in *buffer_data. This clearly only makes sense if the caller lives in the
    // same process as the callee, i.e. is the media_server, as the returned "buffer_data"
    // pointer is just that, a pointer into local address space.
    virtual status_t allocateSecureBuffer(
            node_id node, OMX_U32 port_index, size_t size,
            buffer_id *buffer, void **buffer_data, sp<NativeHandle> *native_handle) = 0;

    // Allocate an OMX buffer of size |allotedSize|. Use |params| as the backup buffer, which
    // may be larger.
    virtual status_t allocateBufferWithBackup(
            node_id node, OMX_U32 port_index, const sp<IMemory> &params,
            buffer_id *buffer, OMX_U32 allottedSize) = 0;

    virtual status_t freeBuffer(
            node_id node, OMX_U32 port_index, buffer_id buffer) = 0;

    enum {
        kFenceTimeoutMs = 1000
    };
    // Calls OMX_FillBuffer on buffer, and passes |fenceFd| to component if it supports
    // fences. Otherwise, it waits on |fenceFd| before calling OMX_FillBuffer.
    // Takes ownership of |fenceFd| even if this call fails.
    virtual status_t fillBuffer(node_id node, buffer_id buffer, int fenceFd = -1) = 0;

    // Calls OMX_EmptyBuffer on buffer (after updating buffer header with |range_offset|,
    // |range_length|, |flags| and |timestamp|). Passes |fenceFd| to component if it
    // supports fences. Otherwise, it waits on |fenceFd| before calling OMX_EmptyBuffer.
    // Takes ownership of |fenceFd| even if this call fails.
    virtual status_t emptyBuffer(
            node_id node,
            buffer_id buffer,
            OMX_U32 range_offset, OMX_U32 range_length,
            OMX_U32 flags, OMX_TICKS timestamp, int fenceFd = -1) = 0;

    virtual status_t getExtensionIndex(
            node_id node,
            const char *parameter_name,
            OMX_INDEXTYPE *index) = 0;

    enum InternalOptionType {
        INTERNAL_OPTION_SUSPEND,  // data is a bool
        INTERNAL_OPTION_REPEAT_PREVIOUS_FRAME_DELAY,  // data is an int64_t
        INTERNAL_OPTION_MAX_TIMESTAMP_GAP, // data is int64_t
        INTERNAL_OPTION_MAX_FPS, // data is float
        INTERNAL_OPTION_START_TIME, // data is an int64_t
        INTERNAL_OPTION_TIME_LAPSE, // data is an int64_t[2]
        INTERNAL_OPTION_COLOR_ASPECTS, // data is ColorAspects
        INTERNAL_OPTION_TIME_OFFSET, // data is an int64_t
    };
    virtual status_t setInternalOption(
            node_id node,
            OMX_U32 port_index,
            InternalOptionType type,
            const void *data,
            size_t size) = 0;
    //[ RTK being ]
    virtual status_t setBSRingBuffer(
            node_id node, OMX_INDEXTYPE index,
            const void *params, size_t size) = 0;

    virtual status_t getIonFd(node_id node, OMX_INDEXTYPE index, void *params, size_t size, OMX_S32 *shareFd) = 0;
    virtual void setReduceOMXMode(node_id node, bool enableReduceOmx) = 0;
    //[ RTK end ]

};

struct omx_message {
    enum {
        EVENT,
        EMPTY_BUFFER_DONE,
        FILL_BUFFER_DONE,
        FRAME_RENDERED,
    } type;

    IOMX::node_id node;
    int fenceFd; // used for EMPTY_BUFFER_DONE and FILL_BUFFER_DONE; client must close this

    union {
        // if type == EVENT
        struct {
            OMX_EVENTTYPE event;
            OMX_U32 data1;
            OMX_U32 data2;
        } event_data;

        // if type == EMPTY_BUFFER_DONE
        struct {
            IOMX::buffer_id buffer;
        } buffer_data;

        // if type == FILL_BUFFER_DONE
        struct {
            IOMX::buffer_id buffer;
            OMX_U32 range_offset;
            OMX_U32 range_length;
            OMX_U32 flags;
            OMX_TICKS timestamp;
        } extended_buffer_data;

        // if type == FRAME_RENDERED
        struct {
            OMX_TICKS timestamp;
            OMX_S64 nanoTime;
        } render_data;
    } u;
};

class IOMXObserver : public IInterface {
public:
    DECLARE_META_INTERFACE(OMXObserver);

    // Handle (list of) messages.
    virtual void onMessages(const std::list<omx_message> &messages) = 0;
};

////////////////////////////////////////////////////////////////////////////////

class BnOMX : public BnInterface<IOMX> {
public:
    virtual status_t onTransact(
            uint32_t code, const Parcel &data, Parcel *reply,
            uint32_t flags = 0);

protected:
    // check if the codec is secure.
    virtual bool isSecure(IOMX::node_id node) {
        return false;
    }
    /* RTK check ReduceOMX Memcpy Mode */
    virtual bool isReduceOMXMode(IOMX::node_id node) {
        return false;
    }
};

class BnOMXObserver : public BnInterface<IOMXObserver> {
public:
    virtual status_t onTransact(
            uint32_t code, const Parcel &data, Parcel *reply,
            uint32_t flags = 0);
};

struct CodecProfileLevel {
    OMX_U32 mProfile;
    OMX_U32 mLevel;
};

inline static const char *asString(MetadataBufferType i, const char *def = "??") {
    using namespace android;
    switch (i) {
        case kMetadataBufferTypeCameraSource:   return "CameraSource";
        case kMetadataBufferTypeGrallocSource:  return "GrallocSource";
        case kMetadataBufferTypeANWBuffer:      return "ANWBuffer";
        case kMetadataBufferTypeNativeHandleSource: return "NativeHandleSource";
        case kMetadataBufferTypeInvalid:        return "Invalid";
        default:                                return def;
    }
}

}  // namespace android

#endif  // ANDROID_IOMX_H_
