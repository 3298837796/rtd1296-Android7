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

#ifndef SAMPLE_ITERATOR_H_

#define SAMPLE_ITERATOR_H_

#include <utils/Vector.h>

namespace android {

class SampleTable;

struct SampleIterator {
    SampleIterator(SampleTable *table);

    status_t seekTo(uint32_t sampleIndex);

    uint32_t getChunkIndex() const { return mCurrentChunkIndex; }
    uint32_t getDescIndex() const { return mChunkDesc; }
    off64_t getSampleOffset() const { return mCurrentSampleOffset; }
    size_t getSampleSize() const { return mCurrentSampleSize; }
#ifdef RTK_PLATFORM
    uint64_t getSampleTime() const { return mCurrentSampleTime; }
    uint64_t getSampleDuration() const { return mCurrentSampleDuration; }
#else
    uint32_t getSampleTime() const { return mCurrentSampleTime; }
    uint32_t getSampleDuration() const { return mCurrentSampleDuration; }
#endif

    status_t getSampleSizeDirect(
            uint32_t sampleIndex, size_t *size);

#ifdef RTK_PLATFORM
    status_t getChunkOffset(uint32_t chunk, off64_t *offset);
#endif

private:
    SampleTable *mTable;

    bool mInitialized;

    uint32_t mSampleToChunkIndex;
    uint32_t mFirstChunk;
    uint32_t mFirstChunkSampleIndex;
    uint32_t mStopChunk;
    uint32_t mStopChunkSampleIndex;
    uint32_t mSamplesPerChunk;
    uint32_t mChunkDesc;

    uint32_t mCurrentChunkIndex;
    off64_t mCurrentChunkOffset;
    Vector<size_t> mCurrentChunkSampleSizes;

    uint32_t mTimeToSampleIndex;
    uint32_t mTTSSampleIndex;
#ifdef RTK_PLATFORM
    uint64_t mTTSSampleTime;
#else
    uint32_t mTTSSampleTime;
#endif
    uint32_t mTTSCount;
#ifdef RTK_PLATFORM
    uint64_t mTTSDuration;
#else
    uint32_t mTTSDuration;
#endif

    uint32_t mCurrentSampleIndex;
    off64_t mCurrentSampleOffset;
    size_t mCurrentSampleSize;
#ifdef RTK_PLATFORM
    uint64_t mCurrentSampleTime;
    uint64_t mCurrentSampleDuration;
#else
    uint32_t mCurrentSampleTime;
    uint32_t mCurrentSampleDuration;
#endif

    void reset();
    status_t findChunkRange(uint32_t sampleIndex);
#ifndef RTK_PLATFORM
    status_t getChunkOffset(uint32_t chunk, off64_t *offset);
#endif
#ifdef RTK_PLATFORM
    status_t findSampleTimeAndDuration(uint32_t sampleIndex, uint64_t *time, uint64_t *duration);
#else
    status_t findSampleTimeAndDuration(uint32_t sampleIndex, uint32_t *time, uint32_t *duration);
#endif

    SampleIterator(const SampleIterator &);
    SampleIterator &operator=(const SampleIterator &);
};

}  // namespace android

#endif  // SAMPLE_ITERATOR_H_
