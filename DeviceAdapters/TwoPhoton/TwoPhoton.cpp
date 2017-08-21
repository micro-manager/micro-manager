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

using namespace std;

const char* g_BitFlowCameraDeviceName = "BitFlowCamera";
const char* g_BitFlowCameraDeviceName2 = "BitFlowCameraX2";

const char* g_PropertyChannel = "InputChannel";
const char* g_On = "On";
const char* g_Off = "Off";
const char* g_OnWarp = "On+Unwarp";
const char* g_FrameAverage = "FrameAverage";
const char* g_RawFramesToCircularBuffer = "RawFramesToCircularBuffer";
const char* g_PropertyDeinterlace = "Deinterlace";
const char* g_PropertyIntegrationMethod = "IntegrationMethod";
const char* g_PropertyIntervalMs = "FrameIntervalMs";
const char* g_PropertyProcessingTimeMs = "ProcessingTimeMs";
const char* g_PropertyCenterOffset = "CenterOffset";
const char* g_PropertyChannelOffset = "ChannelOffsets";
const char* g_PropertyEnableChannels = "EnableChannels";
const char* g_PropertyUseBitflowChannels = "EnableBitflowChannels";

extern const char* g_VShutterDeviceName;


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_BitFlowCameraDeviceName, MM::CameraDevice, "BitFlow frame grabber");
   RegisterDevice(g_BitFlowCameraDeviceName2, MM::CameraDevice, "BitFlow dual frame grabber");
   RegisterDevice(g_VShutterDeviceName, MM::ShutterDevice, "Virtual dual shutter");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   // decide which device class to create based on the deviceName parameter
   if (strcmp(deviceName, g_BitFlowCameraDeviceName) == 0) {
      // create camera
      return new BitFlowCamera(false);
   } if (strcmp(deviceName, g_BitFlowCameraDeviceName2) == 0){
      // create camera
      return new BitFlowCamera(true);
   } else if (strcmp(deviceName, g_VShutterDeviceName) == 0){
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
   initialized_(false),
   inputChannel_(0),
   expNumFrames_(1),
   liveThd_(0),
   binSize_(1),
   scratchBuf_(0),
   lineBuf_(0),
   frameBuf_(0),
   intervalMs_(0),
   processingTimeMs_(0),
   deinterlace_(false),
   cosineWarp_(false),
   channelsProcessed_(false),
   rawFramesToCircularBuffer_(false),
   frameOffset_(0),
   channelOffsets_(0),
   bfDev_(dual),
   byteDepth_(1)
{
	if (dual)
		numChannels_ = 6;
	else
		numChannels_ = 4;

   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_NUM_CHANNELS, "Number of available channels too high for this device adapter."
								  "Your system probably has two Bitflow cards installed."
                                  "Use BitFlowCameraX2 adapter instead.");
   //pre-init properties

   	//skip certain channels on bitflow board(s) if they have problems
   CPropertyAction *pAct = new CPropertyAction (this, &BitFlowCamera::OnEnableBitflowChannels);
   int ret = CreateProperty(g_PropertyUseBitflowChannels, "11111111", MM::String, false, pAct, true);
   assert(ret == DEVICE_OK);

   img_.resize(numChannels_);
}

