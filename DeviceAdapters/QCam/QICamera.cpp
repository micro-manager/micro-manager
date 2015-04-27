///////////////////////////////////////////////////////////////////////////////
// FILE:          QICamera.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Micro-Manager plugin for QImaging cameras using the QCam API.
//                
// AUTHOR:        QImaging, updated by Jeff R. Kuhn, jrkuhn@vt.edu, Oct 2009
//
// COPYRIGHT:     Copyright (C) 2007 Quantitative Imaging Corporation (QImaging).
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
//
// CVS:           $Id: QICamera.cpp,v 1.23 2007/06/01 16:58:26 maustin Exp $
//

#ifdef WIN32
#pragma warning ( disable: 4068)
#endif

#include <string>
#include <math.h>
#include <sstream>
#include <algorithm>
#include <boost/algorithm/string.hpp>
#include <boost/lexical_cast.hpp>
#include "QICamera.h" // Must be included after boost (QCamApi.h uses #define for types(!), which breaks boost)

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Debug helpers
//

#undef DEBUG_METHOD_NAMES
#undef DEBUG_ERRORS

#ifdef DEBUG_METHOD_NAMES
#define START_METHOD(name)              this->LogMessage(name);
#define START_ONPROPERTY(name,action)   this->LogMessage(string(name)+(action==MM::AfterSet?"(AfterSet)":"(BeforeGet)"));
#else
#define START_METHOD(name)
#define START_ONPROPERTY(name,action)
#endif

#ifdef DEBUG_ERRORS
#define REPORT_QERR(inErr)                  LogError("QCamera Error ", inErr, __FILE__, __LINE__);
#define REPORT_QERR2(msg,inErr)             LogError(msg, inErr, __FILE__, __LINE__);
#define QCam_REPORT_QERR(pCam,inErr)        pCam->LogError("QCamera Error ", inErr, __FILE__, __LINE__);
#define REPORT_MMERR(inErr)                 LogError("Metamorph Error ", inErr, __FILE__, __LINE__);
#define REPORT_MMERR3(inErr,location,line)  LogError("Metamorph Error ", inErr, location, line);
#define QCam_REPORT_MMERR(pCam,inErr)       pCam->LogError("Metamorph Error ", inErr, __FILE__, __LINE__);
#else
#define REPORT_QERR(inErr)
#define REPORT_QERR2(msg,inErr)
#define QCam_REPORT_QERR(pCam,inErr) 
#define REPORT_MMERR(inErr)
#define REPORT_MMERR3(inErr,location,line)
#define QCam_REPORT_MMERR(pCam,inErr)
#endif




///////////////////////////////////////////////////////////////////////////////
// Class static variables
//

// camera device name
const char* g_CameraDeviceName = "QCamera";


string Yes("Yes");
string No("No");    


///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_CameraDeviceName, MM::CameraDevice, "QImaging universal camera adapter");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0) {
      return 0;
   }

   // decide which device class to create based on the deviceName parameter
   if (strcmp(deviceName, g_CameraDeviceName) == 0) {
      // create camera
      return new QICamera();
   }

   // ...supplied name not recognized
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

/////////////////////////////////////////////////////////////////////////////
// Helper Functions (global scope)
/////////////////////////////////////////////////////////////////////////////

void ConvertReadoutSpeedToString(QCam_qcReadoutSpeed inSpeed, char *outString)
{	
   float readoutSpeed;

   switch(inSpeed) {
    case qcReadout48M:
       readoutSpeed = 48.0;
       break;

    case qcReadout40M:
       readoutSpeed = 40.0;
       break;

    case qcReadout30M:
       readoutSpeed = 30.0;
       break;

    case qcReadout24M:
       readoutSpeed = 24.0;
       break;

    case qcReadout20M:
       readoutSpeed = 20.0;
       break;

    case qcReadout10M:
       readoutSpeed = 10.0;
       break;

    case qcReadout5M:
       readoutSpeed = 5.0;
       break;

    case qcReadout2M5:
       readoutSpeed = 2.5;
       break;

    case qcReadout1M:
       readoutSpeed = 1.0;
       break;

    default:
       readoutSpeed = 20.0;
       break;
   }

   sprintf(outString, "%4.1f MHz", readoutSpeed);
}

bool dequals(const double a, const double b, const double eps)
{
   return fabs(a - b) < eps;
}

void ConvertReadoutSpeedToEnum(const char *inSpeed, QCam_qcReadoutSpeed *outSpeed)
{
   double			value;
   const double    eps = 0.05;

   // convert the string to a float
   value = atof(inSpeed);

   if (dequals(value, 48.0, eps)) {
      *outSpeed = qcReadout48M;
   } else if (dequals(value, 40.0, eps)) {
      *outSpeed = qcReadout40M;
   } else if (dequals(value, 30.0, eps)){
      *outSpeed = qcReadout30M;
   } else if (dequals(value, 24.0, eps)) {
      *outSpeed = qcReadout24M;
   } else if (dequals(value, 20.0, eps)) {
      *outSpeed = qcReadout20M;
   } else if (dequals(value, 10.0, eps)) {
      *outSpeed = qcReadout10M;
   } else if (dequals(value, 5.0, eps)) {
      *outSpeed = qcReadout5M;
   } else if (dequals(value, 2.5, eps)) {
      *outSpeed = qcReadout2M5;
   } else if (dequals(value, 1.0, eps)) {
      *outSpeed = qcReadout1M;
   } else  {
      // default
      *outSpeed = qcReadout20M;
   }
}

void ConvertReadoutPortToString(QCam_qcReadoutPort inPort, char *outString)
{	
   switch(inPort) {
    case qcPortNormal:
       strcpy(outString, "Normal");
       break;

    case qcPortEM:
       strcpy(outString, "EM Port");
       break;

    default:
       strcpy(outString, "Unknown");
       break;
   }
}


void ConvertReadoutPortToEnum(const char *inPort, QCam_qcReadoutPort *outPort)
{
   if (!strcmp(inPort, "Normal")) {
      *outPort = qcPortNormal;
   } else if (!strcmp(inPort, "EM Port")) {
      *outPort = qcPortEM;
   } else {
      // default
      *outPort = qcPortNormal;
   }
}

void ConvertTriggerTypeToString(QCam_qcTriggerType inType, char *outString)
{	
   switch(inType) {
    case qcTriggerFreerun:
       strcpy(outString, "Freerun");
       break;
    case qcTriggerEdgeHi:
       strcpy(outString, "Edge Hi");
       break;
    case qcTriggerEdgeLow:
       strcpy(outString, "Edge Low");
       break;
    case qcTriggerPulseHi:
       strcpy(outString, "Pulse Hi");
       break;
    case qcTriggerPulseLow:
       strcpy(outString, "Pulse Low");
       break;
    case qcTriggerSoftware:
       strcpy(outString, "Software");
       break;
    case qcTriggerStrobeHi:
       strcpy(outString, "Strobe Hi");
       break;
    case qcTriggerStrobeLow:
       strcpy(outString, "Strobe Low");
       break;
    default:
       strcpy(outString, "Unknown");
       break;
   }
}

void ConvertTriggerTypeToEnum(const char *inType, QCam_qcTriggerType *outType)
{
   if (!strcmp(inType, "Freerun")) {
      *outType = qcTriggerFreerun;
   } else if (!strcmp(inType, "Edge Hi")) {
      *outType = qcTriggerEdgeHi;
   } else if (!strcmp(inType, "Edge Low")) {
      *outType = qcTriggerEdgeLow;
   } else if (!strcmp(inType, "Pulse Hi")) {
      *outType = qcTriggerPulseHi;
   } else if (!strcmp(inType, "Pulse Low")) {
      *outType = qcTriggerPulseLow;
   } else if (!strcmp(inType, "Software")) {
      *outType = qcTriggerSoftware;
   } else if (!strcmp(inType, "Strobe Hi")) {
      *outType = qcTriggerStrobeHi;
   } else if (!strcmp(inType, "Strobe Low")) {
      *outType = qcTriggerStrobeLow;
   } else {
      // default
      *outType = qcTriggerFreerun;
   }
}

static string ConvertCameraTypeToString(unsigned long cameraType)
{
   char cameraStr[256] = "Unknown Model"; // use of C string is only for historical reasons

   switch(cameraType) {
      case qcCameraRet1300B:
         strcpy(cameraStr, "Retiga 1300B");
         break;

      case qcCameraRet1350B:
         strcpy(cameraStr, "Retiga 1350B");
         break;

      case qcCameraQICamB:
         strcpy(cameraStr, "QICAM");
         break;

      case qcCameraMicroPub:
         strcpy(cameraStr, "Micropublisher");
         break;

      case qcCameraRetIT:
         strcpy(cameraStr, "Retiga Intensified");
         break;

      case qcCameraQICamIR:
         strcpy(cameraStr, "QICAM IR");
         break;

      case qcCameraRet4000R:
         strcpy(cameraStr, "Retiga 4000R");
         break;

      case qcCameraRet2000R:
         strcpy(cameraStr, "Retiga 2000R");
         break;

      case qcCameraRoleraXR:
         strcpy(cameraStr, "Rolera XR");
         break;

      case qcCameraRetigaSRV:
         strcpy(cameraStr, "Retiga SRV");
         break;

      case qcCameraRoleraMGi:
         strcpy(cameraStr, "Rolera MGi");
         break;

      case qcCameraRet4000RV:
         strcpy(cameraStr, "Retiga 4000RV");
         break;

      case qcCameraGo1:
         strcpy(cameraStr, "Go 1");
         break;

      case qcCameraGo3:
         strcpy(cameraStr, "Go 3");
         break;

      case qcCameraGo5:
         strcpy(cameraStr, "Go 5");
         break;

      case qcCameraGo21:
         strcpy(cameraStr, "Go 21");
         break;

#ifdef WIN32

      case qcCameraEXiBlue:
         strcpy(cameraStr, "EXi Blue");
         break;


      case 34: // TODO get this from header
         strcpy (cameraStr, "Rolera Bolt");
         break;

#endif
         /*		case qcCameraRoleraOne:
         strcpy(cameraStr, "Rolera One");
         break;
         */

      default:
         strcpy(cameraStr, ("Unknown Model (" + boost::lexical_cast<string>(cameraType) + ")").c_str());
         break;
   }

   return cameraStr;
}

///////////////////////////////////////////////////////////////////////////////
// QIDriver implementation
///////////////////////////////////////////////////////////////////////////////

unsigned QIDriver::s_usageCount = 0;

vector<string> QIDriver::AvailableCameras()
{
   // We cache results, because we may have trouble getting camera names when
   // they are already in use. We assume that this function will have to be
   // called at least once before opening any camera, so that we can get all
   // the camera names. If that is not the case, we still get at least a list
   // of unique id strings that are fully functional if not human-readable.
   static bool cached = false;
   static vector<string> displayStrings;

   if (cached) {
      return displayStrings;
   }

   QIDriver::Access access;
   if (!access) {
      return displayStrings;
   }

   QCam_CamListItem cameras[10];
   unsigned long numCameras = sizeof(cameras) / sizeof(QCam_CamListItem);
   QCam_Err err = QCam_ListCameras(cameras, &numCameras);
   if (err != qerrSuccess || numCameras == 0) {
      return displayStrings;
   }

   for (size_t i = 0; i < numCameras; i++) {
      // Our display strings always start with the unique id for later retrieval
      string displayString = boost::lexical_cast<string>(cameras[i].uniqueId);

      // Append camera model and serial number if available
      if (cameras[i].isOpen) {
         // Fall back to enum values
         displayString += " ";
         displayString += ConvertCameraTypeToString(cameras[i].cameraType);
      }
      else {
         QCam_Handle camera;
         err = QCam_OpenCamera(cameras[i].cameraId, &camera);
         if (err == qerrSuccess) {
            char model[512];
            memset(model, 0, sizeof(model));
            err = QCam_GetCameraModelString(camera, model, sizeof(model) - 1); // not sure if -1 is necessary
            if (err == qerrSuccess) {
               displayString += " ";
               displayString += model;
            }
            else {
               displayString += " ";
               displayString += ConvertCameraTypeToString(cameras[i].cameraType);
            }

            
            char serial[SER_NUM_LEN];
            memset(serial, 0, sizeof(serial));
#ifdef _WIN32
            if (cameras[i].cameraType == qcCameraGoBolt)
            {
                unsigned long serialNumber;
                err = QCam_GetInfo(camera, qinfSerialNumber, &serialNumber);
                if (err == qerrSuccess) {
                   snprintf(serial, SER_NUM_LEN, "%u", serialNumber);
                   if (strlen(serial) == 0) {
                       strcpy(serial, "N/A");
                 }
                 displayString += " S/N ";
                 displayString += serial;
               }
            }
            else
#endif
            {
               err = QCam_GetSerialString(camera, serial, sizeof(serial) -1);
               if (err == qerrSuccess) {
                  if (strlen(serial) == 0) {
                     strcpy(serial, "N/A");
                  }
                  displayString += " S/N ";
                  displayString += serial;
                }
            }

            err = QCam_CloseCamera(camera);
         }
         else {
            displayString += " ";
            displayString += ConvertCameraTypeToString(cameras[i].cameraType);
         }
      }

      displayStrings.push_back(displayString);
   }

   cached = true;
   return displayStrings;
}

unsigned long QIDriver::UniqueIdForCamera(const string& displayString)
{
   vector<string> items;
   boost::split(items, displayString, boost::is_space());
   if (items.empty()) { // Shouldn't happen
      return 0;
   }
   return boost::lexical_cast<unsigned long>(items[0]);
}

QIDriver::Access::Access() : m_error(qerrSuccess)
{
   if (QIDriver::s_usageCount) {
      ++QIDriver::s_usageCount;
      return;
   }

   // try releasing the driver first
   QCam_ReleaseDriver();

   m_error = QCam_LoadDriver();
   if (m_error == qerrSuccess) {
      ++QIDriver::s_usageCount;
   }
}

QIDriver::Access::~Access()
{
   if (bool(*this) && !--QIDriver::s_usageCount) {
      QCam_ReleaseDriver();
   }
}

///////////////////////////////////////////////////////////////////////////////
// QICamera Constructor/Destructor and Initialization/Shutdown
///////////////////////////////////////////////////////////////////////////////


