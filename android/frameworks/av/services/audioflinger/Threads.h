/*
**
** Copyright 2012, The Android Open Source Project
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

#ifndef INCLUDING_FROM_AUDIOFLINGER_H
    #error This header file should only be included from AudioFlinger.h
#endif

class ThreadBase : public Thread {
public:

#include "TrackBase.h"

    enum type_t {
        MIXER,              // Thread class is MixerThread
        DIRECT,             // Thread class is DirectOutputThread
        DUPLICATING,        // Thread class is DuplicatingThread
        RECORD,             // Thread class is RecordThread
        OFFLOAD             // Thread class is OffloadThread
    };

    static const char *threadTypeToString(type_t type);

    ThreadBase(const sp<AudioFlinger>& audioFlinger, audio_io_handle_t id,
                audio_devices_t outDevice, audio_devices_t inDevice, type_t type,
                bool systemReady);
    virtual             ~ThreadBase();

    virtual status_t    readyToRun();

    void dumpBase(int fd, const Vector<String16>& args);
    void dumpEffectChains(int fd, const Vector<String16>& args);

    void clearPowerManager();

    // base for record and playback
    enum {
        CFG_EVENT_IO,
        CFG_EVENT_PRIO,
        CFG_EVENT_SET_PARAMETER,
        CFG_EVENT_CREATE_AUDIO_PATCH,
        CFG_EVENT_RELEASE_AUDIO_PATCH,
    };

    class ConfigEventData: public RefBase {
    public:
        virtual ~ConfigEventData() {}

        virtual  void dump(char *buffer, size_t size) = 0;
    protected:
        ConfigEventData() {}
    };

    // Config event sequence by client if status needed (e.g binder thread calling setParameters()):
    //  1. create SetParameterConfigEvent. This sets mWaitStatus in config event
    //  2. Lock mLock
    //  3. Call sendConfigEvent_l(): Append to mConfigEvents and mWaitWorkCV.signal
    //  4. sendConfigEvent_l() reads status from event->mStatus;
    //  5. sendConfigEvent_l() returns status
    //  6. Unlock
    //
    // Parameter sequence by server: threadLoop calling processConfigEvents_l():
    // 1. Lock mLock
    // 2. If there is an entry in mConfigEvents proceed ...
    // 3. Read first entry in mConfigEvents
    // 4. Remove first entry from mConfigEvents
    // 5. Process
    // 6. Set event->mStatus
    // 7. event->mCond.signal
    // 8. Unlock

    class ConfigEvent: public RefBase {
    public:
        virtual ~ConfigEvent() {}

        void dump(char *buffer, size_t size) { mData->dump(buffer, size); }

        const int mType; // event type e.g. CFG_EVENT_IO
        Mutex mLock;     // mutex associated with mCond
        Condition mCond; // condition for status return
        status_t mStatus; // status communicated to sender
        bool mWaitStatus; // true if sender is waiting for status
        bool mRequiresSystemReady; // true if must wait for system ready to enter event queue
        sp<ConfigEventData> mData;     // event specific parameter data

    protected:
        ConfigEvent(int type, bool requiresSystemReady = false) :
            mType(type), mStatus(NO_ERROR), mWaitStatus(false),
            mRequiresSystemReady(requiresSystemReady), mData(NULL) {}
    };

    class IoConfigEventData : public ConfigEventData {
    public:
        IoConfigEventData(audio_io_config_event event, pid_t pid) :
            mEvent(event), mPid(pid) {}

        virtual  void dump(char *buffer, size_t size) {
            snprintf(buffer, size, "IO event: event %d\n", mEvent);
        }

        const audio_io_config_event mEvent;
        const pid_t                 mPid;
    };

    class IoConfigEvent : public ConfigEvent {
    public:
        IoConfigEvent(audio_io_config_event event, pid_t pid) :
            ConfigEvent(CFG_EVENT_IO) {
            mData = new IoConfigEventData(event, pid);
        }
        virtual ~IoConfigEvent() {}
    };

    class PrioConfigEventData : public ConfigEventData {
    public:
        PrioConfigEventData(pid_t pid, pid_t tid, int32_t prio) :
            mPid(pid), mTid(tid), mPrio(prio) {}

        virtual  void dump(char *buffer, size_t size) {
            snprintf(buffer, size, "Prio event: pid %d, tid %d, prio %d\n", mPid, mTid, mPrio);
        }

        const pid_t mPid;
        const pid_t mTid;
        const int32_t mPrio;
    };

    class PrioConfigEvent : public ConfigEvent {
    public:
        PrioConfigEvent(pid_t pid, pid_t tid, int32_t prio) :
            ConfigEvent(CFG_EVENT_PRIO, true) {
            mData = new PrioConfigEventData(pid, tid, prio);
        }
        virtual ~PrioConfigEvent() {}
    };

    class SetParameterConfigEventData : public ConfigEventData {
    public:
        SetParameterConfigEventData(String8 keyValuePairs) :
            mKeyValuePairs(keyValuePairs) {}

        virtual  void dump(char *buffer, size_t size) {
            snprintf(buffer, size, "KeyValue: %s\n", mKeyValuePairs.string());
        }

        const String8 mKeyValuePairs;
    };

    class SetParameterConfigEvent : public ConfigEvent {
    public:
        SetParameterConfigEvent(String8 keyValuePairs) :
            ConfigEvent(CFG_EVENT_SET_PARAMETER) {
            mData = new SetParameterConfigEventData(keyValuePairs);
            mWaitStatus = true;
        }
        virtual ~SetParameterConfigEvent() {}
    };

    class CreateAudioPatchConfigEventData : public ConfigEventData {
    public:
        CreateAudioPatchConfigEventData(const struct audio_patch patch,
                                        audio_patch_handle_t handle) :
            mPatch(patch), mHandle(handle) {}

        virtual  void dump(char *buffer, size_t size) {
            snprintf(buffer, size, "Patch handle: %u\n", mHandle);
        }

        const struct audio_patch mPatch;
        audio_patch_handle_t mHandle;
    };

    class CreateAudioPatchConfigEvent : public ConfigEvent {
    public:
        CreateAudioPatchConfigEvent(const struct audio_patch patch,
                                    audio_patch_handle_t handle) :
            ConfigEvent(CFG_EVENT_CREATE_AUDIO_PATCH) {
            mData = new CreateAudioPatchConfigEventData(patch, handle);
            mWaitStatus = true;
        }
        virtual ~CreateAudioPatchConfigEvent() {}
    };

    class ReleaseAudioPatchConfigEventData : public ConfigEventData {
    public:
        ReleaseAudioPatchConfigEventData(const audio_patch_handle_t handle) :
            mHandle(handle) {}

        virtual  void dump(char *buffer, size_t size) {
            snprintf(buffer, size, "Patch handle: %u\n", mHandle);
        }

        audio_patch_handle_t mHandle;
    };

    class ReleaseAudioPatchConfigEvent : public ConfigEvent {
    public:
        ReleaseAudioPatchConfigEvent(const audio_patch_handle_t handle) :
            ConfigEvent(CFG_EVENT_RELEASE_AUDIO_PATCH) {
            mData = new ReleaseAudioPatchConfigEventData(handle);
            mWaitStatus = true;
        }
        virtual ~ReleaseAudioPatchConfigEvent() {}
    };

    class PMDeathRecipient : public IBinder::DeathRecipient {
    public:
                    PMDeathRecipient(const wp<ThreadBase>& thread) : mThread(thread) {}
        virtual     ~PMDeathRecipient() {}

        // IBinder::DeathRecipient
        virtual     void        binderDied(const wp<IBinder>& who);

    private:
                    PMDeathRecipient(const PMDeathRecipient&);
                    PMDeathRecipient& operator = (const PMDeathRecipient&);

        wp<ThreadBase> mThread;
    };

    virtual     status_t    initCheck() const = 0;

                // static externally-visible
                type_t      type() const { return mType; }
                bool isDuplicating() const { return (mType == DUPLICATING); }

                audio_io_handle_t id() const { return mId;}

                // dynamic externally-visible
                uint32_t    sampleRate() const { return mSampleRate; }
                audio_channel_mask_t channelMask() const { return mChannelMask; }
                audio_format_t format() const { return mHALFormat; }
                uint32_t channelCount() const { return mChannelCount; }
                // Called by AudioFlinger::frameCount(audio_io_handle_t output) and effects,
                // and returns the [normal mix] buffer's frame count.
    virtual     size_t      frameCount() const = 0;

                // Return's the HAL's frame count i.e. fast mixer buffer size.
                size_t      frameCountHAL() const { return mFrameCount; }

                size_t      frameSize() const { return mFrameSize; }

    // Should be "virtual status_t requestExitAndWait()" and override same
    // method in Thread, but Thread::requestExitAndWait() is not yet virtual.
                void        exit();
    virtual     bool        checkForNewParameter_l(const String8& keyValuePair,
                                                    status_t& status) = 0;
    virtual     status_t    setParameters(const String8& keyValuePairs);
    virtual     String8     getParameters(const String8& keys) = 0;
    virtual     void        ioConfigChanged(audio_io_config_event event, pid_t pid = 0) = 0;
                // sendConfigEvent_l() must be called with ThreadBase::mLock held
                // Can temporarily release the lock if waiting for a reply from
                // processConfigEvents_l().
                status_t    sendConfigEvent_l(sp<ConfigEvent>& event);
                void        sendIoConfigEvent(audio_io_config_event event, pid_t pid = 0);
                void        sendIoConfigEvent_l(audio_io_config_event event, pid_t pid = 0);
                void        sendPrioConfigEvent(pid_t pid, pid_t tid, int32_t prio);
                void        sendPrioConfigEvent_l(pid_t pid, pid_t tid, int32_t prio);
                status_t    sendSetParameterConfigEvent_l(const String8& keyValuePair);
                status_t    sendCreateAudioPatchConfigEvent(const struct audio_patch *patch,
                                                            audio_patch_handle_t *handle);
                status_t    sendReleaseAudioPatchConfigEvent(audio_patch_handle_t handle);
                void        processConfigEvents_l();
    virtual     void        cacheParameters_l() = 0;
    virtual     status_t    createAudioPatch_l(const struct audio_patch *patch,
                                               audio_patch_handle_t *handle) = 0;
    virtual     status_t    releaseAudioPatch_l(const audio_patch_handle_t handle) = 0;
    virtual     void        getAudioPortConfig(struct audio_port_config *config) = 0;


                // see note at declaration of mStandby, mOutDevice and mInDevice
                bool        standby() const { return mStandby; }
                audio_devices_t outDevice() const { return mOutDevice; }
                audio_devices_t inDevice() const { return mInDevice; }

    virtual     audio_stream_t* stream() const = 0;

                sp<EffectHandle> createEffect_l(
                                    const sp<AudioFlinger::Client>& client,
                                    const sp<IEffectClient>& effectClient,
                                    int32_t priority,
                                    audio_session_t sessionId,
                                    effect_descriptor_t *desc,
                                    int *enabled,
                                    status_t *status /*non-NULL*/,
                                    bool pinned);

                // return values for hasAudioSession (bit field)
                enum effect_state {
                    EFFECT_SESSION = 0x1,   // the audio session corresponds to at least one
                                            // effect
                    TRACK_SESSION = 0x2,    // the audio session corresponds to at least one
                                            // track
                    FAST_SESSION = 0x4      // the audio session corresponds to at least one
                                            // fast track
                };

                // get effect chain corresponding to session Id.
                sp<EffectChain> getEffectChain(audio_session_t sessionId);
                // same as getEffectChain() but must be called with ThreadBase mutex locked
                sp<EffectChain> getEffectChain_l(audio_session_t sessionId) const;
                // add an effect chain to the chain list (mEffectChains)
    virtual     status_t addEffectChain_l(const sp<EffectChain>& chain) = 0;
                // remove an effect chain from the chain list (mEffectChains)
    virtual     size_t removeEffectChain_l(const sp<EffectChain>& chain) = 0;
                // lock all effect chains Mutexes. Must be called before releasing the
                // ThreadBase mutex before processing the mixer and effects. This guarantees the
                // integrity of the chains during the process.
                // Also sets the parameter 'effectChains' to current value of mEffectChains.
                void lockEffectChains_l(Vector< sp<EffectChain> >& effectChains);
                // unlock effect chains after process
                void unlockEffectChains(const Vector< sp<EffectChain> >& effectChains);
                // get a copy of mEffectChains vector
                Vector< sp<EffectChain> > getEffectChains_l() const { return mEffectChains; };
                // set audio mode to all effect chains
                void setMode(audio_mode_t mode);
                // get effect module with corresponding ID on specified audio session
                sp<AudioFlinger::EffectModule> getEffect(audio_session_t sessionId, int effectId);
                sp<AudioFlinger::EffectModule> getEffect_l(audio_session_t sessionId, int effectId);
                // add and effect module. Also creates the effect chain is none exists for
                // the effects audio session
                status_t addEffect_l(const sp< EffectModule>& effect);
                // remove and effect module. Also removes the effect chain is this was the last
                // effect
                void removeEffect_l(const sp< EffectModule>& effect, bool release = false);
                // disconnect an effect handle from module and destroy module if last handle
                void disconnectEffectHandle(EffectHandle *handle, bool unpinIfLast);
                // detach all tracks connected to an auxiliary effect
    virtual     void detachAuxEffect_l(int effectId __unused) {}
                // returns a combination of:
                // - EFFECT_SESSION if effects on this audio session exist in one chain
                // - TRACK_SESSION if tracks on this audio session exist
                // - FAST_SESSION if fast tracks on this audio session exist
    virtual     uint32_t hasAudioSession_l(audio_session_t sessionId) const = 0;
                uint32_t hasAudioSession(audio_session_t sessionId) const {
                    Mutex::Autolock _l(mLock);
                    return hasAudioSession_l(sessionId);
                }

                // the value returned by default implementation is not important as the
                // strategy is only meaningful for PlaybackThread which implements this method
                virtual uint32_t getStrategyForSession_l(audio_session_t sessionId __unused)
                        { return 0; }

                // suspend or restore effect according to the type of effect passed. a NULL
                // type pointer means suspend all effects in the session
                void setEffectSuspended(const effect_uuid_t *type,
                                        bool suspend,
                                        audio_session_t sessionId = AUDIO_SESSION_OUTPUT_MIX);
                // check if some effects must be suspended/restored when an effect is enabled
                // or disabled
                void checkSuspendOnEffectEnabled(const sp<EffectModule>& effect,
                                                 bool enabled,
                                                 audio_session_t sessionId =
                                                        AUDIO_SESSION_OUTPUT_MIX);
                void checkSuspendOnEffectEnabled_l(const sp<EffectModule>& effect,
                                                   bool enabled,
                                                   audio_session_t sessionId =
                                                        AUDIO_SESSION_OUTPUT_MIX);

                virtual status_t    setSyncEvent(const sp<SyncEvent>& event) = 0;
                virtual bool        isValidSyncEvent(const sp<SyncEvent>& event) const = 0;

                // Return a reference to a per-thread heap which can be used to allocate IMemory
                // objects that will be read-only to client processes, read/write to mediaserver,
                // and shared by all client processes of the thread.
                // The heap is per-thread rather than common across all threads, because
                // clients can't be trusted not to modify the offset of the IMemory they receive.
                // If a thread does not have such a heap, this method returns 0.
                virtual sp<MemoryDealer>    readOnlyHeap() const { return 0; }

                virtual sp<IMemory> pipeMemory() const { return 0; }

                        void systemReady();

                // checkEffectCompatibility_l() must be called with ThreadBase::mLock held
                virtual status_t    checkEffectCompatibility_l(const effect_descriptor_t *desc,
                                                               audio_session_t sessionId) = 0;

    mutable     Mutex                   mLock;

