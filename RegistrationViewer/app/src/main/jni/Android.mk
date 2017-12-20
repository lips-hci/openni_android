# RegistrationViewer.jni Android makefile.
#
LOCAL_PATH := $(call my-dir)
LIB_DIR := $(LOCAL_PATH)/Libs/armeabi-v7a

#
# libOpenNI
#
include $(CLEAR_VARS)
LOCAL_MODULE := libOpenNI
LOCAL_SRC_FILES := $(LIB_DIR)/libOpenNI.so
include $(PREBUILT_SHARED_LIBRARY)
#
# libOpenNI.jni
#
include $(CLEAR_VARS)
LOCAL_MODULE := libOpenNI.jni
LOCAL_SRC_FILES := $(LIB_DIR)/libOpenNI.jni.so
include $(PREBUILT_SHARED_LIBRARY)
#
# libusb
#
include $(CLEAR_VARS)
LOCAL_MODULE := libusb
LOCAL_SRC_FILES := $(LIB_DIR)/libusb.so
include $(PREBUILT_SHARED_LIBRARY)

#
# Build RegistrationViewer.jni.so
#
include $(CLEAR_VARS)

# set path to source
MY_PREFIX := $(LOCAL_PATH)/
OPENNI_DIR := $(LOCAL_PATH)/Include

# list all source files
MY_SRC_FILES := \
	$(MY_PREFIX)*.cpp

# expand the wildcards
MY_SRC_FILE_EXPANDED := $(wildcard $(MY_SRC_FILES))

# make those paths relative to here
LOCAL_SRC_FILES := $(MY_SRC_FILE_EXPANDED:$(LOCAL_PATH)/%=%)

LOCAL_C_INCLUDES := \
	$(MY_PREFIX) \
	$(OPENNI_DIR)/

LOCAL_CFLAGS:= -fvisibility=hidden -DXN_EXPORTS

LOCAL_LDFLAGS += -Wl,--export-dynamic

LOCAL_LDLIBS := -llog

LOCAL_SHARED_LIBRARIES := libOpenNI libOpenNI.jni libusb

LOCAL_PREBUILT_LIBS := libc

LOCAL_MODULE:= RegistrationViewer.jni

include $(BUILD_SHARED_LIBRARY)
