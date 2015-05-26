///////////////////////////////////////////////////////////////////////////////
// FILE:          BaumerOptronic.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   See BaumerOptronic.h
//
// AUTHOR:        Karl Hoover
//                Made to work again by Nico Stuurman, Feb. 2012, copyright UCSF
//
// COPYRIGHT:     University of California, San Francisco, 2010

// LICENSE:
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

/*
Notes from Nico Stuurman who is trying to understand how this code is supposed to work. (feb. 2012).

Apart from the device adapter itself. there is a "WorkerThread" named
BOImplementationThread. This BOImplementationThread handles all communication with the camera.

The svc function of BOImplementationThread checks the value of flag cameraState_ and takes action
accordingly. Thus, the adapter communicates with the ImplementationThread by setting the state of
flag cameraState_.

On startup, the function BOImplementationThread::BOInitializationSequence() is called. This function
starts the thread mSeqEventHandler, which coninuously checks the camera for new data.
When there is a new image, this thread will acquire the pixels through the call FX_GetImageData.
Pixel data are stored in a global buffer pointed to by pStatic_g, lots of image size are also
stored in globals.

Since this function is a global, using this adapter with multiple cameras is not possible.

--

Further notes from Mark Tsuchida, Sep 2013.

To read this code, start by searching for calls to functions named FX_*. These
are the Baumer library calls. Work back from there to figure out how things happen.

The most central ones are FX_StartDataCapture() and FX_GetImageData().

It would be nice if we could figure out why a spearate thread is used for
everything, and especially why the decision was made to keep the capture
constantly running instead of starting and stopping when acquiring snaps or
sequences.

If any _major_ fix becomes necessary, I'd suggest starting from scratch, as
there is too much uncommented magic in this code.

*/


#include "BaumerOptronic.h"

#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/MMDeviceConstants.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <sstream>
#include <string>

#include <boost/lexical_cast.hpp>

#include <process.h>

// Disable warning for unused BOMSG(), defined in BoMsg.h (via FxApi.h).
// This warning is generated at the end of compilation, so cannot be disabled within push/pop.
#pragma warning(disable: 4505)

using namespace std;


const double CBaumerOptronic::nominalPixelSizeUm_ = 1.0;

// External names used by the rest of the system
const char* g_CameraDeviceName = "BaumerOptronic";

// constants for naming pixel types (allowed values of the "PixelType" property)
const char* g_PixelType_8bit = "8bit";
const char* g_PixelType_16bit = "16bit";
const char* g_PixelType_32bitRGB = "32bitRGB";
const char* g_PixelType_64bitRGB = "64bitRGB";

int gCameraId[16] = { -1 }; // max 16 cameras

// this appears to be used as a global pointer to memory used to deposit pixel data from the camera
void* pStatic_g = NULL; // need a static pointer for the non-member event handler function -- todo class static
// global used in the event handler to get the image size
unsigned long staticImgSize_g = 0; // ditto here

MMThreadLock acquisitionThreadTerminateLock_g;
bool mTerminateFlag_g = false;

bool imageReady_g = false; // (guarded by imageReadyLock_s)

// globals used to store data as reported by the camera
unsigned xDim_g;
unsigned yDim_g;
int bitsInOneColor_g;

inline int BytesInOneComponent(int bitsInOneColor)
{
   return (bitsInOneColor + 7) / 8;
}


// BO library sends out two possible RGB formats, both with code BOIMF_RGB
// if planes = 3 the R, G, and B images are sent out successively
// if 'canals' = 3, the RGB pixels are interleaved.
int nPlanes_g;
int nCanals_g;
eBOIMGCODEINF iCode_g;


// turn acquisition on & off
MMThreadLock seqActiveLock_g;
bool seqactive_g = false;


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_CameraDeviceName, MM::CameraDevice, "Baumer Optronic driver for Leica Cameras");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
   {
      return 0;
   }

   // decide which device class to create based on the deviceName parameter
   if (strcmp(deviceName, g_CameraDeviceName) == 0)
   {
      // create camera
      return new CBaumerOptronic();
   }

   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// CBaumerOptronic implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~


// this runs on the thread that waits for images to be ready

// forward declaration
unsigned int __stdcall mSeqEventHandler(void*);
unsigned long imageTimeoutMs_g = 5000; // milliseconds to wait for image ready event


BOImplementationThread::BOImplementationThread(CBaumerOptronic* pCamera) :
   intervalMs_(100.0),
   numImages_(1),
   acquisitionThread_(NULL),
   imageNotificationEvent_(0),
   exposureUs_(0),
   quantizedGain_(0.),
   bitsInOneColor_(0),
   nPlanes_(0),
   xDim_(0),
   yDim_(0),
   pBuf_(NULL),
   bufSize_(0),
   pColorBuf_(NULL),
   colorBufSize_(0),
   triggerMode_(false),
   command_(Noop),
   stopCameraAfterFirstImage_(false),
   cameraState_(Idle),
   pCamera_(pCamera),
   partialScanMode_(false)
{
   memset(&roi_,0,sizeof(RECT));
}


BOImplementationThread::~BOImplementationThread()
{
   bufSize_ = 0;
   if (NULL != pBuf_)
   {
      free(pBuf_);
   }

   colorBufSize_ = 0;
   if (NULL != pColorBuf_)
   {
      free(pColorBuf_);
   }

   if (NULL != imageNotificationEvent_)
   {
      ::CloseHandle(imageNotificationEvent_);
   }
   mTerminateFlag_g = false;
}

void BOImplementationThread::TriggerMode(const bool v)
{
   MMThreadGuard g(stateMachineLock_);
   WorkerState prevState(cameraState_);
   cameraState_ = Busy;
   triggerMode_ = v;
   (void)FX_SetTriggerMode(gCameraId[0], triggerMode_, NULL);
   cameraState_ = prevState;
}

void BOImplementationThread::ExposureUs(int v)
{
   MMThreadGuard g(stateMachineLock_);
   WorkerState prevState(cameraState_);
   cameraState_ = Busy;
   tEXPO m_Expo;
   int fxr = FX_SetExposureTime(gCameraId[0], v, &m_Expo);
   if (CE_SUCCESS != fxr)
   {
      LLogMessage(GetSDKErrorMessage(fxr));
   }
   else
   {
      exposureUs_ = m_Expo.timeNear;
      LLogMessage(std::string("exposure set to ") +
            boost::lexical_cast<std::string>(exposureUs_), true);
   }
   cameraState_ = prevState;
}

void BOImplementationThread::Gain(double v)
{
   MMThreadGuard g(stateMachineLock_);
   WorkerState prevState(cameraState_);
   cameraState_ = Busy;
   tGAIN m_Gain;
   (void)FX_SetGainFactor(gCameraId[0], v, &m_Gain);
   quantizedGain_ = m_Gain.gainNear;
   cameraState_ = prevState;
}


MMThreadLock BOImplementationThread::imageReadyLock_s;
MMThreadLock BOImplementationThread::imageBufferLock_s;

void BOImplementationThread::Snap()
{
   if (Ready == CameraState())
   {
      {
         MMThreadGuard guard(BOImplementationThread::imageReadyLock_s);
         imageReady_g = false;
      }

      MMThreadGuard g(stateMachineLock_);
      WorkerState prevState = cameraState_;
      cameraState_ = Snapping;

      LLogMessage("FX_CamStart TRUE (Snap)", true);
      FX_CamStart(gCameraId[0], TRUE);
      stopCameraAfterFirstImage_ = true;

      tBoCameraType   dcBoType;               // Cameratype struct
      tBoCameraStatus dcBoStatus;             // Camerastatus struct

      dcBoType.iSizeof = sizeof(dcBoType);
      dcBoStatus.iSizeof = sizeof(dcBoStatus);
      FX_GetCameraInfo(gCameraId[0], &dcBoType, &dcBoStatus);

      unsigned long sz = dcBoStatus.iNumImgCodeBytes;

      {
         // lock access to the static buffer, buffersize
         MMThreadGuard g(BOImplementationThread::imageBufferLock_s);
         if (sz != bufSize_)
         {
            if (NULL != pBuf_)
            {
               free(pBuf_);
               pBuf_ = NULL;
               bufSize_ = 0;
            }
            // malloc is slightly faster than new
            pBuf_ = malloc(sz);
            if (NULL != pBuf_)
            {
               bufSize_ = sz;
            }
         }

         pStatic_g = pBuf_;
         staticImgSize_g = bufSize_;
      }


      if (0 < bufSize_)
      {
         MMThreadGuard g(seqActiveLock_g);
         seqactive_g = true;
      }
      cameraState_ = prevState;
   }

   return;
}
/**
 * What does this function do?
 * It looks as if it enquires with the camera what the size (in bytes)
 * of the image is, allocates a buffer and stores a pointer to the buffer
 * (pStatic_g and/or pBuf_) and its size (staticImgSize_g)
 * but if that is true, why was it called "Acquire"?
 *
 * -> It's probably named Acquire() because it sets seqactive_g to true, which,
 *  if I understand correctly, is a signal to grab the next available image.
 *  (Also compare the Snap() function, which ought to have been written to
 *  share code with this one.)
 */
