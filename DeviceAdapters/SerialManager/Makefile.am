pkglibdir = $(DEVICEADAPTERPATH)
AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS) -fpermissive $(BOOST_CPPFLAGS)
pkglib_LTLIBRARIES = libmmgr_dal_SerialManager.la
libmmgr_dal_SerialManager_la_SOURCES = SerialManager.cpp SerialManager.h \
         AsioClient.h
libmmgr_dal_SerialManager_la_LIBADD = $(MMDEVAPI_LIBADD)
libmmgr_dal_SerialManager_la_LDFLAGS = $(MMDEVAPI_LDFLAGS) $(SERIALFRAMEWORKS) static-libs $(BOOST_LDFLAGS) $(BOOST_ASIO_LIB) $(BOOST_THREAD_LIB) $(BOOST_SYSTEM_LIB)

EXTRA_DIST = license.txt
