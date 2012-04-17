///////////////////////////////////////////////////////////////////////////////
// FILE:          TwoPhoton.cpp
// PROJECT:       100X micro-manager extensions
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Video frame grabber interface with multiple PMTs attached
//
// COPYRIGHT:     Nenad Amodaj 2011, 100X Imaging Inc 2009
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
// AUTHOR:        Nenad Amodaj, November 2009
//
                
#include "TwoPhoton.h"
#include <sstream>
#include <math.h>
#include "ModuleInterface.h"
#include "DeviceUtils.h"
#include "tiffio.h"

using namespace std;

const char* g_BitFlowCameraDeviceName = "BitFlowCamera";
const char* g_BitFlowCameraDeviceName2 = "BitFlowCameraX2";

// const char* g_TwoPhotonFilterDeviceName = "ChannelSelector";
const char* g_PropertyChannel = "InputChannel";
const char* g_PropertyMode = "Mode";
const char* g_DemoMode = "Demo";
const char* g_HardwareMode = "Hardware";
const char* g_PropertyColorMode = "ColorMode";
const char* g_Color = "RGB";
const char* g_Gray = "Grayscale";
const char* g_MultiChannel = "Multichannel";
const char* g_On = "On";
const char* g_Off = "Off";
const char* g_OnWarp = "On+Warp";
const char* g_PropertyDeinterlace = "Deinterlace";
const char* g_PropertyIntervalMs = "FrameIntervalMs";
const char* g_PropertyProcessingTimeMs = "ProcessingTimeMs";
const char* g_PropertyCenterOffset = "CenterOffset";
const char* g_PropertyWarpOffset = "WarpOffset";
const char* g_PropertySlow = "SlowStreaming";
const char* g_PropertyEnableChannels = "EnableChannels";

const char* g_PropertyColor_R = "R";
const char* g_PropertyColor_G = "G";
const char* g_PropertyColor_B = "B";

// Mai Tai device
extern const char* g_MaiTaiDeviceName;
extern const char* g_DemoLaserDeviceName;
extern const char* g_VShutterDeviceName;



// windows DLL entry code
#ifdef WIN32
BOOL APIENTRY DllMain( HANDLE /*hModule*/, 
                      DWORD  ul_reason_for_call, 
                      LPVOID /*lpReserved*/
                      )
{
   switch (ul_reason_for_call)
   {
   case DLL_PROCESS_ATTACH:
   case DLL_THREAD_ATTACH:
   case DLL_THREAD_DETACH:
   case DLL_PROCESS_DETACH:
      break;
   }
   return TRUE;
}
#endif

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_BitFlowCameraDeviceName, "BitFlow frame grabber");
   AddAvailableDeviceName(g_BitFlowCameraDeviceName2, "BitFlow dual frame grabber");
   AddAvailableDeviceName(g_MaiTaiDeviceName, "Mai Tai laser");
   AddAvailableDeviceName(g_DemoLaserDeviceName, "Demo laser");
   AddAvailableDeviceName(g_VShutterDeviceName, "Virtual dual shutter");
   // AddAvailableDeviceName(g_TwoPhotonFilterDeviceName, "2-photon filter wheel");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   // decide which device class to create based on the deviceName parameter
   if (strcmp(deviceName, g_BitFlowCameraDeviceName) == 0)
   {
      // create camera
      return new BitFlowCamera(false);
   }
   if (strcmp(deviceName, g_BitFlowCameraDeviceName2) == 0)
   {
      // create camera
      return new BitFlowCamera(true);
   }
   else if (strcmp(deviceName, g_MaiTaiDeviceName) == 0)
   {
      // create claser
      return new MaiTai();
   }
   else if (strcmp(deviceName, g_DemoLaserDeviceName) == 0)
   {
      // create demo laser
      return new DemoLaser();
   }
   else if (strcmp(deviceName, g_VShutterDeviceName) == 0)
   {
      // create virtual dual shutter
      return new VirtualShutter();
   }

   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// BitFlowCamera implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~

/**
 * Constructor.
 */
BitFlowCamera::BitFlowCamera(bool dual) :
   CCameraBase<BitFlowCamera> (),
   demoFileName_("demo.tif"),
   initialized_(false),
   inputChannel_(0),
   expNumFrames_(1),
   liveThd_(0),
   demoMode_(true),
   binSize_(1),
   colorBuf_(0),
   scratchBuf_(0),
   lineBuf_(0),
   frameBuf_(0),
   demoImageBuf_(0),
   intervalMs_(0),
   processingTimeMs_(0),
   deinterlace_(false),
   cosineWarp_(false),
   frameOffset_(0),
   warpOffset_(0),
   slowStream_(false),
   RChannel_(0),
   GChannel_(1),
   BChannel_(2),
   colorMode_(Grayscale),
   bfDev_(dual)
{
	if (dual)
		numChannels_ = 7;
	else
		numChannels_ = 4;

   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_NUM_CHANNELS, "Number of available channels too high for this device adapter."
								  "Your system probably has two Bitflow cards installed."
                                  "Use BitFlowCameraX2 adapter instead.");

   img_.resize(numChannels_);
   for (unsigned i=0; i<img_.size(); i++)
   {
      img_[i].Resize(demoRawWidth_, demoRawHeight_, byteDepth_);
   }
   colorBuf_ = new unsigned char[demoRawWidth_ * demoRawHeight_ * byteDepth_ * numChannels_];
   frameBuf_ = new unsigned char[demoRawWidth_ * demoRawHeight_ * byteDepth_ * numChannels_];
   scratchBuf_ = new unsigned char[demoRawWidth_ * demoRawHeight_ * byteDepth_];
   lineBuf_ = new unsigned char[demoRawWidth_/2 * byteDepth_];
}

BitFlowCamera::~BitFlowCamera()
{
   delete[] colorBuf_;
   delete[] frameBuf_;
   delete[] demoImageBuf_;
   delete[] scratchBuf_;
   delete[] lineBuf_;
   delete liveThd_;
}

/**
 * Obtains device name.
 */
void BitFlowCamera::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_BitFlowCameraDeviceName);
}

/**
 * Intializes the hardware.
 */
