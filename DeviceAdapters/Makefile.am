AUTOMAKE_OPTIONS = foreign

if BUILD_HAMAMATSU
   HAMAMATSU = Hamamatsu
endif
if BUILD_PVCAM
   PVCAM = PVCAM
endif
##if BUILD_TE2000
##	TE2000 = NikonTE2000
##endif
if BUILD_DC1394
   DC1394 = dc1394
endif

SUBDIRS =  AOTF Andor ASIFW1000 DTOpenLayer DemoCamera SerialManagerUNIX Ludl Prior ParallelPort Sensicam SerialManager ZeissCAN Vincent Conix $(HAMAMATSU) $(PVCAM) $(TE2000) Nikon StanfordPhotonics SutterLambda $(DC1394) Yokogawa

dist-hook:
	rm  $(distdir)/NikonTE2000/NikonTE2000.cpp
	rm  $(distdir)/NikonTE2000/NikonTE2000.h
	rm  $(distdir)/NikonTE2000/TEHub.h
	rm  $(distdir)/NikonTE2000/TEHub.cpp
	rm  $(distdir)/NikonTE2000/*.vcproj
	rm  $(distdir)/NikonTE2000/NikonTE2000_vc8.sln
