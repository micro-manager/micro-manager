///////////////////////////////////////////////////////////////////////////////
// FILE:          PICAMAdapter.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   PICAM camera module
//
// AUTHOR:        Toshio Suzuki
//
// PORTED from    PVCAMUniversal.cpp
//
//                (AUTHOR:        Nico Stuurman, Nenad Amodaj nenad@amodaj.com, 09/13/2005)
//                (COPYRIGHT:     University of California, San Francisco, 2006)
//
//
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

#ifdef WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#endif

#include "ModuleInterface.h"
#include "PICAMAdapter.h"
#include "PICAMParam.h"

#ifdef WIN64
#pragma warning(push)
#include "picam.h"
#include "picam_advanced.h"

#pragma warning(pop)
#endif

#ifdef __APPLE__
#define __mac_os_x
#include <PICAM/master.h>
#include <PICAM/pvcam.h>
#endif

#ifdef linux
#include <pvcam/master.h>
#include <pvcam/pvcam.h>
#endif

#include <string>
#include <sstream>
#include <iomanip>

using namespace std;

//#define DEBUG_METHOD_NAMES

#ifdef DEBUG_METHOD_NAMES
#define START_METHOD(name)              LogMessage(name);
#define START_ONPROPERTY(name,action)   LogMessage(string(name)+(action==MM::AfterSet?"(AfterSet)":"(BeforeGet)"));
#else
#define START_METHOD(name)
#define START_ONPROPERTY(name,action)
#endif

#if WIN64
#define snprintf _snprintf
#endif

// Number of references to this class
int  Universal::refCount_ = 0;
bool Universal::PICAM_initialized_ = false;
MMThreadLock g_picamLock;

// Maximum pixel time to be used in case we fail to get the PARAM_PIX_TIME from the camera.
const int MAX_PIX_TIME = 500;
// Circular buffer default values


// global constants
extern const char* g_ReadoutRate;
extern const char* g_ReadoutPort;
extern const char* g_ReadoutPort_Normal;
extern const char* g_ReadoutPort_Multiplier;
extern const char* g_ReadoutPort_LowNoise;
extern const char* g_ReadoutPort_HighCap;

const char* g_Keyword_ChipName        = "ChipName";
const char* g_Keyword_SerialNumber    = "SerialNumber";
const char* g_Keyword_FirmwareVersion = "FirmwareVersion";
const char* g_Keyword_CCDSerSize      = "X-dimension";
const char* g_Keyword_CCDParSize      = "Y-dimension";
const char* g_Keyword_FWellCapacity   = "FullWellCapacity";
const char* g_Keyword_TriggerMode     = "TriggerMode";
const char* g_Keyword_ExposeOutMode   = "ExposeOutMode";
const char* g_Keyword_ColorMode       = "ColorMode";
const char* g_Keyword_TriggerTimeout  = "Trigger Timeout (secs)";
const char* g_Keyword_ActualGain      = "Actual Gain e/ADU";
const char* g_Keyword_ReadNoise       = "Current Read Noise";
const char* g_Keyword_BinningX        = "BinningX";
const char* g_Keyword_BinningY        = "BinningY";
const char* g_Keyword_MultiplierGain  = "MultiplierGain";
const char* g_Keyword_PreampOffLimit  = "PreampOffLimit";
const char* g_Keyword_Yes             = "Yes";
const char* g_Keyword_No              = "No";
const char* g_Keyword_FrameCapable    = "FTCapable";
const char* g_Keyword_RGB32           = "Color";
const char* g_ON                      = "ON";
const char* g_OFF                     = "OFF";
const char* g_Keyword_AcqMethod           = "AcquisitionMethod";
const char* g_Keyword_AcqMethod_Polling   = "Polling";
const char* g_Keyword_OutputTriggerFirstMissing = "OutputTriggerFirstMissing";

#define MIN_CAMERAS 4

// Universal parameters
// These parameters, their ranges or allowed values are read out from the camera automatically.
// Use these parameters for simple camera properties that do not need special treatment when a
// parameter is changed. See PICAMProperty class and OnUniversalProperty(). These are still
// not perfect, due to PICAM and MM nature it's always better to create custom property with
// unique hanler to properly handle the change in the property.
// - Parameter that is not supported by a particular camera is not displayed.
// - Parameter that is read-only is displayed as read-only
// - Enum parameters are displayed as combo boxes with strings read out from the camera
// - So far only parameters in double range can be used
// Do not use these for static camera properties that never changes. It's more efficient to create
// a simple readonly MM property without a handler (see examples in Initialize())
ParamNameIdPair g_UniversalParams[] = {
   //   {MM::g_Keyword_Offset, PARAM_ADC_OFFSET},         // INT16
   //   {"ClearCycles",        PARAM_CLEAR_CYCLES},       // UNS16
   //   {"PMode",              },              // ENUM
   //   {"ClearMode",          PARAM_CLEAR_MODE},         // ENUM
   {"CleanUntilTrigger", PicamParameter_CleanUntilTrigger},
   {"CleanBeforeExposure", PicamParameter_CleanBeforeExposure},
   {"CleanSerialRegister", PicamParameter_CleanSerialRegister},
   {"CleanHeight", PicamParameter_CleanSectionFinalHeight},
   {"CleanHeightCount", PicamParameter_CleanSectionFinalHeightCount},
   {"CleanCycleCount", PicamParameter_CleanCycleCount},
   {"ShutterMode", PicamParameter_ShutterTimingMode}, // ENUM
   {"ShutterOpenDelay", PicamParameter_ShutterOpeningDelay},    // floating (milliseconds)
   {"ShutterCloseDelay", PicamParameter_ShutterClosingDelay},    // floating (milliseconds)
   {"TriggerDetermination",PicamParameter_TriggerDetermination},
   {"DisableCoolingFan", PicamParameter_DisableCoolingFan },
   {"ReadoutControl", PicamParameter_ReadoutControlMode},
   {"CorrectPixelBias", PicamParameter_CorrectPixelBias},
   {"OutputSignal", PicamParameter_OutputSignal},
   {"InvertOutputSignal", PicamParameter_InvertOutputSignal},
   {"EMIccdGain", PicamParameter_EMIccdGain},
};
const int g_UniversalParamsCount = sizeof(g_UniversalParams)/sizeof(ParamNameIdPair);

HANDLE hDataUpdatedEvent_;

Universal *gUniversal;

///////////////////////////////////////////////////////////////////////////////
// &Universal constructor/destructor
Universal::Universal(short cameraId, const char* name) :
   CCameraBase<Universal> (),
   initialized_(false),
   curImageCnt_(0),
   hPICAM_(0),
   cameraId_(cameraId),
   circBuffer_(0),
   circBufferSize_(0),
   stopOnOverflow_(true),
   snappingSingleFrame_(false),
   singleFrameModeReady_(false),
   sequenceModeReady_(false),
   isAcquiring_(false),
   triggerTimeout_(2),
   microsecResSupported_(false),
   outputTriggerFirstMissing_(0),
   exposure_(10),
   binSize_(1),
   binXSize_(1),
   binYSize_(1),
   newBinSize_(1),
   newBinXSize_(1),
   newBinYSize_(1),
   deviceName_(name),
   rgbaColor_(false)
#ifdef PICAM_FRAME_INFO_SUPPORTED
   ,pFrameInfo_(0)
#endif
{
   InitializeDefaultErrorMessages();

   // add custom messages
   SetErrorText(ERR_CAMERA_NOT_FOUND, "No Camera Found. Is it connected and switched on?");
   SetErrorText(ERR_BUSY_ACQUIRING, "Acquisition already in progress.");
   SetErrorText(ERR_ROI_SIZE_NOT_SUPPORTED, "Selected ROI is not supported by the camera");

   uniAcqThd_ = new AcqSequenceThread(this);             // Pointer to the sequencing thread

   prmTemp_           = NULL;
   prmTempSetpoint_   = NULL;
   prmGainIndex_      = NULL;
   prmGainMultFactor_ = NULL;
   prmExpResIndex_    = NULL;
   prmExpRes_         = NULL;
   prmTriggerMode_    = NULL;
   prmExposeOutMode_  = NULL;
   prmReadoutPort_    = NULL;
   prmColorMode_      = NULL;
}


Universal::~Universal()
{
   if (initialized_)
      Shutdown();
   delete[] circBuffer_;
   if (!uniAcqThd_->getStop()) {
      uniAcqThd_->setStop(true);
      uniAcqThd_->wait();
   }
   delete uniAcqThd_;

   if ( prmTemp_ )
      delete prmTemp_;
   if ( prmTempSetpoint_ )
      delete prmTempSetpoint_;
   if ( prmGainIndex_ )
      delete prmGainIndex_;
   if ( prmGainMultFactor_ )
      delete prmGainMultFactor_;
   if ( prmExpResIndex_ )
      delete prmExpResIndex_;
   if ( prmExpRes_ )
      delete prmExpRes_;
   if ( prmTriggerMode_ )
      delete prmTriggerMode_;
   if ( prmExposeOutMode_ )
      delete prmExposeOutMode_;
   if ( prmReadoutPort_ )
      delete prmReadoutPort_;
   if ( prmColorMode_ )
      delete prmColorMode_;

   // Delete universal parameters
   for ( unsigned i = 0; i < universalParams_.size(); i++ )
      delete universalParams_[i];
   universalParams_.clear();
}

///////////////////////////////////////////////////////
// This gets called back after issuing Picam_StartAcquisition.
// Set hDataUpdatedEvent_ to notify that acquisition has occurred.
///////////////////////////////////////////////////////
PicamError GlobalAcquisitionUpdated(
      PicamHandle device,
      const PicamAvailableData* available,
      const PicamAcquisitionStatus* status )
{
   return gUniversal->AcquisitionUpdated( device, available,status );
}