protected:

                // entry describing an effect being suspended in mSuspendedSessions keyed vector
                class SuspendedSessionDesc : public RefBase {
                public:
                    SuspendedSessionDesc() : mRefCount(0) {}

                    int mRefCount;          // number of active suspend requests
                    effect_uuid_t mType;    // effect type UUID
                };

                void        acquireWakeLock(int uid = -1);
                virtual void acquireWakeLock_l(int uid = -1);
                void        releaseWakeLock();
                void        releaseWakeLock_l();
                void        updateWakeLockUids(const SortedVector<int> &uids);
                void        updateWakeLockUids_l(const SortedVector<int> &uids);
                void        getPowerManager_l();
                void setEffectSuspended_l(const effect_uuid_t *type,
                                          bool suspend,
                                          audio_session_t sessionId);
                // updated mSuspendedSessions when an effect suspended or restored
                void        updateSuspendedSessions_l(const effect_uuid_t *type,
                                                      bool suspend,
                                                      audio_session_t sessionId);
                // check if some effects must be suspended when an effect chain is added
                void checkSuspendOnAddEffectChain_l(const sp<EffectChain>& chain);

                String16 getWakeLockTag();

    virtual     void        preExit() { }
    virtual     void        setMasterMono_l(bool mono __unused) { }
    virtual     bool        requireMonoBlend() { return false; }

    friend class AudioFlinger;      // for mEffectChains

                const type_t            mType;

                // Used by parameters, config events, addTrack_l, exit
                Condition               mWaitWorkCV;

                const sp<AudioFlinger>  mAudioFlinger;

                // updated by PlaybackThread::readOutputParameters_l() or
                // RecordThread::readInputParameters_l()
                uint32_t                mSampleRate;
                size_t                  mFrameCount;       // output HAL, direct output, record
                audio_channel_mask_t    mChannelMask;
                uint32_t                mChannelCount;
                size_t                  mFrameSize;
                // not HAL frame size, this is for output sink (to pipe to fast mixer)
                audio_format_t          mFormat;           // Source format for Recording and
                                                           // Sink format for Playback.
                                                           // Sink format may be different than
                                                           // HAL format if Fastmixer is used.
                audio_format_t          mHALFormat;
                size_t                  mBufferSize;       // HAL buffer size for read() or write()

                Vector< sp<ConfigEvent> >     mConfigEvents;
                Vector< sp<ConfigEvent> >     mPendingConfigEvents; // events awaiting system ready

                // These fields are written and read by thread itself without lock or barrier,
                // and read by other threads without lock or barrier via standby(), outDevice()
                // and inDevice().
                // Because of the absence of a lock or barrier, any other thread that reads
                // these fields must use the information in isolation, or be prepared to deal
                // with possibility that it might be inconsistent with other information.
                bool                    mStandby;     // Whether thread is currently in standby.
                audio_devices_t         mOutDevice;   // output device
                audio_devices_t         mInDevice;    // input device
                audio_devices_t         mPrevOutDevice;   // previous output device
                audio_devices_t         mPrevInDevice;    // previous input device
                struct audio_patch      mPatch;
                audio_source_t          mAudioSource;

                const audio_io_handle_t mId;
                Vector< sp<EffectChain> > mEffectChains;

                static const int        kThreadNameLength = 16; // prctl(PR_SET_NAME) limit
                char                    mThreadName[kThreadNameLength]; // guaranteed NUL-terminated
                sp<IPowerManager>       mPowerManager;
                sp<IBinder>             mWakeLockToken;
                const sp<PMDeathRecipient> mDeathRecipient;
                // list of suspended effects per session and per type. The first (outer) vector is
                // keyed by session ID, the second (inner) by type UUID timeLow field
                KeyedVector< audio_session_t, KeyedVector< int, sp<SuspendedSessionDesc> > >
                                        mSuspendedSessions;
                static const size_t     kLogSize = 4 * 1024;
                sp<NBLog::Writer>       mNBLogWriter;
                bool                    mSystemReady;
                bool                    mNotifiedBatteryStart;
                ExtendedTimestamp       mTimestamp;
};

