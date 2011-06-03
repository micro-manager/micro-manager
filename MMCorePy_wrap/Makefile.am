## Process this file with automake to produce Makefile.in
SWIGLIBSOURCES = MMCorePy.i \
	../MMCore/CircularBuffer.h  \
	../MMCore/ConfigGroup.h  \
	../MMCore/Configuration.h \
	../MMCore/CoreCallback.h \
	../MMCore/CoreProperty.h \
	../MMCore/CoreUtils.h \
	../MMCore/Error.h \
	../MMCore/ErrorCodes.h \
	../MMCore/Host.h \
	../MMCore/MMCore.h  \
	../MMDevice/MMDeviceConstants.h

MMCorePy_wrap.cxx MMCorePy.py: $(SWIGLIBSOURCES)
	$(SWIG) -c++ -python MMCorePy.i 
	
MMCOREPYSOURCES = $(SWIGLIBSOURCES) \
	$(top_srcdir)/MMDevice/DeviceUtils.cpp \
	$(top_srcdir)/MMDevice/ImgBuffer.cpp \
	$(top_srcdir)/MMDevice/Property.cpp \
	$(top_srcdir)/MMCore/CircularBuffer.cpp \
	$(top_srcdir)/MMCore/Configuration.cpp \
	$(top_srcdir)/MMCore/CoreCallback.cpp \
	$(top_srcdir)/MMCore/CoreProperty.cpp \
	$(top_srcdir)/MMCore/FastLogger.cpp \
	$(top_srcdir)/MMCore/Host.cpp \
	$(top_srcdir)/MMCore/MMCore.cpp \
	$(top_srcdir)/MMCore/PluginManager.cpp

_MMCorePy.so: MMCorePy_wrap.cxx $(MMCOREPYSOURCES)
	python setup.py build_ext --inplace

all:
	make MMCorePy_wrap.cxx
	make MMCorePy.py
	make _MMCorePy.so
	cp MMCorePy.py ../bin
	cp _MMCorePy.so ../bin

install:
	cp MMCorePy.py $(DEVICEADAPTERPATH)
	cp _MMCorePy.so $(DEVICEADAPTERPATH)
	cp MMCoreWrapDemo.py $(DEVICEADAPTERPATH)

clean:
	rm MMCorePy_wrap.cxx || echo "Not found, no problem"
	rm MMCorePy.py || echo "Not found, no problem"
	rm _MMCorePy.so || echo "Not found, no problem"
	rm -rf build || echo "Not found, no problem"
	rm -f *.so || echo "Not found, no problem"

