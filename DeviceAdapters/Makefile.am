AUTOMAKE_OPTIONS = foreign

if BUILD_HAMAMATSU
   HAMAMATSU = Hamamatsu
endif
if BUILD_PVCAM
   PVCAM = PVCAM
endif
if BUILD_QCAM
   QCAM = QCam
endif
if BUILD_TE2000
   TE2000 = NikonTE2000
endif
##if BUILD_AZ100
##   AZ100 = NikonAZ100
##endif
if BUILD_DC1394
   DC1394 = dc1394
endif
if BUILD_K8055
   K8055 = K8055
endif
if BUILD_K8061
   K8061 = K8061
endif
if BUILD_USBMANAGER
   USBMANAGER = USBManager 
endif

SUBDIRS =  AOTF Andor ASIFW1000 ASIStage DAZStage DAShutter DTOpenLayer DemoCamera DemoStreamingCamera SerialManager SerialManagerUNIX Ludl LudlLow PI PI_GCS Prior ParallelPort Pecon Sensicam ZeissCAN ZeissCAN29 Vincent Conix $(HAMAMATSU) $(PVCAM) $(QCAM) MultiShutter NikonTE2000 $(TE2000) $(AZ100) $(K8055) $(K8061) $(USBMANAGER) Nikon StanfordPhotonics SpectralLMM5 SutterLambda $(DC1394) Yokogawa

