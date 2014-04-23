
AUTOMAKE_OPTIONS = foreign subdir-objects

noinst_PROGRAMS = mm_devicetest 
mm_devicetest_SOURCES = DeviceTest.cpp ../../MMCore/PluginManager.cpp
mm_devicetest_CPPFLAGS = $(ZLIB_CPPFLAGS)
mm_devicetest_CXXFLAGS = $(ZLIB_CFLAGS)
mm_devicetest_LDFLAGS = $(SERIALFRAMEWORKS) $(ZLIB_LDFLAGS)
mm_devicetest_LDADD = ../../MMCore/libMMCore.la ../../MMDevice/libMMDevice.la $(ZLIB_LIBS)
