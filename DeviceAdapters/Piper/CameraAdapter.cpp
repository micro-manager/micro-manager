///////////////////////////////////////////////////////////////////////////////
// FILE:          CameraAdapter.cpp
// PROJECT:       Piper Micro-Manager Camera Adapter
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:    
//                
// AUTHOR:        Terry L. Sprout, Terry.Sprout@Agile-Automation.com
//
// COPYRIGHT:     (c) 2009, AgileAutomation, Inc, All rights reserved
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
//

#include "stdafx.h"
#include "CameraAdapter.h"

#define __CS_ENABLE TRUE

// The following pointer contains the address of the one and only allowed
// instance of the camera device. Any instance creation attempted while
// this pointer is not NULL will result in a failure.
static CCameraAdapter *s_pOnlyCamera = NULL;

void CALLBACK LibCallback( INT16 nCmd, LPVOID pvCam, LPVOID apvArgs[] )
{
   if( !pvCam )
   {
      return;
   }

   CCameraAdapter *pCamera = (CCameraAdapter*)pvCam;
   switch( nCmd )
   {
   default:
      break;
   case PIL_LCB_GRABBER_CHANGED:
      pCamera->OnGrabberChanged( (LPCTSTR)apvArgs[0] );
      break;
   }
}


void CALLBACK PipeCallback( INT16 /*nPipe*/, INT16 nCmd, LPVOID pvCam, LPVOID apvArgs[] )
{
   if( !pvCam )
   {
      return;
   }

   CCameraAdapter *pCamera = (CCameraAdapter*)pvCam;
   switch( nCmd )
   {
   default:
      break;
   case PIL_PCB_CAMERA_CHANGED:
      pCamera->OnCameraChanged( (LPCTSTR)apvArgs[0], *((int*)apvArgs[1]) );
      break;
   case PIL_PCB_CAMERA_SN_CHANGED:
      pCamera->OnCameraIdChanged( (LPCTSTR)apvArgs[0] );
      break;
   case PIL_PCB_CAMERA_MODE_CHANGED:
      pCamera->OnCameraModeChanged( (LPCTSTR)apvArgs[0] );
      break;
   case PIL_PCB_CAMERA_PROPS_CHANGED:
      pCamera->OnCameraPropertiesChanged();
      break;
   case PIL_PCB_ION_FEEDBACK_SIZE_CHANGED:
      pCamera->OnIonFeedbackSizeChanged( *((int*)apvArgs[0]) );
      break;
   case PIL_PCB_ION_FEEDBACK_ENABLED:
      pCamera->OnIonFeedbackEnabled( *((BOOL*)apvArgs[0]) );
      break;
   case PIL_PCB_HOSTOGRAM_TRANSFER_CHANGED:
      pCamera->OnHistogramTransferChanged
         (
            *((double*)apvArgs[0]), // brightness
            *((double*)apvArgs[1]), // contrast
            *((double*)apvArgs[2])  // gamma
         );
      break;
   case PIL_PCB_HOSTOGRAM_AUTO_CONTRAST:
      pCamera->OnHistogramAutoContrast( *((BOOL*)apvArgs[0]) );
      break;
   case PIL_PCB_DISCRIMINATOR_MAX_VALUE:
      pCamera->OnDiscriminatorMaxValue( *((double*)apvArgs[0]) );
      break;
   case PIL_PCB_DISCRIMINATOR_NEW_VALUES:
      pCamera->OnDiscriminatorNewValues
         (
            *((double*)apvArgs[0]),
            *((double*)apvArgs[1]),
            *((double*)apvArgs[2]),
            *((double*)apvArgs[3]),
            *((BOOL*)apvArgs[4])
         );
      break;
   case PIL_PCB_DISCRIMINATOR_DISCARD_COUNT:
      pCamera->OnDiscriminatorDiscardCount( *((int*)apvArgs[0]), *((int*)apvArgs[1]) );
      break;
   case PIL_PCB_DISCRIMINATOR_ENABLED:
      pCamera->OnDiscriminatorEnabled( *((BOOL*)apvArgs[0]) );
      break;
   case PIL_PCB_INTEGRATOR_DEPTH_CHANGED:
      pCamera->OnIntegratorDepthChanged( *((INT32*)apvArgs[0]) );
      break;
   case PIL_PCB_INTEGRATOR_CURRENT_DEPTH:
      pCamera->OnIntegratorCurrentDepth( *((INT32*)apvArgs[0]) );
      break;
   case PIL_PCB_INTEGRATOR_RATE_CHANGED:
      pCamera->OnIntegratorRateChanged( *((double*)apvArgs[0]) );
      break;
   case PIL_PCB_INTEGRATOR_ENABLED:
      pCamera->OnIntegratorEnabled( *((BOOL*)apvArgs[0]) );
      break;
   case PIL_PCB_AVERAGING_DEPTH_CHANGED:
      pCamera->OnAveragingDepthChanged( *((INT32*)apvArgs[0]) );
      break;
   case PIL_PCB_AVERAGING_CURRENT_DEPTH:
      pCamera->OnAveragingCurrentDepth( *((INT32*)apvArgs[0]) );
      break;
   case PIL_PCB_AVERAGING_RATE_CHANGED:
      pCamera->OnAveragingRateChanged( *((double*)apvArgs[0]) );
      break;
   case PIL_PCB_SMOOTH_AVERAGING_ENABLED:
      pCamera->OnSmoothAveragingEnabled( *((BOOL*)apvArgs[0]) );
      break;
   case PIL_PCB_FAST_AVERAGING_ENABLED:
      pCamera->OnFastAveragingEnabled( *((BOOL*)apvArgs[0]) );
      break;
   }
}

const double CCameraAdapter::nominalPixelSizeUm_ = 1.0;

// Local property names
static LPCTSTR sc_pszPropFrameGrabber = "CameraID-FrameGrabber";
static LPCTSTR sc_pszPropCameraName = "CameraID-Name";
static LPCTSTR sc_pszPropCameraID = "CameraID-Serial";
static LPCTSTR sc_pszPropCameraMode = "CameraID-Mode";
static LPCTSTR sc_pszPropIntegration = "CameraSensor-Integration";
static LPCTSTR sc_pszPropExposure = "CameraSensor-Exposure";
static LPCTSTR sc_pszPropVideoGain = "CameraSensor-Gain";
static LPCTSTR sc_pszPropVideoOffset = "CameraSensor-Offset";
static LPCTSTR sc_pszPropFrameLeft = "CameraFrame-Left";
static LPCTSTR sc_pszPropFrameTop = "CameraFrame-Top";
static LPCTSTR sc_pszPropFrameWidth = "CameraFrame-Width";
static LPCTSTR sc_pszPropFrameHeight = "CameraFrame-Height";
static LPCTSTR sc_pszPropIntensVolts = "CameraIntensifier-McpVolts";
static LPCTSTR sc_pszPropIntensGain = "CameraIntensifier-Gain";
static LPCTSTR sc_pszShowControlPanel = "ShowControlPanel";
static LPCTSTR sc_pszSourceBrightness = "InputFilter-Brightness";
static LPCTSTR sc_pszSourceContrast = "InputFilter-Contrast";
static LPCTSTR sc_pszSourceGamma = "InputFilter-Gamma";
static LPCTSTR sc_pszSourceThreshold = "InputFilter-Threshold";
static LPCTSTR sc_pszSourceAutoContrast = "InputFilter-AutoContrast";
static LPCTSTR sc_pszSourceNormalContrast = "InputFilter-NormalContrast";
static LPCTSTR sc_pszIonFeedbackFilterSize = "IonFeedback-FilterSize";
static LPCTSTR sc_pszEnableIonFeedback = "IonFeedback-Enabled";
static LPCTSTR sc_pszRamStackDepth = "RamStack-Depth";
static LPCTSTR sc_pszRamStackExposure = "RamStack-Exposure";
static LPCTSTR sc_pszEnableRamStack = "RamStack-Enabled";
static LPCTSTR sc_pszRamAverageDepth = "RamAverage-Depth";
static LPCTSTR sc_pszRamAverageExposure = "RamAverage-Exposure";
static LPCTSTR sc_pszEnableSmoothRamAverage = "RamAverage-SmoothEnabled";
static LPCTSTR sc_pszEnableFastRamAverage = "RamAverage-FastEnabled";

int s_nStatus;
#define PIL_ERROR 2000
#define RETURN_ON_PIL_ERROR( result ) if( 0 > (s_nStatus = (result)) ) return PIL_ERROR - (INT16)s_nStatus
#define RETURN_ON_FAILURE( result ) if( 0 > (s_nStatus = (result)) ) return (INT16)s_nStatus
#define RETURN_ON_MM_ERROR( result ) if( DEVICE_OK != (s_nStatus = (result)) ) return s_nStatus

static UINT StreamThread( LPVOID pvAdapter )
{
   ((CCameraAdapter*)pvAdapter)->Capture();
   return 0;
}

///////////////////////////////////////////////////////////////////////////////
// CCameraAdapter implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~

/**
 * CCameraAdapter constructor.
 * Setup default all variables and create device properties required to exist
 * before intialization. In this case, no such properties were required. All
 * properties will be created in the Initialize() method.
 *
 * As a general guideline Micro-Manager devices do not access hardware in the
 * the constructor. We should do as little as possible in the constructor and
 * perform most of the initialization in the Initialize() method.
 */
