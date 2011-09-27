///////////////////////////////////////////////////////////////////////////////
// FILE:          CameraFrontend.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Simple Camera driver
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

#ifndef _CAMERAFRONTEND_H_
#define _CAMERAFRONTEND_H_

#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include "../../MMDevice/DeviceThreads.h"
#include "SimpleCam.h"
#include <string>
#include <map>
#include <FreeImagePlus.h>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_CAM_BAD_PARAM         101
#define ERR_CAM_NOT_CONNECTED     102
#define ERR_CAM_CONNECT_FAIL      103
#define ERR_CAM_SHUTTERSPEED_FAIL 104
#define ERR_CAM_SHUTTER           105
#define ERR_CAM_NO_IMAGE          106
#define ERR_CAM_LOAD              107
#define ERR_CAM_CONVERSION        108

#define ERR_CAM_UNKNOWN           199

//////////////////////////////////////////////////////////////////////////////
// Properties
//
#define g_Keyword_ShutterSpeed   "ShutterSpeed"
#define g_Keyword_TrackExposure  "ShutterSpeedTracksExposure"
#define g_Keyword_KeepOriginals  "KeepOriginals"
#define g_Keyword_BitDepth       "BitDepth"

//////////////////////////////////////////////////////////////////////////////
// CCameraFrontend class
//////////////////////////////////////////////////////////////////////////////
class CCameraFrontend : public CCameraBase<CCameraFrontend>  
{
public:
   CCameraFrontend();
   ~CCameraFrontend();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* name) const;      
   
   // MMCamera API
   // ------------
   int SnapImage();
   const unsigned char* GetImageBuffer();
   const unsigned int* GetImageBufferAsRGB32();
   unsigned GetNumberOfComponents() const;
   int GetComponentName(unsigned int channel, char* name);
   unsigned GetImageWidth() const;
   unsigned GetImageHeight() const;
   unsigned GetImageBytesPerPixel() const;
   unsigned GetBitDepth() const;
   long GetImageBufferSize() const;
   double GetExposure() const;
   void SetExposure(double exposure_ms);
   int SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize); 
   int GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize); 
   int ClearROI();
   double GetPixelSizeUm() const {return GetBinning();}
   int GetBinning() const;
   int SetBinning(int binSize);

   // Sequence related functions
   // ---------------
   int IsExposureSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}

   // action interface
   // ----------------
   int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnKeepOriginals(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBitDepth(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCameraName(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int SetAllowedBinning();
   int SetAllowedCameraNames(std::string& defaultCameraName);
   int SetAllowedShutterSpeeds();
   int SetShutterSpeed(double exposure_ms);
   int SetCameraShutterSpeed();
   double ShutterSpeedToMs(std::string shutterSpeed);
   bool GetBoolProperty(const char *const propName);
   int LoadImage(std::string imageFilename);
   int LoadImage(fipImage imageBitmap);
   void EscapeValues(std::vector<std::string>& valueList);
   void UnEscapeValue(std::string& value);
   
   int DetectCameraLiveView();      /* check whether the camera supports live view */
   bool UseCameraLiveView();        /* if true, use camera live viewfinder image */
   bool cameraSupportsLiveView_;    /* if true, camera supports capturing live viewfinder image */
   bool InLiveMode();               /* true if micro-manager is in Live mode  */

   ImgBuffer img_;
   CSimpleCam cam_;

   bool initialized_;
   bool grayScale_;           /* If true, create grayscale images. If false, create color images */
   unsigned bitDepth_;        /* number of bits per color value. */
   bool keepOriginals_;       /* if true, do not delete picture from disk */

   unsigned imgBinning_;      /* binning of current image */
   bool imgGrayScale_;        /* grayScale of current image */
   unsigned imgBitDepth_;     /* bitDepth of current image */
   
   unsigned roiX_;            /* Region Of Interest */
   unsigned roiY_;
   unsigned roiXSize_;
   unsigned roiYSize_;
   unsigned originX_;         /* coordinates of lower left corner of view window */
   unsigned originY_;
};

#endif //_TETHEREDCAMERA_H_
