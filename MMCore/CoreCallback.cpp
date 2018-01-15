///////////////////////////////////////////////////////////////////////////////
// FILE:          CoreCallback.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Callback object for MMCore device interface. Encapsulates
//                (bottom) internal API for calls going from devices to the 
//                core.
//
//                This class is essentially an extension of the CMMCore class
//                and has full access to CMMCore private members.
//              
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 01/05/2007
//
// COPYRIGHT:     University of California, San Francisco, 2007-2014
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

#include "../MMDevice/DeviceThreads.h"
#include "../MMDevice/DeviceUtils.h"
#include "../MMDevice/ImgBuffer.h"
#include "CircularBuffer.h"
#include "CoreCallback.h"
#include "DeviceManager.h"

#include <boost/date_time/posix_time/posix_time.hpp>
#include <string>
#include <vector>


CoreCallback::CoreCallback(CMMCore* c) :
   core_(c),
   pValueChangeLock_(NULL)
{
   assert(core_);
   pValueChangeLock_ = new MMThreadLock();
}


CoreCallback::~CoreCallback()
{
   delete pValueChangeLock_;
}


int
CoreCallback::LogMessage(const MM::Device* caller, const char* msg,
      bool debugOnly) const
{
   boost::shared_ptr<DeviceInstance> device;
   try
   {
      device = core_->deviceManager_->GetDevice(caller);
   }
   catch (const CMMError&)
   {
      LOG_ERROR(core_->coreLogger_) <<
         "Attempt to log message from unregistered device: " << msg;
      return DEVICE_OK;
   }
   return device->LogMessage(msg, debugOnly);
}


MM::Device*
CoreCallback::GetDevice(const MM::Device* caller, const char* label)
{
   if (!caller || !label)
      return 0;

   try
   {
      MM::Device* pDevice = core_->deviceManager_->GetDevice(label)->GetRawPtr();
      if (pDevice == caller)
         return 0;
      return pDevice;
   }
   catch (const CMMError&)
   {
      return 0;
   }
}


MM::PortType
CoreCallback::GetSerialPortType(const char* portName) const
{
   boost::shared_ptr<SerialInstance> pSerial;
   try
   {
      pSerial = core_->deviceManager_->GetDeviceOfType<SerialInstance>(portName);
   }
   catch (...)
   {
      return MM::InvalidPort;
   }

   return pSerial->GetPortType();
}


MM::ImageProcessor*
CoreCallback::GetImageProcessor(const MM::Device*)
{
   boost::shared_ptr<ImageProcessorInstance> imageProcessor =
      core_->currentImageProcessor_.lock();
   if (imageProcessor)
   {
      return imageProcessor->GetRawPtr();
   }
   return 0;
}


MM::State*
CoreCallback::GetStateDevice(const MM::Device*, const char* label)
{
   try
   {
      return core_->deviceManager_->GetDeviceOfType<StateInstance>(label)->
         GetRawPtr();
   }
   catch (const CMMError&)
   {
      return 0;
   }
}


MM::SignalIO*
CoreCallback::GetSignalIODevice(const MM::Device*, const char* label)
{
   try {
      return core_->deviceManager_->
         GetDeviceOfType<SignalIOInstance>(label)->GetRawPtr();
   }
   catch (const CMMError&)
   {
      return 0;
   }
}


MM::AutoFocus*
CoreCallback::GetAutoFocus(const MM::Device*)
{
   boost::shared_ptr<AutoFocusInstance> autofocus =
      core_->currentAutofocusDevice_.lock();
   if (autofocus)
   {
      return autofocus->GetRawPtr();
   }
   return 0;
}


MM::Hub*
CoreCallback::GetParentHub(const MM::Device* caller) const
{
   if (caller == 0)
      return 0;

   boost::shared_ptr<HubInstance> hubDevice;
   try
   {
      hubDevice = core_->deviceManager_->GetParentDevice(core_->deviceManager_->GetDevice(caller));
   }
   catch (const CMMError&)
   {
      return 0;
   }
   if (hubDevice)
      return hubDevice->GetRawPtr();
   return 0;
}


