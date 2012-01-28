pkglibdir = $(DEVICEADAPTERPATH)
AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS) -fpermissive
pkglib_LTLIBRARIES = libmmgr_dal_NIDAQ.la
libmmgr_dal_NIDAQ_la_SOURCES = NIDAQ.cpp NIDAQ.h \
   ../../MMDevice/MMDevice.h ../../MMDevice/DeviceBase.h
libmmgr_dal_NIDAQ_la_LIBADD = $(MMDEVAPI_LIBADD)
libmmgr_dal_NIDAQ_la_LDFLAGS = $(MMDEVAPI_LDFLAGS) -framework nidaqmxbase -framework nidaqmxbaselv
