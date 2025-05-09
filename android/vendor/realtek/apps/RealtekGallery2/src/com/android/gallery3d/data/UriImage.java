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

package com.android.gallery3d.data;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.BitmapRegionDecoder;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.app.PanoramaMetadataSupport;
import com.android.gallery3d.common.BitmapUtils;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.ThreadPool.CancelListener;
import com.android.gallery3d.util.ThreadPool.Job;
import com.android.gallery3d.util.ThreadPool.JobContext;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;

import com.realtek.app.AirFunManager;
import com.realtek.bitmapfun.util.HeifReader;

public class UriImage extends MediaItem {
    private static final String TAG = "UriImage";

    private static final int STATE_INIT = 0;
    private static final int STATE_DOWNLOADING = 1;
    private static final int STATE_DOWNLOADED = 2;
    private static final int STATE_ERROR = -1;

    private final Uri mUri;
    private final String mContentType;

    private DownloadCache.Entry mCacheEntry;
    private ParcelFileDescriptor mFileDescriptor;
    private int mState = STATE_INIT;
    private int mWidth;
    private int mHeight;
    private int mRotation;
    private PanoramaMetadataSupport mPanoramaMetadata = new PanoramaMetadataSupport(this);

    private GalleryApp mApplication;

    public UriImage(GalleryApp application, Path path, Uri uri, String contentType) {
        super(path, nextVersionNumber());
        mUri = uri;
        mApplication = Utils.checkNotNull(application);
        mContentType = contentType;
    }

    @Override
    public Job<Bitmap> requestImage(int type) {
        if((getSupportedOperations()&SUPPORT_FULL_IMAGE)==0 && type==MediaItem.TYPE_THUMBNAIL)
            type=MediaItem.TYPE_LARGETHUMBNAIL;

        if(GalleryUtils.useReal4KPhoto() == true) {
            if(getMimeType().toLowerCase().indexOf("jpeg")>=0 || getMimeType().toLowerCase().indexOf("jpg")>=0)
                type=MediaItem.TYPE_LARGETHUMBNAIL;
        }
        return new BitmapJob(type);
    }

    @Override
    public Job<BitmapRegionDecoder> requestLargeImage() {
        Log.d(TAG, "requestLargeImage : " + mUri);
        return new RegionDecoderJob();
    }

    private void openFileOrDownloadTempFile(JobContext jc) {
        int state = openOrDownloadInner(jc);
        synchronized (this) {
            mState = state;
            if (mState != STATE_DOWNLOADED) {
                if (mFileDescriptor != null) {
                    Utils.closeSilently(mFileDescriptor);
                    mFileDescriptor = null;
                }
            }
            notifyAll();
        }
    }

    private int openOrDownloadInner(JobContext jc) {
        String scheme = mUri.getScheme();
        if (ContentResolver.SCHEME_CONTENT.equals(scheme)
                || ContentResolver.SCHEME_ANDROID_RESOURCE.equals(scheme)
                || ContentResolver.SCHEME_FILE.equals(scheme)) {
            try {
                if (MIME_TYPE_JPEG.equalsIgnoreCase(mContentType)) {
                    InputStream is = mApplication.getContentResolver()
                            .openInputStream(mUri);
                    mRotation = Exif.getOrientation(is);
                    Utils.closeSilently(is);
                }
                else if(mContentType.equalsIgnoreCase("image/heic") || mContentType.equalsIgnoreCase("image/heif")) {
                    HeifReader heifReader = new HeifReader(mApplication.getAndroidContext(), mUri.toString(), null);
                    mRotation = heifReader.getRotation();
                }
                mFileDescriptor = mApplication.getContentResolver()
                        .openFileDescriptor(mUri, "r");
                if (jc.isCancelled()) return STATE_INIT;
                return STATE_DOWNLOADED;
            } catch (FileNotFoundException e) {
                Log.w(TAG, "fail to open: " + mUri, e);
                return STATE_ERROR;
            }
        } else if(scheme.equals("directconnect")) {
            try {
                mCacheEntry = mApplication.getDownloadCache().download(jc, mUri );
                if (jc.isCancelled()) return STATE_INIT;
                if (mCacheEntry == null) {
                    Log.w(TAG, "download failed " + mUri);
                    return STATE_ERROR;
                }
                mFileDescriptor = ParcelFileDescriptor.open(
                    mCacheEntry.cacheFile, ParcelFileDescriptor.MODE_READ_ONLY);
                return STATE_DOWNLOADED;
            } catch (Throwable t) {
                Log.w(TAG, "download error", t);
                return STATE_ERROR;
            }
        } else {
            try {
                URL url = new URI(mUri.toString()).toURL();
                mCacheEntry = mApplication.getDownloadCache().download(jc, url);
                if (jc.isCancelled()) return STATE_INIT;
                if (mCacheEntry == null) {
                    Log.w(TAG, "download failed " + url);
                    return STATE_ERROR;
                }
                if (MIME_TYPE_JPEG.equalsIgnoreCase(mContentType)) {
                    InputStream is = new FileInputStream(mCacheEntry.cacheFile);
                    mRotation = Exif.getOrientation(is);
                    Utils.closeSilently(is);
                }
                mFileDescriptor = ParcelFileDescriptor.open(
                        mCacheEntry.cacheFile, ParcelFileDescriptor.MODE_READ_ONLY);
                return STATE_DOWNLOADED;
            } catch (Throwable t) {
                Log.w(TAG, "download error", t);
                return STATE_ERROR;
            }
        }
    }