int BitFlowCamera::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   // read in default demo image
   //unsigned width(0), height(0);
   //if (!Read8bitTIFF(demoFileName_.c_str(), &demoImageBuf_, width, height))
   //{
   //   // silently move on
   //}
   //else if (width != demoRawWidth_ || height != demoRawHeight_)
   //{
   //   // image dimensions are not as expected, so ignore and clean up memory
   //   delete[] demoImageBuf_;
   //   demoImageBuf_ = 0;
   //}

   liveThd_ = new LiveThread(this);

   // set property list
   // -----------------

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_BitFlowCameraDeviceName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "BitFlow camera adapter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // binning
   CPropertyAction *pAct = new CPropertyAction (this, &BitFlowCamera::OnBinning);
   ret = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct);
   assert(ret == DEVICE_OK);

   vector<string> binValues;
   binValues.push_back("1");
   ret = SetAllowedValues(MM::g_Keyword_Binning, binValues);
   if (ret != DEVICE_OK)
      return ret;

   // input selection
   pAct = new CPropertyAction (this, &BitFlowCamera::OnInputChannel);
   ret = CreateProperty(g_PropertyChannel, "0", MM::Integer, false, pAct);
   assert(ret == DEVICE_OK);

   vector<string> channelValues;
   for (unsigned i=0; i<numChannels_; i++)
   {
      ostringstream os;
      os << i;
      channelValues.push_back(os.str());
   }
   ret = SetAllowedValues(g_PropertyChannel, channelValues);
   if (ret != DEVICE_OK)
      return ret;

   // mode
   pAct = new CPropertyAction (this, &BitFlowCamera::OnMode);
   ret = CreateProperty(g_PropertyMode, g_DemoMode, MM::String, false, pAct);
   assert(ret == DEVICE_OK);

   vector<string> modeValues;
   modeValues.push_back(g_DemoMode);
   modeValues.push_back(g_HardwareMode);
   ret = SetAllowedValues(g_PropertyMode, modeValues);
   if (ret != DEVICE_OK)
      return ret;

   // de-interlacing
   pAct = new CPropertyAction (this, &BitFlowCamera::OnDeinterlace);
   ret = CreateProperty(g_PropertyDeinterlace, g_Off, MM::String, false, pAct);
   assert(ret == DEVICE_OK);

   vector<string> diValues;
   diValues.push_back(g_Off);
   diValues.push_back(g_On);
   diValues.push_back(g_OnWarp);
   ret = SetAllowedValues(g_PropertyDeinterlace, diValues);
   if (ret != DEVICE_OK)
      return ret;

   // color mode
   pAct = new CPropertyAction (this, &BitFlowCamera::OnColorMode);
   ret = CreateProperty(g_PropertyColorMode, g_Gray, MM::String, false, pAct);
   assert(ret == DEVICE_OK);

   vector<string> colorModeValues;
   colorModeValues.push_back(g_Color);
   colorModeValues.push_back(g_Gray);
   colorModeValues.push_back(g_MultiChannel);
   ret = SetAllowedValues(g_PropertyColorMode, colorModeValues);
   if (ret != DEVICE_OK)
      return ret;

   // channel color selection

   // R color
   pAct = new CPropertyAction (this, &BitFlowCamera::OnRChannel);
   ret = CreateProperty(g_PropertyColor_R, "0", MM::String, false, pAct);
   assert(ret == DEVICE_OK);
   ret = SetAllowedValues(g_PropertyColor_R, channelValues);
   if (ret != DEVICE_OK)
      return ret;

   // G color
   pAct = new CPropertyAction (this, &BitFlowCamera::OnGChannel);
   ret = CreateProperty(g_PropertyColor_G, "1", MM::String, false, pAct);
   assert(ret == DEVICE_OK);
   ret = SetAllowedValues(g_PropertyColor_G, channelValues);
   if (ret != DEVICE_OK)
      return ret;

   // B color
   pAct = new CPropertyAction (this, &BitFlowCamera::OnBChannel);
   ret = CreateProperty(g_PropertyColor_B, "2", MM::String, false, pAct);
   assert(ret == DEVICE_OK);
   ret = SetAllowedValues(g_PropertyColor_B, channelValues);
   if (ret != DEVICE_OK)
      return ret;

   pAct = new CPropertyAction (this, &BitFlowCamera::OnFrameInterval);
   ret = CreateProperty(g_PropertyIntervalMs, "0", MM::Integer, true, pAct);
   if (ret != DEVICE_OK)
      return ret;

   pAct = new CPropertyAction (this, &BitFlowCamera::OnProcessingTime);
   ret = CreateProperty(g_PropertyProcessingTimeMs, "0", MM::Integer, true, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // frame offset
   pAct = new CPropertyAction (this, &BitFlowCamera::OnCenterOffset);
   ret = CreateProperty(g_PropertyCenterOffset, "0", MM::Integer, false, pAct);
   assert(ret == DEVICE_OK);

   SetPropertyLimits(g_PropertyCenterOffset, -BFCamera::MAX_FRAME_OFFSET, BFCamera::MAX_FRAME_OFFSET);

   // frame offset
   pAct = new CPropertyAction (this, &BitFlowCamera::OnWarpOffset);
   ret = CreateProperty(g_PropertyWarpOffset, "0", MM::Integer, false, pAct);
   assert(ret == DEVICE_OK);

   SetPropertyLimits(g_PropertyWarpOffset, -10.0, 10.0);

   // slow stream
   ret = CreateProperty(g_PropertySlow, g_Off, MM::String, false);
   assert(ret == DEVICE_OK);

   // enable specific channels
   pAct = new CPropertyAction (this, &BitFlowCamera::OnEnableChannels);
   ret = CreateProperty(g_PropertyEnableChannels, "11111111", MM::String, false, pAct);
   assert(ret == DEVICE_OK);

   vector<string> slowValues;
   slowValues.push_back(g_Off);
   slowValues.push_back(g_On);
   ret = SetAllowedValues(g_PropertySlow, slowValues);
   if (ret != DEVICE_OK)
      return ret;


   // synchronize all properties
   // --------------------------
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   // setup the buffer
   // ----------------
   ret = ResizeImageBuffer();

   // initialize roi
   roi_.x = 0;
   roi_.y = 0;
   roi_.xSize = GetImageWidth();
   roi_.ySize = GetImageHeight();
  
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

/**
 * Shuts down (unloads) the device.
 */
int BitFlowCamera::Shutdown()
{
   initialized_ = false;
   delete liveThd_;
   liveThd_ = 0;
   delete[] demoImageBuf_;
   demoImageBuf_ = 0;

   return DEVICE_OK;
}

/**
 * Grabs images from all channels. 
 * If the exposure is 0 frames this function just returns without updating images
 */
//int BitFlowCamera::SnapImage()
//{
//   if (expNumFrames_ <= 0)
//      return DEVICE_OK;
//
//   MM::MMTime start = GetCoreCallback()->GetCurrentMMTime();
//   MM::MMTime processingTime(0, 0);
//
//   // clear all accumulators (set to zero)
//   for (unsigned j=0; j<img_.size(); j++)
//      img_[j].ResetPixels();
//
//   unsigned buflen(0);
//
//   if (demoMode_)
//   {
//      buflen = demoRawWidth_*demoRawHeight_*byteDepth_;
//      for (int i=0; i<expNumFrames_; i++)
//      {
//         for (unsigned j=0; j<img_.size(); j++)
//            GenerateSyntheticImage(frameBuf_ + j * buflen, demoRawWidth_, demoRawHeight_, byteDepth_, 33.3);
//         
//         MM::MMTime startProcessingTime = GetCoreCallback()->GetCurrentMMTime();
//         if (deinterlace_)
//            DeinterlaceBuffer(frameBuf_, buflen, demoRawWidth_, false, cosineWarp_);
//         else
//            for (unsigned j=0; j<img_.size(); j++)
//               img_[j].AddPixels(frameBuf_ + j*buflen, demoRawWidth_, roi_.x, roi_.y); // add image
//         
//         processingTime = processingTime + (GetCoreCallback()->GetCurrentMMTime() - startProcessingTime);
//      }
//
//      MM::MMTime startProcessingTime = GetCoreCallback()->GetCurrentMMTime();
//
//      for (unsigned i=0; i<img_.size(); i++)
//         img_[i].Scale(1.0/expNumFrames_); // average
//
//      processingTime = processingTime + (GetCoreCallback()->GetCurrentMMTime() - startProcessingTime);
//   }
//   else
//   {
//      if (!bfDev_.isInitialized())
//         return ERR_HARDWARE_NOT_INITIALIZED;
//
//      for (int k=0; k<expNumFrames_; k++)
//      {
//         const unsigned errBufLen = 1024;
//         char errText[errBufLen];
//         errText[0] = 0;
//         unsigned retCode;
//         unsigned char* buf = const_cast<unsigned char*>(bfDev_.GetImage(retCode, errText, errBufLen, this));
//         
//         if (buf == 0)
//         {
//            ostringstream txt;
//            txt << "Bitflow board failed with code:" << retCode << ", " << errText;
//            GetCoreCallback()->LogMessage(this, txt.str().c_str(), false);
//            return ERR_SNAP_FAILED;
//         }
//         
//         unsigned bufLen = bfDev_.GetBufferSize();
//         
//         MM::MMTime startProcessingTime = GetCoreCallback()->GetCurrentMMTime();
//         if (deinterlace_)
//         {
//            // de-interlace, re-size and correct image
//            DeinterlaceBuffer(buf, bufLen, bfDev_.Width(), false, cosineWarp_);
//         }
//         else
//         {
//            // add images as they come from the frame grabber
//            for (unsigned i=0; i<img_.size(); i++)
//               img_[i].AddPixels(buf + i*bufLen + frameOffset_ + BFCamera::MAX_FRAME_OFFSET, bfDev_.Width(), roi_.x, roi_.y); // add image
//         }
//         processingTime = processingTime + (GetCoreCallback()->GetCurrentMMTime() - startProcessingTime);
//      }
//
//      MM::MMTime startProcessingTime = GetCoreCallback()->GetCurrentMMTime();
//      
//      for (unsigned i=0; i<img_.size(); i++)
//         img_[i].Scale(1.0/expNumFrames_); // average
//
//      processingTime = processingTime + (GetCoreCallback()->GetCurrentMMTime() - startProcessingTime);
//   }
//
//   MM::MMTime end = GetCoreCallback()->GetCurrentMMTime();
//   intervalMs_ = (end - start).getMsec() / expNumFrames_;
//   processingTimeMs_ = processingTime.getMsec() / expNumFrames_;
//
//   return DEVICE_OK;
//}

/**
 * Grabs images from all channels. 
 * Modified to perfrom continouous integration
 */
int BitFlowCamera::SnapImage()
{
   if (expNumFrames_ <= 0)
      return DEVICE_OK;

   MM::MMTime start = GetCoreCallback()->GetCurrentMMTime();
   MM::MMTime processingTime(0, 0);

   // clear all accumulators (set to zero)
   for (unsigned j=0; j<img_.size(); j++)
      img_[j].ResetPixels();

   unsigned buflen(0);

   if (demoMode_)
   {
      buflen = demoRawWidth_*demoRawHeight_*byteDepth_;
      for (int i=0; i<expNumFrames_; i++)
      {
         for (unsigned j=0; j<img_.size(); j++)
            GenerateSyntheticImage(frameBuf_ + j * buflen, demoRawWidth_, demoRawHeight_, byteDepth_, 33.3);
         
         MM::MMTime startProcessingTime = GetCoreCallback()->GetCurrentMMTime();
         if (deinterlace_)
            DeinterlaceBuffer(frameBuf_, buflen, demoRawWidth_, cosineWarp_);
         else
            for (unsigned j=0; j<img_.size(); j++)
               img_[j].AddPixels(frameBuf_ + j*buflen, demoRawWidth_, roi_.x, roi_.y); // add image
         
         processingTime = processingTime + (GetCoreCallback()->GetCurrentMMTime() - startProcessingTime);
      }

      MM::MMTime startProcessingTime = GetCoreCallback()->GetCurrentMMTime();

      for (unsigned i=0; i<img_.size(); i++)
         img_[i].Scale(1.0/expNumFrames_); // average

      processingTime = processingTime + (GetCoreCallback()->GetCurrentMMTime() - startProcessingTime);
   }
   else
   {
      if (!bfDev_.isInitialized())
         return ERR_HARDWARE_NOT_INITIALIZED;

      bfDev_.StartSequence(); // start streaming mode

      for (int k=0; k<expNumFrames_; k++)
      {
         unsigned char* buf = const_cast<unsigned char*>(bfDev_.GetImageCont());
         
         if (buf == 0)
         {
            ostringstream txt;
            txt << "Bitflow board failed in streaming mode";
            GetCoreCallback()->LogMessage(this, txt.str().c_str(), false);
            bfDev_.StopSequence();
            return ERR_SNAP_FAILED;
         }
         
         unsigned bufLen = bfDev_.GetBufferSize();
         
         MM::MMTime startProcessingTime = GetCoreCallback()->GetCurrentMMTime();
         if (deinterlace_)
         {
            // de-interlace, re-size and correct image
            DeinterlaceBuffer(buf, bufLen, bfDev_.Width(), cosineWarp_);
         }
         else
         {
            // add images as they come from the frame grabber
            for (unsigned i=0; i<img_.size(); i++)
               img_[i].AddPixels(buf + i*bufLen + frameOffset_ + BFCamera::MAX_FRAME_OFFSET, bfDev_.Width(), roi_.x, roi_.y); // add image
         }
         processingTime = processingTime + (GetCoreCallback()->GetCurrentMMTime() - startProcessingTime);
      }
      bfDev_.StopSequence(); //stop streaming mode
      MM::MMTime startProcessingTime = GetCoreCallback()->GetCurrentMMTime();
      
      for (unsigned i=0; i<img_.size(); i++)
         img_[i].Scale(1.0/expNumFrames_); // average

      processingTime = processingTime + (GetCoreCallback()->GetCurrentMMTime() - startProcessingTime);
   }

   MM::MMTime end = GetCoreCallback()->GetCurrentMMTime();
   intervalMs_ = (end - start).getMsec() / expNumFrames_;
   processingTimeMs_ = processingTime.getMsec() / expNumFrames_;

   return DEVICE_OK;
}

/**
 * Continuous acqusition frame grab.
 * Intended for live video, performs frame integration
 */
int BitFlowCamera::SnapImageCont()
{
   if (expNumFrames_ <= 0)
      return DEVICE_OK;

   // clear all accumulators (set to zero)
   for (unsigned j=0; j<img_.size(); j++)
      img_[j].ResetPixels();

   if (demoMode_)
   {
      unsigned buflen = demoRawWidth_*demoRawHeight_*byteDepth_;
      for (unsigned j=0; j<img_.size(); j++)
      {
         GenerateSyntheticImage(frameBuf_ + j * buflen, demoRawWidth_, demoRawHeight_, byteDepth_, 33.3);
      }
      if (deinterlace_)
      {
         DeinterlaceBuffer(frameBuf_, buflen, demoRawWidth_, cosineWarp_);
      }
      else
      {
         for (unsigned i=0; i<img_.size(); i++)
            img_[i].AddPixels(frameBuf_ + i*buflen, demoRawWidth_, roi_.x, roi_.y); // add image
      }
   }
   else
   {
      if (!bfDev_.isInitialized())
         return ERR_HARDWARE_NOT_INITIALIZED;

      for (int k=0; k<expNumFrames_; k++)
      {
         unsigned char* buf = const_cast<unsigned char*>(bfDev_.GetImageCont());

         if (buf == 0)
            return ERR_SNAP_FAILED;
         
         unsigned bufLen = bfDev_.GetBufferSize();

         if (deinterlace_)
         {
            // de-interlace, re-size and correct image
            DeinterlaceBuffer(buf, bufLen, bfDev_.Width(), cosineWarp_);
         }
         else
         {
            for (unsigned i=0; i<img_.size(); i++)
               img_[i].AddPixels(buf + i*bufLen+frameOffset_ + BFCamera::MAX_FRAME_OFFSET, bfDev_.Width(), roi_.x, roi_.y);
         }
      }
      
      for (unsigned i=0; i<img_.size(); i++)
         img_[i].Scale(1.0/expNumFrames_); // average
   }

   MM::MMTime end = GetCoreCallback()->GetCurrentMMTime();
   intervalMs_ = (end - startTime_).getMsec();
   startTime_ = end;

   return DEVICE_OK;
}

/**
 * Returns pixel data.
 */
const unsigned char* BitFlowCamera::GetImageBuffer()
{  
   if (colorMode_ == Color)
      return reinterpret_cast<const unsigned char*>(GetImageBufferAsRGB32());
   else
      return img_[inputChannel_].GetPixels();
}

const unsigned char* BitFlowCamera::GetImageBuffer(unsigned chNo)
{
   if (colorMode_ == Color)
   {
      return reinterpret_cast<const unsigned char*>(GetImageBufferAsRGB32());
   }
   else if (colorMode_ == MultiChannel)
   {
	  vector<int> ech = GetEnabledChannels();
      if (chNo >= ech.size())
         return 0;
      else
         return img_[ech[chNo]].GetPixels();
   }
   else
   {
      return img_[inputChannel_].GetPixels();
   }
}

int BitFlowCamera::GetChannelName(unsigned channel, char* name)
{
   if (channel >= img_.size())
      return DEVICE_NONEXISTENT_CHANNEL;

   ostringstream txt;
   if (colorMode_ == MultiChannel)
   {
      vector<int> chm = GetEnabledChannels();
	  if (channel >= chm.size())
		  return DEVICE_NONEXISTENT_CHANNEL;
	  txt << "Input-" << chm[channel];
   }
   else
   {
      txt << "Input-" << channel;
   }
   CDeviceUtils::CopyLimitedString(name, txt.str().c_str());
   return DEVICE_OK;
}


/**
* Returns image buffer X-size in pixels.
*/
unsigned BitFlowCamera::GetImageWidth() const
{
   return img_[inputChannel_].Width();
}

/**
* Returns image buffer Y-size in pixels.
*/
unsigned BitFlowCamera::GetImageHeight() const
{
   return img_[inputChannel_].Height();
}

/**
* Returns image buffer pixel depth in bytes.
*/
unsigned BitFlowCamera::GetImageBytesPerPixel() const
{
   if (colorMode_ == Color)
      return 4;
   else
      return img_[inputChannel_].Depth();
} 

/**
 * Returns the bit depth (dynamic range) of the pixel.
 */
unsigned BitFlowCamera::GetBitDepth() const
{
   return img_[inputChannel_].Depth() * 8;
}

/**
 * Returns the size in bytes of the image buffer.
 */
long BitFlowCamera::GetImageBufferSize() const
{
   if (colorMode_ == Color)
      return img_[inputChannel_].Width() * img_[inputChannel_].Height() * 4;
   else
      return img_[inputChannel_].Width() * img_[inputChannel_].Height() * img_[inputChannel_].Depth();
}

/**
 * Sets the camera Region Of Interest.
 * @param x - top-left corner coordinate
 * @param y - top-left corner coordinate
 * @param xSize - width
 * @param ySize - height
 */
int BitFlowCamera::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
   if (xSize == 0 && ySize == 0)
   {
      // effectively clear ROI
      ResizeImageBuffer();
      roi_.Set(0, 0, GetImageWidth(), GetImageHeight());
   }
   else
   {
      // apply ROI
      roi_.x = x;
      roi_.y = y;
      roi_.xSize = xSize;
      roi_.ySize = ySize;

      for (vector<ImgAccumulator>::iterator i = img_.begin(); i != img_.end(); i++)
         i->Resize(roi_.xSize, roi_.ySize);
   }
   return DEVICE_OK;
}

