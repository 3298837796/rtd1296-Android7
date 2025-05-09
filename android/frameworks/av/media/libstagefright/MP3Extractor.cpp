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

//#define LOG_NDEBUG 0
#define LOG_TAG "MP3Extractor"
#include <utils/Log.h>

#include "include/MP3Extractor.h"

#include "include/avc_utils.h"
#include "include/ID3.h"
#include "include/VBRISeeker.h"
#include "include/XINGSeeker.h"

#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaBufferGroup.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/Utils.h>
#include <utils/String8.h>

//rtk add
#include <inttypes.h>
#define FILE_SIZE_THRESHOLD 20*1024*1024
#define VBR_CHECK_COUNT     50
//rtk end
namespace android {

// Everything must match except for
// protection, bitrate, padding, private bits, mode, mode extension,
// copyright bit, original bit and emphasis.
// Yes ... there are things that must indeed match...
static const uint32_t kMask = 0xfffe0c00;

static bool Resync(
        const sp<DataSource> &source, uint32_t match_header,
        off64_t *inout_pos, off64_t *post_id3_pos, uint32_t *out_header) {
    if (post_id3_pos != NULL) {
        *post_id3_pos = 0;
    }

    if (*inout_pos == 0) {
        // Skip an optional ID3 header if syncing at the very beginning
        // of the datasource.

        for (;;) {
            uint8_t id3header[10];
            if (source->readAt(*inout_pos, id3header, sizeof(id3header))
                    < (ssize_t)sizeof(id3header)) {
                // If we can't even read these 10 bytes, we might as well bail
                // out, even if there _were_ 10 bytes of valid mp3 audio data...
                return false;
            }

            if (memcmp("ID3", id3header, 3)) {
                break;
            }

            // Skip the ID3v2 header.

            size_t len =
                ((id3header[6] & 0x7f) << 21)
                | ((id3header[7] & 0x7f) << 14)
                | ((id3header[8] & 0x7f) << 7)
                | (id3header[9] & 0x7f);

            len += 10;

            *inout_pos += len;

            ALOGV("skipped ID3 tag, new starting offset is %lld (0x%016llx)",
                    (long long)*inout_pos, (long long)*inout_pos);
        }

        if (post_id3_pos != NULL) {
            *post_id3_pos = *inout_pos;
        }
    }

    off64_t pos = *inout_pos;
    bool valid = false;

    const size_t kMaxReadBytes = 1024;
    const size_t kMaxBytesChecked = 128 * 1024;
    uint8_t buf[kMaxReadBytes];
    ssize_t bytesToRead = kMaxReadBytes;
    ssize_t totalBytesRead = 0;
    ssize_t remainingBytes = 0;
    bool reachEOS = false;
    uint8_t *tmp = buf;

    do {
        if (pos >= (off64_t)(*inout_pos + kMaxBytesChecked)) {
            // Don't scan forever.
            ALOGV("giving up at offset %lld", (long long)pos);
            break;
        }

        if (remainingBytes < 4) {
            if (reachEOS) {
                break;
            } else {
                memcpy(buf, tmp, remainingBytes);
                bytesToRead = kMaxReadBytes - remainingBytes;

                /*
                 * The next read position should start from the end of
                 * the last buffer, and thus should include the remaining
                 * bytes in the buffer.
                 */
                totalBytesRead = source->readAt(pos + remainingBytes,
                                                buf + remainingBytes,
                                                bytesToRead);
                if (totalBytesRead <= 0) {
                    break;
                }
                reachEOS = (totalBytesRead != bytesToRead);
                totalBytesRead += remainingBytes;
                remainingBytes = totalBytesRead;
                tmp = buf;
                continue;
            }
        }

        uint32_t header = U32_AT(tmp);

        if (match_header != 0 && (header & kMask) != (match_header & kMask)) {
            ++pos;
            ++tmp;
            --remainingBytes;
            continue;
        }

        size_t frame_size;
        int sample_rate, num_channels, bitrate;
        if (!GetMPEGAudioFrameSize(
                    header, &frame_size,
                    &sample_rate, &num_channels, &bitrate)) {
            ++pos;
            ++tmp;
            --remainingBytes;
            continue;
        }

        ALOGV("found possible 1st frame at %lld (header = 0x%08x)", (long long)pos, header);

        // We found what looks like a valid frame,
        // now find its successors.

        off64_t test_pos = pos + frame_size;

        valid = true;
        for (int j = 0; j < 3; ++j) {
            uint8_t tmp[4];
            if (source->readAt(test_pos, tmp, 4) < 4) {
                valid = false;
                break;
            }

            uint32_t test_header = U32_AT(tmp);

            ALOGV("subsequent header is %08x", test_header);

            if ((test_header & kMask) != (header & kMask)) {
                valid = false;
                break;
            }

            size_t test_frame_size;
            if (!GetMPEGAudioFrameSize(
                        test_header, &test_frame_size)) {
                valid = false;
                break;
            }

            ALOGV("found subsequent frame #%d at %lld", j + 2, (long long)test_pos);

            test_pos += test_frame_size;
        }

        if (valid) {
            *inout_pos = pos;

            if (out_header != NULL) {
                *out_header = header;
            }
        } else {
            ALOGV("no dice, no valid sequence of frames found.");
        }

        ++pos;
        ++tmp;
        --remainingBytes;
    } while (!valid);

    return valid;
}

class MP3Source : public MediaSource {
public:
#if 1 //rtk add
    MP3Source(
            const sp<MetaData> &meta, const sp<DataSource> &source,
            off64_t first_frame_pos, uint32_t fixed_header,
            const sp<MP3Seeker> &seeker, bool seekable);
#else
    MP3Source(
            const sp<MetaData> &meta, const sp<DataSource> &source,
            off64_t first_frame_pos, uint32_t fixed_header,
            const sp<MP3Seeker> &seeker);
#endif //rtk end