// --- PlaybackThread ---
class PlaybackThread : public ThreadBase {
public:

#include "PlaybackTracks.h"

    enum mixer_state {
        MIXER_IDLE,             // no active tracks
        MIXER_TRACKS_ENABLED,   // at least one active track, but no track has any data ready
        MIXER_TRACKS_READY,      // at least one active track, and at least one track has data
        MIXER_DRAIN_TRACK,      // drain currently playing track
        MIXER_DRAIN_ALL,        // fully drain the hardware
        // standby mode does not have an enum value
        // suspend by audio policy manager is orthogonal to mixer state
    };

    // retry count before removing active track in case of underrun on offloaded thread:
    // we need to make sure that AudioTrack client has enough time to send large buffers
    //FIXME may be more appropriate if expressed in time units. Need to revise how underrun is
    // handled for offloaded tracks
    static const int8_t kMaxTrackRetriesOffload = 20;
    static const int8_t kMaxTrackStartupRetriesOffload = 100;
    static const int8_t kMaxTrackStopRetriesOffload = 2;
    // 14 tracks max per client allows for 2 misbehaving application leaving 4 available tracks.
    static const uint32_t kMaxTracksPerUid = 14;

    PlaybackThread(const sp<AudioFlinger>& audioFlinger, AudioStreamOut* output,
                   audio_io_handle_t id, audio_devices_t device, type_t type, bool systemReady);
    virtual             ~PlaybackThread();

                void        dump(int fd, const Vector<String16>& args);

    // Thread virtuals
    virtual     bool        threadLoop();

    // RefBase
    virtual     void        onFirstRef();

