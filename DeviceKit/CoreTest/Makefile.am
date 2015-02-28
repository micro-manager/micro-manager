
AUTOMAKE_OPTIONS = foreign subdir-objects

noinst_PROGRAMS = mm_coretest
mm_coretest_SOURCES = MMCoreTest.cpp ../../MMCore/PluginManager.cpp
mm_coretest_CPPFLAGS = $(BOOST_CPPFLAGS)
mm_coretest_LDFLAGS = $(SERIALFRAMEWORKS) -pthread
mm_coretest_LDADD = ../../MMCore/libMMCore.la ../../MMDevice/libMMDevice.la