void BOImplementationThread::Acquire()
{
   {
      MMThreadGuard guard(BOImplementationThread::imageReadyLock_s);
      imageReady_g = false;
   }

   tBoCameraType   dcBoType;               // Cameratype struct
   tBoCameraStatus dcBoStatus;             // Camerastatus struct

   dcBoType.iSizeof = sizeof(dcBoType);
   dcBoStatus.iSizeof = sizeof(dcBoStatus);
   FX_GetCameraInfo(gCameraId[0], &dcBoType, &dcBoStatus);

   unsigned long sz = dcBoStatus.iNumImgCodeBytes;

   {
      // lock access to the static buffer, buffersize
      MMThreadGuard g(BOImplementationThread::imageBufferLock_s);
      if (sz != bufSize_)
      {
         if (NULL != pBuf_)
         {
            free(pBuf_);
            pBuf_ = NULL;
            bufSize_ = 0;
         }
         // malloc is slightly faster than new
         pBuf_ = malloc(sz);
         if (NULL != pBuf_)
         {
            bufSize_ = sz;
         }
      }

      pStatic_g = pBuf_;
      staticImgSize_g = bufSize_;
   }

   if (0 < bufSize_)
   {
      MMThreadGuard g(seqActiveLock_g);
      seqactive_g = true;
   }

   return;
}


void* BOImplementationThread::CurrentImage(unsigned& xDim, unsigned& yDim,
      int& bitsInOneColor, int& nColors,
      unsigned long& bufSize, MMThreadGuard** ppImageBufferGuard)
{
   int nCanals;
   void* bufferToReturn = NULL;
   *ppImageBufferGuard = NULL;

   for (;;)
   {
      {
         MMThreadGuard g(seqActiveLock_g);
         if (seqactive_g)
         {
            break;
         }
      }
      CDeviceUtils::SleepMs(1);
   }

   unsigned long timeoutMs = 5000UL +
      2UL * static_cast<unsigned long>(ExposureUs()) / 1000UL;
   MM::TimeoutMs timerOut(CurrentMMTimeMM(), timeoutMs);
   for (;;) // Wait for the image to become ready
   {
      {
         MMThreadGuard guard(BOImplementationThread::imageReadyLock_s);
         if (imageReady_g)
         {
            break;
         }
      }

      if (timerOut.expired(CurrentMMTimeMM()))
      {
         LLogMessage("Timed out waiting for image to become ready");
         return NULL;
      }

      CDeviceUtils::SleepMs(1);
   }

   if (stopCameraAfterFirstImage_)
   {
      LLogMessage("FX_CamStart FALSE (after first image, since we are snapping)", true);
      FX_CamStart(gCameraId[0], FALSE);
      stopCameraAfterFirstImage_ = false;
   }

   {
      MMThreadGuard g(seqActiveLock_g);
      seqactive_g = false;
   }

   {
      MMThreadGuard g(BOImplementationThread::imageBufferLock_s);

      bufSize = bufSize_ = staticImgSize_g;
      xDim = xDim_ = xDim_g;
      yDim = yDim_ = yDim_g;
      bitsInOneColor = bitsInOneColor_ = bitsInOneColor_g;
      nPlanes_ = nPlanes_g;
      nCanals = nCanals_g;
   }

   // color images need to be dithered with an empty color for gui
   if (BOIMF_RGB == ImageCode().iCode)
   {
      nColors = 4;
      const unsigned long ditheredSize = xDim * yDim * BytesInOneComponent(bitsInOneColor) * nColors;
      {
         colorBufSize_ = 0;
         if (NULL != pColorBuf_)
         {
            free(pColorBuf_);
         }
         // malloc is slightly faster than new
         pColorBuf_ = malloc(ditheredSize);
         if (NULL != pColorBuf_)
         {
            colorBufSize_ = ditheredSize;
            bufSize = ditheredSize;
            bufferToReturn = pColorBuf_;
         }
         else
         {
            // TODO: handle error when no memory could be allocated
         }
      }
      if (0 != ditheredSize)
      {
         memset(pColorBuf_, 0, ditheredSize);
      }

      // start of access to image buffer
      *ppImageBufferGuard = new MMThreadGuard(BOImplementationThread::imageBufferLock_s);

      if (8 >= bitsInOneColor_) // pixels are 1 byte per color
      {
         char* pInput = static_cast<char*>(pBuf_);
         char* pOutput = static_cast<char*>(pColorBuf_);
         unsigned long nPix = xDim * yDim;
         unsigned long thePixel;
         if ((1 == nPlanes_) && (3 == nCanals)) // interleaved RGB
         {
            for (thePixel = 0; thePixel < nPix; ++thePixel)
            {
               *pOutput++ = *pInput++; // R
               *pOutput++ = *pInput++; // G
               *pOutput++ = *pInput++; // B
               ++pOutput;              // empty
            }
         }
         else if ((3 == nPlanes_) && (1 == nCanals)) // R plane G plane B plane
         {
            for (thePixel = 0; thePixel < nPix; ++thePixel)
            {
               *pOutput++ = pInput[thePixel]; // R
               *pOutput++ = pInput[thePixel + nPix];// G
               *pOutput++ = pInput[thePixel + 2*nPix];// B
               ++pOutput;             // empty
            }
         }
      }
      else // pixels are 2 bytes per color
      {
         short* pInput = static_cast<short*>(pBuf_);
         short* pOutput = static_cast<short*>(pColorBuf_);
         unsigned long nPix = xDim * yDim;
         unsigned long thePixel;
         if ((1 == nPlanes_) && (3 == nCanals)) // interleaved RGB
         {
            for (thePixel = 0; thePixel < nPix; ++thePixel)
            {
               *pOutput++ = *pInput++;// R
               *pOutput++ = *pInput++;// G
               *pOutput++ = *pInput++;// B
               ++pOutput;             // empty
            }
         }
         else if ((3 == nPlanes_) && (1 == nCanals)) // R plane G plane B plane
         {
            for (thePixel = 0; thePixel < nPix; ++thePixel)
            {
               *pOutput++ = pInput[thePixel]; // R
               *pOutput++ = pInput[thePixel + nPix];// G
               *pOutput++ = pInput[thePixel + 2*nPix];// B
               ++pOutput;             // empty
            }
         }
      }
   }
   else // monochrome images
   {
      nColors = 1;
      // start of access to image buffer
      *ppImageBufferGuard = new MMThreadGuard(BOImplementationThread::imageBufferLock_s);
      bufferToReturn = pBuf_;
   }

   return bufferToReturn;
}


void BOImplementationThread::CurrentImageSize(unsigned& xDim, unsigned& yDim,
      int& bitsInOneColor, int& nColors, unsigned long& bufSize)
{
   xDim = xDim_;
   yDim = yDim_;
   bitsInOneColor = bitsInOneColor_;

   if (BOIMF_RGB == ImageCode().iCode)
   {
      bufSize = xDim_ * yDim_ * BytesInOneComponent(bitsInOneColor) * 4;
      nColors = 4;
   }
   else
   {
      bufSize = bufSize_;
      nColors = 1;
   }


   return;
}

// return the possible gain range
std::pair<double, double> BOImplementationThread::GainLimits()
{
   std::pair<double, double> ret(0.,0.);
   MMThreadGuard g(stateMachineLock_);
   WorkerState prevState(cameraState_);
   cameraState_ = Busy;
   tGAIN m_Gain;
   // specifying 0 returns gain range
   (void)FX_SetGainFactor(gCameraId[0], 0., &m_Gain);
   ret = std::make_pair(m_Gain.gainMin, m_Gain.gainMax);
   cameraState_ = prevState;
   return ret;
}