    virtual     status_t    checkEffectCompatibility_l(const effect_descriptor_t *desc,
                                                       audio_session_t sessionId);

protected:
    // Code snippets that were lifted up out of threadLoop()
    virtual     void        threadLoop_mix() = 0;
    virtual     void        threadLoop_sleepTime() = 0;
    virtual     ssize_t     threadLoop_write();
    virtual     void        threadLoop_drain();
    virtual     void        threadLoop_standby();
    virtual     void        threadLoop_exit();
    virtual     void        threadLoop_removeTracks(const Vector< sp<Track> >& tracksToRemove);

                // prepareTracks_l reads and writes mActiveTracks, and returns
                // the pending set of tracks to remove via Vector 'tracksToRemove'.  The caller
                // is responsible for clearing or destroying this Vector later on, when it
                // is safe to do so. That will drop the final ref count and destroy the tracks.
    virtual     mixer_state prepareTracks_l(Vector< sp<Track> > *tracksToRemove) = 0;
                void        removeTracks_l(const Vector< sp<Track> >& tracksToRemove);

                void        writeCallback();
                void        resetWriteBlocked(uint32_t sequence);
                void        drainCallback();
                void        resetDraining(uint32_t sequence);
                void        errorCallback();

    static      int         asyncCallback(stream_callback_event_t event, void *param, void *cookie);

    virtual     bool        waitingAsyncCallback();
    virtual     bool        waitingAsyncCallback_l();
    virtual     bool        shouldStandby_l();
    virtual     void        onAddNewTrack_l();
                void        onAsyncError(); // error reported by AsyncCallbackThread

    // ThreadBase virtuals
    virtual     void        preExit();

    virtual     bool        keepWakeLock() const { return true; }

public:

    virtual     status_t    initCheck() const { return (mOutput == NULL) ? NO_INIT : NO_ERROR; }

//rtk begin
                uint32_t    fwlatency() const;
//rtk end
                // return estimated latency in milliseconds, as reported by HAL
                uint32_t    latency() const;
                // same, but lock must already be held
                uint32_t    latency_l() const;

                void        setMasterVolume(float value);
                void        setMasterMute(bool muted);

                void        setStreamVolume(audio_stream_type_t stream, float value);
                void        setStreamMute(audio_stream_type_t stream, bool muted);

                float       streamVolume(audio_stream_type_t stream) const;

                sp<Track>   createTrack_l(
                                const sp<AudioFlinger::Client>& client,
                                audio_stream_type_t streamType,
                                uint32_t sampleRate,
                                audio_format_t format,
                                audio_channel_mask_t channelMask,
                                size_t *pFrameCount,
                                const sp<IMemory>& sharedBuffer,
                                audio_session_t sessionId,
                                audio_output_flags_t *flags,
                                pid_t tid,
                                int uid,
                                status_t *status /*non-NULL*/);

                AudioStreamOut* getOutput() const;
                AudioStreamOut* clearOutput();
                virtual audio_stream_t* stream() const;

                // a very large number of suspend() will eventually wraparound, but unlikely
                void        suspend() { (void) android_atomic_inc(&mSuspended); }
                void        restore()
                                {
                                    // if restore() is done without suspend(), get back into
                                    // range so that the next suspend() will operate correctly
                                    if (android_atomic_dec(&mSuspended) <= 0) {
                                        android_atomic_release_store(0, &mSuspended);
                                    }
                                }
                bool        isSuspended() const
                                { return android_atomic_acquire_load(&mSuspended) > 0; }

    virtual     String8     getParameters(const String8& keys);
    virtual     void        ioConfigChanged(audio_io_config_event event, pid_t pid = 0);
                status_t    getRenderPosition(uint32_t *halFrames, uint32_t *dspFrames);
                // FIXME rename mixBuffer() to sinkBuffer() and remove int16_t* dependency.
                // Consider also removing and passing an explicit mMainBuffer initialization
                // parameter to AF::PlaybackThread::Track::Track().
                int16_t     *mixBuffer() const {
                    return reinterpret_cast<int16_t *>(mSinkBuffer); };

    virtual     void detachAuxEffect_l(int effectId);
                status_t attachAuxEffect(const sp<AudioFlinger::PlaybackThread::Track> track,
                        int EffectId);
                status_t attachAuxEffect_l(const sp<AudioFlinger::PlaybackThread::Track> track,
                        int EffectId);

                virtual status_t addEffectChain_l(const sp<EffectChain>& chain);
                virtual size_t removeEffectChain_l(const sp<EffectChain>& chain);
                virtual uint32_t hasAudioSession_l(audio_session_t sessionId) const;
                virtual uint32_t getStrategyForSession_l(audio_session_t sessionId);


                virtual status_t setSyncEvent(const sp<SyncEvent>& event);
                virtual bool     isValidSyncEvent(const sp<SyncEvent>& event) const;

                // called with AudioFlinger lock held
                        bool     invalidateTracks_l(audio_stream_type_t streamType);
                virtual void     invalidateTracks(audio_stream_type_t streamType);

    virtual     size_t      frameCount() const { return mNormalFrameCount; }

                status_t    getTimestamp_l(AudioTimestamp& timestamp);

                void        addPatchTrack(const sp<PatchTrack>& track);
                void        deletePatchTrack(const sp<PatchTrack>& track);

    virtual     void        getAudioPortConfig(struct audio_port_config *config);

protected:
    // updated by readOutputParameters_l()
    size_t                          mNormalFrameCount;  // normal mixer and effects

    bool                            mThreadThrottle;     // throttle the thread processing
    uint32_t                        mThreadThrottleTimeMs; // throttle time for MIXER threads
    uint32_t                        mThreadThrottleEndMs;  // notify once per throttling
    uint32_t                        mHalfBufferMs;       // half the buffer size in milliseconds

    void*                           mSinkBuffer;         // frame size aligned sink buffer

    // TODO:
    // Rearrange the buffer info into a struct/class with
    // clear, copy, construction, destruction methods.
    //
    // mSinkBuffer also has associated with it:
    //
    // mSinkBufferSize: Sink Buffer Size
    // mFormat: Sink Buffer Format

    // Mixer Buffer (mMixerBuffer*)
    //
    // In the case of floating point or multichannel data, which is not in the
    // sink format, it is required to accumulate in a higher precision or greater channel count
    // buffer before downmixing or data conversion to the sink buffer.

    // Set to "true" to enable the Mixer Buffer otherwise mixer output goes to sink buffer.
    bool                            mMixerBufferEnabled;

    // Storage, 32 byte aligned (may make this alignment a requirement later).
    // Due to constraints on mNormalFrameCount, the buffer size is a multiple of 16 frames.
    void*                           mMixerBuffer;

    // Size of mMixerBuffer in bytes: mNormalFrameCount * #channels * sampsize.
    size_t                          mMixerBufferSize;

    // The audio format of mMixerBuffer. Set to AUDIO_FORMAT_PCM_(FLOAT|16_BIT) only.
    audio_format_t                  mMixerBufferFormat;

    // An internal flag set to true by MixerThread::prepareTracks_l()
    // when mMixerBuffer contains valid data after mixing.
    bool                            mMixerBufferValid;

    // Effects Buffer (mEffectsBuffer*)
    //
    // In the case of effects data, which is not in the sink format,
    // it is required to accumulate in a different buffer before data conversion
    // to the sink buffer.

