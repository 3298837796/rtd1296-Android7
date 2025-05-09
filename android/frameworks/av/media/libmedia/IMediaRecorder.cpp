/*
 **
 ** Copyright 2008, The Android Open Source Project
 **
 ** Licensed under the Apache License, Version 2.0 (the "License");
 ** you may not use this file except in compliance with the License.
 ** You may obtain a copy of the License at
 **
 **     http://www.apache.org/licenses/LICENSE-2.0
 **
 ** Unless required by applicable law or agreed to in writing, software
 ** distributed under the License is distributed on an "AS IS" BASIS,
 ** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ** See the License for the specific language governing permissions and
 ** limitations under the License.
 */

//#define LOG_NDEBUG 0
#define LOG_TAG "IMediaRecorder"

#include <inttypes.h>
#include <unistd.h>

#include <utils/Log.h>
#include <binder/Parcel.h>
#include <camera/android/hardware/ICamera.h>
#include <camera/ICameraRecordingProxy.h>
#include <media/IMediaRecorderClient.h>
#include <media/IMediaRecorder.h>
#include <gui/Surface.h>
#include <gui/IGraphicBufferProducer.h>

namespace android {

enum {
    RELEASE = IBinder::FIRST_CALL_TRANSACTION,
    INIT,
    CLOSE,
    SET_INPUT_SURFACE,
    QUERY_SURFACE_MEDIASOURCE,
    RESET,
    STOP,
    START,
    PREPARE,
    GET_MAX_AMPLITUDE,
    SET_VIDEO_SOURCE,
    SET_AUDIO_SOURCE,
    SET_OUTPUT_FORMAT,
    SET_VIDEO_ENCODER,
    SET_AUDIO_ENCODER,
    SET_OUTPUT_FILE_FD,
    SET_VIDEO_SIZE,
    SET_VIDEO_FRAMERATE,
    SET_PARAMETERS,
    SET_PREVIEW_SURFACE,
    SET_CAMERA,
    SET_LISTENER,
    SET_CLIENT_NAME,
    PAUSE,
    RESUME,
#ifdef RTK_ENHANCE
    SET_OUTPUT_BUFFER,
#endif
};

class BpMediaRecorder: public BpInterface<IMediaRecorder>
{
public:
    BpMediaRecorder(const sp<IBinder>& impl)
    : BpInterface<IMediaRecorder>(impl)
    {
    }

    status_t setCamera(const sp<hardware::ICamera>& camera, const sp<ICameraRecordingProxy>& proxy)
    {
        ALOGV("setCamera(%p,%p)", camera.get(), proxy.get());
        Parcel data, reply;
        data.writeInterfaceToken(IMediaRecorder::getInterfaceDescriptor());
        data.writeStrongBinder(IInterface::asBinder(camera));
        data.writeStrongBinder(IInterface::asBinder(proxy));
        remote()->transact(SET_CAMERA, data, &reply);
        return reply.readInt32();
    }

    status_t setInputSurface(const sp<IGraphicBufferConsumer>& surface)
    {
        ALOGV("setInputSurface(%p)", surface.get());
        Parcel data, reply;
        data.writeInterfaceToken(IMediaRecorder::getInterfaceDescriptor());
        data.writeStrongBinder(IInterface::asBinder(surface));
        remote()->transact(SET_INPUT_SURFACE, data, &reply);
        return reply.readInt32();
    }

    sp<IGraphicBufferProducer> querySurfaceMediaSource()
    {
        ALOGV("Query SurfaceMediaSource");
        Parcel data, reply;
        data.writeInterfaceToken(IMediaRecorder::getInterfaceDescriptor());
        remote()->transact(QUERY_SURFACE_MEDIASOURCE, data, &reply);
        int returnedNull = reply.readInt32();
        if (returnedNull) {
            return NULL;
        }
        return interface_cast<IGraphicBufferProducer>(reply.readStrongBinder());
    }

