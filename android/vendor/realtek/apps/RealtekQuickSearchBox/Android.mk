#
# Copyright (C) 2009 The Android Open Source Project
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

LOCAL_SDK_VERSION := current

LOCAL_STATIC_JAVA_LIBRARIES := \
    guava \
    android-common

LOCAL_SRC_FILES := \
    $(call all-java-files-under, src) \
    $(call all-logtags-files-under, src)

LOCAL_PACKAGE_NAME := RealtekQuickSearchBox
LOCAL_CERTIFICATE := shared

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

LOCAL_OVERRIDES_PACKAGES := QuickSearchBox

include $(BUILD_PACKAGE)

# Also build our test apk
include $(call all-makefiles-under,$(LOCAL_PATH))
