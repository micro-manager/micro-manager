## Process this file with automake to produce Makefile.in
bin_PROGRAMS = testserial
testserial_SOURCES = Test_Serial.cpp \
   $(top_srcdir)/MMDevice/DeviceUtils.cpp \
   $(top_srcdir)/MMDevice/ImgBuffer.cpp \
   $(top_srcdir)/MMDevice/ModuleInterface.cpp \
   $(top_srcdir)/MMDevice/Property.cpp \
   $(top_srcdir)/MMCore/Configuration.cpp \
   $(top_srcdir)/MMCore/CoreProperty.cpp \
   $(top_srcdir)/MMCore/MMCore.cpp \
   $(top_srcdir)/MMCore/PluginManager.cpp
testserial_LDADD = $(LIBACE)

EXTRA_DIST = Test_Serial.vcproj Test_Serial.sln stdafx.cpp stdafx.h