    status_t setPreviewSurface(const sp<IGraphicBufferProducer>& surface)
    {
        ALOGV("setPreviewSurface(%p)", surface.get());
        Parcel data, reply;
        data.writeInterfaceToken(IMediaRecorder::getInterfaceDescriptor());
        data.writeStrongBinder(IInterface::asBinder(surface));
        remote()->transact(SET_PREVIEW_SURFACE, data, &reply);
        return reply.readInt32();
    }

    status_t init()
    {
        ALOGV("init");
        Parcel data, reply;
        data.writeInterfaceToken(IMediaRecorder::getInterfaceDescriptor());
        remote()->transact(INIT, data, &reply);
        return reply.readInt32();
    }

    status_t setVideoSource(int vs)
    {
        ALOGV("setVideoSource(%d)", vs);
        Parcel data, reply;
        data.writeInterfaceToken(IMediaRecorder::getInterfaceDescriptor());
        data.writeInt32(vs);
        remote()->transact(SET_VIDEO_SOURCE, data, &reply);
        return reply.readInt32();
    }

    status_t setAudioSource(int as)
    {
        ALOGV("setAudioSource(%d)", as);
        Parcel data, reply;
        data.writeInterfaceToken(IMediaRecorder::getInterfaceDescriptor());
        data.writeInt32(as);
        remote()->transact(SET_AUDIO_SOURCE, data, &reply);
        return reply.readInt32();
    }

    status_t setOutputFormat(int of)
    {
        ALOGV("setOutputFormat(%d)", of);
        Parcel data, reply;
        data.writeInterfaceToken(IMediaRecorder::getInterfaceDescriptor());
        data.writeInt32(of);
        remote()->transact(SET_OUTPUT_FORMAT, data, &reply);
        return reply.readInt32();
    }

    status_t setVideoEncoder(int ve)
    {
        ALOGV("setVideoEncoder(%d)", ve);
        Parcel data, reply;
        data.writeInterfaceToken(IMediaRecorder::getInterfaceDescriptor());
        data.writeInt32(ve);
        remote()->transact(SET_VIDEO_ENCODER, data, &reply);
        return reply.readInt32();
    }

    status_t setAudioEncoder(int ae)
    {
        ALOGV("setAudioEncoder(%d)", ae);
        Parcel data, reply;
        data.writeInterfaceToken(IMediaRecorder::getInterfaceDescriptor());
        data.writeInt32(ae);
        remote()->transact(SET_AUDIO_ENCODER, data, &reply);
        return reply.readInt32();
    }

    status_t setOutputFile(int fd, int64_t offset, int64_t length) {
        ALOGV("setOutputFile(%d, %" PRId64 ", %" PRId64 ")", fd, offset, length);
        Parcel data, reply;
        data.writeInterfaceToken(IMediaRecorder::getInterfaceDescriptor());
        data.writeFileDescriptor(fd);
        data.writeInt64(offset);
        data.writeInt64(length);
        remote()->transact(SET_OUTPUT_FILE_FD, data, &reply);
        return reply.readInt32();
    }

    status_t setVideoSize(int width, int height)
    {
        ALOGV("setVideoSize(%dx%d)", width, height);
        Parcel data, reply;
        data.writeInterfaceToken(IMediaRecorder::getInterfaceDescriptor());
        data.writeInt32(width);
        data.writeInt32(height);
        remote()->transact(SET_VIDEO_SIZE, data, &reply);
        return reply.readInt32();
    }

    status_t setVideoFrameRate(int frames_per_second)
    {
        ALOGV("setVideoFrameRate(%d)", frames_per_second);
        Parcel data, reply;
        data.writeInterfaceToken(IMediaRecorder::getInterfaceDescriptor());
        data.writeInt32(frames_per_second);
        remote()->transact(SET_VIDEO_FRAMERATE, data, &reply);
        return reply.readInt32();
    }

    status_t setParameters(const String8& params)
    {
        ALOGV("setParameter(%s)", params.string());
        Parcel data, reply;
        data.writeInterfaceToken(IMediaRecorder::getInterfaceDescriptor());
        data.writeString8(params);
        remote()->transact(SET_PARAMETERS, data, &reply);
        return reply.readInt32();
    }

