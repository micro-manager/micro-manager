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
// CVS:           $Id: $

/**
Notes from Nico Stuurman who is trying to understand how this code is supposed to work. (feb. 2012).

Apart from the device adapter itself. there is a "WorkerThread" named
BOImplementationThread.  This BOImplementationThread handles all communication with the camera.  

The svc function of BOImplementationThread checks the value of flag cameraState_ and takes action 
accordingly. Thus, the adapter communicates with the ImplementationThread by setting the state of 
flag cameraState_.

On startup, the function BOImplementationThread::BOInitializationSequence() is called.  This function
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


#include <cstdio>
#include <string>
#include <math.h>
#include <process.h>
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMCore/Error.h"
#include "../../MMCore/CoreUtils.h"

#include <sstream>


#pragma warning(push)
#pragma warning(disable: 4245)  // bunch of nonsense in the B.O. library.
#pragma warning(disable: 4996)
#include "FxApi.h"
#include "FxError.h"
#pragma warning(pop)

// Disable warning for unused BOMSG(), defined in BoMsg.h (via FxApi.h).
// This warning is generated at the end of compilation, so cannot be disabled within push/pop.
#pragma warning(disable: 4505)


#include "BaumerOptronic.h"


using namespace std;


const double CBaumerOptronic::nominalPixelSizeUm_ = 1.0;
double g_IntensityFactor_ = 1.0;

// External names used used by the rest of the system
// to load particular device from the "BaumerOptronic.dll" library
const char* g_CameraDeviceName = "BaumerOptronic";

// constants for naming pixel types (allowed values of the "PixelType" property)
const char* g_PixelType_8bit = "8bit";
const char* g_PixelType_16bit = "16bit";
const char* g_PixelType_32bitRGB = "32bitRGB";
const char* g_PixelType_64bitRGB = "64bitRGB";

int gCameraId[16] = { -1 };		// max 16 cameras

// this appears to be used as a global pointer to memory used to deposit pixel data from the camera
void* pStatic_g = NULL; // need a static pointer for the non-member event handler function -- todo class static
// global used in the event handler to get the image size
unsigned long staticImgSize_g = 0; // ditto here

MMThreadLock acquisitionThreadTerminateLock_g;
bool mTerminateFlag_g = false;
bool imageReady_g = false;

// globals used to store data as reported by the camera
unsigned short xDim_g;
unsigned short yDim_g;
unsigned short bitsInOneColor_g;

unsigned short BytesInOneComponent(unsigned short bitsInOneColor) 
{
   short ret = (short)(bitsInOneColor + 7);
   ret = (short)(ret/8);
   return ret;
}
   

// BO library sends out two possible RGB formats, both with code BOIMF_RGB
// if planes = 3 the R, G, and B images are sent out successively
// if 'canals' =3, the RGB pixels are interleaved.
unsigned short nPlanes_g;
unsigned short nCanals_g;
unsigned short iCode_g;


// turn acquisition on & off
//MMThreadLock acquisitionSequenceLock_g;
bool seqactive_g = false;


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

/**
 * List all supported hardware devices here
 * Do not discover devices at runtime.  To avoid warnings about missing DLLs, Micro-Manager
 * maintains a list of supported device (MMDeviceList.txt).  This list is generated using 
 * information supplied by this function, so runtime discovery will create problems.
 */
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_CameraDeviceName, "Baumer Optronic driver for Leica Cameras");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

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
unsigned int __stdcall mSeqEventHandler( void*  );
unsigned long synchTimeout_g = 5000; // milliseconds to wait for image ready event


#define FXOK 1


BOImplementationThread::BOImplementationThread(CBaumerOptronic* pCamera) : 
   intervalMs_(100.0), 
   numImages_(1), 
   stop_(false), 
   acquisitionThread_(NULL), 
   quantizedExposure_(0), 
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
   cameraState_(Idle), 
   pCamera_(pCamera), 
   partialScanMode_(false) 
{
   imageEventArray_[0] = NULL; 
   imageEventArray_[1] = NULL; 
   memset(&roi_,0,sizeof(RECT));
}


BOImplementationThread::~BOImplementationThread()
{
   bufSize_ = 0;
   if(NULL!=pBuf_)
      free(pBuf_);

   colorBufSize_ = 0;
   if(NULL!= pColorBuf_)
      free(pColorBuf_);

   if(NULL !=  imageEventArray_[0])
      ::CloseHandle( imageEventArray_[0]);
   mTerminateFlag_g = false;
}

void BOImplementationThread::TriggerMode( const bool v) 
{  
   MMThreadGuard g( stateMachineLock_); 
   WorkerState prevState(cameraState_); 
   cameraState_= Busy; 
   triggerMode_ = v; 
   (void)FX_SetTriggerMode(  gCameraId[0], triggerMode_, NULL );
   cameraState_=prevState;
}

void BOImplementationThread::Exposure( int v ) 
{
   MMThreadGuard g( stateMachineLock_); 
   WorkerState prevState(cameraState_); 
   cameraState_= Busy;
   tEXPO m_Expo;
   int fxr = FX_SetExposureTime( gCameraId[0], v,  &m_Expo); 
   if(CE_SUCCESS!=fxr) 
      LLogMessage(IntelligentErrorString(fxr).c_str());
   else
   {
      quantizedExposure_ = m_Expo.timeNear; 
      LLogMessage(std::string("exposure set to ") + 
         boost::lexical_cast<std::string,int>(quantizedExposure_), true);
   }
   cameraState_=prevState;
}

void BOImplementationThread::Gain(double v) 
{ 
   MMThreadGuard g( stateMachineLock_); 
   WorkerState prevState(cameraState_); 
   cameraState_= Busy; 
   tGAIN m_Gain; 
   (void)FX_SetGainFactor( gCameraId[0], v,  &m_Gain); 
   quantizedGain_ = m_Gain.gainNear; 
   cameraState_=prevState;
}


MMThreadLock BOImplementationThread::imageReadyLock_s;
MMThreadLock BOImplementationThread::imageBufferLock_s;

