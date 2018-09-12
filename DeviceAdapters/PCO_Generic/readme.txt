-------------------------------------------------------------------
 P C O   AG   -     TECHNICAL  INFORMATION  DOCUMENT
-------------------------------------------------------------------

README FOR SOFTWARE/VERSION:  
pco_generic device adapter for Micro Manager

FOR PCO PRODUCT/VERSION:
pco.camera, PixelFly, SensiCam

DESCRIPTION:
This directory contains the source files needed to build the pco_generic device
adapter for MicroManager.

Installation:
To compile and link the project you need to place the
files in pco_generic.zip (library and header files) to the following folder:
<somewhere>..\3rdparty\pco\windows.

Please copy the following files into the MicroManager folder:
  pco_conv.dll, pco_cdlg.dll and pco_cryptdll.dll
  
pco_conv.dll: Image conversion dll
pco_cdlg.dll: Image conversion dialog dll
pco_cryptdll.dll: Software protection dll for conversion

With a PixelFly or SensiCam you're ready to go.

In case you've got a pco.camera (e.g. pco.1200hs, pco.1600...) series you'll
have to copy the interface files to the MicroManager folder.

These files are needed for all interfaces:
  sc2_cam.dll

Additionally for the Matrox Cameralink interface:
  sc2_cl_mtx.dll
  clsermtx.dll
  mtxclsermil.dll

Additionally for the National Cameralink interface:
  sc2_cl_nat.dll

Additionally for the Silicon Software Cameralink interface:
  sc2_cl_me3.dll or
  sc2_cl_me4.dll
  
Additionaly for Cameralink HS interface:
  sc2_clhs.dll  

All files mentioned above can be found either in the CamWare folder, or the sdk.

VERSION HISTORY:
see SVN repository.


 PCO AG
 DONAUPARK 11
 93309 KELHEIM / GERMANY
 PHONE +49 (9441) 20050
 FAX   +49 (9441) 200520
 info@pco.de, support@pco.de
 http://www.pco.de
-------------------------------------------------------------------
 DISCLAIMER
 THE ORIGIN OF THIS INFORMATION MAY BE INTERNAL OR EXTERNAL TO PCO.
 PCO MAKES EVERY EFFORT WITHIN ITS MEANS TO VERIFY THIS INFORMATION.
 HOWEVER, THE INFORMATION PROVIDED IN THIS DOCUMENT IS FOR YOUR
 INFORMATION ONLY. PCO MAKES NO EXPLICIT OR IMPLIED CLAIMS TO THE
 VALIDITY OF THIS INFORMATION.
-------------------------------------------------------------------
 Any trademarks referenced in this document are the property of
 their respective owners.
-------------------------------------------------------------------


for
DEVICE_INTERFACE_VERSION  See mmdevice.h
MODULE_INTERFACE_VERSION  See moduleinterface.h