/**
* QICamera constructor.
* Setup default all variables and create device properties required to exist
* before intialization. In this case, no such properties were required. All
* properties will be created in the Initialize() method.
*
* As a general guideline Micro-Manager devices do not access hardware in the
* the constructor. We should do as little as possible in the constructor and
* perform most of the initialization in the Initialize() method.
*/
QICamera::QICamera()
:CCameraBase<QICamera> ()
,m_isInitialized(false)
,m_softwareTrigger(false)
,m_rgbColor(false)
,m_dExposure(0)
,m_interval(0)
,m_bitDepth(0)
,m_maxBitDepth(0)
,m_frameBuffs(NULL)
,m_frameBuffsAvail(NULL)
,m_frameDoneBuff(-1)
{
   START_METHOD("QICamera::QICamera");

   m_sthd = new QISequenceThread(this);

   // call the base class method to set-up default error codes/messages
   InitializeDefaultErrorMessages();

   // setup additional error codes/messages
   SetErrorText(ERR_NO_CAMERAS_FOUND, "No cameras found.  Please connect a QImaging camera and turn it on");
   SetErrorText(ERR_CAMERA_ALREADY_OPENED, "Camera is already in use");
   SetErrorText(ERR_BUSY_ACQUIRING,   "QImaging camera is already acquiring images.");
   SetErrorText(ERR_SOFTWARE_TRIGGER_FAILED, "QImaging camera is not in software trigger mode.");

   // Get the list of connected cameras, and provide it as a pre-init property
   vector<string> cameras = QIDriver::AvailableCameras();
   if (!cameras.empty()) {
      (void)CreateProperty(g_Keyword_Camera, cameras[0].c_str(), MM::String, false, NULL, true);
      SetAllowedValues(g_Keyword_Camera, cameras);
   }
}



/**
* QICamera destructor.
* If this device used as intended within the Micro-Manager system,
* Shutdown() will be always called before the destructor. But in any case
* we need to make sure that all resources are properly released even if
* Shutdown() was not called.
*/
QICamera::~QICamera()
{
   START_METHOD("QICamera::~QICamera");

   if (m_isInitialized)
      Shutdown();
}

/**
* Obtains device name.
* Required by the MM::Device API.
*/
void QICamera::GetName(char* name) const
{
   START_METHOD("QICamera::GetName");

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
*/
int QICamera::Initialize()
{
   const unsigned short		CAMERA_STRING_LENGTH = 256;

   QCam_Err            err;
   QCam_CamListItem	   cameraList[10];
   unsigned long	   numOfCameras;
   char	               cameraStr[CAMERA_STRING_LENGTH];
   char                cameraName[CAMERA_STRING_LENGTH];
   unsigned short	   major, minor, build;
   char	               qcamVersionStr[256];
   char	               cameraIDStr[256];
   unsigned long	      ccdType;
   unsigned long	      cameraType = qcCameraUnknown;
   int                 nRet;

   START_METHOD("QICamera::Initialize");

   // check
   if (m_isInitialized == true) {
      return DEVICE_OK;
   }

   // init the driver
   m_driverAccess = auto_ptr<QIDriver::Access>(new QIDriver::Access);
   if (!*m_driverAccess) {
      REPORT_QERR(m_driverAccess->Status());
      return DEVICE_NATIVE_MODULE_FAILED;
   }

   // recover the camera unique id set with the pre-init property, if applicable
   bool useUniqueId = false;
   unsigned long uniqueId = 0;
   if (HasProperty(g_Keyword_Camera)) {
      char buf[MM::MaxStrLength];
      if (GetProperty(g_Keyword_Camera, buf) == DEVICE_OK) {
         uniqueId = QIDriver::UniqueIdForCamera(buf);
         useUniqueId = true;
      }
   }

   try {
      // get a list of connected cameras
      // we are only looking for the first camera
      numOfCameras = sizeof(cameraList) / sizeof(QCam_CamListItem);

      err = QCam_ListCameras(cameraList, &numOfCameras);

      // check if we found a camera
      if (err != qerrSuccess || numOfCameras == 0) {
         throw ERR_NO_CAMERAS_FOUND;
      }

      size_t cameraIndex = 0; // open the first camera by default
      if (useUniqueId) {
         bool found = false;
         for (size_t i = 0; i < numOfCameras; i++) {
            if (cameraList[i].uniqueId == uniqueId) {
               cameraIndex = i;
               found = true;
               break;
            }
         }
         if (!found) {
            LogMessage("The specified camera was not found; falling back to using the first available one");
         }
      }

      if (cameraList[cameraIndex].isOpen) {
         throw ERR_CAMERA_ALREADY_OPENED;
      }

      err = QCam_OpenCamera(cameraList[cameraIndex].cameraId, &m_camera);
      if (err != qerrSuccess) {
         REPORT_QERR(err);
         throw DEVICE_ERR;
      }

      // get the QCam version number
      err = QCam_LibVersion(&major, &minor, &build);
      if (err != qerrSuccess) {
         REPORT_QERR(err);
         throw DEVICE_ERR;
      }

      sprintf(qcamVersionStr, "QCam %u.%u.%u", major, minor, build);
      m_nDriverBuild = major * 100 + minor * 10 + build;

      nRet = CreateProperty(MM::g_Keyword_Description, qcamVersionStr, MM::String, true);
      if (DEVICE_OK != nRet) {
         throw nRet;
      }

#ifdef WIN32
      // read the camera settings
      m_settings = malloc(sizeof(QCam_SettingsEx));
      err = QCam_CreateCameraSettingsStruct((QCam_SettingsEx *)m_settings);
      if (err == qerrNotSupported) {
         return DEVICE_NOT_SUPPORTED;
      } else if (err != qerrSuccess) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }

      err = QCam_InitializeCameraSettings(m_camera, (QCam_SettingsEx *)m_settings);
      if (err == qerrNotSupported) {
         return DEVICE_NOT_SUPPORTED;
      } else if (err != qerrSuccess) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }

      err = QCam_ReadSettingsFromCam(m_camera, (QCam_Settings *)m_settings);
      if (err == qerrNotSupported) {
         return DEVICE_NOT_SUPPORTED;
      } else if (err != qerrSuccess) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }

#else
      m_settings = malloc(sizeof(QCam_Settings));
      ((QCam_Settings*)m_settings)->size = sizeof(QCam_Settings);
      err = QCam_ReadDefaultSettings(m_camera, (QCam_Settings*)m_settings);
      if (err != qerrSuccess) {
         REPORT_QERR(err);
         throw DEVICE_ERR;
      }

#endif
      err = QCam_GetInfo(m_camera, qinfCcdType, &ccdType);
      if (err != qerrSuccess) {
         REPORT_QERR(err);
         throw DEVICE_ERR;
      }

      if (ccdType == qcCcdMonochrome) {
         m_rgbColor = false;
      } else if (ccdType == qcCcdColorBayer) {           
         m_rgbColor = false; // default is false for now
         /// COLOR MODE
         // the camera can interpret pixels as color data with the Bayer pattern
         CPropertyAction* pAct = new CPropertyAction (this, &QICamera::OnColorMode);
         CreateProperty(g_Keyword_Color_Mode, g_Value_OFF, MM::String, false, pAct);
         AddAllowedValue(g_Keyword_Color_Mode, g_Value_ON);
         AddAllowedValue(g_Keyword_Color_Mode, g_Value_OFF);
      } else {
         LogMessage("Unsupported camera type", false);
         throw DEVICE_NOT_SUPPORTED;
      }

      m_rgbColor = false;

      // turn post processing on
      /*err = QCam_SetParam((QCam_Settings *)m_settings, qprmDoPostProcessing, true);
      CHECK_ERROR(err);

      err = QCam_SetParam((QCam_Settings *)m_settings, qprmPostProcessBayerAlgorithm, qcBayerInterpAvg4);
      CHECK_ERROR(err);

      err = QCam_SetParam((QCam_Settings *)m_settings, qprmPostProcessImageFormat, qfmtRgb24);
      CHECK_ERROR(err);

      err = QCam_SendSettingsToCam(m_camera, (QCam_Settings *)m_settings);
      CHECK_ERROR(err);*/


      // set property list
      // -----------------

      // NAME
      nRet = CreateProperty(MM::g_Keyword_Name, g_CameraDeviceName, MM::String, true);
      if (DEVICE_OK != nRet) {
         throw nRet;
      }

      // CAMERA NAME

      // get the camera model string
      err = QCam_GetCameraModelString(m_camera, cameraStr, CAMERA_STRING_LENGTH);
      if (err != qerrSuccess) {
         // the function is not supported, so use a lookup table
         err = QCam_GetInfo(m_camera, qinfCameraType, &cameraType);
         if (err != qerrSuccess) {
            REPORT_QERR(err);
            throw DEVICE_ERR;
         }

         strcpy(cameraStr, ConvertCameraTypeToString(cameraType).c_str());
      }

      strcpy(cameraName, cameraStr);
      nRet = CreateProperty(MM::g_Keyword_CameraName, cameraStr, MM::String, true);
      if (nRet != DEVICE_OK) {
         throw nRet;
      }

      // CAMERA ID
#ifdef _WIN32
      if (cameraType == qcCameraGoBolt)
      {
          unsigned long serialNumber;
          err = QCam_GetInfo(m_camera, qinfSerialNumber, &serialNumber);
          if (err != qerrSuccess) {
             REPORT_QERR(err);
             throw DEVICE_ERR;
          }
          else {
            snprintf(cameraStr, CAMERA_STRING_LENGTH, "%u", serialNumber);
         }
      }
      else
#endif
      {
      // get the camera serial string
         err = QCam_GetSerialString(m_camera, cameraStr, CAMERA_STRING_LENGTH);
         if (err != qerrSuccess) {
            REPORT_QERR(err);
            throw DEVICE_ERR;
         }
      }

      // if the string is empty, fill it with "N/A"
      if (strcmp(cameraStr, "") == 0) {
         strcpy(cameraStr, "N/A");
      }

      sprintf(cameraIDStr, "Serial number %s", cameraStr);

      nRet = CreateProperty(MM::g_Keyword_CameraID, cameraIDStr, MM::String, true);
      if (nRet != DEVICE_OK && nRet != DEVICE_NOT_SUPPORTED) {
         throw nRet;
      }

      // EXPOSURE
      nRet = SetupExposure();
      if (nRet != DEVICE_OK && nRet != DEVICE_NOT_SUPPORTED) {
         throw nRet;
      }

      // BINNING
      nRet = SetupBinning();
      if (nRet != DEVICE_OK && nRet != DEVICE_NOT_SUPPORTED) {
         throw nRet;
      }

      if (strcmp(cameraName, "Rolera EMC2") != 0) {
         //  GAIN
         nRet = SetupGain();
         if (nRet != DEVICE_OK && nRet != DEVICE_NOT_SUPPORTED) {
            throw nRet;
         }
      }
      // OFFSET
      nRet = SetupOffset();
      if (nRet != DEVICE_OK && nRet != DEVICE_NOT_SUPPORTED) {
         throw nRet;
      }

      // READOUT SPEED
      nRet = SetupReadoutSpeed();
      if (nRet != DEVICE_OK && nRet != DEVICE_NOT_SUPPORTED) {
         throw nRet;
      }

      // READOUT Port

      nRet = SetupReadoutPort();
      if (nRet != DEVICE_OK && nRet != DEVICE_NOT_SUPPORTED) {
         throw nRet;
      }

      // BIT DEPTH
      nRet = SetupBitDepth();
      if (nRet != DEVICE_OK && nRet != DEVICE_NOT_SUPPORTED) {
         throw nRet;
      }

      // COOLER
      nRet = SetupCooler();
      if (nRet != DEVICE_OK && nRet != DEVICE_NOT_SUPPORTED) {
         throw nRet;
      }

      // REGULATED COOLING
      nRet = SetupRegulatedCooling();
      if (nRet != DEVICE_OK && nRet != DEVICE_NOT_SUPPORTED) {
         throw nRet;
      }

#ifdef WIN32
      //if we have easy em gain then create em gain differently then if only em gain
      unsigned long				isEMGainAvailable;					
      nRet = QCam_GetInfo(m_camera, qinfEasyEmModeSupported, &isEMGainAvailable);
      if (nRet == DEVICE_OK)
      {
         if (1 == isEMGainAvailable)
         {
            // EASY AND EM GAIN
            nRet = SetupEMAndEasyEMGain();
            if (nRet != DEVICE_OK && nRet != DEVICE_NOT_SUPPORTED) {
               throw nRet;
            }
         }
         else 
         {
            // EM GAIN
            nRet = SetupEMGain();
            if (nRet != DEVICE_OK && nRet != DEVICE_NOT_SUPPORTED) {
               throw nRet;
            }
         }
      }
#else
      // EM GAIN
      nRet = SetupEMGain();
      if (nRet != DEVICE_OK && nRet != DEVICE_NOT_SUPPORTED) {
         throw nRet;
      }
#endif
      // IT GAIN
      if (strcmp(cameraStr, "Rolera EMC2") != 0) {
         nRet = SetupITGain();
         if (nRet != DEVICE_OK && nRet != DEVICE_NOT_SUPPORTED) {
            throw nRet;
         }
      }


      // TRIGGER MODE
      nRet = SetupTriggerType();
      if (nRet != DEVICE_OK && nRet != DEVICE_NOT_SUPPORTED) {
         throw nRet;
      }

      // TRIGGER DELAY
      nRet = SetupTriggerDelay();
      if (nRet != DEVICE_OK && nRet != DEVICE_NOT_SUPPORTED) {
         throw nRet;
      }

      // setup the image buffers for queuing frames
      nRet = SetupFrames();
      if (DEVICE_OK != nRet) {
         throw nRet;
      }

      // synchronize all properties
      // --------------------------
      nRet = UpdateStatus();
      if (nRet != DEVICE_OK) {
         throw nRet;
      }

      // setup the buffer
      // ----------------
      nRet = ResizeImageBuffer();
      if (nRet != DEVICE_OK) {
         throw nRet;
      }

      m_isInitialized = true;

   } catch (int error) {
      REPORT_MMERR3(error, "QICamera::Initialize", __LINE__);
      m_isInitialized = false;
      return error;
   }

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
int QICamera::Shutdown()
{
   QCam_Err			err;
   int                 returnValue;

   START_METHOD("QICamera::Shutdown");

   // check
   if (m_isInitialized == false) {
      return DEVICE_OK;
   }

   returnValue = DEVICE_OK;

   //
   // ATTEMPT EACH ONE OF THESE SHUTDOWN OPERATIONS
   // Do not stop on an error, but go through the list.
   // Sometimes this allows the driver to reload after
   // a failed debugging session.
   //

   // abort all frames
   err = QCam_Abort(m_camera);
   if (err != qerrSuccess) {
      returnValue = DEVICE_ERR;
   }

   // turn off streaming
   err = QCam_SetStreaming(m_camera, false);
   if (err != qerrSuccess) {
      returnValue = DEVICE_ERR;
   }

   // close the camera
   err = QCam_CloseCamera(m_camera);
   if (err != qerrSuccess) {
      returnValue = DEVICE_ERR;
   }
#ifdef WIN32
   QCam_ReleaseCameraSettingsStruct((QCam_SettingsEx *)m_settings);
#endif
   // release the driver
   m_driverAccess.reset();

   // free up the memory used by the frames
   if (m_frameBuffs != NULL) {
      for (int i=0; i<m_nFrameBuffs; i++) {
         delete (unsigned char*)m_frameBuffs[i]->pBuffer;
         delete m_frameBuffs[i];
      }
      delete m_frameBuffs;
   }
   if (m_frameBuffsAvail != NULL) {
      delete m_frameBuffsAvail;
   }

   m_isInitialized = false;
   return returnValue;
}

