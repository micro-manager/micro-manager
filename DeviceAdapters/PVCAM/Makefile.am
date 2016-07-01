
AM_CPPFLAGS = -F/Library/Frameworks
AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS)
deviceadapter_LTLIBRARIES = libmmgr_dal_PVCAM.la
libmmgr_dal_PVCAM_la_SOURCES = PVCAMAdapter.cpp PVCAMAdapter.h PVCAMUniversal.cpp PVCAMParam.cpp PvDebayer.cpp PvFrameInfo.cpp PpParam.cpp PollingThread.cpp NotificationThread.cpp NotificationEntry.cpp PvCircularBuffer.cpp AcqConfig.cpp PvRoiCollection.cpp
libmmgr_dal_PVCAM_la_LIBADD = $(MMDEVAPI_LIBADD)
libmmgr_dal_PVCAM_la_LDFLAGS = $(MMDEVAPI_LDFLAGS) $(PVCAMFRAMEWORKS)

EXTRA_DIST = PVCAM.vcproj license.txt