// return possible exposure range (in micro-seconds)
std::pair<int, int> BOImplementationThread::ExposureLimits()
{
   std::pair<int, int> ret(0,0);
   MMThreadGuard g(stateMachineLock_);
   WorkerState prevState(cameraState_);
   cameraState_ = Busy;
   tEXPO m_Exposure;
   // specifying 0 returns exposure range
   (void)FX_SetExposureTime(gCameraId[0], 0, &m_Exposure);
   ret = std::make_pair(m_Exposure.timeMin, m_Exposure.timeMax);
   cameraState_ = prevState;
   return ret;
}


void BOImplementationThread::QueryCapabilities()
{
   MMThreadGuard g(stateMachineLock_);
   int nImgFormat;
   tpBoImgFormat* aImgFormat;
   //query image formats
   int fxret = FX_GetCapability (gCameraId[0], BCAM_QUERYCAP_IMGFORMATS, 0/*UNUSED*/, (void**)&aImgFormat, &nImgFormat);



   if (fxret == CE_SUCCESS)
   {
      // copy out of library's local memory

      for (int i = 0; i < nImgFormat; i++)
      {
         NamedFormat f = std::make_pair(std::string(aImgFormat[i]->aName), *(aImgFormat[i]));
         formats.push_back(f);

         ImageCodesPerFormat theseCodes;
         ImageFiltersPerFormat theseFilters;
         int nattributesAvailable;
         int jj;

         //query image codes
         tpBoImgCode * aImgCode;
         fxret = FX_GetCapability(gCameraId[0], BCAM_QUERYCAP_IMGCODES, f.second.iFormat, (void**)&aImgCode, &nattributesAvailable);
         if (fxret == CE_SUCCESS)
         {
            for (jj = 0; jj < nattributesAvailable; ++jj)
            {
               tBoImgCode tvalue = *(aImgCode[jj]);
               theseCodes.push_back(tvalue);
            }
         }
         // query image filters for this format
         tpBoImgFilter * aImgFilter;
         int fxret = FX_GetCapability(gCameraId[0], BCAM_QUERYCAP_IMGFILTER, f.second.iFormat, (void**)&aImgFilter, &nattributesAvailable);
         if (fxret == CE_SUCCESS)
         {
            for (jj = 0; jj < nattributesAvailable; ++jj)
            {
               tBoImgFilter tvalue = *(aImgFilter[jj]);
               theseFilters.push_back(tvalue);
            }
         }

         // enumerate some more capabilities....
         tpBoCamFunction * aCamFunction;
         int nCamFunction;
         fxret = FX_GetCapability(0, BCAM_QUERYCAP_CAMFUNCTIONS, f.second.iFormat, (void**)&aCamFunction, &nCamFunction);
         std::ostringstream oss;
         oss << " for format " << f.first << " aux camera capabilites:";

         if (fxret == CE_SUCCESS)
         {
            for (int i = 0; i < nCamFunction; i++)
            {
               oss << "\n 0x" << std::hex << aCamFunction[i]->iFunction << "\t" << std::string(aCamFunction[i]->aName);
            }
         }
         else
         {
            oss << " error retrieving auxilliary capabilites";
         }

         LLogMessage(oss.str(), true);


         CompleteFormat f0;
         f0.f_ = f.second;
         f0.formatIndex_ = i;
         f0.name_ = f.first;
         f0.codes_ = theseCodes;
         f0.filters_ = theseFilters;
         completeFormats_.push_back(f0);
      }
   }
}


// iterate through formats and make vectors of properties

void BOImplementationThread::ParseCapabilities()
{
   MMThreadGuard g(stateMachineLock_);
   std::vector<CompleteFormat>::iterator i;

   for (i = completeFormats_.begin(); i != completeFormats_.end(); ++i)
   {
      int thisBin = 1;
      std::vector<std::string> tokens;
      CDeviceUtils::Tokenize(i->name_, tokens, " ");
      std::vector<std::string>::iterator ti;
      for (ti = tokens.begin(); ti != tokens.end(); ++ti)
      {
         if (*ti == "Binning")
         {
            std::istringstream ts(*(ti+1));
            ts>>thisBin;
            break;
         }
      }
      if ((3 != thisBin) && binSizes_.end() == std::find(binSizes_.begin(), binSizes_.end(), thisBin))
      {
         binSizes_.push_back(thisBin);
      }

      int thisDepth = i->f_.iPixelBits;
      if (pixelDepths_.end() == std::find(pixelDepths_.begin(), pixelDepths_.end(), thisDepth))
      {
         pixelDepths_.push_back(thisDepth);
      }

      ImageCodesPerFormat::iterator jj;

      for (jj = i->codes_.begin(); jj != i->codes_.end(); ++jj)
      {

         int thisChannelBits = jj->iCanalBits;
         if (possibleBitsInOneColor_.end() == std::find(possibleBitsInOneColor_.begin(), possibleBitsInOneColor_.end(), thisChannelBits))
         {
            possibleBitsInOneColor_.push_back(thisChannelBits);
         }

         int planes = jj->iPlanes;
         if (this->possibleNPlanes_.end() == std::find(possibleNPlanes_.begin(), possibleNPlanes_.end(), planes))
         {
            possibleNPlanes_.push_back(planes);
         }
      }

      ImageFiltersPerFormat::iterator kk;

      for (kk = i->filters_.begin(); kk != i->filters_.end(); ++kk)
      {
         std::string filterName = kk->aName;
         if (filters_.end() == filters_.find(kk->imgFilterCode))
         {
            filters_[kk->imgFilterCode] = filterName;
         }
      }
   }
   QueryCameraCurrentFormat();
}

/**
 * @brief Function keeps up to date some private member variables describing
 * current image format.
 */
void BOImplementationThread::QueryCameraCurrentFormat()
{
   int fxRet = 0;
   tBoCameraType   dcBoType;               // Cameratype struct
   tBoCameraStatus dcBoStatus;             // Camerastatus struct
   int nImgFormat;
   tpBoImgFormat* aImgFormat;

   dcBoType.iSizeof = sizeof(dcBoType);
   dcBoStatus.iSizeof = sizeof(dcBoStatus);
   fxRet = FX_GetCameraInfo(gCameraId[0], &dcBoType, &dcBoStatus);
   iCode_g = dcBoStatus.eCurImgCode.iCode;
   if (fxRet != CE_SUCCESS)
   {
      LLogMessage(GetSDKErrorMessage(fxRet));
   }
   else
   {
      bitsInOneColor_ = dcBoStatus.eCurImgCode.iCanalBits;
      nPlanes_ = dcBoStatus.eCurImgCode.iPlanes;
   }
   FindImageFormatInFormatCache(dcBoStatus.eCurImgFormat);

   // Find frame dimersions (size) that camera assume for the current image format
   if (partialScanMode_ == false)
   {
      fxRet = FX_GetCapability(gCameraId[0], BCAM_QUERYCAP_IMGFORMATS, 0/*UNUSED*/,
           (void**)&aImgFormat, &nImgFormat);
      if (fxRet != CE_SUCCESS)
      {
         LLogMessage(("Cannot get IMGFORMATS: " + GetSDKErrorMessage(fxRet)).c_str());
         LLogMessage("Cannot get image size; further problems expected");
         nImgFormat = 0;
      }
      for(int i = 0; i < nImgFormat; i++)
      {
         if (dcBoStatus.eCurImgFormat == aImgFormat[i]->iFormat)
         {
            MMThreadGuard g(BOImplementationThread::imageBufferLock_s);
            xDim_ = aImgFormat[i]->iSizeX;
            yDim_ = aImgFormat[i]->iSizeY;
            std::ostringstream os;
            os << "Image shape updated to xDim_: " << xDim_ << " yDim_: " << yDim_ << endl;
            LLogMessage(os.str().c_str());
            break;
         }
      }
   }
}


