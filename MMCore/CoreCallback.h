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
// CVS:           $Id$
//

#ifndef _CORECALLBACK_H_
#define _CORECALLBACK_H_

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
   CoreCallback(CMMCore* c) : core_(c), pValueChangeLock_(NULL) 
   {
      assert(core_);
      pValueChangeLock_ = new MMThreadLock();
   }
   ~CoreCallback() { delete pValueChangeLock_; }

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
 
   int SetSerialProperties(const char* portName,
                           const char* answerTimeout,
                           const char* baudRate,
                           const char* delayBetweenCharsMs,
                           const char* handshaking,
                           const char* parity,
                           const char* stopBits);

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
   int InsertImage(const MM::Device* caller, const unsigned char* buf, unsigned width, unsigned height, unsigned byteDepth, const char* serializedMetadata, const bool doProcess = true);

   /*Deprecated*/ int InsertImage(const MM::Device* caller, const unsigned char* buf, unsigned width, unsigned height, unsigned byteDepth, const Metadata* pMd = 0, const bool doProcess = true);

   int InsertMultiChannel(const MM::Device* caller, const unsigned char* buf, unsigned numChannels, unsigned width, unsigned height, unsigned byteDepth, Metadata* pMd = 0);
   void SetAcqStatus(const MM::Device* caller, int statusCode);
   void ClearImageBuffer(const MM::Device* caller);
   bool InitializeImageBuffer(unsigned channels, unsigned slices, unsigned int w, unsigned int h, unsigned int pixDepth);
   long getImageBufferTotalFrames() {return core_->getBufferTotalCapacity();}
   long getImageBufferFreeFrames() {return core_->getBufferFreeCapacity();}

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
   int GetChannelConfig(char* channelConfigName, const unsigned int channelConfigIterator);

   // notification handlers
   int OnStatusChanged(const MM::Device* /* caller */);
   int OnPropertiesChanged(const MM::Device* /* caller */);
   int OnPropertyChanged(const MM::Device* device, const char* propName, const char* value);
   int OnConfigGroupChanged(const char* groupName, const char* newConfigName);
   int OnPixelSizeChanged(double newPixelSizeUm);
   int OnStagePositionChanged(const MM::Device* device, double pos);
   int OnXYStagePositionChanged(const MM::Device* device, double xpos, double ypos);
   int OnFinished(const MM::Device* /* caller */);


   void NextPostedError(int& /*errorCode*/, char* /*pMessage*/, int /*maxlen*/, int& /*messageLength*/);
   void PostError(const  int, const char*);
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

   MM::AutoFocus* GetAutoFocus(const MM::Device* /* caller */)
   {
      try {
         return core_->autoFocus_;
      } catch(...) {
         //trap all exceptions
         return 0;
      }
   }

   MM::Hub* GetParentHub(const MM::Device* caller) const
   {
      if (caller == 0)
         return 0;

      return core_->pluginManager_.GetParentDevice(*caller);
   }

   MM::Device* GetPeripheral(const MM::Device* caller, unsigned idx) const
   {
      std::vector<MM::Device*> peripherals;
      char hubLabel[MM::MaxStrLength];
      caller->GetLabel(hubLabel);
      std::vector<std::string> peripheralLabels = core_->pluginManager_.GetLoadedPeripherals(hubLabel);
      try
      {
         if (idx < peripheralLabels.size())
            return core_->pluginManager_.GetDevice(peripheralLabels[idx].c_str());
         else
            return 0;
      }
      catch(...)
      {
         // this should not happen
         assert(false);
         return 0;
      }
   }

   unsigned GetNumberOfPeripherals(const MM::Device* caller)
   {
      char hubLabel[MM::MaxStrLength];
      caller->GetLabel(hubLabel);
      return (unsigned) core_->pluginManager_.GetLoadedPeripherals(hubLabel).size();
   }


   void GetLoadedDeviceOfType(const MM::Device* /* caller */, MM::DeviceType devType,  char* deviceName, const unsigned int deviceIterator)
   {
      deviceName[0] = 0;
      std::vector<std::string> v = core_->getLoadedDevicesOfType(devType);
      if( deviceIterator < v.size())
         strncpy( deviceName, v.at(deviceIterator).c_str(), MM::MaxStrLength);
      return;
   }
//#if 0
   // device discovery  -- todo do we need this on the callback??
   MM::DeviceDetectionStatus DetectDevice(const MM::Device* /*pCaller*/, char* deviceName)
   {
      MM::DeviceDetectionStatus result = MM::Unimplemented; 
      try
      {
         if( NULL != deviceName)
         {
            if( 0 < strlen(deviceName))
            {

               result = core_->detectDevice(deviceName);
            }
         }
      }
      catch (...)
      {
         // trap all exceptions
      }

      return result;
   }
//

private:
   CMMCore* core_;
   MMThreadLock* pValueChangeLock_;

};

#endif // _CORECALLBACK_H_