    // Set to "true" to enable the Effects Buffer otherwise effects output goes to sink buffer.
    bool                            mEffectBufferEnabled;

    // Storage, 32 byte aligned (may make this alignment a requirement later).
    // Due to constraints on mNormalFrameCount, the buffer size is a multiple of 16 frames.
    void*                           mEffectBuffer;

    // Size of mEffectsBuffer in bytes: mNormalFrameCount * #channels * sampsize.
    size_t                          mEffectBufferSize;

    // The audio format of mEffectsBuffer. Set to AUDIO_FORMAT_PCM_16_BIT only.
    audio_format_t                  mEffectBufferFormat;

    // An internal flag set to true by MixerThread::prepareTracks_l()
    // when mEffectsBuffer contains valid data after mixing.
    //
    // When this is set, all mixer data is routed into the effects buffer
    // for any processing (including output processing).
    bool                            mEffectBufferValid;

    // suspend count, > 0 means suspended.  While suspended, the thread continues to pull from
    // tracks and mix, but doesn't write to HAL.  A2DP and SCO HAL implementations can't handle
    // concurrent use of both of them, so Audio Policy Service suspends one of the threads to
    // workaround that restriction.
    // 'volatile' means accessed via atomic operations and no lock.
    volatile int32_t                mSuspended;

    int64_t                         mBytesWritten;
    int64_t                         mFramesWritten; // not reset on standby
    int64_t                         mSuspendedFrames; // not reset on standby
private:
    // mMasterMute is in both PlaybackThread and in AudioFlinger.  When a
    // PlaybackThread needs to find out if master-muted, it checks it's local
    // copy rather than the one in AudioFlinger.  This optimization saves a lock.
    bool                            mMasterMute;
                void        setMasterMute_l(bool muted) { mMasterMute = muted; }
protected:
    SortedVector< wp<Track> >       mActiveTracks;  // FIXME check if this could be sp<>
    SortedVector<int>               mWakeLockUids;
    int                             mActiveTracksGeneration;
    wp<Track>                       mLatestActiveTrack; // latest track added to mActiveTracks

    // Allocate a track name for a given channel mask.
    //   Returns name >= 0 if successful, -1 on failure.
    virtual int             getTrackName_l(audio_channel_mask_t channelMask, audio_format_t format,
                                           audio_session_t sessionId, uid_t uid) = 0;
    virtual void            deleteTrackName_l(int name) = 0;

    // Time to sleep between cycles when:
    virtual uint32_t        activeSleepTimeUs() const;      // mixer state MIXER_TRACKS_ENABLED
    virtual uint32_t        idleSleepTimeUs() const = 0;    // mixer state MIXER_IDLE
    virtual uint32_t        suspendSleepTimeUs() const = 0; // audio policy manager suspended us
    // No sleep when mixer state == MIXER_TRACKS_READY; relies on audio HAL stream->write()
    // No sleep in standby mode; waits on a condition

    // Code snippets that are temporarily lifted up out of threadLoop() until the merge
                void        checkSilentMode_l();

    // Non-trivial for DUPLICATING only
    virtual     void        saveOutputTracks() { }
    virtual     void        clearOutputTracks() { }

    // Cache various calculated values, at threadLoop() entry and after a parameter change
    virtual     void        cacheParameters_l();

    virtual     uint32_t    correctLatency_l(uint32_t latency) const;

    virtual     status_t    createAudioPatch_l(const struct audio_patch *patch,
                                   audio_patch_handle_t *handle);
    virtual     status_t    releaseAudioPatch_l(const audio_patch_handle_t handle);

                bool        usesHwAvSync() const { return (mType == DIRECT || mType == OFFLOAD /* rtk add offload */) && (mOutput != NULL)
                                    && mHwSupportsPause
                                    && (mOutput->flags & AUDIO_OUTPUT_FLAG_HW_AV_SYNC); }

                uint32_t    trackCountForUid_l(uid_t uid);

private:

    friend class AudioFlinger;      // for numerous

    PlaybackThread& operator = (const PlaybackThread&);

    status_t    addTrack_l(const sp<Track>& track);
    bool        destroyTrack_l(const sp<Track>& track);
    void        removeTrack_l(const sp<Track>& track);
    void        broadcast_l();

    void        readOutputParameters_l();

    virtual void dumpInternals(int fd, const Vector<String16>& args);
    void        dumpTracks(int fd, const Vector<String16>& args);

    SortedVector< sp<Track> >       mTracks;
    stream_type_t                   mStreamTypes[AUDIO_STREAM_CNT];
    AudioStreamOut                  *mOutput;

    float                           mMasterVolume;
    nsecs_t                         mLastWriteTime;
    int                             mNumWrites;
    int                             mNumDelayedWrites;
    bool                            mInWrite;

    // FIXME rename these former local variables of threadLoop to standard "m" names
    nsecs_t                         mStandbyTimeNs;
    size_t                          mSinkBufferSize;

    // cached copies of activeSleepTimeUs() and idleSleepTimeUs() made by cacheParameters_l()
    uint32_t                        mActiveSleepTimeUs;
    uint32_t                        mIdleSleepTimeUs;

    uint32_t                        mSleepTimeUs;

    // mixer status returned by prepareTracks_l()
    mixer_state                     mMixerStatus; // current cycle
                                                  // previous cycle when in prepareTracks_l()
    mixer_state                     mMixerStatusIgnoringFastTracks;
                                                  // FIXME or a separate ready state per track

    // FIXME move these declarations into the specific sub-class that needs them
    // MIXER only
    uint32_t                        sleepTimeShift;

    // same as AudioFlinger::mStandbyTimeInNsecs except for DIRECT which uses a shorter value
    nsecs_t                         mStandbyDelayNs;

    // MIXER only
    nsecs_t                         maxPeriod;

    // DUPLICATING only
    uint32_t                        writeFrames;

    size_t                          mBytesRemaining;
    size_t                          mCurrentWriteLength;
    bool                            mUseAsyncWrite;
    // mWriteAckSequence contains current write sequence on bits 31-1. The write sequence is
    // incremented each time a write(), a flush() or a standby() occurs.
    // Bit 0 is set when a write blocks and indicates a callback is expected.
    // Bit 0 is reset by the async callback thread calling resetWriteBlocked(). Out of sequence
    // callbacks are ignored.
    uint32_t                        mWriteAckSequence;
    // mDrainSequence contains current drain sequence on bits 31-1. The drain sequence is
    // incremented each time a drain is requested or a flush() or standby() occurs.
    // Bit 0 is set when the drain() command is called at the HAL and indicates a callback is
    // expected.
    // Bit 0 is reset by the async callback thread calling resetDraining(). Out of sequence
    // callbacks are ignored.
    uint32_t                        mDrainSequence;
    // A condition that must be evaluated by prepareTrack_l() has changed and we must not wait
    // for async write callback in the thread loop before evaluating it
    bool                            mSignalPending;
    sp<AsyncCallbackThread>         mCallbackThread;

private:
    // The HAL output sink is treated as non-blocking, but current implementation is blocking
    sp<NBAIO_Sink>          mOutputSink;
    // If a fast mixer is present, the blocking pipe sink, otherwise clear
    sp<NBAIO_Sink>          mPipeSink;
    // The current sink for the normal mixer to write it's (sub)mix, mOutputSink or mPipeSink
    sp<NBAIO_Sink>          mNormalSink;
#ifdef TEE_SINK
    // For dumpsys
    sp<NBAIO_Sink>          mTeeSink;
    sp<NBAIO_Source>        mTeeSource;
#endif
    uint32_t                mScreenState;   // cached copy of gScreenState
    static const size_t     kFastMixerLogSize = 4 * 1024;
    sp<NBLog::Writer>       mFastMixerNBLogWriter;
public:
    virtual     bool        hasFastMixer() const = 0;
    virtual     FastTrackUnderruns getFastTrackUnderruns(size_t fastIndex __unused) const
                                { FastTrackUnderruns dummy; return dummy; }

protected:
                // accessed by both binder threads and within threadLoop(), lock on mutex needed
                unsigned    mFastTrackAvailMask;    // bit i set if fast track [i] is available
                bool        mHwSupportsPause;
                bool        mHwPaused;
                bool        mFlushPending;
};

