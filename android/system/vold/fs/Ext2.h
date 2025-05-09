/*
 * Copyright (C) 2012 The Android Open Source Project
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

#ifndef ANDROID_VOLD_EXT2_H
#define ANDROID_VOLD_EXT2_H

#include <utils/Errors.h>

#include <string>

namespace android {
namespace vold {
namespace ext2 {

bool IsSupported();

status_t Check(const std::string& source, const std::string& target);
status_t Mount(const std::string& source, const std::string& target, bool ro,
        bool remount, bool executable);
status_t Format(const std::string& source);
status_t Resize(const std::string& source, unsigned int numSectors);
status_t RepairMount(const std::string& source, const std::string& target,
        bool ro, bool remount, bool executable,
        bool bCheckVolume, bool bFormatWhenError, bool bTryMount);
}  // namespace ext2
}  // namespace vold
}  // namespace android

#endif
