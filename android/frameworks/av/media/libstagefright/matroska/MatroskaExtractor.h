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

#ifndef MATROSKA_EXTRACTOR_H_

#define MATROSKA_EXTRACTOR_H_

#include "mkvparser/mkvparser.h"

#include <media/stagefright/MediaExtractor.h>
#include <utils/Vector.h>
#include <utils/threads.h>

namespace android {

struct AMessage;
class String8;

class MetaData;
struct DataSourceReader;
struct MatroskaSource;

struct MatroskaExtractor : public MediaExtractor {
    MatroskaExtractor(const sp<DataSource> &source);

    virtual size_t countTracks();

    virtual sp<IMediaSource> getTrack(size_t index);

    virtual sp<MetaData> getTrackMetaData(
            size_t index, uint32_t flags);

    virtual sp<MetaData> getMetaData();

    virtual uint32_t flags() const;

    virtual const char * name() { return "MatroskaExtractor"; }

protected:
    virtual ~MatroskaExtractor();

private:
    friend struct MatroskaSource;
    friend struct BlockIterator;

    struct TrackInfo {
        unsigned long mTrackNum;
        bool mEncrypted;
        sp<MetaData> mMeta;
        const MatroskaExtractor *mExtractor;
        Vector<const mkvparser::CuePoint*> mCuePoints;

        const mkvparser::Track* getTrack() const;
        const mkvparser::CuePoint::TrackPosition *find(long long timeNs) const;
#ifdef RTK_PLATFORM
        int mContentCompSettingLen;
#endif
    };
#ifdef RTK_PLATFORM
    typedef struct {
        uint32_t   biSize;
        uint32_t   biWidth;
        uint32_t   biHeight;
        uint16_t   biPlanes;
        uint16_t   biBitCount;
        uint32_t   biCompression;
        uint32_t   biSizeImage;
        uint32_t   biXPelsPerMeter;
        uint32_t   biYPelsPerMeter;
        uint32_t   biClrUsed;
        uint32_t   biClrImportant;
    } BITMAPINFOHEADER, *PBITMAPINFOHEADER, *LPBITMAPINFOHEADER;

    bool mIsVFWFOURCC;
#endif

    Mutex mLock;
    Vector<TrackInfo> mTracks;

    sp<DataSource> mDataSource;
    DataSourceReader *mReader;
    mkvparser::Segment *mSegment;
    bool mExtractedThumbnails;
    bool mIsLiveStreaming;
    bool mIsWebm;
    int64_t mSeekPreRollNs;

    status_t synthesizeAVCC(TrackInfo *trackInfo, size_t index);
    void addTracks();
    void findThumbnails();
    void getColorInformation(const mkvparser::VideoTrack *vtrack, sp<MetaData> &meta);
    bool isLiveStreaming() const;

    MatroskaExtractor(const MatroskaExtractor &);
    MatroskaExtractor &operator=(const MatroskaExtractor &);
};

bool SniffMatroska(
        const sp<DataSource> &source, String8 *mimeType, float *confidence,
        sp<AMessage> *);

}  // namespace android

#endif  // MATROSKA_EXTRACTOR_H_