void
CoreCallback::GetLoadedDeviceOfType(const MM::Device*, MM::DeviceType devType,
      char* deviceName, const unsigned int deviceIterator)
{
   deviceName[0] = 0;
   std::vector<std::string> v = core_->getLoadedDevicesOfType(devType);
   if( deviceIterator < v.size())
      strncpy( deviceName, v.at(deviceIterator).c_str(), MM::MaxStrLength);
   return;
}


void
CoreCallback::Sleep(const MM::Device*, double intervalMs)
{
   CDeviceUtils::SleepMs((long)(0.5 + intervalMs));
}


/**
 * Get the metadata tags attached to device caller, and merge them with metadata
 * in pMd (if not null). Returns a metadata object.
 */
Metadata
CoreCallback::AddCameraMetadata(const MM::Device* caller, const Metadata* pMd)
{
   Metadata newMD;
   if (pMd)
   {
      newMD = *pMd;
   }

   boost::shared_ptr<CameraInstance> camera =
      boost::static_pointer_cast<CameraInstance>(
            core_->deviceManager_->GetDevice(caller));

   std::string label = camera->GetLabel();
   newMD.put("Camera", label);

   std::string serializedMD;
   try
   {
      serializedMD = camera->GetTags();
   }
   catch (const CMMError&)
   {
      return newMD;
   }

   Metadata devMD;
   devMD.Restore(serializedMD.c_str());
   newMD.Merge(devMD);

   return newMD;
}

int CoreCallback::InsertImage(const MM::Device* caller, const unsigned char* buf, unsigned width, unsigned height, unsigned byteDepth, const char* serializedMetadata, const bool doProcess)
{
   Metadata md;
   md.Restore(serializedMetadata);
   return InsertImage(caller, buf, width, height, byteDepth, &md, doProcess);
}

int CoreCallback::InsertImage(const MM::Device* caller, const unsigned char* buf, unsigned width, unsigned height, unsigned byteDepth, const Metadata* pMd, bool doProcess)
{
   try 
   {
      Metadata md = AddCameraMetadata(caller, pMd);

      if(doProcess)
      {
         MM::ImageProcessor* ip = GetImageProcessor(caller);
         if( NULL != ip)
         {
            ip->Process(const_cast<unsigned char*>(buf), width, height, byteDepth);
         }
      }
      if (core_->cbuf_->InsertImage(buf, width, height, byteDepth, &md))
         return DEVICE_OK;
      else
         return DEVICE_BUFFER_OVERFLOW;
   }
   catch (CMMError& /*e*/)
   {
      return DEVICE_INCOMPATIBLE_IMAGE;
   }
}

int CoreCallback::InsertImage(const MM::Device* caller, const unsigned char* buf, unsigned width, unsigned height, unsigned byteDepth, unsigned nComponents, const char* serializedMetadata, const bool doProcess)
{
   Metadata md;
   md.Restore(serializedMetadata);
   return InsertImage(caller, buf, width, height, byteDepth, nComponents, &md, doProcess);
}

int CoreCallback::InsertImage(const MM::Device* caller, const unsigned char* buf, unsigned width, unsigned height, unsigned byteDepth, unsigned nComponents, const Metadata* pMd, bool doProcess)
{
   try 
   {
      Metadata md = AddCameraMetadata(caller, pMd);

      if(doProcess)
      {
         MM::ImageProcessor* ip = GetImageProcessor(caller);
         if( NULL != ip)
         {
            ip->Process(const_cast<unsigned char*>(buf), width, height, byteDepth);
         }
      }
      if (core_->cbuf_->InsertImage(buf, width, height, byteDepth, nComponents, &md))
         return DEVICE_OK;
      else
         return DEVICE_BUFFER_OVERFLOW;
   }
   catch (CMMError& /*e*/)
   {
      return DEVICE_INCOMPATIBLE_IMAGE;
   }
}

int CoreCallback::InsertImage(const MM::Device* caller, const ImgBuffer & imgBuf)
{
   Metadata md = imgBuf.GetMetadata();
   unsigned char* p = const_cast<unsigned char*>(imgBuf.GetPixels());
   MM::ImageProcessor* ip = GetImageProcessor(caller);
   if( NULL != ip)
   {
      ip->Process(p, imgBuf.Width(), imgBuf.Height(), imgBuf.Depth());
   }

   return InsertImage(caller, imgBuf.GetPixels(), imgBuf.Width(), 
      imgBuf.Height(), imgBuf.Depth(), &md);
}