BitFlowCamera::~BitFlowCamera()
{
   delete[] frameBuf_;
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

   // filtering method
   pAct = new CPropertyAction (this, &BitFlowCamera::OnFilterMethod);
   ret = CreateProperty(g_PropertyIntegrationMethod, g_FrameAverage, MM::String, false, pAct);
   assert(ret == DEVICE_OK);

   vector<string> rfValues;
   rfValues.push_back(g_FrameAverage);
   rfValues.push_back(g_RawFramesToCircularBuffer);
   ret = SetAllowedValues(g_PropertyIntegrationMethod, rfValues);
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
   pAct = new CPropertyAction (this, &BitFlowCamera::OnChannelOffset);
   ret = CreateProperty(g_PropertyChannelOffset,  "000000", MM::String, false, pAct);
   assert(ret == DEVICE_OK);

   // enable specific channels
   pAct = new CPropertyAction (this, &BitFlowCamera::OnEnableChannels);
   ret = CreateProperty(g_PropertyEnableChannels, "11111111", MM::String, false, pAct);
   assert(ret == DEVICE_OK);
   
   // synchronize all properties
   // --------------------------
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;


   //Initialize bitflow wrapper
   if (!bfDev_.isInitialized())  {
	   int ret = bfDev_.Initialize(this, this->GetCoreCallback());
	   if (ret != DEVICE_OK) {
		   return ret;
	   }

	   char message[200];
		strcpy(message,"number of buffers, number of channels ");
		strcat(message,  CDeviceUtils::ConvertToString((int) bfDev_.GetNumberOfBuffers()) );
		strcat(message,  CDeviceUtils::ConvertToString((int) numChannels_) );
		GetCoreCallback()->LogMessage(this,message, true );


	   // at this point frame grabber is successfully initialized so
	   // we can re-assign image buffers
	   if (bfDev_.GetNumberOfBuffers() != numChannels_)
	   {
		   bfDev_.Shutdown();
		   return ERR_NUM_CHANNELS;
	   }
   }


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

   return DEVICE_OK;
}

/**
 * Grabs images from all channels. 
 * Modified to perfrom continouous integration
 */
int BitFlowCamera::SnapImage()
{
	int numFrames = expNumFrames_;
	if (numFrames <= 0)
		return DEVICE_OK;

	// clear all accumulators (set to zero)
	if (!rawFramesToCircularBuffer_) {
		for (unsigned j=0; j<img_.size(); j++) {
			img_[j].ResetPixels();
		}
	}

	if (!bfDev_.isInitialized())
		return ERR_HARDWARE_NOT_INITIALIZED;
	bfDev_.StartSequence(); // start streaming mode
	for (int k=0; k < numFrames; k++) {

		//char message[100];
		//strcpy(message,"Frame number ");
		//strcat(message,CDeviceUtils::ConvertToString(k));
		//GetCoreCallback()->LogMessage(this, message,true);

		unsigned char* buf = const_cast<unsigned char*>(bfDev_.GetImageCont());      
		if (buf == 0) {
			ostringstream txt;
			txt << "Bitflow board failed in streaming mode";
			GetCoreCallback()->LogMessage(this, txt.str().c_str(), false);
			bfDev_.StopSequence();
			return ERR_SNAP_FAILED;
		}
		unsigned bufLen = bfDev_.GetBufferSize();
		if (rawFramesToCircularBuffer_) {
			//put double wide, warped image in the circular buffer for correction in java layer
			for (unsigned i=0; i<img_.size(); i++) { //put an image from each active channel in circular buffer
				unsigned int width = bfDev_.Width();
				unsigned int height = bfDev_.Height();

				unsigned ret = GetCoreCallback()->InsertImage(this,buf + i*bufLen + BFCamera::MAX_FRAME_OFFSET,width,height,1);
				if (ret == DEVICE_BUFFER_OVERFLOW) {
					GetCoreCallback()-> LogMessage(this, "BitFlow snapImage: device buffer overflow", false);
					return DEVICE_ERR;
				} else if (ret == DEVICE_INCOMPATIBLE_IMAGE) {
					GetCoreCallback()-> LogMessage(this, "BitFlow thread: wrong image size for circular buffer insert", false);
					return DEVICE_ERR;			
				} else if (ret != DEVICE_OK) {
					GetCoreCallback()-> LogMessage(this, "BitFlow thread: error inserting image", false);
					return DEVICE_ERR;
				}
			}
		} else if (deinterlace_) {
			// de-interlace, re-size and correct image
			DeinterlaceBuffer(buf, bufLen, bfDev_.Width(), cosineWarp_);
		} else {
			// add images as they come from the frame grabber
			for (unsigned i=0; i<img_.size(); i++)
				img_[i].AddPixels(buf + i*bufLen + GetChannelOffset(i) + BFCamera::MAX_FRAME_OFFSET, bfDev_.Width(), roi_.x, roi_.y); // add image
		}
	}
	bfDev_.StopSequence(); //stop streaming mode

	//mark that new image needs to be processed by rank filtering or frame averaging before core
	//can grab it
   channelsProcessed_ = false;
   //return here so that shutter closes ASAP
   //Do frame averaging/rank filtering later once shutter closed
   return DEVICE_OK;
}

