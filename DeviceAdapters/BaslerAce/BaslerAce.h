///////////////////////////////////////////////////////////////////////////////
// FILE:          BaslerAce.h
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Adapter for Basler Ace Cameras
//
// COPYRIGHT:     Henry Pinkard, 2018
//
// LICENSE:       This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//                
//                You should have received a copy of the GNU Lesser General Public
//                License along with the source distribution; if not, write to
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
//                Boston, MA  02111-1307  USA
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.  
//                
// AUTHOR:        Henry Pinkard, 2018
//                


#pragma once

#include "DeviceBase.h"
#include "DeviceThreads.h"
#include <string>
#include <vector>
#include <map>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
//#define ERR_UNKNOWN_BINNING_MODE 410


//////////////////////////////////////////////////////////////////////////////
// Basler USB Ace camera class
//////////////////////////////////////////////////////////////////////////////

class BaslerCamera : public CCameraBase<BaslerCamera>  {
public:
   BaslerCamera();
   ~BaslerCamera();
  
   // MMDevice API
   // ------------
   int Initialize();
   int Shutdown();
  
   void GetName(char* name) const;      
   bool Busy() {return false;}
  
   // MMCamera API
   // ------------
   int SnapImage();
   const unsigned char* GetImageBuffer();

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
   int GetBinning() const;
   int SetBinning(int binSize);
   int IsExposureSequenceable(bool& seq) const {seq = false; return DEVICE_OK;}

    /**
     * Starts continuous acquisition.
     */
    int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
    int StartSequenceAcquisition(double interval_ms);
    int StopSequenceAcquisition();
    int PrepareSequenceAcqusition();
    
    /**
     * Flag to indicate whether Sequence Acquisition is currently running.
     * Return true when Sequence acquisition is activce, false otherwise
     */
    bool IsCapturing();

   // action interface
   // ----------------
	   int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGain(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnOffset(MM::PropertyBase* pProp, MM::ActionType eAct);
  // int OnSensorReadoutMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnShutterMode(MM::PropertyBase* pProp, MM::ActionType eAct);


private:

   CInstantCamera camera_;


   unsigned maxWidth_, maxHeight_;
   double exposure_us_, exposureMax_, exposureMin_;
   double gain_, gainMax_, gainMin_;
   double offset_, offsetMin_, offsetMax_;
   unsigned bitDepth_;
   std::string pixelType_;
   std::string sensorReadoutMode_;
   std::string shutterMode_;
   void* imgBuffer_;
   INodeMap* nodeMap_;

   bool initialized_;

   //MM::MMTime startTime_;


   void ResizeSnapBuffer();
};

//Callback class for putting frames in circular buffer as they arrive
class CircularBufferInserter : public CImageEventHandler {
private:
	BaslerCamera* dev_;

public:
	CircularBufferInserter(BaslerCamera* dev);

	virtual void OnImageGrabbed( CInstantCamera& camera, const CGrabResultPtr& ptrGrabResult);
};


