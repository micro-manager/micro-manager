AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = NIDAQ.la
NIDAQ_la_SOURCES = NIDAQ.cpp NIDAQ.h \
   ../../MMDevice/MMDevice.h ../../MMDevice/DeviceBase.h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
NIDAQ_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a
NIDAQ_la_LDFLAGS = -module -framework nidaqmxbase -framework nidaqmxbaselv

install:
	cp .libs/NIDAQ.so $(DEVICEADAPTERPATH)/$(MMPREFIX)NIDAQ$(MMSUFFIX)
