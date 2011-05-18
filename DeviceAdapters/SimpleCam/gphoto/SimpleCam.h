///////////////////////////////////////////////////////////////////////////////
// FILE:          SimpleCam.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Camera driver for gphoto2 cameras.
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

#ifndef _SIMPLECAM_H_
#define _SIMPLECAM_H_

#include <string>
#include <vector>
#include <sstream>
#define _SIMPLECAM_GPHOTO_
#include <gphoto2/gphoto2.h>
#include <gphoto2/gphoto2-version.h>
#include <gphoto2/gphoto2-port-version.h>
#include <FreeImagePlus.h>

// Device name and description

#define SIMPLECAM_DEVICENAME  "GPhoto"
#define SIMPLECAM_DESCRIPTION "GPhoto2 Generic Camera driver"

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
   std::string captureImage();                                       /* if connected to a camera, takes a picture, and saves it to disk. return value is the filename of the picture */
   fipImage capturePreview();                                        /* if connected to a camera, returns a viewfinder preview. return value is a FreeImagePlus bitmap */

private:
   GPContext *context_;
   Camera   *camera_;
   int getShutterSpeedWidget(CameraWidget* &rootConfig, CameraWidget* &shutterSpeedWidget);
   int getWidget(CameraWidget* &rootConfig, CameraWidget* &configWidget, const char* configName);
   int setLibPaths();   /* Mac OS X: load gphoto2 camera drivers from libgphoto2/libgphoto2, and i/o drivers from libgphoto2/libgphoto2_port, if these directories exist. */
};

#endif //_SIMPLECAM_H_
// not truncated
