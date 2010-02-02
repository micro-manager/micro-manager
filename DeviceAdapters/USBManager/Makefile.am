AM_CXXFLAGS = -fpermissive
lib_LTLIBRARIES = libmmgr_dal_USBManager.la
libmmgr_dal_USBManager_la_SOURCES = USBManager.cpp USBManager.h 
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
libmmgr_dal_USBManager_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a  $(LIBUSB)
libmmgr_dal_USBManager_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_USBManager.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_USBManager$(MMSUFFIX)

EXTRA_DIST = license.txt