int BOImplementationThread::svc()
{
   // loop in this working thread until the camera is shutdown.
   for (;;)
   {
      if (Exit == Command())
      {
         if (Idle < CameraState())
         {
            // kill the acquisition thread
            {
               LLogMessage("Send termination request to BO acquisition thread ", true);
               MMThreadGuard g(::acquisitionThreadTerminateLock_g);
               mTerminateFlag_g = true;
            }

            LLogMessage("sent terminate request to BO acquisition thread ", true);
            ::TerminateThread(acquisitionThread_, 0);
            LLogMessage("BO acquisition thread terminated ", true);
            CloseHandle(acquisitionThread_);
            acquisitionThread_ = NULL;

            MMThreadGuard g(stateMachineLock_);
            int fxReturn = FX_CloseCamera(gCameraId[0]);
            if (1 != fxReturn)
            {
               std::ostringstream oss;
               oss << "FX_CloseCamera error: 0x" << ios::hex << fxReturn;
               LLogMessage(oss.str());
            }
            fxReturn = FX_DeInitLibrary();
            if (1 != fxReturn)
            {
               std::ostringstream oss;
               oss << "FX_DeInitLibrary error: 0x" << ios::hex << fxReturn;
               LLogMessage(oss.str());
            }
         }
         break;
      }

      switch (CameraState())
      {

         case Idle: // wait for initialization request

            if (InitializeLibrary == Command())
            {
               int fxReturn = BOInitializationSequence();
               TriggerMode(triggerMode_); // write the initialized value down to the HW
               Command(Noop);
               if (fxReturn == CE_SUCCESS)
               {
                  CameraState(Ready); // camera is ready to take images.
               }
            }
            break;

         case Ready: //ready for a operational command
            if (StartSequence == Command())
            {
               Command(Noop);
               LLogMessage("FX_CamStart TRUE (StartSequence)", true);
               FX_CamStart(gCameraId[0], TRUE);
               CameraState(Acquiring);
            }
            else if (SnapCommand == Command())
            {
               Snap();
               Command(Noop);
            }
            break;

         case Acquiring: // sequence Acquisition processing
            {
               // checks size of buffer and allocates a new one if needed
               Acquire();

               // complicated way to wait for one exposure time
               MM::TimeoutMs timerOut(CurrentMMTimeMM(), ExposureUs() / 1000);
               for (;;)
               {
                  if (StopSequence == Command())
                  {
                     Command(Noop);
                     LLogMessage("FX_CamStart FALSE (StopSequence)", true);
                     FX_CamStart(gCameraId[0], FALSE);
                     {
                        MMThreadGuard g(seqActiveLock_g);
                        seqactive_g = false;
                     }
                     CameraState(Ready);
                     break;
                  }
                  if (Exit == Command())
                  {
                     LLogMessage("FX_CamStart FALSE (Exit)", true);
                     FX_CamStart(gCameraId[0], FALSE);
                     {
                        MMThreadGuard g(seqActiveLock_g);
                        seqactive_g = false;
                     }
                     CameraState(Ready);
                     break;
                  }
                  if (timerOut.expired(CurrentMMTimeMM()))
                  {
                     break;
                  }

                  // Avoid hogging the CPU
                  CDeviceUtils::SleepMs(1);
               }

               if (CameraState() == Acquiring)
               {
                  int ret = pCamera_->SendImageToCore();
                  if (ret != DEVICE_OK)
                  {
                     ostringstream os;
                     os << "SendImageToCore failed with errorcode: " << ret;
                     LLogMessage(os.str());
                     CameraState(Ready);
                     break;
                  }
                  ++frameCount_;
                  if (numImages_ <= frameCount_)
                  {
                     LLogMessage("FX_CamStart FALSE (reached requested frame count)", true);
                     FX_CamStart(gCameraId[0], FALSE);
                     {
                        MMThreadGuard g(seqActiveLock_g);
                        seqactive_g = false;
                     }
                     CameraState(Ready);
                  }
               }
            }
            break;

         default:
            CameraState(Idle);
            break;
      }

      // Avoid hogging the CPU
      CDeviceUtils::SleepMs(1);
   }

   Command(Noop);
   LLogMessage("CCamera acquisition thread is exiting... ", true);
   return 0;
}


int BOImplementationThread::BOInitializationSequence()
{
   int fxReturn = 0;

   MMThreadGuard g(stateMachineLock_);
   char fxMess[500];
   fxMess[0] = 0;

   fxReturn = FX_DeleteLabelInfo();

   // **** Init Library
   fxReturn = FX_InitLibrary();
   if (fxReturn == CE_SUCCESS)
   {
      // **** Enumerate all 1394 devices ***************
      int DevCount; // number of cameras
      fxReturn = FX_EnumerateDevices(&DevCount);
      if (fxReturn == CE_SUCCESS)
      {
         if (1 == DevCount)
         {
            // **** Label a special device **********************
            gCameraId[0] = 0;
            fxReturn = FX_LabelDevice(0, gCameraId[0]);
            if (fxReturn == CE_SUCCESS)
            {
               // **** Open a labeled device ********************
               fxReturn = FX_OpenCamera(gCameraId[0]);
               if (fxReturn == CE_SUCCESS)
               {
                  std::ostringstream oss;
                  oss << "Opened Leica / Baumer Optronic Camera # " << gCameraId[0];
                  LLogMessage(oss.str(), false);

                  imageNotificationEvent_ = ::CreateEvent(NULL,FALSE,FALSE,NULL);
                  unsigned int tempthid = 0;
                  // Install Image Event Handler Thread for incoming data
                  acquisitionThread_ = (HANDLE)_beginthreadex(NULL, 0, &mSeqEventHandler,
                        (PVOID)imageNotificationEvent_, 0, &tempthid);
                  std::ostringstream s2;
                  s2 << " BO acquistion thread id " << std::hex << acquisitionThread_ << " was started ... ";
                  LLogMessage(s2.str(), true);
                  ::SetThreadPriority(acquisitionThread_, THREAD_PRIORITY_TIME_CRITICAL); //?? really

                  fxReturn = FX_DefineImageNotificationEvent(gCameraId[0], imageNotificationEvent_);
                  if (fxReturn == CE_SUCCESS)
                  {
                     // **** Allocate Buffers ********************
                     fxReturn = FX_AllocateResources(gCameraId[0], 10, 0);
                     if (fxReturn == CE_SUCCESS)
                     {
                        // **** Start capture process********************
                        fxReturn = FX_StartDataCapture(gCameraId[0], TRUE);
                        if (fxReturn != CE_SUCCESS)
                        {
                           sprintf(fxMess,"FX_StartDataCapture error: %08x", fxReturn);
                           LLogMessage(fxMess);
                        }
                     }
                     else
                     {
                        sprintf(fxMess,"FX_AllocateResources error: %08x", fxReturn);
                        LLogMessage(fxMess);
                     }
                  }
                  else
                  {
                     sprintf(fxMess,"FX_DefineImageNotificationEvent error: %08x", fxReturn);
                     LLogMessage(fxMess);
                  }
               }
               else
               {
                  sprintf(fxMess,"FX_OpenCamera error: %08x", fxReturn);
                  LLogMessage(fxMess);
               }
            }
            else
            {
               sprintf(fxMess,"FX_LabelDevice error: %08x", fxReturn);
               LLogMessage(fxMess);
            }
         }
         else
         {
            // XXX BUG! We should be returning an error in this case, but we're not.
            sprintf(fxMess,"# cameras must be 1, but %d cameras found \n", DevCount);
            LLogMessage(fxMess);
         }
      }
      else
      {
         sprintf(fxMess,"FX_EnumerateDevices error: %08x", fxReturn);
         LLogMessage(fxMess);
      }
   }
   else
   {
      sprintf(fxMess,"FX_InitLibrary error: %08x", fxReturn);
      LLogMessage(fxMess);
   }

   if (fxReturn == CE_SUCCESS)
   {
      LLogMessage(" BO camera library initialized OK!", true);
   }
   return fxReturn;
}


void BOImplementationThread::LLogMessage(const std::string message, const bool debugOnly)
{
   LLogMessage(message.c_str(), debugOnly);
}


void BOImplementationThread::LLogMessage(const char* pMessage, const bool debugOnly)
{
   if (NULL != pCamera_)
   {
      pCamera_->LogMessage(pMessage, debugOnly);
   }
}


MM::MMTime BOImplementationThread::CurrentMMTimeMM() // MMTime as milliseconds
{
   MM::MMTime ret(0);
   if (0 != pCamera_)
   {
      ret = pCamera_->GetCurrentMMTime();
   }
   return ret;
}


int BOImplementationThread::BinSize() const
{
   return BinSizeFromCompleteFormat(this->completeFormatIter_);
}


int BOImplementationThread::BinSizeFromCompleteFormat(std::vector<CompleteFormat>::iterator i) const
{
   int thisBin = 1;
   if (completeFormats_.end() != i)
   {
      std::vector<std::string> tokens;
      CDeviceUtils::Tokenize(i->name_, tokens, " ");
      std::vector<std::string>::iterator ti;
      for (ti = tokens.begin(); ti != tokens.end(); ++ti)
      {
         if (*ti == "Binning")
         {
            std::istringstream ts(*(ti+1));
            ts>>thisBin;
            break;
         }
      }
   }
   return thisBin;
}


