AM_CXXFLAGS = -fpermissive $(BOOST_CPPFLAGS)
lib_LTLIBRARIES = libmmgr_dal_FreeSerialPort.la
libmmgr_dal_FreeSerialPort_la_SOURCES = FreeSerialPort.cpp FreeSerialPort.h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
libmmgr_dal_FreeSerialPort_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_FreeSerialPort_la_LDFLAGS = -module

install:
	cp .libs/libmmgr_dal_FreeSerialPort.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_FreeSerialPort$(MMSUFFIX)

EXTRA_DIST = license.txt