PicamError Universal::AcquisitionUpdated(
      PicamHandle device,
      const PicamAvailableData* available,
      const PicamAcquisitionStatus* status )
{
   int ret;

   dataUpdated_.bOverruned=false;
   dataUpdated_.bAcquisitionInactive=false;
   dataUpdated_.readout_count=0;

   if( available && available->readout_count )
   {
      // - copy the last available frame to the shared image buffer and notify
      pi64s lastReadoutOffset = readoutStride_ * (available->readout_count-1);
      pi64s lastFrameOffset = frameStride_ * (framesPerReadout_-1);
      const pibyte* frame =
         static_cast<const pibyte*>( available->initial_readout ) +
         lastReadoutOffset + lastFrameOffset;

      // - check for overrun after copying
      pibln overran;
      PicamError error =
         PicamAdvanced_HasAcquisitionBufferOverrun( device, &overran );

      if( error != PicamError_None )
      {
         // std::cout << "Failed to read overrun " << available->readout_count << std::endl;
         ;
      }
      else{
         if( overran )
         {
            // std::cout << "Overrun " << available->readout_count << std::endl;
            dataUpdated_.bOverruned=true;
         }
         else{
            //std::cout << "Available " << available->readout_count << std::endl;

            // So far there is no way to use metadada for single frame mode (SnapImage())
            if (sequenceModeReady_)
            {
               Metadata md;
               ret = BuildMetadata( md );
               if (ret==DEVICE_OK)
               {
                  ret = PushImage( frame, &md );
                  if (ret==DEVICE_OK) {
                     curImageCnt_++;
                     dataUpdated_.readout_count=1; //???
                  }
               }
            }
            else
            {
               void* pixBuffer = const_cast<unsigned char*> (img_.GetPixels());

               memcpy(pixBuffer, frame, frameStride_);
               curImageCnt_++;
               dataUpdated_.readout_count=1; //???
            }

         }
      }
   }

   // - note when acquisition has completed
   if( !status->running )
   {
      dataUpdated_.bAcquisitionInactive=true;
   }


   SetEvent( hDataUpdatedEvent_ );
   return PicamError_None;
}

////////////////////////////////////////////////////////////////////////////////
// InitializeCalculatedBufferSize
// - calculates the first buffer size for a camera just opened
////////////////////////////////////////////////////////////////////////////////
bool Universal::InitializeCalculatedBufferSize()
{
   // - get the current readout rate
   // - note this accounts for rate increases in online scenarios
   piflt onlineReadoutRate;
   PicamError error =
      Picam_GetParameterFloatingPointValue(
            hPICAM_,
            PicamParameter_OnlineReadoutRateCalculation,
            &onlineReadoutRate );

   if( error != PicamError_None ){
      // std::cout << "Failed to get online readout rate." << std::endl;
      return FALSE;
   }

   // - get the current readout stride
   piint readoutStride;
   error =  Picam_GetParameterIntegerValue(
         hPICAM_,
         PicamParameter_ReadoutStride,
         &readoutStride );
   if( error != PicamError_None ){
      // std::cout << "Failed to get online readout stride." << std::endl;
      return FALSE;
   }
   // - calculate the buffer size
   pi64s readouts = static_cast<pi64s>(max( 3.*onlineReadoutRate, 2. ) ) ;
   pi64s calculatedBufferSize = readoutStride * readouts;

   if( calculatedBufferSize == 0 )
   {
      // std::cout << "Cannot start with a circular buffer of no length."  << std::endl;
      return false;
   }

   PicamAcquisitionBuffer buffer;

   if (circBufferSize_ != calculatedBufferSize)
   {
      if( circBuffer_ != NULL )
         delete[] circBuffer_;
   }
   circBufferSize_ = calculatedBufferSize;
   circBuffer_ = new unsigned char[calculatedBufferSize];

   buffer.memory = circBuffer_;
   buffer.memory_size = circBufferSize_;

   // Could not get to work without setting user buffer
   PicamAdvanced_SetAcquisitionBuffer( hPICAM_, &buffer );

   Picam_GetParameterIntegerValue(hPICAM_,  PicamParameter_ReadoutStride,    &readoutStride_ );
   Picam_GetParameterIntegerValue(hPICAM_,  PicamParameter_FramesPerReadout, &framesPerReadout_ );
   Picam_GetParameterIntegerValue(hPICAM_,  PicamParameter_FrameStride,      &frameStride_ );
   Picam_GetParameterIntegerValue(hPICAM_,  PicamParameter_FrameSize,        &frameSize_ );

   return TRUE;
}


