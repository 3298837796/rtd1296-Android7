/*
 * Copyright (C) 2010 The Android Open Source Project
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

#ifndef NUPLAYER_RENDERER_H_

#define NUPLAYER_RENDERER_H_

#include <media/AudioResamplerPublic.h>
#include <media/AVSyncSettings.h>

#include "NuPlayer.h"
#ifdef RTK_PLATFORM
#define RTK_ENB_QUEUE_DEBUG_LOG
#endif

namespace android {

struct ABuffer;
class  AWakeLock;
struct MediaClock;
struct VideoFrameScheduler;

struct NuPlayer::Renderer : public AHandler {
    enum Flags {
        FLAG_REAL_TIME = 1,
        FLAG_OFFLOAD_AUDIO = 2,
    };
    Renderer(const sp<MediaPlayerBase::AudioSink> &sink,
             const sp<AMessage> &notify,
             uint32_t flags = 0);

    static size_t AudioSinkCallback(
            MediaPlayerBase::AudioSink *audioSink,
            void *data, size_t size, void *me,
            MediaPlayerBase::AudioSink::cb_event_t event);

    void queueBuffer(
            bool audio,
            const sp<ABuffer> &buffer,
            const sp<AMessage> &notifyConsumed);

    void queueEOS(bool audio, status_t finalResult);

    status_t setPlaybackSettings(const AudioPlaybackRate &rate /* sanitized */);
    status_t getPlaybackSettings(AudioPlaybackRate *rate /* nonnull */);
    status_t setSyncSettings(const AVSyncSettings &sync, float videoFpsHint);
    status_t getSyncSettings(AVSyncSettings *sync /* nonnull */, float *videoFps /* nonnull */);

    void flush(bool audio, bool notifyComplete);

    void signalTimeDiscontinuity();

    void signalAudioSinkChanged();

    void signalDisableOffloadAudio();
    void signalEnableOffloadAudio();

    void pause();
    void resume();

    void setVideoFrameRate(float fps);

    status_t getCurrentPosition(int64_t *mediaUs);
    int64_t getVideoLateByUs();

    status_t openAudioSink(
            const sp<AMessage> &format,
            bool offloadOnly,
            bool hasVideo,
            uint32_t flags,
            bool *isOffloaded);
    void closeAudioSink();

    enum {
        kWhatEOS                      = 'eos ',
        kWhatFlushComplete            = 'fluC',
        kWhatPosition                 = 'posi',
        kWhatVideoRenderingStart      = 'vdrd',
        kWhatMediaRenderingStart      = 'mdrd',
        kWhatAudioTearDown            = 'adTD',
        kWhatAudioOffloadPauseTimeout = 'aOPT',
    };

    enum AudioTearDownReason {
        kDueToError = 0,   // Could restart with either offload or non-offload.
        kDueToTimeout,
        kForceNonOffload,  // Restart only with non-offload.
    };

#ifdef RTK_PLATFORM
    int32_t setSelectTrackFlg(bool Flag);
#endif

protected:
    virtual ~Renderer();

    virtual void onMessageReceived(const sp<AMessage> &msg);

private:
    enum {
        kWhatDrainAudioQueue     = 'draA',
        kWhatDrainVideoQueue     = 'draV',
        kWhatPostDrainVideoQueue = 'pDVQ',
        kWhatQueueBuffer         = 'queB',
        kWhatQueueEOS            = 'qEOS',
        kWhatConfigPlayback      = 'cfPB',
        kWhatConfigSync          = 'cfSy',
        kWhatGetPlaybackSettings = 'gPbS',
        kWhatGetSyncSettings     = 'gSyS',
        kWhatFlush               = 'flus',
        kWhatPause               = 'paus',
        kWhatResume              = 'resm',
        kWhatOpenAudioSink       = 'opnA',
        kWhatCloseAudioSink      = 'clsA',
        kWhatStopAudioSink       = 'stpA',
        kWhatDisableOffloadAudio = 'noOA',
        kWhatEnableOffloadAudio  = 'enOA',
        kWhatSetVideoFrameRate   = 'sVFR',
    };

    struct QueueEntry {
        sp<ABuffer> mBuffer;
        sp<AMessage> mNotifyConsumed;
        size_t mOffset;
        status_t mFinalResult;
        int32_t mBufferOrdinal;
    };

    static const int64_t kMinPositionUpdateDelayUs;

    sp<MediaPlayerBase::AudioSink> mAudioSink;
    bool mUseVirtualAudioSink;
    sp<AMessage> mNotify;
    Mutex mLock;
    uint32_t mFlags;
    List<QueueEntry> mAudioQueue;
    List<QueueEntry> mVideoQueue;