class MixerThread : public PlaybackThread {
public:
    MixerThread(const sp<AudioFlinger>& audioFlinger,
                AudioStreamOut* output,
                audio_io_handle_t id,
                audio_devices_t device,
                bool systemReady,
                type_t type = MIXER);
    virtual             ~MixerThread();

    // Thread virtuals

    virtual     bool        checkForNewParameter_l(const String8& keyValuePair,
                                                   status_t& status);
    virtual     void        dumpInternals(int fd, const Vector<String16>& args);

protected:
    virtual     mixer_state prepareTracks_l(Vector< sp<Track> > *tracksToRemove);
    virtual     int         getTrackName_l(audio_channel_mask_t channelMask, audio_format_t format,
                                           audio_session_t sessionId, uid_t uid);
    virtual     void        deleteTrackName_l(int name);
    virtual     uint32_t    idleSleepTimeUs() const;
    virtual     uint32_t    suspendSleepTimeUs() const;
    virtual     void        cacheParameters_l();

    virtual void acquireWakeLock_l(int uid = -1) {
        PlaybackThread::acquireWakeLock_l(uid);
        if (hasFastMixer()) {
            mFastMixer->setBoottimeOffset(
                    mTimestamp.mTimebaseOffset[ExtendedTimestamp::TIMEBASE_BOOTTIME]);
        }
    }

    // threadLoop snippets
    virtual     ssize_t     threadLoop_write();
    virtual     void        threadLoop_standby();
    virtual     void        threadLoop_mix();
    virtual     void        threadLoop_sleepTime();
    virtual     void        threadLoop_removeTracks(const Vector< sp<Track> >& tracksToRemove);
    virtual     uint32_t    correctLatency_l(uint32_t latency) const;

    virtual     status_t    createAudioPatch_l(const struct audio_patch *patch,
                                   audio_patch_handle_t *handle);
    virtual     status_t    releaseAudioPatch_l(const audio_patch_handle_t handle);

                AudioMixer* mAudioMixer;    // normal mixer
private:
                // one-time initialization, no locks required
                sp<FastMixer>     mFastMixer;     // non-0 if there is also a fast mixer
                sp<AudioWatchdog> mAudioWatchdog; // non-0 if there is an audio watchdog thread

                // contents are not guaranteed to be consistent, no locks required
                FastMixerDumpState mFastMixerDumpState;
#ifdef STATE_QUEUE_DUMP
                StateQueueObserverDump mStateQueueObserverDump;
                StateQueueMutatorDump  mStateQueueMutatorDump;
#endif
                AudioWatchdogDump mAudioWatchdogDump;

                // accessible only within the threadLoop(), no locks required
                //          mFastMixer->sq()    // for mutating and pushing state
                int32_t     mFastMixerFutex;    // for cold idle

                std::atomic_bool mMasterMono;
public:
    virtual     bool        hasFastMixer() const { return mFastMixer != 0; }
    virtual     FastTrackUnderruns getFastTrackUnderruns(size_t fastIndex) const {
                              ALOG_ASSERT(fastIndex < FastMixerState::sMaxFastTracks);
                              return mFastMixerDumpState.mTracks[fastIndex].mUnderruns;
                            }

protected:
    virtual     void       setMasterMono_l(bool mono) {
                               mMasterMono.store(mono);
                               if (mFastMixer != nullptr) { /* hasFastMixer() */
                                   mFastMixer->setMasterMono(mMasterMono);
                               }
                           }
                // the FastMixer performs mono blend if it exists.
                // Blending with limiter is not idempotent,
                // and blending without limiter is idempotent but inefficient to do twice.
    virtual     bool       requireMonoBlend() { return mMasterMono.load() && !hasFastMixer(); }
};

class DirectOutputThread : public PlaybackThread {
public:

    DirectOutputThread(const sp<AudioFlinger>& audioFlinger, AudioStreamOut* output,
                       audio_io_handle_t id, audio_devices_t device, bool systemReady);
    virtual                 ~DirectOutputThread();

    // Thread virtuals

    virtual     bool        checkForNewParameter_l(const String8& keyValuePair,
                                                   status_t& status);
    virtual     void        flushHw_l();

protected:
    virtual     int         getTrackName_l(audio_channel_mask_t channelMask, audio_format_t format,
                                           audio_session_t sessionId, uid_t uid);
    virtual     void        deleteTrackName_l(int name);
    virtual     uint32_t    activeSleepTimeUs() const;
    virtual     uint32_t    idleSleepTimeUs() const;
    virtual     uint32_t    suspendSleepTimeUs() const;
    virtual     void        cacheParameters_l();

    // threadLoop snippets
    virtual     mixer_state prepareTracks_l(Vector< sp<Track> > *tracksToRemove);
    virtual     void        threadLoop_mix();
    virtual     void        threadLoop_sleepTime();
    virtual     void        threadLoop_exit();
    virtual     bool        shouldStandby_l();

    virtual     void        onAddNewTrack_l();

    // volumes last sent to audio HAL with stream->set_volume()
    float mLeftVolFloat;
    float mRightVolFloat;

    DirectOutputThread(const sp<AudioFlinger>& audioFlinger, AudioStreamOut* output,
                        audio_io_handle_t id, uint32_t device, ThreadBase::type_t type,
                        bool systemReady);
    void processVolume_l(Track *track, bool lastTrack);

    // prepareTracks_l() tells threadLoop_mix() the name of the single active track
    sp<Track>               mActiveTrack;

    wp<Track>               mPreviousTrack;         // used to detect track switch

public:
    virtual     bool        hasFastMixer() const { return false; }
};

class OffloadThread : public DirectOutputThread {
public:

    OffloadThread(const sp<AudioFlinger>& audioFlinger, AudioStreamOut* output,
                        audio_io_handle_t id, uint32_t device, bool systemReady);
    virtual                 ~OffloadThread() {};
    virtual     void        flushHw_l();

protected:
    // threadLoop snippets
    virtual     mixer_state prepareTracks_l(Vector< sp<Track> > *tracksToRemove);
    virtual     void        threadLoop_exit();

    virtual     bool        waitingAsyncCallback();
    virtual     bool        waitingAsyncCallback_l();
    virtual     void        invalidateTracks(audio_stream_type_t streamType);