/**
* Returns the actual dimensions of the current ROI.
*/
int BitFlowCamera::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
   x = roi_.x;
   y = roi_.y;
   xSize = roi_.xSize;
   ySize = roi_.ySize;

   return DEVICE_OK;
}

/**
* Resets the Region of Interest to full frame.
*/
int BitFlowCamera::ClearROI()
{
   ResizeImageBuffer();
   roi_.x = 0;
   roi_.y = 0;
   roi_.xSize = GetImageWidth();
   roi_.ySize = GetImageHeight();
      
   return DEVICE_OK;
}

/**
* Returns the current exposure setting in milliseconds.
* Required by the MM::Camera API.
*/
double BitFlowCamera::GetExposure() const
{
   return expNumFrames_;
}

/**
 * Sets exposure in milliseconds.
 * Required by the MM::Camera API.
 */
void BitFlowCamera::SetExposure(double exp)
{
   // truncate floating point value to integer
   expNumFrames_ = (int)exp;
   if (expNumFrames_ < 0)
      expNumFrames_ = 0;
   if (expNumFrames_ > maxFrames_)
      expNumFrames_ = maxFrames_;

   for (unsigned i=0; i<img_.size(); i++)
      img_[i].Resize(expNumFrames_);

}

/**
 * Returns the current binning factor.
 */
