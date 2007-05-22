///////////////////////////////////////////////////////////////////////////////
// FILE:          Andor.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Andor camera module
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 06/30/2006
// COPYRIGHT:     University of California, San Francisco, 2006
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
// CVS:           $Id$
//
#ifndef _ANDOR_H_
#define _ANDOR_H_

#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ImgBuffer.h"
#include "../../MMDevice/DeviceUtils.h"
#include <string>
#include <map>

// error codes
#define ERR_BUFFER_ALLOCATION_FAILED 101
#define ERR_INCOMPLETE_SNAP_IMAGE_CYCLE 102
#define ERR_INVALID_ROI 103
#define ERR_INVALID_READOUT_MODE_SETUP 103
#define ERR_CAMERA_DOES_NOT_EXIST 104

//////////////////////////////////////////////////////////////////////////////
// Implementation of the MMDevice and MMCamera interfaces
//
class Ixon : public CCameraBase<Ixon>
{
public:
   static Ixon* GetInstance();
   unsigned DeReference(); // jizhen 05.16.2007
   ~Ixon();

   // MMDevice API
   int Initialize();
   int Shutdown();

   void GetName(char* pszName) const;
   bool Busy() {return busy_;}

   // MMCamera API
   int SnapImage();
   const unsigned char* GetImageBuffer();
   unsigned GetImageWidth() const {return img_.Width();}
   unsigned GetImageHeight() const {return img_.Height();}
   unsigned GetImageBytesPerPixel() const {return img_.Depth();}
   long GetImageBufferSize() const {return img_.Width() * img_.Height() * GetImageBytesPerPixel();}
   unsigned GetBitDepth() const;
   double GetExposure() const;
   void SetExposure(double dExp);
   int SetROI(unsigned uX, unsigned uY, unsigned uXSize, unsigned uYSize);
   int GetROI(unsigned& uX, unsigned& uY, unsigned& uXSize, unsigned& uYSize);
   int ClearROI();

   // action interface for the camera
   int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnGain(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnEMGain(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnReadoutMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnReadoutTime(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnOffset(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnTemperature(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDriverDir(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnShutterMode(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnCooler(MM::PropertyBase* pProp, MM::ActionType eAct);// jizhen 05.11.2007
   int OnFanMode(MM::PropertyBase* pProp, MM::ActionType eAct);// jizhen 05.16.2007

private:
   Ixon();
   int ResizeImageBuffer();

   static Ixon* instance_;
   static unsigned refCount_;
   ImgBuffer img_;
   bool busy_;
   bool initialized_;
   bool snapInProgress_;

   struct ROI {
      int x;
      int y;
      int xSize;
      int ySize;

      ROI() : x(0), y(0), xSize(0), ySize(0) {}
      ~ROI() {}

      bool isEmpty() {return x==0 && y==0 && xSize==0 && ySize == 0;}
   };

   ROI roi_;
   int binSize_;
   double expMs_;
   std::string driverDir_;
   int fullFrameX_;
   int fullFrameY_;
   short* fullFrameBuffer_;
   std::vector<std::string> readoutModes_;

   int EmCCDGainLow_, EmCCDGainHigh_;
   int minTemp_, maxTemp_;
};

class Shutter : public CShutterBase<Shutter>
{
public:
   Shutter();
   ~Shutter();

   bool Busy();
   void GetName(char* pszName) const;
   int Initialize();
   int Shutdown();

   // Shutter API
   int SetOpen(bool open = true);
   int GetOpen(bool& open);
   int Fire(double deltaT);

   // action interface
   // ----------------
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnDelay(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   int SetShutterPosition(bool state);
   int GetShutterPosition(bool& state);
   bool initialized_;

   Ixon* camera_;
};

#endif //_ANDOR_H_