    virtual     bool        keepWakeLock() const { return (mKeepWakeLock || (mDrainSequence & 1)); }

private:
    size_t      mPausedWriteLength;     // length in bytes of write interrupted by pause
    size_t      mPausedBytesRemaining;  // bytes still waiting in mixbuffer after resume
    bool        mKeepWakeLock;          // keep wake lock while waiting for write callback
    uint64_t    mOffloadUnderrunPosition; // Current frame position for offloaded playback
                                          // used and valid only during underrun.  ~0 if
                                          // no underrun has occurred during playback and
                                          // is not reset on standby.
};

class AsyncCallbackThread : public Thread {
public:

    AsyncCallbackThread(const wp<PlaybackThread>& playbackThread);

    virtual             ~AsyncCallbackThread();

    // Thread virtuals
    virtual bool        threadLoop();

    // RefBase
    virtual void        onFirstRef();

            void        exit();
            void        setWriteBlocked(uint32_t sequence);
            void        resetWriteBlocked();
            void        setDraining(uint32_t sequence);
            void        resetDraining();
            void        setAsyncError();

private:
    const wp<PlaybackThread>   mPlaybackThread;
    // mWriteAckSequence corresponds to the last write sequence passed by the offload thread via
    // setWriteBlocked(). The sequence is shifted one bit to the left and the lsb is used
    // to indicate that the callback has been received via resetWriteBlocked()
    uint32_t                   mWriteAckSequence;
    // mDrainSequence corresponds to the last drain sequence passed by the offload thread via
    // setDraining(). The sequence is shifted one bit to the left and the lsb is used
    // to indicate that the callback has been received via resetDraining()
    uint32_t                   mDrainSequence;
    Condition                  mWaitWorkCV;
    Mutex                      mLock;
    bool                       mAsyncError;
};

class DuplicatingThread : public MixerThread {
public:
    DuplicatingThread(const sp<AudioFlinger>& audioFlinger, MixerThread* mainThread,
                      audio_io_handle_t id, bool systemReady);
    virtual                 ~DuplicatingThread();

    // Thread virtuals
                void        addOutputTrack(MixerThread* thread);
                void        removeOutputTrack(MixerThread* thread);
                uint32_t    waitTimeMs() const { return mWaitTimeMs; }
protected:
    virtual     uint32_t    activeSleepTimeUs() const;

private:
                bool        outputsReady(const SortedVector< sp<OutputTrack> > &outputTracks);
protected:
    // threadLoop snippets
    virtual     void        threadLoop_mix();
    virtual     void        threadLoop_sleepTime();
    virtual     ssize_t     threadLoop_write();
    virtual     void        threadLoop_standby();
    virtual     void        cacheParameters_l();

private:
    // called from threadLoop, addOutputTrack, removeOutputTrack
    virtual     void        updateWaitTime_l();
protected:
    virtual     void        saveOutputTracks();
    virtual     void        clearOutputTracks();
private:

                uint32_t    mWaitTimeMs;
    SortedVector < sp<OutputTrack> >  outputTracks;
    SortedVector < sp<OutputTrack> >  mOutputTracks;
public:
    virtual     bool        hasFastMixer() const { return false; }
};


// record thread
class RecordThread : public ThreadBase
{
public:

    class RecordTrack;

    /* The ResamplerBufferProvider is used to retrieve recorded input data from the
     * RecordThread.  It maintains local state on the relative position of the read
     * position of the RecordTrack compared with the RecordThread.
     */
    class ResamplerBufferProvider : public AudioBufferProvider
    {
    public:
        ResamplerBufferProvider(RecordTrack* recordTrack) :
            mRecordTrack(recordTrack),
            mRsmpInUnrel(0), mRsmpInFront(0) { }
        virtual ~ResamplerBufferProvider() { }

        // called to set the ResamplerBufferProvider to head of the RecordThread data buffer,
        // skipping any previous data read from the hal.
        virtual void reset();

        /* Synchronizes RecordTrack position with the RecordThread.
         * Calculates available frames and handle overruns if the RecordThread
         * has advanced faster than the ResamplerBufferProvider has retrieved data.
         * TODO: why not do this for every getNextBuffer?
         *
         * Parameters
         * framesAvailable:  pointer to optional output size_t to store record track
         *                   frames available.
         *      hasOverrun:  pointer to optional boolean, returns true if track has overrun.
         */

        virtual void sync(size_t *framesAvailable = NULL, bool *hasOverrun = NULL);

        // AudioBufferProvider interface
        virtual status_t    getNextBuffer(AudioBufferProvider::Buffer* buffer);
        virtual void        releaseBuffer(AudioBufferProvider::Buffer* buffer);
    private:
        RecordTrack * const mRecordTrack;
        size_t              mRsmpInUnrel;   // unreleased frames remaining from
                                            // most recent getNextBuffer
                                            // for debug only
        int32_t             mRsmpInFront;   // next available frame
                                            // rolling counter that is never cleared
    };

    /* The RecordBufferConverter is used for format, channel, and sample rate
     * conversion for a RecordTrack.
     *
     * TODO: Self contained, so move to a separate file later.
     *
     * RecordBufferConverter uses the convert() method rather than exposing a
     * buffer provider interface; this is to save a memory copy.
     */
    class RecordBufferConverter
    {
    public:
        RecordBufferConverter(
                audio_channel_mask_t srcChannelMask, audio_format_t srcFormat,
                uint32_t srcSampleRate,
                audio_channel_mask_t dstChannelMask, audio_format_t dstFormat,
                uint32_t dstSampleRate);

        ~RecordBufferConverter();

        /* Converts input data from an AudioBufferProvider by format, channelMask,
         * and sampleRate to a destination buffer.
         *
         * Parameters
         *      dst:  buffer to place the converted data.
         * provider:  buffer provider to obtain source data.
         *   frames:  number of frames to convert
         *
         * Returns the number of frames converted.
         */
        size_t convert(void *dst, AudioBufferProvider *provider, size_t frames);

        // returns NO_ERROR if constructor was successful
        status_t initCheck() const {
            // mSrcChannelMask set on successful updateParameters
            return mSrcChannelMask != AUDIO_CHANNEL_INVALID ? NO_ERROR : NO_INIT;
        }

        // allows dynamic reconfigure of all parameters
        status_t updateParameters(
                audio_channel_mask_t srcChannelMask, audio_format_t srcFormat,
                uint32_t srcSampleRate,
                audio_channel_mask_t dstChannelMask, audio_format_t dstFormat,
                uint32_t dstSampleRate);

        // called to reset resampler buffers on record track discontinuity
        void reset() {
            if (mResampler != NULL) {
                mResampler->reset();
            }
        }

    private:
        // format conversion when not using resampler
        void convertNoResampler(void *dst, const void *src, size_t frames);

        // format conversion when using resampler; modifies src in-place
        void convertResampler(void *dst, /*not-a-const*/ void *src, size_t frames);

        // user provided information
        audio_channel_mask_t mSrcChannelMask;
        audio_format_t       mSrcFormat;
        uint32_t             mSrcSampleRate;
        audio_channel_mask_t mDstChannelMask;
        audio_format_t       mDstFormat;
        uint32_t             mDstSampleRate;

        // derived information
        uint32_t             mSrcChannelCount;
        uint32_t             mDstChannelCount;
        size_t               mDstFrameSize;

