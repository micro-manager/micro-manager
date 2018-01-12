///////////////////////////////////////////////////////////////////////////////
// FILE:          TSICam.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Thorlabs Scientific Imaging camera adapter
//                
// AUTHOR:        Nenad Amodaj, 2012
// COPYRIGHT:     Thorlabs
//
// DISCLAIMER:    This file is provided WITHOUT ANY WARRANTY;
//                without even the implied warranty of MERCHANTABILITY or
//                FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

#ifdef WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
//#include <fcntl.h>
//#include <io.h>
#pragma warning(disable : 4996) // disable warning for deprecated CRT functions on Windows 
#endif

#include <ModuleInterface.h>
#include "TsiCam.h"

#ifdef WIN32
   // global DLL handle
   HMODULE g_tsiDllHandle = 0;
#endif

#ifdef __APPLE__
#endif

#ifdef linux
#endif

#include <string>
#include <sstream>
#include <iomanip>

using namespace std;

TsiSDK* TsiCam::tsiSdk = 0;

static const WORD MAX_CONSOLE_LINES = 500;

namespace
{

void BuildWhiteBalancedPipeline (TsiColorImage& tsiColorImg, TsiColorCamera* tsiColorCamera)
{
   int rows = tsiColorImg.m_Height;
   int columns = tsiColorImg.m_Width;
   double rSum = 0.0, gSum = 0.0, bSum = 0.0;

   // Compute the sum of the pixel intensity values
   // separately for all 3 color channels.
   for (int i = 0; i < rows; ++i)
   {
      for (int j = 0; j < columns; ++j)
      {
         int tsiIdx = i*tsiColorImg.m_Width + j;

         rSum += tsiColorImg.m_ColorPixelDataBGR.BGR_16[tsiIdx].r;
         gSum += tsiColorImg.m_ColorPixelDataBGR.BGR_16[tsiIdx].g;
         bSum += tsiColorImg.m_ColorPixelDataBGR.BGR_16[tsiIdx].b;
      }
   }

   // Calculate the luminance coefficient
   double lumin = 0.2126 * rSum + 0.7152 * gSum + 0.0722 * bSum;

   // Determine the color balance coefficients.
   double rScaler = (rSum != 0) ? lumin / rSum : 1.0;
   double bScaler = (bSum != 0) ? lumin / bSum : 1.0;
   double gScaler = (gSum != 0) ? lumin / gSum : 1.0;

   // Send the coefficients to the SDK color pipeline
   // on the diagonal of a 3x3 matrix.
   //configure color processing
   double matrix[9] = { 1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0 };
   matrix[0] = rScaler;
   matrix[4] = gScaler;
   matrix[8] = bScaler;

   tsiColorCamera->ClearColorPipeline();
   tsiColorCamera->ConcatenateColorTransform(matrix);
   tsiColorCamera->ConcatenateColorTransform(TSI_Camera_Color_Correction, 0);
   tsiColorCamera->ConcatenateColorTransform(TSI_sRGB8_32, 0);
   tsiColorCamera->FinalizeColorPipeline();
}

}

TsiCam::TsiCam() :
   initialized(0), stopOnOverflow(false),
   acquiring(0),
   bayerMask(false),
   bitDepth(14),
   wb(false),
   whiteBalanceSelected(0),
   color(true),
   triggerPolarity(Positive),
   trigger(Software)
{
   // set default error messages
   InitializeDefaultErrorMessages();

   // set device specific error messages
   SetErrorText(ERR_TSI_DLL_LOAD_FAILED, "Couldn't find TSI SDK dll.\n"
      "  Make sure TSI DLLs are installed.");
   SetErrorText(ERR_TSI_SDK_LOAD_FAILED, "Error loading TSI SDK.");
   SetErrorText(ERR_TSI_OPEN_FAILED, "Failed opening TSI SDK.");
   SetErrorText(ERR_TSI_CAMERA_NOT_FOUND, "Couldn't detect any TSI cameras.\n"
      "  Make sure cameras are attached and the power is ON.");
   SetErrorText(ERR_IMAGE_TIMED_OUT, "Timed out waiting for the image from the camera.");
   SetErrorText(ERR_INVALID_CHANNEL_INDEX, "Invalid channel index");

   // initialize roi-bin structure
   roiBinData.XBin = 1;
   roiBinData.YBin = 1;
   roiBinData.XOrigin = 0;
   roiBinData.YOrigin = 0;
   roiBinData.YPixels = 0; // setting number of pixels to 0, signifies that we are not initialized yet
   roiBinData.XPixels = 0;

   // this identifies which camera we want to access
   CreateProperty(MM::g_Keyword_CameraID, "0", MM::Integer, false, 0, true);
   liveAcqThd_ = new AcqSequenceThread(this);
}


TsiCam::~TsiCam()
{
   Shutdown();
   delete liveAcqThd_;
}

///////////////////////////////////////////////////////////////////////////////
// MMDevice API
//
void TsiCam::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceTsiCam);
}

