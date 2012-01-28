pkglibdir = $(DEVICEADAPTERPATH)
AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS) -fpermissive 
pkglib_LTLIBRARIES = libmmgr_dal_CSU22.la libmmgr_dal_CSUX.la
libmmgr_dal_CSU22_la_SOURCES = CSU22.cpp CSU22.h CSU22Hub.h CSU22Hub.cpp ../../MMDevice/MMDevice.h ../../MMDevice/DeviceBase.h ../../MMDevice/ModuleInterface.h
libmmgr_dal_CSU22_la_LIBADD = $(MMDEVAPI_LIBADD)
libmmgr_dal_CSU22_la_LDFLAGS = $(MMDEVAPI_LDFLAGS)

libmmgr_dal_CSUX_la_SOURCES = CSUX.cpp CSUX.h CSUXHub.h CSUXHub.cpp ../../MMDevice/MMdevice.h ../../MMDevice/DeviceBase.h ../../MMDevice/ModuleInterface.h
libmmgr_dal_CSUX_la_LIBADD = $(MMDEVAPI_LIBADD)
libmmgr_dal_CSUX_la_LDFLAGS = $(MMDEVAPI_LDFLAGS)
