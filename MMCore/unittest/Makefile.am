check_PROGRAMS = \
	CoreSanityTests \
	LoggingSplitEntryIntoLinesTests \
	LoggerTests
AM_DEFAULT_SOURCE_EXT = .cpp
AM_CPPFLAGS = -isystem ../../testing/gtest/include -I.. $(BOOST_CPPFLAGS)
LDADD = ../../testing/libgtest.la ../libMMCore.la
TESTS = $(check_PROGRAMS)