int BitFlowCamera::GetBinning() const
{
   return binSize_;
}

int BitFlowCamera::SetBinning(int binFactor)
{
   if (binFactor < 0 && binFactor > 1)
   {
      return ERR_UNKNOWN_BINNING_MODE;
   }
   binSize_ = binFactor;
   return DEVICE_OK;
}

int BitFlowCamera::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{
   if (IsCapturing())
      return DEVICE_CAMERA_BUSY_ACQUIRING;

   // this will open the shutter
   GetCoreCallback()->PrepareForAcq(this);

   if (!demoMode_)
   {
      // start the frame grabber
      int ret = bfDev_.StartContinuousAcq();
      if (ret != DEVICE_OK)
         return ret;
   }

   startTime_ = GetCurrentMMTime();

   liveThd_->EnableStreaming(true);
   liveThd_->SetNumImages(numImages);
   startTime_ = GetCoreCallback()->GetCurrentMMTime();
   liveThd_->activate();

   return DEVICE_OK;
}

int BitFlowCamera::StartSequenceAcquisition(double interval_ms)
{
   if (IsCapturing())
      return DEVICE_CAMERA_BUSY_ACQUIRING;

   liveThd_->EnableStreaming(false);
   liveThd_->SetNumImages(-1);

   startTime_ = GetCoreCallback()->GetCurrentMMTime();
   liveThd_->activate();

   return DEVICE_OK;
}

bool BitFlowCamera::IsCapturing()
{
   return liveThd_->IsRunning();
}


int BitFlowCamera::StopSequenceAcquisition()
{
   bfDev_.StopContinuousAcq();
   liveThd_->Abort();
   return DEVICE_OK;
}

int BitFlowCamera::PrepareSequenceAcqusition()
{
   // nothing to prepare
   return DEVICE_OK;
}

const unsigned int* BitFlowCamera::GetImageBufferAsRGB32()
{
   assert(byteDepth_ == 1);

   unsigned size = GetImageWidth() * GetImageHeight();
   unsigned char* colorBufPtr = colorBuf_;
   
   if (RChannel_ >= (int)img_.size())
      return 0;

   if (GChannel_ >= (int)img_.size())
      return 0;

   if (BChannel_ >= (int)img_.size())
      return 0;

   for (unsigned i=0; i<size; i++)
   {
      *(colorBufPtr++) = *(img_[BChannel_].GetPixels() + i);
      *(colorBufPtr++) = *(img_[GChannel_].GetPixels() + i);
      *(colorBufPtr++) = *(img_[RChannel_].GetPixels() + i);
      *(colorBufPtr++) = 0; // alpha
   }

   return reinterpret_cast<unsigned int*> (colorBuf_);
}

const unsigned char* BitFlowCamera::GetImageBufferAllChannels()
{
   assert(byteDepth_ == 1);

   unsigned size = GetImageWidth() * GetImageHeight();
   unsigned char* blobBufPtr = colorBuf_;

   for (unsigned i=0; i<img_.size(); i++)
   {
      memcpy(blobBufPtr + i*size, img_[i].GetPixels(), size);
   }

   return colorBuf_;
}

unsigned BitFlowCamera::GetNumberOfComponents() const
{
   if (colorMode_ == Color)
      return 4;
   else
      return 1;
}

unsigned BitFlowCamera::GetNumberOfChannels() const
{
   if (colorMode_ == MultiChannel)
   {
	   return GetEnabledChannels().size();
      //return (unsigned)img_.size();
   }
   else
      return 1;
}


int BitFlowCamera::GetComponentName(unsigned channel, char* name)
{
   if (channel < 0 || channel >= img_.size())
      return DEVICE_NONEXISTENT_CHANNEL;

   ostringstream txt;
   txt << "Input-" << channel;
   CDeviceUtils::CopyLimitedString(name, txt.str().c_str());
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// BitFlowCamera Action handlers
///////////////////////////////////////////////////////////////////////////////
/**
 * Handles "Binning" property.
 */
int BitFlowCamera::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long binFactor;
      pProp->Get(binFactor);

      int ret = SetBinning(binFactor);
      if (ret != DEVICE_OK)
         return ret;

      return ResizeImageBuffer();
   }

   return DEVICE_OK;
}

int BitFlowCamera::OnInputChannel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long input;
      pProp->Get(input);

      if (input < (long)numChannels_ && input >= 0)
      {
         inputChannel_ = input;
      }
      else
      {
         inputChannel_ = 0;
         return ERR_UNKNOWN_INPUT_CHANNEL;
      }
   }
   return DEVICE_OK;
}