CCameraAdapter::CCameraAdapter( LPCTSTR pszName )
   : m_punRoi(NULL)
   , m_sMyName(pszName)
   , m_bIsConnected(FALSE)
   , m_bIsInitialized(false)
   , m_bIsBusy(false)
   , readoutUs_(0.0)
{
   m_bStream = FALSE;
   m_nStreamImages = 0;
   m_nStreamCount = 0;
   m_unTimeout = 5000;
   m_fStreamAvgTime = 0.0;
   m_fExposure = 1.0;
   m_nFrameClocks = 1;
   m_nMMFrameClocks = 1;
   m_bChangingGrabber = FALSE;
   m_bChangingCamera = FALSE;
   m_bChangingCameraSn = FALSE;
   m_bChangingCameraMode = FALSE;
   m_nIntegratorDepth = 1;
   m_bUpdateInProgress = FALSE;
   m_bExposureInSync = TRUE;
   m_bShowControlPanel = TRUE;
   m_bStopOnOverflow = FALSE;
   m_bModeChanged = FALSE;
   m_bPropsChanged = FALSE;
   m_bClearROI = FALSE;
   m_pCS = new CCriticalSection;
   m_hCaptureThread = NULL;
   m_hCaptureEnded = CreateEvent( NULL, TRUE, FALSE, NULL );

   m_nCurGrabber = -1;
   m_nCurCamera = -1;
   m_nCurCameraSn = -1;
   m_nCurCameraMode = -1;
   m_nNewCameraMode = -1;

   // operator settings
   m_fSourceBrightness = 0.0;
   m_fSourceContrast = 1.0;
   m_fSourceGamma = 1.0;
   m_nSourceThreshold = 0;
   m_bSourceAutoContrast = FALSE;
   m_nIonFeedbackFilterSize = 1;
   m_bIonFeedbackEnabled = FALSE;
   m_nIntegratorDepth = 1;
   m_fIntegratorRate = 1.0;
   m_fIntegratorExposure = 1.0;
   m_bIntegratorEnabled = FALSE;
   m_nAveragingDepth = 1;
   m_fAveragingRate = 1.0;
   m_bSmoothAvgEnabled = FALSE;
   m_bFastAvgEnabled = FALSE;
   m_fDiscriminatorMax = 0.0;
   m_fDiscriminatorAverage = 0.0;
   m_fDiscriminatorStdDev = 0.0;
   m_fDiscriminatorMeasured = 0.0;
   m_fDiscriminatorSigma = 0.0;
   m_bDiscriminatorDiscarded = FALSE;
   m_nDiscriminatorNumDiscarded = 0;
   m_nDiscriminatorNumTotal = 0;
   m_bDiscriminatorEnabled = FALSE;

   m_fCapFrameRate = 1.0;
   m_nCapBits = 0;
   m_nCapLeft = 0;
   m_nCapWidth = 0;
   m_nCapTop = 0;
   m_nCapHeight = 0;
   m_nCapXBin = 1;
   m_nCapYBin = 1;
   m_nVideoGain = 0;
   m_nVideoOffset = 0;
   m_fIntensifierVolts = 0.0;
   m_fIntensifierGain = 0.0;

   m_nImgLeft = 0;
   m_nImgWidth = 0;
   m_nImgTop = 0;
   m_nImgHeight = 0;
   m_nImgStride = 0;
   m_nImgPixelBytes = 0;
   m_nImgImageBytes = 0;
   m_unImgSaturation = 0;
   m_fImgFrameRate = 1.0;

   m_nRoiLeft = 0;
   m_nRoiWidth = 0;
   m_nRoiTop = 0;
   m_nRoiHeight = 0;
   m_nRoiStride = 0;
   m_nRoiImageBytes = 0;
   m_punRoi = 0;
   m_nFrameClocks = 0;
   m_fExposure = 1.0;
   m_nIntervalCount = 0;

   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();
   readoutStartTime_ = GetCurrentMMTime();
   SetErrorText(ERR_MULTIPLE_LIBRARY, "An SPI camera is being used by another Micro-Manager instance");
   SetErrorText(ERR_MULTIPLE_CAMERA, "Only one SPI camera adapter allowed");
   SetErrorText(ERR_BUSY_ACQUIRING, "Busy acquiring an image");
   SetErrorText(ERR_INVALID_GRABBER, "Invalid frame grabber");
   SetErrorText(ERR_INVALID_CAMERA, "Invalid camera for the selected frame grabber");
   SetErrorText(ERR_INVALID_CAMERA_ID, "Invalid camera S/N for the selectd camera");
   SetErrorText(ERR_INVALID_CAMERA_MODE, "Invalid camera mode for the selectd camera");
   SetErrorText(PIL_ERROR-PIL_LIB_INVALID_LICENSE, "Invalid license to Piper");
   SetErrorText(PIL_ERROR-PIL_LIB_NOT_CONNECTED, "Piper API is not connected");
   SetErrorText(PIL_ERROR-PIL_LIB_ALREADY_CONNECTED, "Piper API is already connected");
   SetErrorText(PIL_ERROR-PIL_INVALID_PIPE_ENUM, "Piper API error: Invalid frame grabber enumeration");
   SetErrorText(PIL_ERROR-PIL_PIPE_NOT_CONNECTED, "Piper API error: Frame grabber is not connected");
   SetErrorText(PIL_ERROR-PIL_PIPE_ALREADY_CONNECTED, "Piper API error: Frame grabber is already connected");
   SetErrorText(PIL_ERROR-PIL_INVALID_CAMERA_ENUM, "Piper API error: Invalid camera enumeration");
   SetErrorText(PIL_ERROR-PIL_INVALID_CAMERA_UID, "Piper API error: Invalid camera unique ID");
   SetErrorText(PIL_ERROR-PIL_INVALID_CAMERA_ID, "Piper API error: Invalid camera ID");
   SetErrorText(PIL_ERROR-PIL_INVALID_CAMERA_SN_ENUM, "Piper API error: Invalid camera serial number");
   SetErrorText(PIL_ERROR-PIL_INVALID_CAMERA_MODE_ENUM, "Piper API error: Invalid camera mode enumeration");
   SetErrorText(PIL_ERROR-PIL_TIME_EXPIRED, "Piper API error: Time expired while acquiring image");

   // show control panel
   CPropertyAction *pShowCPAct = new CPropertyAction (this, &CCameraAdapter::OnShowControlPanel);
   CreateProperty( sc_pszShowControlPanel, "YES", MM::String, false, pShowCPAct, true );

   m_asBooleans.push_back( "NO" );
   m_asBooleans.push_back( "YES" );
   SetAllowedValues( sc_pszShowControlPanel, m_asBooleans );
}

/**
 * CCameraAdapter destructor.
 * If this device used as intended within the Micro-Manager system,
 * Shutdown() will be always called before the destructor. But in any case
 * we need to make sure that all resources are properly released even if
 * Shutdown() was not called.
 */
CCameraAdapter::~CCameraAdapter()
{
   if( m_pCS )
   {
      delete m_pCS;
   }
}

/**
 * Obtains device name.
 * Required by the MM::Device API.
 */
void CCameraAdapter::GetName(char* name) const
{
   // We just return the name we use for referring to this
   // device adapter.
   CDeviceUtils::CopyLimitedString(name, m_sMyName);
}

/**
 * Tells us if device is still processing asynchronous command.
 * Required by the MM:Device API.
 */
bool CCameraAdapter::Busy()
{
//OD   CSingleLock oCS( m_pCS, __CS_ENABLE );
//   return m_bStream ? true : false;
   return false;
}
/**
 * Tells us if camera runs a sequence of capturing.
 * Required by the MM:Device API.
 */
bool CCameraAdapter::IsCapturing()
{
   CSingleLock oCS( m_pCS, __CS_ENABLE );
   return m_bStream ? true : false;
}


/**
 * Intializes the hardware.
 * Required by the MM::Device API.
 * Typically we access and initialize hardware at this point.
 * Device properties are typically created here as well, except
 * the ones we need to use for defining initialization parameters.
 * Such pre-initialization properties are created in the constructor.
 */
