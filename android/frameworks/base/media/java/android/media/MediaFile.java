/*
 * Copyright (C) 2007 The Android Open Source Project
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

package android.media;

import android.media.DecoderCapabilities;
import android.media.DecoderCapabilities.VideoDecoder;
import android.media.DecoderCapabilities.AudioDecoder;
import android.mtp.MtpConstants;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;

//[RTK Begin]
import java.io.File;
//[RTK End]
/**
 * MediaScanner helper class.
 *
 * {@hide}
 */
public class MediaFile {

    // Audio file types
    public static final int FILE_TYPE_MP3     = 1;
    public static final int FILE_TYPE_M4A     = 2;
    public static final int FILE_TYPE_WAV     = 3;
    public static final int FILE_TYPE_AMR     = 4;
    public static final int FILE_TYPE_AWB     = 5;
    public static final int FILE_TYPE_WMA     = 6;
    public static final int FILE_TYPE_OGG     = 7;
    public static final int FILE_TYPE_AAC     = 8;
    public static final int FILE_TYPE_MKA     = 9;
    public static final int FILE_TYPE_FLAC    = 10;
    private static final int FIRST_AUDIO_FILE_TYPE = FILE_TYPE_MP3;
    private static final int LAST_AUDIO_FILE_TYPE = FILE_TYPE_FLAC;

    //[RTK Begin]
    // More audio file types
    public static final int FILE_TYPE_PCM     = 400;
    public static final int FILE_TYPE_APE     = 401;
    public static final int FILE_TYPE_RM      = 402;
    public static final int FILE_TYPE_AIFF    = 403;
    private static final int FIRST_AUDIO_FILE_TYPE2 = FILE_TYPE_PCM;
    private static final int LAST_AUDIO_FILE_TYPE2 = FILE_TYPE_AIFF;
    //[RTK End]

    // MIDI file types
    public static final int FILE_TYPE_MID     = 11;
    public static final int FILE_TYPE_SMF     = 12;
    public static final int FILE_TYPE_IMY     = 13;
    private static final int FIRST_MIDI_FILE_TYPE = FILE_TYPE_MID;
    private static final int LAST_MIDI_FILE_TYPE = FILE_TYPE_IMY;

    // Video file types
    public static final int FILE_TYPE_MP4     = 21;
    public static final int FILE_TYPE_M4V     = 22;
    public static final int FILE_TYPE_3GPP    = 23;
    public static final int FILE_TYPE_3GPP2   = 24;
    public static final int FILE_TYPE_WMV     = 25;
    public static final int FILE_TYPE_ASF     = 26;
    public static final int FILE_TYPE_MKV     = 27;
    public static final int FILE_TYPE_MP2TS   = 28;
    public static final int FILE_TYPE_AVI     = 29;
    public static final int FILE_TYPE_WEBM    = 30;
    private static final int FIRST_VIDEO_FILE_TYPE = FILE_TYPE_MP4;
    private static final int LAST_VIDEO_FILE_TYPE = FILE_TYPE_WEBM;

    // More video file types
    public static final int FILE_TYPE_MP2PS   = 200;
    public static final int FILE_TYPE_QT      = 201;
    //[RTK Begin]
    public static final int FILE_TYPE_FLV     = 202;
    public static final int FILE_TYPE_RMVB    = 203;
    public static final int FILE_TYPE_MPG     = 204;
    public static final int FILE_TYPE_ISO     = 205;
    public static final int FILE_TYPE_AVS     = 206;
    public static final int FILE_TYPE_OGM     = 207;
    public static final int FILE_TYPE_SWF     = 208;
    public static final int FILE_TYPE_TRP     = 209;
    public static final int FILE_TYPE_XVID    = 210;
    private static final int FIRST_VIDEO_FILE_TYPE2 = FILE_TYPE_MP2PS;
    private static final int LAST_VIDEO_FILE_TYPE2 = FILE_TYPE_XVID;
    //[RTK End]

    //[RTK Begin]
    // media folders
    public static final int FOLDER_TYPE_AVCHD   = 300;
    public static final int FOLDER_TYPE_BDROM   = 301;
    public static final int FOLDER_TYPE_DVD     = 302;
    private static final int FIRST_MEDIA_FOLDER_TYPE = FOLDER_TYPE_AVCHD;
    private static final int LAST_MEDIA_FOLDER_TYPE = FOLDER_TYPE_DVD;
    //[RTK End]

