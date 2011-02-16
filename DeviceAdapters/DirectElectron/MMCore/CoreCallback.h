///////////////////////////////////////////////////////////////////////////////
// FILE:          CoreCallback.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Callback object for MMCore device interface. Encapsulates
//                (bottom) internal API for calls going from devices to the 
//                core.
//              
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 01/23/2006

// COPYRIGHT:     University of California, San Francisco, 2006
//
// LICENSE:       This file is distributed under the "Lesser GPL" (LGPL) license.
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
// CVS:           $Id: CoreCallback.h 3801 2010-01-20 00:59:22Z karlh $
//
#ifdef WIN32
#pragma warning (disable : 4312 4244)
#endif

//#include <ace/OS.h>
//#include <ace/High_Res_Timer.h>
//#include <ace/Log_Msg.h>


#ifdef WIN32
#pragma warning (default : 4312 4244)
#endif

//#ifdef __APPLE__
//#include <sys/time.h>
//#endif

#include "IMMLogger.h"
#include "CoreUtils.h"
#include "MMCore.h"
#include "MMEventCallback.h"
#include "../MMDevice/DeviceUtils.h"


using namespace std;

///////////////////////////////////////////////////////////////////////////////
// CoreCallback class
// ------------------

class CoreCallback : public MM::Core
{
public:
   CoreCallback(CMMCore* c) : core_(c) {assert(core_);}
   ~CoreCallback() {}

   int GetDeviceProperty(const char* deviceName, const char* propName, char* value);
   int SetDeviceProperty(const char* deviceName, const char* propName, const char* value);

   /**
    * Writes a message to the Micro-Manager log file.
    */
   int LogMessage(const MM::Device* caller, const char* msg, bool debugOnly) const
   {
      char label[MM::MaxStrLength];
      caller->GetLabel(label);
      if (debugOnly)
         CORE_DEBUG2("Device %s debug message: %s\n", label, msg);
      else
         CORE_LOG2("Device %s message: %s\n", label, msg);
      return DEVICE_OK;
   }

   long GetNumberOfDevices() const
   {
      assert(core_);
      return (long)core_->getLoadedDevices().size();
   }

   /**
    * Returns a direct pointer to the device with the specified name.
    */
   MM::Device* GetDevice(const MM::Device* caller, const char* label)
   {
      assert(core_);
      MM::Device* pDev = 0;

      try
      {
         pDev = core_->getDevice(label);
         if (pDev == caller)
            return 0; // prevent caller from obtaining it's own address
      }
      catch (...)
      {
         // trap all exceptions
      }

      return pDev;
   }
   
   MM::PortType GetSerialPortType(const char* portName) const
   {
      MM::Serial* pSerial = 0;
      try
      {
         pSerial = core_->getSpecificDevice<MM::Serial>(portName);
      }
      catch (...)
      {
         return MM::InvalidPort;
      }

      return pSerial->GetPortType();
   }
 
   int WriteToSerial(const MM::Device* caller, const char* portName, const unsigned char* buf, unsigned long length);
   int ReadFromSerial(const MM::Device* caller, const char* portName, unsigned char* buf, unsigned long bufLength, unsigned long &bytesRead);
   int PurgeSerial(const MM::Device* caller, const char* portName);
   int SetSerialCommand(const MM::Device*, const char* portName, const char* command, const char* term);
   int GetSerialAnswer(const MM::Device*, const char* portName, unsigned long ansLength, char* answerTxt, const char* term);

	unsigned long GetClockTicksUs(const MM::Device* /*caller*/);

	// MMTime, in epoch beginning at 2000 01 01
   MM::MMTime GetCurrentMMTime();

   void Sleep (const MM::Device* /*caller*/, double intervalMs)
   {
		CDeviceUtils::SleepMs((long)(0.5+ intervalMs));
   }

   // continous acquisition support
   int InsertImage(const MM::Device* caller, const ImgBuffer& imgBuf);
   int InsertImage(const MM::Device* caller, const unsigned char* buf, unsigned width, unsigned height, unsigned byteDepth, const Metadata* pMd = 0);
   int InsertMultiChannel(const MM::Device* caller, const unsigned char* buf, unsigned numChannels, unsigned width, unsigned height, unsigned byteDepth, Metadata* pMd = 0);
   void SetAcqStatus(const MM::Device* caller, int statusCode);
   void ClearImageBuffer(const MM::Device* caller);
   bool InitializeImageBuffer(unsigned channels, unsigned slices, unsigned int w, unsigned int h, unsigned int pixDepth);

   int OpenFrame(const MM::Device* caller);
   int CloseFrame(const MM::Device* caller);
   int AcqFinished(const MM::Device* caller, int statusCode);
   int PrepareForAcq(const MM::Device* caller);

   // autofocus support
   const char* GetImage();
   int GetImageDimensions(int& width, int& height, int& depth);
   int GetFocusPosition(double& pos);
   int SetFocusPosition(double pos);
   int MoveFocus(double v);
   int SetXYPosition(double x, double y);
   int GetXYPosition(double& x, double& y);
   int MoveXYStage(double vX, double vY);
   int SetExposure(double expMs);
   int GetExposure(double& expMs);
   int SetConfig(const char* group, const char* name);
   int GetCurrentConfig(const char* group, int bufLen, char* name);
   int GetChannelConfigs(std::vector<std::string>& groups);

   // notification handlers
   int OnStatusChanged(const MM::Device* /* caller */);
   int OnPropertiesChanged(const MM::Device* /* caller */);
   int OnFinished(const MM::Device* /* caller */);

   int OnCoordinateUpdate(const MM::Device* /* caller */);

   std::vector<std::pair< int, std::string> > PostedErrors(void);
   void PostError(const std::pair< int, std::string>& );
   void ClearPostedErrors( void);


   // device management
   MM::ImageProcessor* GetImageProcessor(const MM::Device* /* caller */)
   {
      return core_->imageProcessor_;
   }

   MM::State* GetStateDevice(const MM::Device* /* caller */, const char* deviceName)
   {
      try {
         return core_->getSpecificDevice<MM::State>(deviceName);
      } catch(...) {
         //trap all exceptions
         return 0;
      }
   }

   MM::SignalIO* GetSignalIODevice(const MM::Device* /* caller */, const char* deviceName)
   {
      try {
         return core_->getSpecificDevice<MM::SignalIO>(deviceName);
      } catch(...) {
         //trap all exceptions
         return 0;
      }
   }

   std::vector<std::string> GetLoadedDevicesOfType(const MM::Device* /* caller */, MM::DeviceType devType)
   {
      return core_->getLoadedDevicesOfType(devType);
   }

private:
   CMMCore* core_;
};