int BitFlowCamera::OnMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      if (IsCapturing())
         return DEVICE_CAMERA_BUSY_ACQUIRING;

      string modeName;
      pProp->Get(modeName);
      bool previousMode = demoMode_;

      if (modeName.compare(g_DemoMode) == 0)
         demoMode_ = true;
      else
         demoMode_ = false;
      
      if (previousMode != demoMode_)
      {
         if (demoMode_ == false)
         {
            // new mode is hardware
            if (!bfDev_.isInitialized())
            {
               int ret = bfDev_.Initialize();
               if (ret != DEVICE_OK)
               {
                  demoMode_ = true; // return to demo
                  return ret;
               }

               // at this point frame grabber is successfully initialized so
               // we can re-assign image buffers
			   if (bfDev_.GetNumberOfBuffers() > numChannels_)
			   {
				   bfDev_.Shutdown();
				   demoMode_ = true;
				   return ERR_NUM_CHANNELS;
			   }

               img_.clear();
               img_.resize(bfDev_.GetNumberOfBuffers());
               return ResizeImageBuffer();
            }
         }
         else
         {
            // new mode is demo
            bfDev_.Shutdown();
         }
      }
   }
   else if (eAct == MM::BeforeGet)
   {
      if (demoMode_)
         pProp->Set(g_DemoMode);
      else
         pProp->Set(g_HardwareMode);
   }

   return DEVICE_OK;
}

int BitFlowCamera::OnDeinterlace(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      if (IsCapturing())
         return DEVICE_CAMERA_BUSY_ACQUIRING;
      
      string val;
      pProp->Get(val);
      if (val.compare(g_On) == 0)
      {
         deinterlace_ = true;
         cosineWarp_ = false;
      }
      else if (val.compare(g_OnWarp) == 0)
      {
         deinterlace_ = true;
         cosineWarp_ = true;
      }
      else
      {
         deinterlace_ = false;
         cosineWarp_ = false;
      }

      ResizeImageBuffer();
   }
   else if (eAct == MM::BeforeGet)
   {
      if (deinterlace_)
         if (cosineWarp_)
            pProp->Set(g_OnWarp);
         else
            pProp->Set(g_On);
      else
         pProp->Set(g_Off);
   }

   return DEVICE_OK;

}

int BitFlowCamera::OnFrameInterval(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)(intervalMs_ + 0.5));
   }
   return DEVICE_OK;
}

int BitFlowCamera::OnProcessingTime(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)(processingTimeMs_ + 0.5));
   }
   return DEVICE_OK;
}

/**
 * Center offset property has effect only with snake mode on.
 */
int BitFlowCamera::OnCenterOffset(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long co;
      pProp->Get(co);
      frameOffset_ = (int)co;
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)frameOffset_);
   }

   return DEVICE_OK;
}

/**
 * Warp center offset.
 */
int BitFlowCamera::OnWarpOffset(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long wo;
      pProp->Get(wo);
      warpOffset_ = (int)wo;
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)warpOffset_);
   }

   return DEVICE_OK;
}

int BitFlowCamera::OnColorMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      if (IsCapturing())
      {
         if (colorMode_ == Grayscale)
            pProp->Set(g_Gray); // revert to current
         else if (colorMode_ == Color)
            pProp->Set(g_Color);
         else
            pProp->Set(g_MultiChannel);

         return DEVICE_CAMERA_BUSY_ACQUIRING;
      }
      string mode;
      pProp->Get(mode);
      if (mode.compare(g_Gray) == 0)
         colorMode_ = Grayscale;
      else if (mode.compare(g_Color) == 0)
         colorMode_ = Color;
      else
         colorMode_ = MultiChannel;
   }
   else if (eAct == MM::BeforeGet)
   {
      if (colorMode_ == Grayscale)
         pProp->Set(g_Gray);
      else if (colorMode_ == Color)
         pProp->Set(g_Color);
      else
         pProp->Set(g_MultiChannel);
   }

   return DEVICE_OK;
}

int BitFlowCamera::OnRChannel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long chan;
      pProp->Get(chan);
      RChannel_ = (int)chan;
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)RChannel_);
   }

   return DEVICE_OK;
}

int BitFlowCamera::OnGChannel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long chan;
      pProp->Get(chan);
      GChannel_ = (int)chan;
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)GChannel_);
   }

   return DEVICE_OK;
}

int BitFlowCamera::OnBChannel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      long chan;
      pProp->Get(chan);
      BChannel_ = (int)chan;
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)BChannel_);
   }

   return DEVICE_OK;
}

int BitFlowCamera::OnEnableChannels(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      string chanlist;
      pProp->Get(chanlist);
	  for (unsigned i=0; i<chanlist.length(); i++)
	  {
		  if (i < img_.size())
		  {
			  if (*(chanlist.c_str() + i) == '0')
				img_[i].SetEnable(false);
			  else
				img_[i].SetEnable(true);	
		  }
	  }
   }
   else if (eAct == MM::BeforeGet)
   {
       string chanlist;
	   for (unsigned i=0; i< img_.size(); i++)
		   if (img_[i].IsEnabled())
			  chanlist += "1";
		   else
		      chanlist += "0";
	   pProp->Set(chanlist.c_str());
   }

   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Private methods
///////////////////////////////////////////////////////////////////////////////

/**
* Sync internal image buffer size to the chosen property values.
*/
int BitFlowCamera::ResizeImageBuffer()
{
   if (demoMode_)
   {
      if (deinterlace_)
      {
         for (unsigned i=0; i<img_.size(); i++)
            img_[i].Resize(imageWidth_/binSize_, (demoRawHeight_*2)/binSize_, byteDepth_);
         if (img_.size() > 0)
         {
            GetCosineWarpLUT(altPixelLookup_, demoRawWidth_/2, demoRawWidth_); 
         }
      }
      else
      {
         for (unsigned i=0; i<img_.size(); i++)
            img_[i].Resize(demoRawWidth_/binSize_, demoRawHeight_/binSize_, byteDepth_);
      }
   }
   else
   {
      if (deinterlace_)
      {
         for (unsigned i=0; i<img_.size(); i++)
            img_[i].Resize(imageWidth_/binSize_, (bfDev_.Height()*2)/binSize_, bfDev_.Depth());
         if (img_.size() > 0)
         {
            GetCosineWarpLUT(altPixelLookup_, bfDev_.Width()/2, bfDev_.Width());
         }
     }
      else
      {
         for (unsigned i=0; i<img_.size(); i++)
            img_[i].Resize(bfDev_.Width()/binSize_, bfDev_.Height()/binSize_, bfDev_.Depth());
      }
   }


   delete[] colorBuf_;
   colorBuf_ = new unsigned char[GetImageWidth() * GetImageHeight() * byteDepth_ * img_.size()];

   delete[] scratchBuf_;
   scratchBuf_ = new unsigned char[demoMode_ ? demoRawWidth_ * demoRawHeight_ : bfDev_.Width() * bfDev_.Height()];

   delete[] lineBuf_;
   lineBuf_ = new unsigned char[demoMode_ ? demoRawWidth_/2 : bfDev_.Width()/2];

   return DEVICE_OK;
}

