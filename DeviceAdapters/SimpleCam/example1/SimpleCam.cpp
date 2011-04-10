///////////////////////////////////////////////////////////////////////////////
// FILE:          SimpleCam.cpp
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
// This is an example of a SimpleCam driver, where the captureImage() method returns a filename.
//

#include "SimpleCam.h"
#include <fcntl.h>
#include <iostream>

#include <cstdlib>
#include <complex>

using namespace std;

CSimpleCam::CSimpleCam()
{
}

CSimpleCam::~CSimpleCam()
{
}

/* returns list of detected cameras */
bool CSimpleCam::listCameras(vector<string>& detected)
{
   detected.clear();
   detected.push_back("Generic camera");
   return true;
}

/* attempt to connect to the camera. cameraModelStr is one of the cameras detected by listCameras */
bool CSimpleCam::connectCamera(string cameraName)
{
   return true;
}

/* disconnect from camera */
bool CSimpleCam::disconnectCamera()
{
   return true;
}

/* true if camera is connected and ready */
bool CSimpleCam::isConnected()
{
   return true;
}

/* if connected to a camera, returns list of available shutter speeds */
bool CSimpleCam::listShutterSpeeds(std::vector<std::string>& shutterSpeeds)
{
   shutterSpeeds.clear();
   shutterSpeeds.push_back("auto");
   return true;
}

/* if connected to a camera, returns current shutter speed */
bool CSimpleCam::getShutterSpeed(std::string& currentShutterSpeed)
{
   currentShutterSpeed = "auto";
   return true;
}

/* if connected to a camera, sets new shutter speed. 
   newShutterSpeed is one of the shutter speeds returned by listShutterSpeeds */
bool CSimpleCam::setShutterSpeed(std::string newShutterSpeed)
{
   return true;
}

/* if connected to a camera, takes a picture, and saves it to disk. return value is a filename */
string CSimpleCam::captureImage()
{
   /* create a FreeImagePlus bitmap */
   const int width = 1024;
   const int height = 768;
   fipImage image(FIT_RGB16, width, height, 16);
   
   /* generate a random image */
   double w = image.getWidth();
   double h = image.getHeight();
   double pi = 3.1415926535;
   double period = w / 2;
   double amp = 32768;

   for (int y = 0; y < h; y++)
   {
      FIRGB16 *imgBits = (FIRGB16 *)image.getScanLine(y);
      double phase = y * 2 * pi / 4 / h;
   
      for (int x = 0; x < w; x++)
      {
         double k = w*y+x;
         imgBits[x].red =   min(65535.0, amp * (sin(phase+2*pi*k/period)+1));
         imgBits[x].green = min(65535.0, amp * (sin(phase*2+2*pi*k/period)+1));
         imgBits[x].blue =  min(65535.0, amp * (sin(phase*4+2*pi*k/period)+1));
      }
   }

   /* save bitmap to file */
#ifdef _WIN32
   string fname="C:\\TEST.TIF";
#else
   string fname="/tmp/test.tiff";
#endif
   image.save(fname.c_str());

   /* return value is the filename of a bitmap file */
   return fname;
}

/* if connected to a camera, returns a viewfinder preview. return value is a FreeImagePlus bitmap; typically 320x240 pixels */
fipImage CSimpleCam::capturePreview()
{
   fipImage previewImage;
   return previewImage;
}

// not truncated