/**
 * Returns pixel data.
 */
const unsigned char* BitFlowCamera::GetImageBuffer()
{  
      return img_[inputChannel_].GetPixels();
}

const unsigned char* BitFlowCamera::GetImageBuffer(unsigned chNo) {
	//Apply frame averaging 
	if (!channelsProcessed_)
	{
		MM::MMTime processingTime(0, 0);

		MM::MMTime startProcessingTime = GetCoreCallback()->GetCurrentMMTime();
		for (unsigned i=0; i<img_.size(); i++)
			img_[i].CalculateOutputImage(); // average, sum, or rank filter

		processingTime = processingTime + (GetCoreCallback()->GetCurrentMMTime() - startProcessingTime);
		processingTimeMs_ = processingTime.getMsec() / expNumFrames_;
		channelsProcessed_ = true;
	}
	vector<int> ech = GetEnabledChannels();
	if (chNo >= ech.size())
		return 0;
	else
		return img_[ech[chNo]].GetPixels();

}

int BitFlowCamera::GetChannelName(unsigned channel, char* name)
{
   if (channel >= img_.size())
      return DEVICE_NONEXISTENT_CHANNEL;

   ostringstream txt;
   vector<int> chm = GetEnabledChannels();
   if (channel >= chm.size())
	   return DEVICE_NONEXISTENT_CHANNEL;
   txt << "Input-" << chm[channel];

   CDeviceUtils::CopyLimitedString(name, txt.str().c_str());
   return DEVICE_OK;
}


/**
* Returns image buffer X-size in pixels.
*/
unsigned BitFlowCamera::GetImageWidth() const
{
	if (rawFramesToCircularBuffer_) {
		return bfDev_.Width();
	}
   return img_[inputChannel_].Width();
}

/**
* Returns image buffer Y-size in pixels.
*/
unsigned BitFlowCamera::GetImageHeight() const
{
	if (rawFramesToCircularBuffer_) {
		return bfDev_.Height();
	}
   return img_[inputChannel_].Height();
}

