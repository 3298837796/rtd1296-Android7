LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES += autopair_stack.c

LOCAL_C_INCLUDES := $(rtk_local_C_INCLUDES)
LOCAL_CFLAGS += $(rtk_local_CFLAGS) -Wno-error=unused-parameter

LOCAL_MODULE_RELATIVE_PATH := rtkbt
LOCAL_MODULE_TAGS := optional
LOCAL_SHARED_LIBRARIES := libcutils libc
LOCAL_MODULE := autopair_stack
LOCAL_MULTILIB := 32

include $(BUILD_SHARED_LIBRARY)
