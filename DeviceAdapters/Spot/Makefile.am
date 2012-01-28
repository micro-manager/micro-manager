pkglibdir = $(DEVICEADAPTERPATH)
AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS) -fpermissive
pkglib_LTLIBRARIES = libmmgr_dal_Spot.la
libmmgr_dal_Spot_la_SOURCES = SpotCamera.cpp SpotDevice.cpp CodeUtility.h CodeUtility.cpp SpotCamera.h SpotDevice.h ../../MMDevice/DeviceBase.h ../../MMDevice/ImgBuffer.h ../../MMDevice/DeviceUtils.h ../../MMDevice/DeviceThreads.h
libmmgr_dal_Spot_la_LIBADD = $(MMDEVAPI_LIBADD)
libmmgr_dal_Spot_la_LDFLAGS = $(MMDEVAPI_LDFLAGS) $(SPOTFRAMEWORKS)
