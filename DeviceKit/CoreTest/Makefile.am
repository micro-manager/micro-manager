
AUTOMAKE_OPTIONS = foreign subdir-objects

bin_PROGRAMS = mm_coretest
mm_coretest_SOURCES = MMCoreTest.cpp ../../MMCore/PluginManager.cpp
mm_coretest_CPPFLAGS = $(ZLIB_CPPFLAGS)
mm_coretest_CXXFLAGS = $(ZLIB_CFLAGS)
mm_coretest_LDFLAGS = $(SERIALFRAMEWORKS) $(ZLIB_LDFLAGS)
mm_coretest_LDADD = ../../MMCore/libMMCore.la ../../MMDevice/libMMDevice.la $(ZLIB_LIBS)

install:
	echo "Nothing to do"

