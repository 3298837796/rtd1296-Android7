# Copyright 2009 The Android Open Source Project
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

updater_src_files := \
	install.cpp \
    blockimg.cpp \
	updater.cpp \
	install_rtk.cpp \
	updater_rtk_common.cpp \
	updater_rtk_mmcutils.cpp

#
# Build a statically-linked binary to include in OTA packages
#
include $(CLEAR_VARS)

# Build only in eng, so we don't end up with a copy of this in /system
# on user builds.  (TODO: find a better way to build device binaries
# needed only for OTA packages.)
LOCAL_MODULE_TAGS := eng

LOCAL_CLANG := true

LOCAL_SRC_FILES := $(updater_src_files)

LOCAL_STATIC_LIBRARIES += libfec libfec_rs libext4_utils_static libsquashfs_utils libcrypto_static

ifeq ($(TARGET_USERIMAGES_USE_EXT4), true)
LOCAL_CFLAGS += -DUSE_EXT4
LOCAL_CFLAGS += -DNFLASH_LAOUT
LOCAL_CFLAGS += -Wno-unused-parameter
LOCAL_C_INCLUDES += system/extras/ext4_utils
LOCAL_STATIC_LIBRARIES += \
    libsparse_static \
    libz
endif

ifeq ($(TARGET_BOARD_PLATFORM), rtd298x)
LOCAL_CFLAGS += -DRTD_298x
else
LOCAL_CFLAGS += -DRTD_299x
endif

LOCAL_STATIC_LIBRARIES += $(TARGET_RECOVERY_UPDATER_LIBS) $(TARGET_RECOVERY_UPDATER_EXTRA_LIBS)
LOCAL_STATIC_LIBRARIES += libapplypatch libbase libotafault libedify libmtdutils libminzip libz
LOCAL_STATIC_LIBRARIES += libbz
LOCAL_STATIC_LIBRARIES += libcutils liblog libc
LOCAL_STATIC_LIBRARIES += libselinux
tune2fs_static_libraries := \
 libext2_com_err \
 libext2_blkid \
 libext2_quota \
 libext2_uuid_static \
 libext2_e2p \
 libext2fs
LOCAL_STATIC_LIBRARIES += libtune2fs $(tune2fs_static_libraries)
LOCAL_STATIC_LIBRARIES += libMCP libion

LOCAL_C_INCLUDES += external/e2fsprogs/misc
LOCAL_C_INCLUDES += $(LOCAL_PATH)/..
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../lib_inc

# Each library in TARGET_RECOVERY_UPDATER_LIBS should have a function
# named "Register_<libname>()".  Here we emit a little C function that
# gets #included by updater.c.  It calls all those registration
# functions.

# Devices can also add libraries to TARGET_RECOVERY_UPDATER_EXTRA_LIBS.
# These libs are also linked in with updater, but we don't try to call
# any sort of registration function for these.  Use this variable for
# any subsidiary static libraries required for your registered
# extension libs.

inc := $(call intermediates-dir-for,PACKAGING,updater_extensions)/register.inc

# Encode the value of TARGET_RECOVERY_UPDATER_LIBS into the filename of the dependency.
# So if TARGET_RECOVERY_UPDATER_LIBS is changed, a new dependency file will be generated.
# Note that we have to remove any existing depency files before creating new one,
# so no obsolete dependecy file gets used if you switch back to an old value.
inc_dep_file := $(inc).dep.$(subst $(space),-,$(sort $(TARGET_RECOVERY_UPDATER_LIBS)))
$(inc_dep_file): stem := $(inc).dep
$(inc_dep_file) :
	$(hide) mkdir -p $(dir $@)
	$(hide) rm -f $(stem).*
	$(hide) touch $@

$(inc) : libs := $(TARGET_RECOVERY_UPDATER_LIBS)
$(inc) : $(inc_dep_file)
	$(hide) mkdir -p $(dir $@)
	$(hide) echo "" > $@
	$(hide) $(foreach lib,$(libs),echo "extern void Register_$(lib)(void);" >> $@;)
	$(hide) echo "void RegisterDeviceExtensions() {" >> $@
	$(hide) $(foreach lib,$(libs),echo "  Register_$(lib)();" >> $@;)
	$(hide) echo "}" >> $@

$(call intermediates-dir-for,EXECUTABLES,updater,,,$(TARGET_PREFER_32_BIT))/updater.o : $(inc)
LOCAL_C_INCLUDES += $(dir $(inc))

inc :=
inc_dep_file :=

LOCAL_MODULE := updater

LOCAL_FORCE_STATIC_EXECUTABLE := true

include $(BUILD_EXECUTABLE)
