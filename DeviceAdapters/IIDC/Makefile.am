# To allow building with outdated versions of Boost (present by default on many
# Linux distributions), use the older Boost.Thread interface, even though it
# differs more from C++11. Another way around this might be to use G++'s C++11
# libraries where possible.
AM_CPPFLAGS = $(BOOST_CPPFLAGS) -DBOOST_THREAD_VERSION=2

AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS)
deviceadapter_LTLIBRARIES = libmmgr_dal_IIDC.la
libmmgr_dal_IIDC_la_SOURCES = \
	IIDCCamera.cpp \
	IIDCCamera.h \
	IIDCCapture.cpp \
	IIDCCapture.h \
	IIDCConvert.h \
	IIDCError.h \
	IIDCFeature.cpp \
	IIDCFeature.h \
	IIDCInterface.cpp \
	IIDCInterface.h \
	IIDCUtils.cpp \
	IIDCUtils.h \
	IIDCVideoMode.cpp \
	IIDCVideoMode.h \
	MMIIDCCamera.cpp \
	MMIIDCCamera.h

libmmgr_dal_IIDC_la_CPPFLAGS = $(AM_CPPFLAGS) $(LIBDC1394_CPPFLAGS)
libmmgr_dal_IIDC_la_CXXFLAGS = $(AM_CXXFLAGS) $(LIBDC1394_CFLAGS)
libmmgr_dal_IIDC_la_LIBADD = $(MMDEVAPI_LIBADD) $(BOOST_THREAD_LIB) $(BOOST_SYSTEM_LIB) $(BOOST_DATE_TIME_LIB) $(BOOST_CHRONO_LIB) $(LIBDC1394_LIBS)
libmmgr_dal_IIDC_la_LDFLAGS = $(MMDEVAPI_LDFLAGS) $(BOOST_LDFLAGS) $(LIBDC1394_LDFLAGS)
