
AUTOMAKE_OPTIONS = foreign subdir-objects

noinst_PROGRAMS = mm_devicetest 
mm_devicetest_SOURCES = DeviceTest.cpp ../../MMCore/PluginManager.cpp
mm_devicetest_LDFLAGS = $(SERIALFRAMEWORKS) -pthread
mm_devicetest_LDADD = ../../MMCore/libMMCore.la ../../MMDevice/libMMDevice.la