/**
* Generate a spatial sine wave.
*/
void BitFlowCamera::GenerateSyntheticImage(void* buffer, unsigned width, unsigned height, unsigned depth, double exp)
{
	if (height == 0 || width == 0 || depth == 0)
      return;

   if (demoImageBuf_ != 0 && width == demoRawWidth_ && height == demoRawHeight_ && depth == 1)
   {
      // use demo image if it is loaded and the dimensions are right
      memcpy(buffer, demoImageBuf_, demoRawHeight_ * demoRawWidth_ * depth);
      return;
   }
   else
   {
      // generate a phase shifting sine wave

      const double cPi = 3.14;
      double period = width/30.3;
      static double dPhase = 0.0;
      double dLinePhase = 0.0;
      const double dAmp = exp;
      const double cLinePhaseInc = 2.0 * cPi / 4.0 / height;


      long maxValue = 1 << (depth * 8);

      unsigned j, k;
      if (depth == 1)
      {
         double pedestal = 127 * exp / 100.0 * GetBinning() * GetBinning();
         unsigned char* pBuf = static_cast<unsigned char*>(buffer);
         for (j=0; j<height*2; j++)
         {
               for (k=0; k<width/2; k++)
               {
                  long lIndex = width/2 * j + k;
                  pBuf[lIndex] = (unsigned char) min(255.0, (pedestal + dAmp * sin(dPhase + dLinePhase + (2.0 * cPi * k) / period)));
               }
         }
         // mirror every second line
         for (j=1; j<height*2; j+=2)
         {
               for (k=0; k<width/4; k++)
               {
                  long lIndex = (width/2) * j + k;
                  long rIndex = (width/2) * (j+1) - 1 - k;
                  unsigned char tempr = pBuf[rIndex];
                  unsigned char templ = pBuf[lIndex];
                  pBuf[rIndex] = templ;
                  pBuf[lIndex] = tempr;
               }
         }

         // >> img.SetPixels(pBuf, img.Width(), roi_.x, roi_.y);
      }
      else if (depth == 2)
      {
         assert(false);
      }

      dPhase += cPi / 5.0;
   }
}


int BitFlowCamera::LiveThread::svc()
{
   stopRunning_ = false;
   running_ = true;
   imageCounter_ = 0;

   bool slow = cam_->IsPropertyEqualTo(g_PropertySlow, g_On);
   long slowImageCounter = 0;

   // put the hardware into a continuous acqusition state

   while (true)
   {
      if (stopRunning_)
         break;

      int ret = 0;

      if (streaming_)
         ret = cam_->SnapImageCont();
      else
         ret = cam_->SnapImage();

      if (ret != DEVICE_OK)
      {
         char txt[1000];
         sprintf(txt, "BitFlow live thread: ImageSnap() error %d", ret);
         cam_->GetCoreCallback()->LogMessage(cam_, txt, false);
         break;
      }

      char label[MM::MaxStrLength];
   
      cam_->GetLabel(label);

      MM::MMTime timestamp = cam_->GetCurrentMMTime();
      Metadata md;

      MetadataSingleTag mstStartTime(MM::g_Keyword_Metadata_StartTime, label, true);
	   mstStartTime.SetValue(CDeviceUtils::ConvertToString(cam_->startTime_.getMsec()));
      md.SetTag(mstStartTime);

      MetadataSingleTag mstElapsed(MM::g_Keyword_Elapsed_Time_ms, label, true);
      MM::MMTime elapsed = timestamp - cam_->startTime_;
      mstElapsed.SetValue(CDeviceUtils::ConvertToString(elapsed.getMsec()));
      md.SetTag(mstElapsed);

      MetadataSingleTag mstCount(MM::g_Keyword_Metadata_ImageNumber, label, true);
      mstCount.SetValue(CDeviceUtils::ConvertToString(imageCounter_));
      md.SetTag(mstCount);

      if (streaming_)
      {
         // int skipped = -1;
         if (cam_->colorMode_ == Color)
         {
         }
         else if (cam_->colorMode_ == MultiChannel)
         {
            // insert all enabled channels
			 for (unsigned i=0; i<cam_->GetNumberOfChannels(); i++)
            {
               char buf[MM::MaxStrLength];
               MetadataSingleTag mstChannel(MM::g_Keyword_CameraChannelIndex, label, true);
               snprintf(buf, MM::MaxStrLength, "%d", i);
               mstChannel.SetValue(buf);
               md.SetTag(mstChannel);

               MetadataSingleTag mstChannelName(MM::g_Keyword_CameraChannelName, label, true);
               cam_->GetChannelName(i, buf);
               mstChannelName.SetValue(buf);
               md.SetTag(mstChannelName);
               ret = cam_->GetCoreCallback()->InsertImage(cam_, cam_->GetImageBuffer(i),
                                                          cam_->GetImageWidth(),
                                                          cam_->GetImageHeight(),
                                                          cam_->GetImageBytesPerPixel(),
                                                          &md);
               if (ret == DEVICE_BUFFER_OVERFLOW)
               {
                  cam_->GetCoreCallback()->ClearImageBuffer(cam_);
                  cam_->GetCoreCallback()->InsertImage(cam_, cam_->GetImageBuffer(i),
                                                       cam_->GetImageWidth(),
                                                       cam_->GetImageHeight(),
                                                       cam_->GetImageBytesPerPixel(),
                                                       &md);
               }
               else if (ret != DEVICE_OK)
               {
                  cam_->GetCoreCallback()->LogMessage(cam_, "BitFlow thread: error inserting image", false);
                  break;
               }
            }
         }
         else
         {
            char buf[MM::MaxStrLength];
            MetadataSingleTag mstChannel(MM::g_Keyword_CameraChannelIndex, label, true);
            snprintf(buf, MM::MaxStrLength, "%d", cam_->inputChannel_);
            mstChannel.SetValue(buf);
            md.SetTag(mstChannel);

            MetadataSingleTag mstChannelName(MM::g_Keyword_CameraChannelName, label, true);
            cam_->GetChannelName(cam_->inputChannel_, buf);
            mstChannelName.SetValue(buf);
            md.SetTag(mstChannelName);
            ret = cam_->GetCoreCallback()->InsertImage(cam_, cam_->GetImageBuffer(cam_->inputChannel_),
                                                          cam_->GetImageWidth(),
                                                          cam_->GetImageHeight(),
                                                          cam_->GetImageBytesPerPixel(),
                                                          &md);
            if (ret == DEVICE_BUFFER_OVERFLOW)
            {
               cam_->GetCoreCallback()->ClearImageBuffer(cam_);
               cam_->GetCoreCallback()->InsertImage(cam_, cam_->GetImageBuffer(cam_->inputChannel_),
                                                       cam_->GetImageWidth(),
                                                       cam_->GetImageHeight(),
                                                       cam_->GetImageBytesPerPixel(),
                                                       &md);
            }
            else if (ret != DEVICE_OK)
            {
               cam_->GetCoreCallback()->LogMessage(cam_, "BitFlow thread: error inserting image", false);
               break;
            }
         }
      }
      else
      {
         if (cam_->colorMode_ == MultiChannel)
         {
            // insert all channels
            for (unsigned i=0; i<cam_->GetNumberOfChannels(); i++)
            {
               char buf[MM::MaxStrLength];
               MetadataSingleTag mstChannel(MM::g_Keyword_CameraChannelIndex, label, true);
               snprintf(buf, MM::MaxStrLength, "%d", i);
               mstChannel.SetValue(buf);
               md.SetTag(mstChannel);

               MetadataSingleTag mstChannelName(MM::g_Keyword_CameraChannelName, label, true);
               cam_->GetChannelName(i, buf);
               mstChannelName.SetValue(buf);
               md.SetTag(mstChannelName);
               ret = cam_->GetCoreCallback()->InsertImage(cam_, cam_->GetImageBuffer(i),
                                                          cam_->GetImageWidth(),
                                                          cam_->GetImageHeight(),
                                                          cam_->GetImageBytesPerPixel(),
                                                          &md);
               if (ret == DEVICE_BUFFER_OVERFLOW)
               {
                  cam_->GetCoreCallback()->ClearImageBuffer(cam_);
                  cam_->GetCoreCallback()->InsertImage(cam_, cam_->GetImageBuffer(i),
                                                       cam_->GetImageWidth(),
                                                       cam_->GetImageHeight(),
                                                       cam_->GetImageBytesPerPixel(),
                                                       &md);
               }
               else if (ret != DEVICE_OK)
               {
                  cam_->GetCoreCallback()->LogMessage(cam_, "BitFlow thread: error inserting image", false);
                  break;
               }
            }
         }
         else if (cam_->colorMode_ == Color)
         {
            char buf[MM::MaxStrLength];
            MetadataSingleTag mstChannel(MM::g_Keyword_CameraChannelIndex, label, true);
            snprintf(buf, MM::MaxStrLength, "%s", "0");
            mstChannel.SetValue(buf);
            md.SetTag(mstChannel);

            MetadataSingleTag mstChannelName(MM::g_Keyword_CameraChannelName, label, true);
            mstChannelName.SetValue("RGB");
            md.SetTag(mstChannelName);
            ret = cam_->GetCoreCallback()->InsertImage(cam_, reinterpret_cast<const unsigned char*>(cam_->GetImageBufferAsRGB32()),
                                                          cam_->GetImageWidth(),
                                                          cam_->GetImageHeight(),
                                                          cam_->GetImageBytesPerPixel(),
                                                          &md);
            if (ret == DEVICE_BUFFER_OVERFLOW)
            {
               cam_->GetCoreCallback()->ClearImageBuffer(cam_);
               cam_->GetCoreCallback()->InsertImage(cam_, reinterpret_cast<const unsigned char*>(cam_->GetImageBufferAsRGB32()),
                                                       cam_->GetImageWidth(),
                                                       cam_->GetImageHeight(),
                                                       cam_->GetImageBytesPerPixel(),
                                                       &md);
            }
            else if (ret != DEVICE_OK)
            {
               cam_->GetCoreCallback()->LogMessage(cam_, "BitFlow thread: error inserting image", false);
               break;
            }
         }
         else if (cam_->colorMode_ == Grayscale)
         {
            char buf[MM::MaxStrLength];
            MetadataSingleTag mstChannel(MM::g_Keyword_CameraChannelIndex, label, true);
            snprintf(buf, MM::MaxStrLength, "%d", cam_->inputChannel_);
            mstChannel.SetValue(buf);
            md.SetTag(mstChannel);

            MetadataSingleTag mstChannelName(MM::g_Keyword_CameraChannelName, label, true);
            cam_->GetChannelName(cam_->inputChannel_, buf);
            mstChannelName.SetValue(buf);
            md.SetTag(mstChannelName);
            ret = cam_->GetCoreCallback()->InsertImage(cam_, cam_->GetImageBuffer(cam_->inputChannel_),
                                                          cam_->GetImageWidth(),
                                                          cam_->GetImageHeight(),
                                                          cam_->GetImageBytesPerPixel(),
                                                          &md);
            if (ret == DEVICE_BUFFER_OVERFLOW)
            {
               cam_->GetCoreCallback()->ClearImageBuffer(cam_);
               cam_->GetCoreCallback()->InsertImage(cam_, cam_->GetImageBuffer(cam_->inputChannel_),
                                                       cam_->GetImageWidth(),
                                                       cam_->GetImageHeight(),
                                                       cam_->GetImageBytesPerPixel(),
                                                       &md);
            }
            else if (ret != DEVICE_OK)
            {
               cam_->GetCoreCallback()->LogMessage(cam_, "BitFlow thread: error inserting image", false);
               break;
            }
         }
      }

      imageCounter_++;
      if (numImages_ >=0 && imageCounter_ >= numImages_)
      {
         cam_->bfDev_.StopContinuousAcq();
         break;
      }
   }
   
   running_ = false;
   cam_->GetCoreCallback()->AcqFinished(cam_, 0); // to close the shutter
   return 0;
}