/////////////////////////////////////////////////////////////////////////////
// Setup Methods
/////////////////////////////////////////////////////////////////////////////

/**
* Initialize the Exposure property with allowed values
*/
int QICamera::SetupExposure()
{
   CPropertyAction				*propertyAction;
   char						tempStr[256];
   unsigned long long			exposure, exposureMin, exposureMax;
   float						exposureAsFloat, exposureMinAsFloat, exposureMaxAsFloat;
   QCam_Err					err;
   int							nRet;
   bool                     minMaxSupport = true;

   START_METHOD("QICamera::SetupExposure");

   err = QCam_GetParam64((QCam_Settings *)m_settings, qprm64Exposure, &exposure);
   if (err == qerrNotSupported) {
      return DEVICE_NOT_SUPPORTED;
   } else if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   // get the min value
   err = QCam_GetParam64Min((QCam_Settings *)m_settings, qprm64Exposure, &exposureMin);
   if (err == qerrNotSupported) {
      minMaxSupport = false;
   } else if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   // get the max value
   err = QCam_GetParam64Max((QCam_Settings *)m_settings, qprm64Exposure, &exposureMax);
   if (err == qerrNotSupported) {
      minMaxSupport = false;
   } else if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   // convert the nanosecond exposure time into a millisecond string
   exposureAsFloat = (float)exposure / 1000000.0f;
   sprintf(tempStr, "%f", exposureAsFloat);
   m_dExposure = exposureAsFloat;

   propertyAction = new CPropertyAction (this, &QICamera::OnExposure);
   nRet = CreateProperty(MM::g_Keyword_Exposure, tempStr, MM::Float, false, propertyAction);
   if (nRet != DEVICE_OK) {
      REPORT_MMERR(nRet);
      return nRet;
   }

   if (minMaxSupport) {
      // create the min property
      exposureMinAsFloat = (float)exposureMin / 1000000.0f;
      sprintf(tempStr, "%f", exposureMinAsFloat);
      nRet = CreateProperty(g_Keyword_Exposure_Min, tempStr, MM::Float, true);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return nRet;
      }

      // create the max property
      exposureMaxAsFloat = (float)exposureMax / 1000000.0f;
      sprintf(tempStr, "%f", exposureMaxAsFloat);
      nRet = CreateProperty(g_Keyword_Exposure_Max, tempStr, MM::Float, true);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return nRet;
      }

      nRet = this->SetPropertyLimits(MM::g_Keyword_Exposure, exposureMinAsFloat, exposureMaxAsFloat);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return nRet;
      }
   }
   return DEVICE_OK;
}

/**
* Initialize the Binning property with allowed values
*/
int QICamera::SetupBinning()
{
   unsigned long				binningTable[32];
   int         				binningTableSize = sizeof(binningTable) / sizeof (unsigned long);
   int							counter;
   vector<string>				binValues;
   QCam_Err					err;
   CPropertyAction				*propertyAction;
   int							nRet;
   char						tempStr[256];

   START_METHOD("QICamera::SetupBinning");

   err = QCam_GetParamSparseTable((QCam_Settings *)m_settings, qprmBinning, binningTable, &binningTableSize);

   // if symmetrical binning is not available, try just vertical
   if (err == qerrNotSupported) {
      err = QCam_GetParamSparseTable((QCam_Settings *)m_settings, qprmVerticalBinning, binningTable, &binningTableSize);
   }
   if (err == qerrNotSupported) {
      return DEVICE_NOT_SUPPORTED;
   } else if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   // go through each binning mode
   for (counter = 0; counter < binningTableSize; counter ++) {
      // convert the integer to a string
      sprintf(tempStr, "%lu", binningTable[counter]);

      // add the binning value to the vector
      binValues.push_back(tempStr);
   }

   // create the action 
   propertyAction = new CPropertyAction (this, &QICamera::OnBinning);
   nRet = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, propertyAction);
   if (nRet != DEVICE_OK) {
      REPORT_MMERR(nRet);
      return nRet;
   }

   // set the allowed values
   nRet = SetAllowedValues(MM::g_Keyword_Binning, binValues);
   if (nRet != DEVICE_OK) {
      REPORT_MMERR(nRet);
      return nRet;
   }

   return DEVICE_OK;
}


/**
* Initialize the Gain property with allowed values
*/
int QICamera::SetupGain()
{
   CPropertyAction				*propertyAction;
   char						tempStr[256];
   unsigned long				gain, gainMin, gainMax;
   float						gainAsFloat, gainMinAsFloat, gainMaxAsFloat;
   QCam_Err					err;
   int							nRet;
   bool                     minMaxSupport = true;

   START_METHOD("QICamera::SetupGain");

   // convert the normalized gain into a float
   err = QCam_GetParam((QCam_Settings *)m_settings, qprmNormalizedGain, &gain);
   if (err == qerrNotSupported) {
      return DEVICE_NOT_SUPPORTED;
   } else if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   // get the min value
   err = QCam_GetParamMin((QCam_Settings *)m_settings, qprmNormalizedGain, &gainMin);
   if (err == qerrNotSupported) {
      minMaxSupport = false;
   } else if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   // get the max value
   err = QCam_GetParamMax((QCam_Settings *)m_settings, qprmNormalizedGain, &gainMax);
   if (err == qerrNotSupported) {
      minMaxSupport = false;
   } else if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   // setup the gain property
   gainAsFloat = (float)gain / 1000000.0f;
   sprintf(tempStr, "%f", gainAsFloat);

   propertyAction = new CPropertyAction (this, &QICamera::OnGain);
   nRet = CreateProperty(MM::g_Keyword_Gain, tempStr, MM::Float, false, propertyAction);
   if (nRet != DEVICE_OK) {
      REPORT_MMERR(nRet);
      return nRet;
   }

   if (minMaxSupport) {
      // create the min property
      gainMinAsFloat = (float)gainMin / 1000000.0f;
      sprintf(tempStr, "%f", gainMinAsFloat);
      nRet = CreateProperty(g_Keyword_Gain_Min, tempStr, MM::Float, true);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return nRet;
      }

      // create a max property
      gainMaxAsFloat = (float)gainMax / 1000000.0f;
      sprintf(tempStr, "%f", gainMaxAsFloat);
      nRet = CreateProperty(g_Keyword_Gain_Max, tempStr, MM::Float, true);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return nRet;
      }

      nRet = SetPropertyLimits(MM::g_Keyword_Gain, gainMinAsFloat, gainMaxAsFloat);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return nRet;
      }
   }


   return DEVICE_OK;
}


/**
* Initialize the Offset property with allowed values
*/
int QICamera::SetupOffset()
{
   CPropertyAction				*propertyAction;
   char						tempStr[256];
   signed long					offset, offsetMin, offsetMax;
   QCam_Err			        err;
   int							nRet;
   bool                     minMaxSupport = true;

   START_METHOD("QICamera::SetupOffset");

   // convert the normalized gain into a float
   err = QCam_GetParamS32((QCam_Settings *)m_settings, qprmS32AbsoluteOffset, &offset);
   if (err == qerrNotSupported) {
      return DEVICE_NOT_SUPPORTED;
   } else if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   // get the min/max
   err = QCam_GetParamS32Min((QCam_Settings *)m_settings, qprmS32AbsoluteOffset, &offsetMin);
   if (err == qerrNotSupported) {
      minMaxSupport = false;
   } else if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   err = QCam_GetParamS32Max((QCam_Settings *)m_settings, qprmS32AbsoluteOffset, &offsetMax);
   if (err == qerrNotSupported) {
      minMaxSupport = false;
   } else if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   // create the main property
   sprintf(tempStr, "%ld", offset);
   propertyAction = new CPropertyAction (this, &QICamera::OnOffset);
   nRet = CreateProperty(MM::g_Keyword_Offset, tempStr, MM::Integer, false, propertyAction);
   if (nRet != DEVICE_OK) {
      REPORT_MMERR(nRet);
      return nRet;
   }

   if (minMaxSupport) {
      // create the min property
      sprintf(tempStr, "%ld", offsetMin);
      nRet = CreateProperty(g_Keyword_Offset_Min, tempStr, MM::Integer, true);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return nRet;
      }

      // create the max property
      sprintf(tempStr, "%ld", offsetMax);
      nRet = CreateProperty(g_Keyword_Offset_Max, tempStr, MM::Integer, true);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return nRet;
      }

      nRet = SetPropertyLimits(MM::g_Keyword_Offset, offsetMin, offsetMax);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return nRet;
      }
   }

   return DEVICE_OK;
}

// Helper for SetupReadoutSpeed() below
bool ReadoutSpeedStringSortPredicate(const std::string& lhs, const std::string& rhs)
{
   double          lhsFloat, rhsFloat;

   // convert the string to a float to see what number it is
   lhsFloat = atof(lhs.c_str());
   rhsFloat = atof(rhs.c_str());
   return lhsFloat > rhsFloat;
}

/**
* Initialize the ReadoutSpeed property with allowed values
*/
int QICamera::SetupReadoutSpeed()
{
   QCam_qcReadoutPort          portTable[] = {qcPortNormal, qcPortEM};
   int                         portTableSize = sizeof(portTable) / sizeof(QCam_qcReadoutPort);
   unsigned long               oldReadoutPort;
   bool                        bFound;
   unsigned long				readoutTable[32];
   int							readoutTableSize = sizeof(readoutTable) / sizeof (unsigned long);
   int							iPort, counter;
   vector<string>				readoutValues;
   vector<string>::iterator    readoutItr;
   QCam_Err					err;
   CPropertyAction				*propertyAction;
   int							nRet;
   char						tempStr[256];
   char						defaultSpeedStr[256];
   unsigned long				defaultSpeedEnum;

   START_METHOD("QICamera::SetupReadoutSpeed");

   // Each readout port may have a different readout speed range. We first need
   // to aggregate all possible readout speed ranges for all possible readout ports.
   err = QCam_GetParam((QCam_Settings *)m_settings, qprmReadoutPort, &oldReadoutPort);
   if (!(err == qerrSuccess || err == qerrNotSupported)) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   for (iPort = 0; iPort < portTableSize; iPort++) {
      // fake setting the readout port
      err = QCam_SetParam((QCam_Settings *)m_settings, qprmReadoutPort, portTable[iPort]);
      if (!(err == qerrSuccess || err == qerrNotSupported)) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }
      err = QCam_PreflightSettings(m_camera, (QCam_Settings *)m_settings);
      if (!(err == qerrSuccess || err == qerrNotSupported)) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }

      // retrieve and add the possible readout speeds for this port
      err = QCam_GetParamSparseTable((QCam_Settings *)m_settings, qprmReadoutSpeed, readoutTable, &readoutTableSize);
      if (err == qerrNotSupported) {
         return DEVICE_NOT_SUPPORTED;
      } else if (err != qerrSuccess) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }

      // go through each readout speed and add the string version to the vector
      for (counter = 0; counter < readoutTableSize; counter ++) {
         ConvertReadoutSpeedToString((QCam_qcReadoutSpeed)readoutTable[counter], tempStr);
         // only add it if it does not exist
         bFound = false;
         for (readoutItr = readoutValues.begin(); readoutItr != readoutValues.end(); ++readoutItr) {
            if ((*readoutItr) == tempStr) {
               bFound = true;
               break;
            }
         }
         if (!bFound)
            readoutValues.push_back(tempStr);
      }
   }

   // restore old readout port
   err = QCam_SetParam((QCam_Settings *)m_settings, qprmReadoutPort, oldReadoutPort);
   if (!(err == qerrSuccess || err == qerrNotSupported)) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }
   err = QCam_PreflightSettings(m_camera, (QCam_Settings *)m_settings);
   if (!(err == qerrSuccess || err == qerrNotSupported)) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   // sort the vector since the values may be out of order
   std::sort(readoutValues.begin(), readoutValues.end(), ReadoutSpeedStringSortPredicate);

   // create the default speed string
   err = QCam_GetParam((QCam_Settings *)m_settings, qprmReadoutSpeed, &defaultSpeedEnum);
   if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   ConvertReadoutSpeedToString((QCam_qcReadoutSpeed)defaultSpeedEnum, defaultSpeedStr);

   // SetupReadoutSpeed() can be called more than once to update the allowed values
   // Only create the property if it does not exist yet
   if (!HasProperty(MM::g_Keyword_ReadoutTime)) {
      // create the action 
      propertyAction = new CPropertyAction (this, &QICamera::OnReadoutSpeed);
      nRet = CreateProperty(MM::g_Keyword_ReadoutTime, defaultSpeedStr, MM::String, false, propertyAction);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return nRet;
      }
   }

   // set the allowed values
   ClearAllowedValues(MM::g_Keyword_ReadoutTime);
   nRet = SetAllowedValues(MM::g_Keyword_ReadoutTime, readoutValues);
   if (nRet != DEVICE_OK) {
      REPORT_MMERR(nRet);
      return nRet;
   }

   return DEVICE_OK;	
}


