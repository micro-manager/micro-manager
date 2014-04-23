
AUTOMAKE_OPTIONS = foreign subdir-objects

noinst_PROGRAMS = mm_librarytest
mm_librarytest_SOURCES = LibraryTest.cpp ../../MMCore/PluginManager.cpp
mm_librarytest_CPPFLAGS = $(ZLIB_CPPFLAGS)
mm_librarytest_CXXFLAGS = $(ZLIB_CFLAGS)
mm_librarytest_LDFLAGS = $(SERIALFRAMEWORKS) $(ZLIB_LDFLAGS)
mm_librarytest_LDADD = ../../MMCore/libMMCore.la ../../MMDevice/libMMDevice.la $(ZLIB_LIBS)
