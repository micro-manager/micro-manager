AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS) $(BOOST_CPPFLAGS)
deviceadapter_LTLIBRARIES = libmmgr_dal_UserDefinedSerial.la
libmmgr_dal_UserDefinedSerial_la_SOURCES = \
					   ResponseDetector.cpp \
					   ResponseDetector.h \
					   StringEscapes.cpp \
					   StringEscapes.h \
					   UserDefinedSerial.cpp \
					   UserDefinedSerial.h \
					   UserDefinedSerialConstants.h \
					   UserDefinedSerialImpl.h
libmmgr_dal_UserDefinedSerial_la_LIBADD = $(MMDEVAPI_LIBADD)
libmmgr_dal_UserDefinedSerial_la_LDFLAGS = $(MMDEVAPI_LDFLAGS)

if BUILD_CPP_TESTS
UNITTESTS = unittest
endif

SUBDIRS = . $(UNITTESTS)

EXTRA_DIST = license.txt
