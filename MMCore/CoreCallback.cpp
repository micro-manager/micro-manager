///////////////////////////////////////////////////////////////////////////////
// FILE:          CoreCallback.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Callback object for MMCore device interface. Encapsulates
//                (bottom) internal API for calls going from devices to the 
//                core.
//
//                This class is essentialy an extension of the CMMCore class
//                and has full access to CMMCore private members.
//              
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 01/05/2007

// COPYRIGHT:     University of California, San Francisco, 2007
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
// CVS:           $Id: CoreCallback.h 2 2007-02-27 23:33:17Z nenad $
//

#include "CoreCallback.h"
#include "CircularBuffer.h"
#include "../MMDevice/DeviceUtils.h"
#include "../MMDevice/DeviceThreads.h"
#include "boost/date_time/posix_time/posix_time.hpp"


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

      if(doProcess)
      {
         MM::ImageProcessor* ip = GetImageProcessor(caller);
         if( NULL != ip)
         {
            ip->Process(const_cast<unsigned char*>(buf), width, height, byteDepth);
         }
      }

      if (core_->cbuf_->InsertImage(buf, width, height, byteDepth, pMd))
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

bool CoreCallback::InitializeImageBuffer(unsigned channels, unsigned slices, unsigned int w, unsigned int h, unsigned int pixDepth)
{
   return core_->cbuf_->Initialize(channels, slices, w, h, pixDepth);
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

      MM::ImageProcessor* ip = GetImageProcessor(caller);
      if( NULL != ip)
      {
         ip->Process( const_cast<unsigned char*>(buf), width, height, byteDepth);
      }
      if (core_->cbuf_->InsertMultiChannel(buf, numChannels, width, height, byteDepth, pMd))
         return DEVICE_OK;
      else
         return DEVICE_BUFFER_OVERFLOW;
   }
   catch (CMMError& /*e*/)
   {
      return DEVICE_INCOMPATIBLE_IMAGE;
   }

}

void CoreCallback::SetAcqStatus(const MM::Device* /*caller*/, int /*statusCode*/)
{
   // ???
}

int CoreCallback::OpenFrame(const MM::Device* /*caller*/)
{
   return DEVICE_OK;
}

int CoreCallback::CloseFrame(const MM::Device* /*caller*/)
{
   return DEVICE_OK;
}

int CoreCallback::AcqFinished(const MM::Device* /*caller*/, int /*statusCode*/)
{
   // close the shutter if we are in auto mode
   if (core_->autoShutter_ && core_->shutter_)
   {
      core_->shutter_->SetOpen(false);
      core_->waitForDevice(core_->shutter_);
   }
   return DEVICE_OK;
}

int CoreCallback::PrepareForAcq(const MM::Device* /*caller*/)
{
   // open the shutter if we are in auto mode
   if (core_->autoShutter_ && core_->shutter_)
   {
      core_->shutter_->SetOpen(true);
      core_->waitForDevice(core_->shutter_);
   }
   return DEVICE_OK;
}

/**
 * Handler for the status change event from the device.
 */
int CoreCallback::OnStatusChanged(const MM::Device* /* caller */)
{
   return DEVICE_OK;
}

/**
 * Handler for the property change event from the device.
 */