void CoreCallback::ClearImageBuffer(const MM::Device* /*caller*/)
{
   core_->cbuf_->Clear();
}

bool CoreCallback::InitializeImageBuffer(unsigned channels, unsigned slices,
      unsigned int w, unsigned int h, unsigned int pixDepth)
{
   // Support for multi-slice images has not been implemented
   if (slices != 1)
      return false;

   return core_->cbuf_->Initialize(channels, w, h, pixDepth);
}

int CoreCallback::InsertMultiChannel(const MM::Device* caller,
                              const unsigned char* buf,
                              unsigned numChannels,
                              unsigned width,
                              unsigned height,
                              unsigned byteDepth,
                              Metadata* pMd)
{
   try
   {
      Metadata md = AddCameraMetadata(caller, pMd);

      MM::ImageProcessor* ip = GetImageProcessor(caller);
      if( NULL != ip)
      {
         ip->Process( const_cast<unsigned char*>(buf), width, height, byteDepth);
      }
      if (core_->cbuf_->InsertMultiChannel(buf, numChannels, width, height, byteDepth, &md))
         return DEVICE_OK;
      else
         return DEVICE_BUFFER_OVERFLOW;
   }
   catch (CMMError& /*e*/)
   {
      return DEVICE_INCOMPATIBLE_IMAGE;
   }

}

int CoreCallback::AcqFinished(const MM::Device* caller, int /*statusCode*/)
{
   boost::shared_ptr<DeviceInstance> camera;
   try
   {
      camera = core_->deviceManager_->GetDevice(caller);
   }
   catch (const CMMError&)
   {
      LOG_ERROR(core_->coreLogger_) <<
         "AcqFinished() called from unregistered device";
      return DEVICE_ERR;
   }

   boost::shared_ptr<DeviceInstance> currentCamera =
      core_->currentCameraDevice_.lock();

   if (core_->autoShutter_)
   {
      boost::shared_ptr<ShutterInstance> shutter =
         core_->currentShutterDevice_.lock();
      if (shutter)
      {
         // We need to lock the shutter's module for thread safety, but there's
         // a case where deadlock would result.
         if (camera->GetAdapterModule() == shutter->GetAdapterModule())
         {
            // This is a nasty hack to allow the case where the shutter and
            // camera live in the same module. It is not safe, but this is how
            // _all_ cases used to be implemented, and I can't immediately
            // think of a fully safe fix that is reasonably simple.
            shutter->SetOpen(false);
         }
         else if (currentCamera && currentCamera->GetAdapterModule() ==
               shutter->GetAdapterModule())
         {
            // Likewise, we might be called as a result of a call to
            // StopSequenceAcquisition() on a virtual wrapper camera device
            // (such as Multi Camera), in which case we would get a deadlock if
            // the shutter is in the same module as the virtual camera.
            // This is an even nastier hack in that it ignores the possibility
            // of StopSequenceAcquisition() being called on a camera other than
            // currentCamera, but such cases are rare.
            shutter->SetOpen(false);
         }
         else
         {
            // If the shutter is in a different device adapter, it is safe to
            // lock that adapter.
            mm::DeviceModuleLockGuard g(shutter);
            shutter->SetOpen(false);

            // We could wait for the shutter to close here, but the
            // implementation has always returned without waiting. The camera
            // doesn't care, so let's keep the behavior. Thus,
            // stopSequenceAcquisition() does not wait for the shutter before
            // returning.
         }
      }
   }
   return DEVICE_OK;
}

int CoreCallback::PrepareForAcq(const MM::Device* /*caller*/)
{
   if (core_->autoShutter_)
   {
      boost::shared_ptr<ShutterInstance> shutter =
         core_->currentShutterDevice_.lock();
      if (shutter)
      {
         {
            mm::DeviceModuleLockGuard g(shutter);
            shutter->SetOpen(true);
         }
         core_->waitForDevice(shutter);
      }
   }
   return DEVICE_OK;
}

/**
 * Handler for the property change event from the device.
 */
int CoreCallback::OnPropertiesChanged(const MM::Device* /* caller */)
{
   if (core_->externalCallback_)
      core_->externalCallback_->onPropertiesChanged();

   // TODO It is inconsistent that we do not update the system state cache in
   // this case. However, doing so would be time-consuming (if not unsafe).

   return DEVICE_OK;
}