    virtual status_t start(MetaData *params = NULL);
    virtual status_t stop();

    virtual sp<MetaData> getFormat();

    virtual status_t read(
            MediaBuffer **buffer, const ReadOptions *options = NULL);

protected:
    virtual ~MP3Source();

private:
    static const size_t kMaxFrameSize;
    sp<MetaData> mMeta;
    sp<DataSource> mDataSource;
    off64_t mFirstFramePos;
    uint32_t mFixedHeader;
    off64_t mCurrentPos;
    int64_t mCurrentTimeUs;
    bool mStarted;
    sp<MP3Seeker> mSeeker;
    MediaBufferGroup *mGroup;

    int64_t mBasisTimeUs;
    int64_t mSamplesRead;
//rtk add
    bool mCanSeek;
//rtk end

    MP3Source(const MP3Source &);
    MP3Source &operator=(const MP3Source &);
};

MP3Extractor::MP3Extractor(
        const sp<DataSource> &source, const sp<AMessage> &meta)
    : mInitCheck(NO_INIT),
      mDataSource(source),
      mFirstFramePos(-1),
      mFixedHeader(0) {

    off64_t pos = 0;
    off64_t post_id3_pos;
    uint32_t header;
    bool success;
//rtk add
    int frame_count = 0;
//rtk end

    int64_t meta_offset;
    uint32_t meta_header;
    int64_t meta_post_id3_offset;
    if (meta != NULL
            && meta->findInt64("offset", &meta_offset)
            && meta->findInt32("header", (int32_t *)&meta_header)
            && meta->findInt64("post-id3-offset", &meta_post_id3_offset)) {
        // The sniffer has already done all the hard work for us, simply
        // accept its judgement.
        pos = (off64_t)meta_offset;
        header = meta_header;
        post_id3_pos = (off64_t)meta_post_id3_offset;

        success = true;
    } else {
        success = Resync(mDataSource, 0, &pos, &post_id3_pos, &header);
    }

    if (!success) {
        // mInitCheck will remain NO_INIT
        return;
    }

    mFirstFramePos = pos;
    mFixedHeader = header;
    mMeta = new MetaData;
    sp<XINGSeeker> seeker = XINGSeeker::CreateFromSource(mDataSource, mFirstFramePos);

    if (seeker == NULL) {
        mSeeker = VBRISeeker::CreateFromSource(mDataSource, post_id3_pos);
    } else {
        mSeeker = seeker;
        int encd = seeker->getEncoderDelay();
        int encp = seeker->getEncoderPadding();
        if (encd != 0 || encp != 0) {
            mMeta->setInt32(kKeyEncoderDelay, encd);
            mMeta->setInt32(kKeyEncoderPadding, encp);
        }
    }

    if (mSeeker != NULL) {
        // While it is safe to send the XING/VBRI frame to the decoder, this will
        // result in an extra 1152 samples being output. In addition, the bitrate
        // of the Xing header might not match the rest of the file, which could
        // lead to problems when seeking. The real first frame to decode is after
        // the XING/VBRI frame, so skip there.
        size_t frame_size;
        int sample_rate;
        int num_channels;
        int bitrate;
        GetMPEGAudioFrameSize(
                header, &frame_size, &sample_rate, &num_channels, &bitrate);
        pos += frame_size;
        if (!Resync(mDataSource, 0, &pos, &post_id3_pos, &header)) {
            // mInitCheck will remain NO_INIT
            return;
        }
        mFirstFramePos = pos;
        mFixedHeader = header;
//rtk add
    } else {
        size_t frame_size;
        int sample_rate;
        int num_channels;
        int bitrate;
        off64_t pos_cont = pos;
        off64_t fileSize;
        if (mDataSource->getSize(&fileSize) == OK && fileSize < FILE_SIZE_THRESHOLD) {
            int bitrate_max = 0;
            int bitrate_min = INT_MAX;
            while (pos_cont < fileSize) {
                if (!Resync(mDataSource, 0, &pos_cont, &post_id3_pos, &header)) {
                    //ALOGD(" end of file frame_count %d pos_cont %" PRId64 " file size %" PRId64 "", frame_count, pos_cont, fileSize);
                    break;
                }
                GetMPEGAudioFrameSize(
                        header, &frame_size, &sample_rate, &num_channels, &bitrate);
                if (bitrate > bitrate_max)
                    bitrate_max = bitrate;
                else if (bitrate < bitrate_min && bitrate != 0)
                    bitrate_min = bitrate;

                //Only check the first 50 frames, if this is CBR file to stop counting.
                if (frame_count > VBR_CHECK_COUNT && bitrate_max == bitrate_min) {
                    frame_count = 0;
                    break;
                }
                pos_cont += frame_size;
                frame_count++;
            }
        }

        if (!Resync(mDataSource, 0, &pos, &post_id3_pos, &header)) {
            // mInitCheck will remain NO_INIT
            return;
        }
//rtk end
    }

    size_t frame_size;
    int sample_rate;
    int num_channels;
    int bitrate;
    GetMPEGAudioFrameSize(
            header, &frame_size, &sample_rate, &num_channels, &bitrate);

    unsigned layer = 4 - ((header >> 17) & 3);

    switch (layer) {
        case 1:
            mMeta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_MPEG_LAYER_I);
            break;
        case 2:
            mMeta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_MPEG_LAYER_II);
            break;
        case 3:
            mMeta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_MPEG);
            break;
        default:
            TRESPASS();
    }

    mMeta->setInt32(kKeySampleRate, sample_rate);
    mMeta->setInt32(kKeyBitRate, bitrate * 1000);
    mMeta->setInt32(kKeyChannelCount, num_channels);

    int64_t durationUs;

    if (mSeeker == NULL || !mSeeker->getDuration(&durationUs)) {
        off64_t fileSize;
        if (mDataSource->getSize(&fileSize) == OK) {
            off64_t dataLength = fileSize - mFirstFramePos;
            if (dataLength > INT64_MAX / 8000LL) {
                // duration would overflow
                durationUs = INT64_MAX;
            } else {
                durationUs = 8000LL * dataLength / bitrate;
            }
        } else {
            durationUs = -1;
        }
    }