    status_t setListener(const sp<IMediaRecorderClient>& listener)
    {
        ALOGV("setListener(%p)", listener.get());
        Parcel data, reply;
        data.writeInterfaceToken(IMediaRecorder::getInterfaceDescriptor());
        data.writeStrongBinder(IInterface::asBinder(listener));
        remote()->transact(SET_LISTENER, data, &reply);
        return reply.readInt32();
    }

    status_t setClientName(const String16& clientName)
    {
        ALOGV("setClientName(%s)", String8(clientName).string());
        Parcel data, reply;
        data.writeInterfaceToken(IMediaRecorder::getInterfaceDescriptor());
        data.writeString16(clientName);
        remote()->transact(SET_CLIENT_NAME, data, &reply);
        return reply.readInt32();
    }

    status_t prepare()
    {
        ALOGV("prepare");
        Parcel data, reply;
        data.writeInterfaceToken(IMediaRecorder::getInterfaceDescriptor());
        remote()->transact(PREPARE, data, &reply);
        return reply.readInt32();
    }

    status_t getMaxAmplitude(int* max)
    {
        ALOGV("getMaxAmplitude");
        Parcel data, reply;
        data.writeInterfaceToken(IMediaRecorder::getInterfaceDescriptor());
        remote()->transact(GET_MAX_AMPLITUDE, data, &reply);
        *max = reply.readInt32();
        return reply.readInt32();
    }

    status_t start()
    {
        ALOGV("start");
        Parcel data, reply;
        data.writeInterfaceToken(IMediaRecorder::getInterfaceDescriptor());
        remote()->transact(START, data, &reply);
        return reply.readInt32();
    }

    status_t stop()
    {
        ALOGV("stop");
        Parcel data, reply;
        data.writeInterfaceToken(IMediaRecorder::getInterfaceDescriptor());
        remote()->transact(STOP, data, &reply);
        return reply.readInt32();
    }

    status_t reset()
    {
        ALOGV("reset");
        Parcel data, reply;
        data.writeInterfaceToken(IMediaRecorder::getInterfaceDescriptor());
        remote()->transact(RESET, data, &reply);
        return reply.readInt32();
    }

    status_t pause()
    {
        ALOGV("pause");
        Parcel data, reply;
        data.writeInterfaceToken(IMediaRecorder::getInterfaceDescriptor());
        remote()->transact(PAUSE, data, &reply);
        return reply.readInt32();
    }

    status_t resume()
    {
        ALOGV("resume");
        Parcel data, reply;
        data.writeInterfaceToken(IMediaRecorder::getInterfaceDescriptor());
        remote()->transact(RESUME, data, &reply);
        return reply.readInt32();
    }

    status_t close()
    {
        ALOGV("close");
        Parcel data, reply;
        data.writeInterfaceToken(IMediaRecorder::getInterfaceDescriptor());
        remote()->transact(CLOSE, data, &reply);
        return reply.readInt32();
    }

