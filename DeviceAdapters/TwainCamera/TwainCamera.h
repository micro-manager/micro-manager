///////////////////////////////////////////////////////////////////////////////
// FILE:          TwainCamera.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   generic Twain camera adapter
//                
// COPYRIGHT:     University of California, San Francisco, 2009
//
// LICENSE:       This file is distributed under the BSD license.
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

#ifndef _TWAIN_CAMERA_H_
#define _TWAIN_CAMERA_H_

#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include "../../MMDevice/DeviceThreads.h"
#include <string>
#include <map>


// forward declaration for implementation class
class TwainDevice;
class CameraSequenceThread;

class TwainBad  // exception to throw upon error in Twain device
{
public:

	TwainBad(){};
	#ifdef WIN32
		#pragma warning(disable : 4996)
	#endif
	TwainBad(const char *const ptext):reason_(ptext){ };
	#ifdef WIN32
		#pragma warning(default : 4996)
	#endif
	const char* ReasonText(void){ return reason_.c_str();};
	std::string reason_;

};

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_UNKNOWN_MODE            102
#define ERR_BUSY_ACQIRING           105
#define ERR_UNSUPPORTED_IMAGE_TYPE  106
#define ERR_DEVICE_NOT_AVAILABLE    107

//////////////////////////////////////////////////////////////////////////////
// TwainCamera class
//streaming Camera device
//////////////////////////////////////////////////////////////////////////////
class TwainCamera : public CCameraBase<TwainCamera>  
{
public:
   TwainCamera();
   ~TwainCamera();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* name) const;      
   bool Busy();
   
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
   void SetExposure(double exp);
   int SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize); 
   int GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize); 
   int ClearROI();
   int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
	int StopSequenceAcquisition();
   double GetNominalPixelSizeUm() const {return nominalPixelSizeUm_;}
   double GetPixelSizeUm() const {return nominalPixelSizeUm_ * GetBinning();}
   int GetBinning() const;
   int SetBinning(int binSize);
   int IsExposureSequenceable(bool& isSequenceable) const {isSequenceable = false; return DEVICE_OK;}
	int NextTwainImageIntoImageBuffer(ImgBuffer& img);
	int StartTwainCamera(void);

	std::string deviceName_;

	// expose  CDeviceBase accessors, so that PImpl can use them
	MM::MMTime GetCurrentMMTime()
	{
		return CCameraBase<TwainCamera>::GetCurrentMMTime();
	};


private:

   // action interface
   // ----------------
	int OnCamera(MM::PropertyBase* pProp, MM::ActionType eAct);

	int OnVendorSettings(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct);

   int SetPixelTypesValues();

   //Do necessary for capturing
   //Is called from the thread function
   //Overrides ones defined in the CCameraBase class 
   bool IsCapturing();

   int ThreadRun();
   int PushImage();

   static const double nominalPixelSizeUm_;
   static int imageSizeW_;
   static int imageSizeH_;

   ImgBuffer img_[3];
   bool initialized_;
	bool cameraStarted_;
   bool busy_;
   long readoutUs_;
   MM::MMTime readoutStartTime_;
   bool color_;
   unsigned char* rawBuffer_;
   bool stopOnOverflow_;


   int ResizeImageBuffer(  int imageSizeW = imageSizeW_, 
                           int imageSizeH = imageSizeH_);
   int ResizeImageBuffer(
                           int imageSizeW, 
                           int imageSizeH, 
                           int byteDepth, 
                           int binSize = 1);
	TwainDevice* pTwainDevice_;
	bool stopRequest_;
	

   CameraSequenceThread * thd_;
   friend class CameraSequenceThread;


};


#endif //_TWAIN_CAMERA_H_