int CCameraAdapter::Initialize()
{
   CSingleLock oCS( m_pCS, __CS_ENABLE );
   if (m_bIsInitialized)
      return DEVICE_OK;

   // set property list
   // -----------------
   CPropertyAction *pAct;
   
   // Name
   RETURN_ON_MM_ERROR( CreateProperty(MM::g_Keyword_Name, m_sMyName, MM::String, true) );

   // Description
   RETURN_ON_MM_ERROR( CreateProperty(MM::g_Keyword_Description, "Piper Camera Adapter", MM::String, true) );

   // FrameGrabber
   pAct = new CPropertyAction (this, &CCameraAdapter::OnGrabber);
   RETURN_ON_MM_ERROR( CreateProperty( sc_pszPropFrameGrabber, "", MM::String, false, pAct ) );

   // CameraName
   pAct = new CPropertyAction (this, &CCameraAdapter::OnCamera);
   RETURN_ON_MM_ERROR( CreateProperty( sc_pszPropCameraName, "", MM::String, false, pAct ) );
		      
   // CameraID
   pAct = new CPropertyAction (this, &CCameraAdapter::OnCameraId);
   RETURN_ON_MM_ERROR( CreateProperty( sc_pszPropCameraID, "", MM::String, false, pAct ) );

   // CameraModes
   pAct = new CPropertyAction (this, &CCameraAdapter::OnMode);
   RETURN_ON_MM_ERROR( CreateProperty( sc_pszPropCameraMode, "", MM::String, false, pAct ) );
		      
   // binning
   RETURN_ON_MM_ERROR( CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, true/*, false, pAct*/) );
   RETURN_ON_MM_ERROR( AddAllowedValue(MM::g_Keyword_Binning, "1") );

   // pixel type
   pAct = new CPropertyAction (this, &CCameraAdapter::OnPixelType);
   RETURN_ON_MM_ERROR( CreateProperty(MM::g_Keyword_PixelType, "", MM::String, true, pAct) );

   // camera gain
   pAct = new CPropertyAction (this, &CCameraAdapter::OnVideoGain);
   RETURN_ON_MM_ERROR( CreateProperty( sc_pszPropVideoGain, "0", MM::Integer, false, pAct ) );

   // camera offset
   pAct = new CPropertyAction (this, &CCameraAdapter::OnVideoOffset);
   RETURN_ON_MM_ERROR( CreateProperty( sc_pszPropVideoOffset, "0", MM::Integer, false, pAct ) );

   // intensifier volts
   pAct = new CPropertyAction (this, &CCameraAdapter::OnIntensifierVolts);
   RETURN_ON_MM_ERROR( CreateProperty( sc_pszPropIntensVolts, "0.0", MM::Float, false, pAct ) );

   // intensifier gain
   pAct = new CPropertyAction (this, &CCameraAdapter::OnIntensifierGain);
   RETURN_ON_MM_ERROR( CreateProperty( sc_pszPropIntensGain, "0.0", MM::Float, false, pAct ) );

   // camera left offset
   pAct = new CPropertyAction (this, &CCameraAdapter::OnFrameLeft);
   RETURN_ON_MM_ERROR( CreateProperty( sc_pszPropFrameLeft, "0", MM::Integer, false, pAct ) );

   // camera top offset
   pAct = new CPropertyAction (this, &CCameraAdapter::OnFrameTop);
   RETURN_ON_MM_ERROR( CreateProperty( sc_pszPropFrameTop, "0", MM::Integer, false, pAct ) );

   // camera width
   pAct = new CPropertyAction (this, &CCameraAdapter::OnFrameWidth);
   RETURN_ON_MM_ERROR( CreateProperty( sc_pszPropFrameWidth, "0", MM::Integer, true, pAct ) );

   // camera height
   pAct = new CPropertyAction (this, &CCameraAdapter::OnFrameHeight);
   RETURN_ON_MM_ERROR( CreateProperty( sc_pszPropFrameHeight, "0", MM::Integer, true, pAct ) );

   // exposure
   pAct = new CPropertyAction (this, &CCameraAdapter::OnExposure);
   RETURN_ON_MM_ERROR( CreateProperty( sc_pszPropExposure, "0.0", MM::Float, false, pAct ) );

   // on-chip integration
   pAct = new CPropertyAction (this, &CCameraAdapter::OnIntegration);
   RETURN_ON_MM_ERROR( CreateProperty( sc_pszPropIntegration, "1", MM::Integer, false, pAct ) );

   // Set source histogram brightness
   pAct = new CPropertyAction( this, &CCameraAdapter::OnSourceBrightness );
   RETURN_ON_MM_ERROR( CreateProperty( sc_pszSourceBrightness, "0.0", MM::Float, false, pAct ) );

   // Set source histogram contrast
   pAct = new CPropertyAction( this, &CCameraAdapter::OnSourceContrast );
   RETURN_ON_MM_ERROR( CreateProperty( sc_pszSourceContrast, "1.0", MM::Float, false, pAct ) );

   // Set source histogram gamma
   pAct = new CPropertyAction( this, &CCameraAdapter::OnSourceGamma );
   RETURN_ON_MM_ERROR( CreateProperty( sc_pszSourceGamma, "1.0", MM::Float, true, pAct ) );

   // Set source histogram threshold
   pAct = new CPropertyAction( this, &CCameraAdapter::OnSourceThreshold );
   RETURN_ON_MM_ERROR( CreateProperty( sc_pszSourceThreshold, "0", MM::Integer, false, pAct ) );

   // Set source histogram auto contrast
   pAct = new CPropertyAction( this, &CCameraAdapter::OnSourceAutoContrast );
   RETURN_ON_MM_ERROR( CreateProperty( sc_pszSourceAutoContrast, "NO", MM::String, false, pAct ) );
   RETURN_ON_MM_ERROR( SetAllowedValues( sc_pszSourceAutoContrast, m_asBooleans ) );

   // Set source histogram normal contrast
   pAct = new CPropertyAction( this, &CCameraAdapter::OnSourceNormalContrast );
   RETURN_ON_MM_ERROR( CreateProperty( sc_pszSourceNormalContrast, "NO", MM::String, false, pAct ) );
   RETURN_ON_MM_ERROR( SetAllowedValues( sc_pszSourceNormalContrast, m_asBooleans ) );

   // Set Ion-feedback filter size
   pAct = new CPropertyAction( this, &CCameraAdapter::OnIonFeedbackFilterSize );
   RETURN_ON_MM_ERROR( CreateProperty( sc_pszIonFeedbackFilterSize, "8", MM::Integer, false, pAct ) );

   // Enable Ion-feedback filter
   pAct = new CPropertyAction( this, &CCameraAdapter::OnEnableIonFeedback );
   RETURN_ON_MM_ERROR( CreateProperty( sc_pszEnableIonFeedback, "NO", MM::String, false, pAct ) );
   RETURN_ON_MM_ERROR( SetAllowedValues( sc_pszEnableIonFeedback, m_asBooleans ) );

   // Set RAM Integration depth
   pAct = new CPropertyAction( this, &CCameraAdapter::OnRamStackDepth );
   RETURN_ON_MM_ERROR( CreateProperty( sc_pszRamStackDepth, "1", MM::Integer, false, pAct ) );

   // Set RAM Integration exposure time
   pAct = new CPropertyAction( this, &CCameraAdapter::OnRamStackExposure );
   RETURN_ON_MM_ERROR( CreateProperty( sc_pszRamStackExposure, "1.0", MM::Float, true, pAct ) );

   // Enable RAM Integration
   pAct = new CPropertyAction( this, &CCameraAdapter::OnEnableRamStack );
   RETURN_ON_MM_ERROR( CreateProperty( sc_pszEnableRamStack, "NO", MM::String, false, pAct ) );
   RETURN_ON_MM_ERROR( SetAllowedValues( sc_pszEnableRamStack, m_asBooleans ) );

   // Set RAM Averaging depth
   pAct = new CPropertyAction( this, &CCameraAdapter::OnRamAverageDepth );
   RETURN_ON_MM_ERROR( CreateProperty( sc_pszRamAverageDepth, "1", MM::Integer, false, pAct ) );

   // Set RAM Averaging exposure time
   pAct = new CPropertyAction( this, &CCameraAdapter::OnRamAverageExposure );
   RETURN_ON_MM_ERROR( CreateProperty( sc_pszRamAverageExposure, "1.0", MM::Float, true, pAct ) );

   // Enable smooth RAM averaging (running mean average)
   pAct = new CPropertyAction( this, &CCameraAdapter::OnEnableSmoothRamAverage );
   RETURN_ON_MM_ERROR( CreateProperty( sc_pszEnableSmoothRamAverage, "NO", MM::String, false, pAct ) );
   RETURN_ON_MM_ERROR( SetAllowedValues( sc_pszEnableSmoothRamAverage, m_asBooleans ) );

   // Enable fast RAM averaging (weighted average)
   pAct = new CPropertyAction( this, &CCameraAdapter::OnEnableFastRamAverage );
   RETURN_ON_MM_ERROR( CreateProperty( sc_pszEnableFastRamAverage, "NO", MM::String, false, pAct ) );
   RETURN_ON_MM_ERROR( SetAllowedValues( sc_pszEnableFastRamAverage, m_asBooleans ) );

   // readout time
   pAct = new CPropertyAction (this, &CCameraAdapter::OnReadoutTime);
   RETURN_ON_MM_ERROR( CreateProperty(MM::g_Keyword_ReadoutTime, "0", MM::Float, false, pAct) );

   // actual interval
   RETURN_ON_MM_ERROR( CreateProperty(MM::g_Keyword_ActualInterval_ms, "0.0", MM::Float, false) );

   // Version
   CHAR pszLib[100];
   INT16 nMajor;
   INT16 nMinor;
   INT16 nBuild;
   RETURN_ON_PIL_ERROR( pilGetLibraryId( (LPTSTR)pszLib, 100, nMajor, nMinor, nBuild ) );
   m_sVersion.Format( "v%d.%d.%02d", nMajor, nMinor, nBuild );
   RETURN_ON_MM_ERROR( CreateProperty(MM::g_Keyword_Version, m_sVersion, MM::String, true) );

   oCS.Unlock();

   // It is important to create the properties before failing due to multiple DLL
   // instances or multiple adapter instances. Otherwise MM will complain with
   // unnecessary messages.
   if( 0 < gs_nInstanceCount )
   {
      TRACE0("Not original PiperMmAdapter.DLL instance");
      return ERR_MULTIPLE_LIBRARY;
   }
   if( s_pOnlyCamera )
   {
      // The camera cannot be instantiated more than once
      TRACE0("Not original camera adapter");
      return ERR_MULTIPLE_CAMERA;
   }

   // This is the one and only allowed instance of the camera adapter.
   s_pOnlyCamera = this;

   // Connect to the Piper Interface Library (PIL)
   RETURN_ON_PIL_ERROR( ConnectToPiper() );
   m_bIsConnected = TRUE;

   if( 0 < m_asGrabbers.size() )
   {
      RETURN_ON_PIL_ERROR( ConnectPipe( -1 ) );
   }

   // initialize image buffer
   RETURN_ON_PIL_ERROR( GetProperties() );
   RETURN_ON_PIL_ERROR( ClearROI() );

   // initialize all properties
   // --------------------------
   if( -1 < m_nCurGrabber )
   {
      RETURN_ON_MM_ERROR( SetProperty( sc_pszPropFrameGrabber, m_asGrabbers[m_nCurGrabber].c_str() ) );
   }
   if( -1 < m_nCurCamera )
   {
      RETURN_ON_MM_ERROR( SetProperty( sc_pszPropCameraName, m_asCameras[m_nCurCamera].c_str() ) );
   }
   if( -1 < m_nCurCameraSn )
   {
      RETURN_ON_MM_ERROR( SetProperty( sc_pszPropCameraID, m_asCameraSns[m_nCurCameraSn].c_str() ) );
   }
   if( -1 < m_nCurCameraMode )
   {
      RETURN_ON_MM_ERROR( SetProperty( sc_pszPropCameraMode, m_asCameraModes[m_nCurCameraMode].c_str() ) );
   }
   RETURN_ON_MM_ERROR( SetProperty( MM::g_Keyword_PixelType, m_sPixel ) );
   RETURN_ON_MM_ERROR( SetProperty( sc_pszPropVideoGain, CDeviceUtils::ConvertToString(m_nVideoGain) ) );
   RETURN_ON_MM_ERROR( SetProperty( sc_pszPropVideoOffset, CDeviceUtils::ConvertToString(m_nVideoOffset) ) );
   RETURN_ON_MM_ERROR( SetProperty( sc_pszPropIntensVolts, CDeviceUtils::ConvertToString(m_fIntensifierVolts) ) );
   RETURN_ON_MM_ERROR( SetProperty( sc_pszPropIntensGain, CDeviceUtils::ConvertToString(m_fIntensifierGain) ) );
   RETURN_ON_MM_ERROR( SetProperty( sc_pszPropFrameLeft, CDeviceUtils::ConvertToString(m_nCapLeft) ) );
   RETURN_ON_MM_ERROR( SetProperty( sc_pszPropFrameTop, CDeviceUtils::ConvertToString(m_nCapTop) ) );
   RETURN_ON_MM_ERROR( SetProperty( sc_pszPropExposure, CDeviceUtils::ConvertToString(m_fExposure*1000.0) ) );
   RETURN_ON_MM_ERROR( SetProperty( sc_pszPropIntegration, CDeviceUtils::ConvertToString(m_nFrameClocks) ) );

   // initialize image buffer
   RETURN_ON_PIL_ERROR( ClearROI() );

   // synchronize all properties
   // --------------------------
   //RETURN_ON_MM_ERROR( UpdateStatus() );

   m_bIsInitialized = true;

   return DEVICE_OK;
}

/**
 * Shuts down (unloads) the device.
 * Required by the MM::Device API.
 * Ideally this method will completely unload the device and release all resources.
 * Shutdown() may be called multiple times in a row.
 * After Shutdown() we should be allowed to call Initialize() again to load the device
 * without causing problems.
 */
int CCameraAdapter::Shutdown()
{
   if( this == s_pOnlyCamera )
   {
      // This is the one and only allowed instance. We need to set the
      // pointer to NULL so another instance will be allowed.
      s_pOnlyCamera = NULL;
   }
   m_bIsInitialized = false;
   if( m_hCaptureThread )
   {
      CSingleLock oCS( m_pCS, __CS_ENABLE );
      m_bStream = FALSE;
      oCS.Unlock();
      WaitForSingleObject( m_hCaptureEnded, INFINITE );
      m_hCaptureThread = NULL;
   }
   if( m_punRoi )
   {
      delete [] m_punRoi;
      m_punRoi = NULL;
   }
   if( -1 < m_nCurGrabber )
   {
      pilReleasePipe( m_nCurGrabber );
      m_nCurGrabber = -1;
   }
   return DEVICE_OK;
}