    private boolean prepareInputFile(JobContext jc) {
        jc.setCancelListener(new CancelListener() {
            @Override
            public void onCancel() {
                synchronized (this) {
                    notifyAll();
                }
            }
        });

        if(mFileDescriptor == null) {
            mState = STATE_INIT;
        }

        while (true) {
            synchronized (this) {
                if (jc.isCancelled()) return false;
                if (mState == STATE_INIT) {
                    mState = STATE_DOWNLOADING;
                    // Then leave the synchronized block and continue.
                } else if (mState == STATE_ERROR) {
                    return false;
                } else if (mState == STATE_DOWNLOADED) {
                    return true;
                } else /* if (mState == STATE_DOWNLOADING) */ {
                    try {
                        wait();
                    } catch (InterruptedException ex) {
                        // ignored.
                    }
                    continue;
                }
            }
            // This is only reached for STATE_INIT->STATE_DOWNLOADING
            openFileOrDownloadTempFile(jc);
        }
    }

    private class RegionDecoderJob implements Job<BitmapRegionDecoder> {
        @Override
        public BitmapRegionDecoder run(JobContext jc) {
            if (!prepareInputFile(jc)) return null;
            BitmapRegionDecoder decoder;
            if (mCacheEntry != null && mCacheEntry.cacheFile != null) {
                decoder = DecodeUtils.createBitmapRegionDecoder(
                                    jc, mCacheEntry.cacheFile.getPath(), false);
            }
            else {
                decoder = DecodeUtils.createBitmapRegionDecoder(
                                    jc, mFileDescriptor.getFileDescriptor(), false);
            }
            if (mFileDescriptor != null) {
                Utils.closeSilently(mFileDescriptor);
                mFileDescriptor = null;
            }
            if(decoder!=null) {
                mWidth = decoder.getWidth();
                mHeight = decoder.getHeight();
            }
            return decoder;
        }
    }

    private class BitmapJob implements Job<Bitmap> {
        private int mType;

        protected BitmapJob(int type) {
            mType = type;
        }

        @Override
        public Bitmap run(JobContext jc) {
            if (!prepareInputFile(jc)) return null;
            Bitmap bitmap;
            if(mUri.toString().toLowerCase().endsWith("heic") || mUri.toString().toLowerCase().endsWith("heic")) {
                HeifReader heifReader = new HeifReader(mApplication.getAndroidContext(), mUri.toString(), null);
                bitmap = heifReader.decodeHeif();
            }
            else {
                int targetSize = MediaItem.getTargetSize(mType);
                Options options = new Options();
                options.inPreferredConfig = Config.ARGB_8888;
                Log.d(TAG, "decodeThumbnail : " + mUri);

                DecodeUtils.decodeBounds(jc,
                        mFileDescriptor.getFileDescriptor(), options);
                mWidth = options.outWidth;
                mHeight = options.outHeight;
                bitmap = DecodeUtils.decodeThumbnail(jc,
                        mFileDescriptor.getFileDescriptor(), options, targetSize, mType);
                if (mFileDescriptor != null) {
                    Utils.closeSilently(mFileDescriptor);
                    mFileDescriptor = null;
                }

                if (jc.isCancelled() || bitmap == null) {
                    return null;
                }

                if (mType == MediaItem.TYPE_MICROTHUMBNAIL) {
                    bitmap = BitmapUtils.resizeAndCropCenter(bitmap, targetSize, true);
                } else {
                    bitmap = BitmapUtils.resizeDownBySideLength(bitmap, targetSize, true);
                }
            }
            return bitmap;
        }
    }

