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

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.drm.DrmManagerClient;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.mtp.MtpConstants;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio;
import android.provider.MediaStore.Audio.Playlists;
import android.provider.MediaStore.Files;
import android.provider.MediaStore.Files.FileColumns;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.sax.Element;
import android.sax.ElementListener;
import android.sax.RootElement;
import android.system.ErrnoException;
import android.system.Os;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

import dalvik.system.CloseGuard;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

import libcore.io.Libcore;

//[RTK Begin]
import java.util.Arrays;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
//[RTK End]

/**
 * Internal service helper that no-one should use directly.
 *
 * The way the scan currently works is:
 * - The Java MediaScannerService creates a MediaScanner (this class), and calls
 *   MediaScanner.scanDirectories on it.
 * - scanDirectories() calls the native processDirectory() for each of the specified directories.
 * - the processDirectory() JNI method wraps the provided mediascanner client in a native
 *   'MyMediaScannerClient' class, then calls processDirectory() on the native MediaScanner
 *   object (which got created when the Java MediaScanner was created).
 * - native MediaScanner.processDirectory() calls
 *   doProcessDirectory(), which recurses over the folder, and calls
 *   native MyMediaScannerClient.scanFile() for every file whose extension matches.
 * - native MyMediaScannerClient.scanFile() calls back on Java MediaScannerClient.scanFile,
 *   which calls doScanFile, which after some setup calls back down to native code, calling
 *   MediaScanner.processFile().
 * - MediaScanner.processFile() calls one of several methods, depending on the type of the
 *   file: parseMP3, parseMP4, parseMidi, parseOgg or parseWMA.
 * - each of these methods gets metadata key/value pairs from the file, and repeatedly
 *   calls native MyMediaScannerClient.handleStringTag, which calls back up to its Java
 *   counterparts in this file.
 * - Java handleStringTag() gathers the key/value pairs that it's interested in.
 * - once processFile returns and we're back in Java code in doScanFile(), it calls
 *   Java MyMediaScannerClient.endFile(), which takes all the data that's been
 *   gathered and inserts an entry in to the database.
 *
 * In summary:
 * Java MediaScannerService calls
 * Java MediaScanner scanDirectories, which calls
 * Java MediaScanner processDirectory (native method), which calls
 * native MediaScanner processDirectory, which calls
 * native MyMediaScannerClient scanFile, which calls
 * Java MyMediaScannerClient scanFile, which calls
 * Java MediaScannerClient doScanFile, which calls
 * Java MediaScanner processFile (native method), which calls
 * native MediaScanner processFile, which calls
 * native parseMP3, parseMP4, parseMidi, parseOgg or parseWMA, which calls
 * native MyMediaScanner handleStringTag, which calls
 * Java MyMediaScanner handleStringTag.
 * Once MediaScanner processFile returns, an entry is inserted in to the database.
 *
 * The MediaScanner class is not thread-safe, so it should only be used in a single threaded manner.
 *
 * {@hide}
 */
public class MediaScanner implements AutoCloseable {
    static {
        System.loadLibrary("media_jni");
        native_init();
    }

    private final static String TAG = "MediaScanner";

    private static final String[] FILES_PRESCAN_PROJECTION = new String[] {
            Files.FileColumns._ID, // 0
            Files.FileColumns.DATA, // 1
            Files.FileColumns.FORMAT, // 2
            Files.FileColumns.DATE_MODIFIED, // 3
    };

    private static final String[] ID_PROJECTION = new String[] {
            Files.FileColumns._ID,
    };

    private static final int FILES_PRESCAN_ID_COLUMN_INDEX = 0;
    private static final int FILES_PRESCAN_PATH_COLUMN_INDEX = 1;
    private static final int FILES_PRESCAN_FORMAT_COLUMN_INDEX = 2;
    private static final int FILES_PRESCAN_DATE_MODIFIED_COLUMN_INDEX = 3;

    private static final String[] PLAYLIST_MEMBERS_PROJECTION = new String[] {
            Audio.Playlists.Members.PLAYLIST_ID, // 0
     };

    private static final int ID_PLAYLISTS_COLUMN_INDEX = 0;
    private static final int PATH_PLAYLISTS_COLUMN_INDEX = 1;
    private static final int DATE_MODIFIED_PLAYLISTS_COLUMN_INDEX = 2;

    //[RTK Begin]
    private static final int THRESHOLD_DIFFERENCE_FREE_SPACE = 1024; // 1K bytes (CTS audio file has small size)
    private static final String[] FILES_FREESPACE_PROJECTION = new String[] {
        Files.FileColumns._ID,  // 0
        Files.FileColumns.DATA, // 1
        Files.FileColumns.FORMAT,   // 2
        Files.FileColumns.FREE_SPACE,   // 3
        Files.FileColumns.DATE_MODIFIED,    // 4
        Files.FileColumns.STORAGE_ID, // 5
    };

    private static final int FILES_FREESPACE_ID_COLUMN_INDEX = 0;
    private static final int FILES_FREESPACE_PATH_COLUMN_INDEX = 1;
    private static final int FILES_FREESPACE_FORMAT_COLUMN_INDEX = 2;
    private static final int FILES_FREESPACE_FREE_SPACE_COLUMN_INDEX = 3;
    private static final int FILES_FREESPACE_DATE_MODIFIED_COLUMN_INDEX = 4;
    //[RTK End]

    private static final String RINGTONES_DIR = "/ringtones/";
    private static final String NOTIFICATIONS_DIR = "/notifications/";
    private static final String ALARMS_DIR = "/alarms/";
    private static final String MUSIC_DIR = "/music/";
    private static final String PODCAST_DIR = "/podcasts/";

    public static final String SCANNED_BUILD_PREFS_NAME = "MediaScanBuild";
    public static final String LAST_INTERNAL_SCAN_FINGERPRINT = "lastScanFingerprint";
    private static final String SYSTEM_SOUNDS_DIR = "/system/media/audio";
    private static String sLastInternalScanFingerprint;

    private static final String[] ID3_GENRES = {
        // ID3v1 Genres
        "Blues",
        "Classic Rock",
        "Country",
        "Dance",
        "Disco",
        "Funk",
        "Grunge",
        "Hip-Hop",
        "Jazz",
        "Metal",
        "New Age",
        "Oldies",
        "Other",
        "Pop",
        "R&B",
        "Rap",
        "Reggae",
        "Rock",
        "Techno",
        "Industrial",
        "Alternative",
        "Ska",
        "Death Metal",
        "Pranks",
        "Soundtrack",
        "Euro-Techno",
        "Ambient",
        "Trip-Hop",
        "Vocal",
        "Jazz+Funk",
        "Fusion",
        "Trance",
        "Classical",
        "Instrumental",
        "Acid",
        "House",
        "Game",
        "Sound Clip",
        "Gospel",
        "Noise",
        "AlternRock",
        "Bass",
        "Soul",
        "Punk",
        "Space",
        "Meditative",
        "Instrumental Pop",
        "Instrumental Rock",
        "Ethnic",
        "Gothic",
        "Darkwave",
        "Techno-Industrial",
        "Electronic",
        "Pop-Folk",
        "Eurodance",
        "Dream",
        "Southern Rock",
        "Comedy",
        "Cult",
        "Gangsta",
        "Top 40",
        "Christian Rap",
        "Pop/Funk",
        "Jungle",
        "Native American",
        "Cabaret",
        "New Wave",
        "Psychadelic",
        "Rave",
        "Showtunes",
        "Trailer",
        "Lo-Fi",
        "Tribal",
        "Acid Punk",
        "Acid Jazz",
        "Polka",
        "Retro",
        "Musical",
        "Rock & Roll",
        "Hard Rock",
        // The following genres are Winamp extensions
        "Folk",
        "Folk-Rock",
        "National Folk",
        "Swing",
        "Fast Fusion",
        "Bebob",
        "Latin",
        "Revival",
        "Celtic",
        "Bluegrass",
        "Avantgarde",
        "Gothic Rock",
        "Progressive Rock",
        "Psychedelic Rock",
        "Symphonic Rock",
        "Slow Rock",
        "Big Band",
        "Chorus",
        "Easy Listening",
        "Acoustic",
        "Humour",
        "Speech",
        "Chanson",
        "Opera",
        "Chamber Music",
        "Sonata",
        "Symphony",
        "Booty Bass",
        "Primus",
        "Porn Groove",
        "Satire",
        "Slow Jam",
        "Club",
        "Tango",
        "Samba",
        "Folklore",
        "Ballad",
        "Power Ballad",
        "Rhythmic Soul",
        "Freestyle",
        "Duet",
        "Punk Rock",
        "Drum Solo",
        "A capella",
        "Euro-House",
        "Dance Hall",
        // The following ones seem to be fairly widely supported as well
        "Goa",
        "Drum & Bass",
        "Club-House",
        "Hardcore",
        "Terror",
        "Indie",
        "Britpop",
        null,
        "Polsk Punk",
        "Beat",
        "Christian Gangsta",
        "Heavy Metal",
        "Black Metal",
        "Crossover",
        "Contemporary Christian",
        "Christian Rock",
        "Merengue",
        "Salsa",
        "Thrash Metal",
        "Anime",
        "JPop",
        "Synthpop",
        // 148 and up don't seem to have been defined yet.
    };

    private long mNativeContext;
    private final Context mContext;
    private final String mPackageName;
    private String mVolumeName;
    private ContentProviderClient mMediaProvider;
    private final Uri mAudioUri;
    private final Uri mVideoUri;
    private final Uri mImagesUri;
    private final Uri mThumbsUri;
    private final Uri mPlaylistsUri;
    private final Uri mFilesUri;
    private final Uri mFilesUriNoNotify;
    private final boolean mProcessPlaylists;
    private final boolean mProcessGenres;
    private int mMtpObjectHandle;

    private final AtomicBoolean mClosed = new AtomicBoolean();
    private final CloseGuard mCloseGuard = CloseGuard.get();

    private final String mExternalStoragePath;

    /** whether to use bulk inserts or individual inserts for each item */
    private static final boolean ENABLE_BULK_INSERTS = true;

    // used when scanning the image database so we know whether we have to prune
    // old thumbnail files
    private int mOriginalCount;
    /** Whether the scanner has set a default sound for the ringer ringtone. */
    private boolean mDefaultRingtoneSet;
    /** Whether the scanner has set a default sound for the notification ringtone. */
    private boolean mDefaultNotificationSet;
    /** Whether the scanner has set a default sound for the alarm ringtone. */
    private boolean mDefaultAlarmSet;
    /** The filename for the default sound for the ringer ringtone. */
    private String mDefaultRingtoneFilename;
    /** The filename for the default sound for the notification ringtone. */
    private String mDefaultNotificationFilename;
    /** The filename for the default sound for the alarm ringtone. */
    private String mDefaultAlarmAlertFilename;
    /**
     * The prefix for system properties that define the default sound for
     * ringtones. Concatenate the name of the setting from Settings
     * to get the full system property.
     */
    private static final String DEFAULT_RINGTONE_PROPERTY_PREFIX = "ro.config.";

    private final BitmapFactory.Options mBitmapOptions = new BitmapFactory.Options();

    private static class FileEntry {
        long mRowId;
        String mPath;
        long mLastModified;
        int mFormat;
        boolean mLastModifiedChanged;