/**
* Initialize the ReadoutPort property with allowed values.
*/
int QICamera::SetupReadoutPort()
{
   unsigned long				readoutTable[32];
   int							readoutTableSize = sizeof(readoutTable) / sizeof (unsigned long);
   int							counter;
   vector<string>				readoutValues;
   QCam_Err					err;
   CPropertyAction				*propertyAction;
   int							nRet;
   char						tempStr[256];
   char						defaultPortStr[256];
   unsigned long				defaultPortEnum;

   START_METHOD("QICamera::SetupReadoutPort");

   err = QCam_GetParamSparseTable((QCam_Settings *)m_settings, qprmReadoutPort, readoutTable, &readoutTableSize);
   if (err == qerrNotSupported) {
      return DEVICE_NOT_SUPPORTED;
   } else if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   // go through each readout ports
   for (counter = 0; counter < readoutTableSize; counter ++) {
      // convert the readout port enum to a string
      ConvertReadoutPortToString((QCam_qcReadoutPort)readoutTable[counter], tempStr);

      // add the binning value to the vector
      readoutValues.push_back(tempStr);
   }

   // create the default speed string
   err = QCam_GetParam((QCam_Settings *)m_settings, qprmReadoutPort, &defaultPortEnum);
   if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   ConvertReadoutPortToString((QCam_qcReadoutPort)defaultPortEnum, defaultPortStr);

   // create the action 
   propertyAction = new CPropertyAction (this, &QICamera::OnReadoutPort);
   nRet = CreateProperty(MM::g_Keyword_ReadoutMode, defaultPortStr, MM::String, false, propertyAction);
   if (nRet != DEVICE_OK) {
      REPORT_MMERR(nRet);
      return nRet;
   }

   // set the allowed values
   nRet = SetAllowedValues(MM::g_Keyword_ReadoutMode, readoutValues);
   if (nRet != DEVICE_OK) {
      REPORT_MMERR(nRet);
      return nRet;
   }

   return DEVICE_OK;	
}


/**
* Initialize the BitDepth property with allowed values
*/
int QICamera::SetupBitDepth()
{
   QCam_Err            err;
   unsigned long	      maxBitDepth;
   unsigned long		   imageFormat;
   CPropertyAction	   *propertyAction;
   int                 nRet;
   vector<string>      pixelTypeValues;
   const char          *mono8Str = "8bit";
   const char          *initialStr;
   char	               mono16Str[256];
   unsigned long	      formatTable[32];
   int                 formatTableSize = sizeof(formatTable) / sizeof (unsigned long);
   int                 counter;
   bool                mono8allowed, mono16allowed;

   START_METHOD("QICamera::SetupBitDepth");

   // get the max bit depth and cache it
   err = QCam_GetInfo(m_camera, qinfBitDepth, &maxBitDepth);
   if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }
   m_maxBitDepth = (int)maxBitDepth;
   sprintf(mono16Str, "%dbit", m_maxBitDepth);

   // get the allowed image formats. Not all cameras support 8bit mode
   mono8allowed = false;
   mono16allowed = false;
   err = QCam_GetParamSparseTable((QCam_Settings *)m_settings, qprmImageFormat, formatTable, &formatTableSize);
   if (err == qerrSuccess) {
      // Go through the availabe image formats and look for monochrome versions
      for (counter = 0; counter < formatTableSize; counter++) {
         if (formatTable[counter] == qfmtMono8) {
            mono8allowed = true;
         } else if (formatTable[counter] == qfmtMono16) {
            mono16allowed = true;
         }
      }
   } else {
      // only one format allowed.
      if (m_maxBitDepth > 8) {
         mono16allowed = true;
      } else {
         mono8allowed = true;
      }
   }

   // get the current bit depth
   err = QCam_GetParam((QCam_Settings *)m_settings, qprmImageFormat, &imageFormat);
   if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }
   if (QCam_is16bit(imageFormat)) {
      m_bitDepth = m_maxBitDepth;
      initialStr = mono16Str;        
   } else {	
      m_bitDepth = 8;
      initialStr = mono8Str;
   }

   // create the action
   propertyAction = new CPropertyAction (this, &QICamera::OnBitDepth);
   nRet = CreateProperty(MM::g_Keyword_PixelType, initialStr, MM::String, false, propertyAction);
   if (nRet != DEVICE_OK) {
      REPORT_MMERR(nRet);
      return nRet;
   }

   // set the allowed values
   if (mono8allowed)
      pixelTypeValues.push_back(mono8Str);
   if (mono16allowed)
      pixelTypeValues.push_back(mono16Str);		// 10, 12, 14 or 16 bit
   nRet = SetAllowedValues(MM::g_Keyword_PixelType, pixelTypeValues);
   if (nRet != DEVICE_OK) {
      REPORT_MMERR(nRet);
      return nRet;
   }

   return DEVICE_OK;
}

/**
* Initialize the Cooler property with allowed values
*/
int QICamera::SetupCooler()
{
   QCam_Err					err;
   unsigned long				isCoolerAvailable;
   CPropertyAction				*propertyAction;
   int							nRet;
   char						tempStr[256];
   vector<string>				coolerValues;

   START_METHOD("QICamera::SetupCooler");

   // check if the camera has a cooler
   err = QCam_GetInfo(m_camera, qinfCooled, &isCoolerAvailable);
   if (err == qerrNotSupported) {
      return DEVICE_NOT_SUPPORTED;
   } else if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }
   if (isCoolerAvailable != 1) {
      return DEVICE_NOT_SUPPORTED;
   }

   // if so, setup a parameter with On/Off values
   // create the property
   propertyAction = new CPropertyAction (this, &QICamera::OnCooler);
   nRet = CreateProperty(g_Keyword_Cooler, "On", MM::String, false, propertyAction);
   if (nRet != DEVICE_OK) {
      REPORT_MMERR(nRet);
      return nRet;
   }

   strcpy(tempStr, "On");
   coolerValues.push_back(tempStr);

   strcpy(tempStr, "Off");
   coolerValues.push_back(tempStr);

   nRet = SetAllowedValues(g_Keyword_Cooler, coolerValues);
   if (nRet != DEVICE_OK) {
      REPORT_MMERR(nRet);
      return nRet;
   }

   return DEVICE_OK;
}

/**
* Initialize the RegulatedCooling property with allowed values
*/
int QICamera::SetupRegulatedCooling()
{
   QCam_Err					err;
   unsigned long				isRegulatedCoolingAvailable;
   signed long					minCoolingTemp, maxCoolingTemp;
   CPropertyAction				*propertyAction;
   int							nRet;
   char						minTempStr[256];
   char						maxTempStr[256];
   bool                        minMaxSupport = true;

   START_METHOD("QICamera::SetupRegulatedCooling");

   // check if the camera has a cooler
   err = QCam_GetInfo(m_camera, qinfRegulatedCooling, &isRegulatedCoolingAvailable);
   if (err == qerrNotSupported) {
      return DEVICE_NOT_SUPPORTED;
   } else if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   if (isRegulatedCoolingAvailable != 1) {
      return DEVICE_NOT_SUPPORTED;
   }
   // get the min cooling temp available
   err = QCam_GetParamS32Min((QCam_Settings *)m_settings, qprmS32RegulatedCoolingTemp, &minCoolingTemp);
   if (err == qerrNotSupported) {
      minMaxSupport = false;
   } else if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   // get the max cooling temp available
   err = QCam_GetParamS32Max((QCam_Settings *)m_settings, qprmS32RegulatedCoolingTemp, &maxCoolingTemp);
   if (err == qerrNotSupported) {
      minMaxSupport = false;
   } else if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }


   if (maxCoolingTemp == minCoolingTemp)
   {
      minMaxSupport = false;
      // create the actual temperature property
      propertyAction = new CPropertyAction (this, &QICamera::OnRegulatedCooling);
      nRet = CreateProperty(MM::g_Keyword_CCDTemperature, "0", MM::Integer, true);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return nRet;
      }
   }
   else 
   {
      // create the actual temperature property
      propertyAction = new CPropertyAction (this, &QICamera::OnRegulatedCooling);
      nRet = CreateProperty(MM::g_Keyword_CCDTemperature, "0", MM::Integer, false, propertyAction);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return nRet;
      }
   }


   if (minMaxSupport) {
      // create a min temperature property
      sprintf(minTempStr, "%ld", minCoolingTemp);
      nRet = CreateProperty(g_Keyword_CCDTemperature_Min, minTempStr, MM::Integer, true);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return nRet;
      }

      // create a max temperature property
      sprintf(maxTempStr, "%ld", maxCoolingTemp);
      nRet = CreateProperty(g_Keyword_CCDTemperature_Max, maxTempStr, MM::Integer, true);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return nRet;
      }

      nRet = SetPropertyLimits(MM::g_Keyword_CCDTemperature, minCoolingTemp, maxCoolingTemp);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return nRet;
      }

   }

   return DEVICE_OK;

}

/**
* Initialize the EMGain (electron multiplier gain)
* property with allowed values
*/
int QICamera::SetupEMGain()
{
   CPropertyAction				*propertyAction;
   char						tempStr[256];
   unsigned long				emGain, emGainMin, emGainMax;
   QCam_Err					err;
   int							nRet;
   unsigned long				isEMGainAvailable;					
   bool                     minMaxSupport = true;

   START_METHOD("QICamera::SetupEMGain");

   // check if the camera supports EM gain
   err = QCam_GetInfo(m_camera, qinfEMGain, &isEMGainAvailable);
   if (err == qerrNotSupported) {
      return DEVICE_NOT_SUPPORTED;
   } else if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   if (isEMGainAvailable == 0) {
      return DEVICE_NOT_SUPPORTED;
   }

   // get the min value
   err = QCam_GetParamMin((QCam_Settings *)m_settings, qprmEMGain, &emGainMin);
   if (err == qerrNotSupported) {
      minMaxSupport = false;
   } else if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   // get the max value
   err = QCam_GetParamMax((QCam_Settings *)m_settings, qprmEMGain, &emGainMax);
   if (err == qerrNotSupported) {
      minMaxSupport = false;
   } else if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   err = QCam_GetParam((QCam_Settings *)m_settings, qprmEMGain, &emGain);
   if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   sprintf(tempStr, "%lu", emGain);

   propertyAction = new CPropertyAction (this, &QICamera::OnEMGain);
   nRet = CreateProperty(MM::g_Keyword_EMGain, tempStr, MM::Integer, false, propertyAction);
   if (nRet != DEVICE_OK) {
      REPORT_MMERR(nRet);
      return nRet;
   }

   if (minMaxSupport) {
      // create the min property
      sprintf(tempStr, "%lu", emGainMin);
      nRet = CreateProperty(g_Keyword_EMGain_Min, tempStr, MM::Integer, true);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return nRet;
      }

      // create the max property
      sprintf(tempStr, "%lu", emGainMax);
      nRet = CreateProperty(g_Keyword_EMGain_Max, tempStr, MM::Integer, true);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return nRet;
      }

      nRet = SetPropertyLimits(MM::g_Keyword_EMGain, emGainMin, emGainMax);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return nRet;
      }

   }
   return DEVICE_OK;
}

/**
* Initialize the ITGain (intensifier gain) property with allowed values
*/
int QICamera::SetupITGain()
{
   CPropertyAction				*propertyAction;
   char						tempStr[256];
   unsigned long long			itGain, itGainMin,itGainMax;
   QCam_Err					err;
   int							nRet;
   unsigned long				itModel;		
   float						itGainAsFloat, itGainMinAsFloat, itGainMaxAsFloat;
   bool                     minMaxSupport = true;

   START_METHOD("QICamera::SetupITGain");

   // check if the camera supports EM gain
   err = QCam_GetInfo(m_camera, qinfIntensifierModel, &itModel);
   if (err == qerrNotSupported) {
      return DEVICE_NOT_SUPPORTED;
   } else if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   // get the min value
   err = QCam_GetParam64Min((QCam_Settings *)m_settings, qprm64NormIntensGain, &itGainMin);
   if (err == qerrNotSupported) {
      minMaxSupport = false;
   } else if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   // get the max value
   err = QCam_GetParam64Max((QCam_Settings *)m_settings, qprm64NormIntensGain, &itGainMax);
   if (err == qerrNotSupported) {
      minMaxSupport = false;
   } else if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   // convert the intensifier gain into a float
   err = QCam_GetParam64((QCam_Settings *)m_settings, qprm64NormIntensGain, &itGain);
   if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   // setup the intensified gain property
   itGainAsFloat = (float)itGain / 1000000.0f;
   sprintf(tempStr, "%f", itGainAsFloat);

   propertyAction = new CPropertyAction (this, &QICamera::OnITGain);
   nRet = CreateProperty(g_Keyword_ITGain, tempStr, MM::Float, false, propertyAction);
   if (nRet != DEVICE_OK) {
      REPORT_MMERR(nRet);
      return nRet;
   }

   if (minMaxSupport) {
      // create the min property
      itGainMinAsFloat = (float)itGainMin / 1000000.0f;
      sprintf(tempStr, "%f", itGainMinAsFloat);
      nRet = CreateProperty(g_Keyword_ITGain_Min, tempStr, MM::Float, true);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return nRet;
      }

      // create the max property
      itGainMaxAsFloat = (float)itGainMax / 1000000.0f;
      sprintf(tempStr, "%f", itGainMaxAsFloat);
      nRet = CreateProperty(g_Keyword_ITGain_Max, tempStr, MM::Float, true);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return nRet;
      }

      nRet = SetPropertyLimits(g_Keyword_ITGain, itGainMinAsFloat, itGainMaxAsFloat);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return nRet;
      }

   }
   return DEVICE_OK;
}

/**
* Initialize the EMGain (electron multiplier gain)
* property with allowed values
*/
int QICamera::SetupEMAndEasyEMGain()
{
   unsigned long				emGain, emGainMin, emGainMax;
   QCam_Err					   err;
   unsigned long				isEMGainAvailable;					
   bool                    minMaxSupport = true;

   START_METHOD("QICamera::SetupEMAndEasyEMGain");

   // first check if the camera supports EM gain
   err = QCam_GetInfo(m_camera, qinfEMGain, &isEMGainAvailable);
   if (err == qerrNotSupported) {
      return DEVICE_NOT_SUPPORTED;
   } else if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   if (isEMGainAvailable == 0) {
      return DEVICE_NOT_SUPPORTED;
   }

   // get the min value
   err = QCam_GetParamMin((QCam_Settings *)m_settings, qprmEMGain, &emGainMin);
   if (err == qerrNotSupported) {
      minMaxSupport = false;
   } else if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   // get the max value
   err = QCam_GetParamMax((QCam_Settings *)m_settings, qprmEMGain, &emGainMax);
   if (err == qerrNotSupported) {
      minMaxSupport = false;
   } else if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   // convert the em gain into a float
   err = QCam_GetParam((QCam_Settings *)m_settings, qprmEMGain, &emGain);
   if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

#ifdef WIN32

   unsigned long           easyGainAsLong;
   int                     nRet;
   CPropertyActionEx       *propertyAction;
   char                    tempStr[256];

   sprintf(tempStr, "%lu", emGain);

   propertyAction = new CPropertyActionEx (this, &QICamera::OnEasyEMGain, 1);
   nRet = CreateProperty("Easy EM Gain", tempStr, MM::Integer, false, propertyAction);
   if (nRet != DEVICE_OK) {
      REPORT_MMERR(nRet);
      return nRet;
   }

   if (minMaxSupport) {
      // create the min property
      sprintf(tempStr, "%lu", emGainMin);
      nRet = CreateProperty(g_Keyword_EMGain_Min, tempStr, MM::Integer, true);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return nRet;
      }

      // create the max property
      sprintf(tempStr, "%lu", emGainMax);
      nRet = CreateProperty(g_Keyword_EMGain_Max, tempStr, MM::Integer, true);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return nRet;
      }

      nRet = SetPropertyLimits("Easy EM Gain", emGainMin, emGainMax);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return nRet;
      }

   }

   string	easyGain;
   easyGainAsLong = 0;
   err = QCam_GetParam((QCam_Settings *)m_settings, qprmEasyEmMode, &easyGainAsLong);
   if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   if (easyGainAsLong == 1){

      easyGain = Yes.c_str();

      if (DEVICE_OK != SetEasyEMGain(easyGainAsLong)){
         return DEVICE_ERR;
      }
   }
   else {
      easyGain = No.c_str();
   }

   propertyAction = new CPropertyActionEx (this, &QICamera::OnEasyEMGain,0);
   nRet = CreateProperty("Easy EM Enabled", easyGain.c_str(), MM::String, false, propertyAction);
   if (nRet != DEVICE_OK) {
      REPORT_MMERR(nRet);
      return nRet;
   }

   vector<std::string> resetValues;
   resetValues.push_back(Yes.c_str());
   resetValues.push_back(No.c_str());
   SetAllowedValues("Easy EM Enabled", resetValues);