void BOImplementationThread::Snap(void)
{
   // XXX BUG: We can end up grabbing an image whose exposure started before
   // the SnapImage() call commenced.
   try
   {
      if( Ready == CameraState())
      {
         {
            MMThreadGuard guard(BOImplementationThread::imageReadyLock_s);
            imageReady_g = false;
         }

         MMThreadGuard g( stateMachineLock_);
         WorkerState prevState = cameraState_;
         cameraState_ = Snapping;
   
         tBoCameraType   dcBoType;               // Cameratype struct
         tBoCameraStatus dcBoStatus;             // Camerastatus struct

         dcBoType.iSizeof = sizeof(dcBoType);
         dcBoStatus.iSizeof = sizeof(dcBoStatus);
         FX_GetCameraInfo( gCameraId[0], &dcBoType, &dcBoStatus );
         //maxxDim_ = dcBoType.wTotalFrmSizeX;
         //maxyDim_ = dcBoType.wTotalFrmSizeY;

         unsigned long sz = dcBoStatus.iNumImgCodeBytes;

         {
            // lock access to the static buffer, buffersize
            MMThreadGuard g( BOImplementationThread::imageBufferLock_s);
            if( sz != bufSize_)
            {
               if ( NULL != pBuf_)
               {
                  free( pBuf_);
                  pBuf_ = NULL;
                  bufSize_ = 0;
               }
               // malloc is slightly faster than new
               pBuf_ = malloc( sz);
               if ( NULL != pBuf_)
                  bufSize_ = sz;
            }

            pStatic_g = pBuf_;
            staticImgSize_g = bufSize_;
         }


         if( 0 < bufSize_)
         {
            seqactive_g = true;
         }
         cameraState_ = prevState;

      }
   } catch(...){}

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
void BOImplementationThread::Acquire(void)
{
   try
   {
      {
         MMThreadGuard guard(BOImplementationThread::imageReadyLock_s);
         imageReady_g = false;
      }

      tBoCameraType   dcBoType;               // Cameratype struct
      tBoCameraStatus dcBoStatus;             // Camerastatus struct

      dcBoType.iSizeof = sizeof(dcBoType);
      dcBoStatus.iSizeof = sizeof(dcBoStatus);
      FX_GetCameraInfo( gCameraId[0], &dcBoType, &dcBoStatus );

      unsigned long sz = dcBoStatus.iNumImgCodeBytes;

      {
         // lock access to the static buffer, buffersize
         MMThreadGuard g( BOImplementationThread::imageBufferLock_s);
         if( sz != bufSize_)
         {
            if ( NULL != pBuf_)
            {
               free( pBuf_);
               pBuf_ = NULL;
               bufSize_ = 0;
            }
            // malloc is slightly faster than new
            pBuf_ = malloc( sz);
            if ( NULL != pBuf_)
               bufSize_ = sz;
         }

         pStatic_g = pBuf_;
         staticImgSize_g = bufSize_;
      }


      if( 0 < bufSize_)
      {
         seqactive_g = true;
      }

   } catch(...){}

   return;
}


void* BOImplementationThread::CurrentImage( unsigned short& xDim,  unsigned short& yDim, 
                                          unsigned short& bitsInOneColor, unsigned short& nColors,  
                                          unsigned long& bufSize, MMThreadGuard** ppImageBufferGuard)
{
   unsigned short nCanals;
   void* bufferToReturn = NULL;
   *ppImageBufferGuard = NULL;

   MM::TimeoutMs timerOut( CurrentMMTimeMM(), (long)(Exposure() + 5000.0) );
   for (;;)
   {
      CDeviceUtils::SleepMs(0);
      MMThreadGuard guard(BOImplementationThread::imageReadyLock_s);
      if( imageReady_g)
         break;
      if( timerOut.expired(CurrentMMTimeMM()))
      {
         PostError(DEVICE_SERIAL_TIMEOUT, "in CurrentImage");
         return NULL;
      }
   }
 
   // XXX BUG: We never reset seqactive_g if GetImageBuffer() is not called
   seqactive_g = false;

   MMThreadGuard* g = new MMThreadGuard(BOImplementationThread::imageBufferLock_s);
   bufSize = bufSize_ = staticImgSize_g;
   xDim =  xDim_ = xDim_g;
   yDim = yDim_ = yDim_g;
   bitsInOneColor = bitsInOneColor_ = bitsInOneColor_g;
   nPlanes_ = nPlanes_g;
   nCanals = nCanals_g;
   delete (g);


   // color images need to be dithered with an empty color for gui
   if (BOIMF_RGB == ImageCode().iCode)
   {
      nColors = 4;      
      const unsigned long ditheredSize = xDim * yDim * BytesInOneComponent(bitsInOneColor) * nColors;
      {
         colorBufSize_ = 0;
         if(NULL!= pColorBuf_)
         {
            free(pColorBuf_);
         }
         // malloc is slightly faster than new
         pColorBuf_ = malloc( ditheredSize);
         if( NULL != pColorBuf_)
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
      if( 0 != ditheredSize)   
         memset(pColorBuf_, 0, ditheredSize);

      // start of access to image buffer
      *ppImageBufferGuard = new MMThreadGuard(BOImplementationThread::imageBufferLock_s);

      if( 8 >= bitsInOneColor_) // pixels are 1 byte per color
      {
         char* pInput = static_cast<char*>(pBuf_);
         char* pOutput = static_cast<char*>(pColorBuf_);
         unsigned long nPix = xDim * yDim;
         unsigned long thePixel;
         if( (1 == nPlanes_) && (3 == nCanals)) // interleaved RGB
         {
            for( thePixel = 0; thePixel < nPix; ++thePixel)
            {
               *pOutput++ = *pInput++;// R
               *pOutput++ = *pInput++;// G
               *pOutput++ = *pInput++;// B
               ++pOutput;             // empty
            }

         }
         else if ( (3 == nPlanes_) && (1 == nCanals)) // R plane G plane B plane
         {
            for( thePixel = 0; thePixel < nPix; ++thePixel)
            {
               *pOutput++ = pInput[thePixel]; // R
               *pOutput++ = pInput[thePixel + nPix];// G
               *pOutput++ =  pInput[thePixel + 2*nPix];// B
               ++pOutput;             // empty
            }
         }
      }
      else  // pixels are 2 bytes per color
      {
         short* pInput = static_cast<short*>(pBuf_);
         short* pOutput = static_cast<short*>(pColorBuf_);
         unsigned long nPix = xDim * yDim;
         unsigned long thePixel;
         if( (1 == nPlanes_) && (3 == nCanals)) // interleaved RGB
         {
            for( thePixel = 0; thePixel < nPix; ++thePixel)
            {
               *pOutput++ = *pInput++;// R
               *pOutput++ = *pInput++;// G
               *pOutput++ = *pInput++;// B
               ++pOutput;             // empty
            }

         }
         else if ( (3 == nPlanes_) && (1 == nCanals)) // R plane G plane B plane
         {
            for( thePixel = 0; thePixel < nPix; ++thePixel)
            {
               *pOutput++ = pInput[thePixel]; // R
               *pOutput++ = pInput[thePixel + nPix];// G
               *pOutput++ =  pInput[thePixel + 2*nPix];// B
               ++pOutput;             // empty
            }

         }
      }
   }
   else  // monochrome images
   {
      nColors = 1;
      // start of access to image buffer
      *ppImageBufferGuard = new MMThreadGuard(BOImplementationThread::imageBufferLock_s);
      bufferToReturn = pBuf_;

   }

   return bufferToReturn;
}


void BOImplementationThread::CurrentImageSize( unsigned short& xDim,  unsigned short& yDim, unsigned short& bitsInOneColor, unsigned short& nColors,  unsigned long& bufSize)
{
   MMThreadGuard g(mmCameraLock_);
   xDim =  xDim_;
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
   std::pair< double, double> ret(0.,0.);
   MMThreadGuard g( stateMachineLock_);
   WorkerState prevState(cameraState_);
   cameraState_= Busy; 
   tGAIN m_Gain; 
   // specifying 0 returns gain range
   (void)FX_SetGainFactor( gCameraId[0], 0.,  &m_Gain);
   ret = std::make_pair( m_Gain.gainMin, m_Gain.gainMax);
   cameraState_= prevState;
   return ret;
};

// return possible exposure range (in micro-seconds)
std::pair<int, int> BOImplementationThread::ExposureLimits()
{
   std::pair< int, int> ret(0,0);
   MMThreadGuard g( stateMachineLock_);
   WorkerState prevState(cameraState_);
   cameraState_= Busy; 
   tEXPO m_Exposure; 
   // specifying 0 returns exposure range
   (void)FX_SetExposureTime( gCameraId[0], 0,  &m_Exposure);
   ret = std::make_pair( m_Exposure.timeMin, m_Exposure.timeMax);
   cameraState_= prevState;
   return ret;
};


void BOImplementationThread::QueryCapabilities(void)
{
   MMThreadGuard g( stateMachineLock_);
	int nImgFormat;	
	tpBoImgFormat*  aImgFormat;
   //query image formats
	int fxret = FX_GetCapability (gCameraId[0], BCAM_QUERYCAP_IMGFORMATS, 0/*UNUSED*/, (void**)&aImgFormat, &nImgFormat );



   if( 1 == fxret)
   {
		//tBoImgFormat* m_aImgFormat = (tBoImgFormat* )new tBoImgFormat[nImgFormat];

      // copy out of library's local memory

		for( int i = 0; i < nImgFormat; i++ ) 
      {
         NamedFormat f = std::make_pair( std::string(aImgFormat[i]->aName), *(aImgFormat[i])  );
         formats.push_back(f);

         ImageCodesPerFormat theseCodes;
         ImageFiltersPerFormat theseFilters;
         int nattributesAvailable;
         int jj;

         //query image codes
	      tpBoImgCode * 	 aImgCode;
         fxret = FX_GetCapability( gCameraId[0], BCAM_QUERYCAP_IMGCODES, f.second.iFormat, (void**)&aImgCode, &nattributesAvailable );
         if( 1 == fxret)
         {
            for( jj = 0; jj < nattributesAvailable; ++jj)
            {
               tBoImgCode tvalue = *(aImgCode[jj]);
               theseCodes.push_back( tvalue );
            }

         }
         // query image filters for this format
	      tpBoImgFilter * 	 aImgFilter;
	      int fxret = FX_GetCapability( gCameraId[0], BCAM_QUERYCAP_IMGFILTER, f.second.iFormat, (void**)&aImgFilter, &nattributesAvailable );
         if( 1 == fxret)
         {
            for( jj = 0; jj < nattributesAvailable; ++jj)
            {
               tBoImgFilter tvalue = *(aImgFilter[jj]);
               theseFilters.push_back( tvalue);
            }
         }

         // enumerate some more capabilities....
         tpBoCamFunction *       aCamFunction;
         int nCamFunction;
         fxret = FX_GetCapability(  0, BCAM_QUERYCAP_CAMFUNCTIONS, f.second.iFormat, (void**)&aCamFunction, &nCamFunction );
         std::ostringstream oss;
         oss << " for format " << f.first << "  aux camera capabilites:";

         if( 1 == fxret)
         {
            for( int i = 0; i < nCamFunction; i++ ) 
            {
               oss << "\n 0x" << std::hex << aCamFunction[i]->iFunction << "\t" << std::string(aCamFunction[i]->aName);
            }
         }
         else
         {
            oss << " error retrieving auxilliary capabilites";
         }

         LLogMessage(oss.str().c_str(), true);


         CompleteFormat f0;
         f0.f_ = f.second;
         f0.formatIndex_ = i;
         f0.name_ = f.first;
         f0.codes_ = theseCodes;
         f0.filters_ = theseFilters;
         completeFormats_.push_back ( f0 );

   	}


   }


}


// iterate through formats and make vectors of properties

void BOImplementationThread::ParseCapabilities(void)
{
   MMThreadGuard g( stateMachineLock_);
   std::vector< CompleteFormat >::iterator i;

   for ( i = completeFormats_.begin(); i != completeFormats_.end(); ++i)
   {
      int thisBin = 1;
      std::vector< std::string > tokens;
      CDeviceUtils::Tokenize( i->name_, tokens, " ");
      std::vector< std::string >::iterator ti;
      for( ti = tokens.begin(); ti != tokens.end(); ++ti)
      {
         if (*ti == "Binning")
         {
            std::istringstream ts(*(ti+1));
            ts>>thisBin;
            break;
         }

      }
      if( (3!= thisBin) && binSizes_.end() == std::find( binSizes_.begin(), binSizes_.end(), thisBin))
         binSizes_.push_back(thisBin);
      
      FrameSize thisSize = std::make_pair<int, int> ( i->f_.iSizeX, i->f_.iSizeY);
      if( frameSizes_.end() == std::find( frameSizes_.begin(), frameSizes_.end(), thisSize))
         frameSizes_.push_back( thisSize);

      int thisDepth = i->f_.iPixelBits;
      if ( pixelDepths_.end() ==  std::find( pixelDepths_.begin(), pixelDepths_.end(), thisDepth))
         pixelDepths_.push_back( thisDepth);

      ImageCodesPerFormat::iterator jj;

      for ( jj = i->codes_.begin(); jj != i->codes_.end(); ++jj)
      {

         int thisChannelBits = jj->iCanalBits;
         if (possibleBitsInOneColor_.end() == std::find( possibleBitsInOneColor_.begin(), possibleBitsInOneColor_.end(), thisChannelBits))
            possibleBitsInOneColor_.push_back(thisChannelBits);

         int planes = jj->iPlanes;
         if( this->possibleNPlanes_.end() == std::find( possibleNPlanes_.begin(), possibleNPlanes_.end(), planes))
            possibleNPlanes_.push_back( planes);
      }

      ImageFiltersPerFormat::iterator kk;

      for( kk = i->filters_.begin(); kk != i->filters_.end(); ++kk)
      {
         std::string filterName = kk->aName;
         if (filters_.end() == filters_.find(kk->imgFilterCode))
            filters_[kk->imgFilterCode] = filterName;

      }
   }
   QueryCameraCurrentFormat();
}

void BOImplementationThread::QueryCameraCurrentFormat(void)
{
   tBoCameraType   dcBoType;               // Cameratype struct
   tBoCameraStatus dcBoStatus;             // Camerastatus struct

   dcBoType.iSizeof = sizeof(dcBoType);
   dcBoStatus.iSizeof = sizeof(dcBoStatus);
   int fxRet = FX_GetCameraInfo( gCameraId[0], &dcBoType, &dcBoStatus );
   ::iCode_g = static_cast<unsigned short>(dcBoStatus.eCurImgCode.iCode); 
   if( CE_SUCCESS != fxRet)
   {
      PostError(MMERR_GENERIC, IntelligentErrorString(fxRet).c_str());
   }
   else
   {
      bitsInOneColor_ = static_cast<unsigned short>(dcBoStatus.eCurImgCode.iCanalBits);
      nPlanes_ =  static_cast<unsigned short>(dcBoStatus.eCurImgCode.iPlanes);
   }

   FindImageFormatInFormatCache(dcBoStatus.eCurImgFormat);
}


int BOImplementationThread::svc(void)
{
   // loop in this working thread until the camera is shutdown.
   for (;;)
   {
      if( Exit == Command())
      {
         if ( Idle < CameraState() )
         {
            try
            { 
               // kill the acquisition thread
               {
                  LLogMessage( "Send termination request to BO acquisition thread ", true);
                  MMThreadGuard g(::acquisitionThreadTerminateLock_g);
                  mTerminateFlag_g = true;	
               }

               LLogMessage( "sent terminate request to BO acquisition thread ", true);
		         ::TerminateThread( acquisitionThread_, 0 );
               LLogMessage( "BO acquisition thread terminated ", true);
		         CloseHandle( acquisitionThread_ );
               acquisitionThread_= NULL;

               MMThreadGuard g( stateMachineLock_);
               int fxReturn = FX_CloseCamera( gCameraId[0] );
               if( 1 != fxReturn)
               {
                  std::ostringstream oss;
                  oss << "FX_CloseCamera error: 0x" << ios::hex << fxReturn;
                  LLogMessage(oss.str().c_str());
               }
      		   fxReturn = FX_DeInitLibrary();
               if( 1 != fxReturn)
               {
                  std::ostringstream oss;
                  oss << "FX_DeInitLibrary error: 0x" << ios::hex << fxReturn;
                  LLogMessage(oss.str().c_str());
               }
            }
            catch(...){}
         }
         Stop();
         //LLogMessage( "stop request sent to CCamera acquisition thread ", true);
         //wait();
         //Command(Noop);
         break;
      }

      switch( CameraState())
      {

         case Idle: // wait for initialization request

            if( InitializeLibrary == Command())
            {
               int fxReturn = BOInitializationSequence();
               TriggerMode(triggerMode_); // write the initialized value down to the HW
               Command(Noop);
               if ( 1 == fxReturn)
                  CameraState(Ready); // camera is ready to take images.
            }
            break;

         case Ready: //ready for a operational command
            if( StartSequence == Command())
            {
                 Command(Noop);
                 CameraState(Acquiring);
            } 
            else if ( SnapCommand == Command())
            {
               MMThreadGuard( this->mmCameraLock_);
               Snap();
               Command(Noop);
            }
            break;

         case Acquiring:  // sequence Acquisition processing
            {
               int ret = DEVICE_ERR;

               // checks size of buffer and allocates a new one if needed
               Acquire();

               // complicated way to wait for one exposure time
               // XXX BUG: "Exposure() / 1000" is zero unless Exposure > 1000.
               MM::TimeoutMs timerOut(CurrentMMTimeMM(), Exposure() / 1000 );
               for (;;)
               {
                  if( StopSequence == Command())
                  {
                     Command(Noop);
                     CameraState(Ready);
                     break;
                  }
                  if( Exit == Command())
                  {
                     CameraState(Ready);
                     break;
                  }
                  if (timerOut.expired(CurrentMMTimeMM())) 
                     break;

                  Sleep(0);
               }


               //if (ret != DEVICE_OK)
               //{
               //   std::ostringstream mezz;
               //   mezz <<  "in SnapImage";
               //   pCamera_->GetCoreCallback()->PostError(std::make_pair( (int)ret, mezz.str()));
               //   CameraState(Ready);
               //}
               // hmmm.
               MMThreadGuard g(mmCameraLock_);
               
               
               // GetImageBuffer and copy pixels into circular buffer
               ret = pCamera_->InsertImage();
               if (ret != DEVICE_OK)
               {
                  ostringstream os;
                  os << "InsertImage failed with errorcode: " << ret;
                  pCamera_->GetCoreCallback()->PostError(ret, os.str().c_str() );
                  CameraState(Ready);
                  break;
               }
               ++frameCount_;
               if (numImages_ <= frameCount_)
               {
                  CameraState(Ready);
               }
            }
            break;

         default:
            CameraState(Idle);
            break;
      }
      CDeviceUtils::SleepMs(0);
   }

   Command(Noop);
   LLogMessage( "CCamera acquisition thread is exiting... ", true);
   return 0;
}




// enumerate capabilities and formats



#if 0

int CCameraFx::EnumImgMode()
{
	int 	        nImgFormat;	
	tpBoImgFormat*  aImgFormat;
	int nRetCode = wrapFX_GetCapability( m_Define.CamLabel, 
						BCAM_QUERYCAP_IMGFORMATS, 0/*UNUSED*/, (void**)&aImgFormat, &nImgFormat );
	
	if( nRetCode == TRUE ) {
		if( m_aImgFormat ) 
			delete[] m_aImgFormat;
        if( m_aImgFormatNames ) 
			delete[] m_aImgFormatNames;
		m_aImgFormat = (tBoImgFormat* )new tBoImgFormat[nImgFormat];
        m_aImgFormatNames = (tPH128*  )new tPH128[nImgFormat];
		for( int i = 0; i < nImgFormat; i++ ) {
			memcpy( &m_aImgFormat[i], aImgFormat[i], sizeof(tBoImgFormat));
            strncpy( m_aImgFormatNames[i].placeholder, aImgFormat[i]->aName, min( 128, strlen(aImgFormat[i]->aName)));
            m_aImgFormatNames[i].placeholder[min( 127, strlen(aImgFormat[i]->aName))] = 0;
            m_aImgFormat[i].aName = (char*)m_aImgFormatNames[i].placeholder;
   		}
		m_nImgFormat = nImgFormat;
		return nImgFormat;
	}
	return 0;	
}

int CCameraFx::EnumMonMode()
{
	int 	        nMonitorFormat;	
	tpBoMonFormat*  aMonitorFormat;
	int nRetCode = wrapFX_GetCapability( m_Define.CamLabel, 
						BCAM_QUERYCAP_MONFORMATS, 0/*UNUSED*/, (void**)&aMonitorFormat, &nMonitorFormat );
	
	if( nRetCode == TRUE ) {
		if( m_aMonitorFormat ) 
			delete[] m_aMonitorFormat;
        if( m_aMonitorFormatNames ) 
			delete[] m_aMonitorFormatNames;
		m_aMonitorFormat = (tBoMonFormat* )new tBoMonFormat[nMonitorFormat];
        m_aMonitorFormatNames = (tPH128*  )new tPH128[nMonitorFormat];
		for( int i = 0; i < nMonitorFormat; i++ ) {
			memcpy( &m_aMonitorFormat[i], aMonitorFormat[i], sizeof(tBoMonFormat));
            strncpy( m_aMonitorFormatNames[i].placeholder, aMonitorFormat[i]->aName, min( 128, strlen(aMonitorFormat[i]->aName)));
            m_aMonitorFormatNames[i].placeholder[min( 127, strlen(aMonitorFormat[i]->aName))] = 0;
            // m_aMonitorFormat[i].aName = (char*)m_aMonitorFormatNames[i].placeholder;
   		}
		m_nMonitorFormat = nMonitorFormat;
		return nMonitorFormat;
	}
	return 0;	
}




int CCameraFx::EnumImgCode( int iFormat ) 
{
	int 	         nImgCode;
	tpBoImgCode * 	 aImgCode;
	int nRetCode = wrapFX_GetCapability(  m_Define.CamLabel, BCAM_QUERYCAP_IMGCODES, iFormat, (void**)&aImgCode, &nImgCode );

	if( nRetCode == TRUE ) {
		if( m_aImgCode ) 
			delete[] m_aImgCode;
		m_aImgCode = (tBoImgCodeS *) new tBoImgCodeS[nImgCode];
		for( int i = 0; i < nImgCode; i++ ) {
			memcpy( &m_aImgCode[i], aImgCode[i], sizeof(tBoImgCodeS));
   		}
		m_nImgCode = nImgCode;
		return nImgCode;
	}
	return 0;	
}

int CCameraFx::EnumImgIpol( int iFormat ) 
{
	int 	         nImgIpol;
	tpBoRawInterpolation * 	 aImgIpol;
	int nRetCode = wrapFX_GetCapability(  m_Define.CamLabel, BCAM_QUERYCAP_RAWINTERPOL,  iFormat, (void**)&aImgIpol, &nImgIpol );

	if( nRetCode == TRUE ) {
		if( m_aImgIpol ) 
			delete[] m_aImgIpol;
		m_aImgIpol = (tBoRawInterpolation *) new tBoRawInterpolation[nImgIpol];
		for( int i = 0; i < nImgIpol; i++ ) {
			memcpy( &m_aImgIpol[i], aImgIpol[i], sizeof(tBoRawInterpolation));
   		}
		m_nImgIpol = nImgIpol;
		return nImgIpol;
	}
	else {
		if( m_aImgIpol ) 
			delete[] m_aImgIpol;
		m_aImgIpol = NULL;
		m_nImgIpol = nImgIpol; 
		return 0;	
	}
}

int CCameraFx::EnumImgFilter( int iFormat ) 
{
	int 	         nImgFilter;
	tpBoImgFilter * 	 aImgFilter;
	int nRetCode = wrapFX_GetCapability(  m_Define.CamLabel, BCAM_QUERYCAP_IMGFILTER,  iFormat, (void**)&aImgFilter, &nImgFilter );

	if( nRetCode == TRUE ) {
		if( m_aImgFilter) 
			delete[] m_aImgFilter;
		m_aImgFilter = (tBoImgFilter *) new tBoImgFilter[nImgFilter];
		for( int i = 0; i < nImgFilter; i++ ) {
			memcpy( &m_aImgFilter[i], aImgFilter[i], sizeof(tBoImgFilter));
   		}
		m_nImgFilter = nImgFilter;
		return nImgFilter;
	}
	else {
		if( m_aImgFilter ) 
			delete[] m_aImgFilter;
		m_aImgFilter = NULL;
		m_nImgFilter = nImgFilter; 
		return 0;	
	}
}
#endif


int BOImplementationThread::BOInitializationSequence(void)
{
   int fxReturn = 0;
   try
   {
      MMThreadGuard g(stateMachineLock_);
      //todo  ::  the printf's are from Baumer's example program, replace with std::ostringstream
      char fxMess[500];
      fxMess[0] = 0;

      fxReturn = FX_DeleteLabelInfo();

      // **** Init Library 
      fxReturn = FX_InitLibrary();
      if( FXOK == fxReturn)
      {
         // **** Enumerate all 1394 devices ***************
         int DevCount; // number of cameras
         fxReturn = FX_EnumerateDevices(  &DevCount );
         if ( FXOK == fxReturn)
         {
            if( 1 == DevCount ) 
            {
               // **** Label a special device **********************
               gCameraId[0] = 0;
               fxReturn = FX_LabelDevice( 0, gCameraId[0] );  
               if ( FXOK == fxReturn)
               {
                  // **** Open a labeled device ********************
                  fxReturn = FX_OpenCamera(gCameraId[0]);   
                  if ( FXOK == fxReturn)
                  {
                     std::ostringstream oss;
                     oss << "Opened Leica / Baumer Optronic Camera # " << gCameraId[0];
                     LLogMessage(oss.str().c_str(), false);

                     imageEventArray_[0] = ::CreateEvent(NULL,FALSE,FALSE,NULL); 
                     imageEventArray_[1] = NULL;
                     unsigned int tempthid = 0;
                     // Install Image Event Handler Thread for incoming data
		               acquisitionThread_ = (HANDLE)_beginthreadex( NULL, 0, &mSeqEventHandler, 
                        (PVOID)imageEventArray_, 0, &tempthid);
                     std::ostringstream s2;
                     s2 <<  " BO acquistion thread id " << std::hex << acquisitionThread_ << " was started ... ";
                     LLogMessage(s2.str().c_str(), true);
                     ::SetThreadPriority(acquisitionThread_, THREAD_PRIORITY_TIME_CRITICAL);  //?? really

                     fxReturn = FX_DefineImageNotificationEvent( gCameraId[0], imageEventArray_[0] );
                     if ( FXOK == fxReturn)
                     {
                        // **** Allocate Buffers ********************
                        fxReturn = FX_AllocateResources( gCameraId[0], 10, 0 ); 
                        if ( FXOK == fxReturn)
                        {
                           // **** Start capture process********************
                           fxReturn = FX_StartDataCapture(  gCameraId[0], TRUE  );
                           if ( FXOK != fxReturn)
                           {
                              sprintf(fxMess,"FX_StartDataCapture error: %08x", fxReturn );
                              LLogMessage( fxMess);
                           }
                        }
                        else
                        {
                           sprintf(fxMess,"FX_AllocateResources error: %08x", fxReturn );
                           LLogMessage( fxMess);
                        }
                     }
                     else
                     {
                        sprintf(fxMess,"FX_DefineImageNotificationEvent error: %08x", fxReturn );
                        LLogMessage( fxMess);
                     }
                  }
                  else
                  {
                     sprintf(fxMess,"FX_OpenCamera error: %08x", fxReturn );
                     LLogMessage( fxMess);
                  }
               }
               else
               {
                  sprintf(fxMess,"FX_LabelDevice error: %08x", fxReturn );
                  LLogMessage( fxMess);
               }
            }
            else
            {
               sprintf(fxMess,"# cameras must be 1, but %d cameras found \n", DevCount );	
               LLogMessage( fxMess);
            }
         }
         else
         {
            sprintf(fxMess,"FX_EnumerateDevices error: %08x", fxReturn );
            LLogMessage( fxMess);
         }

      }
      else
      {
         sprintf(fxMess,"FX_InitLibrary error: %08x", fxReturn );
         LLogMessage( fxMess);
      }

   }
   catch(...){}
   if (1 == fxReturn)
      LLogMessage(" BO camera library initialized OK!", true);
   return fxReturn;
}


void BOImplementationThread::LLogMessage(const std::string message, const bool debugOnly)
{
   LLogMessage(message.c_str(), debugOnly);
}


void BOImplementationThread::LLogMessage(const char* pMessage, const bool debugOnly)
{
   if( NULL != pCamera_)
   {
      MMThreadGuard g(mmCameraLock_);
      pCamera_->LogMessage(pMessage, debugOnly);
   }

}


MM::MMTime BOImplementationThread::CurrentMMTimeMM() // MMTime as milliseconds
{
   MM::MMTime ret(0);
   if( 0 != pCamera_)
      ret = pCamera_->GetCurrentMMTime();
   return ret;
}





void BOImplementationThread::PostError(const int errorCode, const char* pMessage)
{
   if( NULL != pCamera_)
   {
//      printf( "%d %s\n", errorCode,pMessage);
      MMThreadGuard g(mmCameraLock_);
      pCamera_->GetCoreCallback()->PostError( errorCode, pMessage  );
   }
}



int BOImplementationThread::BinSize(void) const
{
   return BinSizeFromCompleteFormat(this->completeFormatIter_);

}


int BOImplementationThread::BinSizeFromCompleteFormat(std::vector< CompleteFormat >::iterator i) const
{
   int thisBin = 1;
   if (completeFormats_.end() != i)
   {
      std::vector< std::string > tokens;
      CDeviceUtils::Tokenize( i->name_, tokens, " ");
      std::vector< std::string >::iterator ti;
      for( ti = tokens.begin(); ti != tokens.end(); ++ti)
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
int BOImplementationThread::BitDepthFromCompleteFormat(std::vector< CompleteFormat >::iterator i)
{
   return i->f_.iPixelBits;
}

tBoImgCode BOImplementationThread::ImageCode(void)
{
   tBoCameraType   dcBoType;               // Cameratype struct
   tBoCameraStatus dcBoStatus;             // Camerastatus struct

   dcBoType.iSizeof = sizeof(dcBoType);
   dcBoStatus.iSizeof = sizeof(dcBoStatus);
   int fxRet = FX_GetCameraInfo( gCameraId[0], &dcBoType, &dcBoStatus );
   ::iCode_g = static_cast<unsigned short>(dcBoStatus.eCurImgCode.iCode); 

   if( 1 != fxRet)
      PostError(MMERR_GENERIC, IntelligentErrorString(fxRet).c_str());

   return dcBoStatus.eCurImgCode;

}


bool  BOImplementationThread::ImageFormat( int imageFormatIndex)
{
   int fxret = 0;
   bool ret = false;

   fxret = FX_SetImageFormat( gCameraId[0], imageFormatIndex);

   if( 1 == fxret)
   {
      std::vector< CompleteFormat >::iterator i;
      for ( i = completeFormats_.begin(); i != completeFormats_.end(); ++i)
      {
         if( imageFormatIndex == i->formatIndex_)
         {
            ret = true;
            completeFormatIter_ = i;
            break;
         }
      }
   }
   return ret;

}


void BOImplementationThread::FindImageFormatInFormatCache( int imageFormatIndex)
{
   completeFormatIter_ = completeFormats_.end();
   std::vector< CompleteFormat >::iterator i;
   for ( i = completeFormats_.begin(); i != completeFormats_.end(); ++i)
   {
      if( imageFormatIndex == i->formatIndex_)
      {
         completeFormatIter_ = i;break;
      }
   }
}


void BOImplementationThread::BinSize(const int v)
{
   if (v != BinSizeFromCompleteFormat(completeFormatIter_))
   {
      // what image format parameters are selected?
      tBoImgCode currentCode = ImageCode();

      //  don't understand meaning of 'valid bits' inside BitDepthFromCompleteFormat:
      //int currentBitDepth = BitDepthFromCompleteFormat(completeFormatIter_);
      //int currentBitDepth = currentCode.iCanalBits;

      std::vector< CompleteFormat >::iterator i;
      bool foundMatch = false;
      for ( i = completeFormats_.begin(); i != completeFormats_.end(); ++i)
      {
         if( v == BinSizeFromCompleteFormat(i))
         {
            ImageCodesPerFormat::iterator jj;
            for ( jj = i->codes_.begin(); jj != i->codes_.end(); ++jj)
            {
               // todo don't really need same code, just same # bits would be good.
               if (currentCode == *jj)
               {
                  if(ImageFormat( i->formatIndex_))
                  {
                     // N.B. TURNS OFF ROI at this point!! this could be improved to recalculated roi each time binsize is changed
                     partialScanMode_ = false;
                     int fxr =  FX_SetImageCode( gCameraId[0], currentCode);
                     if( 1 == fxr)
                     {
                        foundMatch = true;
                        QueryCameraCurrentFormat();
                        break;
                     }
                  }
               }
               if (foundMatch)
                  break;
            }
            if (foundMatch)
               break;
         }
      }
      if( ! foundMatch)
      {
         std::ostringstream oss;
         oss << " in BinSize can not set bins to " << v;
         PostError(MMERR_GENERIC, oss.str().c_str());
      }
   }
}

void BOImplementationThread::BitsInOneColor( const int bits)
{

   MMThreadGuard g( stateMachineLock_);
   WorkerState prevState(cameraState_); 
   cameraState_= Busy;

      // what image format parameters are currently selected?
   tBoImgCode currentCode = ImageCode();


   int currentBits = currentCode.iCanalBits;
   if( currentBits != bits)
   {
      int currentBin = this->BinSize();
      std::vector< CompleteFormat >::iterator i;
      bool foundMatch = false;
      for ( i = completeFormats_.begin(); i != completeFormats_.end(); ++i)
      {
         if( currentBin == BinSizeFromCompleteFormat(i))
         {
            ImageCodesPerFormat::iterator jj;
            for ( jj = i->codes_.begin(); jj != i->codes_.end(); ++jj)
            {
               if (bits == jj->iCanalBits)
               {
                  if(ImageFormat( i->formatIndex_))
                  {
                     //here do i need recalculate ROI??

                     int fxr =  FX_SetImageCode( gCameraId[0], *jj);
                     if( 1 == fxr)
                     {
                        foundMatch = true;
                        QueryCameraCurrentFormat();
                        break;
                     }
                  }
               }
               if (foundMatch)
                  break;
            }
            if (foundMatch)
               break;
         }
      }
      if( ! foundMatch)
      {
         cameraState_ = prevState;
         std::ostringstream oss;
         oss << " in BitsInOneColor can not set bits to " << bits;
         throw CMMError(oss.str().c_str(), MMERR_GENERIC);
      }
   }
   cameraState_=prevState;
}


// check if current format is monochrome

bool BOImplementationThread::MonoChrome(void)
{
   bool ret = false;
   switch( ImageCode().iCode)
   {
      case BOIMF_PIXORG   :      ///<  use  only  the  information  of  tBoImgCode 
         break;
      case BOIMF_RAWMONO   :      ///<  raw  monochrome    pattern 
         ret = true;
         break;
      case BOIMF_RAWBAYER   :      ///<  raw  Bayer  pattern 
      case BOIMF_RGB   :      ///<  rgbrgb...  allgemein 
         break;
      case BOIMF_MONO   :      ///<  mm... 
         ret = true;
         break;
      case BOIMF_RAWBAYER_GR     :      ///<  raw  Bayer  pattern  red  line  start  with  green 
      case BOIMF_RAWBAYER_GB     :      ///<  raw  Bayer  pattern  blue  line  start  with  green 
      case BOIMF_RAWBAYER_BG     :      ///<  raw  Bayer  pattern  blue  line  start  with  blue 
      case BOIMF_RAW   :      ///<  raw  memory  image,  internal  usage 
      case BOIMF_NOTDEF   :      ///<  not  defined     
      case BOIMF_YUV444   :      //not  supported  yet 
      case BOIMF_YUV422   :      //not  supported  yet 
      case BOIMF_YUV411   :      //not  supported  yet 
         break;
      default:
         break;

   }
   return ret;
}


// check if current format is color
bool BOImplementationThread::Color(void)
{
   bool ret = false;
   switch( ImageCode().iCode)
   {
      case BOIMF_PIXORG   :      ///<  use  only  the  information  of  tBoImgCode 
         break;
      case BOIMF_RAWMONO   :      ///<  raw  monochrome    pattern 
         break;
      case BOIMF_RAWBAYER   :      ///<  raw  Bayer  pattern 
         break;
      case BOIMF_RGB   :      ///<  rgbrgb...  allgemein 
         ret = true;
         break;
      case BOIMF_MONO   :      ///<  mm... 
         break;
      case BOIMF_RAWBAYER_GR     :      ///<  raw  Bayer  pattern  red  line  start  with  green 
      case BOIMF_RAWBAYER_GB     :      ///<  raw  Bayer  pattern  blue  line  start  with  green 
      case BOIMF_RAWBAYER_BG     :      ///<  raw  Bayer  pattern  blue  line  start  with  blue 
      case BOIMF_RAW   :      ///<  raw  memory  image,  internal  usage 
      case BOIMF_NOTDEF   :      ///<  not  defined     
      case BOIMF_YUV444   :      //not  supported  yet 
      case BOIMF_YUV422   :      //not  supported  yet 
      case BOIMF_YUV411   :      //not  supported  yet 
         break;
      default:
         break;
   }
  
   return ret;
}




std::string BOImplementationThread::IntelligentErrorString(const int fxcode)
{
   char *pmess = FX_GetErrString(fxcode);
   std::ostringstream oss;
   oss << "err: " << std::hex << fxcode;
   if( NULL != pmess)
      oss << std::string(pmess);
   return oss.str().c_str();
}



void BOImplementationThread::CancelROI(void)
{

   RECT returnedRect;
   memset( &returnedRect, 0, sizeof( RECT));
   int fxret = FX_SetPartialScanEx  (   gCameraId[0], false, &returnedRect,   &returnedRect);
   if(1 == fxret)
      roi_ = returnedRect;
   partialScanMode_ = false;
   QueryCameraCurrentFormat();
}




void BOImplementationThread::SetROI( const unsigned int  x, const unsigned int  y, const unsigned int  xSize, const unsigned int  ySize)
{

   RECT returnedRect;
   if ( (0 == x) && (0 == y) && ( xSize == xDim_ )  && ( ySize == yDim_) ) // ROI is being cleared, use full image format
   {
      memset( &returnedRect, 0, sizeof( RECT));
      int fxret = FX_SetPartialScanEx  (   gCameraId[0], false, &returnedRect,   &returnedRect);
      if(1 == fxret)
         roi_ = returnedRect;
      partialScanMode_ = false;
   }
   else
   {
      RECT requestedRect;
      requestedRect.bottom = y + ySize;
      requestedRect.left = x;
      requestedRect.right = x + xSize;
      requestedRect.top = y;
      int fxret = FX_SetPartialScanEx  (   gCameraId[0], true, &requestedRect,   &returnedRect);
      if(1 == fxret)
      {
         roi_ = returnedRect;
         partialScanMode_ = true;
      }
   }
   QueryCameraCurrentFormat();
}

void BOImplementationThread::GetROI( unsigned int & x, unsigned int & y, unsigned int & xSize, unsigned int & ySize)
{

   if( partialScanMode_)  // subregion was successfully set and is in use
   {
      x = static_cast<unsigned int >( roi_.left);
      y = static_cast<unsigned int >( roi_.top);
      ySize = static_cast<unsigned int >( roi_.bottom - roi_.top);
      xSize = static_cast<unsigned int >( roi_.right - roi_.left);
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
unsigned int __stdcall mSeqEventHandler( void* pArguments )
{
   HANDLE * aHandle = (HANDLE*)pArguments;
   tBoImgDataInfoHeader imgHeader;
   memset(&imgHeader, 0x00, sizeof(imgHeader));
   for( ; mTerminateFlag_g == false; ) 
   {
      Sleep(0);
      MMThreadGuard g(::acquisitionThreadTerminateLock_g); // have to wait for the event time-out !!

      DWORD  Status = WaitForMultipleObjects( 1, aHandle, FALSE,  synchTimeout_g ); 
      Status -= WAIT_OBJECT_0;
      if( Status == 0 )
      {
         if( seqactive_g ) 
         {
				imgHeader.sFlags.fFlipHori  = false;
				imgHeader.sFlags.fFlipVert  = true;
				imgHeader.sFlags.fSyncStamp = true;

            // each operation should only atomically lock the mutex...

            bool bufferIsReady = false;

            {
               MMThreadGuard g(BOImplementationThread::imageBufferLock_s);
               bufferIsReady = ( 0 < staticImgSize_g) && ( NULL != pStatic_g);
            }

            if( bufferIsReady)
            {
               MMThreadGuard g(BOImplementationThread::imageBufferLock_s);
		         Status = FX_GetImageData( gCameraId[0], &imgHeader, pStatic_g , staticImgSize_g );
            }

		      if( Status == 1 ) 
            {
               MMThreadGuard g(BOImplementationThread::imageBufferLock_s);
               xDim_g = static_cast<unsigned short> (imgHeader.iSizeX);
               yDim_g = static_cast<unsigned short> (imgHeader.iSizeY);
               bitsInOneColor_g = static_cast<unsigned short> (imgHeader.sDataCode.iCanalBits);
               nPlanes_g = static_cast<unsigned short> (imgHeader.sDataCode.iPlanes);
               nCanals_g = static_cast<unsigned short> (imgHeader.sDataCode.iCanals);
               iCode_g = static_cast<unsigned short> (imgHeader.sDataCode.iCode);

               {
                  MMThreadGuard guard(BOImplementationThread::imageReadyLock_s);
                  imageReady_g = true;
               }
            }
			   else 
            {
			      Sleep(0);
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
   CCameraBase<CBaumerOptronic> (),
   initialized_(false),
   readoutUs_(0.0),
   pWorkerThread_(NULL),
   stopOnOverflow_(false)
{
   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();
   readoutStartTime_ = GetCurrentMMTime();
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
   
   if ( initialized_ && (NULL != pWorkerThread_) )
   {
      LogMessage(" sending Exit command to implementation thread " , true);
      pWorkerThread_->Command(Exit);
      LogMessage(  "deleting  BO camera implementation thread " , true);
      // It is not clear whether Stop does anything
      pWorkerThread_->Stop();
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
      return DEVICE_OK;


   // start the implementation thread
   pWorkerThread_ = new BOImplementationThread(this);
   pWorkerThread_->Start();
   pWorkerThread_->Command(InitializeLibrary);

   MM::TimeoutMs timerOut(GetCurrentMMTime(), 6000);

   do
   {
      if(timerOut.expired(GetCurrentMMTime()))
         break;
      // yeild until the libaries have been loaded
      CDeviceUtils::SleepMs(10);
   } while( Ready != pWorkerThread_->CameraState());

   // query the 'formats' structures
   pWorkerThread_->QueryCapabilities();

   // march through the 'formats' structures and extract the possible physical property settings
   pWorkerThread_->ParseCapabilities();
   // Note: the camera uses microseconds for exposure, whereas we use milliseconds
   std::pair< int, int> exposureLowHigh = pWorkerThread_->ExposureLimits();
   std::pair< double, double> gainLowHigh = pWorkerThread_->GainLimits();

   // set property list
   // -----------------

   CPropertyAction *pAct = new CPropertyAction (this, &CBaumerOptronic::OnGain);
   std::ostringstream gainLimit;
   gainLimit << gainLowHigh.first;
   (void)CreateProperty(MM::g_Keyword_Gain, gainLimit.str().c_str(), MM::Float, false, pAct);
   (void)SetPropertyLimits(MM::g_Keyword_Gain, gainLowHigh.first, gainLowHigh.second);

   pAct = new CPropertyAction (this, &CBaumerOptronic::OnExposure);
   std::ostringstream oss;
   oss << exposureLowHigh.first;
   (void)CreateProperty(MM::g_Keyword_Exposure, oss.str().c_str(), MM::Float, false, pAct);
   (void)SetPropertyLimits(MM::g_Keyword_Exposure, exposureLowHigh.first/1000, exposureLowHigh.second/1000);
   // We can not query from the camera.  To start in a known state, set the exposure time
   SetExposure(25.0);


   // Name
   int nRet = CreateProperty(MM::g_Keyword_Name, g_CameraDeviceName, MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // Description
   nRet = CreateProperty(MM::g_Keyword_Description, "Baumer Optronic Adapter for Leica Cameras", MM::String, true);
   if (DEVICE_OK != nRet)
      return nRet;

   // CameraName
   nRet = CreateProperty(MM::g_Keyword_CameraName, "BaumerOptronic-MultiMode", MM::String, true);
   assert(nRet == DEVICE_OK);

   // CameraID
   nRet = CreateProperty(MM::g_Keyword_CameraID, "V1.0", MM::String, true);
   assert(nRet == DEVICE_OK);

   // binning
   pAct = new CPropertyAction (this, &CBaumerOptronic::OnBinning);
   nRet = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);

   std::vector<string> binss;
   std::vector< int > binis = pWorkerThread_->BinSizes();
   std::vector< int >::iterator viit;

   for( viit = binis.begin(); viit != binis.end(); ++viit)
   {
      std::ostringstream osss;
      osss << *viit;
      binss.push_back( osss.str().c_str());
   }

   SetAllowedValues(MM::g_Keyword_Binning, binss);

   // pixel type
   pAct = new CPropertyAction (this, &CBaumerOptronic::OnPixelType);
   nRet = CreateProperty(MM::g_Keyword_PixelType, g_PixelType_8bit, MM::String, true, pAct);
   assert(nRet == DEVICE_OK);

   vector<string> pixelTypeValues;
   pixelTypeValues.push_back(g_PixelType_8bit);
   pixelTypeValues.push_back(g_PixelType_16bit);
	pixelTypeValues.push_back(g_PixelType_32bitRGB);

   nRet = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
   if (nRet != DEVICE_OK)
      return nRet;

   // Bit depth
   pAct = new CPropertyAction (this, &CBaumerOptronic::OnBitDepth);
   nRet = CreateProperty("BitDepth", "8", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);

   vector<string> bitDepths;

   std::vector< int> pbb = pWorkerThread_->PossibleBitsInOneColor();
   std::vector< int>::iterator pbbi;

   for( pbbi=pbb.begin(); pbbi!=pbb.end(); ++pbbi)
   {
      std::ostringstream osss;
      osss << *pbbi;
      bitDepths.push_back(osss.str().c_str());
   }

   nRet = SetAllowedValues("BitDepth", bitDepths);
   if (nRet != DEVICE_OK)
      return nRet;
#if 0

   // camera offset
   nRet = CreateProperty(MM::g_Keyword_Offset, "0", MM::Integer, false);
   assert(nRet == DEVICE_OK);

   // camera temperature
   nRet = CreateProperty(MM::g_Keyword_CCDTemperature, "0", MM::Float, false);
   assert(nRet == DEVICE_OK);
   SetPropertyLimits(MM::g_Keyword_CCDTemperature, -100, 10);

   // readout mode

   pAct = new CPropertyAction (this, &CBaumerOptronic::OnReadoutMode);
   nRet = CreateProperty(MM::g_Keyword_ReadoutMode, "0", MM::String, false, pAct);
   assert(nRet == DEVICE_OK);
   AddAllowedValue(MM::g_Keyword_ReadoutMode, "Fast readout", BOCFN_ROFAST);
   AddAllowedValue(MM::g_Keyword_ReadoutMode, "Fast readout faster", BOCFN_ROFASTP);
   AddAllowedValue(MM::g_Keyword_ReadoutMode, "Slow readout", BOCFN_ROSLOW);
   AddAllowedValue(MM::g_Keyword_ReadoutMode, "Slow readout faster", BOCFN_ROSLOWP);
#endif

   // synchronize all properties
   // --------------------------
   nRet = UpdateStatus();
   if (nRet != DEVICE_OK)
      return nRet;

   // setup the buffer
   // ----------------
   SnapImage();
   GetImageBuffer();
   nRet = ResizeImageBuffer();
   if (nRet != DEVICE_OK)
   {
      if ( NULL != pWorkerThread_)
      {
         LogMessage(  " sending Exit command to implementation thread " , true);
         pWorkerThread_->Command(Exit);
         pWorkerThread_->wait();
         LogMessage(  "deleting  BO camera implementation thread " , true);
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
* (i.e., before readout).  This behavior is needed for proper synchronization with the shutter.
* Required by the MM::Camera API.
*/
int CBaumerOptronic::SnapImage()
{
   pWorkerThread_->Command(SnapCommand);

   // XXX BUG: "Exposure() / 1000" is zero unless Exposure > 1000.
   CDeviceUtils::SleepMs(pWorkerThread_->Exposure() / 1000);

   return DEVICE_OK;
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
   // todo :: use start time or time after image is available
   MM::MMTime curTime = GetCurrentMMTime();


   unsigned short xDim;
   unsigned short yDim;
   unsigned short bitsInOneColor;
   unsigned short nPlanes;
   unsigned long bufSize;

   MMThreadGuard* pImageBufferGuard = NULL;
   void* p = pWorkerThread_->CurrentImage(xDim,  yDim, bitsInOneColor, nPlanes,  
                                          bufSize, &pImageBufferGuard);

   void* pixBuffer = NULL;
   if( NULL!= p)
   {
      short bytesInOnePlane = BytesInOneComponent(bitsInOneColor);

      img_.Resize(xDim, yDim, nPlanes * bytesInOnePlane);
    
      unsigned long size = GetImageBufferSize();
      std::ostringstream os;
      os << "ImageBuffer size is:" << size;
      LogMessage(os.str().c_str());

      pixBuffer = const_cast<unsigned char*> (img_.GetPixels());
      memcpy(pixBuffer, p, bufSize); 
   }

   // release lock on image buffer
   if( NULL!= pImageBufferGuard)
      delete pImageBufferGuard;

   // capture complete
   return (unsigned char*)pixBuffer;

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
   return pWorkerThread_->BitsInOneColor();
}

/**
* Returns the size in bytes of the image buffer.
* Required by the MM::Camera API.
*/
long CBaumerOptronic::GetImageBufferSize() const
{
   return img_.Width() * img_.Height() * GetImageBytesPerPixel();
}



unsigned  CBaumerOptronic::GetNumberOfComponents() const 
{
   bool  col = pWorkerThread_->Color();
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

      unsigned short x,y,bits,colors;
      unsigned long bufs;
      pWorkerThread_->CurrentImageSize(x,y,bits,colors,bufs);
      pWorkerThread_->SetROI( 0, 0, x, y);
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

   pWorkerThread_->GetROI( x, y, xSize, ySize);

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
      return 0.0;
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
      return 1;
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
   switch(eAct)
   {
   case MM::AfterSet:
      {
         bool liveMode = false;
         if (IsCapturing())
            liveMode = true;


         //if(IsCapturing())
         //   return DEVICE_CAMERA_BUSY_ACQUIRING;

         // the user just set the new value for the property, so we have to
         // apply this value to the 'hardware'.
         long binFactor;
         pProp->Get(binFactor);

         pWorkerThread_->BinSize(binFactor);
         // needed so that everyone learns about the new image size
         SnapImage();
         GetImageBuffer();
         unsigned short x,y,bits,colors;
         unsigned long bufs;
         pWorkerThread_->CurrentImageSize(x,y,bits,colors,bufs);
         img_.Resize(x, y);
         ret=DEVICE_OK;
      }break;
   case MM::BeforeGet:
      {
        pProp->Set((long)pWorkerThread_->BinSize());
         ret=DEVICE_OK;
      }break;
   }
   return ret; 
}

/**
* Handles "PixelType" property.  N.B. current implementation requires this property whether or not it does anything.
*/
int CBaumerOptronic::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_OK;
   switch(eAct)
   {
   case MM::AfterSet:
      {

      } break;
   case MM::BeforeGet:
      {
         if (GetNumberOfComponents() == 4)
            if (bitsInOneColor_g > 8)
               pProp->Set(g_PixelType_64bitRGB);
            else
               pProp->Set(g_PixelType_32bitRGB);
         else if (GetImageBytesPerPixel() == 2)
            pProp->Set(g_PixelType_16bit);
         else
            pProp->Set(g_PixelType_8bit);
      }break;
   }
   return ret; 
}

/**
* Handles "BitDepth" property.
*/
int CBaumerOptronic::OnBitDepth(MM::PropertyBase* pProp, MM::ActionType eAct)
{

   if (Ready != pWorkerThread_->CameraState())
      return DEVICE_CAMERA_BUSY_ACQUIRING;

   int ret = DEVICE_ERR;
   try
   {
      switch(eAct)
      {
      case MM::AfterSet:    // property was written -> apply value to hardware
         {
            long bitDepth;
            pProp->Get(bitDepth);
           // if (GetNumberOfComponents() == 4)
            //   bitDepth = 8; // We can not yet handle 64bitRGB
            pWorkerThread_->BitsInOneColor(bitDepth);
            bitsInOneColor_g = (unsigned short)bitDepth;
            int bytesPerPixel = BytesInOneComponent(bitsInOneColor_g);
            //todo is color image selected at this time?
			   img_.Resize(img_.Width(), img_.Height(), bytesPerPixel * GetNumberOfComponents() );
            ret=DEVICE_OK;
         } break;
      case MM::BeforeGet: // property will be read -> update property with value from hardware
         {
            pProp->Set((long)pWorkerThread_->BitsInOneColor());
            ret=DEVICE_OK;
         }break;
      }
   }
   catch(CMMError& e)
   {
      ret = e.getCode();
   }
   return ret; 
}

/**
* Handles "ReadoutMode" property.
*/
#if 0
int CBaumerOptronic::OnReadoutMode(MM::Property* pProp, MM::ActionType eAct)
{
   if (eAct == MM::AfterSet)
   {
      std::string sval;
      long theMode;
      pProp->Get(sval);
      pProp->GetData(sval.c_str(), theMode);
      pWorkerThread->ReadOutMode(theMode);
   }
   else if (eAct == MM::BeforeGet)
   {
      long theMode = pWorkerThread->ReadOutMode();
      std::string p;

      switch(theMode)
      {
      case  BOCFN_ROFAST:
        p =  "Fast readout";
        break;
      case  BOCFN_ROFASTP:
        p = "Fast readout faster";
        break;
      case BOCFN_ROSLOW:
        p =  "Slow readout";
        break;
      case  BOCFN_ROSLOWP:
         p = "Slow readout faster";
         break;
      default:
         break;
      }
      pProp->Set(p.c_str());
   }
   return DEVICE_OK;
}


#endif

// Exposure Time
int CBaumerOptronic::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   // exposure property is stored in milliseconds,
   // while the driver returns the value in micro-seconds
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(static_cast<long>(pWorkerThread_->Exposure()) / 1000);
   }
   else if (eAct == MM::AfterSet)
   {
      long val;
      pProp->Get(val);
      synchTimeout_g = val + 500;
      pWorkerThread_->Exposure(static_cast<int>(1000 * val));
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
   GetCoreCallback()->ClearPostedErrors();

   if( Acquiring == pWorkerThread_->CameraState())
      return DEVICE_CAMERA_BUSY_ACQUIRING;

   MM::TimeoutMs timerOut(GetCurrentMMTime(), 1000);
   while( Ready != pWorkerThread_->CameraState())
   {
      Sleep(0);
      if( timerOut.expired(GetCurrentMMTime()))
         return DEVICE_SERIAL_TIMEOUT;
   }

   stopOnOverflow_ = stopOnOverflow;
   pWorkerThread_->Interval( interval_ms);  

   LogMessage(" sequence starting, exposure is " + boost::lexical_cast<std::string, int>(pWorkerThread_->Exposure()), true);

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

   unsigned short xDim;  
   unsigned short yDim; 
   unsigned short bitsInOneColor; 
   unsigned short nColors;  
   unsigned long bufSize;

   pWorkerThread_->CurrentImageSize( xDim,  yDim,  bitsInOneColor,  nColors,   bufSize);

   // make sure the circular buffer is properly sized
   GetCoreCallback()->InitializeImageBuffer(1, 1, xDim, yDim, BytesInOneComponent(bitsInOneColor) * nColors);

   pWorkerThread_->Command(::StartSequence);
   
   LogMessage("Acquisition started");
   return DEVICE_OK;
}

//int CBaumerOptronic::RestartSequenceAcquisition() {
//   return StartSequenceAcquisition(sequenceLength_ - imageCounter_, interval_ms_, stopOnOverflow_);
//}

/**
 * Stops Burst acquisition
 */
int CBaumerOptronic::StopSequenceAcquisition()
{
   pWorkerThread_->Command(StopSequence);
   MM::TimeoutMs timerOut(GetCurrentMMTime(), (long)(pWorkerThread_->Exposure() + 1000));

   while( Acquiring  == pWorkerThread_->CameraState())
   {
      Sleep(0);
      if( timerOut.expired(GetCurrentMMTime()))
         return DEVICE_SERIAL_TIMEOUT;
   }
  // pWorkerThread_->cameraState_ = Ready;
   MM::Core* cb = GetCoreCallback();
   if (cb)
      return cb->AcqFinished(this, 0);
   else
      return DEVICE_OK;

}

/**
 * Uses GetImageBuffer to get pixels from camera and inserts 
 * into circular buffer
 */
int CBaumerOptronic::InsertImage()
{
   char label[MM::MaxStrLength];
   this->GetLabel(label);
   Metadata md;
   md.put("Camera", label);
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
   } else
      return ret;
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
      return ret;


   unsigned short xDim;
   unsigned short yDim;
   unsigned short bitsInOneColor;
   unsigned short colors;
   unsigned long bufSize;
   pWorkerThread_->CurrentImageSize(xDim,  yDim, bitsInOneColor, colors,  bufSize);

// todo get rid of this:

   if (( 0 == xDim) ||  (0 == yDim) || ( 0 == bitsInOneColor))
      return DEVICE_ERR;
   //   xDim = 1600;
   //if
   //   yDim = 1200;
   //if
   //   bitsInOneColor = 8;

   short nbytes = (short)(bitsInOneColor+7);
   nbytes /= 8;


   img_.Resize(xDim, yDim, colors*nbytes );
   return DEVICE_OK;
}



bool CBaumerOptronic::IsCapturing(void)
{
   return ( Acquiring == pWorkerThread_->CameraState());
}