#ifdef RTK_PLATFORM
    uint64_t mNumFramesWritten;
#else
    uint32_t mNumFramesWritten;
#endif
    uint32_t mVideoLateCount;
    sp<VideoFrameScheduler> mVideoScheduler;

    bool mDrainAudioQueuePending;
    bool mDrainVideoQueuePending;
    int32_t mAudioQueueGeneration;
    int32_t mVideoQueueGeneration;
    int32_t mAudioDrainGeneration;
    int32_t mVideoDrainGeneration;
    int32_t mAudioEOSGeneration;

    sp<MediaClock> mMediaClock;
    float mPlaybackRate; // audio track rate

    AudioPlaybackRate mPlaybackSettings;
    AVSyncSettings mSyncSettings;
    float mVideoFpsHint;

    int64_t mAudioFirstAnchorTimeMediaUs;
    int64_t mAnchorTimeMediaUs;
    int64_t mAnchorNumFramesWritten;
    int64_t mVideoLateByUs;
#ifdef RTK_PLATFORM
    int64_t mPreVideoLateByUs;
    int64_t Diff(int64_t a , int64_t b);
#ifdef RTK_ENB_QUEUE_DEBUG_LOG
    int64_t mAudioTimeUs;
    int64_t mVideoTimeUs;
    struct DeBugQueueEntry {
        bool bIsStoreByAudio;
        bool bIsVideoLate;
        bool bIsVideoJump;
        int64_t sAnchorTimeMediaUs;
        int64_t sAudmediaTimeUs;
        int64_t sVidmediaTimeUs;
        int64_t sVidrealTimeUs;
        int64_t snowUs;
        int64_t sPendingAudioPlayoutDurationUs;

        //clock info
        int64_t sClkAnchorTimeMediaUs;
        int64_t sClkAnchorTimeRealUs;
        int64_t sClkMaxTimeMediaUs;
        int64_t sClkStartingTimeMediaUs;
    };

    List<DeBugQueueEntry> mDBGInfoQueue;
    void POPQueueDBGInfo();
    void QueueDBGInfo(bool bIsStoreByAudio, bool bIsVideoLate, bool bIsVideoJump, int64_t realTimeUs, int64_t nowUs, int64_t PendingAudDuraUs);
#endif
    int64_t mSelectTrackAnchorTimeUs;
    bool mSelectTrackEnb;
    bool mVideoDropFlg;
    bool mAudioDropFlg;
#endif
    bool mHasAudio;
    bool mHasVideo;

    bool mNotifyCompleteAudio;
    bool mNotifyCompleteVideo;

    bool mSyncQueues;

    // modified on only renderer's thread.
    bool mPaused;
    int64_t mPauseDrainAudioAllowedUs; // time when we can drain/deliver audio in pause mode.

#ifdef RTK_PLATFORM
    bool mAudioEOS;
    bool mWaitAudioSink;