#endif

   return DEVICE_OK;
}

/**
* Allocate the circular frame buffer structures. 
*/
int QICamera::SetupFrames()
{
   START_METHOD("QICamera::SetupFrames");

   // Do not allocate the actual frame buffers storage yet. 
   // This will be done in ResizeImageBuffer()

   // allocate and initialize buffer for circular buffer of alternating frames
   m_frameBuffs = new QCam_Frame*[m_nFrameBuffs];
   m_frameBuffsAvail = new bool[m_nFrameBuffs];
   for (int i=0; i<m_nFrameBuffs; i++) {
      m_frameBuffs[i] = new QCam_Frame;
      m_frameBuffs[i]->pBuffer = NULL;
      m_frameBuffs[i]->bufferSize = 0;
      m_frameBuffsAvail[i] = true;
   }

   return DEVICE_OK;
}

/**
* Initialize the triggering type.
* Set the default to Freerun mode.
*/
int QICamera::SetupTriggerType()
{
   unsigned long				typeTable[32];
   int							typeTableSize = sizeof(typeTable) / sizeof (unsigned long);
   int							counter;
   vector<string>				typeValues;
   QCam_Err					err;
   CPropertyAction				*propertyAction;
   int							nRet;
   char						tempStr[256];
   char						defaultTypeStr[256];

   START_METHOD("QICamera::TriggerType");

   err = QCam_GetParamSparseTable((QCam_Settings *)m_settings, qprmTriggerType, typeTable, &typeTableSize);
   if (err == qerrNotSupported) {
      return DEVICE_NOT_SUPPORTED;
   } else if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   // go through each trigger type
   for (counter = 0; counter < typeTableSize; counter ++) {
      // convert the trigger type enum to a string
      ConvertTriggerTypeToString((QCam_qcTriggerType)typeTable[counter], tempStr);

      // add the trigger value to the vector
      typeValues.push_back(tempStr);
   }

   // Startup in Freerun trigger mode
   err = QCam_SetParam((QCam_Settings *)m_settings, qprmTriggerType, qcTriggerFreerun);
   if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   nRet = SendSettingsToCamera((QCam_Settings *)m_settings);
   if (nRet != DEVICE_OK) {
      REPORT_MMERR(nRet);
      return DEVICE_ERR;
   }

   // create the default trigger type string
   ConvertTriggerTypeToString(qcTriggerFreerun, defaultTypeStr);

   // create the action 
   propertyAction = new CPropertyAction (this, &QICamera::OnTriggerType);
   nRet = CreateProperty(g_Keyword_TriggerType, defaultTypeStr, MM::String, false, propertyAction);
   if (nRet != DEVICE_OK) {
      REPORT_MMERR(nRet);
      return nRet;
   }

   // set the allowed values
   nRet = SetAllowedValues(g_Keyword_TriggerType, typeValues);
   if (nRet != DEVICE_OK) {
      REPORT_MMERR(nRet);
      return nRet;
   }

   return DEVICE_OK;	
}

/**
* Initialize the Trigger delay property with allowed values
*/
int QICamera::SetupTriggerDelay()
{
   CPropertyAction				*propertyAction;
   char						tempStr[256];
   unsigned long			    delay, delayMin, delayMax;
   float						delayAsFloat, delayMinAsFloat, delayMaxAsFloat;
   QCam_Err					err;
   int							nRet;
   bool                        minMaxSupport = true;

   START_METHOD("QICamera::SetupTriggerDelay");

   err = QCam_GetParam((QCam_Settings *)m_settings, qprmTriggerDelay, &delay);
   if (err == qerrNotSupported) {
      return DEVICE_NOT_SUPPORTED;
   } else if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   // get the min value
   err = QCam_GetParamMin((QCam_Settings *)m_settings, qprmTriggerDelay, &delayMin);
   if (err == qerrNotSupported) {
      minMaxSupport = false;
   } else if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   // get the max value
   err = QCam_GetParamMax((QCam_Settings *)m_settings, qprmTriggerDelay, &delayMax);
   if (err == qerrNotSupported) {
      minMaxSupport = false;
   } else if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   // convert the nanosecond delay time into a millisecond string
   delayAsFloat = (float)delay / 1000000.0f;
   sprintf(tempStr, "%f", delayAsFloat);

   propertyAction = new CPropertyAction (this, &QICamera::OnTriggerDelay);
   nRet = CreateProperty(g_Keyword_TriggerDelay, tempStr, MM::Float, false, propertyAction);
   if (nRet != DEVICE_OK) {
      REPORT_MMERR(nRet);
      return nRet;
   }

   if (minMaxSupport) {
      // create the min property
      delayMinAsFloat = (float)delayMin / 1000000.0f;
      sprintf(tempStr, "%f", delayMinAsFloat);
      nRet = CreateProperty(g_Keyword_TriggerDelay_Min, tempStr, MM::Float, true);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return nRet;
      }

      // create the max property
      delayMaxAsFloat = (float)delayMax / 1000000.0f;
      sprintf(tempStr, "%f", delayMaxAsFloat);
      nRet = CreateProperty(g_Keyword_TriggerDelay_Max, tempStr, MM::Float, true);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return nRet;
      }

      nRet = this->SetPropertyLimits(g_Keyword_TriggerDelay, delayMinAsFloat, delayMaxAsFloat);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return nRet;
      }
   }
   return DEVICE_OK;
}


/////////////////////////////////////////////////////////////////////////////
// MMCamera API
/////////////////////////////////////////////////////////////////////////////

/**
* Determines if the camera is currently changing state.
* Required by the MM::Camera API
*/
bool QICamera::Busy()
{
   // JK: There appears to be controversy over the meaning of Busy()
   // In my opinion, Busy should reflect a device that is in the
   // middle of a state change (opening the shutter, moving an axis,
   // etc). Live acquisition is a constant state rather than a change
   // in state. Busy should not be used to signal live acquisition.
   // Instead, the CCameraBase function IsCapturing() should be used
   // to signal the live acquisition state, not Busy().
   // Busy() should be used primarily for non-blocking devices:
   // devices that return control immediately to the caller
   // upon a state change command. Here, Busy() should reflect an
   // ongoing change in state.
   // This QICamera device is a blocking device. Any request to change
   // state (binning, exposure, etc) does not return control to the 
   // caller until the state has changed. Therefore, Busy() always
   // returns false.
   return false;
}

/**
* Performs exposure and grabs a single image.
* Required by the MM::Camera API.
*/
int QICamera::SnapImage()
{
   QCam_Err  	err;
   int          ret;
   START_METHOD("QICamera::SnapImage");

   if (m_sthd->IsRunning())
      return ERR_BUSY_ACQUIRING;

   //in software trigger mode use QCam_Trigger() instead of QCam_GrabFrame() in order
   //to not generate more than one expose out pulse on the SyncB signal
   if (m_softwareTrigger)
   {
       // turn on image streaming
       err = QCam_SetStreaming(m_camera, true);
       if (err != qerrSuccess) {
          REPORT_QERR(err);
          return DEVICE_ERR;
       }

       m_frameDoneBuff = -1;
       //mark one buffer as available and queue up a frame
       m_frameBuffsAvail[0] = true;
       QueueFrame(0);

       //send software trigger to the camera to acquire one frame
       Trigger();
       ret = m_frameDoneEvent.Wait((long)m_dExposure+3000);
       if (ret != MM_WAIT_OK) {
          QCam_REPORT_QERR(m_pCam, ret);
	      
          // abort any remaining frames
          err = QCam_Abort(m_camera);
          if (err != qerrSuccess) {
              QCam_REPORT_QERR(m_pCam, err);
          }
          return DEVICE_SNAP_IMAGE_FAILED;
       }       

       // turn off image streaming
       err = QCam_SetStreaming(m_camera, false);
       if (err != qerrSuccess) {
          REPORT_QERR(err);
          return DEVICE_ERR;
       }
   }
   //in modes other than software trigger use QCam_GrabFrame()
   else 
   {
       err = QCam_GrabFrame(m_camera, m_frameBuffs[0]);
   }
   
   if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_SNAP_IMAGE_FAILED;
   }

   // special post processing for Bayer color images
   if (m_rgbColor)
   {
      // input format is: qfmtBayer8
      // output format should be: qfmtBgrx32
      if (m_bitDepth > 8)
         m_debayer.Process(m_colorBuffer, (unsigned short*)m_frameBuffs[0]->pBuffer, m_frameBuffs[0]->width, m_frameBuffs[0]->height, m_bitDepth);
      else
         m_debayer.Process(m_colorBuffer, (unsigned char*)m_frameBuffs[0]->pBuffer, m_frameBuffs[0]->width, m_frameBuffs[0]->height, m_bitDepth);
   }
   return DEVICE_OK;
}

/**
* Sends software trigger to start new acquisition
*/
int QICamera::Trigger()
{
   QCam_Err				err;

   START_METHOD("QICamera::Trigger");

   if (!m_softwareTrigger)
      return ERR_SOFTWARE_TRIGGER_FAILED;

   err = QCam_Trigger(m_camera);
   if (err != qerrSuccess) {
      REPORT_QERR(err);
      return ERR_SOFTWARE_TRIGGER_FAILED;
   }

   return DEVICE_OK;
}

/**
* Returns pixel data.
* Required by the MM::Camera API.
* The calling program will assume the size of the buffer based on the values
* obtained from GetImageBufferSize(), which in turn should be consistent with
* values returned by GetImageWidth(), GetImageHeight() and GetImageBytesPerPixel().
* The calling program allso assumes that camera never changes the size of
* the pixel buffer on its own. In other words, the buffer can change only if
* appropriate properties are set (such as binning, pixel type, etc.)
*/
const unsigned char* QICamera::GetImageBuffer()
{
   START_METHOD("QICamera::GetImageBuffer");

   if (m_rgbColor)
      return (const unsigned char*)m_colorBuffer.GetPixels();
   else
      return (const unsigned char*)m_frameBuffs[0]->pBuffer;
}

/**
* Returns image buffer X-size in pixels.
* Required by the MM::Camera API.
*/
unsigned QICamera::GetImageWidth() const
{
   QCam_Err			err;
   unsigned long		imageWidth;

   //    START_METHOD("QICamera::GetImageWidth");

   // check
   if (!m_isInitialized) {
      return 0;
   }

   // get the image width
   err = QCam_GetInfo(m_camera, qinfImageWidth, &imageWidth);
   if (err != qerrSuccess) {
      return 0;
   }

   return imageWidth;
}

/**
* Returns image buffer Y-size in pixels.
* Required by the MM::Camera API.
*/
unsigned QICamera::GetImageHeight() const
{
   QCam_Err			err;
   unsigned long		imageHeight;

   //    START_METHOD("QICamera::GetImageHeight");

   // check
   if (!m_isInitialized) {
      return 0;
   }

   // get the image width
   err = QCam_GetInfo(m_camera, qinfImageHeight, &imageHeight);
   if (err != qerrSuccess) {
      return 0;
   }

   return imageHeight;
}

/**
* Returns image buffer pixel depth in bytes.
* Required by the MM::Camera API.
*/
unsigned QICamera::GetImageBytesPerPixel() const
{
   //    START_METHOD("QICamera::GetImageBytesPerPixel");

   // check
   if (!m_isInitialized) {
      return 0;
   }

   if (m_rgbColor)
      return 4; // rgba format (standard for micro-manager)
   else
      return (m_bitDepth > 8) ? 2 : 1;
} 

/**
* Returns the bit depth (dynamic range) of the pixel.
* This does not affect the buffer size, it just gives the client application
* a guideline on how to interpret pixel values.
* Required by the MM::Camera API.
*/
unsigned QICamera::GetBitDepth() const
{
   START_METHOD("QICamera::GetBitDepth");

   // check
   if (!m_isInitialized) {
      return 0;
   }

   // NS: use a cached value, not sure if this can get stale...
   return m_rgbColor ? 8 : m_bitDepth;
}

/**
* Returns the size in bytes of the image buffer.
* Required by the MM::Camera API.
*/
long QICamera::GetImageBufferSize() const
{
   START_METHOD("QICamera::GetImageBufferSize");

   long size = m_imageWidth * m_imageHeight * GetImageBytesPerPixel();
   return size;
}

/**
* Sets the camera Region Of Interest.
* Required by the MM::Camera API.
* This command will change the dimensions of the image.
* Depending on the hardware capabilities the camera may not be able to configure the
* exact dimensions requested - but should try do as close as possible.
* If the hardware does not have this capability the software should simulate the ROI by
* appropriately cropping each frame.
* @param x - top-left corner coordinate
* @param y - top-left corner coordinate
* @param xSize - width
* @param ySize - height
*/
int QICamera::SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize)
{
   QCam_Err				err;

   START_METHOD("QICamera::SetROI");

   if (xSize == 0 && ySize == 0) {
      // effectively clear ROI
      return ClearROI();
   }

   // set the roi
   err = QCam_SetParam((QCam_Settings *)m_settings, qprmRoiX, x);
   if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }
   err = QCam_SetParam((QCam_Settings *)m_settings, qprmRoiY, y);
   if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   err = QCam_SetParam((QCam_Settings *)m_settings, qprmRoiWidth, xSize);
   if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }
   err = QCam_SetParam((QCam_Settings *)m_settings, qprmRoiHeight, ySize);
   if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   // commit it
   int nRet = SendSettingsToCamera((QCam_Settings *)m_settings);
   if (nRet != DEVICE_OK) {
      REPORT_MMERR(nRet);
      return DEVICE_ERR;
   }

   // Apply the ROI internally
   ResizeImageBuffer();

   return DEVICE_OK;
}

