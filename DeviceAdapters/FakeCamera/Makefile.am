
AM_CPPFLAGS = $(OPENCV_CPPFLAGS)
AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS) $(OPENCV_CFLAGS)
AM_LDFLAGS = $(MMDEVAPI_LDFLAGS) $(OPENCV_LDFLAGS)

deviceadapter_LTLIBRARIES = libmmgr_dal_FakeCamera.la
libmmgr_dal_FakeCamera_la_SOURCES = FakeCamera.cpp \
	FakeCamera.h \
  	error_code.cpp \
  	error_code.h \
	module.cpp \
	../../MMDevice/MMDevice.h
libmmgr_dal_FakeCamera_la_LDFLAGS = $(MMDEVAPI_LDFLAGS)  $(OPENCV_LDFLAGS)
libmmgr_dal_FakeCamera_la_LIBADD = $(MMDEVAPI_LIBADD) $(OPENCV_LIBS)

EXTRA_DIST = FakeCamera.vcproj