///////////////////////////////////////////////////////////////////////////////
// Function name   : &Universal::Initialize
// Description     : Initializes the camera
//                   Sets up the (single) image buffer
// Return type     : bool
int Universal::Initialize()
{
   START_METHOD(">>> Universal::Initialize");

   int nRet;               // MM error code
   CPropertyAction *pAct;

   readoutStride_ = 0;               // - stride to next readout (bytes)
   framesPerReadout_ = 0;            // - number of frames in a readout
   frameStride_ = 0;                 // - stride to next frame (bytes)
   frameSize_ = 0;                   // - size of frame (bytes)

   // Property: Description of the adapter
   nRet = CreateProperty(MM::g_Keyword_Description, "PICAM API device adapter", MM::String, true);
   assert(nRet == DEVICE_OK);

   if (refCount_ <= 0)
   {
      if (Picam_InitializeLibrary() != PicamError_None)
      {
         LogCamError(__LINE__, "First PICAM init failed");
         // PICam should not be initialized, but maybe it is. Try again.
         if (!Picam_UninitializeLibrary() != PicamError_None)
         {
            return LogCamError(__LINE__, "PICAM init failed and cannot uninit either");
         }
         if (Picam_InitializeLibrary() != PicamError_None)
         {
            return LogCamError(__LINE__, "Second PICAM init failed");
         }
      }
      PICAM_initialized_ = true;
      refCount_ = 1;
   }

   // gather information about the camera
   // ------------------------------------------

   // Get PICAM version
   piint major,minor,  distribution, released ;
   const PicamCameraID *camID;
   piint numCamsAvailable = 0;
   piint numDemos = 0;
   PicamCameraID *demoID = NULL;
   //Only 4 cameras in demolist... more can be added.  Also add more sn's too
   piint demoList[] = { PicamModel_Pixis1024B, PicamModel_Nirvana640, PicamModel_ProEM1024B, PicamModel_Pylonir102417  };
   const pichar *sn[] = { "1000000001", "1000000002", "1000000003" , "1000000004" };

   Picam_GetVersion(&major, &minor, &distribution, &released );
   Picam_GetAvailableCameraIDs( &camID, &numCamsAvailable );
   Picam_DestroyCameraIDs( camID );

   // Add demo Camera
   if( numCamsAvailable < MIN_CAMERAS )
   {
      numDemos = MIN_CAMERAS - numCamsAvailable;
      demoID = (PicamCameraID*) malloc( sizeof( PicamCameraID ) * numDemos );
   }
   piint count = 0;
   while( numCamsAvailable < MIN_CAMERAS )
   {
      //need a minimum of MIN_CAMERAS(2) for multi-camera example
      Picam_ConnectDemoCamera( (PicamModel)demoList[count], sn[count], &demoID[count] );
      ++numCamsAvailable;
      ++count;
   }

   stringstream ver;
   ver << major << "." << minor ;
   nRet = CreateProperty("PICAM Version", ver.str().c_str(), MM::String, true);
   ver << ". Number of cameras detected: " << numCamsAvailable;
   LogMessage("PICAM VERSION: " + ver.str());
   assert(nRet == DEVICE_OK);


   // Get handles to the cameras
   Picam_GetAvailableCameraIDs( &camID, &numCamsAvailable );



   if (cameraId_>=0 && cameraId_<numCamsAvailable)
      CameraInfo_ =camID[cameraId_];
   else
      nRet=DEVICE_ERR;
   Picam_DestroyCameraIDs( camID );

   if (nRet!=DEVICE_OK)
      return nRet;

   // Open the camera
   if( PicamAdvanced_OpenCameraDevice( &CameraInfo_, &hPICAM_ )!= PicamError_None )
      return LogCamError(__LINE__, "Picam_OpenCamera" );

   /* Set camName_*/
   const pichar* model_string;
   Picam_GetEnumerationString( PicamEnumeratedType_Model, CameraInfo_.model, &model_string);
   sprintf_s(camName_, sizeof(camName_), "%s", model_string);
   Picam_DestroyString( model_string );

   /// --- BUILD THE SPEED TABLE
   LogMessage( "Building Speed Table" );
   nRet = buildSpdTable();
   if ( nRet != DEVICE_OK )
      return nRet;

   /// --- STATIC PROPERTIES
   /// are properties that are not changed during session. These are read-out only once.
   LogMessage( "Initializing Static Camera Properties" );
   nRet = initializeStaticCameraParams();
   if ( nRet != DEVICE_OK )
      return nRet;

   /// --- DYNAMIC PROPERTIES
   /// are properties that may be updated by a camera or changed by the user during session.
   /// These are read upon opening the camera and then updated on various events. These usually
   /// needs a handler that is called by MM when the GUI asks for the property value.
   LogMessage( "Initializing Dynamic Camera Properties" );

   /// TRIGGER MODE (EXPOSURE MODE)
   prmTriggerMode_ = new PvEnumParam( g_Keyword_TriggerMode, PicamParameter_TriggerResponse, this );
   if ( prmTriggerMode_->IsAvailable() )
   {
      pAct = new CPropertyAction (this, &Universal::OnTriggerMode);
      CreateProperty(g_Keyword_TriggerMode, prmTriggerMode_->ToString().c_str(), MM::String, false, pAct);
      SetAllowedValues( g_Keyword_TriggerMode, prmTriggerMode_->GetEnumStrings());

      pAct = new CPropertyAction (this, &Universal::OnTriggerTimeOut);
      CreateProperty(g_Keyword_TriggerTimeout, "2", MM::Integer, false, pAct);
   }

   /// CAMERA TEMPERATURE
   /// The actual value is read out from the camera in OnTemperature(). Please note
   /// we cannot read the temperature when continuous sequence is running.
   prmTemp_ = new PvParam<piflt>( MM::g_Keyword_CCDTemperature, PicamParameter_SensorTemperatureReading, this );
   if ( prmTemp_->IsAvailable() )
   {
      pAct = new CPropertyAction (this, &Universal::OnTemperature);
      nRet = CreateProperty(MM::g_Keyword_CCDTemperature,
            CDeviceUtils::ConvertToString((double)prmTemp_->Current()), MM::Float, true, pAct);
      assert(nRet == DEVICE_OK);
   }

   /// CAMERA TEMPERATURE SET POINT
   /// The desired value of the CCD chip
   prmTempSetpoint_ = new PvParam<piflt>( MM::g_Keyword_CCDTemperatureSetPoint, PicamParameter_SensorTemperatureSetPoint, this );
   if ( prmTempSetpoint_->IsAvailable() )
   {
      pAct = new CPropertyAction (this, &Universal::OnTemperatureSetPoint);
      nRet = CreateProperty(MM::g_Keyword_CCDTemperatureSetPoint,
            CDeviceUtils::ConvertToString((double)prmTempSetpoint_->Current()), MM::Float, false, pAct);
      SetPropertyLimits(MM::g_Keyword_CCDTemperatureSetPoint, prmTempSetpoint_->Min(),prmTempSetpoint_->Max());
   }

   /// EXPOSURE TIME
   pAct = new CPropertyAction (this, &Universal::OnExposure);
   nRet = CreateProperty(MM::g_Keyword_Exposure, "10.0", MM::Float, false, pAct);
   assert(nRet == DEVICE_OK);

   /// SYMMETRIC BINNING used to set the bin from MM GUI. Instead of asymmetric binning the
   /// value is restricted to specific values.
   pAct = new CPropertyAction (this, &Universal::OnBinning);
   nRet = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);
   vector<string> binValues;
   // So far there is no way how to read the available binning modes from the camera, so we must hardcode them
   binValues.push_back("1");
   binValues.push_back("2");
   binValues.push_back("4");
   binValues.push_back("8");
   nRet = SetAllowedValues(MM::g_Keyword_Binning, binValues);
   if (nRet != DEVICE_OK)
      return nRet;

   /// ASYMMETRIC BINNINGS. We don't set any allowed values here, this is an
   /// advanced feature so users should know what they do. The value can be set only from
   /// Device/Property browser. Changing the asymmetric binning does not change the symmetric
   /// bin value, but changing the symmetric bin updates bots asymmetric values accordingly.
   pAct = new CPropertyAction (this, &Universal::OnBinningX);
   nRet = CreateProperty(g_Keyword_BinningX, "1", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);
   pAct = new CPropertyAction (this, &Universal::OnBinningY);
   nRet = CreateProperty(g_Keyword_BinningY, "1", MM::Integer, false, pAct);
   assert(nRet == DEVICE_OK);

   /// PIXEL TYPE (BIT DEPTH).
   /// The value changes with selected port and speed
   pAct = new CPropertyAction (this, &Universal::OnPixelType);
   nRet = CreateProperty(MM::g_Keyword_PixelType, "", MM::String, true, pAct);
   if (nRet != DEVICE_OK)
      return nRet;

   /// Gain and speed depends on readout port. At first we just prepare these properties and then apply
   /// a readout port value which will update the allowed values of gain and speed properties accordingly.
   /// Changing the port resets the speed.
   /// Changing the speed causes change in Gain range, Pixel time and current Bit depth

   /// GAIN
   /// Note that this can change depending on output port, and readout rate.
   prmGainIndex_ = new PvParam<piint>( MM::g_Keyword_Gain, PicamParameter_AdcAnalogGain, this );
   if (prmGainIndex_->IsAvailable())
   {
      pAct = new CPropertyAction (this, &Universal::OnGain);
      nRet = CreateProperty(MM::g_Keyword_Gain, prmGainIndex_->ToString().c_str(), MM::Integer, false, pAct);
      if (nRet != DEVICE_OK)
         return nRet;
   }

   /// SPEED
   /// Note that this can change depending on output port, and readout rate.
   pAct = new CPropertyAction (this, &Universal::OnReadoutRate);
   nRet = CreateProperty(g_ReadoutRate, camCurrentSpeed_.spdString.c_str(), MM::String, false, pAct);
   if (nRet != DEVICE_OK)
      return nRet;

   /// READOUT PORT
   prmReadoutPort_ = new PvEnumParam(g_ReadoutPort, PicamParameter_AdcQuality, this );
   if ( prmReadoutPort_->IsAvailable() )
   {
      pAct = new CPropertyAction (this, &Universal::OnReadoutPort);
      vector<string> portStrings = prmReadoutPort_->GetEnumStrings();
      // If there is more than 1 port we make it selectable, otherwise just display readonly value
      if ( portStrings.size() > 1 )
      {
         nRet = CreateProperty(g_ReadoutPort, prmReadoutPort_->ToString().c_str(), MM::String, false, pAct);
         nRet = SetAllowedValues(g_ReadoutPort, prmReadoutPort_->GetEnumStrings());
      }
      else
         nRet = CreateProperty(g_ReadoutPort, prmReadoutPort_->ToString().c_str(), MM::String, true, pAct);


      // find Electron Multiplied port
      /*    const PicamCollectionConstraint* port_capable=NULL;
            Picam_GetParameterCollectionConstraint(
            hPICAM_, PicamParameter_AdcQuality,
            PicamConstraintCategory_Capable,
            &port_capable);
            for (int portIndex = 0; portIndex < port_capable->values_count; portIndex++)
            {
            if (PicamAdcQuality_ElectronMultiplied==(piint)(port_capable->values_array[portIndex]))
            {
      // Set ADC Quality
      Picam_SetParameterIntegerValue(
      hPICAM_, PicamParameter_AdcQuality,
      (piint)PicamAdcQuality_ElectronMultiplied);

      /// MULTIPLIER GAIN
      // Detect whether this is an interline chip and do not expose EM Gain if it is.
      prmGainMultFactor_ = new PvParam<piint>(g_Keyword_MultiplierGain, PicamParameter_AdcEMGain, this);

      if (prmGainMultFactor_->IsAvailable() )
      {
      LogMessage("This Camera has Em Gain");
      pAct = new CPropertyAction (this, &Universal::OnMultiplierGain);
      nRet = CreateProperty(g_Keyword_MultiplierGain, "1", MM::Integer, false, pAct);
      assert(nRet == DEVICE_OK);
      // The ATTR_MIN is 0 but according to PVCAM manual the range is from 1 to ATTR_MAX
      nRet = SetPropertyLimits(g_Keyword_MultiplierGain, 1, prmGainMultFactor_->Max());


      if (nRet != DEVICE_OK)
      return nRet;
      }
      else
      {
      LogMessage("This Camera does not have EM Gain");
      }
      break;
      }

      }
      Picam_DestroyCollectionConstraints(port_capable);*/



   }

   /// MULTIPLIER GAIN
   // Detect whether this is an interline chip and do not expose EM Gain if it is.
   prmGainMultFactor_ = new PvParam<piint>(g_Keyword_MultiplierGain, PicamParameter_AdcEMGain, this);

   if (prmGainMultFactor_->IsAvailable() )
   {
      LogMessage("This Camera has Em Gain");
      pAct = new CPropertyAction (this, &Universal::OnMultiplierGain);
      nRet = CreateProperty(g_Keyword_MultiplierGain, "1", MM::Integer, false, pAct);
      assert(nRet == DEVICE_OK);
      // The ATTR_MIN is 0 but according to PVCAM manual the range is from 1 to ATTR_MAX
      nRet = SetPropertyLimits(g_Keyword_MultiplierGain, 1, prmGainMultFactor_->Max());
      if (nRet != DEVICE_OK)
         return nRet;
   }
   else
   {
      LogMessage("This Camera does not have EM Gain");
   }

   /// EXPOSURE RESOLUTION
   // The PARAM_EXP_RES_INDEX is used to get and set the current exposure resolution (usec, msec, sec, ...)
   // The PARAM_EXP_RES is only used to enumerate the supported exposure resolutions and their string names
   microsecResSupported_ = false;



   /// FRAME TRANSFER MODE
   /// ... Set by "UniversalParams"

   /// properties that allow to enable/disable/set various post processing features
   /// supported by Photometrics cameras. The parameter properties are read out from
   /// the camera and created automatically.
   initializePostProcessing();

   // The _outputTriggerFirstMissing does not seem to be used anywhere, we may
   // want to remove it later.
   pAct = new CPropertyAction (this, &Universal::OnOutputTriggerFirstMissing);
   nRet = CreateProperty(g_Keyword_OutputTriggerFirstMissing, "0", MM::Integer, false, pAct);
   AddAllowedValue(g_Keyword_OutputTriggerFirstMissing, "0");
   AddAllowedValue(g_Keyword_OutputTriggerFirstMissing, "1");

   // Circular buffer size. This allows the user to set how many frames we want to allocate the PICAM
   // PICAM circular buffer for. The default value is fine for most cases, however chaning this value
   // may help in some cases (e.g. lowering it down to 3 helped to resolve ICX-674 image tearing issues)
   //   pAct = new CPropertyAction(this, &Universal::OnCircBufferFrameCount);
   //   nRet = CreateProperty( g_Keyword_CircBufFrameCnt,
   //      CDeviceUtils::ConvertToString(CIRC_BUF_FRAME_CNT_DEF), MM::Integer, false, pAct);
   //   SetPropertyLimits( g_Keyword_CircBufFrameCnt, CIRC_BUF_FRAME_CNT_MIN, CIRC_BUF_FRAME_CNT_MAX );



   initializeUniversalParams();


   // think of this function used in this case as setting the ROI to full-frame,
   //  however in other places in code it's just updating the ROI members of this class,
   //  when new binning is selected, etc...
   ClearROI();


   // Force updating the port. This calls OnReadoutPort() that internally updates speed choices, gain range,
   // current bit depth and current pix time (readout speed). All these MM parameters must be already instantiated!
   SetProperty(g_ReadoutPort, prmReadoutPort_->ToString().c_str());
   portChanged();



   // FRAME_INFO SUPPORT
#ifdef PICAM_FRAME_INFO_SUPPORTED
   // Initialize the FRAME_INFO structure, this will contain the frame metadata provided by PICAM
   if ( !pl_create_frame_info_struct( &pFrameInfo_ ) )
   {
      return LogCamError(__LINE__, "Failed to initialize the FRAME_INFO structure");
   }
#endif

   // for callback function
   //
   //
   hDataUpdatedEvent_ = CreateEvent( NULL, TRUE, FALSE, NULL );

   // Regist Update
   gUniversal=this;
   PicamAdvanced_RegisterForAcquisitionUpdated(hPICAM_,  GlobalAcquisitionUpdated );

   initialized_ = true;


   START_METHOD("<<< Universal::Initialize");
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Function name   : &Universal::Shutdown
// Description     : Deactivate the camera, reverse the initialization process
// Return type     : bool
int Universal::Shutdown()
{
   if (initialized_)
   {
      // Regist Update
      PicamAdvanced_UnregisterForAcquisitionUpdated(hPICAM_,  GlobalAcquisitionUpdated );

      // Close Device
      PicamAdvanced_CloseCameraDevice( hPICAM_ );

      if (PICAM_initialized_ && --refCount_ <= 0)
      {
         refCount_ = 0;
         if (!Picam_UninitializeLibrary()){
            LogCamError(__LINE__, "pl_pvcam_uninit");
         }
         PICAM_initialized_ = false;
      }
#ifdef PICAM_FRAME_INFO_SUPPORTED
      if ( pFrameInfo_ )
      {
         pl_release_frame_info_struct( pFrameInfo_ );
         pFrameInfo_ = NULL;
      }
#endif
      initialized_ = false;
   }
   return DEVICE_OK;
}


bool Universal::IsCapturing()
{
   return isAcquiring_;
}

int Universal::GetBinning () const
{
   return binSize_;
}

int Universal::SetBinning (int binSize)
{
   ostringstream os;
   os << binSize;
   return SetProperty(MM::g_Keyword_Binning, os.str().c_str());
}