// N.B. This returns, for example, 12 even when the selected code specifies 8 !!!!
int BOImplementationThread::BitDepthFromCompleteFormat(std::vector<CompleteFormat>::iterator i)
{
   return i->f_.iPixelBits;
}

tBoImgCode BOImplementationThread::ImageCode()
{
   tBoCameraType   dcBoType;               // Cameratype struct
   tBoCameraStatus dcBoStatus;             // Camerastatus struct

   dcBoType.iSizeof = sizeof(dcBoType);
   dcBoStatus.iSizeof = sizeof(dcBoStatus);
   int fxRet = FX_GetCameraInfo(gCameraId[0], &dcBoType, &dcBoStatus);
   iCode_g = dcBoStatus.eCurImgCode.iCode;

   if (1 != fxRet)
   {
      LLogMessage(GetSDKErrorMessage(fxRet));
   }

   return dcBoStatus.eCurImgCode;
}


bool BOImplementationThread::ImageFormat(int imageFormatIndex)
{
   int fxret = 0;
   bool ret = false;

   fxret = FX_SetImageFormat(gCameraId[0], imageFormatIndex);

   if (fxret == CE_SUCCESS)
   {
      std::vector<CompleteFormat>::iterator i;
      for (i = completeFormats_.begin(); i != completeFormats_.end(); ++i)
      {
         if (imageFormatIndex == i->formatIndex_)
         {
            ret = true;
            completeFormatIter_ = i;
            break;
         }
      }
   }
   return ret;
}


void BOImplementationThread::FindImageFormatInFormatCache(int imageFormatIndex)
{
   completeFormatIter_ = completeFormats_.end();
   std::vector<CompleteFormat>::iterator i;
   for (i = completeFormats_.begin(); i != completeFormats_.end(); ++i)
   {
      if (imageFormatIndex == i->formatIndex_)
      {
         completeFormatIter_ = i;
         break;
      }
   }
}


void BOImplementationThread::BinSize(const int v)
{
   if (v != BinSizeFromCompleteFormat(completeFormatIter_))
   {
      // what image format parameters are selected?
      tBoImgCode currentCode = ImageCode();

      // don't understand meaning of 'valid bits' inside BitDepthFromCompleteFormat:
      //int currentBitDepth = BitDepthFromCompleteFormat(completeFormatIter_);
      //int currentBitDepth = currentCode.iCanalBits;

      std::vector<CompleteFormat>::iterator i;
      bool foundMatch = false;
      for (i = completeFormats_.begin(); i != completeFormats_.end(); ++i)
      {
         if (v == BinSizeFromCompleteFormat(i))
         {
            ImageCodesPerFormat::iterator jj;
            for (jj = i->codes_.begin(); jj != i->codes_.end(); ++jj)
            {
               // todo don't really need same code, just same # bits would be good.
               if (currentCode == *jj)
               {
                  if (ImageFormat(i->formatIndex_))
                  {
                     // N.B. TURNS OFF ROI at this point!! this could be improved to recalculated roi each time binsize is changed
                     partialScanMode_ = false;
                     int fxret = FX_SetImageCode(gCameraId[0], currentCode);
                     if (fxret == CE_SUCCESS)
                     {
                        foundMatch = true;
                        QueryCameraCurrentFormat();
                        break;
                     }
                  }
               }
               if (foundMatch)
               {
                  break;
               }
            }
            if (foundMatch)
            {
               break;
            }
         }
      }
      if (!foundMatch)
      {
         std::ostringstream oss;
         oss << " in BinSize can not set bins to " << v;
         LLogMessage(oss.str());
      }
   }
}

int BOImplementationThread::SetBitsInOneColor(int bits)
{

   MMThreadGuard g(stateMachineLock_);
   WorkerState prevState(cameraState_);
   cameraState_ = Busy;

   // what image format parameters are currently selected?
   tBoImgCode currentCode = ImageCode();


   int currentBits = currentCode.iCanalBits;
   if (currentBits != bits)
   {
      int currentBin = this->BinSize();
      std::vector<CompleteFormat>::iterator i;
      bool foundMatch = false;
      for (i = completeFormats_.begin(); i != completeFormats_.end(); ++i)
      {
         if (currentBin == BinSizeFromCompleteFormat(i))
         {
            ImageCodesPerFormat::iterator jj;
            for (jj = i->codes_.begin(); jj != i->codes_.end(); ++jj)
            {
               if (bits == jj->iCanalBits)
               {
                  if (ImageFormat(i->formatIndex_))
                  {
                     //here do i need recalculate ROI??

                     int fxret = FX_SetImageCode(gCameraId[0], *jj);
                     if (fxret == CE_SUCCESS)
                     {
                        foundMatch = true;
                        QueryCameraCurrentFormat();
                        break;
                     }
                  }
               }
               if (foundMatch)
               {
                  break;
               }
            }
            if (foundMatch)
            {
               break;
            }
         }
      }
      if (!foundMatch)
      {
         cameraState_ = prevState;
         return DEVICE_ERR;
      }
   }
   cameraState_ = prevState;
   return DEVICE_OK;
}


// check if current format is monochrome

bool BOImplementationThread::MonoChrome()
{
   bool ret = false;
   switch (ImageCode().iCode)
   {
      case BOIMF_PIXORG: // use only the information of tBoImgCode
         break;
      case BOIMF_RAWMONO: // raw monochrome pattern
         ret = true;
         break;
      case BOIMF_RAWBAYER: // raw Bayer pattern
      case BOIMF_RGB: // rgbrgb... allgemein
         break;
      case BOIMF_MONO: // mm...
         ret = true;
         break;
      case BOIMF_RAWBAYER_GR: // raw Bayer pattern red line start with green
      case BOIMF_RAWBAYER_GB: // raw Bayer pattern blue line start with green
      case BOIMF_RAWBAYER_BG: // raw Bayer pattern blue line start with blue
      case BOIMF_RAW: // raw memory image, internal usage
      case BOIMF_NOTDEF: // not defined
      case BOIMF_YUV444: // not supported yet
      case BOIMF_YUV422: // not supported yet
      case BOIMF_YUV411: // not supported yet
         break;
      default:
         break;
   }
   return ret;
}


// check if current format is color
bool BOImplementationThread::Color()
{
   bool ret = false;
   switch (ImageCode().iCode)
   {
      case BOIMF_PIXORG: // use only the information of tBoImgCode
         break;
      case BOIMF_RAWMONO: // raw monochrome pattern
         break;
      case BOIMF_RAWBAYER: // raw Bayer pattern
         break;
      case BOIMF_RGB: // rgbrgb... allgemein
         ret = true;
         break;
      case BOIMF_MONO: // mm...
         break;
      case BOIMF_RAWBAYER_GR: // raw Bayer pattern red line start with green
      case BOIMF_RAWBAYER_GB: // raw Bayer pattern blue line start with green
      case BOIMF_RAWBAYER_BG: // raw Bayer pattern blue line start with blue
      case BOIMF_RAW: // raw memory image, internal usage
      case BOIMF_NOTDEF: // not defined
      case BOIMF_YUV444: //not supported yet
      case BOIMF_YUV422: //not supported yet
      case BOIMF_YUV411: //not supported yet
         break;
      default:
         break;
   }

   return ret;
}




std::string BOImplementationThread::GetSDKErrorMessage(const int fxcode)
{
   char *pmess = FX_GetErrString(fxcode);
   std::ostringstream oss;
   oss << "FX error: " << std::hex << fxcode;
   if (NULL != pmess)
   {
      oss << ' ' << std::string(pmess);
   }
   return oss.str();
}



void BOImplementationThread::CancelROI()
{
   RECT returnedRect;
   memset(&returnedRect, 0, sizeof(RECT));
   int fxret = FX_SetPartialScanEx(gCameraId[0], false, &returnedRect, &returnedRect);
   if (fxret == CE_SUCCESS)
   {
      roi_ = returnedRect;
   }
   partialScanMode_ = false;
   QueryCameraCurrentFormat(); // Reset image xDim_, yDim_ to full size
}



