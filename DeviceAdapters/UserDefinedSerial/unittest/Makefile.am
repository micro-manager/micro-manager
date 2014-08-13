check_PROGRAMS = \
	EscapeTests
AM_DEFAULT_SOURCE_EXT = .cpp
AM_CPPFLAGS = -isystem ../../../testing/gtest/include -I.. \
	      $(MMDEVAPI_CXXFLAGS) $(BOOST_CPPFLAGS)
AM_LDFLAGS = $(MMDEVAPI_LDFLAGS)
LDADD = ../../../testing/libgtest.la $(MMDEVAPI_LIBADD) \
	../UserDefinedSerial.lo
TESTS = $(check_PROGRAMS)