/***
 * Read and create basic static camera properties that will be displayed in
 * Device/Property Browser. These properties are read only and does not change
 * during camera session.
 */
int Universal::initializeStaticCameraParams()
{
   START_METHOD("Universal::initializeStaticCameraProperties");
   int nRet;

   // Read the static parameres to class variables. Some of them are also used elswhere.
   // Some are not critical so we don't return error everytime

   // Camera name: "PM1394Cam00" etc.
   nRet = CreateProperty(MM::g_Keyword_Name, camName_, MM::String, true);
   assert(nRet == DEVICE_OK);

   // Camera chip name: "EX2-ICX285" etc.
   strcpy(camChipName_, CameraInfo_.sensor_name);

   // Camera serial number: "A09J821001" etc.
   nRet = CreateProperty(g_Keyword_SerialNumber, CameraInfo_.serial_number, MM::String, true);

   // Camera CCD size
   const PicamRoisConstraint  *constraint; /* Constraints */

   /* Obtain the image size */
   /* Variables to compute central region in image */
   PicamError err; /* Error Code */

   /* Get dimensional constraints */
   err = Picam_GetParameterRoisConstraint(hPICAM_,
         PicamParameter_Rois,
         PicamConstraintCategory_Required,
         &constraint);
   /* Error check */
   if (err == PicamError_None)
   {
      /* Get width and height from constraints */
      camParSize_ = (piint)constraint->height_constraint.maximum;
      camSerSize_= (piint)constraint->width_constraint.maximum;

      /* Clean up constraints after using constraints */
      Picam_DestroyRoisConstraints(constraint);

      nRet = CreateProperty(g_Keyword_CCDParSize, CDeviceUtils::ConvertToString(camParSize_), MM::Integer, true);
      nRet = CreateProperty(g_Keyword_CCDSerSize, CDeviceUtils::ConvertToString(camSerSize_), MM::Integer, true);
      assert(nRet == DEVICE_OK);
   }


   return nRet;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
// ~~~~~~~~~~~~~~~

/***
 * Symmetric binning property that is changed from main uManager GUI.
 * Changing the symmetric binning updates the asymmetric bin values accordingly
 * but not vice versa. We cannot display the asymmetric bin in GUI so we don't
 * update the symmetric bin value if the asymmetric bin changes.
 */
int Universal::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnBinning", eAct);
   long bin;

   if (eAct == MM::AfterSet)
   {
      pProp->Get(bin);
      if (!IsCapturing())
      {
         // If not capturing then change the bin immediately so it gets
         // reflected in the UI.
         binSize_ = bin;
         // Setting the symmetric bin resets the assymetric bin
         binXSize_= bin;
         binYSize_= bin;
         SetROI( 0, 0, camSerSize_, camParSize_ );
      }
      // If we are in the live mode, we just store the new values
      // and resize the buffer once the acquisition is started again.
      // (this fixes a crash that occured when switching binning during live mode)
      newBinSize_ = bin;
      newBinXSize_ = bin;
      newBinYSize_ = bin;
      sequenceModeReady_ = false;
      singleFrameModeReady_ = false;
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)binSize_);
   }
   return DEVICE_OK;
}

/***
 * Assymetric binning can be only set in Property Browser.
 * If the user sets the BinningX the symmetric binning combo box in MM GUI
 * is not updated (because there is no way to display asymmetric bin in main GUI).
 * However, if the user sets the symmetric bin value, then both the asymmetric
 * bin values are updated accordingly.
 */
int Universal::OnBinningX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnBinningX", eAct);
   long binX;
   int ret = DEVICE_OK;

   if (eAct == MM::AfterSet)
   {
      pProp->Get(binX);
      if (binX < 1)
      {
         LogMMError( 0, __LINE__, "Value of BinningX cannot be negative" );
         ret = DEVICE_INVALID_PROPERTY_VALUE;
      }
      else
      {
         if (!IsCapturing())
         {
            binXSize_= binX;
            SetROI( 0, 0, camSerSize_, camParSize_ );
         }
         newBinXSize_ = binX;
         sequenceModeReady_ = false;
         singleFrameModeReady_ = false;
      }
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)binXSize_);
   }
   return ret;
}
/***
 * Assymetric binning can be only set in Property Browser.
 * If the user sets the BinningY the symmetric binning combo box in MM GUI
 * is not updated (because there is no way to display asymmetric bin in main GUI).
 * However, if the user sets the symmetric bin value, then both the asymmetric
 * bin values are updated accordingly.
 */
int Universal::OnBinningY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnBinningY", eAct);
   long binY;
   int ret = DEVICE_OK;

   if (eAct == MM::AfterSet)
   {
      pProp->Get(binY);
      if (binY < 1)
      {
         LogMMError( 0, __LINE__, "Value of BinningY cannot be negative" );
         ret = DEVICE_INVALID_PROPERTY_VALUE;
      }
      else
      {
         if (!IsCapturing())
         {
            binYSize_= binY;
            SetROI( 0, 0, camSerSize_, camParSize_ );
         }
         newBinYSize_ = binY;
         sequenceModeReady_ = false;
         singleFrameModeReady_ = false;
      }
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)binYSize_);
   }
   return ret;
}

/***
 * This does not seem to be used anywhere.
 */
int Universal::OnOutputTriggerFirstMissing(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnOutputTriggerFirstMissing", eAct);
   if (eAct == MM::AfterSet)
   {
      pProp->Get(outputTriggerFirstMissing_);
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(outputTriggerFirstMissing_);
   }
   return DEVICE_OK;
}

/***
 * Sets or gets the current expose time
 */
int Universal::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnExposure", eAct);
   // exposure property is stored in milliseconds,
   // whereas the driver returns the value in seconds
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(exposure_);
   }
   else if (eAct == MM::AfterSet)
   {
      double oldExposure = exposure_;
      pProp->Get(exposure_);

      // we need to make sure to reconfigure the acquisition when exposure time changes.
      if (exposure_ != oldExposure)
      {
         // we need to make sure to stop the acquisition when exposure time is changed.
         if (IsCapturing())
            StopSequenceAcquisition();

         sequenceModeReady_ = false;
         singleFrameModeReady_ = false;
      }

   }
   return DEVICE_OK;
}

/***
 * The PARAM_BIT_DEPTH is read only. The bit depth depends on selected Port and Speed.
 */
int Universal::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnPixelType", eAct);

   char buf[8];
   if (eAct == MM::BeforeGet)
   {
      snprintf(buf, 8, "%ubit", camCurrentSpeed_.bitDepth); // 12bit, 14bit, 16bit, ...
      pProp->Set(buf);
   }

   return DEVICE_OK;
}


/***
 * Gets or sets the readout speed. The available choices are obtained from the
 * speed table which is build in Initialize(). If a change in speed occurs we need
 * to update Gain range, Pixel time, Actual Gain, Bit depth and Read Noise.
 * See speedChanged()
 */
int Universal::OnReadoutRate(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnReadoutRate", eAct);

   piint currentPort = prmReadoutPort_->Current();


   if (eAct == MM::AfterSet)
   {
      string selectedSpdString;
      pProp->Get(selectedSpdString);

      if (IsCapturing())
         StopSequenceAcquisition();

      // Find the corresponding speed index from reverse speed table
      SpdTabEntry selectedSpd = camSpdTableReverse_[currentPort][selectedSpdString];
      if ( Picam_SetParameterFloatingPointValue(hPICAM_, PicamParameter_AdcSpeed, selectedSpd.adcRate) != PicamError_None )
      {
         LogCamError(__LINE__, "PicamParameter_AdcSpeed");
         return DEVICE_CAN_NOT_SET_PROPERTY;
      }
      /* Error check */
      pibln committed;
      Picam_AreParametersCommitted( hPICAM_, &committed );

      if( !committed )
      {
         const PicamParameter* failed_parameter_array = NULL;
         piint failed_parameter_count = 0;

         Picam_CommitParameters( hPICAM_, &failed_parameter_array, &failed_parameter_count );
         if( failed_parameter_count ){


            LogCamError(__LINE__, "Picam_SetParameter PicamParameter_AdcSpeed");
            Picam_DestroyParameters( failed_parameter_array );
            return DEVICE_CAN_NOT_SET_PROPERTY;
         }
      }




      // Update the current speed if everything succeed
      camCurrentSpeed_ = selectedSpd;
      // Update all speed-dependant variables
      speedChanged();
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(camCurrentSpeed_.spdString.c_str());
   }


   return DEVICE_OK;
}

/***
 * Gets or sets the readout port. Change in readout port resets the speed which
 * in turn changes Gain range, Pixel time, Actual Gain, Bit depth and Read Noise.
 * see portChanged() and speedChanged();
 */
int Universal::OnReadoutPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnReadoutPort", eAct);

   if (eAct == MM::AfterSet)
   {
      string portStr;
      pProp->Get( portStr );
      if ( IsCapturing() )
         StopSequenceAcquisition();

      prmReadoutPort_->Set( portStr );


      prmReadoutPort_->Apply();
      // Update other properties that might have changed because of port change
      portChanged();
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(prmReadoutPort_->ToString().c_str());
   }

   return DEVICE_OK;
}

/***
 * The TriggerTimeOut is used in WaitForExposureDone() to specify how long should we wait
 * for a frame to arrive. Increasing this value may help to avoid timouts on long exposures
 * or when there are long pauses between triggers.
 */
int Universal::OnTriggerTimeOut(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnTriggerTimeOut", eAct);

   if (eAct == MM::AfterSet)
   {
      pProp->Get(triggerTimeout_);
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set(triggerTimeout_);
   }


   return DEVICE_OK;
}

/***
 * Trigger mode is set in ResizeImageBufferContinuous
 */
int Universal::OnTriggerMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnTriggerMode", eAct);
   if (eAct == MM::AfterSet)
   {
      // The acquisition must be stopped, and will be
      // automatically started again by MMCore
      if (IsCapturing())
         StopSequenceAcquisition();

      // request reconfiguration of acquisition before next use
      singleFrameModeReady_ = false;
      sequenceModeReady_ = false;

      string valStr;
      pProp->Get( valStr );

      prmTriggerMode_->Set( valStr );
      prmTriggerMode_->Apply();
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set( prmTriggerMode_->ToString().c_str() );
   }

   return DEVICE_OK;
}

/***
 * PARAM_EXPOSE_OUT_MODE
 */
