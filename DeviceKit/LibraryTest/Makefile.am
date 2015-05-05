
AUTOMAKE_OPTIONS = foreign subdir-objects

noinst_PROGRAMS = mm_librarytest
mm_librarytest_SOURCES = LibraryTest.cpp ../../MMCore/PluginManager.cpp
mm_librarytest_CPPFLAGS = $(BOOST_CPPFLAGS)
mm_librarytest_LDFLAGS = $(SERIALFRAMEWORKS) -pthread
mm_librarytest_LDADD = ../../MMCore/libMMCore.la ../../MMDevice/libMMDevice.la
