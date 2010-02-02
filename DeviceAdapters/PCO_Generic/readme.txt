-------------------------------------------------------------------
 P C O   AG   -     TECHNICAL  INFORMATION  DOCUMENT
-------------------------------------------------------------------

README FOR SOFTWARE/VERSION:  
pco_generic device adapter for Micro Manager 1.00

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
  pcocnv.dll

In case you've got a pco.camera series you'll have to copy the interface
files to the MicroManager folder.
These files are needed for all interfaces:
  sc2_cam.dll

Additionally for the Matrox Cameralink interface:
  sc2_cl_mtx.dll
  clsermtx.dll
  mtxclsermil.dll

Additionally for the National Cameralink interface:
  sc2_cl_nat.dll

All files mentioned above can be found either in the CamWare folder, or the sdk.

VERSION HISTORY:
Version 1.00
- Initial version

KNOWN BUGS:
- none; (Who can say...?)


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