int Universal::OnExposeOutMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnExposeOutMode", eAct);

   if (eAct == MM::AfterSet)
   {
      // The acquisition must be stopped, and will be
      // automatically started again by MMCore
      if (IsCapturing())
         StopSequenceAcquisition();

      // request reconfiguration of acquisition before next use
      singleFrameModeReady_ = false;
      sequenceModeReady_ = false;

      string valStr;
      pProp->Get( valStr );

      prmExposeOutMode_->Set( valStr );
      // We don't call Write() here because the PARAM_EXPOSE_OUT_MODE cannot be set,
      // it can only be retrieved and used in pl_setup_cont so we use the
      // prmExposeOutMode just as a cache to store our value
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set( prmExposeOutMode_->ToString().c_str() );
   }

   return DEVICE_OK;
}

// Gain
int Universal::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnGain", eAct);
   if (eAct == MM::AfterSet)
   {
      long gain;
      pProp->Get(gain);
      piint pvGain = (piint)gain;

      if (IsCapturing())
         StopSequenceAcquisition();

      prmGainIndex_->Set(pvGain);
      prmGainIndex_->Apply();

      singleFrameModeReady_ = false;
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)prmGainIndex_->Current());
   }

   return DEVICE_OK;
}


// EM Gain
int Universal::OnMultiplierGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnMultiplierGain", eAct);

   // Picam_SetParameterIntegerValueOnline can probably be used, so PvParam
   // should be extended

   if (eAct == MM::AfterSet)
   {
      long gain;
      pProp->Get(gain);
      piint pvGain = (piint)gain;

      prmGainMultFactor_->Set(pvGain);
      // EM Gain can only be set in the device when an acquisition is
      // in progress.
      if (IsCapturing())
      {
         prmGainMultFactor_->OnLineApply();
      }
      else
      {
         prmGainMultFactor_->Apply();
      }
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set((long)prmGainMultFactor_->Current());
   }
   return DEVICE_OK;
}



// Current camera temperature
int Universal::OnTemperature(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnTemperature", eAct);
   if (eAct == MM::AfterSet)
   { // Nothing to set, param is read-only
   }
   else if (eAct == MM::BeforeGet)
   {
      // We can read the temperature only if the streaming is not active
      if (!IsCapturing())
      {
         prmTemp_->Update();
      }
      pProp->Set((double)prmTemp_->Current() );
   }

   return DEVICE_OK;
}

// Desired camera temperature
int Universal::OnTemperatureSetPoint(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("Universal::OnTemperatureSetPoint)", eAct);
   if (eAct == MM::AfterSet)
   {
      double temp;
      pProp->Get(temp);
      piflt pvTemp = (piflt)(temp );

      if (IsCapturing())
         StopSequenceAcquisition();

      // Set the value to desired one
      prmTempSetpoint_->Set( pvTemp );
      prmTempSetpoint_->Apply();
   }
   else if (eAct == MM::BeforeGet)
   {
      pProp->Set((double)prmTempSetpoint_->Current());
   }

   return DEVICE_OK;
}

/*
 * Universal property value handler
 * The universal properties are automatically read from the camera and does not need a custom
 * value handler. This is useful for simple camera parameters that does not need special treatment.
 * So far only Enum and Integer values are supported. Other types should be implemented manaully.
 */
int Universal::OnUniversalProperty(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{
   START_ONPROPERTY("Universal::OnUniversalProperty", eAct);
   PvUniversalParam* param = universalParams_[index];
   if (eAct == MM::AfterSet)
   {
      // Before sending any value to the camera we must disable the streaming.
      // If the streaming is active the MM will resume it automatically as soon as this method finishes.
      if ( IsCapturing() )
         StopSequenceAcquisition();

      if ( param->IsEnum() )
      {
         // Enum values are treated as strings. The value is displayed as a combo box with values
         // read out from the camera. When the user picks the option, the string is compared in Set
         // method and proper enum value is send to the camera.
         std::string valToSet;
         pProp->Get( valToSet ); // Get the value that MM wants us to set
         param->Set( valToSet ); // Set the value to the PICAM parameter
      }
      else
      {
         double valToSet;
         pProp->Get( valToSet );
         param->Set( valToSet );
      }
      // We can only Write the parameters to the camera when the streaming is off, this is assured
      // only in this place.
      param->Write();
      // We immediately read the parameter back from the camera because it might get adjusted.
      // The parameter value is cached internaly and as soon as MM resumes the streaming we return
      // this cached value and do not touch the camera at all.
      param->Read();
   }
   else if (eAct == MM::BeforeGet)
   {
      // Here we can only return the cached parameter value. At this point the MM might already
      // resumed the streaming so no pl_set_param or pl_get_param should be called.
      if ( param->IsEnum() )
      {
         pProp->Set( param->ToString().c_str() );
      }
      else
      {
         // So far we only support 64bit double or Enum values for "Universal" properties.
         // No other value types should be added to g_UniversalParams, a regular property
         // with hand made handler should be manually created instead.
         pProp->Set( param->ToDouble() );
      }
   }

   return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// API methods
// ~~~~~~~~~~~

void Universal::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, deviceName_.c_str());
}

int Universal::initializeUniversalParams()
{
   int nRet = DEVICE_OK;
   universalParams_.clear();
   long propertyIndex = 0;

   // Iterate through all the parameters we have allowed to be used as Universal
   for ( int i = 0; i < g_UniversalParamsCount; i++ )
   {
      PvUniversalParam* p = new PvUniversalParam( g_UniversalParams[i].name, g_UniversalParams[i].id, this);
      if (p->IsAvailable())
      {
         if (p->IsEnum())
         {
            CPropertyActionEx *pAct = new CPropertyActionEx(this, &Universal::OnUniversalProperty, propertyIndex);
            nRet = CreateProperty( g_UniversalParams[i].name, p->ToString().c_str(), MM::String, p->IsReadOnly(), pAct);
            if ( !p->IsReadOnly() )
               SetAllowedValues( g_UniversalParams[i].name, p->GetEnumStrings() );
         }
         else
         {
            CPropertyActionEx *pAct = new CPropertyActionEx(this, &Universal::OnUniversalProperty, propertyIndex);
            nRet = CreateProperty( g_UniversalParams[i].name, p->ToString().c_str(), MM::Integer, p->IsReadOnly(), pAct);


            if ( !p->IsReadOnly() )
            {
               double min = p->GetMin();
               double max = p->GetMax();
               if ( (max-min) > 10 )
               {
                  // The property will show up as slider with defined range
                  SetPropertyLimits(g_UniversalParams[i].name, min, max);
               }
               else if ( (max-min) < 1000000 )
               {
                  // The property will show up as combo box with predefined values
                  vector<std::string> values;
                  for (int j = (int)min; j <= (int)max; j++)
                  {
                     ostringstream os;
                     os << j;
                     values.push_back(os.str());
                  }
                  SetAllowedValues(g_UniversalParams[i].name, values);
               }
               else
               {
                  // The property will be a simple edit box with editable value
                  LogMessage("The property has too large range. Not setting limits.");
               }
            }
         }
         universalParams_.push_back(p);
         propertyIndex++;
      }
      else
      {
         delete p;
      }
   }

   return nRet;
}

int Universal::initializePostProcessing()
{
   int nRet = DEVICE_OK;

   return nRet;
}



bool Universal::Busy()
{
   START_METHOD("Universal::Busy");
   return snappingSingleFrame_;
}


/**
 * Function name   : &Universal::SnapImage
 * Description     : Acquires a single frame and stores it in the internal
 *                   buffer.
 *                   This command blocks the calling thread
 *                   until the image is fully captured.
 * Return type     : int
 * Timing data     : On an Intel Mac Pro OS X 10.5 with CoolsnapEZ, 0 ms exposure
 *                   pl_exp_start_seq takes 28 msec
 *                   WaitForExposureDone takes 25 ms, for a total of 54 msec
 */
int Universal::SnapImage()
{
   START_METHOD("Universal::SnapImage");

   MM::MMTime start = GetCurrentMMTime();

   int nRet = DEVICE_ERR;

   if(snappingSingleFrame_)
   {
      LogMessage("Warning: Entering SnapImage while GetImage has not been done for previous frame", true);
      return nRet;
   }

   if(IsCapturing())
      return DEVICE_CAMERA_BUSY_ACQUIRING;


   if(!singleFrameModeReady_)
   {
      g_picamLock.Lock();
      Picam_StopAcquisition(hPICAM_);
      g_picamLock.Unlock();

      MM::MMTime mid = GetCurrentMMTime();
      LogTimeDiff(start, mid, "Exposure took 1: ", true);

      nRet = ResizeImageBufferSingle();
      if (nRet != DEVICE_OK)
         return LogMMError(nRet, __LINE__);
      singleFrameModeReady_ = true;

      mid = GetCurrentMMTime();
      LogTimeDiff(start, mid, "Exposure took 2: ", true);
   }

   if (!InitializeCalculatedBufferSize())
   {
      snappingSingleFrame_ = false;
      singleFrameModeReady_ = false;
      LogCamError( __LINE__, "Set Circular buffer error" );

   }

   // Reset event handle
   ResetEvent(hDataUpdatedEvent_);

   snappingSingleFrame_ = true;
   numImages_ = 1;
   curImageCnt_ = 0;


   g_picamLock.Lock();
   double maxReadTimeSec = (double)(camCurrentSpeed_.pixTime * GetImageHeight() * GetImageWidth()) / 1000000000.0f;
   // make the time out 2 seconds plus twice the exposure
   // Added readout time, this caused troubles on very low readout speeds and large buffers, this code timeouted before the image was read out
   MM::MMTime timeout((long)(triggerTimeout_ + maxReadTimeSec + 2*GetExposure() * 0.001), (long)(2*GetExposure() * 1000+ maxReadTimeSec));
   piint timeout_ms=(piint)((double)timeout.getMsec()+0.5);



   MM::MMTime end = GetCurrentMMTime();

   /* Number of frames */
   Picam_SetParameterLargeIntegerValue( hPICAM_, PicamParameter_ReadoutCount, 0);//numImages );

   const PicamParameter* failed_parameters;
   piint failed_parameters_count;
   Picam_CommitParameters(
         hPICAM_,
         &failed_parameters,
         &failed_parameters_count );
   Picam_DestroyParameters( failed_parameters );

   nRet=DEVICE_ERR;

   if (PicamError_None== Picam_StartAcquisition( hPICAM_ ))
   {
      if (WAIT_OBJECT_0==WaitForSingleObject( hDataUpdatedEvent_, timeout_ms+10000))
      {
         // maybe acquired!
         // numImages_=1
         if (curImageCnt_>0)
            nRet=DEVICE_OK;
      }
      else{
         // Timed out
         snappingSingleFrame_ = false;
         singleFrameModeReady_ = false;
         LogCamError( __LINE__, "Picam_StartAcquisition timed out" );

      }
      Picam_StopAcquisition(hPICAM_);
   }
   else{
      snappingSingleFrame_ = false;
      singleFrameModeReady_ = false;

   }
   g_picamLock.Unlock();

   end = GetCurrentMMTime();

   LogTimeDiff(start, end, "Exposure took 4: ", true);

   return nRet;
}


