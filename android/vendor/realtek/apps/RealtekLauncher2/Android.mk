#
# Copyright (C) 2008 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
$(info $(shell ($(LOCAL_PATH)/multilan.sh $(LOCAL_PATH) )))

LOCAL_MODULE_TAGS := optional

LOCAL_STATIC_JAVA_LIBRARIES := android-common android-support-v13

LOCAL_SRC_FILES := $(call all-java-files-under, src) $(call all-renderscript-files-under, src)
#LOCAL_SDK_VERSION := current

LOCAL_PACKAGE_NAME := RtkLauncher2
LOCAL_CERTIFICATE := platform

LOCAL_OVERRIDES_PACKAGES := Home Launcher2

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

#LOCAL_JAVA_LIBRARIES := realtek

include $(BUILD_PACKAGE)

include $(call all-makefiles-under,$(LOCAL_PATH))
