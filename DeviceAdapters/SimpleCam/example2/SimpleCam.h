///////////////////////////////////////////////////////////////////////////////
// FILE:          SimpleCam.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Example of a SimpleCam Camera driver. 
//                
// AUTHOR:        Koen De Vleeschauwer, www.kdvelectronics.eu, 2011
//
// COPYRIGHT:     (c) 2011, Koen De Vleeschauwer, www.kdvelectronics.eu
//
// LICENSE:       This file is distributed under the LGPL license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
//

//
// Description:
// This is an example of a SimpleCam driver, where the captureImage() method returns a bitmap.
//

#ifndef _SIMPLECAM_H_
#define _SIMPLECAM_H_

#include <string>
#include <vector>
#include <sstream>
#include <FreeImagePlus.h>

// Device name and description

#define SIMPLECAM_DEVICENAME  "GPhoto"
#define SIMPLECAM_DESCRIPTION "Generic Camera driver"

class CSimpleCam
{
public:
   CSimpleCam();
   ~CSimpleCam();
   bool listCameras(std::vector<std::string>& detected);             /* returns list of detected cameras */
   bool connectCamera(std::string cameraModelStr);                   /* attempt to connect to the camera. cameraModelStr is one of the cameras detected by listCameras */
   bool disconnectCamera();                                          /* disconnect from camera */
   bool isConnected();                                               /* true if camera is connected and ready */
   bool listShutterSpeeds(std::vector<std::string>& shutterSpeeds);  /* if connected to a camera, returns list of available shutter speeds */
   bool getShutterSpeed(std::string& currentShutterSpeed);           /* if connected to a camera, returns current shutter speed */
   bool setShutterSpeed(std::string newShutterSpeed);                /* if connected to a camera, sets new shutter speed. newShutterSpeed is one of the shutter speeds returned by listShutterSpeeds */
   fipImage captureImage();                                          /* if connected to a camera, takes a picture. return value a FreeImagePlus bitmap */
   fipImage capturePreview();                                        /* if connected to a camera, returns a viewfinder preview. return value is a FreeImagePlus bitmap */
};

#endif //_SIMPLECAM_H_
// not truncated