int CoreCallback::OnPropertiesChanged(const MM::Device* /* caller */)
{
   if (core_->externalCallback_)
      core_->externalCallback_->onPropertiesChanged();

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
      const PropertySetting* ps = new PropertySetting(label, propName, value, core_->isPropertyReadOnly(label, propName));
      core_->stateCache_.addSetting(*ps);
      core_->externalCallback_->onPropertyChanged(label, propName, value);
      
      // find all configs that contain this property and callback to indicate 
      // that the config group changed
      // TODO: assess whether performace is better by maintaining a map tying 
      // property to configurations
  /*
      using namespace std;
      vector<string> configGroups = core_->getAvailableConfigGroups ();
      for (vector<string>::iterator it = configGroups.begin(); 
            it!=configGroups.end(); ++it) 
      {
         vector<string> configs = core_->getAvailableConfigs((*it).c_str());
         bool found = false;
         for (vector<string>::iterator itc = configs.begin();
               itc != configs.end() && !found; itc++) 
         {
            Configuration config = core_->getConfigData((*it).c_str(), (*itc).c_str());
            if (config.isPropertyIncluded(label, propName)) {
               found = true;
               // If we are part of this configuration, notify that it was changed
               // get the new config from cache rather than query the hardware
               string currentConfig = core_->getCurrentConfigFromCache( (*it).c_str() );
               OnConfigGroupChanged((*it).c_str(), currentConfig.c_str());
            }
         }
      }
          

       * this is prone to thread clashes.  Update from Cache if possible
      vector<string> pixelSizeConfigs = core_->getAvailablePixelSizeConfigs();
      bool found = false;
      for (vector<string>::iterator itpsc = pixelSizeConfigs.begin();
            itpsc != pixelSizeConfigs.end() && !found; itpsc++) 
      {
         Configuration pixelSizeConfig = core_->getPixelSizeConfigData( (*itpsc).c_str());
         if (pixelSizeConfig.isPropertyIncluded(label, propName)) {
            found = true;
            double pixSizeUm;
            try {
               pixSizeUm = core_->getPixelSizeUm();
            }
            catch (CMMError ) {
               pixSizeUm = 0.0;
            }
            OnPixelSizeChanged(pixSizeUm);
         }
      }
      */
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
 * Handler for the operation finished event from the device.
 */
int CoreCallback::OnFinished(const MM::Device* /* caller */)
{
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
   return core_->setSerialProperties(portName, answerTimeout, baudRate,
      delayBetweenCharsMs, handshaking, parity, stopBits);

}

/**
 * Sends an array of bytes to the port.
 */
int CoreCallback::WriteToSerial(const MM::Device* caller, const char* portName, const unsigned char* buf, unsigned long length)
{
   MM::Serial* pSerial = 0;
   try
   {
      pSerial = core_->getSpecificDevice<MM::Serial>(portName);
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
   if (pSerial == caller)
      return DEVICE_SELF_REFERENCE;

   return pSerial->Write(buf, length);
}
   
/**
  * Reads bytes form the port, up to the buffer length.
  */
int CoreCallback::ReadFromSerial(const MM::Device* caller, const char* portName, unsigned char* buf, unsigned long bufLength, unsigned long &bytesRead)
{
   MM::Serial* pSerial = 0;
   try
   {
      pSerial = core_->getSpecificDevice<MM::Serial>(portName);
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
   if (pSerial == caller)
      return DEVICE_SELF_REFERENCE;

   return pSerial->Read(buf, bufLength, bytesRead);
}

/**
 * Clears port buffers.
 */
int CoreCallback::PurgeSerial(const MM::Device* caller, const char* portName)
{
   MM::Serial* pSerial = 0;
   try
   {
      pSerial = core_->getSpecificDevice<MM::Serial>(portName);
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
   if (pSerial == caller)
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
   string answer;
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
   if (core_->focusStage_)
   {
      return core_->focusStage_->GetPositionUm(pos);
   }
   pos = 0.0;
   return DEVICE_CORE_FOCUS_STAGE_UNDEF;
}

int CoreCallback::SetFocusPosition(double pos)
{
   if (core_->focusStage_)
   {
      int ret = core_->focusStage_->SetPositionUm(pos);
      if (ret != DEVICE_OK)
         return ret;
      core_->waitForDevice(core_->focusStage_);
      return DEVICE_OK;
   }
   return DEVICE_CORE_FOCUS_STAGE_UNDEF;
}


int CoreCallback::MoveFocus(double velocity)
{
   MMThreadGuard guard(CMMCore::deviceLock_);

   if (core_->focusStage_)
   {
      int ret = core_->focusStage_->Move(velocity);
      if (ret != DEVICE_OK)
         return ret;
      return DEVICE_OK;
   }
   return DEVICE_CORE_FOCUS_STAGE_UNDEF;
}


int CoreCallback::GetXYPosition(double& x, double& y)
{
   if (core_->xyStage_)
   {
      return core_->xyStage_->GetPositionUm(x, y);
   }
   x = 0.0;
   y = 0.0;
   return DEVICE_CORE_FOCUS_STAGE_UNDEF;
}

int CoreCallback::SetXYPosition(double x, double y)
{
   if (core_->xyStage_)
   {
      int ret = core_->xyStage_->SetPositionUm(x, y);
      if (ret != DEVICE_OK)
         return ret;
      core_->waitForDevice(core_->xyStage_);
      return DEVICE_OK;
   }
   return DEVICE_CORE_FOCUS_STAGE_UNDEF;
}

int CoreCallback::MoveXYStage(double vx, double vy)
{
   MMThreadGuard guard(CMMCore::deviceLock_);

   if (core_->xyStage_)
   {
      int ret = core_->xyStage_->Move(vx, vy);
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
      string cfgName = core_->getCurrentConfig(group);
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

      vector<string> cfgs = core_->getAvailableConfigs(core_->getChannelGroup().c_str());
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
      string propVal = core_->getProperty(deviceName, propName);
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
      string propVal(value);
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

void CoreCallback::PostError( const int errorCode, const char* pMessage /* length */ )
{
   MMThreadGuard g(*(core_->pPostedErrorsLock_));
   core_->postedErrors_.push_back(std::make_pair(errorCode, std::string(pMessage)));
}

void CoreCallback::ClearPostedErrors( void)
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