//rtk add
    if (frame_count > 0) {
        durationUs = frame_count * 1000000ll * (sample_rate >= 32000 ? 1152 : 576) / sample_rate;
        //mMeta->setInt32(kKeyBitRate, 0);
        isSeekable = false;
        ALOGD("Disable SEEK!!!This is VBR file w/o VBRI/XING Header frame_count %d durationUs %" PRId64 "", frame_count, durationUs);
    }
    else
        isSeekable = true;
//rtk end
    if (durationUs >= 0) {
        mMeta->setInt64(kKeyDuration, durationUs);
    }

    mInitCheck = OK;

    // Get iTunes-style gapless info if present.
    // When getting the id3 tag, skip the V1 tags to prevent the source cache
    // from being iterated to the end of the file.
    ID3 id3(mDataSource, true);
    if (id3.isValid()) {
        ID3::Iterator *com = new ID3::Iterator(id3, "COM");
        if (com->done()) {
            delete com;
            com = new ID3::Iterator(id3, "COMM");
        }
        while(!com->done()) {
            String8 commentdesc;
            String8 commentvalue;
            com->getString(&commentdesc, &commentvalue);
            const char * desc = commentdesc.string();
            const char * value = commentvalue.string();

            // first 3 characters are the language, which we don't care about
            if(strlen(desc) > 3 && strcmp(desc + 3, "iTunSMPB") == 0) {

                int32_t delay, padding;
                if (sscanf(value, " %*x %x %x %*x", &delay, &padding) == 2) {
                    mMeta->setInt32(kKeyEncoderDelay, delay);
                    mMeta->setInt32(kKeyEncoderPadding, padding);
                }
                break;
            }
            com->next();
        }
        delete com;
        com = NULL;
    }
}

