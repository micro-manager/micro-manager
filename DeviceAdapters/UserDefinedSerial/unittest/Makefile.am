check_PROGRAMS = \
	Escape-Tests \
	Unescape-Tests
AM_DEFAULT_SOURCE_EXT = .cpp
AM_CPPFLAGS = $(GMOCK_CPPFLAGS) -I.. $(BOOST_CPPFLAGS)
AM_CXXFLAGS = $(MMDEVAPI_CXXFLAGS)
LDADD = ../../../testing/libgmock.la $(MMDEVAPI_LIBADD) \
	../StringEscapes.lo
TESTS = $(check_PROGRAMS)
