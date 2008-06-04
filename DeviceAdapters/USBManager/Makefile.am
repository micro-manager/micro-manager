AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = USBManager.la
USBManager_la_SOURCES = USBManager.cpp USBManager.h 
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
USBManager_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a  $(LIBUSB)
USBManager_la_LDFLAGS = -module

install:
	cp .libs/USBManager.so $(IJPATH)/$(MMPREFIX)USBManager

EXTRA_DIST = license.txt