const unsigned char* Universal::GetImageBuffer()
{
   START_METHOD("Universal::GetImageBuffer");

   if(!snappingSingleFrame_)
   {
      LogMMMessage(__LINE__, "Warning: GetImageBuffer called before SnapImage()");
      return 0;
   }

   // wait for data or error
   unsigned char* pixBuffer(0);

   if (rgbaColor_)
   {
      // debayer the image and convert to color
      debayer_.Process(colorImg_, img_, (unsigned)camCurrentSpeed_.bitDepth);
      pixBuffer = colorImg_.GetPixelsRW();
   }
   else
      // use unchanged grayscale image
      pixBuffer = img_.GetPixelsRW();

   snappingSingleFrame_ = false;

   return pixBuffer;
}


double Universal::GetExposure() const
{
   START_METHOD("Universal::GetExposure");
   char buf[MM::MaxStrLength];
   buf[0] = '\0';
   GetProperty(MM::g_Keyword_Exposure, buf);
   return atof(buf);
}

void Universal::SetExposure(double exp)
{
   START_METHOD("Universal::SetExposure");
   int ret = SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(exp));
   if (ret != DEVICE_OK)
      LogMMError(ret, __LINE__);
}

/**
 * Returns the number of bits per pixel.
 * IN COLOR MODE THIS MEHOD RETURNS MODIFIED VALUE
 *
 */
unsigned Universal::GetBitDepth() const
{
   return rgbaColor_ ? 8 : (unsigned)camCurrentSpeed_.bitDepth;
}

long Universal::GetImageBufferSize() const
{
   if (rgbaColor_)
      return colorImg_.Width() * colorImg_.Height() * colorImg_.Depth();
   else
      return img_.Width() * img_.Height() * img_.Depth();
}


int Universal::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
   START_METHOD("Universal::SetROI");

   // PICAM does not like ROIs smaller than 2x2 pixels (8 bytes)
   // (This check avoids crash for 1x1 ROIs in PICAM 2.9.5)
   if ( xSize * ySize < 4 )
   {
      LogCamError( __LINE__, "Universal::SetROI ROI size not supported" );
      return ERR_ROI_SIZE_NOT_SUPPORTED;
   }

   // The acquisition must be stopped, and will be automatically started again by MMCore
   if (IsCapturing())
      StopSequenceAcquisition();

   // Request reconfiguration of acquisition before next use
   singleFrameModeReady_ = false;
   sequenceModeReady_    = false;

   // This is a workaround for strange behavior of ROI in MicroManager (1.4.15)
   // When ROI is drawn and applied on a binned image the MM sends the ROI in image coordinates,
   // e.g. 256x256 for 512x512 CCD with bin 2.
   // However, when user clicks the full ROI button, the coordinates are suddenly sent in
   // CCD coordinates, e.g. 512x512 for the same image (which is displayed as 256x256 image)
   // Here we simply decide what is MicroManager trying to send us and handle the ROI accordingly.
   // This might be a bug in the adapter code that makes the MM behave this way but I haven't found where.
   if ( x == 0 && y == 0 && xSize == (unsigned int)camSerSize_ && ySize == (unsigned int)camParSize_ )
   {
      roi_.PICAMRegion( (piint)x, (piint)y, (piint)xSize, (piint)ySize,
            (piint)binXSize_, (piint)binYSize_, camRegion_ );
   }
   else
   {
      roi_.PICAMRegion( (piint)(x*binXSize_), (piint)(y*binYSize_),
            (piint)(xSize*binXSize_), (piint)(ySize*binYSize_),
            (piint)binXSize_, (piint)binYSize_, camRegion_ );
   }

   // after a parameter is set, micromanager checks the size of the image,
   //  so we must make sure to update the size of the img_ buffer,
   //  before this function exits, also we don't want to configure a sequence
   //  when the initialized_ flag isn't set, because that simply isn't needed.
   img_.Resize(roi_.newXSize, roi_.newYSize, 2);
   colorImg_.Resize(roi_.newXSize, roi_.newYSize, 4);

   return DEVICE_OK;
}

int Universal::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
   START_METHOD("Universal::GetROI");

   x = roi_.newX;
   y = roi_.newY;
   xSize = roi_.newXSize;
   ySize = roi_.newYSize;

   return DEVICE_OK;
}

int Universal::ClearROI()
{
   START_METHOD("Universal::ClearROI");

   SetROI( 0, 0, camSerSize_, camParSize_ );

   return DEVICE_OK;
}

bool Universal::GetErrorText(int errorCode, char* text) const
{
   if (CCameraBase<Universal>::GetErrorText(errorCode, text))
      return true; // base message

   return false;
}

///////////////////////////////////////////////////////////////////////////////
// Utility methods
///////////////////////////////////////////////////////////////////////////////

int Universal::portChanged()
{
   std::vector<std::string> spdChoices;
   piint curPort = prmReadoutPort_->Current();

   // Read the available speeds for this port from our speed table
   std::map<piint, SpdTabEntry>::iterator i = camSpdTable_[curPort].begin();


   for( ; i != camSpdTable_[curPort].end(); ++i )
   {
      if (i->second.bEnable)
         spdChoices.push_back(i->second.spdString);
   }

   // Set the allowed readout rates
   SetAllowedValues( g_ReadoutRate, spdChoices );
   // Set the current speed to first avalable rate
   /*********************************************************/
   SetProperty( g_ReadoutRate, spdChoices[0].c_str());


   return DEVICE_OK;
}

int Universal::speedChanged()
{
   // Set the gain range for this speed. If the range is short enough use combo box
   if (camCurrentSpeed_.gainMax - camCurrentSpeed_.gainMin > 10 )
   {
      SetPropertyLimits( MM::g_Keyword_Gain, camCurrentSpeed_.gainMin, camCurrentSpeed_.gainMax );
   }
   else
   {
      vector<string> gainChoices;
      for (piint i = camCurrentSpeed_.gainMin; i <= camCurrentSpeed_.gainMax; i++)
      {
         gainChoices.push_back( CDeviceUtils::ConvertToString( i ) );
      }
      SetAllowedValues(MM::g_Keyword_Gain, gainChoices);
   }
   SetProperty( MM::g_Keyword_Gain, CDeviceUtils::ConvertToString(camCurrentSpeed_.gainMin) );

   return DEVICE_OK;
}

/*
 * Build the speed table based on camera settings. We use the speed table to get actual
 * bit depth, readout speed and gain range based on speed index.
 */
int Universal::buildSpdTable()
{
   camSpdTable_.clear();
   camSpdTableReverse_.clear();

   const PicamCollectionConstraint* speed_capable=NULL;
   const PicamCollectionConstraint* port_capable=NULL;
   const PicamCollectionConstraint* gain_capable=NULL;
   piint depth;
   pibln readable;
   pibln relevant;
   int nPortMax=1;
   piint nDefaultPort=0;
   piint nDefaultADC=0;
   piflt dDefaultAdcSpeed;

   /* Get the bit depth */
   Picam_CanReadParameter(hPICAM_, PicamParameter_PixelBitDepth, &readable );
   if (readable)
      Picam_GetParameterIntegerValue(hPICAM_, PicamParameter_PixelBitDepth,  &depth);
   else
      depth=16;

   /* Get ADC Quality */
   Picam_IsParameterRelevant(hPICAM_, PicamParameter_AdcQuality, &relevant);

   if (relevant){
      Picam_GetParameterCollectionConstraint(
            hPICAM_, PicamParameter_AdcQuality,
            PicamConstraintCategory_Capable,
            &port_capable);
      /* Read Default port */
      Picam_GetParameterIntegerDefaultValue(
            hPICAM_, PicamParameter_AdcQuality,
            &nDefaultPort);
      nPortMax=port_capable->values_count;


   }

   piint         nPortNum=0;
   pibln         settable;

   for (int portIndex = 0; portIndex < nPortMax; portIndex++){
      const pichar* adc_string;

      if (port_capable){
         nPortNum=(piint)(port_capable->values_array[portIndex]);
         /* Set ADC Quality */
         Picam_SetParameterIntegerValue(
               hPICAM_, PicamParameter_AdcQuality,
               (piint)nPortNum);


         Picam_GetEnumerationString( PicamEnumeratedType_AdcQuality, nPortNum, &adc_string);

      }

      /* Read Default ADC */
      if (nPortNum==nDefaultPort){
         Picam_GetParameterFloatingPointDefaultValue(
               hPICAM_, PicamParameter_AdcSpeed,
               &dDefaultAdcSpeed);
      }

      /* Get Speed table */
      Picam_GetParameterCollectionConstraint(
            hPICAM_, PicamParameter_AdcSpeed,
            PicamConstraintCategory_Capable,
            &speed_capable);
      for (piint spdIndex=0; spdIndex<speed_capable->values_count;spdIndex++){
         SpdTabEntry spdEntry;
         stringstream tmp;

         spdEntry.bitDepth = depth;
         spdEntry.portIndex = nPortNum; //portIndex;
         spdEntry.spdIndex = spdIndex;


         /* This speed is default */
         if (nPortNum==nDefaultPort){
            if ((spdEntry.adcRate>dDefaultAdcSpeed*0.9) && (spdEntry.adcRate<dDefaultAdcSpeed*1.1))
               nDefaultADC=spdIndex;
         }

         Picam_GetParameterCollectionConstraint(
               hPICAM_, PicamParameter_AdcAnalogGain,
               PicamConstraintCategory_Capable,
               &gain_capable);

         spdEntry.adcRate= speed_capable->values_array[spdIndex];
         spdEntry.gainMin = (piint)(gain_capable->values_array[0]);
         spdEntry.gainMax = (piint)(gain_capable->values_array[gain_capable->values_count-1]);

         Picam_DestroyCollectionConstraints(gain_capable);

         // values_array is MHz unit
         spdEntry.pixTime=(piint)(1000.0f/speed_capable->values_array[spdIndex]);


         // Save the string we will use in user interface for this choice
         if (speed_capable->values_array[spdIndex]>0.9){
            tmp << speed_capable->values_array[spdIndex] << "MHz " << spdEntry.bitDepth << "bit";
         }
         else{
            tmp << (speed_capable->values_array[spdIndex]*1000.0f) << "KHz " << spdEntry.bitDepth << "bit";

         }
         if (port_capable)
            tmp << " (" << adc_string << ")";

         /* Can set the speed?
            It's depend on port
            If device was opened PicamAdvanced_OpenCameraDevice faunction, it can confirm Picam_CanSetParameterFloatingPointValue
            */
         Picam_CanSetParameterFloatingPointValue(
               hPICAM_, PicamParameter_AdcSpeed,
               speed_capable->values_array[spdIndex], &settable);

         if (settable){
            Picam_SetParameterFloatingPointValue(
                  hPICAM_, PicamParameter_AdcSpeed,
                  speed_capable->values_array[spdIndex]);
         }

         /*
            If use Picam_OpenCamera, couldn't... I don't why..
            */
         /*
            Picam_SetParameterFloatingPointValue(
            hPICAM_, PicamParameter_AdcSpeed,
            speed_capable->values_array[spdIndex]);

            Picam_AreParametersCommitted( hPICAM_, &committed );

            settable=true;

            if( !committed )
            {
            const PicamParameter* failed_parameter_array = NULL;
            piint failed_parameter_count = 0;

            Picam_CommitParameters( hPICAM_, &failed_parameter_array, &failed_parameter_count );
            if( failed_parameter_count ){
            Picam_DestroyParameters( failed_parameter_array );
            settable=false;
            }
            }*/
         spdEntry.bEnable= (pibln)settable;

         spdEntry.spdString = tmp.str();
         camSpdTable_[nPortNum][spdIndex] = spdEntry;
         camSpdTableReverse_[nPortNum][spdEntry.spdString] = spdEntry;

      }
      if (port_capable)
         Picam_DestroyString( adc_string );
      Picam_DestroyCollectionConstraints(speed_capable);
   }
   if (port_capable)
      Picam_DestroyCollectionConstraints(port_capable);

   camCurrentSpeed_ = camSpdTable_[nDefaultPort][nDefaultADC];

   return DEVICE_OK;
}

