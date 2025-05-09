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

/**
 * {@hide}
 */
public interface MediaScannerClient
{
    //[RTK Begin]
    // The values should be synced with frameworkds/native/include/utils/Errors.h
    public static final int OK = 0;
    public static final int OK_AVCHD_FOLDER = 200;
    public static final int OK_BDROM_FOLDER = 201;
    public static final int OK_DVD_FOLDER = 202;

    // change its return from void to int
    // We use the return to know whether it is a bd/avchd/dvd folder.
    public int scanFile(String path, long lastModified, long fileSize,
            boolean isDirectory, boolean noMedia);
    //[RTK End]
    /**
     * Called by native code to return metadata extracted from media files.
     */
    public void handleStringTag(String name, String value);

    /**
     * Called by native code to return mime type extracted from DRM content.
     */
    public void setMimeType(String mimeType);
}