size_t MP3Extractor::countTracks() {
    return mInitCheck != OK ? 0 : 1;
}

sp<IMediaSource> MP3Extractor::getTrack(size_t index) {
    if (mInitCheck != OK || index != 0) {
        return NULL;
    }

#if 1 //rtk add
    return new MP3Source(
            mMeta, mDataSource, mFirstFramePos, mFixedHeader,
            mSeeker, isSeekable);
#else
    return new MP3Source(
            mMeta, mDataSource, mFirstFramePos, mFixedHeader,
            mSeeker);
#endif //rtk end
}

sp<MetaData> MP3Extractor::getTrackMetaData(
        size_t index, uint32_t /* flags */) {
    if (mInitCheck != OK || index != 0) {
        return NULL;
    }

    return mMeta;
}

////////////////////////////////////////////////////////////////////////////////

// The theoretical maximum frame size for an MPEG audio stream should occur
// while playing a Layer 2, MPEGv2.5 audio stream at 160kbps (with padding).
// The size of this frame should be...
// ((1152 samples/frame * 160000 bits/sec) /
//  (8000 samples/sec * 8 bits/byte)) + 1 padding byte/frame = 2881 bytes/frame.
// Set our max frame size to the nearest power of 2 above this size (aka, 4kB)
const size_t MP3Source::kMaxFrameSize = (1 << 12); /* 4096 bytes */
#if 1 //rtk add
MP3Source::MP3Source(
        const sp<MetaData> &meta, const sp<DataSource> &source,
        off64_t first_frame_pos, uint32_t fixed_header,
        const sp<MP3Seeker> &seeker, bool seekable)
