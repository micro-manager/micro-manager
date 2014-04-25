
AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS) $(BOOST_CPPFLAGS)
deviceadapter_LTLIBRARIES = libmmgr_dal_SerialManager.la
libmmgr_dal_SerialManager_la_SOURCES = SerialManager.cpp SerialManager.h \
         AsioClient.h
libmmgr_dal_SerialManager_la_LIBADD = $(MMDEVAPI_LIBADD) $(BOOST_ASIO_LIB) $(BOOST_THREAD_LIB) $(BOOST_SYSTEM_LIB)
libmmgr_dal_SerialManager_la_LDFLAGS = $(MMDEVAPI_LDFLAGS) $(SERIALFRAMEWORKS) $(BOOST_LDFLAGS)

EXTRA_DIST = license.txt
