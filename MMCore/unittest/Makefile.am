check_PROGRAMS = \
	LoggerSanityTests \
	CoreSanityTests
AM_DEFAULT_SOURCE_EXT = .cpp
AM_CPPFLAGS = -isystem ../../testing/gtest/include -I..
LDADD = ../../testing/libgtest.la ../libMMCore.la
TESTS = $(check_PROGRAMS)