int TsiCam::Initialize()
{
   if (g_tsiDllHandle == 0)
   {
      // load TSL dll and create api handle
      g_tsiDllHandle = LoadLibrary("tsi_sdk.dll");
      if (g_tsiDllHandle)
      {
         TSI_CREATE_SDK tsi_create_sdk = (TSI_CREATE_SDK)GetProcAddress(g_tsiDllHandle, "tsi_create_sdk");
         if(tsi_create_sdk != 0)
            TsiCam::tsiSdk = tsi_create_sdk();
      }
   }

   if (g_tsiDllHandle == 0)
      return ERR_TSI_DLL_LOAD_FAILED;

   if (tsiSdk == 0)
      return ERR_TSI_SDK_LOAD_FAILED;

   if (!tsiSdk->Open())
      return ERR_TSI_OPEN_FAILED;

   int numCameras = tsiSdk->GetNumberOfCameras();
   if (numCameras == 0)
      return ERR_TSI_CAMERA_NOT_FOUND;

   // open the camera with the specified CameraID (default = 0)
   long camIndex(0);
   int ret = GetProperty(MM::g_Keyword_CameraID, camIndex);
   assert(ret == DEVICE_OK);

   camHandle_ = tsiSdk->GetCamera(camIndex);
   if (camHandle_ == 0)
      return ERR_TSI_CAMERA_NOT_FOUND;

   if (!camHandle_->Open())
      return ERR_CAMERA_OPEN_FAILED;

   camHandle_->Stop();

   // retrieve camera name
   char* camName = camHandle_->GetCameraName();
   assert(camName);
   ret = CreateProperty(MM::g_Keyword_CameraName, camName, MM::String, true);
   assert(ret == DEVICE_OK);

   // exposure
   CPropertyAction *pAct = new CPropertyAction (this, &TsiCam::OnExposure);
   ret = CreateProperty(MM::g_Keyword_Exposure, "2.0", MM::Float, false, pAct);
   assert(ret == DEVICE_OK);

   // gain
   pAct = new CPropertyAction (this, &TsiCam::OnGain);
   ret = CreateProperty(g_Gain, "0", MM::Integer, false, pAct);
   assert(ret == DEVICE_OK);

   bool bRet;

   // Unit of measurement for exposures - set to milliseconds
   uint32_t exp_unit = (uint32_t) TSI_EXP_UNIT_MILLISECONDS;
   bRet = camHandle_->SetParameter(TSI_PARAM_EXPOSURE_UNIT, (void*) &exp_unit);
   assert(bRet);

   // readout rate
   // try setting different rates to find out what is available

   bRet = false;
   vector<string> rateValues;
   uint32_t rateIdx(0);
   uint32_t rateIdxOrg(0);
   uint32_t rateIdxMin(0);
   uint32_t rateIdxMax(0);
   bRet = camHandle_->GetParameter(TSI_PARAM_READOUT_SPEED_INDEX, sizeof(uint32_t), &rateIdxOrg);
   assert(bRet);

   bRet = GetAttrValue(TSI_PARAM_READOUT_SPEED_INDEX, TSI_ATTR_MIN_VALUE, &rateIdxMin, sizeof (rateIdxMin)) ;
   assert(bRet);

   bRet = GetAttrValue(TSI_PARAM_READOUT_SPEED_INDEX, TSI_ATTR_MAX_VALUE, &rateIdxMax, sizeof (rateIdxMax)) ;
   assert(bRet);

   LogMessage("Getting readout speeds");

   rateIdx = rateIdxMin;

   while (bRet && (rateIdx <= rateIdxMax))
   {
      ostringstream txt;
      uint32_t speedMHz(0);

	  char Msg [80];
	  sprintf (Msg, "Getting speed - Index (%u)", rateIdx);
	  LogMessage(Msg);

	  bRet = camHandle_->SetParameter(TSI_PARAM_READOUT_SPEED_INDEX, rateIdx);
	  if (bRet) 
	  {

		  bRet = camHandle_->GetParameter(TSI_PARAM_READOUT_SPEED, sizeof(uint32_t), &speedMHz);
		  if (bRet)
		  {
			 txt << speedMHz / (uint32_t)1000000 << "_MHz";
			 rateValues.push_back(txt.str().c_str());
			 rateIdx++;
		  }
		  else
		  {
			 LogMessage("Error getting readout speed");
			 break;
		  }

	  }
	  else
	  {
		  LogMessage("camHandle_->SetParameter(TSI_PARAM_READOUT_SPEED_INDEX, rateIdx) failed");
	  }

   }

   if (rateValues.size() > 0)
   {
      pAct = new CPropertyAction (this, &TsiCam::OnReadoutRate);
      ret = CreateProperty(g_ReadoutRate, rateValues[0].c_str(), MM::String, false, pAct);
      assert(ret == DEVICE_OK);
      for (size_t i=0; i<rateValues.size(); i++)
      {
         AddAllowedValue(g_ReadoutRate, rateValues[i].c_str(), (long)i);
      }
      bRet = camHandle_->SetParameter(TSI_PARAM_READOUT_SPEED_INDEX, rateIdxOrg);
      assert(bRet);
   }


   if (ParamSupported (TSI_PARAM_TAPS_INDEX)) {

	   // TAPS
	   // try setting different rates to find out what is available
	   vector<string> tapValues;
	   uint32_t tapIdxOrg(0);

	   bRet;

	   uint32_t tapsIdxMin(0);
	   uint32_t tapsIdxMax(0);

	   bRet = camHandle_->GetParameter(TSI_PARAM_TAPS_INDEX, sizeof(uint32_t), &tapIdxOrg);
	   uint32_t tapIdx(0);

	   bRet = GetAttrValue(TSI_PARAM_TAPS_INDEX, TSI_ATTR_MIN_VALUE, &tapsIdxMin, sizeof (tapsIdxMin)) ;
	   assert(bRet);

	   bRet = GetAttrValue(TSI_PARAM_TAPS_INDEX, TSI_ATTR_MAX_VALUE, &tapsIdxMax, sizeof (tapsIdxMax)) ;
	   assert(bRet);

	   tapIdx = tapsIdxMin;

	   while (bRet && (tapIdx <= tapsIdxMax))
	   {

		  ostringstream txt;
		  uint32_t taps(0);

		  char Msg [80];
		  sprintf (Msg, "Getting taps value - Index (%u)", tapIdx);
		  LogMessage(Msg);

		  bRet = camHandle_->SetParameter(TSI_PARAM_TAPS_INDEX, tapIdx);
		  if (bRet)
		  {
			  bRet = camHandle_->GetParameter(TSI_PARAM_TAPS_VALUE, sizeof(uint32_t), &taps);
			  if (bRet)
			  {
				 txt << taps;
				 tapValues.push_back(txt.str().c_str());
				 tapIdx++;
			  }
			  else
			  {
				 LogMessage("Error getting tap value");
				 break;
			  }
		  }
		  else 
		  {
			 LogMessage("Error setting tap index");
		  }

	   }

	   if (tapValues.size() > 0)
	   {
		  pAct = new CPropertyAction (this, &TsiCam::OnTaps);
		  ret = CreateProperty(g_NumberOfTaps, tapValues[0].c_str(), MM::String, false, pAct);
		  assert(ret == DEVICE_OK);
		  for (size_t i=0; i<tapValues.size(); i++)
		  {
			 AddAllowedValue(g_NumberOfTaps, tapValues[i].c_str(), (long)i);
		  }
		  bRet = camHandle_->SetParameter(TSI_PARAM_TAPS_INDEX, tapIdxOrg);
		  assert(bRet);
	   }
   }

   // check color capabilities
   bayerMask = false;
   char colorFiltertype[32] = {0};
   bRet = camHandle_->GetParameter (TSI_PARAM_COLOR_FILTER_TYPE, 32, &colorFiltertype);
   if (bRet && (strlen (colorFiltertype) != 0) && (strcmp (colorFiltertype, "mono") != 0))
      bayerMask = true;
   else
      bayerMask = false;

   CreateProperty(g_ColorFilterArray, strlen(colorFiltertype) == 0 ? g_No : colorFiltertype, MM::String, true);

   if (bayerMask)
   {
      color = true;
      // create color select property
      pAct = new CPropertyAction(this, &TsiCam::OnColorEnable);
      CreateProperty(g_ColorEnable, g_On, MM::String, false, pAct);
      AddAllowedValue(g_ColorEnable, g_On);
      AddAllowedValue(g_ColorEnable, g_Off);

      //configure color processing
      ConfigureDefaultColorPipeline();

      // create white balance property
      pAct = new CPropertyAction(this, &TsiCam::OnWhiteBalance);
      ret = CreateProperty(g_WhiteBalance, g_Off, MM::String, false, pAct);
      AddAllowedValue(g_WhiteBalance, g_Off);
      AddAllowedValue(g_WhiteBalance, g_Set);
      AddAllowedValue(g_WhiteBalance, g_On);

   } else {
      // we can't support color if no bayer mask
      color = false;
   }

   // binning
   pAct = new CPropertyAction (this, &TsiCam::OnBinning);
   ret = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct);
   assert(ret == DEVICE_OK);

   vector<string> binValues;
   binValues.push_back("1");
   binValues.push_back("2");
   binValues.push_back("4");
   binValues.push_back("8");
  
   ret = SetAllowedValues(MM::g_Keyword_Binning, binValues);
   assert(ret == DEVICE_OK);

   // create Trigger mode property
   pAct = new CPropertyAction(this, &TsiCam::OnTriggerMode);
   trigger = Software;
   ret = CreateProperty(g_TriggerMode, g_Software, MM::String, false, pAct);
   AddAllowedValue(g_TriggerMode, g_Software, (long)Software);
   AddAllowedValue(g_TriggerMode, g_HardwareEdge, (long)HardwareEdge);
   AddAllowedValue(g_TriggerMode, g_HardwareDuration, (long)HardwareDuration);

   // create Trigger polarity
   pAct = new CPropertyAction(this, &TsiCam::OnTriggerPolarity);
   triggerPolarity = Positive;
   ret = CreateProperty(g_TriggerPolarity, g_Positive, MM::String, false, pAct);
   AddAllowedValue(g_TriggerPolarity, g_Positive, (long) Positive);
   AddAllowedValue(g_TriggerPolarity, g_Negative, (long) Negative);

   ret = ResizeImageBuffer();
   if (ret != DEVICE_OK)
      return ret;

   initialized = true;
   return DEVICE_OK;
}