        FileEntry(long rowId, String path, long lastModified, int format) {
            mRowId = rowId;
            mPath = path;
            mLastModified = lastModified;
            mFormat = format;
            mLastModifiedChanged = false;
        }

        @Override
        public String toString() {
            return mPath + " mRowId: " + mRowId;
        }
    }

    private static class PlaylistEntry {
        String path;
        long bestmatchid;
        int bestmatchlevel;
    }

    private final ArrayList<PlaylistEntry> mPlaylistEntries = new ArrayList<>();
    private ArrayList<FileEntry> mPlayLists = new ArrayList<>();

    private MediaInserter mMediaInserter;

    private DrmManagerClient mDrmManagerClient = null;

    private boolean mUseRtkMediaProvider = false;
    private static boolean mScanning = false;
    private static boolean mForceStop = false;

    public MediaScanner(Context c, String volumeName, String path) {
        mUseRtkMediaProvider = SystemProperties.get("persist.media.RTKMediaProvider").equals("true") ? true : false;
        mExternalStoragePath = Environment.getExternalStorageDirectory().getAbsolutePath();

        native_setup();
        mContext = c;
        mPackageName = c.getPackageName();
        mVolumeName = volumeName;

        mBitmapOptions.inSampleSize = 1;
        mBitmapOptions.inJustDecodeBounds = true;

        setDefaultRingtoneFileNames();

        mMediaProvider = mContext.getContentResolver()
                .acquireContentProviderClient(MediaStore.AUTHORITY);

        if (sLastInternalScanFingerprint == null) {
            final SharedPreferences scanSettings =
                    mContext.getSharedPreferences(SCANNED_BUILD_PREFS_NAME, Context.MODE_PRIVATE);
            sLastInternalScanFingerprint =
                    scanSettings.getString(LAST_INTERNAL_SCAN_FINGERPRINT, new String());
        }
        //RTK begin
        String evanVolume = volumeName;
        if(mExternalPaths == null || mStorageManager == null)
        {
            mStorageManager = (StorageManager) mContext.getSystemService(Context.STORAGE_SERVICE);
            mExternalPaths = mStorageManager.getVolumePaths();
        }

        //We only consider external storage (but not include EMMC case)
        if(path != null && path.startsWith("/storage/") && !path.startsWith("/storage/emulated/"))
        {
            Log.d(TAG, "MediaScanner Path " + path);
            Log.d(TAG, "mExternalPath length " + mExternalPaths.length);
            for(int i=0;i<mExternalPaths.length;i++)
                if(mExternalPaths[i].equals(path)){
                    evanVolume += "-" + mExternalPaths[i].substring(1+mExternalPaths[i].lastIndexOf("/"));
                    Log.d(TAG, "Got Same mExternalPath " + mExternalPaths[i] + " new volume " + evanVolume);
                    volumeName = evanVolume;
                    break;
                }
        }
        //RTK end

        mAudioUri = Audio.Media.getContentUri(volumeName);
        mVideoUri = Video.Media.getContentUri(volumeName);
        mImagesUri = Images.Media.getContentUri(volumeName);
        mThumbsUri = Images.Thumbnails.getContentUri(volumeName);
        mFilesUri = Files.getContentUri(volumeName);
        mFilesUriNoNotify = mFilesUri.buildUpon().appendQueryParameter("nonotify", "1").build();

        if (!volumeName.equals("internal")) {
            // we only support playlists on external media
            mProcessPlaylists = true;
            mProcessGenres = true;
            mPlaylistsUri = Playlists.getContentUri(volumeName);
        } else {
            mProcessPlaylists = false;
            mProcessGenres = false;
            mPlaylistsUri = null;
        }

        final Locale locale = mContext.getResources().getConfiguration().locale;
        if (locale != null) {
            String language = locale.getLanguage();
            String country = locale.getCountry();
            if (language != null) {
                if (country != null) {
                    setLocale(language + "_" + country);
                } else {
                    setLocale(language);
                }
            }
        }

        mCloseGuard.open("close");
    }

    private void setDefaultRingtoneFileNames() {
        mDefaultRingtoneFilename = SystemProperties.get(DEFAULT_RINGTONE_PROPERTY_PREFIX
                + Settings.System.RINGTONE);
        mDefaultNotificationFilename = SystemProperties.get(DEFAULT_RINGTONE_PROPERTY_PREFIX
                + Settings.System.NOTIFICATION_SOUND);
        mDefaultAlarmAlertFilename = SystemProperties.get(DEFAULT_RINGTONE_PROPERTY_PREFIX
                + Settings.System.ALARM_ALERT);
    }

    private final MyMediaScannerClient mClient = new MyMediaScannerClient();

    //[RTK Begin]
    private static StorageManager mStorageManager = null;
    private static String[] mExternalPaths = null;
    //[RTK End]

    private boolean isDrmEnabled() {
        String prop = SystemProperties.get("drm.service.enabled");
        return prop != null && prop.equals("true");
    }

    private class MyMediaScannerClient implements MediaScannerClient {

        private final SimpleDateFormat mDateFormatter;

        private String mArtist;
        private String mAlbumArtist;    // use this if mArtist is missing
        private String mAlbum;
        private String mTitle;
        private String mComposer;
        private String mGenre;
        private String mMimeType;
        private int mFileType;
        private int mTrack;
        private int mYear;
        private int mDuration;
        private String mPath;
        private long mDate;
        private long mLastModified;
        private long mFileSize;
        private String mWriter;
        private int mCompilation;
        private boolean mIsDrm;
        private boolean mNoMedia;   // flag to suppress file from appearing in media tables
        private int mWidth;
        private int mHeight;
        //[RTK Begin]
        private long mFreeSpace;
        //[RTK End]

        public MyMediaScannerClient() {
            mDateFormatter = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
            mDateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        }

        public FileEntry beginFile(String path, String mimeType, long lastModified,
                long fileSize, boolean isDirectory, boolean noMedia) {
            mMimeType = mimeType;
            mFileType = 0;
            mFileSize = fileSize;
            mIsDrm = false;

            if (!isDirectory) {
                if (!noMedia && isNoMediaFile(path)) {
                    noMedia = true;
                }
                mNoMedia = noMedia;

                // try mimeType first, if it is specified
                if (mimeType != null) {
                    mFileType = MediaFile.getFileTypeForMimeType(mimeType);
                }

                // if mimeType was not specified, compute file type based on file extension.
                if (mFileType == 0) {
                    MediaFile.MediaFileType mediaFileType = MediaFile.getFileType(path);
                    if (mediaFileType != null) {
                        mFileType = mediaFileType.fileType;
                        if (mMimeType == null) {
                            mMimeType = mediaFileType.mimeType;
                        }
                    }
                }

                if (isDrmEnabled() && MediaFile.isDrmFileType(mFileType)) {
                    mFileType = getFileTypeFromDrm(path);
                }
            }
            else {
                MediaFile.MediaFileType mediaFileType = null;
                if (isAVCHDFolder(path)) {
                    mediaFileType = MediaFile.getFileType(".avchd-dir");
                }
                else if (isDVDFolder(path)) {
                    mediaFileType = MediaFile.getFileType(".dvd-dir");
                }
                if (mFileType == 0) {
                    if (mediaFileType != null) {
                        mFileType = mediaFileType.fileType;
                        if (mMimeType == null) {
                            mMimeType = mediaFileType.mimeType;
                        }
                    }
                }
            }

            FileEntry entry = makeEntryFor(path);
            // add some slack to avoid a rounding error
            long delta = (entry != null) ? (lastModified - entry.mLastModified) : 0;
            boolean wasModified = delta > 1 || delta < -1;
            if (entry == null || wasModified) {
                if (wasModified) {
                    entry.mLastModified = lastModified;
                } else {
                    entry = new FileEntry(0, path, lastModified,
                            (isDirectory ? MtpConstants.FORMAT_ASSOCIATION : 0));
                }
                entry.mLastModifiedChanged = true;
            }

            if (mProcessPlaylists && MediaFile.isPlayListFileType(mFileType)) {
                mPlayLists.add(entry);
                // we don't process playlists in the main scan, so return null
                return null;
            }

            // clear all the metadata
            mArtist = null;
            mAlbumArtist = null;
            mAlbum = null;
            mTitle = null;
            mComposer = null;
            mGenre = null;
            mTrack = 0;
            mYear = 0;
            mDuration = 0;
            mPath = path;
            mDate = 0;
            mLastModified = lastModified;
            mWriter = null;
            mCompilation = 0;
            mWidth = 0;
            mHeight = 0;

            return entry;
        }

        @Override
        public int scanFile(String path, long lastModified, long fileSize,
                boolean isDirectory, boolean noMedia) {
            // This is the callback funtion from native codes.
            // Log.v(TAG, "scanFile: "+path);
            doScanFile(path, null, lastModified, fileSize, isDirectory, false, noMedia);
            //[RTK Begin]
            return returnByFileType(mFileType);
            //[RTK End]
        }

        //[RTK Begin]
        private int returnByFileType(int fileType) {
            int result = 0;
            switch (fileType)
            {
            case MediaFile.FOLDER_TYPE_AVCHD:
                result = OK_AVCHD_FOLDER;
                break;
            case MediaFile.FOLDER_TYPE_BDROM:
                result = OK_BDROM_FOLDER;
                break;
            case MediaFile.FOLDER_TYPE_DVD:
                result = OK_DVD_FOLDER;
                break;
            default:
                result = OK;
            }
            return result;
        }

        public Uri doScanFile(String path, String mimeType, boolean scanAlways, boolean IsExternalStorageRoot) {
            if (IsExternalStorageRoot) {
                File file = new File(path);
                long lastModified = file.lastModified();
                long fileSize = file.length();
                boolean isDirectory = file.isDirectory();
                boolean noMedia = isNoMediaPath(path);

                mFreeSpace = file.getFreeSpace();
                Log.d(TAG, "path = " + path + ", free space = " + mFreeSpace + " bytes");

                return doScanFile(path, mimeType, lastModified, fileSize, isDirectory, scanAlways, noMedia);
            }
            return null;
        }
        //[RTK End]