/**
* Returns image buffer pixel depth in bytes.
*/
unsigned BitFlowCamera::GetImageBytesPerPixel() const
{
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
   if (expNumFrames_ <= 0)
      expNumFrames_ = 1;
   if (expNumFrames_ > maxFrames_)
      expNumFrames_ = maxFrames_;

   for (unsigned i=0; i<img_.size(); i++)
      img_[i].SetLength(expNumFrames_);
   //callback to GUI
   this->GetCoreCallback()->OnExposureChanged(this,expNumFrames_);
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

int BitFlowCamera::StartSequenceAcquisition(long numImages, double, bool){
   if (IsCapturing())
      return DEVICE_CAMERA_BUSY_ACQUIRING;

   // this will open the shutter
   GetCoreCallback()->PrepareForAcq(this);

   // start the frame grabber
   int ret = bfDev_.StartContinuousAcq();
   if (ret != DEVICE_OK)
	   return ret;

   startTime_ = GetCurrentMMTime();

   liveThd_->SetNumImages(numImages);
   startTime_ = GetCoreCallback()->GetCurrentMMTime();
   liveThd_->activate();

   return DEVICE_OK;
}

int BitFlowCamera::StartSequenceAcquisition(double ) {
   if (IsCapturing())
      return DEVICE_CAMERA_BUSY_ACQUIRING;

   // this will open the shutter
   GetCoreCallback()->PrepareForAcq(this);

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
   GetCoreCallback()->AcqFinished(this, 0);
   return DEVICE_OK;
}

int BitFlowCamera::PrepareSequenceAcqusition()
{
   // nothing to prepare
   return DEVICE_OK;
}

unsigned BitFlowCamera::GetNumberOfComponents() const
{
     return 1;
}

unsigned BitFlowCamera::GetNumberOfChannels() const
{
	return static_cast<unsigned int>( GetEnabledChannels().size());
	//return (unsigned)img_.size();
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

int BitFlowCamera::OnFilterMethod(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
	   if (IsCapturing())
		   return DEVICE_CAMERA_BUSY_ACQUIRING;
	   string val;
	   pProp->Get(val);
	   if (val.compare(g_RawFramesToCircularBuffer) == 0) {
		   rawFramesToCircularBuffer_ = true;
	   } else {
		   //frame averaging
		   rawFramesToCircularBuffer_ = false;
	   }
	   //resize image accumulators to reflect new byte depth
	   ResizeImageBuffer();
   } else if (eAct == MM::BeforeGet){
	   if  (rawFramesToCircularBuffer_) {
		   pProp->Set(g_RawFramesToCircularBuffer);	
	   } else {
		   pProp->Set(g_FrameAverage);
	   }
   }
   return DEVICE_OK;
}

int BitFlowCamera::OnFrameInterval(MM::PropertyBase* pProp, MM::ActionType eAct) {
   if (eAct == MM::BeforeGet) {
      pProp->Set((long)(intervalMs_ + 0.5));
   }
   return DEVICE_OK;
}

int BitFlowCamera::OnProcessingTime(MM::PropertyBase* pProp, MM::ActionType eAct) {
   if (eAct == MM::BeforeGet) {
      pProp->Set((long)(processingTimeMs_ + 0.5));
   }
   return DEVICE_OK;
}

/**
 * Center offset property has effect only with snake mode on.
 */
int BitFlowCamera::OnCenterOffset(MM::PropertyBase* pProp, MM::ActionType eAct) {
   if (eAct == MM::AfterSet) {
      long co;
      pProp->Get(co);
      frameOffset_ = (int)co;
   }
   else if (eAct == MM::BeforeGet) {
      pProp->Set((long)frameOffset_);
   }

   return DEVICE_OK;
}

/**
 * Warp center offset.
 */
int BitFlowCamera::OnChannelOffset(MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (eAct == MM::AfterSet)
	{
		string chanlist;
		pProp->Get(chanlist);
		channelOffsets_.clear();
		for (unsigned i=0; i<chanlist.length(); i++)
		{
			if (i < img_.size())
			{
				//parse char to int
				int offset = *(chanlist.c_str() + i) - '0';
				channelOffsets_.push_back(offset);	
			}
		}
	}
	else if (eAct == MM::BeforeGet)
	{
		string chanlist;
		for (unsigned i=0; i< img_.size(); i++)
		{
			//make sure warp offsets initialized
			if (channelOffsets_.size() < i + 1)
				channelOffsets_.push_back(0);
			std::string s = to_string((long long) (channelOffsets_[i]));
			chanlist += s;
		}
		pProp->Set(chanlist.c_str());
	}

	return DEVICE_OK;
}

int BitFlowCamera::OnEnableChannels(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      string chanlist;
      pProp->Get(chanlist);
	  		  bool atLeastOneChannel = false;
	  for (unsigned i=0; i<chanlist.length(); i++)
	  {

		  if (i < img_.size())
		  {
			  if (*(chanlist.c_str() + i) == '0') {
				img_[i].SetEnable(false);
			  } else {
				img_[i].SetEnable(true);
				atLeastOneChannel = true;
			  }
			  //if all 0, its a mistake, so enable all
			  if (i == img_.size() - 1 && !atLeastOneChannel) {
				for (unsigned j=0; j<chanlist.length(); j++) {
					img_[j].SetEnable(true);
				}
			  }
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

int BitFlowCamera::OnEnableBitflowChannels(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)  {
      string chanlist;
	  pProp->Get(chanlist);	  
	  bfDev_.UseVFGs(chanlist);
   }  else if (eAct == MM::BeforeGet) {
       string chanlist;
	   for (unsigned i=0; i < numChannels_; i++)
		   if (bfDev_.VFGActive(i))
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
	if (deinterlace_) {
		for (unsigned i=0; i<img_.size(); i++)
			img_[i].Resize(imageWidth_/binSize_, (bfDev_.Height()*2)/binSize_, byteDepth_);
		if (img_.size() > 0) {
			GetCosineWarpLUT(altPixelLookup_, bfDev_.Width()/2, bfDev_.Width());
		}
	}
	else {
		for (unsigned i=0; i<img_.size(); i++)
			img_[i].Resize(bfDev_.Width()/binSize_, bfDev_.Height()/binSize_, byteDepth_);
	}


   delete[] scratchBuf_;
   scratchBuf_ = new unsigned char[ bfDev_.Width() * bfDev_.Height()];

   delete[] lineBuf_;
   lineBuf_ = new unsigned char[bfDev_.Width()/2];

   return DEVICE_OK;
}

/**
* Generate a spatial sine wave.
*/
void BitFlowCamera::GenerateSyntheticImage(void* buffer, unsigned width, unsigned height, unsigned depth, double exp)
{
	if (height == 0 || width == 0 || depth == 0)
		return;


	// generate a phase shifting sine wave

	const double cPi = 3.14;
	double period = width/30.3;
	static double dPhase = 0.0;
	double dLinePhase = 0.0;
	const double dAmp = exp;
//	const double cLinePhaseInc = 2.0 * cPi / 4.0 / height;


//	long maxValue = 1 << (depth * 8);

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


int BitFlowCamera::LiveThread::svc()
{
   stopRunning_ = false;
   running_ = true;
   imageCounter_ = 0;

   // put the hardware into a continuous acqusition state
   while (true)  {
      if (stopRunning_)
         break;

      int ret = cam_->SnapImage();

      if (ret != DEVICE_OK) {
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
			  md.Serialize().c_str());
		  if (ret == DEVICE_BUFFER_OVERFLOW) {
			  cam_->GetCoreCallback()->ClearImageBuffer(cam_);
			  cam_->GetCoreCallback()->InsertImage(cam_, cam_->GetImageBuffer(i),
				  cam_->GetImageWidth(),
				  cam_->GetImageHeight(),
				  cam_->GetImageBytesPerPixel(),
				  md.Serialize().c_str());
		  }
		  else if (ret != DEVICE_OK) {
			  cam_->GetCoreCallback()->LogMessage(cam_, "BitFlow thread: error inserting image", false);
			  break;
		  }
	  }


      imageCounter_++;
      if (numImages_ >=0 && imageCounter_ >= numImages_) {
         cam_->bfDev_.StopContinuousAcq();
         break;
      }
   }
   running_ = false;
   return 0;
}

void BitFlowCamera::LiveThread::Abort() {
   stopRunning_ = true;
   wait();
}

///////////////////////////////////////////////////////////////////////////////
// UTILTY image warping functions
///////////////////////////////////////////////////////////////////////////////

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
   //angle_factor=360.0/1524;
   angle_factor=360.0/1288; //theres a new camera file in town....

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


/**
 * Get offset specific to channel
 */
int BitFlowCamera::GetChannelOffset(int index) {

	int offset = frameOffset_ + (channelOffsets_[index] / 2);
	return offset;
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

      unsigned char* channelPtr = const_cast<unsigned char*>(buf) + i*bufLen + BFCamera::MAX_FRAME_OFFSET + GetChannelOffset(i);

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
		 //warp offset is 0 or 1, additional channle offsets apply to main offset
		 int channelWarpOffset = channelOffsets_[i] % 2;
		 // shift the line using warp offset
		 memcpy(lineBuf_, srcLinePtr - channelWarpOffset, rawWidth2-channelWarpOffset);
		 memcpy(srcLinePtr, lineBuf_, rawWidth2);
	  }
   }
}

/**
 * Creates correctly sized and warped images from the raw buffer.
 */
void BitFlowCamera::ConstructImage(unsigned char* buf, int bufLen, unsigned rawWidth, bool warp)
{
   assert(rawWidth/2 > img_[0].Width());
   int fullWidth = rawWidth/2;
   const int warpOffsetBase = -112; // this warp offset produces full frame at 480 pixels per line

   for (unsigned i=0; i<img_.size(); i++)
   {
      int lineWidth = img_[i].Width();
      
      unsigned char* channelPtr = const_cast<unsigned char*>(buf) + i*bufLen + BFCamera::MAX_FRAME_OFFSET + GetChannelOffset(i);

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
            int srcIdx = fullWidth/2 - lineWidth/2  + warpOffsetBase + k;
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