/**
* Returns the actual dimensions of the current ROI.
* Required by the MM::Camera API.
*/
int QICamera::GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize)
{
   QCam_Err			err;
   unsigned long		roiX, roiY, roiWidth, roiHeight;

   START_METHOD("QICamera::GetROI");

   // get the roi
   err = QCam_GetParam((QCam_Settings *)m_settings, qprmRoiX, &roiX);
   if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }
   err = QCam_GetParam((QCam_Settings *)m_settings, qprmRoiY, &roiY);
   if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }
   err = QCam_GetParam((QCam_Settings *)m_settings, qprmRoiWidth, &roiWidth);
   if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }
   err = QCam_GetParam((QCam_Settings *)m_settings, qprmRoiHeight, &roiHeight);
   if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   // use local varibles of unsigned long type to avoid
   // compiler warnings about casting unsigned int to unsigned long
   x = roiX;
   y = roiY;
   xSize = roiWidth;
   ySize = roiHeight;

   return DEVICE_OK;
}

/**
* Resets the Region of Interest to full frame.
* Required by the MM::Camera API.
*/
int QICamera::ClearROI()
{
   QCam_Err			err;
   unsigned long		maxWidth; 
   unsigned long		maxHeight; 

   START_METHOD("QICamera::ClearROI");

   // Determine the maximum size (at the current binning level)
   err = QCam_GetInfo(m_camera, qinfCcdWidth, &maxWidth); 
   if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }
   err = QCam_GetInfo(m_camera, qinfCcdHeight, &maxHeight); 
   if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   // reset the ROI to be full frame 
   err = QCam_SetParam((QCam_Settings *)m_settings, qprmRoiX, 0); 
   if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }
   err = QCam_SetParam((QCam_Settings *)m_settings, qprmRoiY, 0); 
   if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }
   err = QCam_SetParam((QCam_Settings *)m_settings, qprmRoiWidth, maxWidth); 
   if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }
   err = QCam_SetParam((QCam_Settings *)m_settings, qprmRoiHeight, maxHeight); 
   if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   // commit it
   int nRet = SendSettingsToCamera((QCam_Settings *)m_settings);
   if (nRet != DEVICE_OK) {
      REPORT_MMERR(nRet);
      return DEVICE_ERR;
   }

   return ResizeImageBuffer();
}

/**
* Returns the current exposure setting in milliseconds.
* Required by the MM::Camera API.
*/
double QICamera::GetExposure() const
{
   double					exposure;
   unsigned long long		exposureAsLongLong;
   QCam_Err				err;

   START_METHOD("QICamera::GetExposure");

   // get it
   err = QCam_GetParam64((QCam_Settings *)m_settings, qprm64Exposure, &exposureAsLongLong);
   if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   // convert it to milliseconds
   exposure = exposureAsLongLong / 1000000.0;
   return exposure;
}

/**
* Sets exposure in milliseconds.
* Required by the MM::Camera API.
*/
void QICamera::SetExposure(double exp)
{
   START_METHOD("QICamera::SetExposure");

   m_dExposure = exp;
   SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(exp));
}

/**
* Returns the current binning mode.
*/
int QICamera::GetBinning () const 
{
   START_METHOD("QICamera::GetBinning");

   char binMode[MM::MaxStrLength];
   GetProperty(MM::g_Keyword_Binning, binMode);
   return atoi(binMode);
}

/**
* Sets the current binning mode.
*/
int QICamera::SetBinning (int binSize) 
{
   START_METHOD("QICamera::SetBinning");

   ostringstream os;
   os << binSize;
   return SetProperty(MM::g_Keyword_Binning, os.str().c_str());
}


///////////////////////////////////////////////////////////////////////////////
// QICamera Action handlers
///////////////////////////////////////////////////////////////////////////////

/**
* Sets or gets the Exposure property
*/
int QICamera::OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   double					exposure;
   unsigned long long		exposureAsLongLong;
   QCam_Err				err;

   START_ONPROPERTY("QICamera::OnExposure", eAct);
   ostringstream txt;
   txt << "Entered QICamera::OnExposure";

   // see if the user wants to get or set the property
   if (eAct == MM::AfterSet) {	
      txt<<" AfterSet ";
      bool wasCapturing = IsCapturing();
      if (wasCapturing)
         StopSequenceAcquisition();

      pProp->Get(exposure);
      // convert the exposure to nanoseconds 64-bit long
      exposureAsLongLong = (unsigned long long) (exposure * 1000000.0);

      // set it
      err = QCam_SetParam64((QCam_Settings *)m_settings, qprm64Exposure, exposureAsLongLong);
      if (err != qerrSuccess) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }

      int nRet = SendSettingsToCamera((QCam_Settings *)m_settings);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return DEVICE_ERR;
      }
      m_dExposure = exposure;

      txt<<exposure;
      if (wasCapturing)
         RestartSequenceAcquisition();

   } else if (eAct == MM::BeforeGet) {
      // get it
      txt<<" BeforeGet ";
      err = QCam_GetParam64((QCam_Settings *)m_settings, qprm64Exposure, &exposureAsLongLong);
      if (err != qerrSuccess) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }

      // convert it to milliseconds
      exposure = exposureAsLongLong / 1000000.0;
      pProp->Set(exposure);
      txt<<exposure;
   }

   return DEVICE_OK; 
}

/**
* Sets or gets the Binning property
*/
int QICamera::OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   long					binningMode;
   QCam_Err				err;

   START_ONPROPERTY("QICamera::OnBinning", eAct);

   // see if the user wants to get or set the property
   if (eAct == MM::AfterSet)  {	
      bool wasCapturing = IsCapturing();
      if (wasCapturing)
         StopSequenceAcquisition();

      pProp->Get(binningMode);

      // set it (try symmetrical binning first)
      err = QCam_SetParam((QCam_Settings *)m_settings, qprmBinning, binningMode);
      if (err == qerrNotSupported) {
         // try just vertical
         err = QCam_SetParam((QCam_Settings *)m_settings, qprmVerticalBinning, binningMode);
      }
      if (err != qerrSuccess) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }

      // commit it
      // ClearRoi() calls ResizeImageBuffer() for us
      int ret = ClearROI();
      if (ret != DEVICE_OK) {
         return ret;
      }

      if (wasCapturing)
         RestartSequenceAcquisition();

   } else if (eAct == MM::BeforeGet) {
      // get it
      err = QCam_GetParam((QCam_Settings *)m_settings, qprmBinning, (unsigned long*)&binningMode);
      if (err == qerrNotSupported) {
         // try just vertical
         err = QCam_GetParam((QCam_Settings *)m_settings, qprmVerticalBinning, (unsigned long*)&binningMode);
      }
      if (err != qerrSuccess) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }

      pProp->Set(binningMode);
   }

   return DEVICE_OK; 
}

/**
* Sets or gets the Gain property
*/
int QICamera::OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   double				gain;
   unsigned long		gainAsLong;
   QCam_Err				err;
   START_ONPROPERTY("QICamera::OnGain", eAct);

   // see if the user wants to get or set the property
   if (eAct == MM::AfterSet) {	
      bool wasCapturing = IsCapturing();
      if (wasCapturing)
         StopSequenceAcquisition();

      pProp->Get(gain);

      // convert the exposure to the real normalized gain value
      gainAsLong = (unsigned long) (gain * 1000000.0f);

      // set it
      err = QCam_SetParam((QCam_Settings *)m_settings, qprmNormalizedGain, gainAsLong);
      if (err != qerrSuccess) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }

      // commit it
      int nRet = SendSettingsToCamera((QCam_Settings *)m_settings);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return DEVICE_ERR;
      }

      if (wasCapturing)
         RestartSequenceAcquisition();

   } else if (eAct == MM::BeforeGet) {
      // get it
      err = QCam_GetParam((QCam_Settings *)m_settings, qprmNormalizedGain, &gainAsLong);
      if (err != qerrSuccess) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }

      // convert it to a more readable form
      gain = gainAsLong / 1000000.0;
      pProp->Set(gain);
   }

   return DEVICE_OK; 
}

/**
* Sets or gets the Offset property
*/
int QICamera::OnOffset(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   long					offset;
   QCam_Err				err;

   START_ONPROPERTY("QICamera::OnOffset", eAct);

   // see if the user wants to get or set the property
   if (eAct == MM::AfterSet) {	
      bool wasCapturing = IsCapturing();
      if (wasCapturing)
         StopSequenceAcquisition();

      pProp->Get(offset);

      // set it
      err = QCam_SetParamS32((QCam_Settings *)m_settings, qprmS32AbsoluteOffset, offset);
      if (err != qerrSuccess) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }

      // commit it
      int nRet = SendSettingsToCamera((QCam_Settings *)m_settings);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return DEVICE_ERR;
      }

      if (wasCapturing)
         RestartSequenceAcquisition();

   } else if (eAct == MM::BeforeGet) {
      // get it
      err = QCam_GetParamS32((QCam_Settings *)m_settings, qprmS32AbsoluteOffset, &offset);
      if (err != qerrSuccess) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }

      pProp->Set(offset);
   }

   return DEVICE_OK; 
}

/**
* Sets or gets the Readout Speed property
*/
int QICamera::OnReadoutSpeed(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   std::string				readoutSpeedStr;
   char					readoutSpeedChars[256];
   QCam_qcReadoutSpeed		readoutSpeedEnum;
   QCam_Err				err;

   START_ONPROPERTY("QICamera::OnReadoutSpeed", eAct);

   // see if the user wants to get or set the property
   if (eAct == MM::AfterSet) {	
      bool wasCapturing = IsCapturing();
      if (wasCapturing)
         StopSequenceAcquisition();

      pProp->Get(readoutSpeedStr);

      // convert the string to a readout speed enum
      ConvertReadoutSpeedToEnum(readoutSpeedStr.c_str(), &readoutSpeedEnum);

      // set it
      err = QCam_SetParam((QCam_Settings *)m_settings, qprmReadoutSpeed, readoutSpeedEnum);
      if (err != qerrSuccess) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }

      // commit it
      int nRet = SendSettingsToCamera((QCam_Settings *)m_settings);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return DEVICE_ERR;
      }

#ifdef WIN32

      unsigned long isEMGainAvailable = 0;

      err = QCam_GetInfo(m_camera, qinfEasyEmModeSupported, &isEMGainAvailable);
      if (err != qerrSuccess) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      } 

      if (1 == isEMGainAvailable)
      {
         unsigned long easyGainAsLong = 0;
         err = QCam_GetParam((QCam_Settings *)m_settings, qprmEasyEmMode, &easyGainAsLong);
         if (err != qerrSuccess) {
            REPORT_QERR(err);
            return DEVICE_ERR;
         }

         if (easyGainAsLong == 1)
         {
            nRet = SetEasyEMGain(easyGainAsLong);
            if (nRet != DEVICE_OK) {
               return DEVICE_ERR;
            }
         }
      }

#endif
      // Because we set more allowed values than possible, we need to retrieve the readout
      // speed from the camera to check
      UpdateProperty(MM::g_Keyword_ReadoutTime);

      if (wasCapturing)
         RestartSequenceAcquisition();

   } else if (eAct == MM::BeforeGet) {
      // get it

      err = QCam_GetParam((QCam_Settings *)m_settings, qprmReadoutSpeed, (unsigned long*)&readoutSpeedEnum);
      if (err != qerrSuccess) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }

      ConvertReadoutSpeedToString(readoutSpeedEnum, readoutSpeedChars);

      pProp->Set(readoutSpeedChars);
   }

   return DEVICE_OK; 
}

/**
* Sets or gets the Readout Mode property
*/
int QICamera::OnReadoutPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   std::string				readoutPortStr;
   char					readoutPortChars[256];
   QCam_qcReadoutPort		readoutPortEnum;
   QCam_Err				err;

   START_ONPROPERTY("QICamera::OnReadoutPort", eAct);

   // see if the user wants to get or set the property
   if (eAct == MM::AfterSet) {	
      bool wasCapturing = IsCapturing();
      if (wasCapturing)
         StopSequenceAcquisition();

      pProp->Get(readoutPortStr);

      // convert the string to a readout speed enum
      ConvertReadoutPortToEnum(readoutPortStr.c_str(), &readoutPortEnum);

      // set it
      err = QCam_SetParam((QCam_Settings *)m_settings, qprmReadoutPort, readoutPortEnum);
      if (err != qerrSuccess) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }

      // commit it
      int nRet = SendSettingsToCamera((QCam_Settings *)m_settings);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return DEVICE_ERR;
      }

      // The readout speed may have changed. Update the property with current value
      UpdateProperty(MM::g_Keyword_ReadoutTime);

      if (wasCapturing)
         RestartSequenceAcquisition();

   } else if (eAct == MM::BeforeGet) {
      // get it
      err = QCam_GetParam((QCam_Settings *)m_settings, qprmReadoutPort, (unsigned long*)&readoutPortEnum);
      if (err != qerrSuccess) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }

      ConvertReadoutPortToString(readoutPortEnum, readoutPortChars);

      pProp->Set(readoutPortChars);
   }

   return DEVICE_OK; 
}

/**
* Sets or gets the BitDepth property
*/
int QICamera::OnBitDepth(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   std::string				bitDepthString;
   unsigned long			bitDepth;
   unsigned long			imageFormat;
   char					tempStr[256];
   QCam_Err				err;

   START_ONPROPERTY("QICamera::OnBitDepth", eAct);

   // see if the user wants to get or set the property
   if (eAct == MM::AfterSet) {	
      bool wasCapturing = IsCapturing();
      if (wasCapturing)
         StopSequenceAcquisition();

      pProp->Get(bitDepthString);

      // conver the string to a number
      bitDepth = atoi(bitDepthString.c_str());

      // set it
      if (bitDepth == 8) {
         // 8 bit
         err = QCam_SetParam((QCam_Settings *)m_settings, qprmImageFormat, qfmtMono8);
         if (err != qerrSuccess) {
            REPORT_QERR(err);
            return DEVICE_ERR;
         }
      } else {
         // 16 bit
         err = QCam_SetParam((QCam_Settings *)m_settings, qprmImageFormat, qfmtMono16);
         if (err != qerrSuccess) {
            REPORT_QERR(err);
            return DEVICE_ERR;
         }
      }

      // commit it
      int nRet = SendSettingsToCamera((QCam_Settings *)m_settings);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return DEVICE_ERR;
      }

      unsigned long imageFormat;
      err = QCam_GetParam((QCam_Settings *)m_settings, qprmImageFormat, &imageFormat);

      // update cached value
      m_bitDepth = QCam_is16bit(imageFormat) ? 2 : 1;

      // reset the image buffer
      ResizeImageBuffer();

      if (wasCapturing)
         RestartSequenceAcquisition();

   } else if (eAct == MM::BeforeGet) {
      // get it
      err = QCam_GetParam((QCam_Settings *)m_settings, qprmImageFormat, &imageFormat);
      if (err != qerrSuccess) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }

      // see which one it is
      if (QCam_is16bit(imageFormat)) {
         m_bitDepth = m_maxBitDepth;
      } else {	
         m_bitDepth = 8;
      }

      sprintf(tempStr, "%dbit", m_bitDepth);
      pProp->Set(tempStr);
   }

   return DEVICE_OK; 
}