#else
MP3Source::MP3Source(
        const sp<MetaData> &meta, const sp<DataSource> &source,
        off64_t first_frame_pos, uint32_t fixed_header,
        const sp<MP3Seeker> &seeker)
#endif //rtk end
    : mMeta(meta),
      mDataSource(source),
      mFirstFramePos(first_frame_pos),
      mFixedHeader(fixed_header),
      mCurrentPos(0),
      mCurrentTimeUs(0),
      mStarted(false),
      mSeeker(seeker),
      mGroup(NULL),
      mBasisTimeUs(0),
#if 1 //rtk add
      mSamplesRead(0),
      mCanSeek(seekable) {
#else
      mSamplesRead(0) {
#endif //rtk end
}

MP3Source::~MP3Source() {
    if (mStarted) {
        stop();
    }
}

status_t MP3Source::start(MetaData *) {
    CHECK(!mStarted);

    mGroup = new MediaBufferGroup;

    mGroup->add_buffer(new MediaBuffer(kMaxFrameSize));

    mCurrentPos = mFirstFramePos;
    mCurrentTimeUs = 0;

    mBasisTimeUs = mCurrentTimeUs;
    mSamplesRead = 0;

    mStarted = true;

    return OK;
}

status_t MP3Source::stop() {
    CHECK(mStarted);

    delete mGroup;
    mGroup = NULL;

    mStarted = false;

    return OK;
}

sp<MetaData> MP3Source::getFormat() {
    return mMeta;
}

status_t MP3Source::read(
        MediaBuffer **out, const ReadOptions *options) {
    *out = NULL;

    int64_t seekTimeUs;
    ReadOptions::SeekMode mode;
    bool seekCBR = false;
    //rtk add
    int bitrate_chk;
    mMeta->findInt32(kKeyBitRate, &bitrate_chk);
    if (options != NULL && options->getSeekTo(&seekTimeUs, &mode) && bitrate_chk && mCanSeek) {
    //rtd end
    //if (options != NULL && options->getSeekTo(&seekTimeUs, &mode) && bitrate_chk) {
        int64_t actualSeekTimeUs = seekTimeUs;
        if (mSeeker == NULL
                || !mSeeker->getOffsetForTime(&actualSeekTimeUs, &mCurrentPos)) {
            int32_t bitrate;
            if (!mMeta->findInt32(kKeyBitRate, &bitrate)) {
                // bitrate is in bits/sec.
                ALOGI("no bitrate");

                return ERROR_UNSUPPORTED;
            }

            mCurrentTimeUs = seekTimeUs;
            mCurrentPos = mFirstFramePos + seekTimeUs * bitrate / 8000000;
            seekCBR = true;
        } else {
            mCurrentTimeUs = actualSeekTimeUs;
        }

        mBasisTimeUs = mCurrentTimeUs;
        mSamplesRead = 0;
    }

    MediaBuffer *buffer;
    status_t err = mGroup->acquire_buffer(&buffer);
    if (err != OK) {
        return err;
    }

    size_t frame_size;
    int bitrate;
    int num_samples;
    int sample_rate;
    for (;;) {
        ssize_t n = mDataSource->readAt(mCurrentPos, buffer->data(), 4);
        if (n < 4) {
            buffer->release();
            buffer = NULL;

            return ERROR_END_OF_STREAM;
        }

        uint32_t header = U32_AT((const uint8_t *)buffer->data());

        if ((header & kMask) == (mFixedHeader & kMask)
            && GetMPEGAudioFrameSize(
                header, &frame_size, &sample_rate, NULL,
                &bitrate, &num_samples)) {

            // re-calculate mCurrentTimeUs because we might have called Resync()
            if (seekCBR) {
                mCurrentTimeUs = (mCurrentPos - mFirstFramePos) * 8000 / bitrate;
                mBasisTimeUs = mCurrentTimeUs;
            }

            break;
        }

        // Lost sync.
        ALOGV("lost sync! header = 0x%08x, old header = 0x%08x\n", header, mFixedHeader);

        off64_t pos = mCurrentPos;
        if (!Resync(mDataSource, mFixedHeader, &pos, NULL, NULL)) {
            ALOGE("Unable to resync. Signalling end of stream.");

            buffer->release();
            buffer = NULL;

            return ERROR_END_OF_STREAM;
        }

        mCurrentPos = pos;

        // Try again with the new position.
    }

    CHECK(frame_size <= buffer->size());

    ssize_t n = mDataSource->readAt(mCurrentPos, buffer->data(), frame_size);
    if (n < (ssize_t)frame_size) {
        buffer->release();
        buffer = NULL;

        return ERROR_END_OF_STREAM;
    }

    buffer->set_range(0, frame_size);

    buffer->meta_data()->setInt64(kKeyTime, mCurrentTimeUs);
    buffer->meta_data()->setInt32(kKeyIsSyncFrame, 1);

    mCurrentPos += frame_size;

    mSamplesRead += num_samples;
    mCurrentTimeUs = mBasisTimeUs + ((mSamplesRead * 1000000) / sample_rate);

    *out = buffer;

    return OK;
}

sp<MetaData> MP3Extractor::getMetaData() {
    sp<MetaData> meta = new MetaData;

    if (mInitCheck != OK) {
        return meta;
    }

    meta->setCString(kKeyMIMEType, "audio/mpeg");

    ID3 id3(mDataSource);

    if (!id3.isValid()) {
        return meta;
    }

    struct Map {
        int key;
        const char *tag1;
        const char *tag2;
    };
    static const Map kMap[] = {
        { kKeyAlbum, "TALB", "TAL" },
        { kKeyArtist, "TPE1", "TP1" },
        { kKeyAlbumArtist, "TPE2", "TP2" },
        { kKeyComposer, "TCOM", "TCM" },
        { kKeyGenre, "TCON", "TCO" },
        { kKeyTitle, "TIT2", "TT2" },
        { kKeyYear, "TYE", "TYER" },
        { kKeyAuthor, "TXT", "TEXT" },
        { kKeyCDTrackNumber, "TRK", "TRCK" },
        { kKeyDiscNumber, "TPA", "TPOS" },
        { kKeyCompilation, "TCP", "TCMP" },
    };
    static const size_t kNumMapEntries = sizeof(kMap) / sizeof(kMap[0]);

    for (size_t i = 0; i < kNumMapEntries; ++i) {
        ID3::Iterator *it = new ID3::Iterator(id3, kMap[i].tag1);
        if (it->done()) {
            delete it;
            it = new ID3::Iterator(id3, kMap[i].tag2);
        }

        if (it->done()) {
            delete it;
            continue;
        }

        String8 s;
        it->getString(&s);
        delete it;

        meta->setCString(kMap[i].key, s);
    }

    size_t dataSize;
    String8 mime;
    const void *data = id3.getAlbumArt(&dataSize, &mime);

    if (data) {
        meta->setData(kKeyAlbumArt, MetaData::TYPE_NONE, data, dataSize);
        meta->setCString(kKeyAlbumArtMIME, mime.string());
    }

    return meta;
}

bool SniffMP3(
        const sp<DataSource> &source, String8 *mimeType,
        float *confidence, sp<AMessage> *meta) {
    off64_t pos = 0;
    off64_t post_id3_pos;
    uint32_t header;
    if (!Resync(source, 0, &pos, &post_id3_pos, &header)) {
        return false;
    }

    *meta = new AMessage;
    (*meta)->setInt64("offset", pos);
    (*meta)->setInt32("header", header);
    (*meta)->setInt64("post-id3-offset", post_id3_pos);

    *mimeType = MEDIA_MIMETYPE_AUDIO_MPEG;
    *confidence = 0.2f;

    return true;
}

}  // namespace android