    @Override
    public int getSupportedOperations() {
        int supported = SUPPORT_PRINT | SUPPORT_SETAS;
        if (isSharable()) supported |= SUPPORT_SHARE;
        if (BitmapUtils.isSupportedByRegionDecoder(mContentType)) {
            supported |= SUPPORT_EDIT | SUPPORT_FULL_IMAGE;
        }
        if (BitmapUtils.isRotationSupported(mContentType)) {
            supported |= SUPPORT_ROTATE;
        }
        return supported;
    }

    @Override
    public void getPanoramaSupport(PanoramaSupportCallback callback) {
        mPanoramaMetadata.getPanoramaSupport(mApplication, callback);
    }

    @Override
    public void clearCachedPanoramaSupport() {
        mPanoramaMetadata.clearCachedValues();
    }

    @Override
    public void rotate(int degrees) {
        GalleryUtils.assertNotInRenderThread();
        int rotation = (this.mRotation + degrees) % 360;
        if (rotation < 0) rotation += 360;
        this.mRotation= rotation;
    }

    private boolean isSharable() {
        // We cannot grant read permission to the receiver since we put
        // the data URI in EXTRA_STREAM instead of the data part of an intent
        // And there are issues in MediaUploader and Bluetooth file sender to
        // share a general image data. So, we only share for local file.
        return ContentResolver.SCHEME_FILE.equals(mUri.getScheme());
    }

    @Override
    public int getMediaType() {
        return MEDIA_TYPE_IMAGE;
    }

    @Override
    public Uri getContentUri() {
        return mUri;
    }

    @Override
    public String getFilePath() {
        return mUri.toString();
    }
    
    @Override
    public MediaDetails getDetails() {
        MediaDetails details = super.getDetails();
        if (mWidth != 0 && mHeight != 0) {
            details.addDetail(MediaDetails.INDEX_WIDTH, mWidth);
            details.addDetail(MediaDetails.INDEX_HEIGHT, mHeight);
        }
        if (mContentType != null) {
            details.addDetail(MediaDetails.INDEX_MIMETYPE, mContentType);
        }
        details.addDetail(MediaDetails.INDEX_ORIENTATION, Integer.valueOf(mRotation));
        if (ContentResolver.SCHEME_FILE.equals(mUri.getScheme())) {
            String filePath = mUri.getPath();
            details.addDetail(MediaDetails.INDEX_PATH, filePath);
            MediaDetails.extractExifInfo(details, filePath);
        }
        else if(mUri.getScheme().equals("http")){
            details.addDetail(MediaDetails.INDEX_PATH, mUri);
            if(mCacheEntry != null && mCacheEntry.cacheFile != null) {
                try {
                    InputStream is = new FileInputStream(mCacheEntry.cacheFile);
                    MediaDetails.extractExifInfo(details, is);
                    Utils.closeSilently(is);
                } catch (FileNotFoundException e) {
                    Log.w(TAG, "fail to open: " + mUri, e);
                }
            }
        }
        return details;
    }

    @Override
    public String getMimeType() {
        return mContentType;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (mFileDescriptor != null) {
                Utils.closeSilently(mFileDescriptor);
                mFileDescriptor = null;
            }
        } finally {
            super.finalize();
        }
    }

    @Override
    public int getWidth() {
        return mWidth;
    }

    @Override
    public int getHeight() {
        return mHeight;
    }

    @Override
    public int getRotation() {
        return mRotation;
    }

    public void resetState() {
        //Log.d(TAG, "resetState");
        mState = STATE_INIT;
        if (null != mCacheEntry && null != mCacheEntry.cacheFile) {
            mCacheEntry.cacheFile.delete();
            mCacheEntry.cacheFile = null;
            mCacheEntry = null;
        }
    }
}