void BitFlowCamera::LiveThread::Abort()
{
   stopRunning_ = true;
   wait();
}

///////////////////////////////////////////////////////////////////////////////
// UTILTY image warping functions
///////////////////////////////////////////////////////////////////////////////

bool BitFlowCamera::isChannelIncluded(int chan)
{
   return (chan == RChannel_ || chan == GChannel_ || chan == BChannel_);
}

/**
 * Almost verbatim copy of the original cosine warping routine, creating a 
 * pixel lookup table.
 */
void BitFlowCamera::GetCosineWarpLUT(vector<int> &new_pixel, int image_width, int raw_width)
{
   const double PIE(3.141582);
   vector<int> real_pixel;
   real_pixel.resize(image_width);
   new_pixel.resize(image_width);

   double cosine;
	double angle_factor;
	double angle_position;
	double radian_position;
	double linear_position;
	double correction_factor;
	double lowest_factor;
	//double correct_pixel;
   int old_pixel;
	int pixel;
	int center_pixel;
   //char ch;

   /* image width in pixels after pixel reversal routine
      half value in H_Max_Capture_size in MU Tech Driver file
      Could be an option in the Stream Filter
      May also need to be adjusted by the offset in the Pixel reversal process
   */

   /*calculate the angle per clock interval for H scan line
   360.0  = 360 degrees for forward and vbackward scan,
   1400 = number of clock intervals for each line
   (from the Mu-Tech driver file: [PPL Control],  Pixel_Per_Line = 1400)
   This variable could be automatically read or be part of the
   stream filter options*/

   angle_factor=360.0/raw_width;
   angle_factor=360.0/1524;

   /*
   Dec 2006
   PixelsPerLine = 127.0 * FREQ – FREQ can be varied by the user 
   127 = usecond for the line scan and FREQ = the acquisition H clock frequency. 
   Thus for the Raven and CRS mirror this = 127 x 12 = 1524 ppl

   angle_factor= 360/PixelsPerline – this is what is used now 

   1. First convert the image pixel location to a degree location
   This is done from the right side of the image as this is the
   only location for which the degree position (90) is known.
   2. Calculate radian position (linear equivalent of angle) from the right edge
   of the image.
   3. Determine the cosine value of the radian position.
   4. Determine the linear position in radians with respect to the image center
   5. Determine the correction factor, linear position/cosine position
   relatative to image center
   6. Find pixel = to image center based on mirror rotation,
   where correction factor = 1.0000000000
   7. Repeat steps 1 - 5 in 2nd loop
   8. Calculate corrected pixel location from center: Center position +
   pixel distance from center divide by correction factor */

   /*6. First loop to find center pixel*/
   
   lowest_factor=2.0; /*set comparison factor at greater value*/
   for(pixel=0;pixel<image_width;pixel++)
	{
	   old_pixel = image_width - pixel;
	   angle_position = 90.0 - (pixel * angle_factor);         /*1*/
	   radian_position =(90.0*PIE/180.0) - (angle_position * PIE/180.0);  /*2*/
	   cosine=cos(radian_position);                           /*3*/
	   linear_position = (PIE/2.0) - radian_position;         /*4*/
	   correction_factor = linear_position/cosine;                /*5*/
	   /*printf("\n %d  %d %f",pixel,old_pixel,correction_factor);*/    /*line to check values*/
	   if(correction_factor<lowest_factor)                                /*6*/
		{
		   lowest_factor=correction_factor;
		   center_pixel=image_width - pixel;
		}
	}
   /*
   This code in blue is in the original dll and was used to find where the correction factor went to 1.0 
   but I believe it can be replaced with a much simpler code

   The pixel of the image where the distortion factor = 1 is at angle time 0 (or phase 0) and is 
	= (FREQx127.0)/4  (pixels per line/4)
   However this must be expressed relative to position where the distortion is the greatest at time, T or Phase Pie/2)
	Correct Image Center position = PPL/2 – FREQx127/4
   */

   //printf("\nCenter pixel = %d, correlation factor = %f",center_pixel,lowest_factor);

   /*7. Loop to calculated corrected pixel position*/
   for(pixel=0;pixel<image_width;pixel++)
	{
	   old_pixel = image_width - pixel - 1;
	   angle_position = 90.0 - (pixel * angle_factor);         /*1*/
	   radian_position =(90.0*PIE/180.0) - (angle_position * PIE/180.0);  /*2*/
	   cosine=cos(radian_position);                           /*3*/
	   linear_position = (PIE/2.0) - radian_position;         /*4*/
	   correction_factor = linear_position/cosine;           /*5*/
      if (correction_factor == 0.0)
         correction_factor = 1.0;
	   real_pixel[old_pixel] = center_pixel + (int)((double)(old_pixel - center_pixel)/correction_factor); /*8*/
	}
   
   /*
   Again this code can be replaced by simpler code 
   The correction factor = ø /sin ø where  ø = pixel number (from the correct center pixel) * ?ø 
   ?ø = 2?/freq*127. The corretion factor is calculated for each pixel and applied to the pixel
   */

   /*9 Loop to shift pixels to new image LUT*/
   for(pixel=0;pixel<image_width;pixel++)
	{
	   new_pixel[pixel]=real_pixel[pixel]-real_pixel[0];
	   //printf("\nold pixel %d, new pixel %d", pixel, new_pixel[pixel]);
	}
}