void BOImplementationThread::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
   RECT returnedRect;
   if ((0 == x) && (0 == y) && (xSize == xDim_) && (ySize == yDim_)) // ROI is being cleared, use full image format
   {
      memset(&returnedRect, 0, sizeof(RECT));
      int fxret = FX_SetPartialScanEx(gCameraId[0], false, &returnedRect, &returnedRect);
      if (fxret == CE_SUCCESS)
      {
         roi_ = returnedRect;
      }
      partialScanMode_ = false;
   }
   else
   {
      RECT requestedRect;
      requestedRect.bottom = y + ySize;
      requestedRect.left = x;
      requestedRect.right = x + xSize;
      requestedRect.top = y;
      int fxret = FX_SetPartialScanEx(gCameraId[0], true, &requestedRect, &returnedRect);
      if (fxret == CE_SUCCESS)
      {
         roi_ = returnedRect;
         partialScanMode_ = true;

         // Set new image shape same as returnedRect,
         // otherwise circular buffer would not initialized properly
         MMThreadGuard g(BOImplementationThread::imageBufferLock_s);
         xDim_ = roi_.right - roi_.left;
         yDim_ = roi_.bottom - roi_.top;
         std::ostringstream os;
         os << "Image shape changed by ROI to xDim_: " << xDim_ << " yDim_: " << yDim_ << endl;
         LLogMessage(os.str().c_str());
      }
   }
   QueryCameraCurrentFormat();
}

void BOImplementationThread::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{

   if (partialScanMode_) // subregion was successfully set and is in use
   {
      x = roi_.left;
      y = roi_.top;
      ySize = roi_.bottom - roi_.top;
      xSize = roi_.right - roi_.left;
   }
   else
   {
      QueryCameraCurrentFormat();
      x = 0;
      y = 0;
      xSize = xDim_;
      ySize = yDim_;
   }
}

// waits for the specified image ready event
unsigned int __stdcall mSeqEventHandler(void* pArguments)
{
   HANDLE imageNotificationEvent = (HANDLE)pArguments;
   tBoImgDataInfoHeader imgHeader;
   memset(&imgHeader, 0x00, sizeof(imgHeader));
   for (;;)
   {
      {
         MMThreadGuard g(::acquisitionThreadTerminateLock_g);
         if (mTerminateFlag_g)
         {
            break;
         }
      }

      DWORD waitStatus = WaitForSingleObject(imageNotificationEvent, imageTimeoutMs_g);
      if (waitStatus == WAIT_OBJECT_0)
      {
         {
            MMThreadGuard g(seqActiveLock_g);
            if (!seqactive_g)
            {
               continue;
            }
         }

         imgHeader.sFlags.fFlipHori = false;
         imgHeader.sFlags.fFlipVert = true;
         imgHeader.sFlags.fSyncStamp = true;

         bool bufferIsReady = false;
         DWORD gotImage = FALSE;

         {
            MMThreadGuard g(BOImplementationThread::imageBufferLock_s);
            bufferIsReady = (0 < staticImgSize_g) && (NULL != pStatic_g);
         }

         if (bufferIsReady)
         {
            MMThreadGuard g(BOImplementationThread::imageBufferLock_s);
            gotImage = FX_GetImageData(gCameraId[0], &imgHeader, pStatic_g, staticImgSize_g);
         }

         if (gotImage == TRUE)
         {
            MMThreadGuard g(BOImplementationThread::imageBufferLock_s);
            xDim_g = imgHeader.iSizeX;
            yDim_g = imgHeader.iSizeY;
            bitsInOneColor_g = imgHeader.sDataCode.iCanalBits;
            nPlanes_g = imgHeader.sDataCode.iPlanes;
            nCanals_g = imgHeader.sDataCode.iCanals;
            iCode_g = imgHeader.sDataCode.iCode;

            {
               MMThreadGuard guard(BOImplementationThread::imageReadyLock_s);
               imageReady_g = true;
            }
         }
      }
   }
   return 0;
}








/**
 * CBaumerOptronic constructor.
 * Setup default all variables and create device properties required to exist
 * before intialization. In this case, no such properties were required. All
 * properties will be created in the Initialize() method.
 *
 * As a general guideline Micro-Manager devices do not access hardware in the
 * the constructor. We should do as little as possible in the constructor and
 * perform most of the initialization in the Initialize() method.
 */
CBaumerOptronic::CBaumerOptronic() :
   CCameraBase<CBaumerOptronic>(),
   initialized_(false),
   pWorkerThread_(NULL),
   stopOnOverflow_(false)
{
   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();
}

/**
 * CBaumerOptronic destructor.
 * If this device used as intended within the Micro-Manager system,
 * Shutdown() will be always called before the destructor. But in any case
 * we need to make sure that all resources are properly released even if
 * Shutdown() was not called.
 */
CBaumerOptronic::~CBaumerOptronic()
{
   Shutdown();
}

/**
 * Shuts down (unloads) the device.
 * Required by the MM::Device API.
 * Ideally this method will completely unload the device and release all resources.
 * Shutdown() may be called multiple times in a row.
 * After Shutdown() we should be allowed to call Initialize() again to load the device
 * without causing problems.
 */
int CBaumerOptronic::Shutdown()
{

   if (initialized_ && (NULL != pWorkerThread_))
   {
      LogMessage(" sending Exit command to implementation thread ", true);
      pWorkerThread_->Command(Exit);
      LogMessage("deleting BO camera implementation thread ", true);
      pWorkerThread_->wait();
      delete pWorkerThread_;
      pWorkerThread_ = NULL;
   }
   initialized_ = false;
   return DEVICE_OK;
}

/**
 * Obtains device name.
 * Required by the MM::Device API.
 */
void CBaumerOptronic::GetName(char* name) const
{
   // We just return the name we use for referring to this
   // device adapter.
   CDeviceUtils::CopyLimitedString(name, g_CameraDeviceName);
}

/**
 * Intializes the hardware.
 * Required by the MM::Device API.
 * Typically we access and initialize hardware at this point.
 * Device properties are typically created here as well, except
 * the ones we need to use for defining initialization parameters.
 * Such pre-initialization properties are created in the constructor.
 * (This device does not have any pre-initialization properties)
 */
int CBaumerOptronic::Initialize()
{
   if (initialized_)
   {
      return DEVICE_OK;
   }


   // start the implementation thread
   pWorkerThread_ = new BOImplementationThread(this);
   pWorkerThread_->Start();
   pWorkerThread_->Command(InitializeLibrary);

   MM::TimeoutMs timerOut(GetCurrentMMTime(), 6000);

   do
   {
      if (timerOut.expired(GetCurrentMMTime()))
      {
         break;
      }
      // yeild until the libaries have been loaded
      CDeviceUtils::SleepMs(10);
   } while (Ready != pWorkerThread_->CameraState());
   // XXX BUG! Error not handled if timed out (not that we should be using
   // timeout as a way to detect failure in the first place).

   // query the 'formats' structures
   pWorkerThread_->QueryCapabilities();

   // march through the 'formats' structures and extract the possible physical property settings
   pWorkerThread_->ParseCapabilities();
   // Note: the camera uses microseconds for exposure, whereas we use milliseconds
   std::pair<int, int> exposureLowHigh = pWorkerThread_->ExposureLimits();
   std::pair<double, double> gainLowHigh = pWorkerThread_->GainLimits();

   // set property list
   // -----------------

   CPropertyAction *pAct = new CPropertyAction(this, &CBaumerOptronic::OnGain);
   std::ostringstream gainLimit;
   gainLimit << gainLowHigh.first;
   (void)CreateProperty(MM::g_Keyword_Gain, gainLimit.str().c_str(), MM::Float, false, pAct);
   (void)SetPropertyLimits(MM::g_Keyword_Gain, gainLowHigh.first, gainLowHigh.second);

   pAct = new CPropertyAction(this, &CBaumerOptronic::OnExposure);
   std::ostringstream oss;
   oss << exposureLowHigh.first;
   (void)CreateProperty(MM::g_Keyword_Exposure, oss.str().c_str(), MM::Float, false, pAct);
   (void)SetPropertyLimits(MM::g_Keyword_Exposure, exposureLowHigh.first/1000, exposureLowHigh.second/1000);
   // We can not query from the camera. To start in a known state, set the exposure time
   SetExposure(25.0);


   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, g_CameraDeviceName, MM::String, true);
   if (DEVICE_OK != nRet)
   {
      return nRet;
   }

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "Baumer Optronic Adapter for Leica Cameras", MM::String, true);
   if (DEVICE_OK != nRet)
   {
      return nRet;
   }

   // CameraName
   nRet = CreateProperty(MM::g_Keyword_CameraName, "BaumerOptronic-MultiMode", MM::String, true);
   assert(nRet == DEVICE_OK);

   // CameraID
   nRet = CreateProperty(MM::g_Keyword_CameraID, "V1.0", MM::String, true);
   assert(nRet == DEVICE_OK);

   // binning
   pAct = new CPropertyAction(this, &CBaumerOptronic::OnBinning);
   nRet = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);

   std::vector<string> binss;
   std::vector<int> binis = pWorkerThread_->BinSizes();
   std::vector<int>::iterator viit;

   for (viit = binis.begin(); viit != binis.end(); ++viit)
   {
      std::ostringstream osss;
      osss << *viit;
      binss.push_back(osss.str().c_str());
   }

   SetAllowedValues(MM::g_Keyword_Binning, binss);

   // pixel type
   pAct = new CPropertyAction(this, &CBaumerOptronic::OnPixelType);
   nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_8bit, MM::String, true, pAct);
   assert(nRet == DEVICE_OK);

   vector<string> pixelTypeValues;
   pixelTypeValues.push_back(g_PixelType_8bit);
   pixelTypeValues.push_back(g_PixelType_16bit);
   pixelTypeValues.push_back(g_PixelType_32bitRGB);

   nRet = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
   if (nRet != DEVICE_OK)
   {
      return nRet;
   }

   // Bit depth
   pAct = new CPropertyAction(this, &CBaumerOptronic::OnBitDepth);
   nRet = CreateProperty("BitDepth", "8", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);

   vector<string> bitDepths;

   std::vector<int> pbb = pWorkerThread_->PossibleBitsInOneColor();
   std::vector<int>::iterator pbbi;

   for (pbbi = pbb.begin(); pbbi != pbb.end(); ++pbbi)
   {
      std::ostringstream osss;
      osss << *pbbi;
      bitDepths.push_back(osss.str().c_str());
   }

   nRet = SetAllowedValues("BitDepth", bitDepths);
   if (nRet != DEVICE_OK)
   {
      return nRet;
   }

   // synchronize all properties
   // --------------------------
   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
   {
      return nRet;
   }

   // setup the buffer
   // ----------------
   SnapImage();
   nRet = ResizeImageBuffer();
   if (nRet != DEVICE_OK)
   {
      if (NULL != pWorkerThread_)
      {
         LogMessage(" sending Exit command to implementation thread ", true);
         pWorkerThread_->Command(Exit);
         pWorkerThread_->wait();
         LogMessage("deleting BO camera implementation thread ", true);
         delete pWorkerThread_;
         pWorkerThread_ = NULL;
      }

      return nRet;
   }

   initialized_ = true;

   SnapImage();

   return DEVICE_OK;
}



