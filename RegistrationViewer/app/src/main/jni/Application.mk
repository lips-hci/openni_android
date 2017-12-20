###################
#      NOTE       #
###################

APP_STL := gnustl_static

# Android >= v4.1 JB
APP_PLATFORM := android-16

# Build ARMv7-A machine code.
APP_ABI := armeabi-v7a
APP_CFLAGS := -O3 -ftree-vectorize -ffast-math -funroll-loops
APP_CFLAGS += -fPIC -march=armv7-a -mfloat-abi=softfp -mtune=cortex-a9 -mfpu=vfpv3-d16 -mfpu=vfp

APP_CPPFLAGS += -frtti -std=c++11