/**
 * This function returns the correct exposure mode and exposure value to be used in both
 * pl_exp_setup_seq and pl_exp_setup_cont
 */
int Universal::GetExposureValue(piflt& exposureValue)
{
   int nRet = DEVICE_OK;

   g_picamLock.Lock();
   // PICAM always use msec unit
   exposureValue = (double)exposure_;
   g_picamLock.Unlock();

   return nRet;
}

int Universal::ResizeImageBufferContinuous()
{
   START_METHOD("Universal::ResizeImageBufferContinuous");

   int nRet = DEVICE_ERR;

   try
   {
      img_.Resize(roi_.newXSize, roi_.newYSize);
      colorImg_.Resize(roi_.newXSize, roi_.newYSize, 4);

      piint frameSize = 0;
      piint pvExposureMode = 0;
      piflt pvExposure = 0.0;
      nRet = GetExposureValue(pvExposure);
      if ( nRet != DEVICE_OK )
         return nRet;

      g_picamLock.Lock();

      PicamError err; /* Error Code */
      PicamRois region;

      Picam_GetParameterIntegerValue(hPICAM_,  PicamParameter_ReadoutStride,    &readoutStride_ );
      Picam_GetParameterIntegerValue(hPICAM_,  PicamParameter_FramesPerReadout, &framesPerReadout_ );
      Picam_GetParameterIntegerValue(hPICAM_,  PicamParameter_FrameStride,      &frameStride_ );
      Picam_GetParameterIntegerValue(hPICAM_,  PicamParameter_FrameSize,        &frameSize_ );

      /* Set Exposure time */
      Picam_SetParameterFloatingPointValue(
            hPICAM_,
            PicamParameter_ExposureTime, // msec units
            pvExposure );

      /* Set ROIs */
      region.roi_count=1;
      region.roi_array=&camRegion_;

      /* Set the region of interest */
      err = Picam_SetParameterRoisValue(hPICAM_,
            PicamParameter_Rois,
            &region);

      /* Error check */
      pibln committed;
      Picam_AreParametersCommitted( hPICAM_, &committed );

      if( !committed )
      {
         const PicamParameter* failed_parameter_array = NULL;
         piint failed_parameter_count = 0;

         Picam_CommitParameters( hPICAM_, &failed_parameter_array, &failed_parameter_count );
         if( failed_parameter_count ){
            Picam_DestroyParameters( failed_parameter_array );
            nRet=DEVICE_ERR;
         }
      }



      g_picamLock.Unlock();

      frameSize=img_.Height() * img_.Width() * img_.Depth();
      /* if (img_.Height() * img_.Width() * img_.Depth() != frameSize)
         {
         return LogMMError(DEVICE_INTERNAL_INCONSISTENCY, __LINE__); // buffer sizes don't match ???
         }*/

      nRet = DEVICE_OK;
   }
   catch( const std::exception& e)
   {
      LogCamError( __LINE__, e.what() );
   }

   singleFrameModeReady_ = false;
   LogMessage("ResizeImageBufferContinuous singleFrameModeReady_=false", true);
   return nRet;

}

/**
 * This function calls pl_exp_setup_seq with the correct parameters, to set the camera
 * in a mode in which single images can be taken
 *
 * Timing data:      On a Mac Pro OS X 10.5 with CoolsnapEZ, this functions takes
 *                   takes 245 msec.
 */
int Universal::ResizeImageBufferSingle()
{
   START_METHOD("Universal::ResizeImageBufferSingle");

   int nRet = DEVICE_ERR;

   try
   {
      img_.Resize(roi_.newXSize, roi_.newYSize);
      colorImg_.Resize(roi_.newXSize, roi_.newYSize, 4);

      piint pvExposureMode = 0;
      piflt pvExposure = 0.0;
      nRet = GetExposureValue(pvExposure);
      if ( nRet != DEVICE_OK ){
         return nRet;
      }

      g_picamLock.Lock();

      PicamError err; /* Error Code */
      PicamRois region;

      Picam_GetParameterIntegerValue(hPICAM_,  PicamParameter_ReadoutStride,    &readoutStride_ );
      Picam_GetParameterIntegerValue(hPICAM_,  PicamParameter_FramesPerReadout, &framesPerReadout_ );
      Picam_GetParameterIntegerValue(hPICAM_,  PicamParameter_FrameStride,      &frameStride_ );
      Picam_GetParameterIntegerValue(hPICAM_,  PicamParameter_FrameSize,        &frameSize_ );

      /* Set Exposure time */
      Picam_SetParameterFloatingPointValue(
            hPICAM_,
            PicamParameter_ExposureTime, // msec units
            pvExposure );

      /* Set ROIs */
      region.roi_count=1;
      region.roi_array=&camRegion_;

      /* Set the region of interest */
      err = Picam_SetParameterRoisValue(hPICAM_,
            PicamParameter_Rois,
            &region);

      /* Error check */
      pibln committed;
      Picam_AreParametersCommitted( hPICAM_, &committed );

      if( !committed )
      {
         const PicamParameter* failed_parameter_array = NULL;
         piint failed_parameter_count = 0;

         Picam_CommitParameters( hPICAM_, &failed_parameter_array, &failed_parameter_count );
         if( failed_parameter_count ){
            for (int i=0;i<failed_parameter_count;i++){
               if (failed_parameter_array[i] == PicamParameter_Rois)
                  nRet = DEVICE_ERR;
            }
         }
      }


      g_picamLock.Unlock();


      /*if (img_.Height() * img_.Width() * img_.Depth() != frameSize)
        {
        return LogMMError(DEVICE_INTERNAL_INCONSISTENCY, __LINE__); // buffer sizes don't match ???
        }*/

   }
   catch(...)
   {
      LogMessage("Caught error in ResizeImageBufferSingle", false);
   }

   return nRet;
}


///////////////////////////////////////////////////////////////////////////////
// Continuous acquisition
//

#ifndef linux
/*
 * Overrides a virtual function from the CCameraBase class
 * Do actual capture
 * Called from the acquisition thread function
 */
int Universal::ThreadRun(void)
{
   START_METHOD(">>>Universal::ThreadRun");

   int     ret = DEVICE_ERR;
   char dbgBuf[128]; // Debug log buffer
   uniAcqThd_->setStop(false); // make sure this thread's status is updated properly.
   pibln bRunning=TRUE;
   Metadata md;

   try
   {
      //Picam_GetParameterIntegerValue( hPICAM_, PicamParameter_ReadoutStride, &readoutstride );
      do
      {
         // wait until image is ready
         double maxReadTimeSec = (double)(camCurrentSpeed_.pixTime * GetImageHeight() * GetImageWidth()) / 1000000000.0f;
         // make the time out 2 seconds plus twice the exposure
         // Added readout time, this caused troubles on very low readout speeds and large buffers, this code timeouted before the image was read out
         MM::MMTime timeout((long)(triggerTimeout_ + maxReadTimeSec + 2*GetExposure() * 0.001), (long)(2*GetExposure() * 1000+ maxReadTimeSec));
         piint timeout_ms=(piint)((double)timeout.getMsec()+0.5);
         MM::MMTime startTime = GetCurrentMMTime();
         MM::MMTime elapsed(0,0);

         g_picamLock.Lock();

         if (WAIT_OBJECT_0==WaitForSingleObject( hDataUpdatedEvent_, timeout_ms+(curImageCnt_==0 ? 10000:0)))
         {
            // maybe acquired!
            // numImages_=1
            if (curImageCnt_>0){
               ret=DEVICE_OK;
               bRunning=!(dataUpdated_.bAcquisitionInactive);
            }
         }
         else{
            // Timed out
            bRunning=FALSE;
            StopSequenceAcquisition();
         }
         g_picamLock.Unlock();
         ResetEvent(hDataUpdatedEvent_);


      }
      while (!uniAcqThd_->getStop() && curImageCnt_ < numImages_ && bRunning && ret==DEVICE_OK);

      sprintf( dbgBuf, "ACQ LOOP FINISHED: thdGetStop:%u, ret:%u,  curImageCnt_: %lu, numImages_: %lu", \
            uniAcqThd_->getStop(), ret, curImageCnt_, numImages_);
      LogMMMessage( __LINE__, dbgBuf );

      if (curImageCnt_ >= numImages_)
         curImageCnt_ = 0;
      OnThreadExiting();
      uniAcqThd_->setStop(true);

      START_METHOD("<<<Universal::ThreadRun");
      return ret;

   }
   catch(...)
   {
      LogMessage(g_Msg_EXCEPTION_IN_THREAD, false);
      OnThreadExiting();
      uniAcqThd_->setStop(true);
      return ret;
   }
}

/**
 * Micromanager calls the "live" acquisition a "sequence"
 *  don't get this confused with a PICAM sequence acquisition, it's actually circular buffer mode
 */