int TsiCam::Shutdown()
{
   if (!initialized)
      return DEVICE_OK;

   if (IsCapturing())
      StopSequenceAcquisition();

   StopCamera();

   bool bret = camHandle_->Close();
   if (!bret)
      LogMessage("TSI Camera close failed!");

   bret = tsiSdk->Close();
   if (!bret)
      LogMessage("TSI SDK close failed!");

   // release the library
   if(g_tsiDllHandle != 0)
   {
      TSI_DESTROY_SDK tsi_destroy_sdk = (TSI_DESTROY_SDK)GetProcAddress(g_tsiDllHandle, "tsi_destroy_sdk");
      if(tsi_destroy_sdk != 0)
      {
         if (TsiCam::tsiSdk != 0)
         {
            tsi_destroy_sdk(TsiCam::tsiSdk);
            TsiCam::tsiSdk = 0;
         }
      }
      FreeLibrary(g_tsiDllHandle);
      g_tsiDllHandle = 0;
   }

   initialized = false;
   return DEVICE_OK;
}

bool TsiCam::Busy()
{
   return false;
}

long TsiCam::GetImageBufferSize() const
{
   if (color)
      return colorImg.Height() * colorImg.Width() * GetImageBytesPerPixel();
   else
      return img.Width() * img.Height() * GetImageBytesPerPixel();
}


