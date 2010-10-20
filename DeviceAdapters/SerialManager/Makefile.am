AM_CXXFLAGS = -fpermissive $(BOOST_CPPFLAGS)
lib_LTLIBRARIES = libmmgr_dal_SerialManager.la
libmmgr_dal_SerialManager_la_SOURCES = SerialManager.cpp SerialManager.h \
         AsioClient.h
## adding the libtool library gives problems on OS X 10.4, adding the static lib works
libmmgr_dal_SerialManager_la_LIBADD = ../../MMDevice/.libs/libMMDevice.a 
libmmgr_dal_SerialManager_la_LDFLAGS = -module $(SERIALFRAMEWORKS) static-libs $(BOOST_LDFLAGS) $(BOOST_ASIO_LIB) $(BOOST_THREAD_LIB) $(BOOST_SYSTEM_LIB)

install:
	cp .libs/libmmgr_dal_SerialManager.so $(DESTDIR)$(DEVICEADAPTERPATH)/libmmgr_dal_SerialManager$(MMSUFFIX)


EXTRA_DIST = license.txt