int Universal::PrepareSequenceAcqusition()
{
   START_METHOD("Universal::PrepareSequenceAcqusition");

   if (IsCapturing())
   {
      return ERR_BUSY_ACQUIRING;
   }
   else if (!sequenceModeReady_)
   {
      if ( binSize_ != newBinSize_ || binXSize_ != newBinXSize_ || binYSize_ != newBinYSize_ )
      {
         // Binning has changed so we need to reset the ROI
         roi_.PICAMRegion( 0, 0, camSerSize_, camParSize_, (piint)newBinXSize_,(piint)newBinYSize_, camRegion_ );
      }
      binSize_ = newBinSize_;
      binXSize_ = newBinXSize_;
      binYSize_ = newBinYSize_;
      // reconfigure anything that has to do with pl_exp_setup_cont
      int nRet = ResizeImageBufferContinuous();
      if ( nRet != DEVICE_OK )
      {
         return nRet;
      }
      GetCoreCallback()->InitializeImageBuffer( 1, 1, GetImageWidth(), GetImageHeight(), GetImageBytesPerPixel() );
      GetCoreCallback()->PrepareForAcq(this);
      sequenceModeReady_ = true;
   }
   return DEVICE_OK;
}

/**
 * Micromanager calls the "live" acquisition a "sequence"
 *  don't get this confused with a PICAM sequence acquisition, it's actually circular buffer mode
 */
int Universal::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{
   START_METHOD("Universal::StartSequenceAcquisition");

   int ret = PrepareSequenceAcqusition();
   if (ret != DEVICE_OK)
      return ret;

   stopOnOverflow_  = stopOnOverflow;
   numImages_       = numImages;
   curImageCnt_     = 0;
   pibln committed;

   MM::MMTime start = GetCurrentMMTime();
   g_picamLock.Lock();


   /* Set user circular Buffer*/
   if (!InitializeCalculatedBufferSize())
   {
      g_picamLock.Unlock();

      int picamErr = LogCamError(__LINE__, "PicamParameter_SetCircularBuffer error");
      return picamErr;

   }

   /* Set Number of Images*/
   //pi64s readouts = (numImages<100) ? numImages : 100;

   Picam_SetParameterLargeIntegerValue( hPICAM_, PicamParameter_ReadoutCount, 0);//numImages );
   Picam_AreParametersCommitted( hPICAM_, &committed );
   if( !committed )
   {
      const PicamParameter* failed_parameter_array = NULL;
      piint           failed_parameter_count = 0;
      Picam_CommitParameters( hPICAM_, &failed_parameter_array, &failed_parameter_count );
      if( failed_parameter_count )
      {
         Picam_DestroyParameters( failed_parameter_array );

         g_picamLock.Unlock();

         ResizeImageBufferSingle();
         int picamErr = LogCamError(__LINE__, "PicamParameter_ReadoutCount");
         return picamErr;
      }
   }

   /* Create Handle */
   ResetEvent(hDataUpdatedEvent_);

   if (PicamError_None!= Picam_StartAcquisition( hPICAM_ ))
   {
      g_picamLock.Unlock();
      int picamErr = LogCamError(__LINE__, "Picam_StartAcquisition");
      ResizeImageBufferSingle();

      return picamErr;
   }



   g_picamLock.Unlock();
   startTime_ = GetCurrentMMTime();

   MM::MMTime end = GetCurrentMMTime();
   LogTimeDiff(start, end, true);

   // initially start with the exposure time as the actual interval estimate
   SetProperty(MM::g_Keyword_ActualInterval_ms, CDeviceUtils::ConvertToString(exposure_));

   uniAcqThd_->Start();

   isAcquiring_ = true;

   char label[MM::MaxStrLength];
   GetLabel(label);
   ostringstream os;
   os << "Started sequence on " << label << ", at " << startTime_.serialize() << ", with " << numImages << " and " << interval_ms << " ms" << endl;
   LogMessage(os.str().c_str());

   return DEVICE_OK;
}

/**
 * Micromanager calls the "live" acquisition a "sequence"
 *  don't get this confused with a PICAM sequence acquisition, it's actually circular buffer mode
 */
int Universal::StopSequenceAcquisition()
{
   START_METHOD("Universal::StopSequenceAcquisition");
   // call function of the base class, which does useful work
   int nRet = DEVICE_OK;

   // removed redundant calls to pl_exp_stop_cont &
   //  pl_exp_finish_seq because they get called automatically when the thread exits.
   if(IsCapturing())
   {
      uniAcqThd_->setStop(true);
      uniAcqThd_->wait();

      isAcquiring_ = false;
   }
   curImageCnt_ = 0;

   // LW: Give the camera some time to stop acquiring. This reduces occasional
   //     crashes/hangs when frequently starting/stopping with some fast cameras.
   CDeviceUtils::SleepMs( 50 );

   return nRet;
}



int AcqSequenceThread::svc(void)
{
   int ret=DEVICE_ERR;
   try
   {
      ret = camera_->ThreadRun();
   }
   catch(...)
   {
      camera_->LogMessage(g_Msg_EXCEPTION_IN_THREAD, false);
   }
   return ret;
}



void Universal::OnThreadExiting() throw ()
{
   try
   {
      g_picamLock.Lock();

      Picam_StopAcquisition( hPICAM_ );

      g_picamLock.Unlock();

      sequenceModeReady_ = false;
      isAcquiring_       = false;

      // The AcqFinished is called inside the parent OnThreadExiting()
      CCameraBase<Universal>::OnThreadExiting();
   }
   catch (...)
   {
      LogMMMessage(__LINE__, g_Msg_EXCEPTION_IN_ON_THREAD_EXITING);
   }
}

#endif


/**
 * Creates metadata for current frame
 */
int Universal::BuildMetadata( Metadata& md )
{
   char label[MM::MaxStrLength];
   GetLabel(label);

   MM::MMTime timestamp = GetCurrentMMTime();
   md.Clear();
   md.put("Camera", label);

#ifdef PICAM_FRAME_INFO_SUPPORTED
   md.PutImageTag<int32>("PICAM-FrameNr", pFrameInfo_->FrameNr);
   md.PutImageTag<int32>("PICAM-ReadoutTime", pFrameInfo_->ReadoutTime);
   md.PutImageTag<long64>("PICAM-TimeStamp",  pFrameInfo_->TimeStamp);
   md.PutImageTag<long64>("PICAM-TimeStampBOF", pFrameInfo_->TimeStampBOF);
#endif

   MetadataSingleTag mstStartTime(MM::g_Keyword_Metadata_StartTime, label, true);
   mstStartTime.SetValue(CDeviceUtils::ConvertToString(startTime_.getMsec()));
   md.SetTag(mstStartTime);

   MetadataSingleTag mstElapsed(MM::g_Keyword_Elapsed_Time_ms, label, true);
   MM::MMTime elapsed = timestamp - startTime_;
   mstElapsed.SetValue(CDeviceUtils::ConvertToString(elapsed.getMsec()));
   md.SetTag(mstElapsed);

   MetadataSingleTag mstCount(MM::g_Keyword_Metadata_ImageNumber, label, true);
   mstCount.SetValue(CDeviceUtils::ConvertToString(curImageCnt_));
   md.SetTag(mstCount);

   double actualInterval = elapsed.getMsec() / curImageCnt_;
   SetProperty(MM::g_Keyword_ActualInterval_ms, CDeviceUtils::ConvertToString(actualInterval));

   return DEVICE_OK;
}

int Universal::PushImage(const unsigned char* pixBuffer, Metadata* pMd )
{
   START_METHOD("Universal::PushImage");

   int nRet = DEVICE_ERR;
   MM::Core* pCore = GetCoreCallback();
   // This method inserts a new image into the circular buffer (residing in MMCore)
   nRet = pCore->InsertMultiChannel(this,
         pixBuffer,
         1,
         GetImageWidth(),
         GetImageHeight(),
         GetImageBytesPerPixel(),
         pMd); // Inserting the md causes crash in debug builds
   if (!stopOnOverflow_ && nRet == DEVICE_BUFFER_OVERFLOW)
   {
      // do not stop on overflow - just reset the buffer
      pCore->ClearImageBuffer(this);
      nRet = pCore->InsertMultiChannel(this,
            pixBuffer,
            1,
            GetImageWidth(),
            GetImageHeight(),
            GetImageBytesPerPixel(),
            pMd);
   }

   return nRet;
}


piint Universal::LogCamError(int lineNr, std::string message, bool debug) throw()
{
   return 0;
}

int Universal::LogMMError(int errCode, int lineNr, std::string message, bool debug) const throw()
{
   try
   {
      char strText[MM::MaxStrLength];
      if (!CCameraBase<Universal>::GetErrorText(errCode, strText))
      {
         CDeviceUtils::CopyLimitedString(strText, "Unknown");
      }
      ostringstream os;
      os << "Error code "<< errCode << ": " <<  strText <<"\n";
      os << "In file: " << __FILE__ << ", " << "line: " << lineNr << ", " << message;
      LogMessage(os.str(), debug);
   }
   catch(...) {}
   return errCode;
}

void Universal::LogMMMessage(int lineNr, std::string message, bool debug) const throw()
{
   try
   {
      ostringstream os;
      os << message << ", in file: " << __FILE__ << ", " << "line: " << lineNr;
      LogMessage(os.str(), debug);
   }
   catch(...){}
}



/**************************** Post Processing Functions ******************************/
#ifdef WIN32

int Universal::OnResetPostProcProperties(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_METHOD("Universal::OnResetPostProcProperties");


   return DEVICE_OK;
}

/**
 * Reads current values of all post processing parameters from the camera
 * and stores the values in local array.
 */
int Universal::refreshPostProcValues()
{
   return DEVICE_OK;
}

/**
 * Reverts a single setting that we know had an error
 */
int Universal::revertPostProcValue( long absoluteParamIdx, MM::PropertyBase* pProp )
{

   return DEVICE_OK;
}

/**
 * When user changes a PP property in UI this method is called twice: first with MM::AfterSet followed by
 * immediate MM::BeforeGet to obtain the actual value and display it back in UI.
 * When live mode is active and user sets the property the MM stops acquisition, calls this method with
 * MM::AfterSet, resumes the acquisition and asks for the value back with MM::BeforeGet. For this reason
 * we cannot get the actual property value directly from the camera with pl_get_param because the streaming
 * might be already active. (we cannot call pl_get or pl_set when continuous streaming mode is active)
 */
int Universal::OnPostProcProperties(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{
   START_ONPROPERTY("Universal::OnPostProcProperties", eAct);

   return DEVICE_OK;
}

#endif // WIN32

int Universal::AddFrame(unsigned char* frame)
{
   Metadata md;

   curImageCnt_++; // A new frame has been successfully retrieved from the camera
   BuildMetadata( md );

   PushImage( (unsigned char*)frame, &md );

   return DEVICE_OK;
}
//===========================================================================