/**
* Sets or gets the Cooler property
*/
int QICamera::OnCooler(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   std::string				coolerString;
   QCam_Err				err;
   unsigned long			value;
   char					tempStr[256];

   START_ONPROPERTY("QICamera::OnCooler", eAct);

   // see if the user wants to get or set the property
   if (eAct == MM::AfterSet) {	
      bool wasCapturing = IsCapturing();
      if (wasCapturing)
         StopSequenceAcquisition();

      pProp->Get(coolerString);

      // convert to a number
      if (coolerString.compare("On") == 0) {
         value = true;
      } else {
         value = false;
      }

      // set it
      err = QCam_SetParam((QCam_Settings *)m_settings, qprmCoolerActive, value);
      if (err != qerrSuccess) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }

      // commit it
      int nRet = SendSettingsToCamera((QCam_Settings *)m_settings);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return DEVICE_ERR;
      }

      if (wasCapturing)
         RestartSequenceAcquisition();

   } else if (eAct == MM::BeforeGet) {
      // get it
      err = QCam_GetParam((QCam_Settings *)m_settings, qprmCoolerActive, &value);
      if (err != qerrSuccess) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }

      if (value != 0) {
         strcpy(tempStr, "On");
      } else {
         strcpy(tempStr, "Off");
      }

      pProp->Set(tempStr);
   }

   return DEVICE_OK; 
}

/**
* Sets or gets the Regulated Cooling property
*/
int QICamera::OnRegulatedCooling(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   std::string				tempString;
   QCam_Err				err;
   long					coolingTemp;
   char					tempStr[256];

   START_ONPROPERTY("QICamera::OnRegulatedCooling", eAct);

   // see if the user wants to get or set the property
   if (eAct == MM::AfterSet) {	
      bool wasCapturing = IsCapturing();
      if (wasCapturing)
         StopSequenceAcquisition();

      pProp->Get(tempString);

      // convert to a number
      coolingTemp = atoi(tempString.c_str());

      // set it
      err = QCam_SetParamS32((QCam_Settings *)m_settings, qprmS32RegulatedCoolingTemp, coolingTemp);
      if (err != qerrSuccess) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }

      // commit it
      int nRet = SendSettingsToCamera((QCam_Settings *)m_settings);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return DEVICE_ERR;
      }

      if (wasCapturing)
         RestartSequenceAcquisition();

   } else if (eAct == MM::BeforeGet) {
      // get it
      err = QCam_GetParamS32((QCam_Settings *)m_settings, qprmS32RegulatedCoolingTemp, &coolingTemp);
      if (err != qerrSuccess) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }

      // convert to a string
      sprintf(tempStr, "%ld", coolingTemp);

      pProp->Set(tempStr);
   }

   return DEVICE_OK; 
}

/**
* Sets or gets the EMGain property
*/
int QICamera::OnEMGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   double					emGain;
   unsigned long			emGainAsLong;
   QCam_Err				err;
   START_ONPROPERTY("QICamera::OnEMGain", eAct);

   // see if the user wants to get or set the property
   if (eAct == MM::AfterSet) {	
      bool wasCapturing = IsCapturing();
      if (wasCapturing)
         StopSequenceAcquisition();

      pProp->Get(emGain);

      // convert the exposure to the real normalized gain value
      emGainAsLong = (unsigned long) (emGain);// * 1000000.0f);

      // set it
      err = QCam_SetParam((QCam_Settings *)m_settings, qprmEMGain, emGainAsLong);
      if (err != qerrSuccess) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }

      // commit it
      int nRet = SendSettingsToCamera((QCam_Settings *)m_settings);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return DEVICE_ERR;
      }

      if (wasCapturing)
         RestartSequenceAcquisition();

   } else if (eAct == MM::BeforeGet) {
      // get it
      err = QCam_GetParam((QCam_Settings *)m_settings, qprmEMGain, &emGainAsLong);
      if (err != qerrSuccess) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }

      emGain = emGainAsLong;// / 1000000.0;
      pProp->Set(emGain);
   }

   return DEVICE_OK; 
}



int QICamera::SetEasyEMGain(unsigned long  easyGainAsLong)
{

#ifdef WIN32
   QCam_Err				err = qerrSuccess;
   unsigned long           emGainAsLong = 0;
   if (easyGainAsLong == 1)
   {

      QCam_Param emParam;
      unsigned long uReadoutSpeed;

      err = QCam_GetParam((QCam_Settings *)m_settings, qprmReadoutSpeed, &uReadoutSpeed);
      if (err != qerrSuccess) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }
      switch (uReadoutSpeed)
      {
      case qcReadout10M:
         emParam = qprmEasyEmGainValue10;
         break;

      case qcReadout20M:
         emParam = qprmEasyEmGainValue20;
         break;

      case qcReadout40M:
         emParam = qprmEasyEmGainValue40;
         break;	
      default:
         // Just the the camera to the EMGain that it is currently set to in case 
         // we get an invalid speed
         emParam = qprmEMGain;
         break;
      }

      err = QCam_GetParam((QCam_Settings *)m_settings, emParam, &emGainAsLong);
      if (err != qerrSuccess) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }

      err = QCam_SetParam((QCam_Settings *)m_settings, qprmEMGain, emGainAsLong);
      if (err != qerrSuccess) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }
   }
#endif
   return DEVICE_OK; 
}
/**
* Sets or gets the EMGain property
*/
int QICamera::OnEasyEMGain(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{
#ifdef WIN32

   string					easyGain;
   unsigned long			easyGainAsLong = 0;
   QCam_Err				err;

   START_ONPROPERTY("QICamera::OnEasyEMGain", eAct);


   if (0 == index)
   {
      string value;
      if (eAct == MM::AfterSet)
      {
         bool wasCapturing = IsCapturing();
         if (wasCapturing)
            StopSequenceAcquisition();

         pProp->Get(easyGain);

         if (easyGain == No.c_str())
            easyGainAsLong = 0;
         else
            easyGainAsLong = 1;

         if (easyGain == No.c_str())
         {
            easyGainAsLong = 0;
            err = QCam_SetParam((QCam_Settings *)m_settings, qprmEasyEmMode, easyGainAsLong);
            if (err != qerrSuccess) {
               REPORT_QERR(err);
               return DEVICE_ERR;
            }
         }
         else if (easyGain == Yes.c_str())
         {
            START_ONPROPERTY("QICamera::OnEasyEMGain, yes", eAct);

            easyGainAsLong = 1;
            err = QCam_SetParam((QCam_Settings *)m_settings, qprmEasyEmMode, easyGainAsLong);
            if (err != qerrSuccess) {
               REPORT_QERR(err);
               return DEVICE_ERR;
            }
            if (DEVICE_OK != SetEasyEMGain(easyGainAsLong)){
               return DEVICE_ERR;
            }
         }

         // commit it
         int nRet = SendSettingsToCamera((QCam_Settings *)m_settings);
         if (nRet != DEVICE_OK) {
            REPORT_MMERR(nRet);
            return DEVICE_ERR;
         }

         if (wasCapturing)
            RestartSequenceAcquisition();
      }
      else if (eAct == MM::BeforeGet)
      {

         err = QCam_GetParam((QCam_Settings *)m_settings, qprmEasyEmMode, &easyGainAsLong);
         if (err != qerrSuccess) {
            REPORT_QERR(err);
            return DEVICE_ERR;
         }

         if (easyGainAsLong == 1){

            easyGain = Yes.c_str();

            if (DEVICE_OK != SetEasyEMGain(easyGainAsLong)){
               return DEVICE_ERR;
            }
         }
         else {
            easyGain = No.c_str();
         }

         pProp->Set(easyGain.c_str());
      }
   }
   else
   {
      OnEMGain(pProp, eAct);
   }
#endif
   return DEVICE_OK; 
}


/**
* Sets or gets the ITGain property
*/
int QICamera::OnITGain(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   double					itGain;
   unsigned long long		itGainAsU64;
   QCam_Err				err;

   START_ONPROPERTY("QICamera::OnITGain", eAct);

   // see if the user wants to get or set the property
   if (eAct == MM::AfterSet) {	
      bool wasCapturing = IsCapturing();
      if (wasCapturing)
         StopSequenceAcquisition();

      pProp->Get(itGain);

      // convert the intensifier gain to a uint64
      itGainAsU64 = (unsigned long long) (itGain * 1000000.0f);

      // set it
      err = QCam_SetParam64((QCam_Settings *)m_settings, qprm64NormIntensGain, itGainAsU64);
      if (err != qerrSuccess) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }

      // commit it
      int nRet = SendSettingsToCamera((QCam_Settings *)m_settings);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return DEVICE_ERR;
      }

      if (wasCapturing)
         RestartSequenceAcquisition();

   } else if (eAct == MM::BeforeGet) {
      // get it
      err = QCam_GetParam64((QCam_Settings *)m_settings, qprm64NormIntensGain, &itGainAsU64);
      if (err != qerrSuccess) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }

      // convert it a double
      itGain = (double) itGainAsU64 / 1000000.0f;
      pProp->Set(itGain);
   }

   return DEVICE_OK; 
}

/**
* Sets or gets the Trigger Type property
*/
int QICamera::OnTriggerType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   std::string				typeStr;
   char					typeChars[256];
   QCam_qcTriggerType		typeEnum;
   QCam_Err				err;

   START_ONPROPERTY("QICamera::OnTriggerType", eAct);

   // see if the user wants to get or set the property
   if (eAct == MM::AfterSet) {	
      bool wasCapturing = IsCapturing();
      if (wasCapturing)
         StopSequenceAcquisition();

      pProp->Get(typeStr);

      // convert the string to a trigger type enum
      ConvertTriggerTypeToEnum(typeStr.c_str(), &typeEnum);

      // set it
      err = QCam_SetParam((QCam_Settings *)m_settings, qprmTriggerType, typeEnum);
      if (err != qerrSuccess) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }

      // commit it
      int nRet = SendSettingsToCamera((QCam_Settings *)m_settings);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return DEVICE_ERR;
      }

      // abort any remaining frames
       err = QCam_Abort(m_camera);
       if (err != qerrSuccess) {
          QCam_REPORT_QERR(m_pCam, err);
       }

      // SnapImage needs to know if we have set software triggering
      m_softwareTrigger = (typeEnum == qcTriggerSoftware);

      if (wasCapturing)
         RestartSequenceAcquisition();

   } else if (eAct == MM::BeforeGet) {
      // get it
      err = QCam_GetParam((QCam_Settings *)m_settings, qprmTriggerType, (unsigned long*)&typeEnum);
      if (err != qerrSuccess) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }

      ConvertTriggerTypeToString(typeEnum, typeChars);

      pProp->Set(typeChars);
   }

   return DEVICE_OK; 
}

/**
* Sets or gets the Trigger Delay property
*/
int QICamera::OnTriggerDelay(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   double					delay;
   unsigned long           delayAsLong;
   QCam_Err				err;

   START_ONPROPERTY("QICamera::OnTriggerDelay", eAct);

   // see if the user wants to get or set the property
   if (eAct == MM::AfterSet) {	
      bool wasCapturing = IsCapturing();
      if (wasCapturing)
         StopSequenceAcquisition();

      pProp->Get(delay);
      // convert the delay to nanoseconds 
      delayAsLong = (unsigned long) (delay * 1000000.0);

      // set it
      err = QCam_SetParam((QCam_Settings *)m_settings, qprmTriggerDelay, delayAsLong);
      if (err != qerrSuccess) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }

      int nRet = SendSettingsToCamera((QCam_Settings *)m_settings);
      if (nRet != DEVICE_OK) {
         REPORT_MMERR(nRet);
         return DEVICE_ERR;
      }

      if (wasCapturing)
         RestartSequenceAcquisition();

   } else if (eAct == MM::BeforeGet) {
      // get it
      err = QCam_GetParam((QCam_Settings *)m_settings, qprmTriggerDelay, &delayAsLong);
      if (err != qerrSuccess) {
         REPORT_QERR(err);
         return DEVICE_ERR;
      }

      // convert it to milliseconds
      delay = delayAsLong / 1000000.0;
      pProp->Set(delay);
   }

   return DEVICE_OK; 
}

/**
* Sets or gets the ColorMode property
*/
int QICamera::OnColorMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   START_ONPROPERTY("QICamera::OnColorMode", eAct);

   // see if the user wants to get or set the property
   if (eAct == MM::AfterSet) {	
      bool wasCapturing = IsCapturing();
      if (wasCapturing)
         StopSequenceAcquisition();

         string val;
         pProp->Get(val);
         val.compare(g_Value_ON) == 0 ? m_rgbColor = true : m_rgbColor = false;
         ResizeImageBuffer();

      if (wasCapturing)
         RestartSequenceAcquisition();

   } else if (eAct == MM::BeforeGet) {
      pProp->Set(m_rgbColor ? g_Value_ON : g_Value_OFF);
   }

   return DEVICE_OK; 
}


unsigned QICamera::GetNumberOfComponents() const 
{
   return m_rgbColor ? 4 : 1;
   //return 1;
}