/**
 * Access single image buffer 
 */
const unsigned char* TsiCam::GetImageBuffer()
{
   void* pixBuf(0);
   if (color)
      pixBuf = const_cast<unsigned char*> (colorImg.GetPixels());
   else
      pixBuf = const_cast<unsigned char*> (img.GetPixels());
   
   return (unsigned char*) pixBuf;
}

const unsigned char* TsiCam::GetImageBuffer(unsigned /* chNum */)
{
   // TODO: multichannel
   return GetImageBuffer();
}

const unsigned int* TsiCam::GetImageBufferAsRGB32()
{
   void* pixBuffer = const_cast<unsigned char*> (colorImg.GetPixels());
   return (unsigned int*) pixBuffer;
}
unsigned TsiCam::GetNumberOfComponents() const
{
   // TODO: multichannel
   if (color)
      return 4;
   else
      return 1;
}

unsigned TsiCam::GetNumberOfChannels() const
{
   // TODO: multichannel
   return 1;
}

int TsiCam::GetChannelName(unsigned channel, char* name)
{
   // TODO: multichannel

   if (channel != 0)
      return ERR_INVALID_CHANNEL_INDEX;
   
   strncpy(name, "Channel-0", MM::MaxStrLength);
   return DEVICE_OK;
}

/**
 * Snaps a single image, blocks at least until exposure is finished 
 */
int TsiCam::SnapImage()
{
   camHandle_->SetParameter(TSI_PARAM_FRAME_COUNT, 0);
   StartCamera();

   if (color)
   {
      MM::MMTime start = GetCurrentMMTime();
      MM::MMTime timeout(4000000); // 4 sec timeout
      int err(TSI_NO_ERROR);

      bool computeWhiteBalanceCoefficients = false;

      if (InterlockedCompareExchange(&whiteBalanceSelected, 0, 1))
      {     
         ConfigureWhiteBalanceColorPipeline();
         computeWhiteBalanceCoefficients = true;
      }

      TsiColorImage* tsiColorImg = 0;
      do
      {
         tsiColorImg = getColorCamera()->GetPendingColorImage(TSI_COLOR_POST_PROCESS);
         err = camHandle_->GetErrorCode();
      } while (tsiColorImg == 0 && GetCurrentMMTime() - start < timeout && err == TSI_NO_ERROR);

      if (err != TSI_NO_ERROR)
         return err;

      if (tsiColorImg == 0)
         return ERR_IMAGE_TIMED_OUT;

      if (computeWhiteBalanceCoefficients)
      {
         BuildWhiteBalancedPipeline(*tsiColorImg, getColorCamera());
         getColorCamera()->FreeColorImage(tsiColorImg);
         tsiColorImg = 0;
         start = GetCurrentMMTime();
         do
         {
            tsiColorImg = getColorCamera()->GetPendingColorImage(TSI_COLOR_POST_PROCESS);
            err = camHandle_->GetErrorCode();
         } while (tsiColorImg == 0 && GetCurrentMMTime() - start < timeout && err == TSI_NO_ERROR);
      }

      // adjust image size
      if (colorImg.Width() != tsiColorImg->m_Width || colorImg.Height() != tsiColorImg->m_Height || colorImg.Depth() != 4)
         colorImg.Resize(tsiColorImg->m_Width, tsiColorImg->m_Height, 4);

      colorImg.SetPixels(tsiColorImg->m_ColorPixelDataBGR.ui8);

      getColorCamera()->FreeColorImage(tsiColorImg);
   }
   else
   {
      // grayscale image snap
      MM::MMTime start = GetCurrentMMTime();
      MM::MMTime timeout(4, 0); // 4 sec timeout
      TsiImage* tsiImg = 0;
      do
      {
         tsiImg = camHandle_->GetPendingImage();
      } while (tsiImg == 0 && GetCurrentMMTime() - start < timeout);

      if (tsiImg == 0)
         return ERR_IMAGE_TIMED_OUT;

      // adjust image size
      if (img.Width() != tsiImg->m_Width || img.Height() != tsiImg->m_Height || img.Depth() != tsiImg->m_BytesPerPixel)
         img.Resize(tsiImg->m_Width, tsiImg->m_Height, tsiImg->m_BytesPerPixel);

      img.SetPixels(tsiImg->m_PixelData.vptr);
      camHandle_->FreeImage(tsiImg);
    }

   StopCamera();

   return DEVICE_OK;
}

unsigned TsiCam::GetBitDepth() const
{
   if (color)
      return 8; // 8-bit rgb
   else
      return bitDepth;
 }