/**
 * Performs exposure and grabs a single image.
 * This function should block during the actual exposure and return immediately afterwards
 * (i.e., before readout). This behavior is needed for proper synchronization with the shutter.
 * Required by the MM::Camera API.
 */
int CBaumerOptronic::SnapImage()
{
   pWorkerThread_->Command(SnapCommand);

   return WaitForImageAndCopyToBuffer();
}


/**
 * Returns pixel data.
 * Required by the MM::Camera API.
 * The calling program will assume the size of the buffer based on the values
 * obtained from GetImageBufferSize(), which in turn should be consistent with
 * values returned by GetImageWidth(), GetImageHight() and GetImageBytesPerPixel().
 * The calling program allso assumes that camera never changes the size of
 * the pixel buffer on its own. In other words, the buffer can change only if
 * appropriate properties are set (such as binning, pixel type, etc.)
 */
const unsigned char* CBaumerOptronic::GetImageBuffer()
{
   return img_.GetPixels();
}

/**
 * Returns image buffer X-size in pixels.
 * Required by the MM::Camera API.
 */
unsigned CBaumerOptronic::GetImageWidth() const
{
   return img_.Width();
}

/**
 * Returns image buffer Y-size in pixels.
 * Required by the MM::Camera API.
 */
unsigned CBaumerOptronic::GetImageHeight() const
{
   return img_.Height();
}

/**
 * Returns image buffer pixel depth in bytes.
 * Required by the MM::Camera API.
 */
unsigned CBaumerOptronic::GetImageBytesPerPixel() const
{
   return img_.Depth();
}

/**
 * Returns the bit depth (dynamic range) of the pixel.
 * This does not affect the buffer size, it just gives the client application
 * a guideline on how to interpret pixel values.
 * Required by the MM::Camera API.
 */
unsigned CBaumerOptronic::GetBitDepth() const
{
   return pWorkerThread_->GetBitsInOneColor();
}

/**
 * Returns the size in bytes of the image buffer.
 * Required by the MM::Camera API.
 */
long CBaumerOptronic::GetImageBufferSize() const
{
   return img_.Width() * img_.Height() * GetImageBytesPerPixel();
}



unsigned CBaumerOptronic::GetNumberOfComponents() const
{
   bool col = pWorkerThread_->Color();
   return (col?4:1);
}



/**
 * Sets the camera Region Of Interest.
 * Required by the MM::Camera API.
 * This command will change the dimensions of the image.
 * Depending on the hardware capabilities the camera may not be able to configure the
 * exact dimensions requested - but should try do as close as possible.
 * @param x - top-left corner coordinate
 * @param y - top-left corner coordinate
 * @param xSize - width
 * @param ySize - height
 */
int CBaumerOptronic::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
   if (xSize == 0 && ySize == 0)
   {
      pWorkerThread_->CancelROI();

      unsigned x, y;
      int bits, colors;
      unsigned long bufs;
      pWorkerThread_->CurrentImageSize(x,y,bits,colors,bufs);
      pWorkerThread_->SetROI(0, 0, x, y);
      // effectively clear ROI
      ResizeImageBuffer();
   }
   else
   {
      pWorkerThread_->SetROI(x, y, xSize, ySize);
      // apply ROI
      img_.Resize(xSize, ySize);
   }
   return DEVICE_OK;
}

/**
 * Returns the actual dimensions of the current ROI.
 * Required by the MM::Camera API.
 */
int CBaumerOptronic::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{

   pWorkerThread_->GetROI(x, y, xSize, ySize);

   return DEVICE_OK;
}

/**
 * Resets the Region of Interest to full frame.
 * Required by the MM::Camera API.
 */
int CBaumerOptronic::ClearROI()
{

   pWorkerThread_->CancelROI();

   ResizeImageBuffer();

   return DEVICE_OK;
}

/**
 * Returns the current exposure setting in milliseconds.
 * Required by the MM::Camera API.
 */
double CBaumerOptronic::GetExposure() const
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_Exposure, buf);
   if (ret != DEVICE_OK)
   {
      return 0.0;
   }
   return atof(buf);
}

/**
 * Sets exposure in milliseconds.
 * Required by the MM::Camera API.
 */
void CBaumerOptronic::SetExposure(double exp)
{
   SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(exp));
}

/**
 * Returns the current binning factor.
 * Required by the MM::Camera API.
 */
int CBaumerOptronic::GetBinning() const
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_Binning, buf);
   if (ret != DEVICE_OK)
   {
      return 1;
   }
   return atoi(buf);
}

/**
 * Sets binning factor.
 * Required by the MM::Camera API.
 */
int CBaumerOptronic::SetBinning(int binFactor)
{
   return SetProperty(MM::g_Keyword_Binning, CDeviceUtils::ConvertToString(binFactor));
}



///////////////////////////////////////////////////////////////////////////////
// CBaumerOptronic Action handlers
///////////////////////////////////////////////////////////////////////////////


/**
 * Handles "Binning" property.
 */
int CBaumerOptronic::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_ERR;
   switch (eAct)
   {
      case MM::AfterSet:
         {
            bool liveMode = false;
            if (IsCapturing())
            {
               liveMode = true;
            }


            //if (IsCapturing())
            //   return DEVICE_CAMERA_BUSY_ACQUIRING;

            // the user just set the new value for the property, so we have to
            // apply this value to the 'hardware'.
            long binFactor;
            pProp->Get(binFactor);

            pWorkerThread_->BinSize(binFactor);
            // needed so that everyone learns about the new image size
            SnapImage();
            unsigned x, y;
            int bits, colors;
            unsigned long bufs;
            pWorkerThread_->CurrentImageSize(x,y,bits,colors,bufs);
            img_.Resize(x, y);
            ret = DEVICE_OK;
         }
         break;
      case MM::BeforeGet:
         {
            pProp->Set((long)pWorkerThread_->BinSize());
            ret = DEVICE_OK;
         }
         break;
   }
   return ret;
}

/**
 * Handles "PixelType" property. N.B. current implementation requires this property whether or not it does anything.
 */