        public Uri doScanFile(String path, String mimeType, long lastModified,
                long fileSize, boolean isDirectory, boolean scanAlways, boolean noMedia) {
            Uri result = null;
//            long t1 = System.currentTimeMillis();
            try {
                FileEntry entry = beginFile(path, mimeType, lastModified,
                        fileSize, isDirectory, noMedia);

                if (entry == null) {
                    return null;
                }

                // if this file was just inserted via mtp, set the rowid to zero
                // (even though it already exists in the database), to trigger
                // the correct code path for updating its entry
                if (mMtpObjectHandle != 0) {
                    entry.mRowId = 0;
                }

                if (entry.mPath != null) {
                    if (((!mDefaultNotificationSet &&
                                doesPathHaveFilename(entry.mPath, mDefaultNotificationFilename))
                        || (!mDefaultRingtoneSet &&
                                doesPathHaveFilename(entry.mPath, mDefaultRingtoneFilename))
                        || (!mDefaultAlarmSet &&
                                doesPathHaveFilename(entry.mPath, mDefaultAlarmAlertFilename)))) {
                        Log.w(TAG, "forcing rescan of " + entry.mPath +
                                "since ringtone setting didn't finish");
                        scanAlways = true;
                    } else if (isSystemSoundWithMetadata(entry.mPath)
                            && !Build.FINGERPRINT.equals(sLastInternalScanFingerprint)) {
                        // file is located on the system partition where the date cannot be trusted:
                        // rescan if the build fingerprint has changed since the last scan.
                        Log.i(TAG, "forcing rescan of " + entry.mPath
                                + " since build fingerprint changed");
                        scanAlways = true;
                    }
                }

                // rescan for metadata if file was modified since last scan
                if (entry != null && (entry.mLastModifiedChanged || scanAlways)) {
                    if (noMedia) {
                        result = endFile(entry, false, false, false, false, false);
                    } else {
                        String lowpath = path.toLowerCase(Locale.ROOT);
                        boolean ringtones = (lowpath.indexOf(RINGTONES_DIR) > 0);
                        boolean notifications = (lowpath.indexOf(NOTIFICATIONS_DIR) > 0);
                        boolean alarms = (lowpath.indexOf(ALARMS_DIR) > 0);
                        boolean podcasts = (lowpath.indexOf(PODCAST_DIR) > 0);
                        boolean music = (lowpath.indexOf(MUSIC_DIR) > 0) ||
                            (!ringtones && !notifications && !alarms && !podcasts);

                        boolean isaudio = MediaFile.isAudioFileType(mFileType);
                        boolean isvideo = MediaFile.isVideoFileType(mFileType);
                        boolean isimage = MediaFile.isImageFileType(mFileType);

                        if (isaudio || isvideo || isimage) {
                            path = Environment.maybeTranslateEmulatedPathToInternal(new File(path))
                                    .getAbsolutePath();
                        }

                        // we only extract metadata for audio and video files
                        if (isaudio || isvideo) {
                            processFile(path, mimeType, this);
                        }

                        if (isimage) {
                            processImageFile(path);
                        }

                        result = endFile(entry, ringtones, notifications, alarms, music, podcasts);
                    }
                }
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException in MediaScanner.scanFile()", e);
            }
//            long t2 = System.currentTimeMillis();
//            Log.v(TAG, "scanFile: " + path + " took " + (t2-t1));
            return result;
        }

        private long parseDate(String date) {
            try {
              return mDateFormatter.parse(date).getTime();
            } catch (ParseException e) {
              return 0;
            }
        }

        private int parseSubstring(String s, int start, int defaultValue) {
            int length = s.length();
            if (start == length) return defaultValue;

            char ch = s.charAt(start++);
            // return defaultValue if we have no integer at all
            if (ch < '0' || ch > '9') return defaultValue;

            int result = ch - '0';
            while (start < length) {
                ch = s.charAt(start++);
                if (ch < '0' || ch > '9') return result;
                result = result * 10 + (ch - '0');
            }

            return result;
        }

        public void handleStringTag(String name, String value) {
            if (name.equalsIgnoreCase("title") || name.startsWith("title;")) {
                // Don't trim() here, to preserve the special \001 character
                // used to force sorting. The media provider will trim() before
                // inserting the title in to the database.
                mTitle = value;
            } else if (name.equalsIgnoreCase("artist") || name.startsWith("artist;")) {
                mArtist = value.trim();
            } else if (name.equalsIgnoreCase("albumartist") || name.startsWith("albumartist;")
                    || name.equalsIgnoreCase("band") || name.startsWith("band;")) {
                mAlbumArtist = value.trim();
            } else if (name.equalsIgnoreCase("album") || name.startsWith("album;")) {
                mAlbum = value.trim();
            } else if (name.equalsIgnoreCase("composer") || name.startsWith("composer;")) {
                mComposer = value.trim();
            } else if (mProcessGenres &&
                    (name.equalsIgnoreCase("genre") || name.startsWith("genre;"))) {
                mGenre = getGenreName(value);
            } else if (name.equalsIgnoreCase("year") || name.startsWith("year;")) {
                mYear = parseSubstring(value, 0, 0);
            } else if (name.equalsIgnoreCase("tracknumber") || name.startsWith("tracknumber;")) {
                // track number might be of the form "2/12"
                // we just read the number before the slash
                int num = parseSubstring(value, 0, 0);
                mTrack = (mTrack / 1000) * 1000 + num;
            } else if (name.equalsIgnoreCase("discnumber") ||
                    name.equals("set") || name.startsWith("set;")) {
                // set number might be of the form "1/3"
                // we just read the number before the slash
                int num = parseSubstring(value, 0, 0);
                mTrack = (num * 1000) + (mTrack % 1000);
            } else if (name.equalsIgnoreCase("duration")) {
                mDuration = parseSubstring(value, 0, 0);
            } else if (name.equalsIgnoreCase("writer") || name.startsWith("writer;")) {
                mWriter = value.trim();
            } else if (name.equalsIgnoreCase("compilation")) {
                mCompilation = parseSubstring(value, 0, 0);
            } else if (name.equalsIgnoreCase("isdrm")) {
                mIsDrm = (parseSubstring(value, 0, 0) == 1);
            } else if (name.equalsIgnoreCase("date")) {
                mDate = parseDate(value);
            } else if (name.equalsIgnoreCase("width")) {
                mWidth = parseSubstring(value, 0, 0);
            } else if (name.equalsIgnoreCase("height")) {
                mHeight = parseSubstring(value, 0, 0);
            } else {
                //Log.v(TAG, "unknown tag: " + name + " (" + mProcessGenres + ")");
            }
        }

        private boolean convertGenreCode(String input, String expected) {
            String output = getGenreName(input);
            if (output.equals(expected)) {
                return true;
            } else {
                Log.d(TAG, "'" + input + "' -> '" + output + "', expected '" + expected + "'");
                return false;
            }
        }
        private void testGenreNameConverter() {
            convertGenreCode("2", "Country");
            convertGenreCode("(2)", "Country");
            convertGenreCode("(2", "(2");
            convertGenreCode("2 Foo", "Country");
            convertGenreCode("(2) Foo", "Country");
            convertGenreCode("(2 Foo", "(2 Foo");
            convertGenreCode("2Foo", "2Foo");
            convertGenreCode("(2)Foo", "Country");
            convertGenreCode("200 Foo", "Foo");
            convertGenreCode("(200) Foo", "Foo");
            convertGenreCode("200Foo", "200Foo");
            convertGenreCode("(200)Foo", "Foo");
            convertGenreCode("200)Foo", "200)Foo");
            convertGenreCode("200) Foo", "200) Foo");
        }

        public String getGenreName(String genreTagValue) {

            if (genreTagValue == null) {
                return null;
            }
            final int length = genreTagValue.length();

            if (length > 0) {
                boolean parenthesized = false;
                StringBuffer number = new StringBuffer();
                int i = 0;
                for (; i < length; ++i) {
                    char c = genreTagValue.charAt(i);
                    if (i == 0 && c == '(') {
                        parenthesized = true;
                    } else if (Character.isDigit(c)) {
                        number.append(c);
                    } else {
                        break;
                    }
                }
                char charAfterNumber = i < length ? genreTagValue.charAt(i) : ' ';
                if ((parenthesized && charAfterNumber == ')')
                        || !parenthesized && Character.isWhitespace(charAfterNumber)) {
                    try {
                        short genreIndex = Short.parseShort(number.toString());
                        if (genreIndex >= 0) {
                            if (genreIndex < ID3_GENRES.length && ID3_GENRES[genreIndex] != null) {
                                return ID3_GENRES[genreIndex];
                            } else if (genreIndex == 0xFF) {
                                return null;
                            } else if (genreIndex < 0xFF && (i + 1) < length) {
                                // genre is valid but unknown,
                                // if there is a string after the value we take it
                                if (parenthesized && charAfterNumber == ')') {
                                    i++;
                                }
                                String ret = genreTagValue.substring(i).trim();
                                if (ret.length() != 0) {
                                    return ret;
                                }
                            } else {
                                // else return the number, without parentheses
                                return number.toString();
                            }
                        }
                    } catch (NumberFormatException e) {
                    }
                }
            }

            return genreTagValue;
        }

        private void processImageFile(String path) {
            try {
                mBitmapOptions.outWidth = 0;
                mBitmapOptions.outHeight = 0;
                mBitmapOptions.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, mBitmapOptions);
                mWidth = mBitmapOptions.outWidth;
                mHeight = mBitmapOptions.outHeight;
            } catch (Throwable th) {
                // ignore;
            }
        }

        public void setMimeType(String mimeType) {
            if ("audio/mp4".equals(mMimeType) &&
                    mimeType.startsWith("video")) {
                // for feature parity with Donut, we force m4a files to keep the
                // audio/mp4 mimetype, even if they are really "enhanced podcasts"
                // with a video track
                return;
            }
            mMimeType = mimeType;
            mFileType = MediaFile.getFileTypeForMimeType(mimeType);
        }

        /**
         * Formats the data into a values array suitable for use with the Media
         * Content Provider.
         *
         * @return a map of values
         */
        private ContentValues toValues() {
            ContentValues map = new ContentValues();

            map.put(MediaStore.MediaColumns.DATA, mPath);
            map.put(MediaStore.MediaColumns.TITLE, mTitle);
            map.put(MediaStore.MediaColumns.DATE_MODIFIED, mLastModified);
            map.put(MediaStore.MediaColumns.SIZE, mFileSize);
            map.put(MediaStore.MediaColumns.MIME_TYPE, mMimeType);
            map.put(MediaStore.MediaColumns.IS_DRM, mIsDrm);
            //[RTK Begin]
            if (mUseRtkMediaProvider)
                map.put(MediaStore.MediaColumns.DATE_RECENTPLAY, 0); // default value 0
            //[RTK End]

            String resolution = null;
            if (mWidth > 0 && mHeight > 0) {
                map.put(MediaStore.MediaColumns.WIDTH, mWidth);
                map.put(MediaStore.MediaColumns.HEIGHT, mHeight);
                resolution = mWidth + "x" + mHeight;
            }

            if (!mNoMedia) {
                if (MediaFile.isVideoFileType(mFileType)) {
                    map.put(Video.Media.ARTIST, (mArtist != null && mArtist.length() > 0
                            ? mArtist : MediaStore.UNKNOWN_STRING));
                    map.put(Video.Media.ALBUM, (mAlbum != null && mAlbum.length() > 0
                            ? mAlbum : MediaStore.UNKNOWN_STRING));
                    map.put(Video.Media.DURATION, mDuration);
                    if (resolution != null) {
                        map.put(Video.Media.RESOLUTION, resolution);
                    }
                    if (mDate > 0) {
                        map.put(Video.Media.DATE_TAKEN, mDate);
                    }
                } else if (MediaFile.isImageFileType(mFileType)) {
                    // FIXME - add DESCRIPTION
                } else if (MediaFile.isAudioFileType(mFileType)) {
                    map.put(Audio.Media.ARTIST, (mArtist != null && mArtist.length() > 0) ?
                            mArtist : MediaStore.UNKNOWN_STRING);
                    map.put(Audio.Media.ALBUM_ARTIST, (mAlbumArtist != null &&
                            mAlbumArtist.length() > 0) ? mAlbumArtist : null);
                    map.put(Audio.Media.ALBUM, (mAlbum != null && mAlbum.length() > 0) ?
                            mAlbum : MediaStore.UNKNOWN_STRING);
                    map.put(Audio.Media.COMPOSER, mComposer);
                    map.put(Audio.Media.GENRE, mGenre);
                    if (mYear != 0) {
                        map.put(Audio.Media.YEAR, mYear);
                    }
                    map.put(Audio.Media.TRACK, mTrack);
                    map.put(Audio.Media.DURATION, mDuration);
                    map.put(Audio.Media.COMPILATION, mCompilation);
                }
            }
            return map;
        }