    // Image file types
    public static final int FILE_TYPE_JPEG    = 31;
    public static final int FILE_TYPE_GIF     = 32;
    public static final int FILE_TYPE_PNG     = 33;
    public static final int FILE_TYPE_BMP     = 34;
    public static final int FILE_TYPE_WBMP    = 35;
    public static final int FILE_TYPE_WEBP    = 36;
    private static final int FIRST_IMAGE_FILE_TYPE = FILE_TYPE_JPEG;
    private static final int LAST_IMAGE_FILE_TYPE = FILE_TYPE_WEBP;

    //[RTK Begin]
    // More image file types
    public static final int FILE_TYPE_TIFF    = 500;
    private static final int FIRST_IMAGE_FILE_TYPE2 = FILE_TYPE_TIFF;
    private static final int LAST_IMAGE_FILE_TYPE2 = FILE_TYPE_TIFF;
    //[RTK End]
   
    // Raw image file types
    public static final int FILE_TYPE_DNG     = 300;
    public static final int FILE_TYPE_CR2     = 301;
    public static final int FILE_TYPE_NEF     = 302;
    public static final int FILE_TYPE_NRW     = 303;
    public static final int FILE_TYPE_ARW     = 304;
    public static final int FILE_TYPE_RW2     = 305;
    public static final int FILE_TYPE_ORF     = 306;
    public static final int FILE_TYPE_RAF     = 307;
    public static final int FILE_TYPE_PEF     = 308;
    public static final int FILE_TYPE_SRW     = 309;
    private static final int FIRST_RAW_IMAGE_FILE_TYPE = FILE_TYPE_DNG;
    private static final int LAST_RAW_IMAGE_FILE_TYPE = FILE_TYPE_SRW;

    // Playlist file types
    public static final int FILE_TYPE_M3U      = 41;
    public static final int FILE_TYPE_PLS      = 42;
    public static final int FILE_TYPE_WPL      = 43;
    public static final int FILE_TYPE_HTTPLIVE = 44;

    private static final int FIRST_PLAYLIST_FILE_TYPE = FILE_TYPE_M3U;
    private static final int LAST_PLAYLIST_FILE_TYPE = FILE_TYPE_HTTPLIVE;

    // Drm file types
    public static final int FILE_TYPE_FL      = 51;
    private static final int FIRST_DRM_FILE_TYPE = FILE_TYPE_FL;
    private static final int LAST_DRM_FILE_TYPE = FILE_TYPE_FL;

    // Other popular file types
    public static final int FILE_TYPE_TEXT          = 100;
    public static final int FILE_TYPE_HTML          = 101;
    public static final int FILE_TYPE_PDF           = 102;
    public static final int FILE_TYPE_XML           = 103;
    public static final int FILE_TYPE_MS_WORD       = 104;
    public static final int FILE_TYPE_MS_EXCEL      = 105;
    public static final int FILE_TYPE_MS_POWERPOINT = 106;
    public static final int FILE_TYPE_ZIP           = 107;
    public static final int FILE_TYPE_DIRECTORY     = 108;
    
    public static class MediaFileType {
        public final int fileType;
        public final String mimeType;

        MediaFileType(int fileType, String mimeType) {
            this.fileType = fileType;
            this.mimeType = mimeType;
        }
    }

    private static final HashMap<String, MediaFileType> sFileTypeMap
            = new HashMap<String, MediaFileType>();
    private static final HashMap<String, Integer> sMimeTypeMap
            = new HashMap<String, Integer>();
    // maps file extension to MTP format code
    private static final HashMap<String, Integer> sFileTypeToFormatMap
            = new HashMap<String, Integer>();
    // maps mime type to MTP format code
    private static final HashMap<String, Integer> sMimeTypeToFormatMap
            = new HashMap<String, Integer>();
    // maps MTP format code to mime type
    private static final HashMap<Integer, String> sFormatToMimeTypeMap
            = new HashMap<Integer, String>();

    static void addFileType(String extension, int fileType, String mimeType) {
        sFileTypeMap.put(extension, new MediaFileType(fileType, mimeType));
        sMimeTypeMap.put(mimeType, Integer.valueOf(fileType));
    }