void CCameraAdapter::OnGrabberChanged( LPCTSTR pszGrabber )
{
   if( !m_bIsInitialized || m_bChangingGrabber || (-1 == m_nCurGrabber) )
   {
      return;
   }

   CSingleLock oCS( m_pCS, __CS_ENABLE );
   std::string sGrabber( pszGrabber );
   for( INT16 nPipe=0; nPipe<(INT16)m_asGrabbers.size(); nPipe++ )
   {
      if( m_asGrabbers[nPipe] == sGrabber )
      {
         if( m_nCurGrabber != nPipe )
         {
            oCS.Unlock();
            ConnectPipe( nPipe );
            UpdateGUI();
         }
         break;
      }
   }
}

void CCameraAdapter::OnCameraChanged( LPCTSTR pszCamera, int /*nId*/ )
{
   if( !m_bIsInitialized || m_bChangingCamera )
   {
      return;
   }

   CSingleLock oCS( m_pCS, __CS_ENABLE );
   std::string sCamera( pszCamera );
   for( INT16 nCam=0; nCam<(INT16)m_asCameras.size(); nCam++ )
   {
      if( m_asCameras[nCam] == sCamera )
      {
         if( nCam != m_nCurCamera )
         {
            oCS.Unlock();
            m_nCurCamera = nCam;
            GetCameraChoices();
            GetProperties();
            ClearROI();
            UpdateGUI();
         }
         break;
      }
   }
}

void CCameraAdapter::OnCameraIdChanged( LPCTSTR pszSn )
{
   if( !m_bIsInitialized || m_bChangingCameraSn )
   {
      return;
   }

   CSingleLock oCS( m_pCS, __CS_ENABLE );
   std::string sSn( pszSn );
   for( INT16 nSn=0; nSn<(INT16)m_asCameraSns.size(); nSn++ )
   {
      if( m_asCameraSns[nSn] == sSn )
      {
         oCS.Unlock();
         m_nCurCameraSn = nSn;
         GetProperties();
         ClearROI();
         UpdateGUI();
         break;
      }
   }
}

void CCameraAdapter::OnCameraModeChanged( LPCTSTR pszMode )
{
   if( !m_bIsInitialized || m_bChangingCameraMode )
   {
      return;
   }

   CSingleLock oCS( m_pCS, __CS_ENABLE );
   std::string sMode( pszMode );
   for( INT16 nMode=0; nMode<(INT16)m_asCameraModes.size(); nMode++ )
   {
      if( m_asCameraModes[nMode] == sMode )
      {
         oCS.Unlock();
         m_nCurCameraMode = nMode;
         GetProperties();
         ClearROI();
         UpdateGUI();
         break;
      }
   }
}

void CCameraAdapter::OnCameraPropertiesChanged()
{
   if( !m_bIsInitialized || m_bChangingGrabber || m_bChangingCamera || m_bChangingCameraSn || m_bChangingCameraMode )
   {
      return;
   }
   GetProperties();
   UpdateGUI();
}

void CCameraAdapter::OnIonFeedbackSizeChanged( int nPixels )
{
   if( m_nIonFeedbackFilterSize == (INT16)nPixels )
   {
      return;
   }
   m_nIonFeedbackFilterSize = (INT16)nPixels;
   UpdateGUI();
}

void CCameraAdapter::OnIonFeedbackEnabled( BOOL bEnabled )
{
   if( m_bIonFeedbackEnabled == bEnabled )
   {
      return;
   }
   m_bIonFeedbackEnabled = bEnabled;
   UpdateGUI();
}

void CCameraAdapter::OnHistogramTransferChanged( double fBrightness, double fContrast, double fGamma )
{
   if( (m_fSourceBrightness == fBrightness) && (m_fSourceContrast == fContrast) && (m_fSourceGamma == fGamma) )
   {
      return;
   }
   m_fSourceBrightness = fBrightness;
   //m_fSourceContrast = fContrast;
   m_fSourceGamma = fGamma;
   UpdateGUI();
}

void CCameraAdapter::OnHistogramAutoContrast( BOOL bOn )
{
   if( m_bSourceAutoContrast == bOn )
   {
      return;
   }
   m_bSourceAutoContrast = bOn;
   UpdateGUI();
}

void CCameraAdapter::OnDiscriminatorMaxValue( double fMax )
{
   if( m_fDiscriminatorMax == fMax )
   {
      return;
   }
   m_fDiscriminatorMax = fMax;
}

void CCameraAdapter::OnDiscriminatorNewValues
(
   double fAverage,
   double fStdDev,
   double fMeasured,
   double fSigma,
   BOOL bDiscarded
)
{
   if( (m_fDiscriminatorAverage == fAverage) && (m_fDiscriminatorStdDev == fStdDev) && (m_fDiscriminatorMeasured == fMeasured) && (m_fDiscriminatorSigma == fSigma) && (m_bDiscriminatorDiscarded == bDiscarded) )
   {
      return;
   }
   m_fDiscriminatorAverage = fAverage;
   m_fDiscriminatorStdDev = fStdDev;
   m_fDiscriminatorMeasured = fMeasured;
   m_fDiscriminatorSigma = fSigma;
   m_bDiscriminatorDiscarded = bDiscarded;
   UpdateGUI();
}

void CCameraAdapter::OnDiscriminatorDiscardCount( int nNumDiscarded, int nNumTotal )
{
   if( (m_nDiscriminatorNumDiscarded == nNumDiscarded) && (m_nDiscriminatorNumTotal == nNumTotal) )
   {
      return;
   }
   m_nDiscriminatorNumDiscarded = nNumDiscarded;
   m_nDiscriminatorNumTotal = nNumTotal;
   UpdateGUI();
}

void CCameraAdapter::OnDiscriminatorEnabled( BOOL bEnabled )
{
   if( m_bDiscriminatorEnabled == bEnabled )
   {
      return;
   }
   m_bDiscriminatorEnabled = bEnabled;
   UpdateGUI();
}

void CCameraAdapter::OnIntegratorDepthChanged( INT32 nSize )
{
   //CSingleLock oCS( m_pCS, __CS_ENABLE );
   if( m_nIntegratorDepth == nSize )
   {
      return;
   }
   m_nIntegratorDepth = nSize;
   UpdateGUI();
}

void CCameraAdapter::OnIntegratorCurrentDepth( INT32 nDepth )
{
   if( m_nIntegratorCurrentDepth == nDepth )
   {
      return;
   }
   m_nIntegratorCurrentDepth = nDepth;
   //UpdateGUI();
}

void CCameraAdapter::OnIntegratorRateChanged( double fRate )
{
   if( m_fIntegratorRate == fRate )
   {
      return;
   }
   m_fIntegratorRate = fRate;
   m_fIntegratorExposure = 1.0 / fRate;
   UpdateGUI();
}

void CCameraAdapter::OnIntegratorEnabled( BOOL bEnabled )
{
   if( m_bIntegratorEnabled == bEnabled )
   {
      return;
   }
   m_bIntegratorEnabled = bEnabled;
   UpdateGUI();
}

void CCameraAdapter::OnAveragingDepthChanged( INT32 nSize )
{
   if( m_nAveragingDepth == nSize )
   {
      return;
   }
   m_nAveragingDepth = nSize;
   UpdateGUI();
}

void CCameraAdapter::OnAveragingCurrentDepth( INT32 nDepth )
{
   if( m_nAveragingCurrentDepth == nDepth )
   {
      return;
   }
   m_nAveragingCurrentDepth = nDepth;
   //UpdateGUI();
}

void CCameraAdapter::OnAveragingRateChanged( double fRate )
{
   if( m_fAveragingRate == fRate )
   {
      return;
   }
   m_fAveragingRate = fRate;
   UpdateGUI();
}

void CCameraAdapter::OnSmoothAveragingEnabled( BOOL bEnabled )
{
   if( m_bSmoothAvgEnabled == bEnabled )
   {
      return;
   }
   m_bSmoothAvgEnabled = bEnabled;
   UpdateGUI();
}

void CCameraAdapter::OnFastAveragingEnabled( BOOL bEnabled )
{
   if( m_bFastAvgEnabled == bEnabled )
   {
      return;
   }
   m_bFastAvgEnabled = bEnabled;
   UpdateGUI();
}

INT16 CCameraAdapter::ConnectToPiper()
{
   m_asGrabbers.clear();
   m_asCameras.clear();
   m_asCameraSns.clear();
   m_asCameraModes.clear();
   m_nCurGrabber = -1;
   m_nCurCamera = -1;
   m_nCurCameraSn = -1;
   m_nCurCameraMode = -1;

   RETURN_ON_FAILURE( pilConnectLib( "AgileAutomation, Inc.", LibCallback, this ) );

   INT16 nPipeCnt;
   RETURN_ON_FAILURE( pilGetNumPipes( nPipeCnt ) );
   for( INT16 nPipe=0; nPipe<nPipeCnt; nPipe++ )
   {
      CHAR pszGrabber[100];
      RETURN_ON_FAILURE( pilGetGrabberId( nPipe, pszGrabber, 100 ) );
      m_asGrabbers.push_back( pszGrabber );
   }
   SetAllowedValues( sc_pszPropFrameGrabber, m_asGrabbers );

   return PIL_SUCCESS;
}