int TsiCam::GetBinning() const
{
bool success;
int  xbin;

   success = camHandle_->GetParameter(TSI_PARAM_XBIN, sizeof(uint32_t), (void*)&xbin);
   if(success == false)
   {
      xbin = 1;
   }

   return xbin;
}

int TsiCam::SetBinning(int binSize)
{
   printf("[MicroManager] TsiCam::SetBinning(binSize:%d)\n", binSize);
   ostringstream os;                                                         
   os << binSize;
   return SetProperty(MM::g_Keyword_Binning, os.str().c_str());                                                                                     
}

double TsiCam::GetExposure() const
{
   uint32_t exp(0);
   (void)camHandle_->GetParameter(TSI_PARAM_ACTUAL_EXPOSURE_TIME, sizeof(uint32_t), (void*)&exp);
   return (double)exp / 1000.0; // exposure is expressed always in ms
}

void TsiCam::SetExposure(double dExpMs)
{
   uint32_t exp = (uint32_t)(dExpMs + 0.5);
   (void)camHandle_->SetParameter(TSI_PARAM_EXPOSURE_TIME, (void*)&exp);
}

int TsiCam::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
   roiBinData.XPixels = xSize * roiBinData.XBin;
   roiBinData.YPixels = ySize * roiBinData.YBin;
   roiBinData.XOrigin = x * roiBinData.XBin;
   roiBinData.YOrigin = y * roiBinData.XBin;
   
   return ResizeImageBuffer(roiBinData);
}

int TsiCam::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
   x = roiBinData.XOrigin / roiBinData.XBin;
   y = roiBinData.YOrigin / roiBinData.YBin;
   xSize = roiBinData.XPixels / roiBinData.XBin;
   ySize = roiBinData.YPixels / roiBinData.YBin;

   return DEVICE_OK;
}

int TsiCam::ClearROI()
{
   // reset roi
   roiBinData.XOrigin = 0;
   roiBinData.YOrigin = 0;
   roiBinData.XPixels = fullFrame.XPixels;
   roiBinData.YPixels = fullFrame.YPixels;

   return ResizeImageBuffer(roiBinData);
}