        private Uri endFile(FileEntry entry, boolean ringtones, boolean notifications,
                boolean alarms, boolean music, boolean podcasts)
                throws RemoteException {
            // update database

            // use album artist if artist is missing
            if (mArtist == null || mArtist.length() == 0) {
                mArtist = mAlbumArtist;
            }

            ContentValues values = toValues();
            String title = values.getAsString(MediaStore.MediaColumns.TITLE);
            if (title == null || TextUtils.isEmpty(title.trim())) {
                title = MediaFile.getFileTitle(values.getAsString(MediaStore.MediaColumns.DATA));
                values.put(MediaStore.MediaColumns.TITLE, title);
            }
            String album = values.getAsString(Audio.Media.ALBUM);
            if (MediaStore.UNKNOWN_STRING.equals(album)) {
                album = values.getAsString(MediaStore.MediaColumns.DATA);
                // extract last path segment before file name
                int lastSlash = album.lastIndexOf('/');
                if (lastSlash >= 0) {
                    int previousSlash = 0;
                    while (true) {
                        int idx = album.indexOf('/', previousSlash + 1);
                        if (idx < 0 || idx >= lastSlash) {
                            break;
                        }
                        previousSlash = idx;
                    }
                    if (previousSlash != 0) {
                        album = album.substring(previousSlash + 1, lastSlash);
                        values.put(Audio.Media.ALBUM, album);
                    }
                }
            }
            long rowId = entry.mRowId;
            if (MediaFile.isAudioFileType(mFileType) && (rowId == 0 || mMtpObjectHandle != 0)) {
                // Only set these for new entries. For existing entries, they
                // may have been modified later, and we want to keep the current
                // values so that custom ringtones still show up in the ringtone
                // picker.
                values.put(Audio.Media.IS_RINGTONE, ringtones);
                values.put(Audio.Media.IS_NOTIFICATION, notifications);
                values.put(Audio.Media.IS_ALARM, alarms);
                values.put(Audio.Media.IS_MUSIC, music);
                values.put(Audio.Media.IS_PODCAST, podcasts);
            } else if ((mFileType == MediaFile.FILE_TYPE_JPEG
                    || MediaFile.isRawImageFileType(mFileType)) && !mNoMedia) {
                ExifInterface exif = null;
                try {
                    exif = new ExifInterface(entry.mPath);
                } catch (IOException ex) {
                    // exif is null
                }
                if (exif != null) {
                    float[] latlng = new float[2];
                    if (exif.getLatLong(latlng)) {
                        values.put(Images.Media.LATITUDE, latlng[0]);
                        values.put(Images.Media.LONGITUDE, latlng[1]);
                    }

                    long time = exif.getGpsDateTime();
                    if (time != -1) {
                        values.put(Images.Media.DATE_TAKEN, time);
                    } else {
                        // If no time zone information is available, we should consider using
                        // EXIF local time as taken time if the difference between file time
                        // and EXIF local time is not less than 1 Day, otherwise MediaProvider
                        // will use file time as taken time.
                        time = exif.getDateTime();
                        if (time != -1 && Math.abs(mLastModified * 1000 - time) >= 86400000) {
                            values.put(Images.Media.DATE_TAKEN, time);
                        }
                    }

                    int orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, -1);
                    if (orientation != -1) {
                        // We only recognize a subset of orientation tag values.
                        int degree;
                        switch(orientation) {
                            case ExifInterface.ORIENTATION_ROTATE_90:
                                degree = 90;
                                break;
                            case ExifInterface.ORIENTATION_ROTATE_180:
                                degree = 180;
                                break;
                            case ExifInterface.ORIENTATION_ROTATE_270:
                                degree = 270;
                                break;
                            default:
                                degree = 0;
                                break;
                        }
                        values.put(Images.Media.ORIENTATION, degree);
                    }
                }
            }

            Uri tableUri = mFilesUri;
            MediaInserter inserter = mMediaInserter;
            if (!mNoMedia) {
                if (MediaFile.isVideoFileType(mFileType)) {
                    tableUri = mVideoUri;
                } else if (MediaFile.isImageFileType(mFileType)) {
                    tableUri = mImagesUri;
                } else if (MediaFile.isAudioFileType(mFileType)) {
                    tableUri = mAudioUri;
                }
            }
            Uri result = null;
            boolean needToSetSettings = false;
            // Setting a flag in order not to use bulk insert for the file related with
            // notifications, ringtones, and alarms, because the rowId of the inserted file is
            // needed.
            if (notifications && !mDefaultNotificationSet) {
                if (TextUtils.isEmpty(mDefaultNotificationFilename) ||
                        doesPathHaveFilename(entry.mPath, mDefaultNotificationFilename)) {
                    needToSetSettings = true;
                }
            } else if (ringtones && !mDefaultRingtoneSet) {
                if (TextUtils.isEmpty(mDefaultRingtoneFilename) ||
                        doesPathHaveFilename(entry.mPath, mDefaultRingtoneFilename)) {
                    needToSetSettings = true;
                }
            } else if (alarms && !mDefaultAlarmSet) {
                if (TextUtils.isEmpty(mDefaultAlarmAlertFilename) ||
                        doesPathHaveFilename(entry.mPath, mDefaultAlarmAlertFilename)) {
                    needToSetSettings = true;
                }
            }

            if (rowId == 0) {
                if (mMtpObjectHandle != 0) {
                    values.put(MediaStore.MediaColumns.MEDIA_SCANNER_NEW_OBJECT_ID, mMtpObjectHandle);
                }
                if (tableUri == mFilesUri) {
                    int format = entry.mFormat;
                    if (format == 0) {
                        format = MediaFile.getFormatCode(entry.mPath, mMimeType);
                    }
                    values.put(Files.FileColumns.FORMAT, format);
                    //[RTK Begin]
                    if (mUseRtkMediaProvider)
                        values.put(Files.FileColumns.FREE_SPACE, mFreeSpace);
                    //[RTK End]
                }
                // New file, insert it.
                // Directories need to be inserted before the files they contain, so they
                // get priority when bulk inserting.
                // If the rowId of the inserted file is needed, it gets inserted immediately,
                // bypassing the bulk inserter.
                if (inserter == null || needToSetSettings) {
                    if (inserter != null) {
                        inserter.flushAll();
                    }
                    result = mMediaProvider.insert(tableUri, values);
                } else if (entry.mFormat == MtpConstants.FORMAT_ASSOCIATION) {
                    inserter.insertwithPriority(tableUri, values);
                } else {
                    inserter.insert(tableUri, values);
                }

                if (result != null) {
                    rowId = ContentUris.parseId(result);
                    entry.mRowId = rowId;
                }
            } else {
                // updated file
                result = ContentUris.withAppendedId(tableUri, rowId);
                // path should never change, and we want to avoid replacing mixed cased paths
                // with squashed lower case paths
                values.remove(MediaStore.MediaColumns.DATA);

                int mediaType = 0;
                if (!MediaScanner.isNoMediaPath(entry.mPath)) {
                    int fileType = MediaFile.getFileTypeForMimeType(mMimeType);
                    if (MediaFile.isAudioFileType(fileType)) {
                        mediaType = FileColumns.MEDIA_TYPE_AUDIO;
                    } else if (MediaFile.isVideoFileType(fileType)) {
                        mediaType = FileColumns.MEDIA_TYPE_VIDEO;
                    } else if (MediaFile.isImageFileType(fileType)) {
                        mediaType = FileColumns.MEDIA_TYPE_IMAGE;
                    } else if (MediaFile.isPlayListFileType(fileType)) {
                        mediaType = FileColumns.MEDIA_TYPE_PLAYLIST;
                    }
                    values.put(FileColumns.MEDIA_TYPE, mediaType);
                    //[RTK Begin]
                    if (mUseRtkMediaProvider)
                        values.put(FileColumns.FREE_SPACE, mFreeSpace);
                    //[RTK End]
                }
                mMediaProvider.update(result, values, null, null);
            }

            if(needToSetSettings) {
                if (notifications) {
                    setRingtoneIfNotSet(Settings.System.NOTIFICATION_SOUND, tableUri, rowId);
                    mDefaultNotificationSet = true;
                } else if (ringtones) {
                    setRingtoneIfNotSet(Settings.System.RINGTONE, tableUri, rowId);
                    mDefaultRingtoneSet = true;
                } else if (alarms) {
                    setRingtoneIfNotSet(Settings.System.ALARM_ALERT, tableUri, rowId);
                    mDefaultAlarmSet = true;
                }
            }