int CBaumerOptronic::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_OK;
   switch (eAct)
   {
      case MM::AfterSet:
         break;
      case MM::BeforeGet:
         if (GetNumberOfComponents() == 4)
         {
            if (bitsInOneColor_g > 8)
            {
               pProp->Set(g_PixelType_64bitRGB);
            }
            else
            {
               pProp->Set(g_PixelType_32bitRGB);
            }
         }
         else if (GetImageBytesPerPixel() == 2)
         {
            pProp->Set(g_PixelType_16bit);
         }
         else
         {
            pProp->Set(g_PixelType_8bit);
         }
         break;
   }
   return ret;
}

/**
 * Handles "BitDepth" property.
 */
int CBaumerOptronic::OnBitDepth(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   if (Ready != pWorkerThread_->CameraState())
   {
      return DEVICE_CAMERA_BUSY_ACQUIRING;
   }

   switch (eAct)
   {
      case MM::AfterSet: // property was written -> apply value to hardware
         {
            long bitDepth;
            pProp->Get(bitDepth);
            // if (GetNumberOfComponents() == 4)
            //    bitDepth = 8; // We can not yet handle 64bitRGB
            int ret = pWorkerThread_->SetBitsInOneColor(bitDepth);
            if (ret != DEVICE_OK)
            {
               return ret;
            }
            bitsInOneColor_g = (unsigned short)bitDepth;
            int bytesPerPixel = BytesInOneComponent(bitsInOneColor_g);
            //todo is color image selected at this time?
            img_.Resize(img_.Width(), img_.Height(), bytesPerPixel * GetNumberOfComponents());
         }
         break;

      case MM::BeforeGet: // property will be read -> update property with value from hardware
         pProp->Set((long)pWorkerThread_->GetBitsInOneColor());
         break;
   }
   return DEVICE_OK;
}


// Exposure Time
int CBaumerOptronic::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   // exposure property is stored in milliseconds,
   // while the driver returns the value in micro-seconds
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(static_cast<double>(pWorkerThread_->ExposureUs()) / 1000.0);
   }
   else if (eAct == MM::AfterSet)
   {
      double millisecs;
      pProp->Get(millisecs);
      imageTimeoutMs_g = static_cast<unsigned long>(millisecs) + 500UL;
      int exposureUs = static_cast<int>(1000.0 * millisecs + 0.5);
      pWorkerThread_->ExposureUs(exposureUs);
   }
   return DEVICE_OK;
}


int CBaumerOptronic::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   // exposure property is stored in milliseconds,
   // while the driver returns the value in seconds
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(pWorkerThread_->Gain());
   }
   else if (eAct == MM::AfterSet)
   {
      double val;
      pProp->Get(val);
      pWorkerThread_->Gain(val);
   }
   return DEVICE_OK;
}



// fast acquisition API


/**
 * Starts continuous acquisition
 */
int CBaumerOptronic::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{
   if (Acquiring == pWorkerThread_->CameraState())
   {
      return DEVICE_CAMERA_BUSY_ACQUIRING;
   }

   MM::TimeoutMs timerOut(GetCurrentMMTime(), 1000);
   while (Ready != pWorkerThread_->CameraState())
   {
      CDeviceUtils::SleepMs(1);
      if (timerOut.expired(GetCurrentMMTime()))
      {
         return DEVICE_SERIAL_TIMEOUT;
      }
   }

   stopOnOverflow_ = stopOnOverflow;
   pWorkerThread_->Interval(interval_ms);

   LogMessage(" sequence starting, exposure (us) is " + boost::lexical_cast<std::string>(pWorkerThread_->ExposureUs()), true);

   pWorkerThread_->FrameCount(0);
   pWorkerThread_->SetLength(numImages);
   // N.B. not using base class member imageCounter_

   ostringstream os;
   os << "Started sequence acquisition: " << numImages << " at " << interval_ms << " ms" << endl;
   LogMessage(os.str().c_str());

   // prepare the core
   int ret = GetCoreCallback()->PrepareForAcq(this);
   if (ret != DEVICE_OK)
   {
      ResizeImageBuffer();
      return ret;
   }

   unsigned xDim;
   unsigned yDim;
   int bitsInOneColor;
   int nColors;
   unsigned long bufSize;

   pWorkerThread_->CurrentImageSize(xDim, yDim, bitsInOneColor, nColors, bufSize);

   // make sure the circular buffer is properly sized
   GetCoreCallback()->InitializeImageBuffer(1, 1, xDim, yDim, BytesInOneComponent(bitsInOneColor) * nColors);

   pWorkerThread_->Command(::StartSequence);

   LogMessage("Acquisition started");
   return DEVICE_OK;
}


/**
 * Stops Burst acquisition
 */
int CBaumerOptronic::StopSequenceAcquisition()
{
   pWorkerThread_->Command(StopSequence);

   unsigned long timeoutMs = 1000UL +
      static_cast<unsigned long>(pWorkerThread_->ExposureUs()) / 1000UL;
   MM::TimeoutMs timerOut(GetCurrentMMTime(), timeoutMs);

   while (Acquiring == pWorkerThread_->CameraState())
   {
      CDeviceUtils::SleepMs(1);
      if (timerOut.expired(GetCurrentMMTime()))
      {
         return DEVICE_SERIAL_TIMEOUT;
      }
   }
   // pWorkerThread_->cameraState_ = Ready;
   MM::Core* cb = GetCoreCallback();
   if (cb)
   {
      return cb->AcqFinished(this, 0);
   }
   else
   {
      return DEVICE_OK;
   }
}

int CBaumerOptronic::WaitForImageAndCopyToBuffer()
{
   // Wait for image and put it in img_
   unsigned xDim;
   unsigned yDim;
   int bitsInOneColor;
   int nPlanes;
   unsigned long bufSize;

   MMThreadGuard* pImageBufferGuard = NULL;
   void* p = pWorkerThread_->CurrentImage(xDim, yDim, bitsInOneColor, nPlanes,
         bufSize, &pImageBufferGuard);

   void* pixBuffer = NULL;
   if (p)
   {
      int bytesInOnePlane = BytesInOneComponent(bitsInOneColor);

      img_.Resize(xDim, yDim, nPlanes * bytesInOnePlane);

      pixBuffer = const_cast<unsigned char*>(img_.GetPixels());
      memcpy(pixBuffer, p, bufSize);
   }

   // release lock on image buffer
   if (pImageBufferGuard)
   {
      delete pImageBufferGuard;
   }

   return p ? DEVICE_OK : DEVICE_ERR;
}

/**
 * Puts image into staging buffer (img_), then inserts into curcular buffer
 */
int CBaumerOptronic::SendImageToCore()
{
   char label[MM::MaxStrLength];
   this->GetLabel(label);
   Metadata md;
   md.put("Camera", label);

   int err = WaitForImageAndCopyToBuffer();
   if (err != DEVICE_OK)
   {
      return err;
   }

   const unsigned char* p = GetImageBuffer();
   unsigned w = GetImageWidth();
   unsigned h = GetImageHeight();
   unsigned b = GetImageBytesPerPixel();

   int ret = GetCoreCallback()->InsertImage(this, p, w, h, b, md.Serialize().c_str());

   if (!stopOnOverflow_ && ret == DEVICE_BUFFER_OVERFLOW)
   {
      // do not stop on overflow - just reset the buffer
      GetCoreCallback()->ClearImageBuffer(this);
      return GetCoreCallback()->InsertImage(this, p, w, h, b, md.Serialize().c_str());
   }
   else
   {
      return ret;
   }
}


///////////////////////////////////////////////////////////////////////////////
// Private CBaumerOptronic methods
///////////////////////////////////////////////////////////////////////////////

/**
 * Sync internal image buffer size to the chosen property values.
 */
int CBaumerOptronic::ResizeImageBuffer()
{
   char buf[MM::MaxStrLength];

   int ret = GetProperty(MM::g_Keyword_PixelType, buf);
   if (ret != DEVICE_OK)
   {
      return ret;
   }


   unsigned xDim;
   unsigned yDim;
   int bitsInOneColor;
   int colors;
   unsigned long bufSize;
   pWorkerThread_->CurrentImageSize(xDim, yDim, bitsInOneColor, colors, bufSize);

   if ((0 == xDim) || (0 == yDim) || (0 == bitsInOneColor))
   {
      return DEVICE_ERR;
   }

   short nbytes = (short)(bitsInOneColor+7);
   nbytes /= 8;


   img_.Resize(xDim, yDim, colors*nbytes);
   return DEVICE_OK;
}



bool CBaumerOptronic::IsCapturing()
{
   return (Acquiring == pWorkerThread_->CameraState());
}
