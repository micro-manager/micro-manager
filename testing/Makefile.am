AUTOMAKE_OPTIONS = subdir-objects

check_LTLIBRARIES = libgmock.la
libgmock_la_CPPFLAGS = $(GMOCK_CPPFLAGS) -Igmock -Igmock/gtest
libgmock_la_SOURCES = gmock/gtest/src/gtest-all.cc \
		      gmock/src/gmock-all.cc
