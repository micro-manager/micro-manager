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

// COPYRIGHT:     University of California, San Francisco, 2006-2014
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

#ifndef _CORECALLBACK_H_
#define _CORECALLBACK_H_

#include "Devices/DeviceInstances.h"
#include "CoreUtils.h"
#include "MMCore.h"
#include "MMEventCallback.h"
#include "FastLogger.h"
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
      boost::shared_ptr<DeviceInstance> device;
      try
      {
         device = core_->deviceManager_.GetDevice(caller);
      }
      catch (const CMMError&)
      {
         LOG_ERROR(core_->coreLogger_) <<
            "Attempt to log message from unregistered device: " << msg;
         return DEVICE_OK;
      }
      return device->LogMessage(msg, debugOnly);
   }

   /**
    * Returns a direct pointer to the device with the specified name.
    */
   MM::Device* GetDevice(const MM::Device* caller, const char* label)
   {
      if (!caller || !label)
         return 0;

      try
      {
         MM::Device* pDevice = core_->deviceManager_.GetDevice(label)->GetRawPtr();
         if (pDevice == caller)
            return 0;
         return pDevice;
      }
      catch (const CMMError&)
      {
         return 0;
      }
   }
   
   MM::PortType GetSerialPortType(const char* portName) const
   {
      boost::shared_ptr<SerialInstance> pSerial;
      try
      {
         pSerial = core_->deviceManager_.GetDeviceOfType<SerialInstance>(portName);
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
   int OnStagePositionChanged(const MM::Device* device, double pos);
   int OnXYStagePositionChanged(const MM::Device* device, double xpos, double ypos);
   int OnFinished(const MM::Device* /* caller */);
   int OnExposureChanged(const MM::Device* device, double newExposure);
   int OnSLMExposureChanged(const MM::Device* device, double newExposure);
   int OnMagnifierChanged(const MM::Device* device);


   void NextPostedError(int& /*errorCode*/, char* /*pMessage*/, int /*maxlen*/, int& /*messageLength*/);
   void PostError(const  int, const char*);
   void ClearPostedErrors( void);


   // device management
   MM::ImageProcessor* GetImageProcessor(const MM::Device* /* caller */)
   {
      boost::shared_ptr<ImageProcessorInstance> imageProcessor =
         core_->currentImageProcessor_.lock();
      if (imageProcessor)
      {
         return imageProcessor->GetRawPtr();
      }
      return 0;
   }

   MM::State* GetStateDevice(const MM::Device* /* caller */, const char* label)
   {
      try
      {
         return core_->deviceManager_.GetDeviceOfType<StateInstance>(label)->
            GetRawPtr();
      }
      catch (const CMMError&)
      {
         return 0;
      }
   }

   MM::SignalIO* GetSignalIODevice(const MM::Device* /* caller */, const char* label)
   {
      try {
         return core_->deviceManager_.
            GetDeviceOfType<SignalIOInstance>(label)->GetRawPtr();
      }
      catch (const CMMError&)
      {
         return 0;
      }
   }

   MM::AutoFocus* GetAutoFocus(const MM::Device* /* caller */)
   {
      boost::shared_ptr<AutoFocusInstance> autofocus =
         core_->currentAutofocusDevice_.lock();
      if (autofocus)
      {
         return autofocus->GetRawPtr();
      }
      return 0;
   }

   MM::Hub* GetParentHub(const MM::Device* caller) const
   {
      if (caller == 0)
         return 0;

      boost::shared_ptr<HubInstance> hubDevice;
      try
      {
         hubDevice = core_->deviceManager_.GetParentDevice(core_->deviceManager_.GetDevice(caller));
      }
      catch (const CMMError&)
      {
         return 0;
      }
      if (hubDevice)
         return hubDevice->GetRawPtr();
      return 0;
   }

   MM::Device* GetPeripheral(const MM::Device* caller, unsigned idx) const
   {
      char hubLabel[MM::MaxStrLength];
      caller->GetLabel(hubLabel);
      std::vector<std::string> peripheralLabels = core_->deviceManager_.GetLoadedPeripherals(hubLabel);
      boost::shared_ptr<DeviceInstance> peripheral;
      try
      {
         if (idx < peripheralLabels.size())
            peripheral = core_->deviceManager_.GetDevice(peripheralLabels[idx]);
         else
            return 0;
      }
      catch(...)
      {
         // this should not happen
         assert(false);
         return 0;
      }
      if (peripheral)
         return peripheral->GetRawPtr();
      return 0;
   }

   unsigned GetNumberOfPeripherals(const MM::Device* caller)
   {
      char hubLabel[MM::MaxStrLength];
      caller->GetLabel(hubLabel);
      return (unsigned) core_->deviceManager_.GetLoadedPeripherals(hubLabel).size();
   }


   void GetLoadedDeviceOfType(const MM::Device* /* caller */, MM::DeviceType devType,  char* deviceName, const unsigned int deviceIterator)
   {
      deviceName[0] = 0;
      std::vector<std::string> v = core_->getLoadedDevicesOfType(devType);
      if( deviceIterator < v.size())
         strncpy( deviceName, v.at(deviceIterator).c_str(), MM::MaxStrLength);
      return;
   }

   MMThreadLock* getModuleLock(const MM::Device* caller);
   void removeModuleLock(const MM::Device* caller);

private:
   CMMCore* core_;
   MMThreadLock* pValueChangeLock_;

   Metadata AddCameraMetadata(const MM::Device* caller, const Metadata* pMd);

   int OnConfigGroupChanged(const char* groupName, const char* newConfigName);
   int OnPixelSizeChanged(double newPixelSizeUm);
};

#endif // _CORECALLBACK_H_