INT16 CCameraAdapter::ConnectPipe( INT16 nEnum )
{
   if( (-1 < nEnum) && (nEnum == m_nCurGrabber) )
   {
      return PIL_SUCCESS;
   }

   if( -1 < m_nCurGrabber )
   {
      pilReleasePipe( m_nCurGrabber );
   }
   m_asCameras.clear();
   m_asCameraSns.clear();
   m_asCameraModes.clear();
   m_nCurCamera = -1;
   m_nCurCameraSn = -1;
   m_nCurCameraMode = -1;

   m_bChangingGrabber = TRUE;
   RETURN_ON_FAILURE( pilConnectPipe( nEnum, PipeCallback, this ) );
   m_nCurGrabber = nEnum;

   UINT32 unUniqueId;
   CHAR pszCamera[100];
   pszCamera[0] = 0;
   RETURN_ON_FAILURE( pilGetCameraId( m_nCurGrabber, unUniqueId, pszCamera, 100 ) );

   INT16 nCamCnt;
   RETURN_ON_FAILURE( pilGetNumKnownCameras( m_nCurGrabber, nCamCnt ) );
   for( INT16 nCam=0; nCam<nCamCnt; nCam++ )
   {
      UINT32 unUniqueId;
      CHAR pszCam[100];
      pszCam[0] = 0;
      RETURN_ON_FAILURE( pilGetKnownCameraId( m_nCurGrabber, nCam, unUniqueId, pszCam, 100 ) );
      m_asCameras.push_back( pszCam );
      if( 0 == strcmp( pszCamera, pszCam ) )
      {
         m_nCurCamera = nCam;
      }
      else if( (0 == nCam) && (0 == pszCamera[0]) )
      {
         UINT32 unUniqueId;

         // Need to select twice if no default
         RETURN_ON_FAILURE( pilSetCameraId( m_nCurGrabber, pszCam, unUniqueId ) );
         RETURN_ON_FAILURE( pilSetCameraId( m_nCurGrabber, pszCam, unUniqueId ) );
         RETURN_ON_FAILURE( pilGetCameraId( m_nCurGrabber, unUniqueId, pszCamera, 100 ) );
         m_nCurCamera = 0;
      }
   }
   SetAllowedValues( sc_pszPropCameraName, m_asCameras );

   if( -1 < m_nCurCamera )
   {
      RETURN_ON_FAILURE( SelectCamera( m_nCurCamera ) );
      if( m_bShowControlPanel )
      {
         RETURN_ON_FAILURE( pilShowControlPanel( m_nCurGrabber, TRUE ) );
      }
   }

   RETURN_ON_FAILURE( GetProperties() );
   RETURN_ON_FAILURE( ClearROI() );

   m_bChangingGrabber = FALSE;
   return PIL_SUCCESS;
}

INT16 CCameraAdapter::SelectCamera( INT16 nEnum )
{
   m_bChangingCamera = TRUE;
   m_bChangingCameraSn = TRUE;
   m_bChangingCameraMode = TRUE;
   UINT32 nUniqueId;
   RETURN_ON_FAILURE( pilSetCameraId( m_nCurGrabber, m_asCameras[nEnum].c_str(), nUniqueId ) );
   m_nCurCamera = nEnum;

   RETURN_ON_FAILURE( GetCameraChoices() );

   if( -1 < m_nCurCameraSn )
   {
      RETURN_ON_FAILURE( SelectCameraSn( m_nCurCameraSn ) );
   }
   if( -1 < m_nCurCameraMode )
   {
      RETURN_ON_FAILURE( SelectCameraMode( m_nCurCameraMode ) );
   }

   if( !m_bChangingGrabber )
   {
      RETURN_ON_FAILURE( GetProperties() );
      RETURN_ON_FAILURE( ClearROI() );
   }
   m_bChangingCamera = FALSE;
   m_bChangingCameraSn = FALSE;
   m_bChangingCameraMode = FALSE;

   return PIL_SUCCESS;
}

INT16 CCameraAdapter::GetCameraChoices()
{
   m_asCameraSns.clear();
   m_asCameraModes.clear();
   m_nCurCameraSn = -1;
   m_nCurCameraMode = -1;

   CHAR pszSerial[100];
   RETURN_ON_FAILURE( pilGetCameraSn( m_nCurGrabber, pszSerial, 100 ) );

   INT16 nSnCnt;
   RETURN_ON_FAILURE( pilGetNumCameraSns( m_nCurGrabber, m_nCurCamera, nSnCnt ) );
   for( INT16 nSn=0; nSn<nSnCnt; nSn++ )
   {
      CHAR pszSn[100];
      RETURN_ON_FAILURE( pilGetCameraSnId( m_nCurGrabber, m_nCurCamera, nSn, pszSn, 100 ) );
      m_asCameraSns.push_back( pszSn );
      if( 0 == strcmp( pszSerial, pszSn ) )
      {
         m_nCurCameraSn = nSn;
      }
   }
   SetAllowedValues( sc_pszPropCameraID, m_asCameraSns );

   CHAR pszCurMode[100];
   RETURN_ON_FAILURE( pilGetCameraMode( m_nCurGrabber, pszCurMode, 100 ) );

   INT16 nModeCnt;
   RETURN_ON_FAILURE( pilGetNumCameraModes( m_nCurGrabber, m_nCurCamera, nModeCnt ) );
   for( INT16 nMode=0; nMode<nModeCnt; nMode++ )
   {
      CHAR pszMode[100];
      pszMode[0] = 0;
      RETURN_ON_FAILURE( pilGetCameraModeId( m_nCurGrabber, m_nCurCamera, nMode, pszMode, 100 ) );
      m_asCameraModes.push_back( pszMode );
      if( 0 == strcmp( pszCurMode, pszMode ) )
      {
         m_nCurCameraMode = nMode;
      }
   }
   SetAllowedValues( sc_pszPropCameraMode, m_asCameraModes );

   return PIL_SUCCESS;
}

INT16 CCameraAdapter::SelectCameraSn( INT16 nEnum )
{
   m_bChangingCameraSn = TRUE;
   RETURN_ON_FAILURE( pilSetCameraSn( m_nCurGrabber, m_asCameraSns[nEnum].c_str() ) );
   m_nCurCameraSn = nEnum;

   if( !m_bChangingCamera )
   {
      RETURN_ON_FAILURE( GetProperties() );
      RETURN_ON_FAILURE( ClearROI() );
   }
   m_bChangingCameraSn = m_bChangingCamera;
   return PIL_SUCCESS;
}

INT16 CCameraAdapter::SelectCameraMode( INT16 nEnum )
{
   m_bChangingCameraMode = TRUE;
   RETURN_ON_FAILURE( pilSetCameraMode( m_nCurGrabber, m_asCameraModes[nEnum].c_str() ) );
   m_nCurCameraMode = nEnum;

   m_nFrameClocks = 1;
   m_nMMFrameClocks = 1;
   m_bExposureInSync = TRUE;
   RETURN_ON_FAILURE( SetIntegration( m_nFrameClocks ) );

   if( !m_bChangingCamera )
   {
      RETURN_ON_FAILURE( GetProperties() );
      RETURN_ON_FAILURE( ClearROI() );
   }
   m_bChangingCameraMode = m_bChangingCamera;
   return PIL_SUCCESS;
}

INT16 CCameraAdapter::SetIntegration( INT16 nFrames )
{
   CSingleLock oCS( m_pCS, __CS_ENABLE );
   if( !m_bExposureInSync )
   {
      return PIL_SUCCESS;
   }

   oCS.Unlock();
   RETURN_ON_FAILURE( pilSetCameraIntegration( m_nCurGrabber, nFrames, m_fExposure ) );
   oCS.Lock();

   m_nFrameClocks = nFrames;
   m_nMMFrameClocks = m_nFrameClocks;
   m_bExposureInSync = TRUE;

   if( m_bIsInitialized && !m_bChangingCameraMode )
   {
      oCS.Unlock();
      RETURN_ON_FAILURE( GetProperties() );
      UpdateGUI();
      oCS.Lock();
   }

   m_unTimeout = (UINT)(m_fExposure * 20000.0);
   return PIL_SUCCESS;
}

INT16 CCameraAdapter::GetProperties()
{
   RETURN_ON_FAILURE
      (
         pilGetCaptureProps
            (
               m_nCurGrabber,
               m_nCapBits,
               m_fCapFrameRate,
               m_nCapLeft,
               m_nCapWidth,
               m_nCapTop,
               m_nCapHeight,
               m_nCapXBin,
               m_nCapYBin
            )
      );
   RETURN_ON_FAILURE
      (
         pilGetImageProps
            (
               m_nCurGrabber,
               m_nImgLeft,
               m_nImgWidth,
               m_nImgTop,
               m_nImgHeight,
               m_nImgStride,
               m_nImgPixelBytes,
               m_unImgSaturation,
               m_fImgFrameRate
            )
      );

   RETURN_ON_FAILURE( pilGetCameraIntegration( m_nCurGrabber, m_nFrameClocks, m_fExposure ) );
   RETURN_ON_FAILURE( pilGetCameraVideoGain( m_nCurGrabber, m_nVideoGain ) );
   RETURN_ON_FAILURE( pilGetCameraVideoOffset( m_nCurGrabber, m_nVideoOffset ) );
   RETURN_ON_FAILURE( pilGetIntensifierValues( m_nCurGrabber, m_fIntensifierVolts, m_fIntensifierGain ) );

   m_bExposureInSync = ( m_nFrameClocks == m_nMMFrameClocks );

   m_sPixel.Format( "%dbit", m_nCapBits );
   m_asPixelTypes.clear();
   m_asPixelTypes.push_back( (LPCTSTR)m_sPixel );
   SetAllowedValues( MM::g_Keyword_PixelType, m_asPixelTypes );
   SetProperty( MM::g_Keyword_PixelType, m_sPixel );

   m_nImgImageBytes = m_nImgHeight * m_nImgStride;

   CSingleLock oCS( m_pCS, __CS_ENABLE );
   m_unTimeout = (UINT)(m_fExposure * 20000.0);
   return PIL_SUCCESS;
}

/**
 * Performs exposure and grabs a single image.
 * Required by the MM::Camera API.
 */