int TsiCam::PrepareSequenceAcqusition()
{
   if (IsCapturing())
   {
      return DEVICE_CAMERA_BUSY_ACQUIRING;
   }

   int ret = GetCoreCallback()->PrepareForAcq(this);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int TsiCam::StartSequenceAcquisition(long numImages, double /*interval_ms*/, bool stopOnOvl)
{
   if (IsCapturing())
   {
      printf("[MicroManager] - TsiCam::StartSequenceAcquisition() - Camera is acquiring images returning\n");
      return DEVICE_CAMERA_BUSY_ACQUIRING;
   }

   // the camera ignores interval, running at the rate dictated by the exposure
   stopOnOverflow = stopOnOvl;
   printf("[MicroManager] - TsiCam::StartSequenceAcquisition() - numImages %d - calling Start() stopOnOverflow:%d\n", numImages, stopOnOverflow);
   liveAcqThd_->SetNumFrames(numImages); // continuous
   liveAcqThd_->Start();
   // TSI cameras can be too fast in starting-up acquisition
   // give micro-manager a chance to set itself up
   CDeviceUtils::SleepMs(50);

   return DEVICE_OK;
}

int TsiCam::StartSequenceAcquisition(double /*interval_ms*/)
{
   if (IsCapturing())
      return DEVICE_CAMERA_BUSY_ACQUIRING;

   // the camera ignores interval, running at the rate dictated by the exposure
   stopOnOverflow = false;
   printf("[MicroManager] - TsiCam::StartSequenceAcquisition() - numImages 0 - calling Start() stopOnOverflow:false\n");

   liveAcqThd_->SetNumFrames(0); // continuous
   liveAcqThd_->Start();

   return DEVICE_OK;
}

int TsiCam::StopSequenceAcquisition()
{
   printf("[MicroManager] - TsiCam::StopSequenceAcquisition()\n");

   liveAcqThd_->Stop();
   liveAcqThd_->wait();
   //CDeviceUtils::SleepMs(50); 
   return DEVICE_OK;
}

bool TsiCam::IsCapturing()
{
   return acquiring == 1;
}

///////////////////////////////////////////////////////////////////////////////
// Private utility functions

int TsiCam::ResizeImageBuffer()
{
   uint32_t width(0), height(0);
   if (!camHandle_->GetParameter(TSI_PARAM_HSIZE, sizeof(uint32_t), (void*)&width))
      return camHandle_->GetErrorCode();

   if (!camHandle_->GetParameter(TSI_PARAM_VSIZE, sizeof(uint32_t), (void*)&height))
      return camHandle_->GetErrorCode();

   unsigned int bitpix   = 0;
   if (camHandle_->GetParameter(TSI_PARAM_BITS_PER_PIXEL, sizeof(uint32_t), (void*)&bitpix))
   bitDepth = bitpix;

   // set full frame
   roiBinData.XBin    = 1;
   roiBinData.YBin    = 1;
   roiBinData.XOrigin = 0;
   roiBinData.YOrigin = 0;
   roiBinData.XPixels = width;
   roiBinData.YPixels = height;
   // TT 11-5-2013: This was originally GetParameter() - not sure if bug and meant to be SetParam, or not.
   //if (!camHandle_->SetParameter(TSI_PARAM_ROI_BIN, sizeof(uint32_t), (void*)&roiBinData))
   if (!camHandle_->SetParameter(TSI_PARAM_ROI_BIN, (void*)&roiBinData))
   {
      printf("[MicroManager] TsiCam::ResizeImageBuffer() w:%d h:%d xbin:%d ybin:%d - FAILED **********\n", roiBinData.XPixels, roiBinData.YPixels, roiBinData.XBin, roiBinData.YBin);
      return camHandle_->GetErrorCode();
   }

   img.Resize(width, height, 2);
   colorImg.Resize(width, height, 4);

   fullFrame = roiBinData; // save full frame info

   return DEVICE_OK;
}

int TsiCam::ResizeImageBuffer(TSI_ROI_BIN& roiBin)
{
   const int byteDepth = color ? 4 : 2;

   printf("[MicroManager] TsiCam::ResizeImageBuffer(TSI_ROI_BIN& roiBin) w:%d h:%d xbin:%d ybin:%d\n", roiBinData.XPixels, roiBinData.YPixels, roiBinData.XBin, roiBinData.YBin);

   bool bret = camHandle_->SetParameter(TSI_PARAM_ROI_BIN, (void*)&roiBin);
   if (!bret)
   {
      // roi-bin failed so return to full frame
      TSI_ERROR_CODE errCode = camHandle_->GetErrorCode();
      ResizeImageBuffer();
      return errCode;
   }

   // verify settings
   if (!camHandle_->GetParameter(TSI_PARAM_ROI_BIN, sizeof(uint32_t), (void*)&roiBinData))
      return camHandle_->GetErrorCode();

   img.Resize(roiBinData.XPixels / roiBinData.XBin, roiBinData.YPixels / roiBinData.YBin, byteDepth);
   colorImg.Resize(roiBinData.XPixels / roiBinData.XBin, roiBinData.YPixels / roiBinData.YBin, byteDepth);
   ostringstream os;
   os << "TSI resized to: " << img.Width() << " X " << img.Height() << ", bin factor: "
      << roiBinData.XBin;
   LogMessage(os.str().c_str());

   printf("[MicroManager] TsiCam::ResizeImageBuffer() image resized to w:%d h:%d\n", img.Width(), img.Height());

   return DEVICE_OK;
}

void TsiCam::ReadoutComplete(int /*callback_type_id*/, TsiImage *tsiImg, void *context)
{
   //assert(callback_type_id == TSI_CALLBACK_CAMERA_FRAME_READOUT_COMPLETE);
   TsiCam* cam = static_cast<TsiCam*>(context);
   if (cam->color)
   {
      TsiColorImage* tsiCImg = dynamic_cast<TsiColorImage*>(tsiImg);
      assert(tsiCImg);
      if (cam->colorImg.Width() == tsiCImg->m_Width || cam->colorImg.Height() == tsiCImg->m_Height || cam->colorImg.Depth() == tsiCImg->m_BytesPerPixel)
         cam->PushImage(reinterpret_cast<unsigned char*>(tsiCImg->m_PixelData.vptr));
      else
         assert(!"color image dimensions do not match");
   }
   else
   {
      if (cam->img.Width() == tsiImg->m_Width || cam->img.Height() == tsiImg->m_Height || cam->img.Depth() == tsiImg->m_BytesPerPixel)
         cam->PushImage(reinterpret_cast<unsigned char*>(tsiImg->m_PixelData.vptr));
      else
         assert(!"live image dimensions do not match");
   }
}

int TsiCam::PushImage(unsigned char* imgBuf)
{
   if (color)
   {
      int retCode = GetCoreCallback()->InsertImage(this,
         imgBuf,
         colorImg.Width(),
         colorImg.Height(),
         colorImg.Depth());

      if (!stopOnOverflow && retCode == DEVICE_BUFFER_OVERFLOW)
      {
         // do not stop on overflow - just reset the buffer
         GetCoreCallback()->ClearImageBuffer(this);
         retCode = GetCoreCallback()->InsertImage(this,
            imgBuf,
            colorImg.Width(),
            colorImg.Height(),
            colorImg.Depth());
      }
   }
   else
   {
      int retCode = GetCoreCallback()->InsertImage(this,
         imgBuf,
         img.Width(),
         img.Height(),
         img.Depth());

      if (!stopOnOverflow && retCode == DEVICE_BUFFER_OVERFLOW)
      {
         // do not stop on overflow - just reset the buffer
         GetCoreCallback()->ClearImageBuffer(this);
         retCode = GetCoreCallback()->InsertImage(this,
            imgBuf,
            img.Width(),
            img.Height(),
            img.Depth());
      }
   }

   return DEVICE_OK;
}

int TsiCam::InsertImage()
{
   int retCode = GetCoreCallback()->InsertImage(this,
         img.GetPixels(),
         img.Width(),
         img.Height(),
         img.Depth());

   if (!stopOnOverflow)
   {
      if (retCode == DEVICE_BUFFER_OVERFLOW)
      {
         // do not stop on overflow - just reset the buffer
         GetCoreCallback()->ClearImageBuffer(this);
         retCode = GetCoreCallback()->InsertImage(this,
            img.GetPixels(),
            img.Width(),
            img.Height(),
            img.Depth());
         return DEVICE_OK;
      }
      else
         return retCode;
   }

   return retCode;
}

bool TsiCam::GetAttrValue(TSI_PARAM_ID ParamID, TSI_ATTR_ID AttrID, void *Data, uint32_t DataLength) 
{

	bool return_value = false;

	if (camHandle_) {

		TSI_PARAM_ATTR_ID	ParamAttrID;

		ParamAttrID.ParamID = ParamID;
		ParamAttrID.AttrID  = AttrID;

		if (camHandle_->SetParameter(TSI_PARAM_CMD_ID_ATTR_ID, &ParamAttrID)) {
			if (camHandle_->GetParameter(TSI_PARAM_ATTR, DataLength, Data)) {
				return_value = true;
			} else {
				LogMessage("Could not get parameter to acquire parameter attribute");
	 		}
		} else {
			LogMessage("Could not set parameter to acquire parameter attribute");
		}

	} else {
		LogMessage("Invalid Camera Handle");
	}

	return return_value;

}

bool TsiCam::ParamSupported (TSI_PARAM_ID ParamID)
{

	bool return_value = false;


	if (camHandle_) {

		TSI_PARAM_ATTR_ID	ParamAttrID;
		uint32_t			Flags;

		ParamAttrID.ParamID = ParamID;

		// Get the parameter max

		ParamAttrID.AttrID  = TSI_ATTR_FLAGS;	
		if (camHandle_->SetParameter(TSI_PARAM_CMD_ID_ATTR_ID, &ParamAttrID)) {
			if (camHandle_->GetParameter(TSI_PARAM_ATTR, sizeof(Flags), &Flags)) {
				return_value = ((Flags & TSI_FLAG_UNSUPPORTED) == 0);
	 		}
		}

	}

	return return_value;

}

bool TsiCam::StopCamera()
{
   if (trigger == Software)
      return camHandle_->Stop();
   else
      return camHandle_->StopTriggerAcquisition(false);
}

bool TsiCam::StartCamera()
{
   if (trigger == Software)
      return camHandle_->Start();
   else
      return camHandle_->StartTriggerAcquisition();
}

int AcqSequenceThread::svc (void)
{
   bool rb = camInstance->camHandle_->SetParameter(TSI_PARAM_FRAME_COUNT, numFrames <= 0 ? 0 : numFrames);
   InterlockedExchange(&camInstance->acquiring, 1);
   rb = camInstance->StartCamera();
   assert(rb);

   unsigned count = 0;
   while(!stop)
   {   
      MM::MMTime start = camInstance->GetCurrentMMTime();
      MM::MMTime timeout(4, 0); // 2 seconds, 0 micro seconds
      
      if (camInstance->color)
      {
         TsiColorImage* tsiCImg(0);
         int err = TSI_NO_ERROR;

         bool determineWhiteBalanceCoefficients = false;

         if (InterlockedCompareExchange(&camInstance->whiteBalanceSelected, 0, 1))
         {
            camInstance->ConfigureWhiteBalanceColorPipeline();
            determineWhiteBalanceCoefficients = true;
         }

         while (!stop && (camInstance->GetCurrentMMTime() - start) < timeout && !tsiCImg && err == TSI_NO_ERROR)
         {
            tsiCImg = camInstance->getColorCamera()->GetPendingColorImage(TSI_COLOR_POST_PROCESS);
            Sleep(1);
         }

         if (!tsiCImg || err != TSI_NO_ERROR)
         {
            if (err != TSI_NO_ERROR)
               camInstance->LogMessage("Error acquiring image.");
            else
               camInstance->LogMessage("Camera timed out on GetPendingColorImage().");

            if (determineWhiteBalanceCoefficients)
            {
               camInstance->ConfigureDefaultColorPipeline();
            }

            camInstance->StopCamera();
            InterlockedExchange(&camInstance->acquiring, 0);
            return 1;
         }
      
         if (determineWhiteBalanceCoefficients)
         {
            BuildWhiteBalancedPipeline(*tsiCImg, camInstance->getColorCamera());
            camInstance->getColorCamera()->FreeColorImage(tsiCImg);
            continue;
         }

         camInstance->colorImg.SetPixels(tsiCImg->m_ColorPixelDataBGR.ui8);
   
         if (camInstance->colorImg.Width() == tsiCImg->m_Width || camInstance->colorImg.Height() == tsiCImg->m_Height || camInstance->colorImg.Depth() == 4)
         {
            camInstance->PushImage(reinterpret_cast<unsigned char*>(camInstance->colorImg.GetPixelsRW()));
            camInstance->getColorCamera()->FreeColorImage(tsiCImg);
            camInstance->LogMessage("Acquired image.");
            count++;
            if (numFrames > 0 && count == numFrames)
            {
               printf("[MicroManager] - AcqSequenceThread::svc() - numFrames:%d count:%d - calling Stop()\n", numFrames, count);
               camInstance->LogMessage("Number of frames reached: exiting.");
               camInstance->getColorCamera()->Stop();
               InterlockedExchange(&camInstance->acquiring, 0);
               return 0;
            }
         }
         else
         {
            camInstance->LogMessage("Error: image dimensions do not match.");
            camInstance->getColorCamera()->FreeColorImage(tsiCImg);
            printf("[MicroManager] - AcqSequenceThread::svc() - image dimensions don't match calling stop\n");
            printf("[MicroManager] - Expected image size w:%5d h:%5d bytes_per_pixel:%5d\n", camInstance->img.Width(), camInstance->img.Height(), camInstance->img.Depth());
            printf("[MicroManager] - Actual   image size w:%5d h:%5d bytes_per_pixel:%5d\n", tsiCImg->m_Width, tsiCImg->m_Height, tsiCImg->m_BytesPerPixel);
   
            camInstance->StopCamera();
            InterlockedExchange(&camInstance->acquiring, 0);
            return 1;
         }
      }
      else
      {
         TsiImage* tsiImg(0);
         
         while (!stop && (camInstance->GetCurrentMMTime() - start) < timeout && !tsiImg)
         {
            tsiImg = camInstance->getColorCamera()->GetPendingImage();
            Sleep(1);
         }

         if (!tsiImg)
         {
            camInstance->LogMessage("Camera timed out on GetPendingImage().");
            camInstance->StopCamera();
            InterlockedExchange(&camInstance->acquiring, 0);
            return 1;
         }

         if (camInstance->img.Width() == tsiImg->m_Width || camInstance->img.Height() == tsiImg->m_Height || camInstance->img.Depth() == tsiImg->m_BytesPerPixel)           
         {
            camInstance->PushImage(reinterpret_cast<unsigned char*>(tsiImg->m_PixelData.vptr));
            camInstance->camHandle_->FreeImage(tsiImg);
            camInstance->LogMessage("Acquired image.");
            count++;
            if (numFrames > 0 && count == numFrames)
            {
               printf("[MicroManager] - AcqSequenceThread::svc() - numFrames:%d count:%d - calling Stop()\n", numFrames, count);
               camInstance->LogMessage("Number of frames reached: exiting.");
               camInstance->StopCamera();
               InterlockedExchange(&camInstance->acquiring, 0);
               return 0;
            }
         }
         else
         {
            camInstance->LogMessage("Error: image dimensions do not match.");
            camInstance->camHandle_->FreeImage(tsiImg);
            printf("[MicroManager] - AcqSequenceThread::svc() - image dimensions don't match calling stop\n");
            printf("[MicroManager] - Expected image size w:%5d h:%5d bytes_per_pixel:%5d\n", camInstance->img.Width(), camInstance->img.Height(), camInstance->img.Depth());
            printf("[MicroManager] - Actual   image size w:%5d h:%5d bytes_per_pixel:%5d\n", tsiImg->m_Width, tsiImg->m_Height, tsiImg->m_BytesPerPixel);
  
            camInstance->StopCamera();
            InterlockedExchange(&camInstance->acquiring, 0);
            return 1;
         }
      }
      
      if (numFrames > 0 && count >= numFrames)
      {
         camInstance->LogMessage("Number of frames reached.");
         camInstance->StopCamera();
         InterlockedExchange(&camInstance->acquiring, 0);
      }
   }

   camInstance->LogMessage("User pressed stop.");
   camInstance->StopCamera();
   InterlockedExchange(&camInstance->acquiring, 0);
   return 0;
}

TsiColorCamera* TsiCam::getColorCamera()
{
    return static_cast<TsiColorCamera*> (camHandle_);
}

void TsiCam::convertToRGBA32(TsiColorImage& tsiImg, ImgBuffer& img, int colorBitDepth)
{
   // adjust image size
   if (img.Width() != tsiImg.m_Width || img.Height() != tsiImg.m_Height || img.Depth() != 4)
         img.Resize(tsiImg.m_Width, tsiImg.m_Height, 4);

   int shift = max(0, colorBitDepth - 8);

   // convert from 16 to 8 bit
   for (unsigned i=0; i<img.Height(); i++)
      for (unsigned j=0; j<img.Width(); j++)
      {
         int offset = img.Width()*i*4 + j*4;
         int tsiIdx = i*tsiImg.m_Width + j;
         *(img.GetPixelsRW() + offset ) = (unsigned char)(tsiImg.m_ColorPixelDataBGR.BGR_16[tsiIdx].b >> shift);
         *(img.GetPixelsRW() + offset + 1) = (unsigned char)(tsiImg.m_ColorPixelDataBGR.BGR_16[tsiIdx].g >> shift);
         *(img.GetPixelsRW() + offset + 2) = (unsigned char)(tsiImg.m_ColorPixelDataBGR.BGR_16[tsiIdx].r >> shift);
         *(img.GetPixelsRW() + offset + 3) = 0;
      }
}

int TsiCam::SetWhiteBalance()
{
   if (!color)
      return DEVICE_OK;

   InterlockedExchange(&whiteBalanceSelected, 1);
   wb = true;

   return DEVICE_OK;
}

void TsiCam::ClearWhiteBalance()
{
   ConfigureDefaultColorPipeline();
   wb = false;
}

void TsiCam::ConfigureDefaultColorPipeline()
{
   getColorCamera()->ClearColorPipeline();
   getColorCamera()->ConcatenateColorTransform(TSI_Default_White_Balance, 0);
   getColorCamera()->ConcatenateColorTransform(TSI_Camera_Color_Correction, 0);
   getColorCamera()->ConcatenateColorTransform(TSI_sRGB8_32, 0);
   getColorCamera()->FinalizeColorPipeline();
}

void TsiCam::ConfigureWhiteBalanceColorPipeline()
{
   // rebuild pipeline for linear transform
   getColorCamera()->ClearColorPipeline();
   // Don't concatenate the default white balance matrix here since we want straight linear RGB
   // for the purpose of image analysis.
   getColorCamera()->ConcatenateColorTransform(TSI_Camera_Color_Correction, 0);
   getColorCamera()->ConcatenateColorTransform(TSI_RGB_Linear, 0);
   getColorCamera()->FinalizeColorPipeline();
}