            return result;
        }

        private boolean doesPathHaveFilename(String path, String filename) {
            int pathFilenameStart = path.lastIndexOf(File.separatorChar) + 1;
            int filenameLength = filename.length();
            return path.regionMatches(pathFilenameStart, filename, 0, filenameLength) &&
                    pathFilenameStart + filenameLength == path.length();
        }

        private void setRingtoneIfNotSet(String settingName, Uri uri, long rowId) {
            if (wasRingtoneAlreadySet(settingName)) {
                return;
            }

            ContentResolver cr = mContext.getContentResolver();
            String existingSettingValue = Settings.System.getString(cr, settingName);
            if (TextUtils.isEmpty(existingSettingValue)) {
                final Uri settingUri = Settings.System.getUriFor(settingName);
                final Uri ringtoneUri = ContentUris.withAppendedId(uri, rowId);
                RingtoneManager.setActualDefaultRingtoneUri(mContext,
                        RingtoneManager.getDefaultType(settingUri), ringtoneUri);
            }
            Settings.System.putInt(cr, settingSetIndicatorName(settingName), 1);
        }

        private int getFileTypeFromDrm(String path) {
            if (!isDrmEnabled()) {
                return 0;
            }

            int resultFileType = 0;

            if (mDrmManagerClient == null) {
                mDrmManagerClient = new DrmManagerClient(mContext);
            }

            if (mDrmManagerClient.canHandle(path, null)) {
                mIsDrm = true;
                String drmMimetype = mDrmManagerClient.getOriginalMimeType(path);
                if (drmMimetype != null) {
                    mMimeType = drmMimetype;
                    resultFileType = MediaFile.getFileTypeForMimeType(drmMimetype);
                }
            }
            return resultFileType;
        }

    }; // end of anonymous MediaScannerClient instance

    private boolean ForceStop() {
        if (mForceStop)
            return true;
        else
            return false;
    }
    
    private static boolean isSystemSoundWithMetadata(String path) {
        if (path.startsWith(SYSTEM_SOUNDS_DIR + ALARMS_DIR)
                || path.startsWith(SYSTEM_SOUNDS_DIR + RINGTONES_DIR)
                || path.startsWith(SYSTEM_SOUNDS_DIR + NOTIFICATIONS_DIR)) {
            return true;
        }
        return false;
    }

    private String settingSetIndicatorName(String base) {
        return base + "_set";
    }

    private boolean wasRingtoneAlreadySet(String name) {
        ContentResolver cr = mContext.getContentResolver();
        String indicatorName = settingSetIndicatorName(name);
        try {
            return Settings.System.getInt(cr, indicatorName) != 0;
        } catch (SettingNotFoundException e) {
            return false;
        }
    }

    private void prescan(String filePath, boolean prescanFiles) throws RemoteException {
        
        if(ForceStop()==true) return;
        
        Cursor c = null;
        String where = null;
        String[] selectionArgs = null;

        mPlayLists.clear();

        if (filePath != null) {
            // query for only one file
            where = MediaStore.Files.FileColumns._ID + ">?" +
                " AND " + Files.FileColumns.DATA + "=?";
            selectionArgs = new String[] { "", filePath };
        } else {
            where = MediaStore.Files.FileColumns._ID + ">?";
            selectionArgs = new String[] { "" };
        }

        List<String> projectList = new ArrayList<String>();
        projectList.addAll(Arrays.asList(FILES_PRESCAN_PROJECTION));
        projectList.add(Files.FileColumns.STORAGE_ID);
        String[] projectionIn = projectList.toArray(new String[projectList.size()]);

        mDefaultRingtoneSet = wasRingtoneAlreadySet(Settings.System.RINGTONE);
        mDefaultNotificationSet = wasRingtoneAlreadySet(Settings.System.NOTIFICATION_SOUND);
        mDefaultAlarmSet = wasRingtoneAlreadySet(Settings.System.ALARM_ALERT);

        // Tell the provider to not delete the file.
        // If the file is truly gone the delete is unnecessary, and we want to avoid
        // accidentally deleting files that are really there (this may happen if the
        // filesystem is mounted and unmounted while the scanner is running).
        Uri.Builder builder = mFilesUri.buildUpon();
        builder.appendQueryParameter(MediaStore.PARAM_DELETE_DATA, "false");
        MediaBulkDeleter deleter = new MediaBulkDeleter(mMediaProvider, builder.build());

        // Build the list of files from the content provider
        try {
            if (prescanFiles) {
                // First read existing files from the files table.
                // Because we'll be deleting entries for missing files as we go,
                // we need to query the database in small batches, to avoid problems
                // with CursorWindow positioning.
                long lastId = Long.MIN_VALUE;
                Uri limitUri = mFilesUri.buildUpon().appendQueryParameter("limit", "1000").build();

                HashSet<Long> nonExistStorageIDs = new HashSet<Long>();
                boolean bRemovedNonExistingDB = false;

                // Check if the scanning volume is equal to mExternalStoragePath '/storage/emulated/0'.
                if (mVolumeName != null && mExternalStoragePath != null 
                    && mVolumeName.equals(mExternalStoragePath)) {
                    bRemovedNonExistingDB = true;
                }

                while (true) {
                    
                    if(ForceStop()==true)   return;
                    
                    selectionArgs[0] = "" + lastId;
                    if (c != null) {
                        c.close();
                        c = null;
                    }
                    c = mMediaProvider.query(limitUri, projectionIn,
                            where, selectionArgs, MediaStore.Files.FileColumns._ID, null);
                    if (c == null) {
                        break;
                    }

                    int num = c.getCount();

                    if (num == 0) {
                        break;
                    }
                    while (c.moveToNext()) {
                        
                        if(ForceStop()==true){
                            c.close();
                            return;
                        }
                        
                        long rowId = c.getLong(FILES_PRESCAN_ID_COLUMN_INDEX);
                        String path = c.getString(FILES_PRESCAN_PATH_COLUMN_INDEX);
                        int format = c.getInt(FILES_PRESCAN_FORMAT_COLUMN_INDEX);
                        long lastModified = c.getLong(FILES_PRESCAN_DATE_MODIFIED_COLUMN_INDEX);
                        long storage_id = c.getLong(FILES_PRESCAN_PROJECTION.length);
                        lastId = rowId;

                        // Only consider entries with absolute path names.
                        // This allows storing URIs in the database without the
                        // media scanner removing them.
                        if (path != null && path.startsWith("/")) {
                            boolean exists = false;
                            try {
                                exists = Os.access(path, android.system.OsConstants.F_OK);
                            } catch (ErrnoException e1) {
                            }
                            if (!exists && !MtpConstants.isAbstractObject(format)) {
                                // do not delete missing playlists, since they may have been
                                // modified by the user.
                                // The user can delete them in the media player instead.
                                // instead, clear the path and lastModified fields in the row
                                MediaFile.MediaFileType mediaFileType = MediaFile.getFileType(path);
                                int fileType = (mediaFileType == null ? 0 : mediaFileType.fileType);

                                if (!MediaFile.isPlayListFileType(fileType)) {
                                    nonExistStorageIDs.add(storage_id); // add the storage_id of removed file to the candidate list.
                                    deleter.delete(rowId);
                                    if (path.toLowerCase(Locale.US).endsWith("/.nomedia")) {
                                        deleter.flush();
                                        String parent = new File(path).getParent();
                                        mMediaProvider.call(MediaStore.UNHIDE_CALL, parent, null);
                                    }
                                }
                            }
                        }
                    }
                }
                // Fix:bug[41774]
                // remove external db file of unexisting storages.
                // only if bRemovedNonExistingDB is true.
                // If we don't set the condition, it would cause external db file
                // is removed such that the external storage's content is not
                // scanned into db.
                if (bRemovedNonExistingDB) {
                    for (long id : nonExistStorageIDs) {
                        String db = "external-" + Long.toHexString(id) + ".db";
                        mMediaProvider.call("remove", db, null);
                    }
                }
            }
        }finally {
            if (c != null) {
                c.close();
            }
            deleter.flush();
        }

        // compute original size of images
        mOriginalCount = 0;
        c = mMediaProvider.query(mImagesUri, ID_PROJECTION, null, null, null, null);
        if (c != null) {
            mOriginalCount = c.getCount();
            c.close();
        }
    }

    private void pruneDeadThumbnailFiles() {
        HashSet<String> existingFiles = new HashSet<String>();
        String directory = "/sdcard/DCIM/.thumbnails";
        String [] files = (new File(directory)).list();
        Cursor c = null;
        if (files == null)
            files = new String[0];

        for (int i = 0; i < files.length; i++) {
            String fullPathString = directory + "/" + files[i];
            existingFiles.add(fullPathString);
        }

        try {
            c = mMediaProvider.query(
                    mThumbsUri,
                    new String [] { "_data" },
                    null,
                    null,
                    null, null);
            Log.v(TAG, "pruneDeadThumbnailFiles... " + c);
            if (c != null && c.moveToFirst()) {
                do {
                    String fullPathString = c.getString(0);
                    existingFiles.remove(fullPathString);
                } while (c.moveToNext());
            }

            for (String fileToDelete : existingFiles) {
                if (false)
                    Log.v(TAG, "fileToDelete is " + fileToDelete);
                try {
                    (new File(fileToDelete)).delete();
                } catch (SecurityException ex) {
                }
            }

            Log.v(TAG, "/pruneDeadThumbnailFiles... " + c);
        } catch (RemoteException e) {
            // We will soon be killed...
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    static class MediaBulkDeleter {
        StringBuilder whereClause = new StringBuilder();
        ArrayList<String> whereArgs = new ArrayList<String>(100);
        final ContentProviderClient mProvider;
        final Uri mBaseUri;

        public MediaBulkDeleter(ContentProviderClient provider, Uri baseUri) {
            mProvider = provider;
            mBaseUri = baseUri;
        }

        public void delete(long id) throws RemoteException {
            if (whereClause.length() != 0) {
                whereClause.append(",");
            }
            whereClause.append("?");
            whereArgs.add("" + id);
            if (whereArgs.size() > 100) {
                flush();
            }
        }
        
        /*
        public void deleteAll() throws RemoteException {
        	mProvider.delete(mBaseUri, "", null);
        }
        */
        
        public void flush() throws RemoteException {
            int size = whereArgs.size();
            if (size > 0) {
                String [] foo = new String [size];
                foo = whereArgs.toArray(foo);
                int numrows = mProvider.delete(mBaseUri,
                        MediaStore.MediaColumns._ID + " IN (" +
                        whereClause.toString() + ")", foo);
                //Log.i("@@@@@@@@@", "rows deleted: " + numrows);
                whereClause.setLength(0);
                whereArgs.clear();
            }
        }
    }

    private boolean IsExternalStorageVolumeRoot(String filePath) {
        if (mStorageManager != null) {
            String[] list = mStorageManager.getVolumePaths();
            if (mExternalPaths.length != list.length)
                mExternalPaths = list;
        }
        for (String path : mExternalPaths) {
            if (path.equals(filePath))
                return true;
        }
        return false;
    }

    //[RTK Begin]
    private void postscan(final String[] directories, boolean[] needScanned) throws RemoteException {
    //[RTK End]

        if(ForceStop()==true){
            // allow GC to clean up
            mPlayLists = null;
            mMediaProvider = null;
            return;
        }
        
        // handle playlists last, after we know what media files are on the storage.
        if (mProcessPlaylists) {
            processPlayLists();
        }

        if (mOriginalCount == 0 && mImagesUri.equals(Images.Media.getContentUri("external")))
            pruneDeadThumbnailFiles();

        //[RTK Begin]
        // write the free space of each directory into the database file
        // By the information to determine whether to do scan each directory at this time
        if (mUseRtkMediaProvider) {
            for (int i = 0; i < directories.length; i++) {
                if(ForceStop()==true){
                    // allow GC to clean up
                    mPlayLists = null;
                    mMediaProvider = null;
                    return;
                }
                if (IsExternalStorageVolumeRoot(directories[i]))
                    mClient.doScanFile(directories[i], null, true, true);
                boolean bRetPrimary = mContext.getSystemService(StorageManager.class).getPrimaryVolume().getPath().equals(directories[i]);
                if (IsExternalStorageVolumeRoot(directories[i]) && !bRetPrimary) {
                    if (false == needScanned[i])
                        mMediaProvider.call("merge", directories[i], null);
                    else
                    {
                    	mMediaProvider.call("merge", directories[i], null);
                    	mMediaProvider.call("copy", directories[i], null);
                    }
                }
            }
        }

        if(ForceStop()==true){
            // allow GC to clean up
            mPlayLists = null;
            mMediaProvider = null;
            return;
        }
        reprocessPlayLists();
        //[RTK End]

        // allow GC to clean up
        mPlayLists.clear();
    }

    //[RTK Begin]
    private boolean isInTheDirectory(String directory, String path) {
        return path.startsWith(directory);
    }

    /*
    private void initialize(String volumeName, String path) {
        for (String directory : mExternalPaths) {
            if (isInTheDirectory(directory, path) && !mExternalStoragePath.equals(directory)) {
                volumeName += "-" + directory.substring(1+directory.lastIndexOf("/"));
                break;
            }
        }
        initialize(volumeName);
    }

    private void initialize(String volumeName, String[] directories) {
        if (directories.length == 1) {
                boolean bRetisExternalStorageRemovable = false;
                File initdir = new File(directories[0]);
	if(!directories[0].equals(mContext.getSystemService(StorageManager.class).getPrimaryVolume().getPath())){
                volumeName += "-" + directories[0].substring(1+directories[0].lastIndexOf("/"));
            }
        }
        initialize(volumeName);
    }
    */

    //[RTK End]
    private void releaseResources() {
        // release the DrmManagerClient resources
        if (mDrmManagerClient != null) {
            mDrmManagerClient.close();
            mDrmManagerClient = null;
        }
    }

    //[RTK Begin]
    private boolean needScanTheStorage(String filePath) {
        //1. query the entry with _DATA column and its free space value
        //2. compare the recorded free space with the current free space
        //3. if its difference exceeds a threadhold, return true; otherwise returen false
        Log.d(TAG, "needScanTheStorage: " + filePath);
        Cursor c = null;
        String where = null;
        String[] selectionArgs = null;
        if (filePath == null)
            return true;
        if ((mStorageManager == null) || ((mStorageManager != null) && mStorageManager.getVolume(filePath) == null)) {
            Log.e(TAG, "get StorageManager or StorageVolume is null");
            return true;
        }

        //  /storage/sd[a-z][1-9] or /storage/sd[a-z] 
        // where = "(("+Files.FileColumns.DATA+" LIKE '/storage/sd__') OR ("+Files.FileColumns.DATA+" LIKE '/storage/sd_'))"+" AND " + Files.FileColumns.STORAGE_ID + "=?";
        where = Files.FileColumns.STORAGE_ID + "=?";
        StorageVolume vol = mStorageManager.getStorageVolume(new File(filePath));
        String sid_string = Integer.toString(vol.getStorageId());
        selectionArgs = new String[] { sid_string };
        boolean needDoScan = true;
        try {
            Log.d(TAG, "mFilesUri " + mFilesUri);
            Log.d(TAG, "where " + where);
            Log.d(TAG, "selectionArgs " + sid_string);
            c = mMediaProvider.query(mFilesUri, FILES_FREESPACE_PROJECTION,
                    where, selectionArgs, null/*MediaStore.Files.FileColumns._ID*/, null);
            if (c == null) {
                Log.w(TAG, "get MediaProvider cursor is null");
                return true;
            }
            int num = c.getCount();
            if (num == 0) {
                if (c != null)
                    c.close();
                Log.w(TAG, "git file table with storage ID is null");
                return true;
            }
            //Log.d(TAG, "get " + num + " items");
            File file = new File(filePath);
            long freeSpace = file.getFreeSpace(); // free disk space in bytes
            while (c.moveToNext()) {
                long rowId = c.getLong(FILES_FREESPACE_ID_COLUMN_INDEX);
                String path = c.getString(FILES_FREESPACE_PATH_COLUMN_INDEX);
                int format = c.getInt(FILES_FREESPACE_FORMAT_COLUMN_INDEX);
                long free_space = c.getLong(FILES_FREESPACE_FREE_SPACE_COLUMN_INDEX);
                long lastModified = c.getLong(FILES_FREESPACE_DATE_MODIFIED_COLUMN_INDEX);
                if (Math.abs(free_space - freeSpace) < THRESHOLD_DIFFERENCE_FREE_SPACE) {
                    Log.d(TAG, "Directory: " + filePath + " doesn't need to scan");
                    needDoScan = false;
                    break;
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in MediaProvider.query(...): " + e.toString());
            return true;
        }
        finally {
            if (c != null) {
                c.close();
            }
        }
        Log.d(TAG, "needScanTheStorage " + needDoScan);
        return needDoScan;
    }

    public void setForceStopProperty(boolean stop) {
        if(mForceStop != stop)
        {
            setNativeForceStopProperty(stop);
            mForceStop = stop;
        }
    }

    public boolean isCurrentScanning() {
        return mScanning;
    }

    public boolean getFroceStopProperty() {
        return mForceStop;
    }
    //[RTK End]
    
    public void scanDirectories(String[] directories) {
        mScanning = true;
        if(ForceStop()==true){
            mScanning = false;
            return;
        }
        
        try {
            long start = System.currentTimeMillis();
            //[RTK Begin]
            if (mStorageManager == null) {
                mStorageManager = (StorageManager) mContext.getSystemService(Context.STORAGE_SERVICE);
                mExternalPaths = mStorageManager.getVolumePaths();
            }
            //initialize(volumeName, directories);
            mVolumeName = directories[0];
            //[RTK End]
            prescan(null, true);
            
            if(ForceStop()==true){
                mPlayLists = null;
                mMediaProvider = null;
                mScanning = false;
                return;
            }
            
            long prescan = System.currentTimeMillis();

            if (ENABLE_BULK_INSERTS) {
                // create MediaInserter for bulk inserts
                //mMediaInserter = new MediaInserter(mMediaProvider, 500);
                mMediaInserter = new MediaInserter(mMediaProvider, mPackageName, 10, 240, 1);
            }
            //[RTK Begin]
            boolean[] needScanned = new boolean[directories.length];
            //[RTK End]
            for (int i = 0; i < directories.length; i++) {

                if(ForceStop()==true){
                    mMediaInserter=null;
                    mPlayLists = null;
                    mMediaProvider = null;
                    mScanning = false;
                    return;
                }
                
                //[RTK Begin]
                needScanned[i] = false;
                if (mUseRtkMediaProvider) {
                    if (needScanTheStorage(directories[i])) {
                        needScanned[i] = true;
                        processDirectory(directories[i], mClient);
                    }
                }
                else
                    processDirectory(directories[i], mClient);
                //[RTK End]
            }
            if (ENABLE_BULK_INSERTS) {
                // flush remaining inserts
                mMediaInserter.flushAll();
                mMediaInserter = null;
            }

            long scan = System.currentTimeMillis();
            //[RTK Begin]
            postscan(directories, needScanned);
            //[RTK End]
            long end = System.currentTimeMillis();

            if (true) {
                Log.d(TAG, " prescan time: " + (prescan - start) + "ms\n");
                Log.d(TAG, "    scan time: " + (scan - prescan) + "ms\n");
                Log.d(TAG, "postscan time: " + (end - scan) + "ms\n");
                Log.d(TAG, "   total time: " + (end - start) + "ms\n");
            }
        } catch (SQLException e) {
            // this might happen if the SD card is removed while the media scanner is running
            Log.e(TAG, "SQLException in MediaScanner.scan()", e);
        } catch (UnsupportedOperationException e) {
            // this might happen if the SD card is removed while the media scanner is running
            Log.e(TAG, "UnsupportedOperationException in MediaScanner.scan()", e);
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in MediaScanner.scan()", e);
        } finally{
            mScanning = false;
            releaseResources();
        }
    }

    // this function is used to scan a single file
    public Uri scanSingleFile(String path, String mimeType) {
        try {
            //[RTK Begin]
            if (mStorageManager == null) {
                mStorageManager = (StorageManager) mContext.getSystemService(Context.STORAGE_SERVICE);
                mExternalPaths = mStorageManager.getVolumePaths();
            }
            //initialize(volumeName, path);
            //[RTK End]
            prescan(path, true);

            File file = new File(path);
            if (!file.exists()) {
                return null;
            }

            // lastModified is in milliseconds on Files.
            long lastModifiedSeconds = file.lastModified() / 1000;

            // always scan the file, so we can return the content://media Uri for existing files
            return mClient.doScanFile(path, mimeType, lastModifiedSeconds, file.length(),
                    false, true, MediaScanner.isNoMediaPath(path));
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in MediaScanner.scanFile()", e);
            return null;
        } finally {
            releaseResources();
        }
    }

//[RTK Begin]
    /**
     * BD folder identification rule:
     *   1. There are CLIPINFO, PLAYLIST and STREAM folders in the folder. Then the folder is a BD folder.
     *   2. There is a BDMV folder in the folder and there are CLIPINFO/CLIPINF, PLAYLIST and STREAM folders in BDMV folder,
     *      then the folder is a BD folder. For this case, the BDMV folder is not identified as a BD folder.
     *   3. In the root of storage disk, BDMV folder is not a BD folder.
     */
    private boolean isAVCHDFolder(String path) {
        try {
            File file = new File(path);
            FilenameFilter AVCHDFilter1 = new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    if (name.equals("BDMV")) {
                        // according the rule of identifying BD folder, BDMV folder in root directory
                        // is not identified as BD folder.
                        if (IsExternalStorageVolumeRoot(dir.getAbsolutePath()) == true)
                            return false;
                        return true;
                    }
                    else
                        return false;
                }
            };
            FilenameFilter AVCHDFilter2 = new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    if (/*name.equals("index.bdmv")  // according the rule, we just only
                        || name.equals("index.bdm")  // check if there are STREAM, PLAYLIST, CLIPINFO/CLIPINF
                        || */name.equals("STREAM")   // folders.
                        || name.equals("PLAYLIST")
                        || name.equals("CLIPINFO")
                        || name.equals("CLIPINF")
                        ) {
                        return true;
                    }
                    else
                        return false;
                }
            };
            /*
            FilenameFilter BDROMFilter = new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    if (name.equals("AUXDATA")
                        || name.equals("BDJO")
                        || name.equals("JAR")
                        || name.equals("META")
                        ) {
                        return true;
                    }
                    else
                        return false;
                }
            };*/
            String list[] = file.list(AVCHDFilter1);
            if (list == null) // not a folder
                return false;
            else if (list.length == 0) { // there is no BDMV folder.
                list = file.list(AVCHDFilter2); // check if there are CLIPINFO, PLAYLIST and STREAM folders
                if (list == null || list.length == 0) {
                    return false;
                } else if (list.length == 3) { // there are CLIPINFO, PLAYLIST, STREAM folders
                    return true;               // so it is identified as BD folder.
                } else {
                    return false; // should not run in this case
                }
            } else { // there is BDMV folder, then check if there are CLIPINFO, PLAYLIST and STREAM folders in BDMV
                file = new File(path+"/"+list[0]);
                list = file.list(AVCHDFilter2);
                if (list != null && list.length == 3) { // there are CLIPINFO, PLAYLIST, STREAM folders
                    return true;                        // so it is idenfied as BD folder.
                } else {
                    return false;
                }
                /*
                String bdromList[] = file.list(BDROMFilter);
                if (list == null || list.length == 0
                    || bdromList.length > 0)
                    return false;
                */
            }
            /*
            boolean hasIndexbdmv = false;
            boolean hasIndexbdm = false;
            for (String name : list) {
                if (name.equals("index.bdmv"))
                    hasIndexbdmv = true;
                else if (name.equals("index.bdm"))
                    hasIndexbdm = true;
            }
            if (true == hasIndexbdm)
                return true;
            else if (true == hasIndexbdmv && list.length >= 4)
                return true;
            else
                return false;
            */
        }
        catch (Exception e) {
            Log.e(TAG, "exception: ", e);
        }
        return false;
    }

    /**
     * The DVD folder identification rule:
     *   1. The folder has VIDEO_TS.INFO file. Then it is a DVD folder
     *   2. The folder has a VIDEO_TS folder which has a VIDEO_TS.INFO file. Then it is a DVD folder
     *      For this case, the VIDEO_TS folder is not identified as DVD folder
     *   3. In the root of storage, VIDEO_TS folder is not a DVD folder.
     */
    private boolean isDVDFolder(String path) {
        try {
            File file = new File(path);
            FilenameFilter DVDFilter1 = new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    if (name.equals("VIDEO_TS")
                        /*|| name.equals("DVD_RTAV")*/
                        ) {
                        if (IsExternalStorageVolumeRoot(dir.getAbsolutePath()) == true) {
                            return false; // VIDEO_TS in the root of the storage is not a DVD folder.
                        }
                        return true;
                    }
                    else
                        return false;
                }
            };
            FilenameFilter DVDFilter2 = new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    if (name.equals("VIDEO_TS.IFO")
                            /*
                        || name.equals("VR_MANGR.IFO")
                        || name.equals("VR_MOVIE.VRO") */
                        ) {
                        return true;
                    }
                    else
                        return false;
                }
            };
            String list[] = file.list(DVDFilter1);
            if (list!=null && list.length > 0) { // there is VIDEO_TS folder in it
                file = new File(path + "/" + list[0]);
                list = file.list(DVDFilter2);
            } else {
                list = file.list(DVDFilter2);
            }
            if (list!=null && list.length > 0)
		return true;
            else
                return false;
        }
        catch (Exception e) {
            Log.e(TAG, "exception: ", e);
        }
        return false;
    }