#endif
    bool mVideoSampleReceived;
    bool mVideoRenderingStarted;
    int32_t mVideoRenderingStartGeneration;
    int32_t mAudioRenderingStartGeneration;
    bool mRenderingDataDelivered;

    int64_t mNextAudioClockUpdateTimeUs;
    // the media timestamp of last audio sample right before EOS.
    int64_t mLastAudioMediaTimeUs;

    int32_t mAudioOffloadPauseTimeoutGeneration;
    bool mAudioTornDown;
    audio_offload_info_t mCurrentOffloadInfo;

    struct PcmInfo {
        audio_channel_mask_t mChannelMask;
        audio_output_flags_t mFlags;
        audio_format_t mFormat;
        int32_t mNumChannels;
        int32_t mSampleRate;
    };
    PcmInfo mCurrentPcmInfo;
    static const PcmInfo AUDIO_PCMINFO_INITIALIZER;

    int32_t mTotalBuffersQueued;
    int32_t mLastAudioBufferDrained;
    bool mUseAudioCallback;

    sp<AWakeLock> mWakeLock;

    status_t getCurrentPositionOnLooper(int64_t *mediaUs);
    status_t getCurrentPositionOnLooper(
            int64_t *mediaUs, int64_t nowUs, bool allowPastQueuedVideo = false);
    bool getCurrentPositionIfPaused_l(int64_t *mediaUs);
    status_t getCurrentPositionFromAnchor(
            int64_t *mediaUs, int64_t nowUs, bool allowPastQueuedVideo = false);

    void notifyEOSCallback();
    size_t fillAudioBuffer(void *buffer, size_t size);

    bool onDrainAudioQueue();
    void drainAudioQueueUntilLastEOS();
    int64_t getPendingAudioPlayoutDurationUs(int64_t nowUs);
    void postDrainAudioQueue_l(int64_t delayUs = 0);

    void clearAnchorTime_l();
    void clearAudioFirstAnchorTime_l();
    void setAudioFirstAnchorTimeIfNeeded_l(int64_t mediaUs);
    void setVideoLateByUs(int64_t lateUs);

    void onNewAudioMediaTime(int64_t mediaTimeUs);
    int64_t getRealTimeUs(int64_t mediaTimeUs, int64_t nowUs);

    void onDrainVideoQueue();
    void postDrainVideoQueue();

    void prepareForMediaRenderingStart_l();
    void notifyIfMediaRenderingStarted_l();

    void onQueueBuffer(const sp<AMessage> &msg);
    void onQueueEOS(const sp<AMessage> &msg);
    void onFlush(const sp<AMessage> &msg);
    void onAudioSinkChanged();
    void onDisableOffloadAudio();
    void onEnableOffloadAudio();
    status_t onConfigPlayback(const AudioPlaybackRate &rate /* sanitized */);
    status_t onGetPlaybackSettings(AudioPlaybackRate *rate /* nonnull */);
    status_t onConfigSync(const AVSyncSettings &sync, float videoFpsHint);
    status_t onGetSyncSettings(AVSyncSettings *sync /* nonnull */, float *videoFps /* nonnull */);

    void onPause();
    void onResume();
    void onSetVideoFrameRate(float fps);
    int32_t getQueueGeneration(bool audio);
    int32_t getDrainGeneration(bool audio);
    bool getSyncQueues();
    void onAudioTearDown(AudioTearDownReason reason);
    status_t onOpenAudioSink(
            const sp<AMessage> &format,
            bool offloadOnly,
            bool hasVideo,
            uint32_t flags);
    void onCloseAudioSink();

    void notifyEOS(bool audio, status_t finalResult, int64_t delayUs = 0);
    void notifyFlushComplete(bool audio);
    void notifyPosition();
    void notifyVideoLateBy(int64_t lateByUs);
    void notifyVideoRenderingStart();
    void notifyAudioTearDown(AudioTearDownReason reason);

    void flushQueue(List<QueueEntry> *queue);
    bool dropBufferIfStale(bool audio, const sp<AMessage> &msg);
    void syncQueuesDone_l();

    bool offloadingAudio() const { return (mFlags & FLAG_OFFLOAD_AUDIO) != 0; }

    void startAudioOffloadPauseTimeout();
    void cancelAudioOffloadPauseTimeout();

#ifdef RTK_PLATFORM
    int64_t getDurationUsIfPlayedAtSampleRate(uint64_t numFrames);
#else
    int64_t getDurationUsIfPlayedAtSampleRate(uint32_t numFrames);
#endif

    DISALLOW_EVIL_CONSTRUCTORS(Renderer);
};

} // namespace android

#endif  // NUPLAYER_RENDERER_H_