        // format conversion buffer
        void                *mBuf;
        size_t               mBufFrames;
        size_t               mBufFrameSize;

        // resampler info
        AudioResampler      *mResampler;

        bool                 mIsLegacyDownmix;  // legacy stereo to mono conversion needed
        bool                 mIsLegacyUpmix;    // legacy mono to stereo conversion needed
        bool                 mRequiresFloat;    // data processing requires float (e.g. resampler)
        PassthruBufferProvider *mInputConverterProvider;    // converts input to float
        int8_t               mIdxAry[sizeof(uint32_t) * 8]; // used for channel mask conversion
    };

#include "RecordTracks.h"

            RecordThread(const sp<AudioFlinger>& audioFlinger,
                    AudioStreamIn *input,
                    audio_io_handle_t id,
                    audio_devices_t outDevice,
                    audio_devices_t inDevice,
                    bool systemReady
#ifdef TEE_SINK
                    , const sp<NBAIO_Sink>& teeSink
#endif
                    );
            virtual     ~RecordThread();

    // no addTrack_l ?
    void        destroyTrack_l(const sp<RecordTrack>& track);
    void        removeTrack_l(const sp<RecordTrack>& track);

    void        dumpInternals(int fd, const Vector<String16>& args);
    void        dumpTracks(int fd, const Vector<String16>& args);

    // Thread virtuals
    virtual bool        threadLoop();

    // RefBase
    virtual void        onFirstRef();

    virtual status_t    initCheck() const { return (mInput == NULL) ? NO_INIT : NO_ERROR; }

    virtual sp<MemoryDealer>    readOnlyHeap() const { return mReadOnlyHeap; }

    virtual sp<IMemory> pipeMemory() const { return mPipeMemory; }

            sp<AudioFlinger::RecordThread::RecordTrack>  createRecordTrack_l(
                    const sp<AudioFlinger::Client>& client,
                    uint32_t sampleRate,
                    audio_format_t format,
                    audio_channel_mask_t channelMask,
                    size_t *pFrameCount,
                    audio_session_t sessionId,
                    size_t *notificationFrames,
                    int uid,
                    audio_input_flags_t *flags,
                    pid_t tid,
                    status_t *status /*non-NULL*/);

            status_t    start(RecordTrack* recordTrack,
                              AudioSystem::sync_event_t event,
                              audio_session_t triggerSession);

            // ask the thread to stop the specified track, and
            // return true if the caller should then do it's part of the stopping process
            bool        stop(RecordTrack* recordTrack);

            void        dump(int fd, const Vector<String16>& args);
            AudioStreamIn* clearInput();
            virtual audio_stream_t* stream() const;


    virtual bool        checkForNewParameter_l(const String8& keyValuePair,
                                               status_t& status);
    virtual void        cacheParameters_l() {}
    virtual String8     getParameters(const String8& keys);
    virtual void        ioConfigChanged(audio_io_config_event event, pid_t pid = 0);
    virtual status_t    createAudioPatch_l(const struct audio_patch *patch,
                                           audio_patch_handle_t *handle);
    virtual status_t    releaseAudioPatch_l(const audio_patch_handle_t handle);

            void        addPatchRecord(const sp<PatchRecord>& record);
            void        deletePatchRecord(const sp<PatchRecord>& record);

            void        readInputParameters_l();
    virtual uint32_t    getInputFramesLost();
//rtk begin
    virtual uint32_t    getInputLatency();
    virtual int64_t     getInputTimeStamp();
//rtk end

    virtual status_t addEffectChain_l(const sp<EffectChain>& chain);
    virtual size_t removeEffectChain_l(const sp<EffectChain>& chain);
    virtual uint32_t hasAudioSession_l(audio_session_t sessionId) const;

            // Return the set of unique session IDs across all tracks.
            // The keys are the session IDs, and the associated values are meaningless.
            // FIXME replace by Set [and implement Bag/Multiset for other uses].
            KeyedVector<audio_session_t, bool> sessionIds() const;

    virtual status_t setSyncEvent(const sp<SyncEvent>& event);
    virtual bool     isValidSyncEvent(const sp<SyncEvent>& event) const;

    static void syncStartEventCallback(const wp<SyncEvent>& event);

    virtual size_t      frameCount() const { return mFrameCount; }
            bool        hasFastCapture() const { return mFastCapture != 0; }
    virtual void        getAudioPortConfig(struct audio_port_config *config);

    virtual status_t    checkEffectCompatibility_l(const effect_descriptor_t *desc,
                                                   audio_session_t sessionId);

private:
            // Enter standby if not already in standby, and set mStandby flag
            void    standbyIfNotAlreadyInStandby();

            // Call the HAL standby method unconditionally, and don't change mStandby flag
            void    inputStandBy();

            AudioStreamIn                       *mInput;
            SortedVector < sp<RecordTrack> >    mTracks;
            // mActiveTracks has dual roles:  it indicates the current active track(s), and
            // is used together with mStartStopCond to indicate start()/stop() progress
            SortedVector< sp<RecordTrack> >     mActiveTracks;
            // generation counter for mActiveTracks
            int                                 mActiveTracksGen;
            Condition                           mStartStopCond;

            // resampler converts input at HAL Hz to output at AudioRecord client Hz
            void                               *mRsmpInBuffer; //
            size_t                              mRsmpInFrames;  // size of resampler input in frames
            size_t                              mRsmpInFramesP2;// size rounded up to a power-of-2

            // rolling index that is never cleared
            int32_t                             mRsmpInRear;    // last filled frame + 1

            // For dumpsys
            const sp<NBAIO_Sink>                mTeeSink;

            const sp<MemoryDealer>              mReadOnlyHeap;

            // one-time initialization, no locks required
            sp<FastCapture>                     mFastCapture;   // non-0 if there is also
                                                                // a fast capture

            // FIXME audio watchdog thread

            // contents are not guaranteed to be consistent, no locks required
            FastCaptureDumpState                mFastCaptureDumpState;
#ifdef STATE_QUEUE_DUMP
            // FIXME StateQueue observer and mutator dump fields
#endif
            // FIXME audio watchdog dump

            // accessible only within the threadLoop(), no locks required
            //          mFastCapture->sq()      // for mutating and pushing state
            int32_t     mFastCaptureFutex;      // for cold idle

            // The HAL input source is treated as non-blocking,
            // but current implementation is blocking
            sp<NBAIO_Source>                    mInputSource;
            // The source for the normal capture thread to read from: mInputSource or mPipeSource
            sp<NBAIO_Source>                    mNormalSource;
            // If a fast capture is present, the non-blocking pipe sink written to by fast capture,
            // otherwise clear
            sp<NBAIO_Sink>                      mPipeSink;
            // If a fast capture is present, the non-blocking pipe source read by normal thread,
            // otherwise clear
            sp<NBAIO_Source>                    mPipeSource;
            // Depth of pipe from fast capture to normal thread and fast clients, always power of 2
            size_t                              mPipeFramesP2;
            // If a fast capture is present, the Pipe as IMemory, otherwise clear
            sp<IMemory>                         mPipeMemory;

            static const size_t                 kFastCaptureLogSize = 4 * 1024;
            sp<NBLog::Writer>                   mFastCaptureNBLogWriter;

            bool                                mFastTrackAvail;    // true if fast track available
};
