AUTOMAKE_OPTIONS = subdir-objects

check_LTLIBRARIES = libgmock.la

libgmock_la_CPPFLAGS = $(GMOCK_CPPFLAGS) \
		       -Igoogletest/googletest \
		       -Igoogletest/googlemock

libgmock_la_SOURCES = googletest/googletest/src/gtest-all.cc \
		      googletest/googlemock/src/gmock-all.cc