    status_t release()
    {
        ALOGV("release");
        Parcel data, reply;
        data.writeInterfaceToken(IMediaRecorder::getInterfaceDescriptor());
        remote()->transact(RELEASE, data, &reply);
        return reply.readInt32();
    }

#ifdef RTK_ENHANCE
    status_t setOutputBuffer(const sp<IMemory> &bufferData, const sp<IMemory> &bufferInfo)
    {
        ALOGV("setOutputBuffer");
        Parcel data, reply;
        data.writeInterfaceToken(IMediaRecorder::getInterfaceDescriptor());
        data.writeStrongBinder(IInterface::asBinder(bufferData));
        data.writeStrongBinder(IInterface::asBinder(bufferInfo));
        remote()->transact(SET_OUTPUT_BUFFER, data, &reply);
        return reply.readInt32();
    }
#endif
};

IMPLEMENT_META_INTERFACE(MediaRecorder, "android.media.IMediaRecorder");

// ----------------------------------------------------------------------

status_t BnMediaRecorder::onTransact(
                                     uint32_t code, const Parcel& data, Parcel* reply,
                                     uint32_t flags)
{
    switch (code) {
        case RELEASE: {
            ALOGV("RELEASE");
            CHECK_INTERFACE(IMediaRecorder, data, reply);
            reply->writeInt32(release());
            return NO_ERROR;
        } break;
        case INIT: {
            ALOGV("INIT");
            CHECK_INTERFACE(IMediaRecorder, data, reply);
            reply->writeInt32(init());
            return NO_ERROR;
        } break;
        case CLOSE: {
            ALOGV("CLOSE");
            CHECK_INTERFACE(IMediaRecorder, data, reply);
            reply->writeInt32(close());
            return NO_ERROR;
        } break;
        case RESET: {
            ALOGV("RESET");
            CHECK_INTERFACE(IMediaRecorder, data, reply);
            reply->writeInt32(reset());
            return NO_ERROR;
        } break;
        case STOP: {
            ALOGV("STOP");
            CHECK_INTERFACE(IMediaRecorder, data, reply);
            reply->writeInt32(stop());
            return NO_ERROR;
        } break;
        case START: {
            ALOGV("START");
            CHECK_INTERFACE(IMediaRecorder, data, reply);
            reply->writeInt32(start());
            return NO_ERROR;
        } break;
        case PAUSE: {
            ALOGV("PAUSE");
            CHECK_INTERFACE(IMediaRecorder, data, reply);
            reply->writeInt32(pause());
            return NO_ERROR;
        } break;
        case RESUME: {
            ALOGV("RESUME");
            CHECK_INTERFACE(IMediaRecorder, data, reply);
            reply->writeInt32(resume());
            return NO_ERROR;
        } break;
        case PREPARE: {
            ALOGV("PREPARE");
            CHECK_INTERFACE(IMediaRecorder, data, reply);
            reply->writeInt32(prepare());
            return NO_ERROR;
        } break;
        case GET_MAX_AMPLITUDE: {
            ALOGV("GET_MAX_AMPLITUDE");
            CHECK_INTERFACE(IMediaRecorder, data, reply);
            int max = 0;
            status_t ret = getMaxAmplitude(&max);
            reply->writeInt32(max);
            reply->writeInt32(ret);
            return NO_ERROR;
        } break;
        case SET_VIDEO_SOURCE: {
            ALOGV("SET_VIDEO_SOURCE");
            CHECK_INTERFACE(IMediaRecorder, data, reply);
            int vs = data.readInt32();
            reply->writeInt32(setVideoSource(vs));
            return NO_ERROR;
        } break;
        case SET_AUDIO_SOURCE: {
            ALOGV("SET_AUDIO_SOURCE");
            CHECK_INTERFACE(IMediaRecorder, data, reply);
            int as = data.readInt32();
            reply->writeInt32(setAudioSource(as));
            return NO_ERROR;
        } break;
        case SET_OUTPUT_FORMAT: {
            ALOGV("SET_OUTPUT_FORMAT");
            CHECK_INTERFACE(IMediaRecorder, data, reply);
            int of = data.readInt32();
            reply->writeInt32(setOutputFormat(of));
            return NO_ERROR;
        } break;
        case SET_VIDEO_ENCODER: {
            ALOGV("SET_VIDEO_ENCODER");
            CHECK_INTERFACE(IMediaRecorder, data, reply);
            int ve = data.readInt32();
            reply->writeInt32(setVideoEncoder(ve));
            return NO_ERROR;
        } break;
        case SET_AUDIO_ENCODER: {
            ALOGV("SET_AUDIO_ENCODER");
            CHECK_INTERFACE(IMediaRecorder, data, reply);
            int ae = data.readInt32();
            reply->writeInt32(setAudioEncoder(ae));
            return NO_ERROR;

        } break;
        case SET_OUTPUT_FILE_FD: {
            ALOGV("SET_OUTPUT_FILE_FD");
            CHECK_INTERFACE(IMediaRecorder, data, reply);
            int fd = dup(data.readFileDescriptor());
            int64_t offset = data.readInt64();
            int64_t length = data.readInt64();
            reply->writeInt32(setOutputFile(fd, offset, length));
            ::close(fd);
            return NO_ERROR;
        } break;
        case SET_VIDEO_SIZE: {
            ALOGV("SET_VIDEO_SIZE");
            CHECK_INTERFACE(IMediaRecorder, data, reply);
            int width = data.readInt32();
            int height = data.readInt32();
            reply->writeInt32(setVideoSize(width, height));
            return NO_ERROR;
        } break;
        case SET_VIDEO_FRAMERATE: {
            ALOGV("SET_VIDEO_FRAMERATE");
            CHECK_INTERFACE(IMediaRecorder, data, reply);
            int frames_per_second = data.readInt32();
            reply->writeInt32(setVideoFrameRate(frames_per_second));
            return NO_ERROR;
        } break;
        case SET_PARAMETERS: {
            ALOGV("SET_PARAMETER");
            CHECK_INTERFACE(IMediaRecorder, data, reply);
            reply->writeInt32(setParameters(data.readString8()));
            return NO_ERROR;
        } break;
        case SET_LISTENER: {
            ALOGV("SET_LISTENER");
            CHECK_INTERFACE(IMediaRecorder, data, reply);
            sp<IMediaRecorderClient> listener =
                interface_cast<IMediaRecorderClient>(data.readStrongBinder());
            reply->writeInt32(setListener(listener));
            return NO_ERROR;
        } break;
        case SET_CLIENT_NAME: {
            ALOGV("SET_CLIENT_NAME");
            CHECK_INTERFACE(IMediaRecorder, data, reply);
            reply->writeInt32(setClientName(data.readString16()));
            return NO_ERROR;
        }
        case SET_PREVIEW_SURFACE: {
            ALOGV("SET_PREVIEW_SURFACE");
            CHECK_INTERFACE(IMediaRecorder, data, reply);
            sp<IGraphicBufferProducer> surface = interface_cast<IGraphicBufferProducer>(
                    data.readStrongBinder());
            reply->writeInt32(setPreviewSurface(surface));
            return NO_ERROR;
        } break;
        case SET_CAMERA: {
            ALOGV("SET_CAMERA");
            CHECK_INTERFACE(IMediaRecorder, data, reply);
            sp<hardware::ICamera> camera =
                    interface_cast<hardware::ICamera>(data.readStrongBinder());
            sp<ICameraRecordingProxy> proxy =
                    interface_cast<ICameraRecordingProxy>(data.readStrongBinder());
            reply->writeInt32(setCamera(camera, proxy));
            return NO_ERROR;
        } break;
        case SET_INPUT_SURFACE: {
            ALOGV("SET_INPUT_SURFACE");
            CHECK_INTERFACE(IMediaRecorder, data, reply);
            sp<IGraphicBufferConsumer> surface = interface_cast<IGraphicBufferConsumer>(
                    data.readStrongBinder());
            reply->writeInt32(setInputSurface(surface));
            return NO_ERROR;
        } break;
        case QUERY_SURFACE_MEDIASOURCE: {
            ALOGV("QUERY_SURFACE_MEDIASOURCE");
            CHECK_INTERFACE(IMediaRecorder, data, reply);
            // call the mediaserver side to create
            // a surfacemediasource
            sp<IGraphicBufferProducer> surfaceMediaSource = querySurfaceMediaSource();
            // The mediaserver might have failed to create a source
            int returnedNull= (surfaceMediaSource == NULL) ? 1 : 0 ;
            reply->writeInt32(returnedNull);
            if (!returnedNull) {
                reply->writeStrongBinder(IInterface::asBinder(surfaceMediaSource));
            }
            return NO_ERROR;
        } break;
#ifdef RTK_ENHANCE
        case SET_OUTPUT_BUFFER: {
            ALOGV("SET_OUTPUT_BUFFER");
            CHECK_INTERFACE(IMediaRecorder, data, reply);
            sp<IMemory> bufferData = interface_cast<IMemory>(data.readStrongBinder());
            sp<IMemory> bufferInfo = interface_cast<IMemory>(data.readStrongBinder());
            reply->writeInt32(setOutputBuffer(bufferData, bufferInfo));
            return NO_ERROR;
        }
#endif
        default:
            return BBinder::onTransact(code, data, reply, flags);
    }
}

// ----------------------------------------------------------------------------

} // namespace android