    static void addFileType(String extension, int fileType, String mimeType, int mtpFormatCode) {
        addFileType(extension, fileType, mimeType);
        sFileTypeToFormatMap.put(extension, Integer.valueOf(mtpFormatCode));
        sMimeTypeToFormatMap.put(mimeType, Integer.valueOf(mtpFormatCode));
        sFormatToMimeTypeMap.put(mtpFormatCode, mimeType);
    }

    private static boolean isWMAEnabled() {
        List<AudioDecoder> decoders = DecoderCapabilities.getAudioDecoders();
        int count = decoders.size();
        for (int i = 0; i < count; i++) {
            AudioDecoder decoder = decoders.get(i);
            if (decoder == AudioDecoder.AUDIO_DECODER_WMA) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWMVEnabled() {
        List<VideoDecoder> decoders = DecoderCapabilities.getVideoDecoders();
        int count = decoders.size();
        for (int i = 0; i < count; i++) {
            VideoDecoder decoder = decoders.get(i);
            if (decoder == VideoDecoder.VIDEO_DECODER_WMV) {
                return true;
            }
        }
        return false;
    }

    static {
        addFileType("MP3", FILE_TYPE_MP3, "audio/mpeg", MtpConstants.FORMAT_MP3);
        addFileType("MPGA", FILE_TYPE_MP3, "audio/mpeg", MtpConstants.FORMAT_MP3);
        addFileType("M4A", FILE_TYPE_M4A, "audio/mp4", MtpConstants.FORMAT_MPEG);
        addFileType("WAV", FILE_TYPE_WAV, "audio/x-wav", MtpConstants.FORMAT_WAV);
        addFileType("AMR", FILE_TYPE_AMR, "audio/amr");
        addFileType("AWB", FILE_TYPE_AWB, "audio/amr-wb");
        if (isWMAEnabled()) {
            addFileType("WMA", FILE_TYPE_WMA, "audio/x-ms-wma", MtpConstants.FORMAT_WMA);
        }
        addFileType("OGG", FILE_TYPE_OGG, "audio/ogg", MtpConstants.FORMAT_OGG);
        addFileType("OGG", FILE_TYPE_OGG, "application/ogg", MtpConstants.FORMAT_OGG);
        addFileType("OGA", FILE_TYPE_OGG, "application/ogg", MtpConstants.FORMAT_OGG);
        addFileType("AAC", FILE_TYPE_AAC, "audio/aac", MtpConstants.FORMAT_AAC);
        addFileType("AAC", FILE_TYPE_AAC, "audio/aac-adts", MtpConstants.FORMAT_AAC);
        addFileType("MKA", FILE_TYPE_MKA, "audio/x-matroska");

        //[RTK Begin]
        addFileType("PCM", FILE_TYPE_PCM, "audio/PCM");
        addFileType("PCM", FILE_TYPE_PCM, "audio/ADPCM");
        addFileType("APE", FILE_TYPE_APE, "audio/x-ape");
        addFileType("RM", FILE_TYPE_RM, "audio/COOK");
        addFileType("RA", FILE_TYPE_RM, "audio/COOK");
        addFileType("AIF", FILE_TYPE_AIFF, "audio/x-aiff", MtpConstants.FORMAT_AIFF);
        addFileType("AIFF", FILE_TYPE_AIFF, "audio/x-aiff", MtpConstants.FORMAT_AIFF);
        //[RTK End]
 
        addFileType("MID", FILE_TYPE_MID, "audio/midi");
        addFileType("MIDI", FILE_TYPE_MID, "audio/midi");
        addFileType("XMF", FILE_TYPE_MID, "audio/midi");
        addFileType("RTTTL", FILE_TYPE_MID, "audio/midi");
        addFileType("SMF", FILE_TYPE_SMF, "audio/sp-midi");
        addFileType("IMY", FILE_TYPE_IMY, "audio/imelody");
        addFileType("RTX", FILE_TYPE_MID, "audio/midi");
        addFileType("OTA", FILE_TYPE_MID, "audio/midi");
        addFileType("MXMF", FILE_TYPE_MID, "audio/midi");

        addFileType("MPEG", FILE_TYPE_MP4, "video/mpeg", MtpConstants.FORMAT_MPEG);
        addFileType("MPG", FILE_TYPE_MP4, "video/mpeg", MtpConstants.FORMAT_MPEG);
        addFileType("MP4", FILE_TYPE_MP4, "video/mp4", MtpConstants.FORMAT_MPEG);
        addFileType("M4V", FILE_TYPE_M4V, "video/mp4", MtpConstants.FORMAT_MPEG);
        //[RTK Begin]
        //addFileType("MOV", FILE_TYPE_M4V, "video/mp4", MtpConstants.FORMAT_MPEG);
        //[RTK End]
        addFileType("MOV", FILE_TYPE_QT, "video/quicktime", MtpConstants.FORMAT_MPEG);

        addFileType("3GP", FILE_TYPE_3GPP, "video/3gpp",  MtpConstants.FORMAT_3GP_CONTAINER);
        addFileType("3GPP", FILE_TYPE_3GPP, "video/3gpp", MtpConstants.FORMAT_3GP_CONTAINER);
        addFileType("3G2", FILE_TYPE_3GPP2, "video/3gpp2", MtpConstants.FORMAT_3GP_CONTAINER);
        addFileType("3GPP2", FILE_TYPE_3GPP2, "video/3gpp2", MtpConstants.FORMAT_3GP_CONTAINER);
        addFileType("MKV", FILE_TYPE_MKV, "video/x-matroska");
        addFileType("WEBM", FILE_TYPE_WEBM, "video/webm");
        addFileType("TS", FILE_TYPE_MP2TS, "video/mp2ts");
        addFileType("AVI", FILE_TYPE_AVI, "video/avi");
        //[RTK Begin]
        addFileType("FLV", FILE_TYPE_FLV, "video/flv");
        addFileType("F4V", FILE_TYPE_FLV, "video/flv");
        addFileType("RMVB", FILE_TYPE_RMVB, "video/rm");
        addFileType("RM", FILE_TYPE_RMVB, "video/rm");
//        addFileType("DAT", FILE_TYPE_MPG, "video/mpg");
        addFileType("VOB", FILE_TYPE_MPG, "video/mpg");
        addFileType("ISO", FILE_TYPE_ISO, "video/iso");
        addFileType("AVS", FILE_TYPE_AVS, "video/avs-video");
        addFileType("M2TS", FILE_TYPE_MP2TS, "video/mp2ts");
        addFileType("MTS", FILE_TYPE_MP2TS, "video/mp2ts");
        addFileType("OGM", FILE_TYPE_OGM, "video/ogm");
//        addFileType("SWF", FILE_TYPE_SWF, "application/x-shockwave-flash");
        addFileType("TRP", FILE_TYPE_TRP, "video/trp");
        addFileType("TP", FILE_TYPE_TRP, "video/tp");
        addFileType("XVID", FILE_TYPE_XVID, "video/x-xvid");
        //[RTK End]

        if (isWMVEnabled()) {
            addFileType("WMV", FILE_TYPE_WMV, "video/x-ms-wmv", MtpConstants.FORMAT_WMV);
            addFileType("ASF", FILE_TYPE_ASF, "video/x-ms-asf");
        }

        addFileType("JPG", FILE_TYPE_JPEG, "image/jpeg", MtpConstants.FORMAT_EXIF_JPEG);
        addFileType("JPEG", FILE_TYPE_JPEG, "image/jpeg", MtpConstants.FORMAT_EXIF_JPEG);
        addFileType("GIF", FILE_TYPE_GIF, "image/gif", MtpConstants.FORMAT_GIF);
        addFileType("PNG", FILE_TYPE_PNG, "image/png", MtpConstants.FORMAT_PNG);
        addFileType("BMP", FILE_TYPE_BMP, "image/x-ms-bmp", MtpConstants.FORMAT_BMP);
 
        addFileType("WBMP", FILE_TYPE_WBMP, "image/vnd.wap.wbmp", MtpConstants.FORMAT_DEFINED);
        addFileType("WEBP", FILE_TYPE_WEBP, "image/webp", MtpConstants.FORMAT_DEFINED);
        //[RTK Begin]
        addFileType("TIFF", FILE_TYPE_TIFF, "image/tiff", MtpConstants.FORMAT_TIFF);
        addFileType("TIF", FILE_TYPE_TIFF, "image/tiff", MtpConstants.FORMAT_TIFF);
        //[RTK End]

        addFileType("DNG", FILE_TYPE_DNG, "image/x-adobe-dng", MtpConstants.FORMAT_DNG);
        addFileType("CR2", FILE_TYPE_CR2, "image/x-canon-cr2", MtpConstants.FORMAT_TIFF);
        addFileType("NEF", FILE_TYPE_NEF, "image/x-nikon-nef", MtpConstants.FORMAT_TIFF_EP);
        addFileType("NRW", FILE_TYPE_NRW, "image/x-nikon-nrw", MtpConstants.FORMAT_TIFF);
        addFileType("ARW", FILE_TYPE_ARW, "image/x-sony-arw", MtpConstants.FORMAT_TIFF);
        addFileType("RW2", FILE_TYPE_RW2, "image/x-panasonic-rw2", MtpConstants.FORMAT_TIFF);
        addFileType("ORF", FILE_TYPE_ORF, "image/x-olympus-orf", MtpConstants.FORMAT_TIFF);
        addFileType("RAF", FILE_TYPE_RAF, "image/x-fuji-raf", MtpConstants.FORMAT_DEFINED);
        addFileType("PEF", FILE_TYPE_PEF, "image/x-pentax-pef", MtpConstants.FORMAT_TIFF);
        addFileType("SRW", FILE_TYPE_SRW, "image/x-samsung-srw", MtpConstants.FORMAT_TIFF);

        addFileType("M3U", FILE_TYPE_M3U, "audio/x-mpegurl", MtpConstants.FORMAT_M3U_PLAYLIST);
        addFileType("M3U", FILE_TYPE_M3U, "application/x-mpegurl", MtpConstants.FORMAT_M3U_PLAYLIST);
        addFileType("PLS", FILE_TYPE_PLS, "audio/x-scpls", MtpConstants.FORMAT_PLS_PLAYLIST);
        addFileType("WPL", FILE_TYPE_WPL, "application/vnd.ms-wpl", MtpConstants.FORMAT_WPL_PLAYLIST);
        addFileType("M3U8", FILE_TYPE_HTTPLIVE, "application/vnd.apple.mpegurl");
        addFileType("M3U8", FILE_TYPE_HTTPLIVE, "audio/mpegurl");
        addFileType("M3U8", FILE_TYPE_HTTPLIVE, "audio/x-mpegurl");

        addFileType("FL", FILE_TYPE_FL, "application/x-android-drm-fl");

        addFileType("TXT", FILE_TYPE_TEXT, "text/plain", MtpConstants.FORMAT_TEXT);
        addFileType("HTM", FILE_TYPE_HTML, "text/html", MtpConstants.FORMAT_HTML);
        addFileType("HTML", FILE_TYPE_HTML, "text/html", MtpConstants.FORMAT_HTML);
        addFileType("PDF", FILE_TYPE_PDF, "application/pdf");
        addFileType("DOC", FILE_TYPE_MS_WORD, "application/msword", MtpConstants.FORMAT_MS_WORD_DOCUMENT);
        addFileType("XLS", FILE_TYPE_MS_EXCEL, "application/vnd.ms-excel", MtpConstants.FORMAT_MS_EXCEL_SPREADSHEET);
        addFileType("PPT", FILE_TYPE_MS_POWERPOINT, "application/mspowerpoint", MtpConstants.FORMAT_MS_POWERPOINT_PRESENTATION);
        addFileType("FLAC", FILE_TYPE_FLAC, "audio/flac", MtpConstants.FORMAT_FLAC);
        addFileType("ZIP", FILE_TYPE_ZIP, "application/zip");
        addFileType("MPG", FILE_TYPE_MP2PS, "video/mp2p");
        addFileType("MPEG", FILE_TYPE_MP2PS, "video/mp2p");
        addFileType("DIR", FILE_TYPE_DIRECTORY, "application/folder", MtpConstants.FORMAT_ASSOCIATION);
        //[RTK Begin]
        addFileType("AVCHD-DIR", FOLDER_TYPE_AVCHD, "video/avchd");
        addFileType("BDROM-DIR", FOLDER_TYPE_BDROM, "video/bdrom");
        addFileType("DVD-DIR", FOLDER_TYPE_DVD, "video/dvd");
        //[RTK End]
    }

    public static boolean isAudioFileType(int fileType) {
        return ((fileType >= FIRST_AUDIO_FILE_TYPE &&
                fileType <= LAST_AUDIO_FILE_TYPE) ||
                //[RTK Begin]
                (fileType >= FIRST_AUDIO_FILE_TYPE2 &&
                 fileType <= LAST_AUDIO_FILE_TYPE2) ||
                //[RTK End]
                (fileType >= FIRST_MIDI_FILE_TYPE &&
                fileType <= LAST_MIDI_FILE_TYPE));
    }

    public static boolean isVideoFileType(int fileType) {
        return (fileType >= FIRST_VIDEO_FILE_TYPE &&
                fileType <= LAST_VIDEO_FILE_TYPE)
            || (fileType >= FIRST_VIDEO_FILE_TYPE2 &&
                fileType <= LAST_VIDEO_FILE_TYPE2)
            //[RTK Begin]
            || (fileType >= FIRST_MEDIA_FOLDER_TYPE &&
                fileType <= LAST_MEDIA_FOLDER_TYPE);
            //[RTK End]
    }

    public static boolean isImageFileType(int fileType) {
        return (fileType >= FIRST_IMAGE_FILE_TYPE &&
                fileType <= LAST_IMAGE_FILE_TYPE)
            //[RTK Begin]
            || (fileType >= FIRST_IMAGE_FILE_TYPE2 &&
                fileType <= LAST_IMAGE_FILE_TYPE2)
            //[RTK End]
            || (fileType >= FIRST_RAW_IMAGE_FILE_TYPE &&
                fileType <= LAST_RAW_IMAGE_FILE_TYPE);
    }

    public static boolean isRawImageFileType(int fileType) {
        return (fileType >= FIRST_RAW_IMAGE_FILE_TYPE &&
                fileType <= LAST_RAW_IMAGE_FILE_TYPE);
    }

    public static boolean isPlayListFileType(int fileType) {
        return (fileType >= FIRST_PLAYLIST_FILE_TYPE &&
                fileType <= LAST_PLAYLIST_FILE_TYPE);
    }

    public static boolean isDrmFileType(int fileType) {
        return (fileType >= FIRST_DRM_FILE_TYPE &&
                fileType <= LAST_DRM_FILE_TYPE);
    }

    public static MediaFileType getFileType(String path) {
        int lastDot = path.lastIndexOf('.');
        if (lastDot < 0)
            return null;
        return sFileTypeMap.get(path.substring(lastDot + 1).toUpperCase(Locale.ROOT));
    }

    public static boolean isMimeTypeMedia(String mimeType) {
        int fileType = getFileTypeForMimeType(mimeType);
        return isAudioFileType(fileType) || isVideoFileType(fileType)
                || isImageFileType(fileType) || isPlayListFileType(fileType);
    }

    // generates a title based on file name
    public static String getFileTitle(String path) {
        // extract file name after last slash
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0) {
            lastSlash++;
            if (lastSlash < path.length()) {
                path = path.substring(lastSlash);
            }
        }
        // truncate the file extension (if any)
        int lastDot = path.lastIndexOf('.');
        if (lastDot > 0) {
            path = path.substring(0, lastDot);
        }
        return path;
    }

    public static int getFileTypeForMimeType(String mimeType) {
        Integer value = sMimeTypeMap.get(mimeType);
        return (value == null ? 0 : value.intValue());
    }

    public static String getMimeTypeForFile(String path) {
        MediaFileType mediaFileType = getFileType(path);
        return (mediaFileType == null ? null : mediaFileType.mimeType);
    }

    public static int getFormatCode(String fileName, String mimeType) {
        if (mimeType != null) {
            Integer value = sMimeTypeToFormatMap.get(mimeType);
            if (value != null) {
                return value.intValue();
            }
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            String extension = fileName.substring(lastDot + 1).toUpperCase(Locale.ROOT);
            Integer value = sFileTypeToFormatMap.get(extension);
            if (value != null) {
                return value.intValue();
            }
        }
        //[RTK Begin]
        File file = new File(fileName);
        if (file.isDirectory()) {
            Integer value = sFileTypeToFormatMap.get("DIR");
            if (value != null) {
                return value.intValue();
            }
        }
        //[RTK End]
        return MtpConstants.FORMAT_UNDEFINED;
    }

    public static String getMimeTypeForFormatCode(int formatCode) {
        return sFormatToMimeTypeMap.get(formatCode);
    }
}