//[RTK End]
//
    private static boolean isNoMediaFile(String path) {
        File file = new File(path);
        if (file.isDirectory()) return false;

        // special case certain file names
        // I use regionMatches() instead of substring() below
        // to avoid memory allocation
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash + 2 < path.length()) {
            // ignore those ._* files created by MacOS
            if (path.regionMatches(lastSlash + 1, "._", 0, 2)) {
                return true;
            }

            // ignore album art files created by Windows Media Player:
            // Folder.jpg, AlbumArtSmall.jpg, AlbumArt_{...}_Large.jpg
            // and AlbumArt_{...}_Small.jpg
            if (path.regionMatches(true, path.length() - 4, ".jpg", 0, 4)) {
                if (path.regionMatches(true, lastSlash + 1, "AlbumArt_{", 0, 10) ||
                        path.regionMatches(true, lastSlash + 1, "AlbumArt.", 0, 9)) {
                    return true;
                }
                int length = path.length() - lastSlash - 1;
                if ((length == 17 && path.regionMatches(
                        true, lastSlash + 1, "AlbumArtSmall", 0, 13)) ||
                        (length == 10
                         && path.regionMatches(true, lastSlash + 1, "Folder", 0, 6))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static HashMap<String,String> mNoMediaPaths = new HashMap<String,String>();
    private static HashMap<String,String> mMediaPaths = new HashMap<String,String>();

    /* MediaProvider calls this when a .nomedia file is added or removed */
    public static void clearMediaPathCache(boolean clearMediaPaths, boolean clearNoMediaPaths) {
        synchronized (MediaScanner.class) {
            if (clearMediaPaths) {
                mMediaPaths.clear();
            }
            if (clearNoMediaPaths) {
                mNoMediaPaths.clear();
            }
        }
    }

    public static boolean isNoMediaPath(String path) {
        if (path == null) {
            return false;
        }
        // return true if file or any parent directory has name starting with a dot
        if (path.indexOf("/.") >= 0) {
            return true;
        }

        int firstSlash = path.lastIndexOf('/');
        if (firstSlash <= 0) {
            return false;
        }
        String parent = path.substring(0,  firstSlash);

        synchronized (MediaScanner.class) {
            if (mNoMediaPaths.containsKey(parent)) {
                return true;
            } else if (!mMediaPaths.containsKey(parent)) {
                // check to see if any parent directories have a ".nomedia" file
                // start from 1 so we don't bother checking in the root directory
                int offset = 1;
                while (offset >= 0) {
                    int slashIndex = path.indexOf('/', offset);
                    if (slashIndex > offset) {
                        slashIndex++; // move past slash
                        File file = new File(path.substring(0, slashIndex) + ".nomedia");
                        if (file.exists()) {
                            // we have a .nomedia in one of the parent directories
                            mNoMediaPaths.put(parent, "");
                            return true;
                        }
                    }
                    offset = slashIndex;
                }
                mMediaPaths.put(parent, "");
            }
        }

        return isNoMediaFile(path);
    }

    public void scanMtpFile(String path, int objectHandle, int format) {
        //[RTK Begin]
        //initialize(volumeName, path);
        //[RTK End]
        MediaFile.MediaFileType mediaFileType = MediaFile.getFileType(path);
        int fileType = (mediaFileType == null ? 0 : mediaFileType.fileType);
        File file = new File(path);
        long lastModifiedSeconds = file.lastModified() / 1000;

        if (!MediaFile.isAudioFileType(fileType) && !MediaFile.isVideoFileType(fileType) &&
            !MediaFile.isImageFileType(fileType) && !MediaFile.isPlayListFileType(fileType) &&
            !MediaFile.isDrmFileType(fileType)) {

            // no need to use the media scanner, but we need to update last modified and file size
            ContentValues values = new ContentValues();
            values.put(Files.FileColumns.SIZE, file.length());
            values.put(Files.FileColumns.DATE_MODIFIED, lastModifiedSeconds);
            try {
                String[] whereArgs = new String[] {  Integer.toString(objectHandle) };
                mMediaProvider.update(Files.getMtpObjectsUri(mVolumeName), values,
                        "_id=?", whereArgs);
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException in scanMtpFile", e);
            }
            return;
        }

        mMtpObjectHandle = objectHandle;
        Cursor fileList = null;
        try {
            if (MediaFile.isPlayListFileType(fileType)) {
                // build file cache so we can look up tracks in the playlist
                prescan(null, true);

                FileEntry entry = makeEntryFor(path);
                if (entry != null) {
                    fileList = mMediaProvider.query(mFilesUri,
                            FILES_PRESCAN_PROJECTION, null, null, null, null);
                    processPlayList(entry, fileList);
                }
            } else {
                // MTP will create a file entry for us so we don't want to do it in prescan
                prescan(path, false);

                // always scan the file, so we can return the content://media Uri for existing files
                mClient.doScanFile(path, mediaFileType.mimeType, lastModifiedSeconds, file.length(),
                    (format == MtpConstants.FORMAT_ASSOCIATION), true, isNoMediaPath(path));
            }
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in MediaScanner.scanFile()", e);
        } finally {
            mMtpObjectHandle = 0;
            if (fileList != null) {
                fileList.close();
            }
            releaseResources();
        }
    }

    FileEntry makeEntryFor(String path) {
        String where;
        String[] selectionArgs;
        Cursor c = null;
        try {
            where = Files.FileColumns.DATA + "=?";
            selectionArgs = new String[] { path };
            c = mMediaProvider.query(mFilesUriNoNotify, FILES_PRESCAN_PROJECTION,
                    where, selectionArgs, null, null);
            if(c == null || c.getCount() == 0)
                return null;
            if (c.moveToFirst()) {
                long rowId = c.getLong(FILES_PRESCAN_ID_COLUMN_INDEX);
                int format = c.getInt(FILES_PRESCAN_FORMAT_COLUMN_INDEX);
                long lastModified = c.getLong(FILES_PRESCAN_DATE_MODIFIED_COLUMN_INDEX);
                return new FileEntry(rowId, path, lastModified, format);
            }
        } catch (RemoteException e) {
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return null;
    }

    // returns the number of matching file/directory names, starting from the right
    private int matchPaths(String path1, String path2) {
        int result = 0;
        int end1 = path1.length();
        int end2 = path2.length();

        while (end1 > 0 && end2 > 0) {
            int slash1 = path1.lastIndexOf('/', end1 - 1);
            int slash2 = path2.lastIndexOf('/', end2 - 1);
            int backSlash1 = path1.lastIndexOf('\\', end1 - 1);
            int backSlash2 = path2.lastIndexOf('\\', end2 - 1);
            int start1 = (slash1 > backSlash1 ? slash1 : backSlash1);
            int start2 = (slash2 > backSlash2 ? slash2 : backSlash2);
            if (start1 < 0) start1 = 0; else start1++;
            if (start2 < 0) start2 = 0; else start2++;
            int length = end1 - start1;
            if (end2 - start2 != length) break;
            if (path1.regionMatches(true, start1, path2, start2, length)) {
                result++;
                end1 = start1 - 1;
                end2 = start2 - 1;
            } else break;
        }

        return result;
    }

    private boolean matchEntries(long rowId, String data) {

        int len = mPlaylistEntries.size();
        boolean done = true;
        for (int i = 0; i < len; i++) {
            PlaylistEntry entry = mPlaylistEntries.get(i);
            if (entry.bestmatchlevel == Integer.MAX_VALUE) {
                continue; // this entry has been matched already
            }
            done = false;
            if (data.equalsIgnoreCase(entry.path)) {
                entry.bestmatchid = rowId;
                entry.bestmatchlevel = Integer.MAX_VALUE;
                continue; // no need for path matching
            }

            int matchLength = matchPaths(data, entry.path);
            if (matchLength > entry.bestmatchlevel) {
                entry.bestmatchid = rowId;
                entry.bestmatchlevel = matchLength;
            }
        }
        return done;
    }

    private void cachePlaylistEntry(String line, String playListDirectory) {
        PlaylistEntry entry = new PlaylistEntry();
        // watch for trailing whitespace
        int entryLength = line.length();
        while (entryLength > 0 && Character.isWhitespace(line.charAt(entryLength - 1))) entryLength--;
        // path should be longer than 3 characters.
        // avoid index out of bounds errors below by returning here.
        if (entryLength < 3) return;
        if (entryLength < line.length()) line = line.substring(0, entryLength);

        // does entry appear to be an absolute path?
        // look for Unix or DOS absolute paths
        char ch1 = line.charAt(0);
        boolean fullPath = (ch1 == '/' ||
                (Character.isLetter(ch1) && line.charAt(1) == ':' && line.charAt(2) == '\\'));
        // if we have a relative path, combine entry with playListDirectory
        if (!fullPath)
            line = playListDirectory + line;
        entry.path = line;
        //FIXME - should we look for "../" within the path?

        mPlaylistEntries.add(entry);
    }

    private void processCachedPlaylist(Cursor fileList, ContentValues values, Uri playlistUri) {
        fileList.moveToPosition(-1);
        while (fileList.moveToNext()) {
            long rowId = fileList.getLong(FILES_PRESCAN_ID_COLUMN_INDEX);
            String data = fileList.getString(FILES_PRESCAN_PATH_COLUMN_INDEX);
            if (matchEntries(rowId, data)) {
                break;
            }
        }

        int len = mPlaylistEntries.size();
        int index = 0;
        for (int i = 0; i < len; i++) {
            PlaylistEntry entry = mPlaylistEntries.get(i);
            if (entry.bestmatchlevel > 0) {
                try {
                    values.clear();
                    values.put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, Integer.valueOf(index));
                    values.put(MediaStore.Audio.Playlists.Members.AUDIO_ID, Long.valueOf(entry.bestmatchid));
                    mMediaProvider.insert(playlistUri, values);
                    index++;
                } catch (RemoteException e) {
                    Log.e(TAG, "RemoteException in MediaScanner.processCachedPlaylist()", e);
                    return;
                }
            }
        }
        mPlaylistEntries.clear();
    }

    private void processM3uPlayList(String path, String playListDirectory, Uri uri,
            ContentValues values, Cursor fileList) {
        BufferedReader reader = null;
        try {
            File f = new File(path);
            if (f.exists()) {
                reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(f)), 8192);
                String line = reader.readLine();
                mPlaylistEntries.clear();
                while (line != null) {
                    // ignore comment lines, which begin with '#'
                    if (line.length() > 0 && line.charAt(0) != '#') {
                        cachePlaylistEntry(line, playListDirectory);
                    }
                    line = reader.readLine();
                }

                processCachedPlaylist(fileList, values, uri);
            }
        } catch (IOException e) {
            Log.e(TAG, "IOException in MediaScanner.processM3uPlayList()", e);
        } finally {
            try {
                if (reader != null)
                    reader.close();
            } catch (IOException e) {
                Log.e(TAG, "IOException in MediaScanner.processM3uPlayList()", e);
            }
        }
    }

    private void processPlsPlayList(String path, String playListDirectory, Uri uri,
            ContentValues values, Cursor fileList) {
        BufferedReader reader = null;
        try {
            File f = new File(path);
            if (f.exists()) {
                reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(f)), 8192);
                String line = reader.readLine();
                mPlaylistEntries.clear();
                while (line != null) {
                    // ignore comment lines, which begin with '#'
                    if (line.startsWith("File")) {
                        int equals = line.indexOf('=');
                        if (equals > 0) {
                            cachePlaylistEntry(line.substring(equals + 1), playListDirectory);
                        }
                    }
                    line = reader.readLine();
                }

                processCachedPlaylist(fileList, values, uri);
            }
        } catch (IOException e) {
            Log.e(TAG, "IOException in MediaScanner.processPlsPlayList()", e);
        } finally {
            try {
                if (reader != null)
                    reader.close();
            } catch (IOException e) {
                Log.e(TAG, "IOException in MediaScanner.processPlsPlayList()", e);
            }
        }
    }

    class WplHandler implements ElementListener {

        final ContentHandler handler;
        String playListDirectory;

        public WplHandler(String playListDirectory, Uri uri, Cursor fileList) {
            this.playListDirectory = playListDirectory;

            RootElement root = new RootElement("smil");
            Element body = root.getChild("body");
            Element seq = body.getChild("seq");
            Element media = seq.getChild("media");
            media.setElementListener(this);

            this.handler = root.getContentHandler();
        }

        @Override
        public void start(Attributes attributes) {
            String path = attributes.getValue("", "src");
            if (path != null) {
                cachePlaylistEntry(path, playListDirectory);
            }
        }

       @Override
       public void end() {
       }

        ContentHandler getContentHandler() {
            return handler;
        }
    }

    private void processWplPlayList(String path, String playListDirectory, Uri uri,
            ContentValues values, Cursor fileList) {
        FileInputStream fis = null;
        try {
            File f = new File(path);
            if (f.exists()) {
                fis = new FileInputStream(f);

                mPlaylistEntries.clear();
                Xml.parse(fis, Xml.findEncodingByName("UTF-8"),
                        new WplHandler(playListDirectory, uri, fileList).getContentHandler());

                processCachedPlaylist(fileList, values, uri);
            }
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fis != null)
                    fis.close();
            } catch (IOException e) {
                Log.e(TAG, "IOException in MediaScanner.processWplPlayList()", e);
            }
        }
    }

    private void processPlayList(FileEntry entry, Cursor fileList) throws RemoteException {
        String path = entry.mPath;
        ContentValues values = new ContentValues();
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0) throw new IllegalArgumentException("bad path " + path);
        Uri uri, membersUri;
        long rowId = entry.mRowId;

        // make sure we have a name
        String name = values.getAsString(MediaStore.Audio.Playlists.NAME);
        if (name == null) {
            name = values.getAsString(MediaStore.MediaColumns.TITLE);
            if (name == null) {
                // extract name from file name
                int lastDot = path.lastIndexOf('.');
                name = (lastDot < 0 ? path.substring(lastSlash + 1)
                        : path.substring(lastSlash + 1, lastDot));
            }
        }

        values.put(MediaStore.Audio.Playlists.NAME, name);
        values.put(MediaStore.Audio.Playlists.DATE_MODIFIED, entry.mLastModified);

        if (rowId == 0) {
            values.put(MediaStore.Audio.Playlists.DATA, path);
            uri = mMediaProvider.insert(mPlaylistsUri, values);
            rowId = ContentUris.parseId(uri);
            membersUri = Uri.withAppendedPath(uri, Playlists.Members.CONTENT_DIRECTORY);
        } else {
            uri = ContentUris.withAppendedId(mPlaylistsUri, rowId);
            mMediaProvider.update(uri, values, null, null);

            // delete members of existing playlist
            membersUri = Uri.withAppendedPath(uri, Playlists.Members.CONTENT_DIRECTORY);
            mMediaProvider.delete(membersUri, null, null);
        }

        String playListDirectory = path.substring(0, lastSlash + 1);
        MediaFile.MediaFileType mediaFileType = MediaFile.getFileType(path);
        int fileType = (mediaFileType == null ? 0 : mediaFileType.fileType);

        if (fileType == MediaFile.FILE_TYPE_M3U) {
            processM3uPlayList(path, playListDirectory, membersUri, values, fileList);
        } else if (fileType == MediaFile.FILE_TYPE_PLS) {
            processPlsPlayList(path, playListDirectory, membersUri, values, fileList);
        } else if (fileType == MediaFile.FILE_TYPE_WPL) {
            processWplPlayList(path, playListDirectory, membersUri, values, fileList);
        }
    }

    private void processPlayLists() throws RemoteException {

        if(ForceStop()==true)   return;
        
        Iterator<FileEntry> iterator = mPlayLists.iterator();
        Cursor fileList = null;
        try {
            // use the files uri and projection because we need the format column,
            // but restrict the query to just audio files
            fileList = mMediaProvider.query(Uri.parse("content://media/external/file"), FILES_PRESCAN_PROJECTION,
                    "media_type=2", null, null, null);
            while (iterator.hasNext()) {

                if(ForceStop()==true){
                    fileList.close();
                    return;
                }
                
                FileEntry entry = iterator.next();
                // only process playlist files if they are new or have been modified since the last scan
                if (entry.mLastModifiedChanged) {
                    processPlayList(entry, fileList);
                }
            }
        } catch (RemoteException e1) {
        } finally {
            if (fileList != null) {
                fileList.close();
            }
        }
    }

    private void reprocessPlayLists() throws RemoteException {

        if(ForceStop()==true)   return;
        
        // Get audio table data to rebuild PlayLists list.
        ArrayList<FileEntry> PlayLists = new ArrayList<FileEntry>();
        Cursor cursor_playlists = null;
        try {
            String[] AUDIO_PLAYLISTS_PROJECTION = new String[] { "_data",
                    "date_modified" };
            // query audio_playlists table view.
            cursor_playlists = mMediaProvider.query(
                    Uri.parse("content://media/external/audio/playlists"),
                    AUDIO_PLAYLISTS_PROJECTION, null, null, null, null);
            if (cursor_playlists != null && cursor_playlists.moveToFirst()) {
                // mPlayLists.clear();
                do {
                    String _data = cursor_playlists.getString(0);
                    long date_modified = cursor_playlists.getLong(1);
                    FileEntry entry = new FileEntry(0, _data, date_modified, 0);
                    entry.mLastModifiedChanged = true;
                    PlayLists.add(entry);
                } while (cursor_playlists.moveToNext());
            }
        } catch (RemoteException e1) {
            Log.d(TAG, "reprocessPlayLists cursor_playlists Exception:" + e1);
            return;
        } finally {
            if (cursor_playlists != null)
                cursor_playlists.close();
        }

        // Erase all data of audio_playlists_map table
        mMediaProvider.call("delete_all_audio_playlists_map",
                null, null);

        // re create audio_playlists_map...
        Iterator<FileEntry> iterator = PlayLists.iterator();
        Cursor fileList = null;
        try {
            // use the files uri and projection because we need the format
            // column,
            // but restrict the query to just audio files
            fileList = mMediaProvider.query(
                    Uri.parse("content://media/external/file"),
                    FILES_PRESCAN_PROJECTION, "media_type=2", null, null, null);
            if(fileList==null)
                return;
            
            while (iterator.hasNext()) {

                if(ForceStop()==true){
                    fileList.close();
                    fileList=null;
                    return;
                }
                
                FileEntry entry = iterator.next();
                // only process playlist files if they are new or have been
                // modified since the last scan
                if (entry.mLastModifiedChanged) {

                    String path = entry.mPath;
                    ContentValues values = new ContentValues();
                    int lastSlash = path.lastIndexOf('/');
                    if (lastSlash < 0)
                        throw new IllegalArgumentException("bad path " + path);

                    // make sure we have a name
                    String name = values
                            .getAsString(MediaStore.Audio.Playlists.NAME);
                    if (name == null) {
                        name = values
                                .getAsString(MediaStore.MediaColumns.TITLE);
                        if (name == null) {
                            // extract name from file name
                            int lastDot = path.lastIndexOf('.');
                            name = (lastDot < 0 ? path.substring(lastSlash + 1)
                                    : path.substring(lastSlash + 1, lastDot));
                        }
                    }

                    values.put(MediaStore.Audio.Playlists.NAME, name);
                    values.put(MediaStore.Audio.Playlists.DATE_MODIFIED,
                            entry.mLastModified);

                    Cursor cursor_playlists_id = null;
                    try {
                        String[] AUDIO_PLAYLISTS_ID_PROJECTION = new String[] { "_id", };
                        String where = "media_type=4 and _data='" + path + "'";
                        cursor_playlists_id = mMediaProvider.query(
                                Uri.parse("content://media/external/file"),
                                AUDIO_PLAYLISTS_ID_PROJECTION, where, null,
                                null, null);
                        if (cursor_playlists_id != null
                                && cursor_playlists_id.moveToFirst()) {
                            do {
                                
                                if(ForceStop()==true){
                                    cursor_playlists_id.close();
                                    cursor_playlists_id=null;
                                    fileList.close();
                                    fileList=null;
                                    return;
                                }
                                
                                long _id = cursor_playlists_id.getLong(0);
                                Uri members_uri = ContentUris.withAppendedId(
                                        mPlaylistsUri, _id);
                                members_uri = Uri.withAppendedPath(members_uri,
                                        Playlists.Members.CONTENT_DIRECTORY);

                                String playListDirectory = path.substring(0,
                                        lastSlash + 1);
                                MediaFile.MediaFileType mediaFileType = MediaFile
                                        .getFileType(path);
                                int fileType = (mediaFileType == null ? 0
                                        : mediaFileType.fileType);

                                if (fileType == MediaFile.FILE_TYPE_M3U) {
                                    processM3uPlayList(path, playListDirectory,
                                            members_uri, values, fileList);
                                } else if (fileType == MediaFile.FILE_TYPE_PLS) {
                                    processPlsPlayList(path, playListDirectory,
                                            members_uri, values, fileList);
                                } else if (fileType == MediaFile.FILE_TYPE_WPL) {
                                    processWplPlayList(path, playListDirectory,
                                            members_uri, values, fileList);
                                }

                            } while (cursor_playlists_id.moveToNext());
                        }

                    } catch (RemoteException e1) {

                    } finally {
                        if (cursor_playlists_id != null)
                            cursor_playlists_id.close();
                    }
                }
            }
        } catch (RemoteException e1) {
        } finally {
            if (fileList != null) {
                fileList.close();
            }
        }

    }
    
    private native void processDirectory(String path, MediaScannerClient client);
    private native void processFile(String path, String mimeType, MediaScannerClient client);
    public native void setLocale(String locale);
    public native void setNativeForceStopProperty(boolean stop);

    public native byte[] extractAlbumArt(FileDescriptor fd);

    private static native final void native_init();
    private native final void native_setup();
    private native final void native_finalize();

    @Override
    public void close() {
        mCloseGuard.close();
        if (mClosed.compareAndSet(false, true)) {
            mMediaProvider.close();
            native_finalize();
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            mCloseGuard.warnIfOpen();
            close();
        } finally {
            super.finalize();
        }
    }
}