int CCameraAdapter::SnapImage()
{
   CSingleLock oCS( m_pCS, __CS_ENABLE );

   RETURN_ON_MM_ERROR( ResizeImageBuffer() );

   INT16 nRoiWidth = m_nRoiWidth;
   INT16 nRoiHeight = m_nRoiHeight;
   INT16 nImgPixelBytes = m_nImgPixelBytes;

   oCS.Unlock();
   RETURN_ON_PIL_ERROR( pilSnapImage( m_nCurGrabber, m_punRoi, m_unTimeout ) );
   oCS.Lock();

   int nRet = DEVICE_OK;
//imageprocessor now called from core
   return nRet;
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
const unsigned char* CCameraAdapter::GetImageBuffer()
{
   pilUpdateCamera( m_nCurGrabber );
   pilWaitForCameraUpdate( m_nCurGrabber );
   return m_punRoi;
}

int CCameraAdapter::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{
   if( m_bStream )
   {
      return ERR_BUSY_ACQUIRING;
   }
   m_bStopOnOverflow = (BOOL)stopOnOverflow;

   double fFullExposure = m_fExposure * (double)m_nIntegratorDepth;
   double fInterval = interval_ms / 1000.0;
   m_nIntervalCount = __max( 1, (INT16)ceil( fInterval / fFullExposure ) );
   fInterval = (double)m_nIntervalCount * fFullExposure;
   interval_ms = fInterval * 1000.0;

   SetProperty(MM::g_Keyword_ActualInterval_ms, CDeviceUtils::ConvertToString(interval_ms));

   m_bStream = TRUE;
   m_nStreamImages = numImages;
   ResetEvent( m_hCaptureEnded );
   CWinThread *pThread = AfxBeginThread( StreamThread, this );
   m_hCaptureThread = pThread->m_hThread;
   return DEVICE_OK;
}

int CCameraAdapter::StopSequenceAcquisition()
{
   if( m_bStream && m_hCaptureThread )
   {
      CSingleLock oCS( m_pCS, __CS_ENABLE );
      m_bStream = FALSE;
      oCS.Unlock();
      WaitForSingleObject( m_hCaptureEnded, INFINITE );
      m_hCaptureThread = NULL;
      RETURN_ON_PIL_ERROR( pilStopStreamingCapture( m_nCurGrabber ) );
   }
   return DEVICE_OK;
}

void CCameraAdapter::Capture()
{
   int ret = GetCoreCallback()->PrepareForAcq(this);
   if (ret != DEVICE_OK)
   {
      m_bStream = FALSE;
      m_hCaptureThread = NULL;
      SetEvent( m_hCaptureEnded );
      return;
   }

   UINT unMaxCnt = m_nStreamImages ? m_nStreamImages : INFINITE;
   pilStartStreamingCapture( m_nCurGrabber, m_nStreamImages * m_nIntervalCount );
   PUCHAR punBuffer = new UCHAR[m_nRoiImageBytes];
   m_nStreamCount = 0;
   int nCount = 0;
   int nSkip = m_nIntervalCount;
   double fStart = 0.0;
   double fTotalTime = 0.0;
   double fChange = 0.7 / m_fImgFrameRate;
   m_fStreamAvgTime = 0.0;
   fStart = piulGetSeconds();
   while( true )
   {
      ZeroMemory( punBuffer, m_nRoiImageBytes );
      if( PIL_SUCCESS != pilWaitForNextFrame( m_nCurGrabber, punBuffer, m_unTimeout ) )
      {
         break;
      }
      nSkip--;
      if( nSkip )
      {
         continue;
      }
      else
      {
         nSkip = m_nIntervalCount;
      }

      CSingleLock oCS( m_pCS, __CS_ENABLE );
      double fEnd = piulGetSeconds();
      double fTime = fEnd - fStart - fTotalTime;
      if( !nCount )
      {
         nCount = 1;
         fTotalTime = fTime;
         m_fStreamAvgTime = fTime;
      }
      else if( fChange < fabs( fTime - m_fStreamAvgTime ) )
      {
         nCount = 0;
         fStart = fEnd;
         fTotalTime = 0.0;
         m_fStreamAvgTime = fTime;
      }
      else
      {
         nCount++;
         fTotalTime = fEnd - fStart;
         m_fStreamAvgTime = fTotalTime / (double)nCount;
      }
      if( !m_nStreamCount )
      {
         fStart = fEnd;
         fTotalTime = 0.0;
      }
      m_nStreamCount++;
      oCS.Unlock();

      // process image
      SetProperty(MM::g_Keyword_ActualInterval_ms, CDeviceUtils::ConvertToString(m_fStreamAvgTime * 1000.0));

      //imageprocessor now called from core

      // insert image into the circular buffer
      int ret = GetCoreCallback()->InsertImage
         (
            this,
            punBuffer,
            m_nRoiWidth,
            m_nRoiHeight,
            m_nImgPixelBytes
         );
      if (ret != DEVICE_OK)
      {
         // Micro-Manager can't keep up
         if (!m_bStopOnOverflow && ret == DEVICE_BUFFER_OVERFLOW)
         {
            // do not stop on overflow - just reset the buffer
           GetCoreCallback()->ClearImageBuffer(this);
           ret = GetCoreCallback()->InsertImage
                  (
                     this,
                     punBuffer,
                     m_nRoiWidth,
                     m_nRoiHeight,
                     m_nImgPixelBytes
                     );
            if (ret != DEVICE_OK)
            {
               m_bStream = FALSE;
            }
         }
      }
      if( !m_bStream || (m_nStreamCount == (long)unMaxCnt) )
      {
         pilStopStreamingCapture( m_nCurGrabber );
         break;
      }
   }
   delete [] punBuffer;

   CSingleLock oCS( m_pCS, __CS_ENABLE );
   m_bStream = FALSE;
   m_hCaptureThread = NULL;
   SetEvent( m_hCaptureEnded );
   oCS.Unlock();

   GetCoreCallback()->AcqFinished(this, 0);
   LogMessage(g_Msg_SEQUENCE_ACQUISITION_THREAD_EXITING);
}

/**
 * Returns image buffer X-size in pixels.
 * Required by the MM::Camera API.
 */
unsigned CCameraAdapter::GetImageWidth() const
{
   CSingleLock oCS( m_pCS, __CS_ENABLE );
   return m_nRoiWidth;
}

/**
 * Returns image buffer Y-size in pixels.
 * Required by the MM::Camera API.
 */
unsigned CCameraAdapter::GetImageHeight() const
{
   CSingleLock oCS( m_pCS, __CS_ENABLE );
   return m_nRoiHeight;
}

/**
 * Returns image buffer pixel depth in bytes.
 * Required by the MM::Camera API.
 */
unsigned CCameraAdapter::GetImageBytesPerPixel() const
{
   CSingleLock oCS( m_pCS, __CS_ENABLE );
   return m_nImgPixelBytes;
} 

/**
 * Returns the bit depth (dynamic range) of the pixel.
 * This does not affect the buffer size, it just gives the client application
 * a guideline on how to interpret pixel values.
 * Required by the MM::Camera API.
 */
unsigned CCameraAdapter::GetBitDepth() const
{
   CSingleLock oCS( m_pCS, __CS_ENABLE );
   return m_nCapBits;
}

/**
 * Returns the size in bytes of the image buffer.
 * Required by the MM::Camera API.
 */
long CCameraAdapter::GetImageBufferSize() const
{
   CSingleLock oCS( m_pCS, __CS_ENABLE );
   return m_nRoiImageBytes;
}

/**
 * Sets the camera Region Of Interest.
 * Required by the MM::Camera API.
 * This command will change the dimensions of the image.
 * Depending on the hardware capabilities the camera may not be able to configure the
 * exact dimensions requested - but should try to as close as possible.
 * If the hardware does not have this capability the software should simulate the ROI by
 * appropriately cropping each frame.
 */
int CCameraAdapter::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
   m_nRoiLeft = (INT16)x;
   m_nRoiTop = (INT16)y;
   m_nRoiWidth = (INT16)xSize;
   m_nRoiHeight = (INT16)ySize;
   RETURN_ON_PIL_ERROR( pilSetImageRoi
      (
         m_nCurGrabber,
         m_nRoiLeft,
         m_nRoiWidth,
         m_nRoiTop,
         m_nRoiHeight,
         m_nRoiStride
      ) );
   m_nRoiImageBytes = m_nRoiHeight * m_nRoiStride;

   return DEVICE_OK;
}

/**
 * Returns the actual dimensions of the current ROI.
 * Required by the MM::Camera API.
 */
int CCameraAdapter::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
   CSingleLock oCS( m_pCS, __CS_ENABLE );
   x = m_nRoiLeft;
   y = m_nRoiTop;

   xSize = m_nRoiWidth;
   ySize = m_nRoiHeight;

   return DEVICE_OK;
}

/**
 * Resets the Region of Interest to full frame.
 * Required by the MM::Camera API.
 */
int CCameraAdapter::ClearROI()
{
   m_nRoiLeft = 0;
   m_nRoiTop = 0;
   m_nRoiWidth = m_nImgWidth;
   m_nRoiHeight = m_nImgHeight;
   m_nRoiStride = m_nImgStride;
   RETURN_ON_PIL_ERROR( pilSetImageRoi
      (
         m_nCurGrabber,
         m_nRoiLeft,
         m_nRoiWidth,
         m_nRoiTop,
         m_nRoiHeight,
         m_nRoiStride
      ) );
   m_nRoiImageBytes = m_nRoiHeight * m_nRoiStride;
   return DEVICE_OK;
}

/**
 * Returns the current exposure setting in milliseconds.
 * Required by the MM::Camera API.
 */
double CCameraAdapter::GetExposure() const
{
   CSingleLock oCS( m_pCS, __CS_ENABLE );
   CCameraAdapter *pThis = const_cast<CCameraAdapter*>(this);
   pThis->m_nMMFrameClocks = m_nFrameClocks;
   pThis->m_bExposureInSync = TRUE;
   return floor( 1000000.0 * m_fExposure ) / 1000.0;
}

/**
 * Sets exposure in milliseconds.
 * Required by the MM::Camera API.
 */
void CCameraAdapter::SetExposure(double exp)
{
   CSingleLock oCS( m_pCS, __CS_ENABLE );
   if( !m_bExposureInSync )
   {
      return;
   }
   m_fExposure = exp / 1000.0;

   oCS.Unlock();
   pilSetCameraExposure( m_nCurGrabber, m_fExposure, m_nFrameClocks );
   oCS.Lock();

   exp = m_fExposure * 1000.0;
   m_nMMFrameClocks = m_nFrameClocks;
   m_bExposureInSync = TRUE;

   m_unTimeout = (UINT)(exp) * 20;
}

/**
 * Returns the current binning factor.
 * Required by the MM::Camera API.
 */
