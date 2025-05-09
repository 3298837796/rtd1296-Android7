# Copyright (C) 2015 The Android Open Source Project
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

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, ../../common/host-side/tradefed/src)

LOCAL_JAVA_RESOURCE_DIRS := res
LOCAL_JAVA_RESOURCE_DIRS += ../../common/host-side/tradefed/res

LOCAL_SUITE_BUILD_NUMBER := $(BUILD_NUMBER_FROM_FILE)
LOCAL_SUITE_TARGET_ARCH := $(TARGET_ARCH)
LOCAL_SUITE_NAME := CTS
LOCAL_SUITE_FULLNAME := "Compatibility Test Suite"
LOCAL_SUITE_VERSION := 7.1_r6

LOCAL_MODULE := cts-tradefed

include $(BUILD_COMPATIBILITY_SUITE)

# Build all sub-directories
include $(call all-makefiles-under,$(LOCAL_PATH))