/**
 * Device signals that a specific property changed and reports the new value
 */
int CoreCallback::OnPropertyChanged(const MM::Device* device, const char* propName, const char* value)
{
   if (core_->externalCallback_) 
   {
      MMThreadGuard g(*pValueChangeLock_);
      char label[MM::MaxStrLength];
      device->GetLabel(label);
      bool readOnly;
      device->GetPropertyReadOnly(propName, readOnly);
      const PropertySetting* ps = new PropertySetting(label, propName, value, readOnly);
      {
         MMThreadGuard scg(core_->stateCacheLock_);
         core_->stateCache_.addSetting(*ps);
      }
      core_->externalCallback_->onPropertyChanged(label, propName, value);

      // Find all configs that contain this property and callback to indicate 
      // that the config group changed
      // TODO: Assess whether performance is better by maintaining a map tying
      // property to configurations
      std::vector<std::string> configGroups = 
         core_->getAvailableConfigGroups ();
      for (std::vector<std::string>::iterator it = configGroups.begin(); 
            it != configGroups.end(); ++it) 
      {
         std::vector<std::string> configs = 
            core_->getAvailableConfigs((*it).c_str());
         bool found = false;
         for (std::vector<std::string>::iterator itc = configs.begin();
               itc != configs.end() && !found; itc++) 
         {
            Configuration config = 
               core_->getConfigData((*it).c_str(), (*itc).c_str());
            if (config.isPropertyIncluded(label, propName)) {
               found = true;
               // If we are part of this configuration, notify that it 
               // was changed. Get the new config from cache rather 
               // than by querying the hardware
               std::string currentConfig = 
                  core_->getCurrentConfigFromCache( (*it).c_str() );
               OnConfigGroupChanged((*it).c_str(), currentConfig.c_str());
            }
         }
      }
          

      // Check if pixel size was potentially affected.  If so, update from cache
      std::vector<std::string> pixelSizeConfigs = core_->getAvailablePixelSizeConfigs();
      bool found = false;
      for (std::vector<std::string>::iterator itpsc = pixelSizeConfigs.begin();
            itpsc != pixelSizeConfigs.end() && !found; itpsc++) 
      {
         Configuration pixelSizeConfig = core_->getPixelSizeConfigData( (*itpsc).c_str());
         if (pixelSizeConfig.isPropertyIncluded(label, propName)) {
            found = true;
            double pixSizeUm;
            try {
               // update pixel size from cache
               pixSizeUm = core_->getPixelSizeUm(true);
            }
            catch (CMMError ) {
               pixSizeUm = 0.0;
            }
            OnPixelSizeChanged(pixSizeUm);
         }
      }
   }

   return DEVICE_OK;
}

/**
 * Callback indicating that a configuration group has changed
 */
int CoreCallback::OnConfigGroupChanged(const char* groupName, const char* newConfigName)
{
   if (core_->externalCallback_) {
      core_->externalCallback_->onConfigGroupChanged(groupName, newConfigName);
   }

   return DEVICE_OK;
}

/**
 * Callback indicating that Pixel Size has changed
 */
int CoreCallback::OnPixelSizeChanged(double newPixelSizeUm)
{
   if (core_->externalCallback_) {
      core_->externalCallback_->onPixelSizeChanged(newPixelSizeUm);
   }

   return DEVICE_OK;
}

/**
 * Handler for Stage position update
 */
int CoreCallback::OnStagePositionChanged(const MM::Device* device, double pos)
{
   if (core_->externalCallback_) {
      char label[MM::MaxStrLength];
      device->GetLabel(label);
      core_->externalCallback_->onStagePositionChanged(label, pos);
   }

   return DEVICE_OK;
}

/**
 * Handler for XYStage position update
 */
int CoreCallback::OnXYStagePositionChanged(const MM::Device* device, double xPos, double yPos)
{
   if (core_->externalCallback_) {
      char label[MM::MaxStrLength];
      device->GetLabel(label);
      core_->externalCallback_->onXYStagePositionChanged(label, xPos, yPos);
   }

   return DEVICE_OK;
}

/**
 * Handler for exposure update
 * 
 */