int CCameraAdapter::GetBinning() const
{
   //CSingleLock oCS( m_pCS, __CS_ENABLE );
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
int CCameraAdapter::SetBinning(int binFactor)
{
   //CSingleLock oCS( m_pCS, __CS_ENABLE );
   return SetProperty(MM::g_Keyword_Binning, CDeviceUtils::ConvertToString(binFactor));
}
///////////////////////////////////////////////////////////////////////////////
// CCameraAdapter Action handlers
///////////////////////////////////////////////////////////////////////////////

/**
 * Handles "ShowControlPanel" property.
 */
int CCameraAdapter::OnShowControlPanel( MM::PropertyBase* pProp, MM::ActionType eAct )
{
   if( MM::AfterSet == eAct )
   {
      // the user just set the new value for the property, so we have to
      // apply this value to the 'hardware'.
      std::string sShow;
      pProp->Get( sShow );
      m_bShowControlPanel = (m_asBooleans[1] == sShow) ? TRUE : FALSE;
      if( m_bIsConnected )
      {
         RETURN_ON_PIL_ERROR( pilShowControlPanel( m_nCurGrabber, m_bShowControlPanel ) );
      }
   }
   else if( MM::BeforeGet == eAct )
   {
      // the user is requesting the current value for the property, so
      // either ask the 'hardware' or let the system return the value
      // cached in the property.
      pProp->Set( m_asBooleans[m_bShowControlPanel ? 1 : 0].c_str() );
   }

   return DEVICE_OK; 
}

/**
 * Handles "Grabber" property.
 */
int CCameraAdapter::OnGrabber(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if( MM::AfterSet == eAct )
   {
      // the user just set the new value for the property, so we have to
      // apply this value to the 'hardware'.
      std::string sGrabber;
      pProp->Get( sGrabber );
      for( INT16 nPipe=0; nPipe<(INT16)m_asGrabbers.size(); nPipe++ )
      {
         if( m_asGrabbers[nPipe] == sGrabber )
         {
            if( m_nCurGrabber != nPipe )
            {
               RETURN_ON_PIL_ERROR( ConnectPipe( nPipe ) );
            }
            return DEVICE_OK;
         }
      }
      return ERR_INVALID_GRABBER;
   }
   else if( MM::BeforeGet == eAct )
   {
      // the user is requesting the current value for the property, so
      // either ask the 'hardware' or let the system return the value
      // cached in the property.
      if( -1 < m_nCurGrabber )
      {
         pProp->Set( m_asGrabbers[m_nCurGrabber].c_str() );
      }
      else
      {
         pProp->Set( "" );
      }
   }

   return DEVICE_OK; 
}

/**
 * Handles "Camera" property.
 */
int CCameraAdapter::OnCamera(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if( MM::AfterSet == eAct )
   {
      // the user just set the new value for the property, so we have to
      // apply this value to the 'hardware'.
      std::string sCamera;
      pProp->Get( sCamera );
      for( INT16 nCam=0; nCam<(INT16)m_asCameras.size(); nCam++ )
      {
         if( m_asCameras[nCam] == sCamera )
         {
            RETURN_ON_PIL_ERROR( SelectCamera( nCam ) );
            return DEVICE_OK;
         }
      }
      return ERR_INVALID_CAMERA;
   }
   else if( MM::BeforeGet == eAct )
   {
      // the user is requesting the current value for the property, so
      // either ask the 'hardware' or let the system return the value
      // cached in the property.
      if( -1 < m_nCurCamera )
      {
         pProp->Set( m_asCameras[m_nCurCamera].c_str() );
      }
      else
      {
         pProp->Set( "" );
      }
   }

   return DEVICE_OK; 
}

/**
 * Handles "CameraID" property.
 */
int CCameraAdapter::OnCameraId(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if( MM::AfterSet == eAct )
   {
      // the user just set the new value for the property, so we have to
      // apply this value to the 'hardware'.
      std::string sSn;
      pProp->Get( sSn );
      for( INT16 nSn=0; nSn<(INT16)m_asCameraSns.size(); nSn++ )
      {
         if( m_asCameraSns[nSn] == sSn )
         {
            RETURN_ON_PIL_ERROR( SelectCameraSn( nSn ) );
            return DEVICE_OK;
         }
      }
      return ERR_INVALID_CAMERA_ID;
   }
   else if( MM::BeforeGet == eAct )
   {
      // the user is requesting the current value for the property, so
      // either ask the 'hardware' or let the system return the value
      // cached in the property.
      if( -1 < m_nCurCameraSn )
      {
         pProp->Set( m_asCameraSns[m_nCurCameraSn].c_str() );
      }
      else
      {
         pProp->Set( "" );
      }
   }

   return DEVICE_OK; 
}

/**
 * Handles "CameraMode" property.
 */
int CCameraAdapter::OnMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if( MM::AfterSet == eAct )
   {
      // the user just set the new value for the property, so we have to
      // apply this value to the 'hardware'.
      std::string sMode;
      pProp->Get( sMode );
      for( INT16 nMode=0; nMode<(INT16)m_asCameraModes.size(); nMode++ )
      {
         if( m_asCameraModes[nMode] == sMode )
         {
            RETURN_ON_PIL_ERROR( SelectCameraMode( nMode ) );
            return DEVICE_OK;
         }
      }
      return ERR_INVALID_CAMERA_MODE;
   }
   else if( MM::BeforeGet == eAct )
   {
      // the user is requesting the current value for the property, so
      // either ask the 'hardware' or let the system return the value
      // cached in the property.
      if( -1 < m_nCurCameraMode )
      {
         pProp->Set( m_asCameraModes[m_nCurCameraMode].c_str() );
      }
      else
      {
         pProp->Set( "" );
      }
   }

   return DEVICE_OK; 
}

/**
 * Handles "VideoGain" property.
 */
int CCameraAdapter::OnVideoGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if( MM::AfterSet == eAct )
   {
      // the user just set the new value for the property, so we have to
      // apply this value to the 'hardware'.
      long nGain;
      pProp->Get( nGain );
      m_nVideoGain = (INT16)nGain;
      RETURN_ON_PIL_ERROR( pilSetCameraVideoGain( m_nCurGrabber, m_nVideoGain ) );
   }
   else if( MM::BeforeGet == eAct )
   {
      // the user is requesting the current value for the property, so
      // either ask the 'hardware' or let the system return the value
      // cached in the property.
      pProp->Set( (long)m_nVideoGain );
   }

   return DEVICE_OK; 
}

/**
 * Handles "VideoOffset" property.
 */
int CCameraAdapter::OnVideoOffset(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if( MM::AfterSet == eAct )
   {
      // the user just set the new value for the property, so we have to
      // apply this value to the 'hardware'.
      long nOffset;
      pProp->Get( nOffset );
      m_nVideoOffset = (INT16)nOffset;
      RETURN_ON_PIL_ERROR( pilSetCameraVideoOffset( m_nCurGrabber, m_nVideoOffset ) );
   }
   else if( MM::BeforeGet == eAct )
   {
      // the user is requesting the current value for the property, so
      // either ask the 'hardware' or let the system return the value
      // cached in the property.
      pProp->Set( (long)m_nVideoOffset );
   }

   return DEVICE_OK; 
}

/**
 * Handles "IntensifierVolts" property.
 */
int CCameraAdapter::OnIntensifierVolts(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if( MM::AfterSet == eAct )
   {
      // the user just set the new value for the property, so we have to
      // apply this value to the 'hardware'.
      double fVolts;
      pProp->Get( fVolts );
      m_fIntensifierVolts = fVolts;
      RETURN_ON_PIL_ERROR( pilSetIntensifierVolts( m_nCurGrabber, m_fIntensifierVolts, m_fIntensifierGain ) );
   }
   else if( MM::BeforeGet == eAct )
   {
      // the user is requesting the current value for the property, so
      // either ask the 'hardware' or let the system return the value
      // cached in the property.
      pProp->Set( m_fIntensifierVolts );
   }

   return DEVICE_OK; 
}

/**
 * Handles "IntensifierGain" property.
 */
int CCameraAdapter::OnIntensifierGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if( MM::AfterSet == eAct )
   {
      // the user just set the new value for the property, so we have to
      // apply this value to the 'hardware'.
      double fGain;
      pProp->Get( fGain );
      m_fIntensifierGain = fGain;
      RETURN_ON_PIL_ERROR( pilSetIntensifierGain( m_nCurGrabber, m_fIntensifierGain, m_fIntensifierVolts ) );
   }
   else if( MM::BeforeGet == eAct )
   {
      // the user is requesting the current value for the property, so
      // either ask the 'hardware' or let the system return the value
      // cached in the property.
      pProp->Set( m_fIntensifierGain );
   }

   return DEVICE_OK; 
}

/**
 * Handles "FrameLeft" property.
 */
int CCameraAdapter::OnFrameLeft(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if( MM::AfterSet == eAct )
   {
      // the user just set the new value for the property, so we have to
      // apply this value to the 'hardware'.
      long nLeft;
      pProp->Get( nLeft );
      m_nCapLeft = (INT16)nLeft;
      RETURN_ON_PIL_ERROR( pilSetCaptureOffset( m_nCurGrabber, m_nCapLeft, m_nCapTop ) );
   }
   else if( MM::BeforeGet == eAct )
   {
      // the user is requesting the current value for the property, so
      // either ask the 'hardware' or let the system return the value
      // cached in the property.
      pProp->Set( (long)m_nCapLeft );
   }

   return DEVICE_OK; 
}

/**
 * Handles "FrameTop" property.
 */
int CCameraAdapter::OnFrameTop(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if( MM::AfterSet == eAct )
   {
      // the user just set the new value for the property, so we have to
      // apply this value to the 'hardware'.
      long nTop;
      pProp->Get( nTop );
      m_nCapTop = (INT16)nTop;
      RETURN_ON_PIL_ERROR( pilSetCaptureOffset( m_nCurGrabber, m_nCapLeft, m_nCapTop ) );
   }
   else if( MM::BeforeGet == eAct )
   {
      // the user is requesting the current value for the property, so
      // either ask the 'hardware' or let the system return the value
      // cached in the property.
      pProp->Set( (long)m_nCapTop );
   }

   return DEVICE_OK; 
}

int CCameraAdapter::OnFrameWidth(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if( MM::AfterSet == eAct )
   {
      // This a readonly property
   }
   else if( MM::BeforeGet == eAct )
   {
      pProp->Set( (long)m_nCapWidth );
   }

   return DEVICE_OK; 
}

int CCameraAdapter::OnFrameHeight(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if( MM::AfterSet == eAct )
   {
      // This a readonly property
   }
   else if( MM::BeforeGet == eAct )
   {
      pProp->Set( (long)m_nCapHeight );
   }

   return DEVICE_OK; 
}

/**
 * Handles "Exposure" property.
 */
int CCameraAdapter::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if( MM::AfterSet == eAct )
   {
      // the user just set the new value for the property, so we have to
      // apply this value to the 'hardware'.
      double fExposureMM;
      pProp->Get(fExposureMM);
      SetExposure( fExposureMM );
   }
   else if( MM::BeforeGet == eAct )
   {
      // the user is requesting the current value for the property, so
      // either ask the 'hardware' or let the system return the value
      // cached in the property.
      pProp->Set( GetExposure() );
   }

   return DEVICE_OK; 
}

/**
 * Handles "Integration" property.
 */
int CCameraAdapter::OnIntegration(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if( MM::AfterSet == eAct )
   {
      // the user just set the new value for the property, so we have to
      // apply this value to the 'hardware'.
      long nFrames;
      pProp->Get(nFrames);
      RETURN_ON_PIL_ERROR( SetIntegration( (INT16)nFrames ) );
   }
   else if( MM::BeforeGet == eAct )
   {
      // the user is requesting the current value for the property, so
      // either ask the 'hardware' or let the system return the value
      // cached in the property.
      m_nMMFrameClocks = m_nFrameClocks;
      m_bExposureInSync = TRUE;
      pProp->Set( (long)m_nFrameClocks );
   }

   return DEVICE_OK; 
}

/**
 * Handles "Binning" property.
 */
int CCameraAdapter::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if( MM::AfterSet == eAct )
   {
      // the user just set the new value for the property, so we have to
      // apply this value to the 'hardware'.
      long binFactor;
      pProp->Get(binFactor);
   }
   else if( MM::BeforeGet == eAct )
   {
      // the user is requesting the current value for the property, so
      // either ask the 'hardware' or let the system return the value
      // cached in the property.
   }

   return DEVICE_OK; 
}