void BitFlowCamera::SetFrameOffset(int offset)
{
   if (offset < -BFCamera::MAX_FRAME_OFFSET)
      frameOffset_ = -BFCamera::MAX_FRAME_OFFSET;
   else if (offset > BFCamera::MAX_FRAME_OFFSET)
      frameOffset_ = BFCamera::MAX_FRAME_OFFSET;
   else
      frameOffset_= offset;
}

int BitFlowCamera::GetFrameOffset()
{
   return frameOffset_;
}

/**
 * Fills the image array with correctly sized, deinterlaced and warped images.
 */
void BitFlowCamera::DeinterlaceBuffer(unsigned char* buf, int bufLen, unsigned rawWidth, bool warp)
{
   MirrorBuffer(buf, bufLen, (unsigned)img_.size(), rawWidth, img_[0].Height()/2);
   ConstructImage(buf, bufLen, rawWidth, warp);
}

/**
 * Mirrors every second line in the buffer.
 */
void BitFlowCamera::MirrorBuffer(unsigned char* buf, int bufLen, unsigned numChannels, unsigned rawWidth, unsigned rawHeight)
{
   for (unsigned i=0; i<numChannels; i++)
   {

      //if (!isChannelIncluded(i) && liveThd_->isStreaming())
      //   continue; // skip channels that are not included

      unsigned char* channelPtr(0);
      if (demoMode_)
         channelPtr = const_cast<unsigned char*>(buf) + i*bufLen;
      else
         channelPtr = const_cast<unsigned char*>(buf) + i*bufLen + BFCamera::MAX_FRAME_OFFSET + frameOffset_;

      unsigned rawWidth2 = rawWidth/2;
      for (unsigned j=0; j<rawHeight; j++)
      {
         // second half or the line reversed
         unsigned char* srcLinePtr = channelPtr + j*rawWidth + rawWidth/2;
         for (unsigned k=0; k<rawWidth/4; k++)
         {
            unsigned char temp;
            int mirrorIdx = rawWidth2 - k - 1;
            temp = srcLinePtr[k];
            srcLinePtr[k] = srcLinePtr[mirrorIdx];
            srcLinePtr[mirrorIdx] = temp;
         }

         // shift the line using warp offset
         if (warpOffset_ > 0)
         {
            memcpy(lineBuf_, srcLinePtr + warpOffset_, rawWidth2-warpOffset_);
            memcpy(srcLinePtr, lineBuf_, rawWidth2);
         }
         else if (warpOffset_ < 0)
         {
            memcpy(lineBuf_ + warpOffset_, srcLinePtr, rawWidth2 + warpOffset_);
            memcpy(srcLinePtr, lineBuf_, rawWidth2);
         }
      }
   }
}

/**
 * Creates correctly sized and warped images from the raw buffer.
 */
void BitFlowCamera::ConstructImage(unsigned char* buf, int bufLen, unsigned rawWidth, bool warp)
{
   assert(rawWidth/2 > img_[0].Width());
   unsigned char* lineBufPtr = lineBuf_;
   int fullWidth = rawWidth/2;
   const int warpOffsetBase = -119; // this warp offset produces full frame at 480 pixels per line

   for (unsigned i=0; i<img_.size(); i++)
   {
      /*
      if (!isChannelIncluded(i) && liveThd_->isStreaming())
         continue; // skip channels that are not included
      */

      int lineWidth = img_[i].Width();
      
      unsigned char* channelPtr(0);
      if (demoMode_)
         channelPtr = const_cast<unsigned char*>(buf) + i*bufLen;
      else
         channelPtr = const_cast<unsigned char*>(buf) + i*bufLen + BFCamera::MAX_FRAME_OFFSET + frameOffset_;

      for (unsigned j=0; j<img_[i].Height(); j++)
      {
         unsigned char* rawLinePtr = channelPtr + j*fullWidth;
         unsigned char* destLinePtr = lineBuf_;
         unsigned char* destPtr = scratchBuf_ + j*lineWidth;

         // warp if required
         if (warp)
         {
            memset(destLinePtr, 0, fullWidth);
            for (int k=0; k<fullWidth; k++)
            {
               int index = altPixelLookup_[k];
               if (index >= 0 && index < fullWidth)
                  destLinePtr[index] = rawLinePtr[k];
            }
         }
         else
            memcpy(destLinePtr, rawLinePtr, fullWidth);

         // select the output frame
         for (int k=0; k<lineWidth; k++)
         {
            int srcIdx = fullWidth/2 - lineWidth/2 /* + warpOffset_ */ + warpOffsetBase + k;
            if (srcIdx < 0 || srcIdx > (int)fullWidth - 1)
               destPtr[k] = 0;
            else
               destPtr[k] = destLinePtr[srcIdx];
         }
      }

      // averaging
      img_[i].AddPixels(scratchBuf_, lineWidth, roi_.x, roi_.y);
   }

}

// OBSOLETE. Just for testing
void BitFlowCamera::ConstructImageSingle(unsigned char* buf, int bufLen, unsigned rawWidth, bool cont, bool warp)
{
   assert(rawWidth/2 > img_[0].Width());
   unsigned char* lineBufPtr = lineBuf_;
   int fullWidth = rawWidth/2;
   int warpOffsetBase = -119; // this warp offset produces full frame at 480 pixels per line

   int lineWidth = img_[inputChannel_].Width();
   unsigned char* channelPtr(0);
   if (demoMode_)
      channelPtr = const_cast<unsigned char*>(buf) + inputChannel_*bufLen;
   else
      channelPtr = const_cast<unsigned char*>(buf) + inputChannel_*bufLen + BFCamera::MAX_FRAME_OFFSET + frameOffset_;

   if (warp)
   {
      for (unsigned j=0; j<img_[inputChannel_].Height(); j++)
      {
         unsigned char* rawLinePtr = channelPtr + j*fullWidth;
         unsigned char* destLinePtr = scratchBuf_ + j*fullWidth;
         memset(destLinePtr, 0, fullWidth);
         for (int k=0; k<fullWidth; k++)
         {
            int index = altPixelLookup_[k];
            if (index >= 0 && index < fullWidth)
               destLinePtr[index] = rawLinePtr[k];
         }
      }
      memcpy(channelPtr, scratchBuf_, fullWidth * img_[inputChannel_].Height());
   }
      
   // select the output frame
   for (unsigned j=0; j<img_[inputChannel_].Height(); j++)
   {
      unsigned char* rawLinePtr = channelPtr + j*fullWidth;
      unsigned char* destLinePtr = scratchBuf_ + j*lineWidth;
      for (int k=0; k<lineWidth; k++)
      {
         int srcIdx = fullWidth/2 - lineWidth/2 /* + warpOffset_ */ + warpOffsetBase + k;
         if (srcIdx < 0 || srcIdx > (int)fullWidth - 1)
            destLinePtr[k] = 0;
         else
            destLinePtr[k] = rawLinePtr[srcIdx];
      }
   }

   // averaging
   if (cont)
      img_[inputChannel_].SetPixels(scratchBuf_, lineWidth, roi_.x, roi_.y);
   else
      img_[inputChannel_].AddPixels(scratchBuf_, lineWidth, roi_.x, roi_.y);

}