int CoreCallback::OnExposureChanged(const MM::Device* device, double newExposure)
{
   if (core_->externalCallback_) {
      char label[MM::MaxStrLength];
      device->GetLabel(label);
      core_->externalCallback_->onExposureChanged(label, newExposure);
   }
   return DEVICE_OK;
}

/**
 * Handler for SLM exposure update
 * 
 */
int CoreCallback::OnSLMExposureChanged(const MM::Device* device, double newExposure)
{
   if (core_->externalCallback_) {
      MMThreadGuard g(*pValueChangeLock_);
      char label[MM::MaxStrLength];
      device->GetLabel(label);
      core_->externalCallback_->onSLMExposureChanged(label, newExposure);
   }
   return DEVICE_OK;
}

/**
 * Handler for magnifier changer
 * 
 */
int CoreCallback::OnMagnifierChanged(const MM::Device* /* device */)
{
   if (core_->externalCallback_) 
   {
      double pixSizeUm;
      try 
      {
         // update pixel size from cache
         pixSizeUm = core_->getPixelSizeUm(true);
      }
      catch (CMMError ) {
         pixSizeUm = 0.0;
      }
      OnPixelSizeChanged(pixSizeUm);
   }
   return DEVICE_OK;
}



int CoreCallback::SetSerialProperties(const char* portName,
                                      const char* answerTimeout,
                                      const char* baudRate,
                                      const char* delayBetweenCharsMs,
                                      const char* handshaking,
                                      const char* parity,
                                      const char* stopBits)
{
   try
   {
      core_->setSerialProperties(portName, answerTimeout, baudRate,
         delayBetweenCharsMs, handshaking, parity, stopBits);
   }
   catch (CMMError& e)
   {
      return e.getCode();
   }

   return DEVICE_OK;
}

/**
 * Sends an array of bytes to the port.
 */
int CoreCallback::WriteToSerial(const MM::Device* caller, const char* portName, const unsigned char* buf, unsigned long length)
{
   boost::shared_ptr<SerialInstance> pSerial;
   try
   {
      pSerial = core_->deviceManager_->GetDeviceOfType<SerialInstance>(portName);
   }
   catch (CMMError& err)
   {
      return err.getCode();    
   }
   catch (...)
   {
      return DEVICE_SERIAL_COMMAND_FAILED;
   }

   // don't allow self reference
   if (pSerial->GetRawPtr() == caller)
      return DEVICE_SELF_REFERENCE;

   return pSerial->Write(buf, length);
}
   
/**
  * Reads bytes form the port, up to the buffer length.
  */
int CoreCallback::ReadFromSerial(const MM::Device* caller, const char* portName, unsigned char* buf, unsigned long bufLength, unsigned long &bytesRead)
{
   boost::shared_ptr<SerialInstance> pSerial;
   try
   {
      pSerial = core_->deviceManager_->GetDeviceOfType<SerialInstance>(portName);
   }
   catch (CMMError& err)
   {
      return err.getCode();    
   }
   catch (...)
   {
      return DEVICE_SERIAL_COMMAND_FAILED;
   }

   // don't allow self reference
   if (pSerial->GetRawPtr() == caller)
      return DEVICE_SELF_REFERENCE;

   return pSerial->Read(buf, bufLength, bytesRead);
}

/**
 * Clears port buffers.
 */
int CoreCallback::PurgeSerial(const MM::Device* caller, const char* portName)
{
   boost::shared_ptr<SerialInstance> pSerial;
   try
   {
      pSerial = core_->deviceManager_->GetDeviceOfType<SerialInstance>(portName);
   }
   catch (CMMError& err)
   {
      return err.getCode();    
   }
   catch (...)
   {
      return DEVICE_SERIAL_COMMAND_FAILED;
   }

   // don't allow self reference
   if (pSerial->GetRawPtr() == caller)
      return DEVICE_SELF_REFERENCE;

   return pSerial->Purge();
}

/**
 * Sends an ASCII command terminated by the specified character sequence.
 */
int CoreCallback::SetSerialCommand(const MM::Device*, const char* portName, const char* command, const char* term)
{
   try {
      core_->setSerialPortCommand(portName, command, term);
   }
   catch (...)
   {
      // trap all exceptions and return generic serial error
      return DEVICE_SERIAL_COMMAND_FAILED;
   }
   return DEVICE_OK;
}