/**
 * Handles "PixelType" property.
 */
int CCameraAdapter::OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if( MM::AfterSet == eAct )
   {
      string pixelType;
      pProp->Get(pixelType);
   }
   else if( MM::BeforeGet == eAct )
   {
      pProp->Set( m_sPixel );
   }

   return DEVICE_OK;
}

int CCameraAdapter::OnSourceBrightness( MM::PropertyBase* pProp, MM::ActionType eAct )
{
   if( MM::AfterSet == eAct )
   {
      double fValue;
      pProp->Get( fValue );
      m_fSourceBrightness = fValue;
      RETURN_ON_PIL_ERROR( pilSetInputTransform( m_nCurGrabber, m_fSourceBrightness, m_fSourceContrast, m_fSourceGamma ) );
   }
   else if( MM::BeforeGet == eAct )
   {
      pProp->Set( m_fSourceBrightness );
   }

   return DEVICE_OK;
}

int CCameraAdapter::OnSourceContrast( MM::PropertyBase* pProp, MM::ActionType eAct )
{
   if( MM::AfterSet == eAct )
   {
      double fValue;
      pProp->Get( fValue );
      m_fSourceContrast = fValue;
      RETURN_ON_PIL_ERROR( pilSetInputTransform( m_nCurGrabber, m_fSourceBrightness, m_fSourceContrast, m_fSourceGamma ) );
   }
   else if( MM::BeforeGet == eAct )
   {
      pProp->Set( m_fSourceContrast );
   }

   return DEVICE_OK;
}

int CCameraAdapter::OnSourceGamma( MM::PropertyBase* pProp, MM::ActionType eAct )
{
   if( MM::AfterSet == eAct )
   {
      double fValue;
      pProp->Get( fValue );
      //m_fSourceGamma = fValue;
   }
   else if( MM::BeforeGet == eAct )
   {
      pProp->Set( 1.0 );
   }

   return DEVICE_OK;
}

int CCameraAdapter::OnSourceThreshold( MM::PropertyBase* pProp, MM::ActionType eAct )
{
   if( MM::AfterSet == eAct )
   {
      long nValue;
      pProp->Get( nValue );
      m_nSourceThreshold = (INT16)__min( nValue, (long)m_unImgSaturation );
      RETURN_ON_PIL_ERROR( pilSetInputThreshold( m_nCurGrabber, m_nSourceThreshold ) );
   }
   else if( MM::BeforeGet == eAct )
   {
      pProp->Set( (long)m_nSourceThreshold );
   }

   return DEVICE_OK;
}

int CCameraAdapter::OnSourceAutoContrast( MM::PropertyBase* pProp, MM::ActionType eAct )
{
   if( MM::AfterSet == eAct )
   {
      std::string sValue;
      pProp->Get( sValue );
      m_bSourceAutoContrast = (m_asBooleans[1] == sValue) ? TRUE : FALSE;
      RETURN_ON_PIL_ERROR( pilSetInputAutoContrast( m_nCurGrabber, m_bSourceAutoContrast ) );
   }
   else if( MM::BeforeGet == eAct )
   {
      pProp->Set( m_asBooleans[m_bSourceAutoContrast ? 1 : 0].c_str() );
   }

   return DEVICE_OK;
}

int CCameraAdapter::OnSourceNormalContrast( MM::PropertyBase* pProp, MM::ActionType eAct )
{
   if( MM::AfterSet == eAct )
   {
      std::string sValue;
      pProp->Get( sValue );
      BOOL bNormal = (m_asBooleans[1] == sValue) ? TRUE : FALSE;
      if( bNormal )
      {
         RETURN_ON_PIL_ERROR( pilSetInputNormalContrast( m_nCurGrabber ) );
      }
   }
   else if( MM::BeforeGet == eAct )
   {
      pProp->Set( m_asBooleans[0].c_str() );
   }

   return DEVICE_OK;
}

int CCameraAdapter::OnIonFeedbackFilterSize( MM::PropertyBase* pProp, MM::ActionType eAct )
{
   if( MM::AfterSet == eAct )
   {
      long nValue;
      pProp->Get( nValue );
      m_nIonFeedbackFilterSize = (INT16)nValue;
      RETURN_ON_PIL_ERROR( pilSetIonFeedbackFilterSize( m_nCurGrabber, m_nIonFeedbackFilterSize ) );
   }
   else if( MM::BeforeGet == eAct )
   {
      pProp->Set( (long)m_nIonFeedbackFilterSize );
   }

   return DEVICE_OK;
}

int CCameraAdapter::OnEnableIonFeedback( MM::PropertyBase* pProp, MM::ActionType eAct )
{
   if( MM::AfterSet == eAct )
   {
      std::string sValue;
      pProp->Get( sValue );
      m_bIonFeedbackEnabled = (m_asBooleans[1] == sValue) ? TRUE : FALSE;
      RETURN_ON_PIL_ERROR( pilEnableIonFeedback( m_nCurGrabber, m_bIonFeedbackEnabled ) );
   }
   else if( MM::BeforeGet == eAct )
   {
      pProp->Set( m_asBooleans[m_bIonFeedbackEnabled ? 1 : 0].c_str() );
   }

   return DEVICE_OK;
}

int CCameraAdapter::OnRamStackDepth( MM::PropertyBase* pProp, MM::ActionType eAct )
{
   if( MM::AfterSet == eAct )
   {
      long nValue;
      pProp->Get( nValue );
      m_nIntegratorDepth = (INT32)nValue;
      RETURN_ON_PIL_ERROR( pilSetRamIntegratorDepth( m_nCurGrabber, m_nIntegratorDepth, m_fIntegratorExposure ) );
      m_fIntegratorRate = 1.0 / m_fIntegratorExposure;
   }
   else if( MM::BeforeGet == eAct )
   {
      pProp->Set( (long)m_nIntegratorDepth );
   }

   return DEVICE_OK;
}

int CCameraAdapter::OnRamStackExposure( MM::PropertyBase* pProp, MM::ActionType eAct )
{
   if( MM::AfterSet == eAct )
   {
      double fValue;
      pProp->Get( fValue );
      m_fIntegratorExposure = fValue / 1000.0;
      m_fIntegratorRate = 1.0 / m_fIntegratorExposure;
   }
   else if( MM::BeforeGet == eAct )
   {
      pProp->Set( m_fIntegratorExposure * 1000.0 );
   }

   return DEVICE_OK;
}

int CCameraAdapter::OnEnableRamStack( MM::PropertyBase* pProp, MM::ActionType eAct )
{
   if( MM::AfterSet == eAct )
   {
      std::string sValue;
      pProp->Get( sValue );
      m_bIntegratorEnabled = (m_asBooleans[1] == sValue) ? TRUE : FALSE;
      RETURN_ON_PIL_ERROR( pilEnableRamIntegrator( m_nCurGrabber, m_bIntegratorEnabled ) );
   }
   else if( MM::BeforeGet == eAct )
   {
      pProp->Set( m_asBooleans[m_bIntegratorEnabled ? 1 : 0].c_str() );
   }

   return DEVICE_OK;
}

int CCameraAdapter::OnRamAverageDepth( MM::PropertyBase* pProp, MM::ActionType eAct )
{
   if( MM::AfterSet == eAct )
   {
      long nValue;
      pProp->Get( nValue );
      m_nAveragingDepth = (INT32)nValue;
      RETURN_ON_PIL_ERROR( pilSetRamAveragingDepth( m_nCurGrabber, m_nAveragingDepth ) );
   }
   else if( MM::BeforeGet == eAct )
   {
      pProp->Set( (long)m_nAveragingDepth );
   }

   return DEVICE_OK;
}

int CCameraAdapter::OnRamAverageExposure( MM::PropertyBase* pProp, MM::ActionType eAct )
{
   if( MM::AfterSet == eAct )
   {
      double fValue;
      pProp->Get( fValue );
      m_fAveragingRate = 1000.0 / fValue;
   }
   else if( MM::BeforeGet == eAct )
   {
      pProp->Set( 1000.0 / m_fAveragingRate );
   }

   return DEVICE_OK;
}

int CCameraAdapter::OnEnableSmoothRamAverage( MM::PropertyBase* pProp, MM::ActionType eAct )
{
   if( MM::AfterSet == eAct )
   {
      std::string sValue;
      pProp->Get( sValue );
      m_bSmoothAvgEnabled = (m_asBooleans[1] == sValue) ? TRUE : FALSE;
      RETURN_ON_PIL_ERROR( pilEnableSmoothRamAveraging( m_nCurGrabber, m_bSmoothAvgEnabled ) );
   }
   else if( MM::BeforeGet == eAct )
   {
      pProp->Set( m_asBooleans[m_bSmoothAvgEnabled ? 1 : 0].c_str() );
   }

   return DEVICE_OK;
}

int CCameraAdapter::OnEnableFastRamAverage( MM::PropertyBase* pProp, MM::ActionType eAct )
{
   if( MM::AfterSet == eAct )
   {
      std::string sValue;
      pProp->Get( sValue );
      m_bFastAvgEnabled = (m_asBooleans[1] == sValue) ? TRUE : FALSE;
      RETURN_ON_PIL_ERROR( pilEnableFastRamAveraging( m_nCurGrabber, m_bFastAvgEnabled ) );
   }
   else if( MM::BeforeGet == eAct )
   {
      pProp->Set( m_asBooleans[m_bFastAvgEnabled ? 1 : 0].c_str() );
   }

   return DEVICE_OK;
}

/**
 * Handles "ReadoutTime" property.
 */
int CCameraAdapter::OnReadoutTime( MM::PropertyBase* pProp, MM::ActionType eAct )
{
   if (eAct == MM::AfterSet)
   {
      double readoutMs;
      pProp->Get(readoutMs);

      readoutUs_ = readoutMs * 1000.0;
   }
   else if( MM::BeforeGet == eAct )
   {
      pProp->Set(readoutUs_ / 1000.0);
   }

   return DEVICE_OK;
}
///////////////////////////////////////////////////////////////////////////////
// Private CCameraAdapter methods
///////////////////////////////////////////////////////////////////////////////

/**
 * Sync internal image buffer size to the chosen property values.
 */
int CCameraAdapter::ResizeImageBuffer()
{
   if( m_punRoi )
   {
      delete [] m_punRoi;
   }
   m_punRoi = new UCHAR[m_nRoiImageBytes];
   ZeroMemory( m_punRoi, m_nRoiImageBytes );
   return DEVICE_OK;
}

void CCameraAdapter::UpdateGUI()
{
   if( m_bIsInitialized )
   {
      OnPropertiesChanged();
   }
}