int QICamera::GetComponentName(unsigned comp, char* name)
{
   char compName[MM::MaxStrLength];
   snprintf(compName, MM::MaxStrLength, "component-%d", comp);
   CDeviceUtils::CopyLimitedString(name, compName);
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Private QICamera methods
///////////////////////////////////////////////////////////////////////////////

/**
* Sync internal image buffer size to the chosen property values.
*/
int QICamera::ResizeImageBuffer()
{
   unsigned long			imageWidth, imageHeight;
   unsigned long			imageFormat;
   unsigned long           requiredSize;
   QCam_Err				err;

   START_METHOD("QICamera::ResizeImageBuffer");

   // get the image width and height
   err = QCam_GetInfo(m_camera, qinfImageWidth, &imageWidth);
   if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   err = QCam_GetInfo(m_camera, qinfImageHeight, &imageHeight);
   if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   // get the image format
   err = QCam_GetParam((QCam_Settings *)m_settings, qprmImageFormat, &imageFormat);
   if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   requiredSize = QCam_CalcImageSize(imageFormat, imageWidth, imageHeight);

   // ## IMPORTANT (JK) ##
   // Through an agonizing several days of debugging, I found that
   // the QCam_ErrFrame.bufferSize parameter MUST be the exact same size 
   // as the  captured image. If it is LARGER than required, the 
   // QCam driver (ver 2.0.8.2) throws a memory Access Violation. 
   // Bad driver! No Biscuit.
   // Therefore, we will just reallocate all of the image the 
   // buffers if the required size changes.

   if (m_frameBuffs[0]->bufferSize != requiredSize) {
      // reallocate the frame storage buffers but only if necessary
      // The code below will execute the first time because the m_frameBuffs 
      // are each allocated with "bufferSize = 0;"
      for (int i=0; i<m_nFrameBuffs; i++) {
         if (m_frameBuffs[i]->pBuffer != NULL) {
            delete (unsigned char*)m_frameBuffs[i]->pBuffer;
         }
         m_frameBuffs[i]->pBuffer = new unsigned char[requiredSize];
         m_frameBuffs[i]->bufferSize = requiredSize;
      }
   }

   // store the image buffer parameters
   m_imageWidth = (unsigned int)imageWidth;
   m_imageHeight = (unsigned int)imageHeight;
   if (QCam_is16bit(imageFormat)) {
      m_bitDepth = m_maxBitDepth;
   } else {
      m_bitDepth = 8;
   }

   // make sure the circular buffer is properly sized
   GetCoreCallback()->InitializeImageBuffer(1, 1, m_imageWidth, m_imageHeight, GetImageBytesPerPixel());

   return DEVICE_OK;
}

/**
* Updates the camera settings.
* This helper function can handle qerrBusy errors
*/
int QICamera::SendSettingsToCamera(QCam_Settings *settings)
{
   QCam_Err                err;

   START_METHOD("QICamera::SendSettingsToCamera");

   // Try to commit parameters
   err = QCam_SendSettingsToCam(m_camera, settings);
   if (err == qerrBusy) {
      // NOTE: This error condition should hopefully never be executed
      // Any SendSettingsToCamera call should have stopped the image
      // thread beforehand.
      REPORT_QERR2("QICamera::SendSettingsToCamera: Camera was busy. Aborting first. ", err);

      // The queue was busy. Abort any operation and try again
      err = QCam_Abort(m_camera);
      if (err != qerrSuccess) {
         REPORT_QERR(err);
      }
      err = QCam_SendSettingsToCam(m_camera, settings);
   }

   if (err != qerrSuccess) {
      REPORT_QERR(err);
      return err;
   }

   return qerrSuccess;
}

/**
* Logs error 
*/
int QICamera::LogError(std::string message, int err, char* file, int line) const
{
   ostringstream os;
   os << message << err;
   if (file) {
      os << ": in " << file << "(" << line << ")";
   }
   LogMessage(os.str().c_str());
   return err;
}

/////////////////////////////////////////////////////////////////////////////
// Asynchronous Threadded Functions
/////////////////////////////////////////////////////////////////////////////

/**
* Start an infinite stream of acquisitions in the background.
*/
int QICamera::StartSequenceAcquisition(double interval_ms)
{
   START_METHOD("QICamera::StartSequenceAcquisition(long)");

   return StartSequenceAcquisition(LONG_MAX, interval_ms, false);
}

/**
* Start acquiring images in the background.
*/
int QICamera::StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow)
{
   QCam_Err err;

   START_METHOD("QICamera::StartSequenceAcquisition(long,double,bool)");

   m_interval = interval_ms;
   if (m_sthd->IsRunning())
      return DEVICE_CAMERA_BUSY_ACQUIRING;

   int ret = GetCoreCallback()->PrepareForAcq(this);
   if (ret != DEVICE_OK) {
      REPORT_MMERR(ret);
      return ret;
   }

   // turn on image streaming
   err = QCam_SetStreaming(m_camera, true);
   if (err != qerrSuccess) {
      REPORT_QERR(err);
      return DEVICE_ERR;
   }

   // start the acquisition thread
   m_sthd->SetLength(numImages);
   setStopOnOverflow(stopOnOverflow);
   m_sthd->Start();

   return DEVICE_OK;
}

/**
* Resumes any sequence acquisition
*/
int QICamera::RestartSequenceAcquisition()
{
   return StartSequenceAcquisition(m_sthd->GetRemaining(), m_interval, isStopOnOverflow());
}


/**
* Stop any current acquisition operations.
*/
int QICamera::StopSequenceAcquisition()
{
   START_METHOD("QICamera::StopSequenceAcquisition");

   m_sthd->Stop();
   // set a dummy event to stop any current waiting by the sequence thread
   {
      MMThreadGuard guard(this->m_frameDoneLock);
      m_frameDoneBuff = -1;
   }
   m_frameDoneEvent.Set();
   if (m_sthd->IsRunning()) {
      m_sthd->wait();
   }

   return DEVICE_OK;
}

/**
* Main entry point for the sequence acquisition thread.
*/
int QICamera::QISequenceThread::svc()
{
   int             ret;
   QCam_Err        err;
   int             returnError = 0;
   int             iFrameBuff;
   long            timeout;

   //START_METHOD("QICamera::QISequenceThread::svc ENTER");

   // Reset the image queue and image count
   m_captureCount = 0;
   m_pCam->m_frameDoneBuff = -1;
   for (int i=0; i<m_nFrameBuffs; i++) {
      m_pCam->m_frameBuffsAvail[i] = true;
   }

   // set a reasonable timeout (in msec) for waiting on frames
   timeout = (long)(5 * m_pCam->m_dExposure + 50000);

   // start streaming images. 
   // Queue up all of the circular image buffers so we always have an image
   // in the queue.
   for (int i=0; i<m_nFrameBuffs; i++) {
      m_pCam->QueueFrame(i);
   }

   //in software trigger mode queueing frames is not sufficient to start an acquisition
   //therefore call QCam_Trigger() for the first frame
   if (m_pCam->m_softwareTrigger == true) {
       err = QCam_Trigger(m_pCam->m_camera);
       if (err != qerrSuccess) {
           returnError = 1;
           REPORT_QERR(err);
       }
   }
   
   while (!m_stop && m_captureCount < GetLength() && returnError == 0) {
      // wait for a new frame finish capturing
      ret = m_pCam->m_frameDoneEvent.Wait(timeout);
      if (ret != MM_WAIT_OK) {
         QCam_REPORT_QERR(m_pCam, ret);
         returnError = 1;
         break;
      }

      // determine which buffer was just signalled
      {
         MMThreadGuard guard(m_pCam->m_frameDoneLock);
         iFrameBuff = m_pCam->m_frameDoneBuff;
      }
      if (iFrameBuff >= 0) {
         ret = m_pCam->InsertImage(iFrameBuff);
         if (ret != DEVICE_OK) {
            QCam_REPORT_MMERR(m_pCam, ret);
            returnError = 1;
            break;
         }
         m_captureCount++;

         // mark this frame as available again
         m_pCam->m_frameBuffsAvail[iFrameBuff] = true;

         // requeue this frame buffer
         if (!m_stop) {
            ret = m_pCam->QueueFrame(iFrameBuff);
            if (ret != DEVICE_OK) {
               QCam_REPORT_MMERR(m_pCam, ret);
               returnError = 1;
               break;
            }
            //call QCam_Trigger() also for each subsequent requeued frame
            if (m_pCam->m_softwareTrigger) {
                err = QCam_Trigger(m_pCam->m_camera);
                if (err != qerrSuccess) {
                   REPORT_QERR(err);
                   returnError = 1;
                   break;
                }
            }
         }
      }
   }

   // abort any remaining frames
   err = QCam_Abort(m_pCam->m_camera);
   if (err != qerrSuccess) {
      QCam_REPORT_QERR(m_pCam, err);
   }

   // turn off streaming
   err = QCam_SetStreaming(m_pCam->m_camera, false);
   if (err != qerrSuccess) {
      QCam_REPORT_QERR(m_pCam, err);
   }

   this->m_isRunning = false;

   MM::Core* cb = m_pCam->GetCoreCallback();
   if (cb)
      cb->AcqFinished(m_pCam, 0);

   //START_METHOD("QICamera::QISequenceThread::svc EXIT 0");
   return returnError;
}

/**
* Process the captured frame and add it to the MM::Core circular queue.
* This function should be called after every image is captured.
*/
int QICamera::InsertImage(int iFrameBuff)
{
   int             ret;

   //START_METHOD("QICamera::InsertImage");

   //if (pFrame->errorCode != qerrSuccess) {
   //    printf("## InsertImage found errorCode = %d\n", pFrame->errorCode);
   //}

   // process the image
   //image processor now called from core
   // This method inserts new image in the circular buffer (residing in MMCore)
   // NOTE: it essentially does a memcpy() from the camera's buffer
   // to MMCore's circular buffer
   // get the current image buffer
   QCam_Frame* pFrame = m_frameBuffs[iFrameBuff];
   
   if (m_rgbColor) {
      // input format is: qfmtBayer8
      // output format should be: qfmtBgrx32
      
      if (m_bitDepth > 8)
         m_debayer.Process(m_colorBuffer, (unsigned short*)pFrame->pBuffer, pFrame->width, pFrame->height, m_bitDepth);
      else
         m_debayer.Process(m_colorBuffer, (unsigned char*)pFrame->pBuffer, pFrame->width, pFrame->height, m_bitDepth);

      ret = GetCoreCallback()->InsertImage(this, (unsigned char*) m_colorBuffer.GetPixelsRW(), 
         m_colorBuffer.Width(), m_colorBuffer.Height(), m_colorBuffer.Depth());

      if (!isStopOnOverflow() && ret == DEVICE_BUFFER_OVERFLOW) {
         // do not stop on overflow - just reset the buffer
         GetCoreCallback()->ClearImageBuffer(this);
         ret = GetCoreCallback()->InsertImage(this, (unsigned char*) m_colorBuffer.GetPixelsRW(), 
            m_colorBuffer.Width(), m_colorBuffer.Height(), m_colorBuffer.Depth());
      }

   } else {
      int bytes = (pFrame->bits > 8) ? 2 : 1;
      ret = GetCoreCallback()->InsertImage(this, (unsigned char*) pFrame->pBuffer, 
         pFrame->width, pFrame->height, bytes);

      if (!isStopOnOverflow() && ret == DEVICE_BUFFER_OVERFLOW) {
         // do not stop on overflow - just reset the buffer
         GetCoreCallback()->ClearImageBuffer(this);
         ret = GetCoreCallback()->InsertImage(this, (unsigned char*) pFrame->pBuffer, 
            pFrame->width, pFrame->height, bytes);
      }
   }
   return ret;
}

/**
* Adds a buffer to the camera's internal capture queue.
* NOTE: The internal queue can only hold 100 entries
*/
int QICamera::QueueFrame(int iFrameBuff)
{
   QCam_Err err;

#ifdef QCam_ErrEXPOSURE_DONE_REQUIRED
   err = QCam_QueueFrame(m_camera, m_frameBuffs[iFrameBuff], 
      FrameDoneCallback, qcCallbackDone | qcCallbackExposeDone, this, iFrameBuff);
#else
   err = QCam_QueueFrame(m_camera, m_frameBuffs[iFrameBuff], 
      FrameDoneCallback, qcCallbackDone , this, iFrameBuff);
#endif
   if (err == qerrQueueFull) {
      // the queue was full
      return DEVICE_BUFFER_OVERFLOW;
   } else if (err != qerrSuccess) {
      // something else happened while filling the queue
      REPORT_QERR(err);
      return DEVICE_ERR;
   }
   return DEVICE_OK;
}

/** 
* Callback passed to QCam_QueueFrame
* Calls the camera's FrameDone or ExposureDone method with the frame number
*/
void QCAMAPI FrameDoneCallback (void* userPtr, unsigned long userFrame,
                                QCam_Err errcode, unsigned long flags /*NOT USED*/)
{
   // NOTE: Only the following QCam functions may be used within this callback
   //  QCam_QueueSettings, QCam_QueueFrame, QCam_GetInfo, QCam_GetParam
   //  QCam_SetParam, QCam_GetParamMin, QCam_GetParamMax, QCam_PreflightSettings
   //  QCam_ReadDefaultSettings, QCam_ReadSettingsFromCam
   if (userPtr != NULL)  {
#ifdef QCam_ErrEXPOSURE_DONE_REQUIRED
      if (flags & qcCallbackExposeDone)
         ((QICamera*)userPtr)->ExposureDone(userFrame, errcode);
#endif
      if (flags & qcCallbackDone)
         ((QICamera*)userPtr)->FrameDone(userFrame, errcode);
   }
}

/**
* Called when a frame has been transferred from the camera.
*/
void QICamera::FrameDone(long frameNumber, QCam_Err errcode) 
{
   //QCam_Err        err;

   assert(frameNumber >= 0 && frameNumber < m_nFrameBuffs);

   if ((errcode == qerrSuccess) || (errcode == qerrBlackFill)) {
      // Has this buffer been processed the last time through?
      if (m_frameBuffsAvail[frameNumber]) {
         // This frame buffer was processed successfully last time. It is OK to read.
         // First, take it out of the availability list.
         m_frameBuffsAvail[frameNumber] = false;
         // Let the service thread process it. Signal the frame done event.
         // Make sure to set the current frame BEFORE signalling the frameDone event
         {
            MMThreadGuard guard(this->m_frameDoneLock);
            m_frameDoneBuff = (int)frameNumber;
         }
         m_frameDoneEvent.Set();
      } else {
         // This frame cannot be processed, since it is still being used
         // by the service thread. Just ReQueue it.
         QueueFrame((int)frameNumber);
         // ignore errors for now
      } 
   } else if (errcode == qerrFirewireOverflow) {
      // TODO: Stop and restart the image queue if we cannot keep up
   }
}

#ifdef QCam_ErrEXPOSURE_DONE_REQUIRED
/**
* Called when the frame has finished exposing.
* Usually called before FrameDone()
*/
void QICamera::ExposureDone(long /*frameNumber*/, QCam_Err /*errcode*/)
{
   // We do not do anything with this callback yet
}
#endif