/**
 * Receives an ASCII string terminated by the specified character sequence.
 * The terminator string is stripped of the answer. If the termination code is not
 * received within the com port timeout and error will be flagged.
 */
int CoreCallback::GetSerialAnswer(const MM::Device*, const char* portName, unsigned long ansLength, char* answerTxt, const char* term)
{
   std::string answer;
   try {
      answer = core_->getSerialPortAnswer(portName, term);
      if (answer.length() >= ansLength)
         return DEVICE_SERIAL_BUFFER_OVERRUN;
   }
   catch (...)
   {
      // trap all exceptions and return generic serial error
      return DEVICE_SERIAL_COMMAND_FAILED;
   }
   strcpy(answerTxt, answer.c_str());
   return DEVICE_OK;
}

const char* CoreCallback::GetImage()
{
   try
   {
      core_->snapImage();
      return (const char*) core_->getImage();
   }
   catch (...)
   {
      return 0;
   }
}

int CoreCallback::GetImageDimensions(int& width, int& height, int& depth)
{
   width = core_->getImageWidth();
   height = core_->getImageHeight();
   depth = core_->getBytesPerPixel();
   return DEVICE_OK;
}

int CoreCallback::GetFocusPosition(double& pos)
{
   boost::shared_ptr<StageInstance> focus = core_->currentFocusDevice_.lock();
   if (focus)
   {
      return focus->GetPositionUm(pos);
   }
   pos = 0.0;
   return DEVICE_CORE_FOCUS_STAGE_UNDEF;
}

int CoreCallback::SetFocusPosition(double pos)
{
   boost::shared_ptr<StageInstance> focus = core_->currentFocusDevice_.lock();
   if (focus)
   {
      int ret = focus->SetPositionUm(pos);
      if (ret != DEVICE_OK)
         return ret;
      core_->waitForDevice(focus);
      return DEVICE_OK;
   }
   return DEVICE_CORE_FOCUS_STAGE_UNDEF;
}


int CoreCallback::MoveFocus(double velocity)
{
   boost::shared_ptr<StageInstance> focus = core_->currentFocusDevice_.lock();
   if (focus)
   {
      mm::DeviceModuleLockGuard g(focus);
      int ret = focus->Move(velocity);
      if (ret != DEVICE_OK)
         return ret;
      return DEVICE_OK;
   }
   return DEVICE_CORE_FOCUS_STAGE_UNDEF;
}


int CoreCallback::GetXYPosition(double& x, double& y)
{
   boost::shared_ptr<XYStageInstance> xyStage =
      core_->currentXYStageDevice_.lock();
   if (xyStage)
   {
      return xyStage->GetPositionUm(x, y);
   }
   x = 0.0;
   y = 0.0;
   return DEVICE_CORE_FOCUS_STAGE_UNDEF;
}

int CoreCallback::SetXYPosition(double x, double y)
{
   boost::shared_ptr<XYStageInstance> xyStage =
      core_->currentXYStageDevice_.lock();
   if (xyStage)
   {
      int ret = xyStage->SetPositionUm(x, y);
      if (ret != DEVICE_OK)
         return ret;
      core_->waitForDevice(xyStage);
      return DEVICE_OK;
   }
   return DEVICE_CORE_FOCUS_STAGE_UNDEF;
}

int CoreCallback::MoveXYStage(double vx, double vy)
{
   boost::shared_ptr<XYStageInstance> xyStage =
      core_->currentXYStageDevice_.lock();
   if (xyStage)
   {
      mm::DeviceModuleLockGuard g(xyStage);
      int ret = xyStage->Move(vx, vy);
      if (ret != DEVICE_OK)
         return ret;
      return DEVICE_OK;
   }
   return DEVICE_CORE_FOCUS_STAGE_UNDEF;
}

int CoreCallback::SetExposure(double expMs)
{
   try 
   {
      core_->setExposure(expMs);
   }
   catch (...)
   {
      // TODO: log
      return DEVICE_CORE_EXPOSURE_FAILED;
   }

   return DEVICE_OK;
}

int CoreCallback::GetExposure(double& expMs) 
{
   try 
   {
      expMs = core_->getExposure();
   }
   catch (...)
   {
      // TODO: log
      return DEVICE_CORE_EXPOSURE_FAILED;
   }

   return DEVICE_OK;
}

int CoreCallback::SetConfig(const char* group, const char* name)
{
   try 
   {
      core_->setConfig(group, name);
      core_->waitForConfig(group, name);
   }
   catch (...)
   {
      // TODO: log
      return DEVICE_CORE_CONFIG_FAILED;
   }

   return DEVICE_OK;
}

int CoreCallback::GetCurrentConfig(const char* group, int bufLen, char* name)
{
   try 
   {
      std::string cfgName = core_->getCurrentConfig(group);
      strncpy(name, cfgName.c_str(), bufLen);
   }
   catch (...)
   {
      // TODO: log
      return DEVICE_CORE_CONFIG_FAILED;
   }

   return DEVICE_OK;
}

int CoreCallback::GetChannelConfig(char* channelConfigName, const unsigned int channelConfigIterator)
{
   if (0 == channelConfigName)
      return DEVICE_CORE_CHANNEL_PRESETS_FAILED;
   try 
   {
      channelConfigName[0] = 0;

      std::vector<std::string> cfgs = core_->getAvailableConfigs(core_->getChannelGroup().c_str());
      if( channelConfigIterator < cfgs.size())
      {
         strncpy( channelConfigName, cfgs.at(channelConfigIterator).c_str(), MM::MaxStrLength);
      }
   }
   catch (...)
   {
      return DEVICE_CORE_CHANNEL_PRESETS_FAILED;
   }

   return DEVICE_OK;
}

int CoreCallback::GetDeviceProperty(const char* deviceName, const char* propName, char* value)
{
   try
   {
      std::string propVal = core_->getProperty(deviceName, propName);
      CDeviceUtils::CopyLimitedString(value, propVal.c_str());
   }
   catch(CMMError& e)
   {
      return e.getCode();
   }

   return DEVICE_OK;
}

int CoreCallback::SetDeviceProperty(const char* deviceName, const char* propName, const char* value)
{
   try
   {
      std::string propVal(value);
      core_->setProperty(deviceName, propName, propVal.c_str());
   }
   catch(CMMError& e)
   {
      return e.getCode();
   }

   return DEVICE_OK;
}

void CoreCallback::NextPostedError(int& errorCode, char* pMessage, int maxlen, int& messageLength)
{
   MMThreadGuard g(*(core_->pPostedErrorsLock_));
   errorCode = 0;
   messageLength = 0;
   if( 0 < core_->postedErrors_.size())
   {
      std::pair< int, std::string> nextError = core_->postedErrors_.front();
      core_->postedErrors_.pop_front();
      errorCode = nextError.first;
      if( 0 != pMessage)
      {
         if( 0 < maxlen )
         {
            *pMessage = 0;
#ifdef _WINDOWS
            messageLength = min( maxlen, (int) nextError.second.length());
#else
            messageLength = std::min( maxlen, (int) nextError.second.length());
#endif
            strncpy(pMessage, nextError.second.c_str(), messageLength);
         }
      }
   }
	return ;
}

void CoreCallback::PostError(const int errorCode, const char* pMessage)
{
   MMThreadGuard g(*(core_->pPostedErrorsLock_));
   core_->postedErrors_.push_back(std::make_pair(errorCode, std::string(pMessage)));
}

void CoreCallback::ClearPostedErrors()
{
   MMThreadGuard g(*(core_->pPostedErrorsLock_));
	core_->postedErrors_.clear();
}






/**
 * Returns the number of microsecond tick
 * N.B. an unsigned long microsecond count rolls over in just over an hour!!!!
 * NOTE: This method is 'obsolete.'
 */
unsigned long CoreCallback::GetClockTicksUs(const MM::Device* /*caller*/)
{
	using namespace boost::posix_time;
	using namespace boost::gregorian;
	boost::posix_time::ptime t = boost::posix_time::microsec_clock::local_time();
	boost::gregorian::date today( day_clock::local_day());
	boost::posix_time::ptime timet_start(today); 
	time_duration diff = t - timet_start; 
	return (unsigned long) diff.total_microseconds();
}

MM::MMTime CoreCallback::GetCurrentMMTime()
{		
	return GetMMTimeNow();
}
